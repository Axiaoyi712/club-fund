package com.clubfund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clubfund.dto.ActivityForm;
import com.clubfund.entity.Activity;
import com.clubfund.entity.ApproveLog;
import com.clubfund.entity.BudgetItem;
import com.clubfund.entity.Club;
import com.clubfund.entity.Expense;
import com.clubfund.entity.User;
import com.clubfund.mapper.ActivityMapper;
import com.clubfund.mapper.ApproveLogMapper;
import com.clubfund.mapper.BudgetItemMapper;
import com.clubfund.mapper.ClubMapper;
import com.clubfund.mapper.ExpenseMapper;
import com.clubfund.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动申请表业务。
 * 核心逻辑：
 * 1. 预算金额 = 数量 × 单价，每行自动算
 * 2. 合计自动累加（预算合计、申请金额合计）
 * 3. 提交校验：活动开始时间必须晚于今天 + 7天（学院规则：至少提前一周填报）
 */
@Service
public class ActivityService {

    /** 列表每页条数 */
    private static final long PAGE_SIZE = 10;
    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999.99");

    private final ActivityMapper activityMapper;
    private final BudgetItemMapper budgetItemMapper;
    private final ClubMapper clubMapper;
    private final UserMapper userMapper;
    private final ApproveService approveService;
    private final ApproveLogMapper approveLogMapper;
    private final ExpenseMapper expenseMapper;
    private final NotificationService notificationService;
    private final ExpenseService expenseService;
    private final ArchiveService archiveService;

    public ActivityService(ActivityMapper activityMapper, BudgetItemMapper budgetItemMapper,
                           ClubMapper clubMapper, UserMapper userMapper,
                           ApproveService approveService, ApproveLogMapper approveLogMapper,
                           ExpenseMapper expenseMapper, NotificationService notificationService,
                           ExpenseService expenseService, ArchiveService archiveService) {
        this.activityMapper = activityMapper;
        this.budgetItemMapper = budgetItemMapper;
        this.clubMapper = clubMapper;
        this.userMapper = userMapper;
        this.approveService = approveService;
        this.approveLogMapper = approveLogMapper;
        this.expenseMapper = expenseMapper;
        this.notificationService = notificationService;
        this.expenseService = expenseService;
        this.archiveService = archiveService;
    }

    /**
     * 提交申请表：先算合计，再存活动表 + 存明细行。
     * 校验不通过时返回错误信息；通过返回 null。
     */
    @Transactional(rollbackFor = Exception.class)
    public String add(ActivityForm form, User user) {
        if (!validSubmissionToken(form.getSubmissionToken())) {
            return "申请表已失效，请核对内容后重新提交";
        }
        Activity activity = new Activity();
        activity.setSubmissionToken(form.getSubmissionToken());
        String error = prepareActivity(form, user, activity);
        if (error != null) return error;
        activity.setStatus("submitted");                 // 初始状态：已提交待审批
        activity.setApplyUser(user.getId());
        LocalDateTime submittedAt = LocalDateTime.now();
        activity.setFirstApplyTime(submittedAt);
        activity.setApplyTime(submittedAt);
        activityMapper.insert(activity);

        // 存明细行
        saveBudgetItems(activity.getId(), form.getItems());

        // 通知：给所有主席团用户发一条审批待办
        notificationService.notifyRole("chairman", "todo",
                "新活动申请待审批：" + activity.getTitle(), activity.getId());
        return null;   // 成功
    }

    public boolean validSubmissionToken(String token) {
        return token != null && token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    /** 凭证只在当前申请人范围内查找，不能跳转到别人的申请。 */
    public Activity submittedFor(String token, User user) {
        if (!validSubmissionToken(token)) return null;
        return activityMapper.selectOne(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getApplyUser, user.getId())
                .eq(Activity::getSubmissionToken, token));
    }

    /** 新增与重提共用校验、金额计算和字段赋值。 */
    private String prepareActivity(ActivityForm form, User user, Activity activity) {
        // 与社团删除、成员添加按同一社团记录串行，校验后直到申请写入完成才释放。
        Club club = form.getClubId() == null ? null : clubMapper.selectOne(
                new LambdaQueryWrapper<Club>().eq(Club::getId, form.getClubId()).last("FOR UPDATE"));
        if (club == null) {
            return "所选社团不存在或已被删除，请重新选择";
        }
        if (!user.getId().equals(club.getLeaderId())) {
            return "只有所选社团的负责人可以提交申请";
        }
        if (form.getTitle() == null || form.getTitle().isBlank()) {
            return "请填写活动名称";
        }
        if (form.getTitle().length() > 200) {
            return "活动名称不能超过200个字符";
        }
        if (form.getOrganizer() != null && form.getOrganizer().length() > 100) {
            return "承办/协办单位不能超过100个字符";
        }
        if (form.getLocation() != null && form.getLocation().length() > 100) {
            return "活动地点不能超过100个字符";
        }
        // 校验1：明细至少一行
        if (form.getItems() == null || form.getItems().isEmpty()) {
            return "请至少添加一条预算明细";
        }
        // 校验2：活动开始时间必须提前至少一周（学院规则）
        if (form.getStartTime() == null) {
            return "请填写活动开始时间";
        }
        if (form.getEndTime() == null) {
            return "请填写活动结束时间";
        }
        if (form.getStartTime().getYear() < 1000 || form.getStartTime().getYear() > 9999
                || form.getEndTime().getYear() < 1000 || form.getEndTime().getYear() > 9999) {
            return "活动时间必须在1000年至9999年之间";
        }
        if (form.getEndTime().isBefore(form.getStartTime())) {
            return "活动结束时间不能早于开始时间";
        }
        LocalDateTime earliest = LocalDateTime.now().plusDays(7);
        if (form.getStartTime().isBefore(earliest)) {
            return "按学院要求，活动开始时间必须至少在申请当天的一周之后（当前最早可申请：" + earliest.toLocalDate() + "）";
        }

        // 逐行算预算金额，并累加合计
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalApply = BigDecimal.ZERO;
        for (int i = 0; i < form.getItems().size(); i++) {
            BudgetItem item = form.getItems().get(i);
            String row = "第" + (i + 1) + "行预算：";
            if (item == null || item.getItemName() == null || item.getItemName().isBlank()) {
                return row + "请填写开支项目";
            }
            if (item.getItemName().length() > 100) {
                return row + "开支项目不能超过100个字符";
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                return row + "数量必须是正整数";
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) {
                return row + "单价必须是大于或等于0的金额";
            }
            if (item.getUnitPrice().compareTo(MAX_MONEY) > 0) {
                return row + "单价不能超过99,999,999.99元";
            }
            if (item.getUnitPrice().scale() > 2) {
                return row + "单价最多两位小数，请修改后提交";
            }
            BigDecimal apply = item.getApplyAmount() == null ? BigDecimal.ZERO : item.getApplyAmount();
            if (apply.signum() < 0 || apply.compareTo(MAX_MONEY) > 0) {
                return row + "申请金额必须在0至99,999,999.99元之间";
            }
            if (apply.scale() > 2) {
                return row + "申请金额最多两位小数，请修改后提交";
            }
            item.setUnitPrice(item.getUnitPrice().setScale(2));
            apply = apply.setScale(2);
            item.setApplyAmount(apply);
            BigDecimal qty = BigDecimal.valueOf(item.getQuantity());
            BigDecimal budget = item.getUnitPrice().multiply(qty);   // 预算金额 = 数量×单价
            if (budget.compareTo(MAX_MONEY) > 0) {
                return row + "预算金额不能超过99,999,999.99元";
            }
            if (apply.compareTo(budget) > 0) {
                return row + "申请金额不能超过该项预算金额（¥" + budget.toPlainString() + "）";
            }
            item.setBudgetAmount(budget);
            totalBudget = totalBudget.add(budget);
            totalApply = totalApply.add(apply);
        }

        if (totalBudget.compareTo(MAX_MONEY) > 0 || totalApply.compareTo(MAX_MONEY) > 0) {
            return "预算合计和申请金额合计均不能超过99,999,999.99元";
        }

        activity.setClubId(form.getClubId());
        activity.setOrganizer(form.getOrganizer());
        activity.setTitle(form.getTitle());
        activity.setLocation(form.getLocation());
        activity.setStartTime(form.getStartTime());
        activity.setEndTime(form.getEndTime());
        activity.setTotalBudget(totalBudget);
        activity.setTotalApply(totalApply);
        return null;
    }

    private void saveBudgetItems(Long activityId, List<BudgetItem> items) {
        for (BudgetItem item : items) {
            item.setId(null);
            item.setActivityId(activityId);
            budgetItemMapper.insert(item);
        }
    }

    /**
     * 分页查询申请列表：
     * - 申请人只看自己的申请
     * - 其他角色（主席团/团委老师/领导/管理员）看全部（他们负责审批）
     */
    public Map<String, Object> listFor(User user, int pageNum) {
        LambdaQueryWrapper<Activity> qw = visibleQuery(user);
        qw.orderByDesc(Activity::getApplyTime);
        Page<Activity> page = activityMapper.selectPage(new Page<>(pageNum, PAGE_SIZE), qw);

        // 拼上：主办社团名称、申请人姓名
        Map<Long, String> clubNames = new HashMap<>();
        Map<Long, String> userNames = new HashMap<>();
        page.getRecords().forEach(a -> {
            Club club = clubMapper.selectById(a.getClubId());
            if (club != null) clubNames.put(a.getClubId(), club.getName());
            User u = userMapper.selectById(a.getApplyUser());
            if (u != null) userNames.put(a.getApplyUser(), u.getRealName());
        });

        Map<String, Object> result = new HashMap<>();
        result.put("page", page);
        result.put("clubNames", clubNames);
        result.put("userNames", userNames);
        return result;
    }

    /**
     * 看板视图：按审批状态分列（待审批/审批中/已驳回/已获批）。
     * 不分页全量返回（演示数据量小），便于前端按状态分组展示。
     */
    public Map<String, Object> board(User user) {
        LambdaQueryWrapper<Activity> qw = visibleQuery(user);
        qw.orderByDesc(Activity::getApplyTime);
        List<Activity> all = activityMapper.selectList(qw);

        List<Activity> pending = new ArrayList<>();
        List<Activity> inProgress = new ArrayList<>();
        List<Activity> rejected = new ArrayList<>();
        List<Activity> approved = new ArrayList<>();
        for (Activity a : all) {
            switch (a.getStatus()) {
                case "submitted" -> pending.add(a);
                case "chairman_passed", "teacher_passed", "leader_passed" -> inProgress.add(a);
                case "rejected" -> rejected.add(a);
                case "approved" -> approved.add(a);
            }
        }

        Map<Long, String> clubNames = new HashMap<>();
        Map<Long, String> userNames = new HashMap<>();
        all.forEach(a -> {
            Club club = clubMapper.selectById(a.getClubId());
            if (club != null) clubNames.put(a.getClubId(), club.getName());
            User u = userMapper.selectById(a.getApplyUser());
            if (u != null) userNames.put(a.getApplyUser(), u.getRealName());
        });

        Map<String, Object> result = new HashMap<>();
        result.put("pending", pending);
        result.put("inProgress", inProgress);
        result.put("rejected", rejected);
        result.put("approved", approved);
        result.put("clubNames", clubNames);
        result.put("userNames", userNames);
        return result;
    }

    /** 详情、列表、看板和导出使用相同的数据可见范围。 */
    private boolean canViewAll(User user) {
        return switch (user.getRole()) {
            case "chairman", "teacher", "leader", "admin" -> true;
            default -> false;
        };
    }

    private LambdaQueryWrapper<Activity> visibleQuery(User user) {
        LambdaQueryWrapper<Activity> query = new LambdaQueryWrapper<>();
        if (canViewAll(user)) {
            return query;
        }
        if (!"applicant".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "你没有权限查看活动申请");
        }
        return query.eq(Activity::getApplyUser, user.getId());
    }

    public Activity getVisibleActivity(Long id, User user) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "申请表不存在");
        }
        if (!canViewAll(user) && !("applicant".equals(user.getRole())
                && user.getId().equals(activity.getApplyUser()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "你没有权限查看这份申请");
        }
        return activity;
    }

    public List<Activity> listForExport(User user) {
        return activityMapper.selectList(visibleQuery(user).orderByDesc(Activity::getApplyTime));
    }

    /** 原申请人仍负责原社团，且申请已驳回时，才允许修改或重提。 */
    public String resubmitError(Activity activity, User user) {
        if (activity == null) return "申请表不存在";
        if (!activity.getApplyUser().equals(user.getId())) return "只有申请人本人可以重新提交";
        Club club = clubMapper.selectById(activity.getClubId());
        if (club == null || !user.getId().equals(club.getLeaderId())) {
            return "只有该社团的当前负责人可以重新提交申请";
        }
        if (!"rejected".equals(activity.getStatus())) {
            return "只有被驳回的申请可以重新提交，请刷新后重试";
        }
        return null;
    }

    /** 调用方校验修改权限后，回填原申请及预算。 */
    public ActivityForm formForResubmit(Activity activity) {
        ActivityForm form = new ActivityForm();
        form.setClubId(activity.getClubId());
        form.setOrganizer(activity.getOrganizer());
        form.setTitle(activity.getTitle());
        form.setLocation(activity.getLocation());
        form.setStartTime(activity.getStartTime());
        form.setEndTime(activity.getEndTime());
        form.setItems(budgetItemMapper.selectList(new LambdaQueryWrapper<BudgetItem>()
                .eq(BudgetItem::getActivityId, activity.getId()).orderByAsc(BudgetItem::getId)));
        return form;
    }

    /** 旧入口仍对原有内容执行完整校验。 */
    @Transactional(rollbackFor = Exception.class)
    public String resubmit(Long id, User user) {
        return resubmit(id, null, user);
    }

    /** 修改与重提在同一事务完成，保留原申请ID和审批历史。 */
    @Transactional(rollbackFor = Exception.class)
    public String resubmit(Long id, ActivityForm form, User user) {
        Activity activity = activityMapper.selectOne(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getId, id).last("FOR UPDATE"));
        String error = resubmitError(activity, user);
        if (error != null) return error;
        if (form == null) form = formForResubmit(activity);
        if (!activity.getClubId().equals(form.getClubId())) {
            return "重新提交时不能更换主办社团";
        }
        error = prepareActivity(form, user, activity);
        if (error != null) return error;

        // 显式更新可空字段，确保旧驳回原因和用户清除的可选内容真正置空。
        activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, id)
                .set(Activity::getOrganizer, activity.getOrganizer())
                .set(Activity::getTitle, activity.getTitle())
                .set(Activity::getLocation, activity.getLocation())
                .set(Activity::getStartTime, activity.getStartTime())
                .set(Activity::getEndTime, activity.getEndTime())
                .set(Activity::getTotalBudget, activity.getTotalBudget())
                .set(Activity::getTotalApply, activity.getTotalApply())
                .set(Activity::getStatus, "submitted")
                .set(Activity::getRejectReason, null)
                .set(Activity::getApplyTime, LocalDateTime.now()));
        budgetItemMapper.delete(new LambdaQueryWrapper<BudgetItem>().eq(BudgetItem::getActivityId, id));
        saveBudgetItems(id, form.getItems());
        notificationService.completeTodos(id);
        notificationService.notifyRole("chairman", "todo",
                "申请已重新提交，待审批：" + activity.getTitle(), id);
        return null;
    }

    /** 查询详情：申请表 + 明细行 + 社团名 + 申请人名 + 审批历史 + 当前用户能否审批 */
    public Map<String, Object> detail(Long id, User currentUser) {
        Activity activity = getVisibleActivity(id, currentUser);
        List<BudgetItem> items = budgetItemMapper.selectList(
                new LambdaQueryWrapper<BudgetItem>().eq(BudgetItem::getActivityId, id));
        Club club = clubMapper.selectById(activity.getClubId());
        User user = userMapper.selectById(activity.getApplyUser());
        List<ApproveLog> logs = approveLogMapper.selectList(
                new LambdaQueryWrapper<ApproveLog>()
                        .eq(ApproveLog::getActivityId, id)
                        .orderByAsc(ApproveLog::getCreateTime).orderByAsc(ApproveLog::getId));

        // 支出统计与超支预警
        List<Expense> expenses = expenseMapper.selectList(
                new LambdaQueryWrapper<Expense>()
                        .eq(Expense::getActivityId, id)
                        .orderByAsc(Expense::getExpenseTime));
        BigDecimal expenseTotal = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal applyTotal = activity.getTotalApply() == null ? BigDecimal.ZERO : activity.getTotalApply();
        BigDecimal remain = applyTotal.subtract(expenseTotal);     // 剩余可用
        boolean overBudget = expenseTotal.compareTo(applyTotal) > 0;  // 是否超支

        // 明细级执行分析：每条预算明细行 → 已关联的支出合计
        Map<Long, BigDecimal> spentMap = new HashMap<>();
        for (Expense e : expenses) {
            if (e.getBudgetItemId() != null) {
                spentMap.merge(e.getBudgetItemId(), e.getAmount(), BigDecimal::add);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("activity", activity);
        result.put("items", items);
        result.put("clubName", club != null ? club.getName() : "");
        result.put("applyUserName", user != null ? user.getRealName() : "");
        result.put("logs", logs);
        result.put("expenses", expenses);
        result.put("expenseTotal", expenseTotal);
        result.put("overBudget", overBudget);
        result.put("overAmount", expenseTotal.subtract(applyTotal));  // 超支金额（正数才提示）
        result.put("remain", remain);
        result.put("spentMap", spentMap);   // 明细行支出聚合（明细级执行对比）
        // 当前用户是否轮到审批这张表（页面据此显示审批按钮）
        result.put("canApprove", approveService.canApproveNow(activity, currentUser));
        // 申请人本人？（页面据此显示"重新提交"按钮）
        result.put("isOwner", activity.getApplyUser().equals(currentUser.getId()));
        result.put("canAddExpense", expenseService.canAdd(activity, currentUser));
        String archiveError = archiveService.archiveError(activity, currentUser);
        result.put("canArchive", archiveError == null);
        result.put("archiveError", archiveError);
        result.put("canResubmit", activity.getApplyUser().equals(currentUser.getId())
                && club != null && currentUser.getId().equals(club.getLeaderId()));
        return result;
    }
}
