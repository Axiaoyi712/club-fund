package com.clubfund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clubfund.entity.Club;
import com.clubfund.entity.ClubMember;
import com.clubfund.entity.User;
import com.clubfund.mapper.ActivityMapper;
import com.clubfund.mapper.ClubMapper;
import com.clubfund.mapper.ClubMemberMapper;
import com.clubfund.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 社团业务：列表、新增、编辑、删除。
 * 权限规则：
 * - 新增：任何人可以建社团，创建人自动成为负责人
 * - 编辑/删除：只有管理员 admin 或该社团负责人可以
 */
@Service
public class ClubService {

    private final ClubMapper clubMapper;
    private final UserMapper userMapper;
    private final ActivityMapper activityMapper;
    private final ClubMemberMapper clubMemberMapper;

    public ClubService(ClubMapper clubMapper, UserMapper userMapper, ActivityMapper activityMapper, ClubMemberMapper clubMemberMapper) {
        this.clubMapper = clubMapper;
        this.userMapper = userMapper;
        this.activityMapper = activityMapper;
        this.clubMemberMapper = clubMemberMapper;
    }

    /** 查询全部社团 */
    public List<Club> listAll() {
        return clubMapper.selectList(
                new LambdaQueryWrapper<Club>().orderByDesc(Club::getCreateTime));
    }

    /** 申请表只提供当前账号负责的社团。 */
    public List<Club> listLedBy(Long userId) {
        return clubMapper.selectList(new LambdaQueryWrapper<Club>()
                .eq(Club::getLeaderId, userId).orderByDesc(Club::getCreateTime));
    }

    public boolean hasLedClub(Long userId) {
        return clubMapper.selectCount(new LambdaQueryWrapper<Club>()
                .eq(Club::getLeaderId, userId)) > 0;
    }

    /**
     * 把社团列表和负责人姓名拼在一起返回。
     * 返回 Map 方便页面同时拿到 社团列表 + 负责人姓名映射。
     */
    public Map<String, Object> listWithLeader() {
        List<Club> clubs = listAll();
        // 收集所有负责人的id
        List<Long> leaderIds = clubs.stream().map(Club::getLeaderId).distinct().toList();
        // 查出负责人信息
        Map<Long, String> leaderNameMap = new HashMap<>();
        if (!leaderIds.isEmpty()) {
            userMapper.selectBatchIds(leaderIds)
                    .forEach(u -> leaderNameMap.put(u.getId(), u.getRealName()));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("clubs", clubs);
        result.put("leaderNames", leaderNameMap);
        return result;
    }

    /** 新增社团：创建人自动成为负责人 */
    public String add(Club club, User currentUser) {
        String error = validate(club);
        if (error != null) return error;
        club.setLeaderId(currentUser.getId());
        club.setCreateTime(LocalDateTime.now());
        clubMapper.insert(club);
        return null;
    }

    /** 按id查社团 */
    public Club getById(Long id) {
        return clubMapper.selectById(id);
    }

    /** 编辑社团（名称、简介可改，负责人不变） */
    public String update(Club club) {
        String error = validate(club);
        if (error != null) return error;
        clubMapper.updateById(club);
        return null;
    }

    private String validate(Club club) {
        if (club.getName() == null || club.getName().isBlank()) {
            return "请填写社团名称";
        }
        if (club.getName().length() > 100) {
            return "社团名称不能超过100个字符";
        }
        if (club.getDescription() != null && club.getDescription().length() > 500) {
            return "社团简介不能超过500个字符";
        }
        return null;
    }

    /**
     * 删除社团及其成员记录，事务内任一步骤失败时全部回滚。
     * 返回 null 表示成功，返回字符串表示失败原因。
     * 保护规则：该社团已有活动申请时禁止删除（避免申请数据失去主办单位）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String delete(Long id) {
        // 先锁定社团，再检查关联申请，避免检查后有新申请写入。
        if (clubMapper.selectOne(new LambdaQueryWrapper<Club>()
                .eq(Club::getId, id).last("FOR UPDATE")) == null) {
            return "社团不存在或已被删除，请刷新后重试";
        }
        // 检查是否有关联申请
        Long count = activityMapper.selectCount(
                new LambdaQueryWrapper<com.clubfund.entity.Activity>().eq(com.clubfund.entity.Activity::getClubId, id));
        if (count != null && count > 0) {
            return "该社团已有活动申请，不能删除（可编辑或保留）";
        }
        clubMemberMapper.delete(new LambdaQueryWrapper<ClubMember>()
                .eq(ClubMember::getClubId, id));
        clubMapper.deleteById(id);
        return null;
    }

    /**
     * 判断当前用户能不能操作这个社团：
     * 管理员可以，团委老师、学院领导可以，社团负责人本人可以。
     */
    public boolean canManage(Club club, User user) {
        String role = user.getRole();
        if ("admin".equals(role) || "teacher".equals(role) || "leader".equals(role)) {
            return true;
        }
        return club.getLeaderId() != null && club.getLeaderId().equals(user.getId());
    }
}
