package com.gudu.xsd.modules.member.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gudu.xsd.modules.member.AdminChecker;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.domain.model.MemberProfile;
import com.gudu.xsd.modules.member.domain.repository.MemberProfileRepository;
import com.gudu.xsd.modules.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MybatisMemberProfileRepository implements MemberProfileRepository {

    private final MemberMapper mapper;

    @Override
    public Optional<MemberProfile> findById(Long memberId) {
        if (memberId == null) return Optional.empty();
        return toDomain(mapper.selectById(memberId));
    }

    @Override
    public Optional<MemberProfile> findByAccount(Long accountId) {
        if (accountId == null) return Optional.empty();
        return toDomain(mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Member>()
                .eq("account_id", accountId).orderByAsc("id").last("LIMIT 1")));
    }

    private static Optional<MemberProfile> toDomain(Member m) {
        if (m == null) return Optional.empty();
        return Optional.of(MemberProfile.rehydrate(m.getId(), m.getName(), m.getAvatarUrl(), m.getPhone(),
                AdminChecker.isAdmin(m)));
    }

    @Override
    public List<MemberProfile> findByAvatarUrls(Collection<String> avatarUrls) {
        if (avatarUrls == null || avatarUrls.isEmpty()) return List.of();
        return mapper.selectList(new QueryWrapper<Member>().in("avatar_url", avatarUrls)).stream()
                .map(m -> MemberProfile.rehydrate(m.getId(), m.getName(), m.getAvatarUrl(), m.getPhone(),
                        AdminChecker.isAdmin(m)))
                .toList();
    }

    @Override
    public void save(MemberProfile p) {
        mapper.update(null, new UpdateWrapper<Member>()
                .eq("id", p.id())
                .set("name", p.name())
                .set("avatar_url", p.avatarUrl()));
    }
}
