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
