package com.gudu.xsd.modules.menu.together;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import com.gudu.xsd.modules.menu.Menu;
import com.gudu.xsd.modules.menu.MenuDish;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.menu.together.mapper.MenuActivityMapper;
import com.gudu.xsd.modules.menu.together.mapper.MenuInviteMapper;
import com.gudu.xsd.modules.menu.together.mapper.MenuJoinMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 聚餐邀请凭证（multi-tenant-family E.3）：非本家用户加入聚餐必须持有有效凭证（token / 口令，
 * 未过期、未撤销、属于该食集）；本家成员免凭证；已加入者凭加入记录只能访问这一个食集的聚餐范围。
 * 食集属于家庭 5；调用者上下文是家庭 9 的账号 100（外人）或家庭 5 的账号 200（本家）。
 */
@ExtendWith(MockitoExtension.class)
class TogetherInviteCredentialTest {

    static final long MENU = 7L;
    static final long OTHER_MENU = 8L;

    @Mock MenuMapper menuMapper;
    @Mock MenuDishMapper menuDishMapper;
    @Mock DishService dishService;
    @Mock AccountQueryApi accountQuery;
    @Mock MenuInviteMapper inviteMapper;
    @Mock MenuJoinMapper joinMapper;
    @Mock MenuActivityMapper activityMapper;

    @InjectMocks
    private TogetherService svc;

    @BeforeEach
    void setUp() {
        lenient().when(menuMapper.selectById(MENU)).thenReturn(menu(MENU, 5L));
        lenient().when(menuMapper.selectById(OTHER_MENU)).thenReturn(menu(OTHER_MENU, 5L));
        lenient().when(accountQuery.nicknames(any())).thenReturn(Map.of(100L, "外人", 200L, "本家"));
        lenient().when(joinMapper.selectList(any())).thenReturn(List.of());
        lenient().when(menuDishMapper.selectList(any())).thenReturn(List.of());
        lenient().when(activityMapper.selectList(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static Menu menu(long id, long familyId) {
        Menu m = new Menu();
        m.setId(id);
        m.setName("食集" + id);
        m.setFamilyId(familyId);
        return m;
    }

    private static MenuInvite invite(long menuId, String token, LocalDateTime expiresAt, int revoked) {
        MenuInvite inv = new MenuInvite();
        inv.setMenuId(menuId);
        inv.setFamilyId(5L);
        inv.setCode("ABC234");
        inv.setToken(token);
        inv.setCreatedBy(200L);
        inv.setExpiresAt(expiresAt);
        inv.setRevoked(revoked);
        return inv;
    }

    private static void asOutsider() {
        TenantContextHolder.set(new TenantContext(9L, 100L, 1L, TenantContext.OWNER));
    }

    private static void asFamilyMember() {
        TenantContextHolder.set(new TenantContext(5L, 200L, 2L, TenantContext.MEMBER));
    }

    // ===================== 加入：凭证校验 =====================

    @Test
    void 无凭证_加入被拒() {
        asOutsider();
        assertThatThrownBy(() -> svc.join(null, "x", 100L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> svc.join("  ", "x", 100L)).isInstanceOf(BizException.class);
        verify(joinMapper, never()).insert(any(MenuJoin.class));
    }

    @Test
    void 凭证不存在_加入被拒() {
        asOutsider();
        given(inviteMapper.selectOne(any())).willReturn(null);
        assertThatThrownBy(() -> svc.join("forged", "x", 100L)).hasMessageContaining("无效");
        verify(joinMapper, never()).insert(any(MenuJoin.class));
    }

    @Test
    void 凭证过期_加入_查看_口令均被拒() {
        asOutsider();
        MenuInvite expired = invite(MENU, "tok", LocalDateTime.now().minusMinutes(1), 0);
        given(inviteMapper.selectOne(any())).willReturn(expired);

        assertThatThrownBy(() -> svc.join("tok", "x", 100L)).hasMessageContaining("失效");
        assertThatThrownBy(() -> svc.inviteInfo("tok")).hasMessageContaining("失效");
        assertThatThrownBy(() -> svc.inviteInfoByCode("abc234")).hasMessageContaining("失效");
        verify(joinMapper, never()).insert(any(MenuJoin.class));
    }

    @Test
    void 凭证无有效期记录_按过期处理() {
        asOutsider();
        given(inviteMapper.selectOne(any())).willReturn(invite(MENU, "tok", null, 0));
        assertThatThrownBy(() -> svc.join("tok", "x", 100L)).hasMessageContaining("失效");
    }

    @Test
    void 凭证已撤销_加入_查看_口令均被拒() {
        asOutsider();
        MenuInvite revoked = invite(MENU, "tok", LocalDateTime.now().plusDays(3), 1);
        given(inviteMapper.selectOne(any())).willReturn(revoked);

        assertThatThrownBy(() -> svc.join("tok", "x", 100L)).hasMessageContaining("失效");
        assertThatThrownBy(() -> svc.inviteInfo("tok")).hasMessageContaining("失效");
        assertThatThrownBy(() -> svc.inviteInfoByCode("ABC234")).hasMessageContaining("失效");
        verify(joinMapper, never()).insert(any(MenuJoin.class));
    }

    @Test
    void 有效凭证_加入只落到凭证所属食集() {
        asOutsider();
        given(inviteMapper.selectOne(any())).willReturn(
                invite(OTHER_MENU, "tokB", LocalDateTime.now().plusDays(3), 0));
        given(joinMapper.selectOne(any())).willReturn(null);
        given(joinMapper.insert(any(MenuJoin.class))).willReturn(1);

        TogetherService.JoinVO vo = svc.join("tokB", "", 100L);

        assertThat(vo.menuId()).isEqualTo(OTHER_MENU);
        ArgumentCaptor<MenuJoin> cap = ArgumentCaptor.forClass(MenuJoin.class);
        verify(joinMapper).insert(cap.capture());
        assertThat(cap.getValue().getMenuId()).isEqualTo(OTHER_MENU);
        assertThat(cap.getValue().getMemberId()).isEqualTo(100L);
    }

    // ===================== 访问：凭别的菜单的凭证不行 =====================

    @Test
    void 凭证属于别的菜单_不能访问这个菜单() {
        asOutsider();
        // 只加入了 OTHER_MENU（对 MENU 的 join 记录查询为 0）
        given(joinMapper.selectCount(any())).willReturn(0L);
        TogetherService.Identity me = TogetherService.Identity.member(100L);

        assertThatThrownBy(() -> svc.together(MENU, me))
                .isInstanceOf(BizException.class).hasMessageContaining("不存在");
        assertThatThrownBy(() -> svc.addItem(MENU, me, null, "偷塞的菜", null)).hasMessageContaining("不存在");
        assertThatThrownBy(() -> svc.removeItem(MENU, me, 1L)).hasMessageContaining("不存在");
        assertThatThrownBy(() -> svc.updateNickname(MENU, me, "改名")).hasMessageContaining("不存在");
        assertThatThrownBy(() -> svc.searchDishes(MENU, me, "番茄", 8)).hasMessageContaining("不存在");
        verify(menuDishMapper, never()).insert(any(MenuDish.class));
        verify(joinMapper, never()).insert(any(MenuJoin.class));
    }

    @Test
    void 外人未加入_不会被自动加入() {
        asOutsider();
        given(joinMapper.selectCount(any())).willReturn(0L);
        assertThatThrownBy(() -> svc.together(MENU, TogetherService.Identity.member(100L)))
                .hasMessageContaining("不存在");
        verify(joinMapper, never()).insert(any(MenuJoin.class));
    }

    @Test
    void 访客无加入记录_不能访问() {
        asOutsider();
        given(joinMapper.selectCount(any())).willReturn(0L);
        assertThatThrownBy(() -> svc.together(MENU, TogetherService.Identity.guest("forged-key")))
                .hasMessageContaining("不存在");
    }

    @Test
    void 食集不存在_404() {
        asOutsider();
        assertThatThrownBy(() -> svc.together(999L, TogetherService.Identity.member(100L)))
                .hasMessageContaining("不存在");
    }

    // ===================== 通过路径 =====================

    @Test
    void 同家庭成员_无凭证直接通过并自动加入() {
        asFamilyMember();
        given(joinMapper.selectOne(any())).willReturn(null);
        given(joinMapper.insert(any(MenuJoin.class))).willReturn(1);
        given(inviteMapper.selectOne(any())).willReturn(null);

        TogetherService.TogetherVO vo = svc.together(MENU, TogetherService.Identity.member(200L));

        assertThat(vo).isNotNull();
        verify(joinMapper, never()).selectCount(any());
        verify(joinMapper).insert(any(MenuJoin.class));
    }

    @Test
    void 已加入者_凭加入记录可访问_调用结束后租户上下文恢复() {
        asOutsider();
        given(joinMapper.selectCount(any())).willReturn(1L);
        MenuJoin me = new MenuJoin();
        me.setMenuId(MENU);
        me.setMemberId(100L);
        me.setNickname("外人");
        given(joinMapper.selectOne(any())).willReturn(me);
        given(inviteMapper.selectOne(any())).willReturn(null);

        TogetherService.TogetherVO vo = svc.together(MENU, TogetherService.Identity.member(100L));

        assertThat(vo).isNotNull();
        verify(joinMapper).updateById(me); // 心跳
        assertThat(TenantContextHolder.familyIdOrNull()).isEqualTo(9L);
    }

    // ===================== 生成 / 刷新 / 撤销 =====================

    @Test
    void 生成邀请_带7天有效期且未撤销() {
        asFamilyMember();
        given(inviteMapper.selectOne(any())).willReturn(null);
        ArgumentCaptor<MenuInvite> cap = ArgumentCaptor.forClass(MenuInvite.class);

        svc.invite(MENU, 200L);

        verify(inviteMapper).insert(cap.capture());
        MenuInvite inv = cap.getValue();
        assertThat(inv.getRevoked()).isZero();
        assertThat(inv.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6))
                .isBefore(LocalDateTime.now().plusDays(8));
    }

    @Test
    void 刷新邀请_换token_撤销标记清零且重新计时() {
        asFamilyMember();
        MenuInvite old = invite(MENU, "oldTok", LocalDateTime.now().minusDays(1), 1);
        given(inviteMapper.selectOne(any())).willReturn(old);

        TogetherService.InviteVO vo = svc.invite(MENU, 200L);

        assertThat(vo.token()).isNotEqualTo("oldTok");
        assertThat(old.getToken()).isEqualTo(vo.token());
        assertThat(old.getRevoked()).isZero();
        assertThat(old.isUsable(LocalDateTime.now())).isTrue();
    }

    @Test
    void 撤销邀请_标记revoked_凭证随即不可用() {
        asFamilyMember();
        MenuInvite inv = invite(MENU, "tok", LocalDateTime.now().plusDays(3), 0);
        given(inviteMapper.selectOne(any())).willReturn(inv);

        svc.revokeInvite(MENU, 200L);

        assertThat(inv.getRevoked()).isEqualTo(1);
        verify(inviteMapper).updateById(inv);
        assertThat(inv.isUsable(LocalDateTime.now())).isFalse();
    }

    @Test
    void 撤销邀请_食集不在本家_404() {
        asOutsider();
        given(menuMapper.selectById(MENU)).willReturn(null); // 租户过滤后查不到别家食集
        assertThatThrownBy(() -> svc.revokeInvite(MENU, 100L)).hasMessageContaining("不存在");
        verify(inviteMapper, never()).updateById(any(MenuInvite.class));
    }
}
