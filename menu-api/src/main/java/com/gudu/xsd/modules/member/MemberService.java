package com.gudu.xsd.modules.member;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.member.mapper.MemberMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 成员档案（member 表 = 家庭域表）。multi-tenant-family 起 member 由租户插件自动按当前家庭过滤：
 * 本服务的全部查询天然只看到「本家」成员档案，跨家庭 id 查不到（getById 返回 null）。
 */
@Service
public class MemberService extends ServiceImpl<MemberMapper, Member> {

    /**
     * 本家成员档案（App「家里几口人」、营养报告成员、Agent 画像、餐程默认就餐成员用），按建档顺序。
     * 旧版靠「排除超管 / E2E / 演示作者 / 无健康档案」启发式从全表里猜家庭成员（审计 #1：泄露全部用户），
     * 现在由家庭隔离保证，参数仅为兼容旧签名保留。
     */
    public List<Member> household(Long currentMemberId) {
        return list(new QueryWrapper<Member>().orderByAsc("id"));
    }

    /** 本家分页（按创建时间倒序）。 */
    public IPage<Member> page(PageQuery q) {
        return page(new Page<>(q.getPageNum(), q.getPageSize()),
                new QueryWrapper<Member>().orderByDesc("create_time"));
    }
}
