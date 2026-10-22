package com.clubfund.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clubfund.entity.Activity;
import com.clubfund.entity.ApproveLog;
import com.clubfund.entity.BudgetItem;
import com.clubfund.entity.Club;
import com.clubfund.entity.Expense;
import com.clubfund.entity.User;
import com.clubfund.mapper.ApproveLogMapper;
import com.clubfund.mapper.BudgetItemMapper;
import com.clubfund.mapper.ClubMapper;
import com.clubfund.mapper.ExpenseMapper;
import com.clubfund.mapper.UserMapper;
import com.clubfund.service.ActivityService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Excel 导出控制器：用 Apache POI 生成 .xlsx。
 * - 单份申请表导出（基本信息 + 预算明细 + 审批记录）
 * - 全部申请表列表导出（台账样式）
 */
@Controller
@RequestMapping("/export")
public class ExportController {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ActivityService activityService;
    private final BudgetItemMapper budgetItemMapper;
    private final ApproveLogMapper approveLogMapper;
    private final ClubMapper clubMapper;
    private final UserMapper userMapper;
    private final ExpenseMapper expenseMapper;

    public ExportController(ActivityService activityService, BudgetItemMapper budgetItemMapper,
                            ApproveLogMapper approveLogMapper, ClubMapper clubMapper,
                            UserMapper userMapper, ExpenseMapper expenseMapper) {
        this.activityService = activityService;
        this.budgetItemMapper = budgetItemMapper;
        this.approveLogMapper = approveLogMapper;
        this.clubMapper = clubMapper;
        this.userMapper = userMapper;
        this.expenseMapper = expenseMapper;
    }

    /** 导出单份申请表：标题 + 基本信息 + 预算明细 + 支出执行 + 审批记录 */
    @GetMapping("/activity/{id}")
    public void exportOne(@PathVariable Long id, HttpSession session, HttpServletResponse response) throws IOException {
        User user = (User) session.getAttribute("loginUser");
        Activity activity = activityService.getVisibleActivity(id, user);
        Club club = clubMapper.selectById(activity.getClubId());
        List<BudgetItem> items = budgetItemMapper.selectList(
                new LambdaQueryWrapper<BudgetItem>().eq(BudgetItem::getActivityId, id));
        List<ApproveLog> logs = approveLogMapper.selectList(
                new LambdaQueryWrapper<ApproveLog>().eq(ApproveLog::getActivityId, id));
        List<Expense> expenses = expenseMapper.selectList(
                new LambdaQueryWrapper<Expense>().eq(Expense::getActivityId, id));

        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle head = wb.createCellStyle();
            head.setFont(boldFont(wb));
            CellStyle headFill = wb.createCellStyle();
            headFill.setFont(boldFont(wb));
            headFill.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            headFill.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Sheet1 基本信息
            Sheet info = wb.createSheet("申请表");
            Row r0 = info.createRow(0);
            r0.createCell(0).setCellValue("社团活动经费申请表");
            r0.getCell(0).setCellStyle(head);
            String[][] basic = {
                    {"活动内容", activity.getTitle()},
                    {"主办社团", club == null ? "—" : club.getName()},
                    {"承办/协办单位", orDash(activity.getOrganizer())},
                    {"活动地点", orDash(activity.getLocation())},
                    {"活动时间", fmt(activity.getStartTime()) + " ~ " + fmt(activity.getEndTime())},
                    {"申请人", orDash(activity.getApplyUser() == null ? null : userMapper.selectById(activity.getApplyUser()).getRealName())},
                    {"首次提交时间", activity.getFirstApplyTime() == null ? "未记录" : fmt(activity.getFirstApplyTime())},
                    {"最新提交时间", activity.getApplyTime() == null ? "未记录" : fmt(activity.getApplyTime())},
                    {"状态", statusName(activity.getStatus())},
                    {"预算合计", "¥" + activity.getTotalBudget()},
                    {"申请金额", "¥" + activity.getTotalApply()},
            };
            int rowIdx = 2;
            for (String[] pair : basic) {
                Row row = info.createRow(rowIdx++);
                Cell c0 = row.createCell(0);
                c0.setCellValue(pair[0]);
                c0.setCellStyle(headFill);
                row.createCell(1).setCellValue(pair[1]);
            }

            // Sheet2 预算明细
            Sheet bud = wb.createSheet("预算明细");
            Row bHead = bud.createRow(0);
            String[] cols = {"序号", "开支项目", "数量", "单价(元)", "预算金额", "申请金额"};
            for (int i = 0; i < cols.length; i++) {
                Cell c = bHead.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headFill);
            }
            int br = 1;
            int seq = 1;
            for (BudgetItem it : items) {
                Row row = bud.createRow(br++);
                row.createCell(0).setCellValue(seq++);
                row.createCell(1).setCellValue(it.getItemName());
                row.createCell(2).setCellValue(it.getQuantity());
                row.createCell(3).setCellValue(it.getUnitPrice().doubleValue());
                row.createCell(4).setCellValue(it.getBudgetAmount() == null ? 0 : it.getBudgetAmount().doubleValue());
                row.createCell(5).setCellValue(it.getApplyAmount() == null ? 0 : it.getApplyAmount().doubleValue());
            }
            Row bTotal = bud.createRow(br);
            bTotal.createCell(4).setCellValue(activity.getTotalBudget() == null ? 0 : activity.getTotalBudget().doubleValue());
            bTotal.createCell(5).setCellValue(activity.getTotalApply() == null ? 0 : activity.getTotalApply().doubleValue());

            // Sheet3 支出执行
            Sheet exp = wb.createSheet("支出执行");
            Row eHead = exp.createRow(0);
            String[] eCols = {"支出项目", "金额(元)", "实际支出日期", "登记时间", "备注"};
            for (int i = 0; i < eCols.length; i++) {
                Cell c = eHead.createCell(i);
                c.setCellValue(eCols[i]);
                c.setCellStyle(headFill);
            }
            int er = 1;
            BigDecimal expTotal = BigDecimal.ZERO;
            for (Expense e : expenses) {
                Row row = exp.createRow(er++);
                row.createCell(0).setCellValue(e.getItemName());
                row.createCell(1).setCellValue(e.getAmount().doubleValue());
                row.createCell(2).setCellValue(e.getOccurredDate() == null ? "未记录" : e.getOccurredDate().toString());
                row.createCell(3).setCellValue(fmt(e.getExpenseTime()));
                row.createCell(4).setCellValue(orDash(e.getNote()));
                expTotal = expTotal.add(e.getAmount());
            }
            Row eTotal = exp.createRow(er);
            eTotal.createCell(0).setCellValue("支出合计");
            eTotal.createCell(1).setCellValue(expTotal.doubleValue());

            exp.setColumnWidth(2, 16 * 256);
            exp.setColumnWidth(3, 22 * 256);
            exp.setColumnWidth(4, 40 * 256);

            // Sheet4 审批记录
            Sheet lg = wb.createSheet("审批记录");
            Row lHead = lg.createRow(0);
            String[] lCols = {"审批级", "结果", "审批人", "意见", "时间"};
            for (int i = 0; i < lCols.length; i++) {
                Cell c = lHead.createCell(i);
                c.setCellValue(lCols[i]);
                c.setCellStyle(headFill);
            }
            int lr = 1;
            for (ApproveLog log : logs) {
                Row row = lg.createRow(lr++);
                row.createCell(0).setCellValue(stageName(log.getStage()));
                row.createCell(1).setCellValue("pass".equals(log.getAction()) ? "通过" : "驳回");
                String operatorName = orDash(log.getOperatorName());
                if (log.getOperatorId() != null && log.getOperatorId().equals(activity.getApplyUser())) {
                    operatorName += "（本人审批）";
                }
                row.createCell(2).setCellValue(operatorName);
                row.createCell(3).setCellValue(orDash(log.getComment()));
                row.createCell(4).setCellValue(fmt(log.getCreateTime()));
            }

            lg.setColumnWidth(2, 30 * 256);

            for (int i = 0; i < 4; i++) {
                wb.getSheetAt(i).setColumnWidth(0, 14 * 256);
                wb.getSheetAt(i).setColumnWidth(1, 26 * 256);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String fileName = "活动经费申请表_" + activity.getId() + ".xlsx";
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + new String(fileName.getBytes("UTF-8"), "ISO-8859-1"));
            wb.write(response.getOutputStream());
        }
    }

    /** 申请人导出本人申请，审批角色和管理员导出全部申请。 */
    @GetMapping("/activities")
    public void exportAll(HttpSession session, HttpServletResponse response) throws IOException {
        User user = (User) session.getAttribute("loginUser");
        boolean mine = "applicant".equals(user.getRole());
        List<Activity> list = activityService.listForExport(user);
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle headFill = wb.createCellStyle();
            headFill.setFont(boldFont(wb));
            headFill.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            headFill.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet sh = wb.createSheet(mine ? "我的申请" : "全部申请");
            String[] cols = {"申请编号", "活动内容", "主办社团", "申请人", "预算合计", "申请金额", "状态", "首次提交时间", "最新提交时间"};
            Row head = sh.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = head.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headFill);
            }
            int r = 1;
            for (Activity a : list) {
                Row row = sh.createRow(r++);
                row.createCell(0).setCellValue(a.getId());
                row.createCell(1).setCellValue(a.getTitle());
                Club club = clubMapper.selectById(a.getClubId());
                row.createCell(2).setCellValue(club == null ? "—" : club.getName());
                User u = a.getApplyUser() == null ? null : userMapper.selectById(a.getApplyUser());
                row.createCell(3).setCellValue(u == null ? "—" : u.getRealName());
                row.createCell(4).setCellValue(a.getTotalBudget() == null ? 0 : a.getTotalBudget().doubleValue());
                row.createCell(5).setCellValue(a.getTotalApply() == null ? 0 : a.getTotalApply().doubleValue());
                row.createCell(6).setCellValue(statusName(a.getStatus()));
                row.createCell(7).setCellValue(a.getFirstApplyTime() == null ? "未记录" : fmt(a.getFirstApplyTime()));
                row.createCell(8).setCellValue(a.getApplyTime() == null ? "未记录" : fmt(a.getApplyTime()));
            }
            for (int i = 0; i < cols.length; i++) {
                sh.setColumnWidth(i, 16 * 256);
            }
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + (mine ? "my.xlsx" : "all.xlsx"));
            wb.write(response.getOutputStream());
        }
    }

    private Font boldFont(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        return font;
    }

    private String statusName(String status) {
        if (status == null) return "—";
        return switch (status) {
            case "submitted" -> "待审批";
            case "chairman_passed" -> "主席团已通过";
            case "teacher_passed" -> "团委老师已通过";
            case "approved" -> "已获批";
            case "rejected" -> "已驳回";
            default -> status;
        };
    }

    /** 审批级代码转中文 */
    private String stageName(String stage) {
        if (stage == null) return "—";
        return switch (stage) {
            case "chairman" -> "主席团";
            case "teacher" -> "团委老师";
            case "leader" -> "学院领导";
            default -> stage;
        };
    }

    private String fmt(java.time.LocalDateTime t) {
        return t == null ? "—" : t.format(DTF);
    }

    private String orDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
