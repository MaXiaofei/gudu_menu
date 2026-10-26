package com.gudu.xsd.modules.community.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** A.12 厨房圈紧急关停：关闭时用户侧读写统一 5031，管理后台审核接口不受影响。 */
class CommunitySuspendGuardTest {

    private final ObjectMapper om = new ObjectMapper();

    private MockHttpServletResponse call(CommunitySuspendGuard guard, String method, String path, boolean[] passed)
            throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, "/gudu" + path);
        req.setContextPath("/gudu");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        passed[0] = guard.preHandle(req, resp, null);
        return resp;
    }

    @Test
    void 开关关闭_读写都返回暂停服务_开关打开_放行() throws Exception {
        AtomicBoolean on = new AtomicBoolean(false);
        CommunitySuspendGuard guard = new CommunitySuspendGuard(on::get, om);
        boolean[] passed = new boolean[1];

        for (String[] r : new String[][] {{"GET", "/community/feed"}, {"GET", "/community/posts/1"},
                {"POST", "/community/posts"}, {"POST", "/community/posts/1/comments"}}) {
            MockHttpServletResponse resp = call(guard, r[0], r[1], passed);
            assertThat(passed[0]).as(r[1]).isFalse();
            JsonNode body = om.readTree(resp.getContentAsString());
            assertThat(body.get("code").asInt()).isEqualTo(CommunitySuspendGuard.CODE_COMMUNITY_SUSPENDED);
            assertThat(body.get("msg").asText()).isEqualTo("厨房圈暂停服务");
        }

        on.set(true);
        MockHttpServletResponse ok = call(guard, "GET", "/community/feed", passed);
        assertThat(passed[0]).isTrue();
        assertThat(ok.getContentAsString()).isEmpty();
    }

    @Test
    void 管理后台审核接口_不在拦截范围() throws Exception {
        CommunitySuspendGuard guard = new CommunitySuspendGuard(() -> false, om);
        boolean[] passed = new boolean[1];
        call(guard, "GET", "/admin/community/queue", passed);
        assertThat(passed[0]).isTrue();
        call(guard, "POST", "/admin/community/posts/1/moderate", passed);
        assertThat(passed[0]).isTrue();
        assertThat(CommunitySuspendGuard.appliesTo("/admin/community/switch")).isFalse();
        assertThat(CommunitySuspendGuard.appliesTo("/communityx")).isFalse();

        // 注册的路径模式本身也不覆盖 /admin/community/**
        AntPathMatcher m = new AntPathMatcher();
        assertThat(Arrays.stream(CommunityWebConfig.USER_PATHS).anyMatch(p -> m.match(p, "/admin/community/queue")))
                .isFalse();
        assertThat(Arrays.stream(CommunityWebConfig.USER_PATHS).anyMatch(p -> m.match(p, "/community/feed")))
                .isTrue();
    }
}
