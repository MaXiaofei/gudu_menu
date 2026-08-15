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
    private final WxClient wxClient;
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

    /**
     * 微信小程序静默登录（V50）：wx.login 的 code → openid → 查/建 member → Sa-Token 发券。
     * 新用户零门槛：openid 首次出现即自动建号（name=微信用户，phone=wx_ 前缀占位）。
     * 同一 openid 永远同一账号（换设备数据不丢）。
     */
    public Map<String, Object> wxLogin(String code) {
        String openid = wxClient.code2session(code);

        Member m = memberMapper.selectOne(new QueryWrapper<Member>().eq("openid", openid));
        if (m == null) {
            m = new Member();
            m.setOpenid(openid);
            m.setName("微信用户");
            m.setPhone("wx_" + openid.substring(0, Math.min(8, openid.length()))); // 占位手机号（无密码登录）
            memberMapper.insert(m);
        }
        StpUtil.login(m.getId());
        StpUtil.getSession().set("currentMemberId", m.getId());
        return Map.of("token", StpUtil.getTokenValue(), "nickname", m.getName());
    }
}
