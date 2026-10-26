package com.gudu.xsd.modules.family.application;

import com.gudu.xsd.modules.family.application.dto.FamilyViews.FamilySummary;
import com.gudu.xsd.modules.family.application.dto.FamilyViews.NutritionTargetView;
import com.gudu.xsd.modules.family.domain.service.NutritionTargetCalculator;
import com.gudu.xsd.modules.family.domain.model.Family;
import com.gudu.xsd.modules.family.domain.model.FamilyMemberProfile;
import com.gudu.xsd.modules.family.domain.repository.FamilyRepository;
import com.gudu.xsd.modules.family.domain.repository.FamilyRoleCache;
import com.gudu.xsd.modules.family.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * family 上下文对外查询（OHS，design §2）：其他上下文与租户拦截器只经此判断成员关系，不引用 Family 聚合。
 */
@Service
@RequiredArgsConstructor
public class FamilyQueryApi {

    private final FamilyRepository families;
    private final ProfileRepository profiles;
    private final FamilyRoleCache cache;

    /** 账号在家庭中的角色（仅未解散家庭）；非成员 empty。Redis 缓存 5 分钟，成员变更事件主动失效。 */
    public Optional<String> roleOf(Long familyId, Long accountId) {
        if (familyId == null || accountId == null) return Optional.empty();
        Optional<String> cached = cache.get(familyId, accountId);
        if (cached.isPresent()) {
            return FamilyRoleCache.NONE.equals(cached.get()) ? Optional.empty() : cached;
        }
        Optional<String> role = families.findById(familyId).filter(Family::isActive)
                .flatMap(f -> f.roleOf(accountId)).map(Enum::name);
        cache.put(familyId, accountId, role.orElse(FamilyRoleCache.NONE));
        return role;
    }

    public boolean isMember(Long familyId, Long accountId) {
        return roleOf(familyId, accountId).isPresent();
    }

    /** 账号在该家庭的关联成员档案 id（就餐成员默认值）。 */
    public Optional<Long> profileIdOf(Long familyId, Long accountId) {
        return profiles.findByFamilyAndAccount(familyId, accountId).map(FamilyMemberProfile::id);
    }

    /** 账号在所有家庭的本人成员档案 id（导出我的数据：按档案归属的做菜记录 / 食记 / 点评 / AI 对话）。 */
    /**
     * 称呼脱敏用（后台调用链摘要）：该档案（= 对话发生时的就餐成员）所在家庭的全部成员称呼；
     * 家庭取不到时退回该档案所属账号的全部家庭的成员称呼；档案不存在返回空。已注销成员不计。
     */
    public List<String> memberNamesAround(Long profileId) {
        FamilyMemberProfile p = profiles.findById(profileId).orElse(null);
        if (p == null) return List.of();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        List<FamilyMemberProfile> inFamily = p.familyId() == null ? List.of() : profiles.findByFamily(p.familyId().value());
        if (!inFamily.isEmpty()) {
            inFamily.forEach(x -> addName(names, x));
        } else {
            addName(names, p);
            for (FamilyMemberProfile mine : p.accountId() == null ? List.<FamilyMemberProfile>of()
                    : profiles.findByAccount(p.accountId())) {
                profiles.findByFamily(mine.familyId().value()).forEach(x -> addName(names, x));
            }
        }
        return List.copyOf(names);
    }

    private static void addName(java.util.Set<String> names, FamilyMemberProfile p) {
        String n = p.displayName();
        if (n != null && !n.isBlank() && !p.deactivated()) names.add(n);
    }

    public List<Long> profileIdsOf(Long accountId) {
        return profiles.findByAccount(accountId).stream().map(FamilyMemberProfile::id).toList();
    }

    /** 我加入的全部家庭。 */
    public List<FamilySummary> familiesOf(Long accountId) {
        return families.findActiveByAccount(accountId).stream()
                .map(f -> new FamilySummary(f.id(), f.name().value(),
                        f.roleOf(accountId).map(Enum::name).orElse(null), f.memberships().size()))
                .toList();
    }

    /**
     * 本家成员档案的每日营养目标（{@link NutritionTargetCalculator}）。档案不属于该家庭 → empty（与「不存在」一致）。
     */
    public Optional<NutritionTargetView> nutritionTarget(Long familyId, Long profileId) {
        return profiles.findInFamily(familyId, profileId)
                .flatMap(p -> NutritionTargetCalculator.calculate(p.health(), p.goal(), p.activityLevel()))
                .map(t -> new NutritionTargetView(t.calorieTarget(), t.proteinTarget(), t.carbTarget(),
                        t.fatTarget(), t.goal(), t.bmr()));
    }

    /** 全部未解散家庭（定时任务逐家庭执行）。 */
    public List<Long> activeFamilyIds() {
        return families.findAllActiveIds();
    }
}
