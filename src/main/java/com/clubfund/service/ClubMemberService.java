package com.clubfund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clubfund.entity.Club;
import com.clubfund.entity.ClubMember;
import com.clubfund.entity.User;
import com.clubfund.mapper.ClubMapper;
import com.clubfund.mapper.ClubMemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 社团成员管理业务：添加/移除/查询成员名单。
 */
@Service
public class ClubMemberService {

    private final ClubMemberMapper clubMemberMapper;
    private final ClubMapper clubMapper;
    private final ClubService clubService;

    public ClubMemberService(ClubMemberMapper clubMemberMapper, ClubMapper clubMapper, ClubService clubService) {
        this.clubMemberMapper = clubMemberMapper;
        this.clubMapper = clubMapper;
        this.clubService = clubService;
    }

    /** 查询某社团成员列表 */
    public List<ClubMember> listByClub(Long clubId) {
        return clubMemberMapper.selectList(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getClubId, clubId)
                        .orderByAsc(ClubMember::getId));
    }

    /** 添加成员。返回 null 成功，返回字符串失败原因 */
    @Transactional(rollbackFor = Exception.class)
    public String addMember(Long clubId, String memberName) {
        if (clubId == null || memberName == null || memberName.isBlank()) {
            return "请输入成员姓名";
        }
        if (memberName.codePointCount(0, memberName.length()) > 50) {
            return "成员姓名最多50字，请修改后添加";
        }
        // 同一社团的添加请求先锁定社团，再核对重名并写入，避免并发重复。
        if (clubMapper.selectOne(new LambdaQueryWrapper<Club>()
                .eq(Club::getId, clubId).last("FOR UPDATE")) == null) {
            return "社团不存在";
        }
        long same = clubMemberMapper.selectCount(
                new LambdaQueryWrapper<ClubMember>()
                        .eq(ClubMember::getClubId, clubId)
                        .eq(ClubMember::getMemberName, memberName.trim()));
        if (same > 0) {
            return "该成员已在社团名单中";
        }
        ClubMember m = new ClubMember();
        m.setClubId(clubId);
        m.setMemberName(memberName.trim());
        m.setJoinTime(LocalDateTime.now());
        clubMemberMapper.insert(m);
        return null;
    }

    /** 按成员实际所属社团校验权限，再限定社团范围移除。 */
    public String removeMember(Long memberId, Long clubId, User user) {
        ClubMember member = clubMemberMapper.selectById(memberId);
        if (member == null) {
            return "成员记录不存在";
        }
        if (!member.getClubId().equals(clubId)) {
            return "成员不属于当前社团，无法移除";
        }
        Club club = clubMapper.selectById(member.getClubId());
        if (club == null || !clubService.canManage(club, user)) {
            return "你没有权限操作这个社团";
        }
        int removed = clubMemberMapper.delete(new LambdaQueryWrapper<ClubMember>()
                .eq(ClubMember::getId, memberId).eq(ClubMember::getClubId, clubId));
        if (removed == 0) {
            return "成员记录不存在或已变更，请刷新后重试";
        }
        return null;
    }
}
