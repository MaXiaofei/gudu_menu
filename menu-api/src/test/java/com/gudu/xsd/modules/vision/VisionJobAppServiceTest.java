package com.gudu.xsd.modules.vision;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.pantry.PantryService;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.vision.application.IngredientAligner;
import com.gudu.xsd.modules.vision.application.VisionJobAppService;
import com.gudu.xsd.modules.vision.application.dto.ApplyItem;
import com.gudu.xsd.modules.vision.application.dto.VisionJobView;
import com.gudu.xsd.modules.vision.domain.model.ConfidenceLevel;
import com.gudu.xsd.modules.vision.domain.model.RawItem;
import com.gudu.xsd.modules.vision.domain.model.RecognizedItem;
import com.gudu.xsd.modules.vision.domain.model.VisionJob;
import com.gudu.xsd.modules.vision.domain.port.ImageStore;
import com.gudu.xsd.modules.vision.domain.port.VisionProvider;
import com.gudu.xsd.modules.vision.domain.repository.VisionJobRepository;
import com.gudu.xsd.modules.vision.infrastructure.MockVisionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisionJobAppServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneId.of("Asia/Shanghai"));
    static final String IMG = "/gudu/uploads/original/20261016/1.jpg";

    private final InMemoryRepo repo = new InMemoryRepo();
    private final IngredientService ingredientService = mock(IngredientService.class);
    private final StockAppService stockAppService = mock(StockAppService.class);
    private final PantryService pantryService = mock(PantryService.class);
    private final ImageStore imageStore = mock(ImageStore.class);
    private final com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService contentSecurity =
            mock(com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService.class);
    private IngredientAligner aligner;
    private VisionJobAppService svc;

    @BeforeEach
    void setUp() throws Exception {
        when(ingredientService.list()).thenReturn(List.of(ing(1L, "番茄", "tomato"), ing(2L, "鸡蛋", "egg"),
                ing(3L, "菠菜", "spinach"), ing(4L, "北豆腐", "tofu"), ing(5L, "小葱", "scallion")));
        when(stockAppService.suggestShelfLifeDays(anyLong())).thenReturn(5);
        when(contentSecurity.reviewAiText(any(), any())).thenAnswer(i -> i.getArgument(1)); // 默认放行
        aligner = new IngredientAligner(ingredientService, stockAppService, CLOCK);
        svc = newService(new MockVisionProvider());
    }

    private VisionJobAppService newService(VisionProvider provider) throws Exception {
        VisionJobAppService s = new VisionJobAppService(repo, provider, aligner, pantryService, stockAppService,
                imageStore, CLOCK, contentSecurity);
        Method m = VisionJobAppService.class.getDeclaredMethod("useExecutor", Executor.class);
        m.setAccessible(true);
        m.invoke(s, (Executor) Runnable::run); // 同步执行
        return s;
    }

    private static Ingredient ing(Long id, String name, String icon) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        i.setIconKey(icon);
        return i;
    }

    @Test
    void 对齐_别名_分级_丢弃低置信_合并_待确认排前() {
        List<RecognizedItem> out = aligner.align(List.of(
                raw("鸡蛋", 6, 0.98), raw("西红柿", 3, 0.94), raw("菠菜", 1, 0.72), raw("柠檬", 1, 0.31),
                raw("土鸡蛋", 4, 0.85), raw("紫甘蓝", 1, 0.66)));
        assertThat(out).extracting(RecognizedItem::name).containsExactly("菠菜", "紫甘蓝", "鸡蛋", "番茄");
        RecognizedItem egg = out.get(2);
        assertThat(egg.amount()).isEqualByComparingTo("10"); // 鸡蛋 + 土鸡蛋 合并
        assertThat(egg.level()).isEqualTo(ConfidenceLevel.AUTO);
        RecognizedItem tomato = out.get(3);
        assertThat(tomato.rawName()).isEqualTo("西红柿");
        assertThat(tomato.ingredientId()).isEqualTo(1L);
        assertThat(tomato.iconKey()).isEqualTo("tomato");
        assertThat(tomato.suggestedBestBefore()).isEqualTo(LocalDate.of(2026, 10, 21));
        RecognizedItem unknown = out.get(1);
        assertThat(unknown.ingredientId()).isNull(); // 没匹配上：入库时按名新建
        assertThat(unknown.suggestedBestBefore()).isNull();
        assertThat(out.get(0).level()).isEqualTo(ConfidenceLevel.CONFIRM);
    }

    @Test
    void 内容安全只在识别完成时检测一次_多次GET不再调用_违规项剔除后落库() {
        when(contentSecurity.reviewAiText(any(), eq("鸡蛋"))).thenReturn(
                com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService.AI_TEXT_REPLACED);
        VisionJobView created = svc.create(7L, "FRIDGE", IMG);
        org.mockito.Mockito.clearInvocations(contentSecurity); // 记下识别完成之后的调用次数基线
        VisionJobView first = svc.get(7L, created.jobId());
        svc.get(7L, created.jobId());
        svc.get(7L, created.jobId());
        verify(contentSecurity, never()).reviewAiText(any(), any()); // GET/轮询不再检测
        assertThat(first.items()).extracting(RecognizedItem::name).doesNotContain("鸡蛋"); // 已处理结果被持久化
        assertThat(first.items()).isNotEmpty();
    }

    @Test
    void 建任务_识别完成_放进余量_只能一次() {
        VisionJobView created = svc.create(7L, "FRIDGE", IMG);
        VisionJobView v = svc.get(7L, created.jobId());
        assertThat(v.status()).isEqualTo("DONE");
        // Mock：10 项，柠檬 0.31 丢弃 → 9 项，其中 2 项待确认
        assertThat(v.items()).hasSize(9);
        assertThat(v.items().stream().filter(i -> i.level() == ConfidenceLevel.CONFIRM)).hasSize(2);
        assertThat(v.items()).anySatisfy(i -> {
            assertThat(i.rawName()).isEqualTo("西红柿");
            assertThat(i.name()).isEqualTo("番茄");
        });

        when(pantryService.manualAdd(eq(1L), any(), eq("ENOUGH"), any())).thenReturn(1L);
        when(pantryService.manualAdd(isNull(), eq("E2E视觉菜心"), eq("ENOUGH"), any())).thenReturn(99L);
        int added = svc.apply(7L, created.jobId(), List.of(
                new ApplyItem("番茄", 1L, BigDecimal.valueOf(3), "个", LocalDate.of(2026, 10, 21)),
                new ApplyItem("E2E视觉菜心", null, BigDecimal.ONE, "把", null),
                new ApplyItem(" ", null, BigDecimal.ONE, null, null)));
        assertThat(added).isEqualTo(2);
        verify(stockAppService).changeBestBefore(1L, LocalDate.of(2026, 10, 21));
        verify(stockAppService, never()).changeBestBefore(eq(99L), any());
        assertThatThrownBy(() -> svc.apply(7L, created.jobId(), List.of(new ApplyItem("番茄", 1L, null, null, null))))
                .hasMessageContaining("已经放进余量");
    }

    @Test
    void 别人的任务看不到_空入库被拒() {
        VisionJobView created = svc.create(7L, "RECEIPT", IMG);
        assertThatThrownBy(() -> svc.get(8L, created.jobId())).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> svc.apply(7L, created.jobId(), List.of())).hasMessageContaining("至少选择");
    }

    @Test
    void 菜谱截图_用料对齐成IngredientLine() {
        VisionJobView created = svc.create(7L, "RECIPE", IMG);
        VisionJobView v = svc.get(7L, created.jobId());
        assertThat(v.recipe().title()).isEqualTo("滑蛋虾仁");
        assertThat(v.recipe().steps()).hasSize(4);
        assertThat(v.recipe().ingredients()).anySatisfy(l -> {
            assertThat(l.name()).isEqualTo("鸡蛋");
            assertThat(l.ingredientId()).isEqualTo(2L);
            assertThat(l.amount()).isEqualTo("3");
        });
        assertThat(v.recipe().ingredients()).anySatisfy(l -> assertThat(l.amount()).isEqualTo("适量"));
    }

    @Test
    void 识别异常记为失败() throws Exception {
        VisionJobAppService failing = newService(new VisionProvider() {
            @Override
            public com.gudu.xsd.modules.vision.domain.model.RecognitionResult recognize(
                    com.gudu.xsd.modules.vision.domain.model.VisionMode mode, String imageUrl) {
                throw new IllegalArgumentException("模型返回的不是 JSON");
            }

            @Override
            public String name() {
                return "broken";
            }
        });
        VisionJobView created = failing.create(7L, "FRIDGE", IMG);
        VisionJobView v = failing.get(7L, created.jobId());
        assertThat(v.status()).isEqualTo("FAILED");
        assertThat(v.error()).contains("识别失败");
    }

    @Test
    void 定时清理_过期原图删除() {
        VisionJob old = VisionJob.create(7L, com.gudu.xsd.modules.vision.domain.model.VisionMode.FRIDGE, IMG,
                LocalDateTime.now(CLOCK).minusHours(25));
        repo.save(old);
        svc.cleanup();
        verify(imageStore, times(1)).delete(IMG);
        assertThat(repo.findById(old.id()).orElseThrow().imageUrl()).isNull();
    }

    private static RawItem raw(String name, int amount, double confidence) {
        return new RawItem(name, BigDecimal.valueOf(amount), "个", confidence, List.of(), null);
    }

    /** 内存仓储（只存引用，够应用层测试用）。 */
    static class InMemoryRepo implements VisionJobRepository {
        @Override
        public int reassignAccount(Long from, Long to) {
            return 0;
        }

        private final Map<Long, VisionJob> store = new HashMap<>();
        private long seq = 1;

        @Override
        public void save(VisionJob job) {
            if (job.id() == null) job.identify(seq++);
            store.put(job.id(), job);
        }

        @Override
        public Optional<VisionJob> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<VisionJob> findWithImageCreatedBefore(LocalDateTime createdBefore, int limit) {
            List<VisionJob> out = new ArrayList<>();
            for (VisionJob j : store.values()) {
                if (j.imageUrl() != null && j.createdAt().isBefore(createdBefore)) out.add(j);
            }
            return out;
        }

        @Override
        public List<VisionJob> findByMember(Long memberId) {
            return store.values().stream().filter(j -> memberId.equals(j.memberId())).toList();
        }

        @Override
        public int deleteByMember(Long memberId) {
            int before = store.size();
            store.values().removeIf(j -> memberId.equals(j.memberId()));
            return before - store.size();
        }

        @Override
        public int deleteCreatedBefore(LocalDateTime createdBefore) {
            int before = store.size();
            store.values().removeIf(j -> j.createdAt().isBefore(createdBefore));
            return before - store.size();
        }
    }
}
