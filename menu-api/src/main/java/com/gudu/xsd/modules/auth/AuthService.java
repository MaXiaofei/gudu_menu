package com.gudu.xsd.modules.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 账号登录:BCrypt 校验 + Sa-Token 发券。
 *
 * <p>V29 合并后登录查 {@link Member}(phone = dto.username,或 admin 走 is_admin=1 的 phone='admin' 行)。
 * loginId = member.id,登录即定就餐成员(session.currentMemberId = member.id),
 * 故合并后不再需要「切换成员」。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberMapper memberMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Map<String, Object> login(LoginDTO dto) {
        Member m = memberMapper.selectOne(
                new QueryWrapper<Member>().eq("phone", dto.getUsername()));
        if (m == null || m.getPasswordHash() == null
                || !passwordEncoder.matches(dto.getPassword(), m.getPasswordHash())) {
            throw new BizException("用户名或密码错误");
        }
        StpUtil.login(m.getId());
        // 合并:登录即定就餐成员,免切换。兼容现有读 session.currentMemberId 的代码。
        StpUtil.getSession().set("currentMemberId", m.getId());
        return Map.of("token", StpUtil.getTokenValue(), "nickname", m.getName());
    }
}
