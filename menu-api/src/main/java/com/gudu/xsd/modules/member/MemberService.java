package com.gudu.xsd.modules.member;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.member.mapper.MemberMapper;
import org.springframework.stereotype.Service;

@Service
public class MemberService extends ServiceImpl<MemberMapper, Member> {

    /**
     * 家庭成员（App 侧「家里几口人」、营养报告成员、Agent 画像、餐程默认就餐成员用）：
     * 当前登录成员本人 + 其它「成员档案」——排除平台超管账号、E2E 测试成员（名称 E2E 前缀）、厨房圈演示作者（cm_ 账号）、
     * 没有健康档案的纯登录账号（如未建档的微信登录用户）。多家庭（multi-tenant-family）落地后改按 familyId。
     * 按 id 升序（建档顺序）。
     */
    public java.util.List<Member> household(Long currentMemberId) {
        return list(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Member>().orderByAsc("id"))
                .stream().filter(m -> isHouseholdMember(m, currentMemberId)).toList();
    }

    /** 厨房圈演示作者（scripts/seed/community.mjs 建的社区用户，不属于任何家庭）。 */
    public static final String COMMUNITY_DEMO_PHONE_PREFIX = "cm_";

    public static boolean isHouseholdMember(Member m, Long currentMemberId) {
        if (m == null || m.getId() == null) return false;
        if (m.getId().equals(currentMemberId)) return true;
        if (AdminChecker.isAdmin(m)) return false;
        if (m.getName() != null && m.getName().startsWith("E2E")) return false;
        if (m.getPhone() != null && m.getPhone().startsWith(COMMUNITY_DEMO_PHONE_PREFIX)) return false;
        return m.getHealthProfile() != null;
    }

    /** 分页查（后台管理）。按创建时间倒序。 */
    public IPage<Member> page(PageQuery q) {
        return page(new Page<>(q.getPageNum(), q.getPageSize()),
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Member>()
                        .orderByDesc("create_time"));
    }
}
