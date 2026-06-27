package com.gudu.xsd.modules.auth;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.mapper.MemberMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthService 单测:V29 合并后登录查 member(phone)+ BCrypt + session 定就餐成员。
 *
 * <p>用 MockedStatic 桩 StpUtil.login/getSession/getTokenValue(同 MpPermissionAspectTest 范式),
 * MemberMapper 用 Mockito 桩 selectOne。BCrypt 用真实编码器(可逆,验证校验链路)。
 */
class AuthServiceTest {

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    /** 造一个 phone='13800000001' 密码 'pw123' 的普通 member。 */
    private Member phoneMember() {
        Member m = new Member();
        m.setId(10L);
        m.setName("测试员");
        m.setPhone("13800000001");
        m.setPasswordHash(encoder.encode("pw123"));
        m.setIsAdmin(0);
        return m;
    }

    /** 造一个 phone='admin' 密码 'admin123' 的超管 member。 */
    private Member adminMember() {
        Member m = new Member();
        m.setId(99L);
        m.setName("掌勺人");
        m.setPhone("admin");
        m.setPasswordHash(encoder.encode("admin123"));
        m.setIsAdmin(1);
        return m;
    }

    @Test
    void 手机号登录成功_返回token和昵称_session定成员() {
        MemberMapper mm = mock(MemberMapper.class);
        when(mm.selectOne(any(Wrapper.class))).thenReturn(phoneMember());
        AuthService svc = new AuthService(mm);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            SaSession session = mock(SaSession.class);
            stp.when(() -> StpUtil.login(10L)).thenAnswer(i -> null);
            stp.when(StpUtil::getSession).thenReturn(session);
            stp.when(StpUtil::getTokenValue).thenReturn("tok-abc");

            Map<String, Object> r = svc.login(new LoginDTO("13800000001", "pw123"));
            assertThat(r.get("token")).isEqualTo("tok-abc");
            assertThat(r.get("nickname")).isEqualTo("测试员");
            // 登录即定就餐成员 = loginId(合并核心)
            verify(session).set("currentMemberId", 10L);
        }
    }

    @Test
    void admin账号登录成功_phone为admin字面量() {
        MemberMapper mm = mock(MemberMapper.class);
        when(mm.selectOne(any(Wrapper.class))).thenReturn(adminMember());
        AuthService svc = new AuthService(mm);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            SaSession session = mock(SaSession.class);
            stp.when(() -> StpUtil.login(99L)).thenAnswer(i -> null);
            stp.when(StpUtil::getSession).thenReturn(session);
            stp.when(StpUtil::getTokenValue).thenReturn("tok-admin");

            Map<String, Object> r = svc.login(new LoginDTO("admin", "admin123"));
            assertThat(r.get("token")).isEqualTo("tok-admin");
            assertThat(r.get("nickname")).isEqualTo("掌勺人");
            verify(session).set("currentMemberId", 99L);
        }
    }

    @Test
    void 手机号不存在_抛用户名或密码错误() {
        MemberMapper mm = mock(MemberMapper.class);
        when(mm.selectOne(any(Wrapper.class))).thenReturn(null);
        AuthService svc = new AuthService(mm);

        assertThatThrownBy(() -> svc.login(new LoginDTO("13900000000", "any")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void 密码错误_抛用户名或密码错误() {
        MemberMapper mm = mock(MemberMapper.class);
        when(mm.selectOne(any(Wrapper.class))).thenReturn(phoneMember());
        AuthService svc = new AuthService(mm);

        assertThatThrownBy(() -> svc.login(new LoginDTO("13800000001", "wrong")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void member无密码hash_抛用户名或密码错误() {
        // 纯家庭成员(phone=null/passwordHash=null)不应能登录
        Member m = phoneMember();
        m.setPasswordHash(null);
        MemberMapper mm = mock(MemberMapper.class);
        when(mm.selectOne(any(Wrapper.class))).thenReturn(m);
        AuthService svc = new AuthService(mm);

        assertThatThrownBy(() -> svc.login(new LoginDTO("13800000001", "pw123")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名或密码错误");
    }
}
