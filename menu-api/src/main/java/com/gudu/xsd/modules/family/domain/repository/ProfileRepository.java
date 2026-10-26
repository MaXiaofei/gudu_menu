package com.gudu.xsd.modules.family.domain.repository;

import com.gudu.xsd.modules.family.domain.model.FamilyMemberProfile;

import java.util.List;
import java.util.Optional;

/**
 * 成员档案仓储（member 表）。实现以系统模式访问并<b>显式</b>带 family_id 条件——
 * 加入 / 合并 / 注销等用例会跨越当前请求的家庭。
 */
public interface ProfileRepository {

    Optional<FamilyMemberProfile> findInFamily(Long familyId, Long profileId);

    Optional<FamilyMemberProfile> findByFamilyAndAccount(Long familyId, Long accountId);

    /** 某账号在所有家庭的关联档案。 */
    List<FamilyMemberProfile> findByAccount(Long accountId);

    void save(FamilyMemberProfile profile);

    /** 健康档案由该账号提供（并单独同意）的档案，跨全部家庭（mini-release-compliance B.2 撤回同意用）。 */
    List<FamilyMemberProfile> findHealthProvidedBy(Long accountId);

    /** 账号合并：健康档案提供人改指目标账号，返回改动条数。 */
    int reassignHealthProvider(Long from, Long to);
}
