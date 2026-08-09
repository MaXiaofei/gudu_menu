package com.gudu.xsd.modules.menu.together;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.mapper.MemberMapper;
import com.gudu.xsd.modules.menu.Menu;
import com.gudu.xsd.modules.menu.MenuDish;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.menu.together.mapper.MenuActivityMapper;
import com.gudu.xsd.modules.menu.together.mapper.MenuInviteMapper;
import com.gudu.xsd.modules.menu.together.mapper.MenuJoinMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 聚餐测试（V45）：邀请生成/刷新、join（guest/member）、轮询清单+心跳、
 * 加菜（dish/自定义/备注）、删菜留痕、未加入 403。
 */
@ExtendWith(MockitoExtension.class)
class TogetherServiceTest {

    @Mock MenuMapper menuMapper;
    @Mock MenuDishMapper menuDishMapper;
    @Mock DishMapper dishMapper;
    @Mock MemberMapper memberMapper;
    @Mock MenuInviteMapper inviteMapper;
    @Mock MenuJoinMapper joinMapper;
    @Mock MenuActivityMapper activityMapper;

    @InjectMocks
    private TogetherService svc;

    private Menu menu(long id, String name) {
        Menu m = new Menu();
        m.setId(id);
        m.setName(name);
        return m;
    }

    private MenuInvite invite(String code, String token) {
        MenuInvite inv = new MenuInvite();
        inv.setMenuId(7L);
        inv.setCode(code);
        inv.setToken(token);
        inv.setCreatedBy(1L);
        return inv;
    }

    private MenuJoin join(Long memberId, String guestKey, String nickname) {
        MenuJoin j = new MenuJoin();
        j.setMenuId(7L);
        j.setMemberId(memberId);
        j.setGuestKey(guestKey);
        j.setNickname(nickname);
        return j;
    }

    // ===================== 邀请 =====================

    @Test
    void 生成邀请_返回6位口令和token() {
        given(menuMapper.selectById(7L)).willReturn(menu(7L, "今晚的饭"));
        given(inviteMapper.selectOne(any())).willReturn(null);
        given(inviteMapper.insert(any())).willReturn(1);

        TogetherService.InviteVO vo = svc.invite(7L, 1L);

        assertThat(vo.code()).hasSize(6);
        assertThat(vo.token()).isNotBlank();
        ArgumentCaptor<MenuInvite> cap = ArgumentCaptor.forClass(MenuInvite.class);
        verify(inviteMapper).insert(cap.capture());
        assertThat(cap.getValue().getMenuId()).isEqualTo(7L);
        assertThat(cap.getValue().getCreatedBy()).isEqualTo(1L);
    }

    @Test
    void 刷新邀请_更新code和token不新增() {
        given(menuMapper.selectById(7L)).willReturn(menu(7L, "今晚的饭"));
        given(inviteMapper.selectOne(any())).willReturn(invite("OLD123", "old-token"));

        TogetherService.InviteVO vo = svc.invite(7L, 1L);

        verify(inviteMapper, never()).insert(any());
        verify(inviteMapper).updateById(argThat(inv ->
                inv.getCode().equals(vo.code()) && inv.getToken().equals(vo.token())));
    }

    @Test
    void 邀请_食集不存在_抛异常() {
        given(menuMapper.selectById(9L)).willReturn(null);

        assertThatThrownBy(() -> svc.invite(9L, 1L))
                .hasMessageContaining("食集不存在");
    }

    @Test
    void 邀请_未登录_抛异常() {
        assertThatThrownBy(() -> svc.invite(7L, null))
                .hasMessageContaining("登录");
    }

    // ===================== 邀请信息 =====================

    @Test
    void 邀请信息_返回食集名和菜数() {
        given(inviteMapper.selectOne(any())).willReturn(invite("ABC123", "tok"));
        given(menuMapper.selectById(7L)).willReturn(menu(7L, "今晚的饭"));
        given(menuDishMapper.selectCount(any())).willReturn(3L);

        TogetherService.InviteInfoVO vo = svc.inviteInfo("tok");

        assertThat(vo.menuId()).isEqualTo(7L);
        assertThat(vo.menuName()).isEqualTo("今晚的饭");
        assertThat(vo.dishCount()).isEqualTo(3);
        assertThat(vo.token()).isEqualTo("tok");
    }

    @Test
    void 口令进入_按code查邀请返回token和食集信息() {
        given(inviteMapper.selectOne(any())).willReturn(invite("ABC123", "tok"));
        given(menuMapper.selectById(7L)).willReturn(menu(7L, "今晚的饭"));
        given(menuDishMapper.selectCount(any())).willReturn(3L);

        TogetherService.InviteInfoVO vo = svc.inviteInfoByCode("abc123"); // 大小写不敏感

        assertThat(vo.token()).isEqualTo("tok"); // 前端用它继续 join/轮询
        assertThat(vo.menuName()).isEqualTo("今晚的饭");
    }

    @Test
    void 口令进入_口令无效_抛异常() {
        given(inviteMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> svc.inviteInfoByCode("ZZZZZZ"))
                .hasMessageContaining("口令");
    }

    @Test
    void 邀请信息_token无效_抛异常() {
        given(inviteMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> svc.inviteInfo("bad"))
                .hasMessageContaining("邀请链接");
    }

    // ===================== 加入 =====================

    @Test
    void 访客加入_首次创建guestKey() {
        given(inviteMapper.selectOne(any())).willReturn(invite("ABC123", "tok"));
        given(joinMapper.insert(any())).willAnswer(inv -> {
            ((MenuJoin) inv.getArgument(0)).setId(1L);
            return 1;
        });
        given(menuMapper.selectById(7L)).willReturn(menu(7L, "今晚的饭"));

        TogetherService.JoinVO vo = svc.join("tok", "小王", null);

        assertThat(vo.guestKey()).isNotBlank();
        ArgumentCaptor<MenuJoin> cap = ArgumentCaptor.forClass(MenuJoin.class);
        verify(joinMapper).insert(cap.capture());
        assertThat(cap.getValue().getNickname()).isEqualTo("小王");
        assertThat(cap.getValue().getMemberId()).isNull();
    }

    @Test
    void 访客加入_每次新建guestKey不按昵称复用() {
        given(inviteMapper.selectOne(any())).willReturn(invite("ABC123", "tok"));
        given(joinMapper.insert(any())).willAnswer(inv -> {
            ((MenuJoin) inv.getArgument(0)).setId(2L);
            return 1;
        });
        given(menuMapper.selectById(7L)).willReturn(menu(7L, "今晚的饭"));

        // 同昵称再来一次：也是新建（匿名昵称会重复，复用会串身份）
        TogetherService.JoinVO v1 = svc.join("tok", "食客AB1", null);
        TogetherService.JoinVO v2 = svc.join("tok", "食客AB1", null);

        assertThat(v1.guestKey()).isNotBlank();
        assertThat(v2.guestKey()).isNotBlank();
        assertThat(v1.guestKey()).isNotEqualTo(v2.guestKey());
        verify(joinMapper, org.mockito.Mockito.times(2)).insert(any());
    }

    @Test
    void 登录用户加入_按memberId建记录_昵称用账号名() {
        given(inviteMapper.selectOne(any())).willReturn(invite("ABC123", "tok"));
        given(joinMapper.selectOne(any())).willReturn(null);
        Member member = new Member();
        member.setId(5L);
        member.setName("老张");
        given(memberMapper.selectById(5L)).willReturn(member);
        given(joinMapper.insert(any())).willReturn(1);
        given(menuMapper.selectById(7L)).willReturn(menu(7L, "今晚的饭"));

        svc.join("tok", null, 5L);

        ArgumentCaptor<MenuJoin> cap = ArgumentCaptor.forClass(MenuJoin.class);
        verify(joinMapper).insert(cap.capture());
        assertThat(cap.getValue().getMemberId()).isEqualTo(5L);
        assertThat(cap.getValue().getNickname()).isEqualTo("老张");
    }

    @Test
    void 访客加入_昵称为空_抛异常() {
        given(inviteMapper.selectOne(any())).willReturn(invite("ABC123", "tok"));

        assertThatThrownBy(() -> svc.join("tok", "  ", null))
                .hasMessageContaining("昵称");
    }

    // ===================== 清单（轮询 + 心跳） =====================

    @Test
    void 清单_已加入访客_返回成员菜动态和邀请() {
        MenuJoin me = join(null, "guest-1", "小王");
        given(menuMapper.selectById(7L)).willReturn(menu(7L, "今晚的饭"));
        given(joinMapper.selectOne(any())).willReturn(me);
        given(joinMapper.selectList(any())).willReturn(List.of(me));
        MenuDish md = new MenuDish();
        md.setId(11L);
        md.setMenuId(7L);
        md.setDishId(10L);
        md.setServingFactor(BigDecimal.ONE);
        md.setAddedByNickname("小王");
        given(menuDishMapper.selectList(any())).willReturn(List.of(md));
        Dish dish = new Dish();
        dish.setId(10L);
        dish.setName("红烧肉");
        given(dishMapper.selectBatchIds(List.of(10L))).willReturn(List.of(dish));
        MenuActivity act = new MenuActivity();
        act.setNickname("小王");
        act.setAction(MenuActivity.ACTION_ADD);
        act.setDishName("红烧肉");
        given(activityMapper.selectList(any())).willReturn(List.of(act));
        given(inviteMapper.selectOne(any())).willReturn(invite("ABC123", "tok"));

        TogetherService.TogetherVO vo = svc.together(7L, TogetherService.Identity.guest("guest-1"));

        assertThat(vo.members()).hasSize(1);
        assertThat(vo.members().get(0).nickname()).isEqualTo("小王");
        assertThat(vo.dishes()).hasSize(1);
        assertThat(vo.dishes().get(0).dishName()).isEqualTo("红烧肉");
        assertThat(vo.dishes().get(0).addedByNickname()).isEqualTo("小王");
        assertThat(vo.activities()).hasSize(1);
        assertThat(vo.invite().code()).isEqualTo("ABC123");
        verify(joinMapper).updateById(me); // 心跳
    }

    @Test
    void 清单_访客未加入_抛异常() {
        given(menuMapper.selectById(7L)).willReturn(menu(7L, "今晚的饭"));
        given(joinMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> svc.together(7L, TogetherService.Identity.guest("nobody")))
                .hasMessageContaining("加入聚餐");
    }

    @Test
    void 清单_登录用户未加入_自动加入() {
        given(menuMapper.selectById(7L)).willReturn(menu(7L, "今晚的饭"));
        given(joinMapper.selectOne(any())).willReturn(null);
        Member member = new Member();
        member.setId(1L);
        member.setName("我");
        given(memberMapper.selectById(1L)).willReturn(member);
        given(joinMapper.insert(any())).willReturn(1);
        given(joinMapper.selectList(any())).willReturn(List.of());
        given(menuDishMapper.selectList(any())).willReturn(List.of());
        given(activityMapper.selectList(any())).willReturn(List.of());
        given(inviteMapper.selectOne(any())).willReturn(null);

        TogetherService.TogetherVO vo = svc.together(7L, TogetherService.Identity.member(1L));

        assertThat(vo.members()).isEmpty();
        ArgumentCaptor<MenuJoin> cap = ArgumentCaptor.forClass(MenuJoin.class);
        verify(joinMapper).insert(cap.capture());
        assertThat(cap.getValue().getMemberId()).isEqualTo(1L);
    }

    // ===================== 昵称修改 =====================

    @Test
    void 修改昵称_已加入访客_更新join昵称() {
        MenuJoin me = join(null, "guest-1", "食客AB1");
        given(joinMapper.selectOne(any())).willReturn(me);

        svc.updateNickname(7L, TogetherService.Identity.guest("guest-1"), " 小王 ");

        assertThat(me.getNickname()).isEqualTo("小王");
        verify(joinMapper).updateById(me);
    }

    @Test
    void 修改昵称_昵称为空_抛异常() {
        MenuJoin me = join(null, "guest-1", "食客AB1");
        given(joinMapper.selectOne(any())).willReturn(me);

        assertThatThrownBy(() -> svc.updateNickname(7L, TogetherService.Identity.guest("guest-1"), "  "))
                .hasMessageContaining("昵称");
        verify(joinMapper, never()).updateById(any());
    }

    @Test
    void 修改昵称_未加入_抛异常() {
        given(joinMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> svc.updateNickname(7L, TogetherService.Identity.guest("nobody"), "小王"))
                .hasMessageContaining("加入聚餐");
    }

    // ===================== 加菜 =====================

    @Test
    void 加菜_按dishId_写menuDish和活动流() {
        MenuJoin me = join(5L, null, "老张");
        given(joinMapper.selectOne(any())).willReturn(me);
        Dish dish = new Dish();
        dish.setId(10L);
        dish.setName("红烧肉");
        given(dishMapper.selectById(10L)).willReturn(dish);
        given(menuDishMapper.insert(any())).willAnswer(inv -> {
            ((MenuDish) inv.getArgument(0)).setId(21L);
            return 1;
        });
        given(activityMapper.insert(any())).willReturn(1);

        Long id = svc.addItem(7L, TogetherService.Identity.member(5L), 10L, null, "少放辣");

        assertThat(id).isEqualTo(21L);
        ArgumentCaptor<MenuDish> mdCap = ArgumentCaptor.forClass(MenuDish.class);
        verify(menuDishMapper).insert(mdCap.capture());
        MenuDish saved = mdCap.getValue();
        assertThat(saved.getDishId()).isEqualTo(10L);
        assertThat(saved.getAddedByMemberId()).isEqualTo(5L);
        assertThat(saved.getAddedByNickname()).isEqualTo("老张");
        assertThat(saved.getNote()).isEqualTo("少放辣");
        assertThat(saved.getServingFactor()).isEqualByComparingTo("1");
        ArgumentCaptor<MenuActivity> actCap = ArgumentCaptor.forClass(MenuActivity.class);
        verify(activityMapper).insert(actCap.capture());
        assertThat(actCap.getValue().getAction()).isEqualTo(MenuActivity.ACTION_ADD);
        assertThat(actCap.getValue().getDishName()).isEqualTo("红烧肉");
    }

    @Test
    void 加菜_自定义菜名_dishId为空存customName() {
        MenuJoin me = join(null, "guest-1", "小王");
        given(joinMapper.selectOne(any())).willReturn(me);
        given(menuDishMapper.insert(any())).willReturn(1);
        given(activityMapper.insert(any())).willReturn(1);

        svc.addItem(7L, TogetherService.Identity.guest("guest-1"), null, "  糖拌西红柿 ", null);

        ArgumentCaptor<MenuDish> mdCap = ArgumentCaptor.forClass(MenuDish.class);
        verify(menuDishMapper).insert(mdCap.capture());
        MenuDish saved = mdCap.getValue();
        assertThat(saved.getDishId()).isNull();
        assertThat(saved.getCustomName()).isEqualTo("糖拌西红柿");
        assertThat(saved.getAddedByNickname()).isEqualTo("小王");
    }

    @Test
    void 加菜_菜名和菜品都空_抛异常() {
        MenuJoin me = join(null, "guest-1", "小王");
        given(joinMapper.selectOne(any())).willReturn(me);

        assertThatThrownBy(() -> svc.addItem(7L, TogetherService.Identity.guest("guest-1"), null, "  ", null))
                .hasMessageContaining("选择菜或输入菜名");
        verify(menuDishMapper, never()).insert(any());
    }

    @Test
    void 加菜_未加入_抛异常() {
        given(joinMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> svc.addItem(7L, TogetherService.Identity.guest("nobody"), 10L, null, null))
                .hasMessageContaining("加入聚餐");
    }

    // ===================== 删菜 =====================

    @Test
    void 删菜_删除menuDish并记remove活动流() {
        MenuJoin me = join(null, "guest-1", "小王");
        given(joinMapper.selectOne(any())).willReturn(me);
        MenuDish md = new MenuDish();
        md.setId(21L);
        md.setMenuId(7L);
        md.setDishId(10L);
        given(menuDishMapper.selectById(21L)).willReturn(md);
        Dish dish = new Dish();
        dish.setId(10L);
        dish.setName("红烧肉");
        given(dishMapper.selectById(10L)).willReturn(dish);
        given(menuDishMapper.deleteById(21L)).willReturn(1);
        given(activityMapper.insert(any())).willReturn(1);

        svc.removeItem(7L, TogetherService.Identity.guest("guest-1"), 21L);

        verify(menuDishMapper).deleteById(21L);
        ArgumentCaptor<MenuActivity> actCap = ArgumentCaptor.forClass(MenuActivity.class);
        verify(activityMapper).insert(actCap.capture());
        assertThat(actCap.getValue().getAction()).isEqualTo(MenuActivity.ACTION_REMOVE);
        assertThat(actCap.getValue().getNickname()).isEqualTo("小王");
        assertThat(actCap.getValue().getDishName()).isEqualTo("红烧肉");
    }

    @Test
    void 删菜_不属于该食集_抛异常() {
        MenuJoin me = join(null, "guest-1", "小王");
        given(joinMapper.selectOne(any())).willReturn(me);
        MenuDish md = new MenuDish();
        md.setId(99L);
        md.setMenuId(8L); // 别的食集
        given(menuDishMapper.selectById(99L)).willReturn(md);

        assertThatThrownBy(() -> svc.removeItem(7L, TogetherService.Identity.guest("guest-1"), 99L))
                .hasMessageContaining("不在这个食集");
        verify(menuDishMapper, never()).deleteById(anyLong());
        verify(activityMapper, never()).insert(any());
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
