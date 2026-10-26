package com.gudu.xsd.modules.community.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MiniProgramCommunityGuardTest {

    private static final String MP = "mp-weixin";

    @Test
    void 小程序_社区写接口全部拦截() {
        assertThat(MiniProgramCommunityGuard.blocks("POST", "/community/posts", MP)).as("发帖").isTrue();
        assertThat(MiniProgramCommunityGuard.blocks("POST", "/community/posts/12/comments", MP)).as("评论").isTrue();
        assertThat(MiniProgramCommunityGuard.blocks("POST", "/community/posts/12/reactions", MP)).as("点赞").isTrue();
        assertThat(MiniProgramCommunityGuard.blocks("POST", "/community/users/3/follow", MP)).as("关注").isTrue();
        assertThat(MiniProgramCommunityGuard.blocks("POST", "/community/reports", MP)).as("举报").isTrue();
        assertThat(MiniProgramCommunityGuard.blocks("POST", "/community/posts/12/copy-recipe", MP)).isTrue();
        assertThat(MiniProgramCommunityGuard.blocks("POST", "/community/drafts/ai-text", MP)).isTrue();
        assertThat(MiniProgramCommunityGuard.blocks("put", "/community/posts/12", " MP-WEIXIN ")).as("大小写/空白").isTrue();
    }

    @Test
    void 读接口_删除自己的帖子_其他端_其他路径_不拦截() {
        assertThat(MiniProgramCommunityGuard.blocks("GET", "/community/feed", MP)).isFalse();
        assertThat(MiniProgramCommunityGuard.blocks("GET", "/community/posts/12/comments", MP)).isFalse();
        assertThat(MiniProgramCommunityGuard.blocks("OPTIONS", "/community/posts", MP)).isFalse();
        assertThat(MiniProgramCommunityGuard.blocks("DELETE", "/community/posts/12", MP)).as("删自己的帖").isFalse();
        assertThat(MiniProgramCommunityGuard.blocks("POST", "/community/posts", null)).as("App/H5").isFalse();
        assertThat(MiniProgramCommunityGuard.blocks("POST", "/community/posts", "h5")).isFalse();
        assertThat(MiniProgramCommunityGuard.blocks("POST", "/communityx", MP)).isFalse();
        assertThat(MiniProgramCommunityGuard.blocks("POST", "/feedback", MP)).isFalse();
    }

    @Test
    void 拦截时返回_HTTP403_与业务码4031() throws Exception {
        ObjectMapper om = new ObjectMapper();
        MiniProgramCommunityGuard guard = new MiniProgramCommunityGuard(om);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/gudu/community/posts");
        req.setContextPath("/gudu");
        req.addHeader("X-Client", MP);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThat(guard.preHandle(req, resp, null)).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        JsonNode body = om.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(MiniProgramCommunityGuard.CODE_COMMUNITY_CLOSED);
        assertThat(body.get("msg").asText()).contains("厨房圈");

        MockHttpServletRequest read = new MockHttpServletRequest("GET", "/gudu/community/feed");
        read.setContextPath("/gudu");
        read.addHeader("X-Client", MP);
        MockHttpServletResponse ok = new MockHttpServletResponse();
        assertThat(guard.preHandle(read, ok, null)).isTrue();
        assertThat(ok.getStatus()).isEqualTo(200);
    }
}
