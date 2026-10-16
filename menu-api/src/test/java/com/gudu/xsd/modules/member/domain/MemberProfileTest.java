package com.gudu.xsd.modules.member.domain;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.member.domain.model.MemberProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberProfileTest {

    private MemberProfile p(String phone) {
        return MemberProfile.rehydrate(1L, "张爸爸", null, phone, false);
    }

    @Test
    void 改昵称_去空白_1到20字() {
        MemberProfile m = p(null);
        m.rename("  小厨  ");
        assertThat(m.name()).isEqualTo("小厨");
        m.rename("a".repeat(20));
        assertThatThrownBy(() -> m.rename("a".repeat(21))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> m.rename("   ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> m.rename(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void 换头像_只接受本站上传_空串清除() {
        MemberProfile m = p(null);
        m.changeAvatar(" /gudu/uploads/2026/10/a.jpg ");
        assertThat(m.avatarUrl()).isEqualTo("/gudu/uploads/2026/10/a.jpg");
        assertThatThrownBy(() -> m.changeAvatar("https://evil.com/a.jpg")).isInstanceOf(DomainException.class);
        m.changeAvatar("");
        assertThat(m.avatarUrl()).isNull();
        m.changeAvatar(null);
        assertThat(m.avatarUrl()).isNull();
    }

    @Test
    void 手机号脱敏_非手机号为null() {
        assertThat(p("13800000001").maskedPhone()).isEqualTo("138****0001");
        assertThat(p("admin").maskedPhone()).isNull();
        assertThat(p(null).maskedPhone()).isNull();
    }

    @Test
    void id必填_超管标记() {
        assertThatThrownBy(() -> MemberProfile.rehydrate(null, "x", null, null, false))
                .isInstanceOf(DomainException.class);
        MemberProfile admin = MemberProfile.rehydrate(2L, "admin", null, "admin", true);
        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.id()).isEqualTo(2L);
    }
}
