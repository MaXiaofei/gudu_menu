package com.gudu.xsd.modules.dish.application;

import com.gudu.xsd.modules.cookbook.application.CookingRecordAppService;
import com.gudu.xsd.modules.cookbook.domain.model.LastCooked;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.application.dto.CookerView;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 菜谱列表「上次 3 天前 · 妈妈做的」（prototype-ui-rollout 0.7）：回填 lastCookedAt / lastCookedBy。
 * 统计范围为全部成员（当前单家庭；multi-tenant-family 落地后改按 familyId 过滤）。
 * 每次请求固定 2 次查询（最近记录 + 成员），无 N+1。
 */
@Service
@RequiredArgsConstructor
public class DishCookHistoryAppService {

    private final CookingRecordAppService cookingRecordAppService;
    private final MemberService memberService;

    public void fillLastCooked(List<Dish> dishes) {
        if (dishes == null || dishes.isEmpty()) return;
        Map<Long, LastCooked> last = cookingRecordAppService.lastCooked(
                dishes.stream().map(Dish::getId).filter(Objects::nonNull).toList());
        if (last.isEmpty()) return;
        List<Long> memberIds = last.values().stream().map(LastCooked::memberId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Member> members = memberIds.isEmpty() ? Map.of()
                : memberService.listByIds(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity(), (a, b) -> a));
        for (Dish d : dishes) {
            LastCooked lc = last.get(d.getId());
            if (lc == null) continue;
            d.setLastCookedAt(lc.cookedAt());
            Member m = lc.memberId() == null ? null : members.get(lc.memberId());
            if (m != null) d.setLastCookedBy(new CookerView(m.getId(), m.getName(), m.getAvatarUrl()));
        }
    }
}
