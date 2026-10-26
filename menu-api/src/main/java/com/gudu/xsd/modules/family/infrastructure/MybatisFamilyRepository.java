package com.gudu.xsd.modules.family.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.family.domain.model.Family;
import com.gudu.xsd.modules.family.domain.model.FamilyName;
import com.gudu.xsd.modules.family.domain.model.FamilyRole;
import com.gudu.xsd.modules.family.domain.model.FamilyStatus;
import com.gudu.xsd.modules.family.domain.model.Invitation;
import com.gudu.xsd.modules.family.domain.model.InviteCode;
import com.gudu.xsd.modules.family.domain.model.Membership;
import com.gudu.xsd.modules.family.domain.repository.FamilyRepository;
import com.gudu.xsd.modules.family.infrastructure.mapper.FamilyInvitationMapper;
import com.gudu.xsd.modules.family.infrastructure.mapper.FamilyMapper;
import com.gudu.xsd.modules.family.infrastructure.mapper.FamilyMembershipMapper;
import com.gudu.xsd.modules.family.infrastructure.po.FamilyInvitationPO;
import com.gudu.xsd.modules.family.infrastructure.po.FamilyMembershipPO;
import com.gudu.xsd.modules.family.infrastructure.po.FamilyPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Family 聚合仓储：family + family_membership + family_invitation 整体加载；保存按差异写入 + 乐观锁。 */
@Repository
@RequiredArgsConstructor
public class MybatisFamilyRepository implements FamilyRepository {

    private final FamilyMapper familyMapper;
    private final FamilyMembershipMapper membershipMapper;
    private final FamilyInvitationMapper invitationMapper;

    @Override
    public Optional<Family> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(familyMapper.selectById(id)).map(this::load);
    }

    @Override
    public Optional<Family> findByInviteCode(InviteCode code) {
        if (code == null) return Optional.empty();
        FamilyInvitationPO inv = invitationMapper.selectOne(
                new QueryWrapper<FamilyInvitationPO>().eq("code", code.value()));
        return inv == null ? Optional.empty() : findById(inv.getFamilyId());
    }

    @Override
    public List<Family> findActiveByAccount(Long accountId) {
        if (accountId == null) return List.of();
        List<FamilyMembershipPO> rows = membershipMapper.selectList(
                new QueryWrapper<FamilyMembershipPO>().eq("account_id", accountId).orderByAsc("joined_at", "id"));
        return rows.stream().map(r -> findById(r.getFamilyId()).orElse(null))
                .filter(f -> f != null && f.isActive()).toList();
    }

    @Override
    public long countActiveByAccount(Long accountId) {
        return findActiveByAccount(accountId).size();
    }

    @Override
    public List<Long> findAllActiveIds() {
        return familyMapper.selectList(new QueryWrapper<FamilyPO>().select("id").eq("status", FamilyStatus.ACTIVE.name())
                .orderByAsc("id")).stream().map(FamilyPO::getId).toList();
    }

    @Override
    public void save(Family f) {
        FamilyPO po = new FamilyPO();
        po.setName(f.name().value());
        po.setOwnerAccountId(f.ownerAccountId());
        po.setStatus(f.status().name());
        po.setDissolvedAt(f.dissolvedAt());
        if (f.id() == null) {
            po.setCreatedAt(f.createdAt());
            po.setVersion(0L);
            familyMapper.insert(po);
            f.identify(po.getId());
        } else {
            int n = familyMapper.update(po, new UpdateWrapper<FamilyPO>().eq("id", f.id()).eq("version", f.version())
                    .set("version", f.version() + 1));
            if (n == 0) throw new BizException("家庭信息已被他人修改，请刷新后重试");
            f.advanceVersion();
        }
        saveMemberships(f);
        saveInvitations(f);
    }

    private void saveMemberships(Family f) {
        Map<Long, FamilyMembershipPO> existing = new HashMap<>();
        for (FamilyMembershipPO r : membershipMapper.selectList(
                new QueryWrapper<FamilyMembershipPO>().eq("family_id", f.id()))) {
            existing.put(r.getAccountId(), r);
        }
        Set<Long> keep = new HashSet<>();
        // 先删后写：避免账号改指（合并）时撞 UNIQUE(family_id, account_id)
        for (Membership m : f.memberships()) keep.add(m.accountId());
        for (FamilyMembershipPO r : existing.values()) {
            if (!keep.contains(r.getAccountId())) membershipMapper.deleteById(r.getId());
        }
        for (Membership m : f.memberships()) {
            FamilyMembershipPO r = existing.get(m.accountId());
            if (r == null) {
                r = new FamilyMembershipPO();
                r.setFamilyId(f.id());
                r.setAccountId(m.accountId());
                r.setRole(m.role().name());
                r.setJoinedAt(m.joinedAt());
                membershipMapper.insert(r);
            } else if (!m.role().name().equals(r.getRole())) {
                r.setRole(m.role().name());
                membershipMapper.updateById(r);
            }
        }
    }

    private void saveInvitations(Family f) {
        Map<String, FamilyInvitationPO> existing = new HashMap<>();
        for (FamilyInvitationPO r : invitationMapper.selectList(
                new QueryWrapper<FamilyInvitationPO>().eq("family_id", f.id()))) {
            existing.put(r.getCode(), r);
        }
        for (Invitation inv : f.invitations()) {
            FamilyInvitationPO r = existing.get(inv.code().value());
            if (r == null) {
                r = new FamilyInvitationPO();
                r.setFamilyId(f.id());
                r.setCode(inv.code().value());
                r.setCreatedBy(inv.createdBy());
                r.setExpiresAt(inv.expiresAt());
                r.setMaxUses(inv.maxUses());
                r.setUsedCount(inv.usedCount());
                r.setRevoked(inv.revoked() ? 1 : 0);
                r.setCreatedAt(inv.createdAt());
                invitationMapper.insert(r);
            } else if (r.getUsedCount() != inv.usedCount() || (r.getRevoked() == 1) != inv.revoked()) {
                r.setUsedCount(inv.usedCount());
                r.setRevoked(inv.revoked() ? 1 : 0);
                invitationMapper.updateById(r);
            }
        }
    }

    private Family load(FamilyPO po) {
        List<Membership> ms = membershipMapper.selectList(
                        new QueryWrapper<FamilyMembershipPO>().eq("family_id", po.getId()))
                .stream().sorted(Comparator.comparing(FamilyMembershipPO::getId))
                .map(r -> new Membership(r.getAccountId(), FamilyRole.of(r.getRole()), r.getJoinedAt())).toList();
        List<Invitation> invs = invitationMapper.selectList(
                        new QueryWrapper<FamilyInvitationPO>().eq("family_id", po.getId()))
                .stream().sorted(Comparator.comparing(FamilyInvitationPO::getId))
                .map(r -> new Invitation(new InviteCode(r.getCode()), r.getCreatedBy(), r.getExpiresAt(),
                        r.getMaxUses() == null ? 1 : r.getMaxUses(), r.getUsedCount() == null ? 0 : r.getUsedCount(),
                        r.getRevoked() != null && r.getRevoked() == 1, r.getCreatedAt())).toList();
        return Family.rehydrate(po.getId(), new FamilyName(po.getName()), FamilyStatus.valueOf(po.getStatus()), ms,
                invs, po.getCreatedAt(), po.getDissolvedAt(), po.getVersion() == null ? 0 : po.getVersion());
    }
}
