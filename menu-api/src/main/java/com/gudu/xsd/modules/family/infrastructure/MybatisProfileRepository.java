package com.gudu.xsd.modules.family.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.common.tenant.FamilyId;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.family.domain.model.FamilyMemberProfile;
import com.gudu.xsd.modules.family.domain.model.HealthProfile;
import com.gudu.xsd.modules.family.domain.repository.ProfileRepository;
import com.gudu.xsd.modules.family.infrastructure.mapper.FamilyProfileMapper;
import com.gudu.xsd.modules.family.infrastructure.po.FamilyProfilePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 成员档案仓储（member 表）。全部以系统模式执行（绕过租户插件）并显式带 family_id 条件——
 * 加入 / 合并 / 注销会写入「非当前家庭」的档案。
 */
@Repository
@RequiredArgsConstructor
public class MybatisProfileRepository implements ProfileRepository {

    /** 新档案默认角色标签：34 = 普通成员（旧 MpPerm 模板）。 */
    static final String DEFAULT_ROLE_TAGS = "34";

    private final FamilyProfileMapper mapper;

    @Override
    public Optional<FamilyMemberProfile> findInFamily(Long familyId, Long profileId) {
        if (familyId == null || profileId == null) return Optional.empty();
        return TenantContextHolder.callAsSystem(() -> Optional.ofNullable(mapper.selectOne(
                new QueryWrapper<FamilyProfilePO>().eq("id", profileId).eq("family_id", familyId))))
                .map(MybatisProfileRepository::toDomain);
    }

    @Override
    public Optional<FamilyMemberProfile> findById(Long profileId) {
        if (profileId == null) return Optional.empty();
        return TenantContextHolder.callAsSystem(() -> Optional.ofNullable(mapper.selectOne(
                new QueryWrapper<FamilyProfilePO>().eq("id", profileId)))).map(MybatisProfileRepository::toDomain);
    }

    @Override
    public List<FamilyMemberProfile> findByFamily(Long familyId) {
        if (familyId == null) return List.of();
        return TenantContextHolder.callAsSystem(() -> mapper.selectList(new QueryWrapper<FamilyProfilePO>()
                        .eq("family_id", familyId).orderByAsc("id")))
                .stream().map(MybatisProfileRepository::toDomain).toList();
    }

    @Override
    public Optional<FamilyMemberProfile> findByFamilyAndAccount(Long familyId, Long accountId) {
        if (familyId == null || accountId == null) return Optional.empty();
        return TenantContextHolder.callAsSystem(() -> mapper.selectList(new QueryWrapper<FamilyProfilePO>()
                        .eq("family_id", familyId).eq("account_id", accountId).orderByAsc("id")))
                .stream().findFirst().map(MybatisProfileRepository::toDomain);
    }

    @Override
    public List<FamilyMemberProfile> findByAccount(Long accountId) {
        if (accountId == null) return List.of();
        return TenantContextHolder.callAsSystem(() -> mapper.selectList(new QueryWrapper<FamilyProfilePO>()
                        .eq("account_id", accountId).orderByAsc("id")))
                .stream().map(MybatisProfileRepository::toDomain).toList();
    }

    @Override
    public void save(FamilyMemberProfile p) {
        FamilyProfilePO po = new FamilyProfilePO();
        po.setFamilyId(p.familyId().value());
        po.setAccountId(p.accountId());
        po.setName(p.displayName());
        po.setAvatarUrl(p.avatarUrl());
        po.setHealthProfile(p.health() == null ? null : p.health().attributes());
        po.setGoal(p.goal());
        po.setActivityLevel(p.activityLevel());
        TenantContextHolder.runAsSystem(() -> {
            if (p.id() == null) {
                po.setRoleTags(DEFAULT_ROLE_TAGS);
                po.setIsAdmin(0);
                po.setCreateTime(LocalDateTime.now());
                mapper.insert(po);
                p.identify(po.getId());
            } else {
                mapper.update(po, new UpdateWrapper<FamilyProfilePO>()
                        .eq("id", p.id()).eq("family_id", p.familyId().value()));
                if (p.accountId() == null || p.deactivated()) {
                    // 解除账号关联 / 注销匿名化：旧凭证列（保留一个版本周期）同步清空，防止旧数据泄露
                    mapper.update(null, new UpdateWrapper<FamilyProfilePO>()
                            .eq("id", p.id()).eq("family_id", p.familyId().value())
                            .set("phone", null).set("openid", null).set("password_hash", null).set("is_admin", 0));
                }
            }
        });
    }

    @Override
    public List<FamilyMemberProfile> findHealthProvidedBy(Long accountId) {
        if (accountId == null) return List.of();
        return TenantContextHolder.callAsSystem(() -> mapper.selectList(new QueryWrapper<FamilyProfilePO>()
                        .eq("health_consent_account_id", accountId).orderByAsc("id")))
                .stream().map(MybatisProfileRepository::toDomain).toList();
    }

    @Override
    public int reassignHealthProvider(Long from, Long to) {
        if (from == null || to == null) return 0;
        return TenantContextHolder.callAsSystem(() -> mapper.update(null, new UpdateWrapper<FamilyProfilePO>()
                .eq("health_consent_account_id", from).set("health_consent_account_id", to)));
    }

    static FamilyMemberProfile toDomain(FamilyProfilePO po) {
        return FamilyMemberProfile.rehydrate(po.getId(), FamilyId.of(po.getFamilyId()), po.getAccountId(),
                po.getName(), po.getAvatarUrl(), po.getHealthProfile() == null ? null
                        : new HealthProfile(po.getHealthProfile()), po.getGoal(), po.getActivityLevel());
    }
}
