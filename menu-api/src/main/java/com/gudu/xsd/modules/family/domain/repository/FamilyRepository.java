package com.gudu.xsd.modules.family.domain.repository;

import com.gudu.xsd.modules.family.domain.model.Family;
import com.gudu.xsd.modules.family.domain.model.InviteCode;

import java.util.List;
import java.util.Optional;

/** 家庭仓储（family / family_membership / family_invitation 三表，整体加载与保存）。 */
public interface FamilyRepository {

    Optional<Family> findById(Long id);

    Optional<Family> findByInviteCode(InviteCode code);

    /** 账号所在的全部未解散家庭（按加入时间升序）。 */
    List<Family> findActiveByAccount(Long accountId);

    long countActiveByAccount(Long accountId);

    /** 全部未解散家庭 id（定时任务按家庭逐个 runAs）。 */
    List<Long> findAllActiveIds();

    /** 保存聚合；新建时回填 id（{@link Family#identify}）。 */
    void save(Family family);
}
