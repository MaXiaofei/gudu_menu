package com.gudu.xsd.modules.member;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.member.mapper.MemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 成员服务单元测试：mock MemberMapper，验证 page 分页（按创建时间倒序）。
 * 范式照 DishServiceTest：手动 mock + 反射注入 baseMapper。
 */
class MemberServiceTest {

    private MemberMapper memberMapper;
    private MemberService svc;

    @BeforeEach
    void setUp() {
        memberMapper = Mockito.mock(MemberMapper.class);
        svc = new MemberService();
        injectBaseMapper(svc, memberMapper);
    }

    private static void injectBaseMapper(MemberService svc, MemberMapper mapper) {
        try {
            var f = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
            f.setAccessible(true);
            f.set(svc, mapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Member member(Long id, String name) {
        Member m = new Member();
        m.setId(id);
        m.setName(name);
        return m;
    }

    /** page：返回分页结果，total/records 正确。 */
    @Test
    void page_返回分页结果() {
        Page<Member> mp = new Page<>(1, 10, 2L);
        mp.setRecords(List.of(member(1L, "张爸爸"), member(2L, "张妈妈")));
        when(memberMapper.selectPage(any(IPage.class), any())).thenReturn(mp);

        PageQuery q = new PageQuery();
        q.setPageNum(1);
        q.setPageSize(10);
        IPage<Member> page = svc.page(q);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(Member::getName)
                .containsExactly("张爸爸", "张妈妈");
    }

    /** page：验证透传的分页参数（pageNum=2 / pageSize=5）。 */
    @Test
    void page_分页参数透传正确() {
        Page<Member> mp = new Page<>(2, 5, 0L);
        mp.setRecords(List.of());
        when(memberMapper.selectPage(any(IPage.class), any())).thenReturn(mp);

        PageQuery q = new PageQuery();
        q.setPageNum(2);
        q.setPageSize(5);
        svc.page(q);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<Member>> captor = ArgumentCaptor.forClass(Page.class);
        Mockito.verify(memberMapper).selectPage(captor.capture(), any());
        assertThat(captor.getValue().getCurrent()).isEqualTo(2);
        assertThat(captor.getValue().getSize()).isEqualTo(5);
    }
}
