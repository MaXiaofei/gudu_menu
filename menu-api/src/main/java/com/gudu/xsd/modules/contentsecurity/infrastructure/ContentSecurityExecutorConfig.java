package com.gudu.xsd.modules.contentsecurity.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 上传后提交图片检测用的小线程池（有界队列，满了拒绝——不影响上传，头像保存时会同步补提交）。 */
@Configuration
public class ContentSecurityExecutorConfig {

    @Bean(name = "contentSecurityExecutor")
    public Executor contentSecurityExecutor() {
        AtomicInteger seq = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200),
                r -> {
                    Thread t = new Thread(r, "content-security-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.AbortPolicy());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
}
