package com.gudu.xsd.modules.ops.application;

import com.gudu.xsd.modules.ops.domain.model.HealthItem;
import com.gudu.xsd.modules.ops.domain.model.HealthReport;
import com.gudu.xsd.modules.ops.domain.service.HealthProbe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 系统健康摘要：并行执行各探测，单项超时（默认 2 秒）后记为异常，互不影响。
 */
@Service
public class OpsHealthAppService {

    private final List<HealthProbe> probes;
    private final long timeoutMs;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ops-health-probe");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public OpsHealthAppService(List<HealthProbe> probes,
                               @Value("${gudu.ops.health.timeout-ms:2000}") long timeoutMs) {
        this.probes = probes;
        this.timeoutMs = timeoutMs;
    }

    public HealthReport check() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        List<Future<HealthItem>> futures = new ArrayList<>();
        for (HealthProbe p : probes) {
            futures.add(pool.submit(() -> run(p)));
        }
        List<HealthItem> items = new ArrayList<>();
        for (int i = 0; i < probes.size(); i++) {
            HealthProbe p = probes.get(i);
            Future<HealthItem> f = futures.get(i);
            try {
                long left = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
                items.add(f.get(left, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                f.cancel(true);
                items.add(HealthItem.fail(p.key(), p.label(), timeoutMs, "超时（超过 " + timeoutMs + " 毫秒）"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                items.add(HealthItem.fail(p.key(), p.label(), 0, "探测被中断"));
            } catch (Exception e) {
                items.add(HealthItem.fail(p.key(), p.label(), 0, describe(e)));
            }
        }
        return HealthReport.of(items);
    }

    private HealthItem run(HealthProbe p) {
        long start = System.nanoTime();
        try {
            String detail = p.check();
            return HealthItem.ok(p.key(), p.label(), elapsedMs(start), detail);
        } catch (Exception e) {
            return HealthItem.fail(p.key(), p.label(), elapsedMs(start), describe(e));
        }
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = root.getMessage();
        return root.getClass().getSimpleName() + (msg == null ? "" : "：" + msg);
    }
}
