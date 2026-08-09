package com.gudu.xsd.modules.menu.together;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.BizException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 食集聚餐（V45，设计文档 §8）：邀请（口令/二维码/链接三载体同效）→ 朋友加入
 * （登录用户=member / H5 访客=guest_key+昵称）→ 加菜直接进食集（带备注）→
 * 已加入成员可删任意菜（留痕）→ 轮询即心跳。
 *
 * 房主 = menu_invite.created_by（邀请生成人）；menu 表无 owner 字段，MVP 不做房主特权。
 */
@Service
@RequiredArgsConstructor
public class TogetherService {

    /** 成员活跃窗口：超过该时长没轮询（心跳）即视为离开，成员区不再显示。 */
    static final int ACTIVE_WINDOW_MINUTES = 15;

    private final MenuMapper menuMapper;
    private final MenuDishMapper menuDishMapper;
    private final DishMapper dishMapper;
    private final MemberMapper memberMapper;
    private final MenuInviteMapper inviteMapper;
    private final MenuJoinMapper joinMapper;
    private final MenuActivityMapper activityMapper;

    /** 身份：登录用户=memberId；H5 访客=guestKey（二选一）。 */
    public record Identity(Long memberId, String guestKey) {
        public static Identity member(Long id) {
            return new Identity(id, null);
        }

        public static Identity guest(String key) {
            return new Identity(null, key);
        }
    }

    public record InviteVO(String code, String token) {}

    /** 邀请信息（含 token：口令进入场景前端需要它继续 join/轮询）。 */
    public record InviteInfoVO(String token, Long menuId, String menuName, int dishCount) {}

    public record JoinVO(String guestKey, Long menuId, String menuName) {}

    public record MemberVO(Long memberId, String nickname, LocalDateTime lastActiveAt) {}

    public record DishVO(Long id, Long dishId, String dishName, BigDecimal servingFactor,
                         String note, String addedByNickname) {}

    public record ActivityVO(String nickname, String action, String dishName, LocalDateTime createTime) {}

    public record TogetherVO(List<MemberVO> members, List<DishVO> dishes,
                             List<ActivityVO> activities, InviteVO invite) {}

    // ===================== 邀请 =====================

    /**
     * 生成/刷新邀请（登录用户，一食集一邀请）：返回 code + token（url 由前端拼，
     * 格式 {base}/together.html?token=，二维码内容=url）。
     */
    @Transactional
    public InviteVO invite(Long menuId, Long memberId) {
        if (menuId == null) throw new BizException("食集 id 不能为空");
        if (memberId == null) throw new BizException("请先登录");
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null) throw new BizException("食集不存在");

        String code = randomCode();
        String token = UUID.randomUUID().toString().replace("-", "");
        MenuInvite existing = inviteMapper.selectOne(
                new QueryWrapper<MenuInvite>().eq("menu_id", menuId));
        if (existing == null) {
            MenuInvite inv = new MenuInvite();
            inv.setMenuId(menuId);
            inv.setCode(code);
            inv.setToken(token);
            inv.setCreatedBy(memberId);
            inviteMapper.insert(inv);
        } else {
            existing.setCode(code);
            existing.setToken(token);
            inviteMapper.updateById(existing);
        }
        return new InviteVO(code, token);
    }

    /** 邀请信息（免身份，H5 进入页校验 token）。 */
    public InviteInfoVO inviteInfo(String token) {
        return inviteInfo(findByToken(token));
    }

    /** 按 6 位口令查邀请（H5 无链接场景：朋友收到口令，在页面输入）。 */
    public InviteInfoVO inviteInfoByCode(String code) {
        if (code == null || code.isBlank()) throw new BizException("口令无效");
        MenuInvite inv = inviteMapper.selectOne(
                new QueryWrapper<MenuInvite>().eq("code", code.trim().toUpperCase()));
        if (inv == null) throw new BizException("口令无效或已失效");
        return inviteInfo(inv);
    }

    private InviteInfoVO inviteInfo(MenuInvite inv) {
        Menu menu = menuMapper.selectById(inv.getMenuId());
        if (menu == null) throw new BizException("食集不存在");
        Long count = menuDishMapper.selectCount(
                new QueryWrapper<MenuDish>().eq("menu_id", inv.getMenuId()));
        return new InviteInfoVO(inv.getToken(), inv.getMenuId(), menu.getName(),
                count == null ? 0 : count.intValue());
    }

    /**
     * 加入聚餐：登录用户按 member_id upsert；H5 访客按昵称复用或新建 guest_key。
     *
     * @return guestKey（H5 访客存 localStorage；登录用户返回 null）
     */
    @Transactional
    public JoinVO join(String token, String nickname, Long memberId) {
        MenuInvite inv = findByToken(token);
        String name = nickname == null ? "" : nickname.trim();
        if (memberId == null && name.isEmpty()) {
            throw new BizException("请先输入昵称");
        }
        String guestKey = null;
        if (memberId != null) {
            Member member = memberMapper.selectById(memberId);
            MenuJoin join = joinMapper.selectOne(new QueryWrapper<MenuJoin>()
                    .eq("menu_id", inv.getMenuId()).eq("member_id", memberId));
            if (join == null) {
                join = new MenuJoin();
                join.setMenuId(inv.getMenuId());
                join.setMemberId(memberId);
                join.setNickname(member != null && member.getName() != null ? member.getName() : name);
                join.setLastActiveAt(LocalDateTime.now());
                joinMapper.insert(join);
            }
        } else {
            // H5 访客：每次进入新建 guest_key（不按昵称复用——匿名昵称会重复，复用会串身份；
            // 刷新丢 key 只是多一条孤儿记录，无害）
            MenuJoin join = new MenuJoin();
            join.setMenuId(inv.getMenuId());
            join.setNickname(name);
            join.setGuestKey(UUID.randomUUID().toString().replace("-", ""));
            join.setLastActiveAt(LocalDateTime.now());
            joinMapper.insert(join);
            guestKey = join.getGuestKey();
        }
        Menu menu = menuMapper.selectById(inv.getMenuId());
        return new JoinVO(guestKey, inv.getMenuId(), menu == null ? null : menu.getName());
    }

    // ===================== 清单（轮询 + 心跳） =====================

    /**
     * 聚餐清单：成员（昵称+最后活跃）+ 菜列表（含 added_by 标记）+ 活动流 + 邀请。
     * 同时更新本人 last_active_at（轮询即心跳）。登录用户（含房主）自动加入。
     */
    @Transactional
    public TogetherVO together(Long menuId, Identity identity) {
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null) throw new BizException("食集不存在");
        MenuJoin me = resolveJoin(menuId, identity, true);
        touch(me);

        List<MenuJoin> joins = joinMapper.selectList(new QueryWrapper<MenuJoin>()
                .eq("menu_id", menuId)
                // 活跃成员：15 分钟没轮询（心跳）即视为离开，不再显示（下次活跃自动回来）
                .ge("last_active_at", LocalDateTime.now().minusMinutes(ACTIVE_WINDOW_MINUTES))
                .orderByDesc("last_active_at"));
        List<MemberVO> members = joins.stream()
                .map(j -> new MemberVO(j.getMemberId(), j.getNickname(), j.getLastActiveAt()))
                .toList();

        List<MenuDish> rows = menuDishMapper.selectList(
                new QueryWrapper<MenuDish>().eq("menu_id", menuId).orderByAsc("id"));
        Map<Long, String> dishNames = dishNameMap(rows);
        List<DishVO> dishes = rows.stream()
                .map(md -> new DishVO(md.getId(), md.getDishId(), displayName(md, dishNames),
                        md.getServingFactor(), md.getNote(), md.getAddedByNickname()))
                .toList();

        List<MenuActivity> acts = activityMapper.selectList(
                new QueryWrapper<MenuActivity>().eq("menu_id", menuId)
                        .orderByDesc("create_time").last("LIMIT 20"));
        List<ActivityVO> activities = acts.stream()
                .map(a -> new ActivityVO(a.getNickname(), a.getAction(), a.getDishName(), a.getCreateTime()))
                .toList();

        MenuInvite inv = inviteMapper.selectOne(
                new QueryWrapper<MenuInvite>().eq("menu_id", menuId));
        InviteVO inviteVO = inv == null ? null : new InviteVO(inv.getCode(), inv.getToken());
        return new TogetherVO(members, dishes, activities, inviteVO);
    }

    // ===================== 昵称 =====================

    /** 修改昵称（已加入成员；H5 顶部昵称 ✎ 铅笔编辑）。 */
    @Transactional
    public void updateNickname(Long menuId, Identity identity, String nickname) {
        MenuJoin me = resolveJoin(menuId, identity, false);
        String name = nickname == null ? "" : nickname.trim();
        if (name.isEmpty() || name.length() > 32) {
            throw new BizException("昵称 1-32 个字");
        }
        me.setNickname(name);
        joinMapper.updateById(me);
    }

    // ===================== 菜谱搜索（H5 加菜候选，免登录） =====================

    /** 菜谱模糊搜索（加菜候选）：name like keyword，最多 limit 条。 */
    public List<DishBrief> searchDishes(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        List<Dish> rows = dishMapper.selectList(new QueryWrapper<Dish>()
                .like("name", keyword.trim())
                .last("LIMIT " + Math.min(Math.max(limit, 1), 10)));
        return rows.stream()
                .map(d -> new DishBrief(d.getId(), d.getName(), d.getCookTime()))
                .toList();
    }

    /** 菜谱候选（加菜用）：id + 名 + 烹饪分钟。 */
    public record DishBrief(Long id, String name, Integer totalMinutes) {}

    // ===================== 加菜 / 删菜 =====================

    /** 朋友加菜：dishId（菜谱）或 customName（自由输入）二选一，可带备注。写 menu_dish + 活动流。 */
    @Transactional
    public Long addItem(Long menuId, Identity identity, Long dishId, String customName, String note) {
        MenuJoin me = resolveJoin(menuId, identity, false);
        String custom = customName == null ? "" : customName.trim();
        if (dishId == null && custom.isEmpty()) {
            throw new BizException("请选择菜或输入菜名");
        }
        String dishName;
        if (dishId != null) {
            Dish dish = dishMapper.selectById(dishId);
            if (dish == null || dish.getName() == null) throw new BizException("菜品不存在");
            dishName = dish.getName();
        } else {
            dishName = custom;
            if (dishName.length() > 64) throw new BizException("菜名太长");
        }

        MenuDish md = new MenuDish();
        md.setMenuId(menuId);
        md.setDishId(dishId);
        md.setCustomName(dishId == null ? dishName : null);
        md.setServingFactor(BigDecimal.ONE);
        md.setNote(note == null || note.isBlank() ? null : note.trim());
        md.setAddedByMemberId(me.getMemberId());
        md.setAddedByNickname(me.getNickname());
        menuDishMapper.insert(md);

        logActivity(menuId, me, MenuActivity.ACTION_ADD, dishId, dishName);
        return md.getId();
    }

    /** 删菜（已加入成员可删任意菜）：删 menu_dish + 活动流记录谁删的。 */
    @Transactional
    public void removeItem(Long menuId, Identity identity, Long menuDishId) {
        MenuJoin me = resolveJoin(menuId, identity, false);
        MenuDish md = menuDishMapper.selectById(menuDishId);
        if (md == null || !menuId.equals(md.getMenuId())) {
            throw new BizException("这道菜不在这个食集里");
        }
        String dishName = md.getCustomName() != null ? md.getCustomName()
                : dishMapper.selectById(md.getDishId()) != null ? dishMapper.selectById(md.getDishId()).getName()
                : null;
        menuDishMapper.deleteById(menuDishId);
        logActivity(menuId, me, MenuActivity.ACTION_REMOVE, md.getDishId(),
                dishName != null ? dishName : "一道菜");
    }

    // ===================== 内部辅助 =====================

    private MenuInvite findByToken(String token) {
        if (token == null || token.isBlank()) throw new BizException("邀请链接无效");
        MenuInvite inv = inviteMapper.selectOne(
                new QueryWrapper<MenuInvite>().eq("token", token));
        if (inv == null) throw new BizException("邀请链接无效或已失效");
        return inv;
    }

    /**
     * 解析当前身份的 join 记录。
     *
     * @param autoJoinMember 登录用户（含房主）未加入时自动加入（聚餐 Tab 场景）
     */
    private MenuJoin resolveJoin(Long menuId, Identity identity, boolean autoJoinMember) {
        if (identity == null) throw new BizException("请先加入聚餐");
        if (identity.memberId() != null) {
            MenuJoin join = joinMapper.selectOne(new QueryWrapper<MenuJoin>()
                    .eq("menu_id", menuId).eq("member_id", identity.memberId()));
            if (join == null && autoJoinMember) {
                Member member = memberMapper.selectById(identity.memberId());
                join = new MenuJoin();
                join.setMenuId(menuId);
                join.setMemberId(identity.memberId());
                join.setNickname(member != null && member.getName() != null ? member.getName() : "我");
                join.setLastActiveAt(LocalDateTime.now());
                joinMapper.insert(join);
            }
            if (join == null) throw new BizException("请先加入聚餐");
            return join;
        }
        MenuJoin join = joinMapper.selectOne(new QueryWrapper<MenuJoin>()
                .eq("menu_id", menuId).eq("guest_key", identity.guestKey()));
        if (join == null) throw new BizException("请先加入聚餐");
        return join;
    }

    private void touch(MenuJoin join) {
        join.setLastActiveAt(LocalDateTime.now());
        joinMapper.updateById(join);
    }

    private void logActivity(Long menuId, MenuJoin me, String action, Long dishId, String dishName) {
        MenuActivity a = new MenuActivity();
        a.setMenuId(menuId);
        a.setMemberId(me.getMemberId());
        a.setNickname(me.getNickname());
        a.setAction(action);
        a.setDishId(dishId);
        a.setDishName(dishName);
        activityMapper.insert(a);
    }

    /** 批量取菜名（dish_id → name）。 */
    private Map<Long, String> dishNameMap(List<MenuDish> rows) {
        List<Long> ids = rows.stream().map(MenuDish::getDishId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return dishMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Dish::getId, Dish::getName, (a, b) -> a));
    }

    /** 展示名：自定义菜名优先，否则 dish 名。 */
    private String displayName(MenuDish md, Map<Long, String> dishNames) {
        if (md.getCustomName() != null && !md.getCustomName().isBlank()) return md.getCustomName();
        String name = md.getDishId() == null ? null : dishNames.get(md.getDishId());
        return name != null ? name : "未知菜品";
    }

    /** 6 位口令：去易混淆字符（0/O/1/I）。 */
    private String randomCode() {
        String chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }
}
