package com.gudu.xsd.modules.menu.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.menu.domain.event.DishCookedEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 一次做菜（聚合根，内存态不落库）：校验份数/备注/用材确认 → {@link #complete()} 登记 {@link DishCookedEvent}。
 * 用材规则：同一食材同时勾「用完」与「用了一些」以「用完」为准。
 */
public final class CookSession extends AggregateRoot<String> {

    public static final int MAX_NOTE_LENGTH = 200;
    static final BigDecimal MAX_SERVINGS = new BigDecimal("99");

    private final String id;
    private final Long memberId;
    private final CookSource source;
    private final Long menuId;
    private final List<CookedDish> dishes;
    private final List<Long> usedUp;
    private final List<Long> partiallyUsed;
    private final String note;
    private boolean completed;

    private CookSession(Long memberId, CookSource source, Long menuId, List<CookedDish> dishes,
                        Collection<Long> usedUp, Collection<Long> partiallyUsed, String note) {
        this.id = UUID.randomUUID().toString();
        this.memberId = memberId;
        this.source = source;
        this.menuId = menuId;
        this.dishes = List.copyOf(dishes);
        Set<Long> up = distinct(usedUp);
        Set<Long> partial = distinct(partiallyUsed);
        partial.removeAll(up);
        this.usedUp = List.copyOf(up);
        this.partiallyUsed = List.copyOf(partial);
        this.note = note;
    }

    /** 单菜直做：份数默认 1，(0, 99]；备注 ≤ 200 字。 */
    public static CookSession forDish(Long memberId, Long dishId, BigDecimal servings,
                                      Collection<Long> usedUp, Collection<Long> partiallyUsed, String note) {
        BigDecimal s = servings == null ? BigDecimal.ONE : servings;
        if (s.signum() <= 0 || s.compareTo(MAX_SERVINGS) > 0) throw new DomainException("份数需大于 0 且不超过 99");
        String n = note == null || note.isBlank() ? null : note.trim();
        if (n != null && n.length() > MAX_NOTE_LENGTH) throw new DomainException("备注最多 200 字");
        return new CookSession(memberId, CookSource.DISH, null, List.of(new CookedDish(dishId, s)),
                usedUp, partiallyUsed, n);
    }

    /** 整集做：食集内每道菜 + 份数。 */
    public static CookSession forMenu(Long memberId, Long menuId, List<CookedDish> dishes,
                                      Collection<Long> usedUp, Collection<Long> partiallyUsed) {
        if (menuId == null) throw new DomainException("食集 id 不能为空");
        return new CookSession(memberId, CookSource.MENU, menuId, dishes == null ? List.of() : dishes,
                usedUp, partiallyUsed, null);
    }

    /** 做完：登记 DishCookedEvent（只能完成一次）。 */
    public DishCookedEvent complete() {
        if (completed) throw new DomainException("这顿饭已经做完了");
        completed = true;
        DishCookedEvent event = new DishCookedEvent(memberId, source, menuId, dishes, usedUp, partiallyUsed, note);
        registerEvent(event);
        return event;
    }

    private static Set<Long> distinct(Collection<Long> ids) {
        Set<Long> out = new LinkedHashSet<>();
        if (ids != null) ids.stream().filter(Objects::nonNull).forEach(out::add);
        return out;
    }

    @Override
    public String id() {
        return id;
    }

    public CookSource source() {
        return source;
    }

    public List<CookedDish> dishes() {
        return new ArrayList<>(dishes);
    }

    public List<Long> usedUp() {
        return usedUp;
    }

    public List<Long> partiallyUsed() {
        return partiallyUsed;
    }

    public boolean isCompleted() {
        return completed;
    }
}
