package com.gudu.xsd.modules.vision.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.pantry.PantryService;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.vision.application.dto.ApplyItem;
import com.gudu.xsd.modules.vision.application.dto.VisionJobView;
import com.gudu.xsd.modules.vision.domain.model.RawRecipe;
import com.gudu.xsd.modules.vision.domain.model.RecognitionResult;
import com.gudu.xsd.modules.vision.domain.model.VisionJob;
import com.gudu.xsd.modules.vision.domain.model.VisionMode;
import com.gudu.xsd.modules.vision.domain.port.ImageStore;
import com.gudu.xsd.modules.vision.domain.port.VisionProvider;
import com.gudu.xsd.modules.vision.domain.repository.VisionJobRepository;
import com.gudu.xsd.modules.vision.domain.service.ChineseQuantityParser;
import com.gudu.xsd.modules.vision.domain.service.IngredientNameMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 拍照识别应用服务（vision 上下文）：建任务 → 异步识别（提交事务后投递）→ 对齐字典/分级 →
 * 用户确认后批量放进余量（复用 pantry 入库）；定时清理到期原图与结果。
 */
@Slf4j
@Service
public class VisionJobAppService {

    public static final int MAX_APPLY_ITEMS = 50;
    static final int CLEANUP_BATCH = 200;
    static final String APPLY_NOTE = "拍照识别入库";
    static final String LEVEL_ENOUGH = "ENOUGH";

    private final VisionJobRepository repository;
    private final VisionProvider provider;
    private final IngredientAligner aligner;
    private final PantryService pantryService;
    private final StockAppService stockAppService;
    private final ImageStore imageStore;
    private final Clock clock;
    private Executor executor = defaultExecutor();

    public VisionJobAppService(VisionJobRepository repository, VisionProvider provider, IngredientAligner aligner,
                               PantryService pantryService, StockAppService stockAppService, ImageStore imageStore,
                               Clock clock) {
        this.repository = repository;
        this.provider = provider;
        this.aligner = aligner;
        this.pantryService = pantryService;
        this.stockAppService = stockAppService;
        this.imageStore = imageStore;
        this.clock = clock;
    }

    /** 测试注入同步执行器。 */
    void useExecutor(Executor e) {
        this.executor = e;
    }

    @Transactional(rollbackFor = Exception.class)
    public VisionJobView create(Long memberId, String mode, String imageUrl) {
        VisionJob job = VisionJob.create(memberId, VisionMode.of(mode), imageUrl, LocalDateTime.now(clock));
        repository.save(job);
        Long id = job.id();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(id);
                }
            });
        } else {
            submit(id);
        }
        return view(job);
    }

    public VisionJobView get(Long memberId, Long jobId) {
        return view(owned(memberId, jobId));
    }

    /** 执行识别（异步线程内调用；失败记 FAILED，不抛出）。 */
    void process(Long jobId) {
        Optional<VisionJob> found = repository.findById(jobId);
        if (found.isEmpty()) return;
        VisionJob job = found.get();
        try {
            RecognitionResult r = provider.recognize(job.mode(), job.imageUrl());
            job.complete(aligner.align(r.items()), r.recipe(), r.degraded());
        } catch (RuntimeException e) {
            log.warn("识别失败 job={} provider={}: {}", jobId, provider.name(), e.getMessage());
            job.fail("识别失败，请换个角度再拍一张");
        }
        repository.save(job);
    }

    /** 放进余量：逐项按 id 或名称入库（档位「充足」），有保质期则一并登记；每个任务只能放一次。 */
    @Transactional(rollbackFor = Exception.class)
    public int apply(Long memberId, Long jobId, List<ApplyItem> items) {
        VisionJob job = owned(memberId, jobId);
        if (items == null || items.isEmpty()) throw new BizException("请至少选择一样食材");
        if (items.size() > MAX_APPLY_ITEMS) throw new BizException("一次最多放进 " + MAX_APPLY_ITEMS + " 样");
        job.markApplied(LocalDateTime.now(clock));
        int added = 0;
        for (ApplyItem it : items) {
            if (it == null || (it.ingredientId() == null && (it.name() == null || it.name().isBlank()))) continue;
            Long ingId = pantryService.manualAdd(it.ingredientId(), it.name(), LEVEL_ENOUGH, APPLY_NOTE);
            if (it.bestBefore() != null) stockAppService.changeBestBefore(ingId, it.bestBefore());
            added++;
        }
        if (added == 0) throw new BizException("请至少选择一样食材");
        repository.save(job);
        return added;
    }

    /** 每小时：删 24 小时前的识别原图；删 7 天前的任务记录。 */
    @Scheduled(cron = "0 17 * * * *")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (VisionJob job : repository.findWithImageCreatedBefore(now.minusHours(VisionJob.IMAGE_TTL_HOURS), CLEANUP_BATCH)) {
            imageStore.delete(job.imageUrl());
            job.clearImage();
            repository.save(job);
        }
        int removed = repository.deleteCreatedBefore(now.minusDays(VisionJob.RESULT_TTL_DAYS));
        if (removed > 0) log.info("清理过期识别任务 {} 条", removed);
    }

    private void submit(Long id) {
        try {
            executor.execute(() -> process(id));
        } catch (RuntimeException e) { // 队列满：同步失败标记，避免任务永远 PENDING
            repository.findById(id).ifPresent(j -> {
                j.fail("识别排队人数太多，请稍后再试");
                repository.save(j);
            });
        }
    }

    private VisionJob owned(Long memberId, Long jobId) {
        VisionJob job = repository.findById(jobId).orElseThrow(() -> new BizException("识别任务不存在或已过期"));
        if (!job.ownedBy(memberId)) throw new BizException("识别任务不存在或已过期");
        return job;
    }

    VisionJobView view(VisionJob job) {
        VisionJobView.RecipeView recipe = null;
        RawRecipe r = job.recipe();
        if (r != null) {
            List<VisionJobView.Line> lines = r.ingredients().stream().map(l -> {
                Optional<IngredientNameMatcher.Entry> e = aligner.lookup(l.name());
                ChineseQuantityParser.Quantity q = ChineseQuantityParser.parse(l.amount());
                boolean vague = l.amount() == null || l.amount().contains("适量") || l.amount().contains("少许");
                return new VisionJobView.Line(e.map(IngredientNameMatcher.Entry::id).orElse(null),
                        e.map(IngredientNameMatcher.Entry::name).orElse(l.name()),
                        e.map(IngredientNameMatcher.Entry::iconKey).orElse(null),
                        vague ? (l.amount() == null ? "适量" : l.amount().trim()) : VisionJobView.plain(q.amount()),
                        l.unit() != null ? l.unit() : q.unit());
            }).toList();
            recipe = new VisionJobView.RecipeView(r.title(), lines, r.steps());
        }
        return new VisionJobView(job.id(), job.mode().name(), job.status().name(), job.error(),
                job.imageExpiresAt(), job.items(), recipe, job.degraded());
    }

    private static Executor defaultExecutor() {
        AtomicInteger seq = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "vision-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
}
