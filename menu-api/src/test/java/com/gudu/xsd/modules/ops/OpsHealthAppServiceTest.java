package com.gudu.xsd.modules.ops;

import com.gudu.xsd.modules.ops.application.OpsHealthAppService;
import com.gudu.xsd.modules.ops.domain.model.HealthItem;
import com.gudu.xsd.modules.ops.domain.model.HealthReport;
import com.gudu.xsd.modules.ops.domain.service.HealthProbe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpsHealthAppServiceTest {

    private static HealthProbe probe(String key, Body body) {
        return new HealthProbe() {
            public String key() { return key; }
            public String label() { return key + "标签"; }
            public String check() throws Exception { return body.run(); }
        };
    }

    @FunctionalInterface
    interface Body { String run() throws Exception; }

    @Test
    void 全部正常_汇总ok() {
        OpsHealthAppService svc = new OpsHealthAppService(
                List.of(probe("a", () -> "好"), probe("b", () -> "也好")), 2000);
        HealthReport r = svc.check();
        assertThat(r.status()).isEqualTo("ok");
        assertThat(r.items()).extracting(HealthItem::status).containsOnly("ok");
        assertThat(r.items().get(0).detail()).isEqualTo("好");
        assertThat(r.items().get(0).latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void 单项异常_不影响其他项_且不泄露连接串密码() {
        OpsHealthAppService svc = new OpsHealthAppService(List.of(
                probe("db", () -> { throw new IllegalStateException("连接失败 jdbc:mysql://10.0.0.1:3306/x password=abc123"); }),
                probe("redis", () -> "ok")), 2000);
        HealthReport r = svc.check();
        assertThat(r.status()).isEqualTo("err");
        HealthItem db = r.items().get(0);
        assertThat(db.status()).isEqualTo("err");
        assertThat(db.error()).doesNotContain("10.0.0.1").doesNotContain("abc123").doesNotContain("jdbc:");
        assertThat(r.items().get(1).status()).isEqualTo("ok");
    }

    @Test
    void 单项超时_记为异常_其他项照常() {
        OpsHealthAppService svc = new OpsHealthAppService(List.of(
                probe("slow", () -> { Thread.sleep(5000); return "不会到这"; }),
                probe("fast", () -> "ok")), 300);
        long t0 = System.currentTimeMillis();
        HealthReport r = svc.check();
        assertThat(System.currentTimeMillis() - t0).isLessThan(2000);
        assertThat(r.items().get(0).status()).isEqualTo("err");
        assertThat(r.items().get(0).error()).contains("超时");
        assertThat(r.items().get(1).status()).isEqualTo("ok");
    }

    @Test
    void 错误摘要过长会截断() {
        assertThat(HealthItem.sanitize("x".repeat(500)).length()).isLessThanOrEqualTo(81);
        assertThat(HealthItem.sanitize(null)).isEqualTo("未知错误");
    }
}
