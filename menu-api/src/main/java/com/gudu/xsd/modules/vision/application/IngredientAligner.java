package com.gudu.xsd.modules.vision.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.pantry.application.StockAppService;
import com.gudu.xsd.modules.vision.domain.model.ConfidenceLevel;
import com.gudu.xsd.modules.vision.domain.model.RawItem;
import com.gudu.xsd.modules.vision.domain.model.RecognizedItem;
import com.gudu.xsd.modules.vision.domain.service.ConfidencePolicy;
import com.gudu.xsd.modules.vision.domain.service.IngredientNameMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 识别结果 → 食材字典对齐 + 置信分级 + 保质期建议。字典与别名表（classpath:vision/ingredient-alias.json，
 * 与种子脚本同源）缓存 10 分钟。同一食材多次出现合并（数量相加、置信取高）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngredientAligner {

    static final String ALIAS_RESOURCE = "vision/ingredient-alias.json";
    static final long CACHE_MINUTES = 10;

    private final IngredientService ingredientService;
    private final StockAppService stockAppService;
    private final Clock clock;

    /** 字典按「可见范围」缓存：公共 + 本家私有食材因家庭而异，不能跨家庭共用（key = 有效家庭 id，无家庭为 -1）。 */
    private final Map<Long, Cached> cache = new java.util.concurrent.ConcurrentHashMap<>();

    private record Cached(IngredientNameMatcher matcher, Instant loadedAt) {
    }

    public List<RecognizedItem> align(List<RawItem> raws) {
        IngredientNameMatcher m = matcher();
        Map<String, RecognizedItem> merged = new LinkedHashMap<>();
        Map<Long, LocalDate> bestBeforeCache = new HashMap<>();
        LocalDate today = LocalDate.now(clock);
        for (RawItem raw : raws == null ? List.<RawItem>of() : raws) {
            Optional<ConfidenceLevel> level = ConfidencePolicy.classify(raw.confidence());
            if (level.isEmpty()) continue; // < 0.5 不展示
            Optional<IngredientNameMatcher.Match> match = m.match(raw.name());
            Long ingId = match.map(x -> x.entry().id()).orElse(null);
            String name = match.map(x -> x.entry().name()).orElse(raw.name());
            String icon = match.map(x -> x.entry().iconKey()).orElse(null);
            LocalDate bestBefore = ingId == null ? null
                    : bestBeforeCache.computeIfAbsent(ingId, id -> suggestBestBefore(id, today));
            RecognizedItem item = new RecognizedItem(name, raw.name(), ingId, icon, raw.amount(), raw.unit(),
                    ConfidencePolicy.normalize(raw.confidence()), level.get(), raw.alternatives(), bestBefore);
            String key = ingId != null ? "id:" + ingId : "name:" + name;
            merged.merge(key, item, IngredientAligner::combine);
        }
        // 待确认的排前面（原型「请确认」组在上），组内保持识别顺序
        List<RecognizedItem> out = new ArrayList<>(merged.values());
        out.sort((a, b) -> Boolean.compare(a.level() != ConfidenceLevel.CONFIRM, b.level() != ConfidenceLevel.CONFIRM));
        return out;
    }

    /** 名称 → 食材（菜谱截图用料行对齐，未匹配返回 empty）。 */
    public Optional<IngredientNameMatcher.Entry> lookup(String name) {
        return matcher().match(name).map(IngredientNameMatcher.Match::entry);
    }

    private static RecognizedItem combine(RecognizedItem a, RecognizedItem b) {
        BigDecimal amount = a.amount().add(b.amount());
        RecognizedItem hi = a.confidence() >= b.confidence() ? a : b;
        return new RecognizedItem(hi.name(), hi.rawName(), hi.ingredientId(), hi.iconKey(), amount, hi.unit(),
                hi.confidence(), hi.level(), hi.alternatives(), hi.suggestedBestBefore());
    }

    private LocalDate suggestBestBefore(Long ingredientId, LocalDate today) {
        try {
            Integer days = stockAppService.suggestShelfLifeDays(ingredientId);
            return days == null ? null : today.plusDays(days);
        } catch (RuntimeException e) {
            return null;
        }
    }

    IngredientNameMatcher matcher() {
        Instant now = clock.instant();
        Long familyId = com.gudu.xsd.common.tenant.DishVisibility.effectiveFamilyId();
        Long key = familyId == null ? -1L : familyId;
        Cached c = cache.get(key);
        if (c == null || c.loadedAt().plus(CACHE_MINUTES, ChronoUnit.MINUTES).isBefore(now)) {
            List<IngredientNameMatcher.Entry> entries = ingredientService.list().stream()
                    .filter(i -> i.getName() != null && (i.getDeleted() == null || i.getDeleted() == 0))
                    .map(IngredientAligner::entryOf).toList();
            // 内置别名 JSON + 运营维护的别名表（后者覆盖前者）
            Map<String, String> alias = new HashMap<>(loadAlias());
            alias.putAll(ingredientService.aliasToNameMap());
            c = new Cached(new IngredientNameMatcher(entries, alias), now);
            cache.put(key, c);
        }
        return c.matcher();
    }

    private static IngredientNameMatcher.Entry entryOf(Ingredient i) {
        return new IngredientNameMatcher.Entry(i.getId(), i.getName(), i.getIconKey(), i.getPurchaseCategoryId());
    }

    static Map<String, String> loadAlias() {
        try (InputStream in = new ClassPathResource(ALIAS_RESOURCE).getInputStream()) {
            JsonNode alias = new ObjectMapper().readTree(in).path("alias");
            return new ObjectMapper().convertValue(alias, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            log.warn("食材别名表加载失败，只做精确/包含匹配: {}", e.getMessage());
            return Map.of();
        }
    }
}
