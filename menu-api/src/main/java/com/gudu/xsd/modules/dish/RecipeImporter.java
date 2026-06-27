package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL 导入菜谱：Jsoup 抓网页 → 按 host 分发解析 → 装配 {@link DishSaveDTO}。
 *
 * <p>纯 Java 解析，不调 AI。针对下厨房/美食杰/豆果美食三类站用 CSS 选择器提取，
 * 其他站兜底（title 作菜名 + 正文尽力提取），并在 note 标注「该网站可能解析不全」。
 *
 * <p>食材匹配：抓到的食材名按 ingredient.name 精确/模糊匹配，命中的关联 ingredientId + 解析出克数；
 * 未命中的留名进入 note 的食材清单（dish_ingredient 表无 name 列，不关联）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeImporter {

    private final IngredientMapper ingredientMapper;

    /** 仿真 UA，避免被基础 UA 过滤挡掉。 */
    private static final String UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final int TIMEOUT_MS = 10_000;

    /** 抓网页 → 解析 → 装配 DTO（不落库，落库交给 DishService.saveFull）。 */
    public DishSaveDTO importFromUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BizException("URL 不能为空");
        }
        String trimmed = url.trim();
        Document doc = fetch(trimmed);

        ParsedRecipe parsed;
        String host = hostOf(trimmed);
        if (host.contains("xiachufang.com")) {
            parsed = parseXiachufang(doc);
        } else if (host.contains("meishij.net")) {
            parsed = parseMeishij(doc);
        } else if (host.contains("douguo.com")) {
            parsed = parseDouguo(doc);
        } else {
            parsed = parseGeneric(doc);
            parsed.unknownSite = true;
        }

        if (parsed.name == null || parsed.name.isBlank()) {
            throw new BizException("未能解析菜谱，请检查 URL");
        }
        return assemble(parsed, trimmed);
    }

    // ===== 抓取 =====

    private Document fetch(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .get();
        } catch (java.net.SocketTimeoutException e) {
            throw new BizException("抓取超时，请稍后重试");
        } catch (org.jsoup.HttpStatusException e) {
            int code = e.getStatusCode();
            if (code == 403 || code == 401) {
                throw new BizException("抓取失败，网站拒绝访问（" + code + "）");
            }
            throw new BizException("抓取失败（HTTP " + code + "），可能网站限制");
        } catch (IllegalArgumentException e) {
            throw new BizException("URL 格式不正确");
        } catch (Exception e) {
            log.warn("import-url 抓取失败 url={} err={}", url, e.toString());
            throw new BizException("抓取失败，可能网站限制或网络异常");
        }
    }

    private static String hostOf(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getHost() == null ? "" : u.getHost().toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    // ===== 下厨房 =====

    private ParsedRecipe parseXiachufang(Document doc) {
        ParsedRecipe r = new ParsedRecipe();
        // 菜名：优先结构化选择器，兜底 h1 / title
        r.name = firstText(doc,
                ".recipe-show-name", "h1.recipe-name", "h1");
        // 封面
        r.coverUrl = firstAttr(doc, "img.recipe-show-img", "src",
                ".recipe-show img", "src", "meta[property=og:image]", "content");

        // 食材：下厨房新版结构 .ingredients li，旧版 .ings tr
        Elements ingEls = doc.select(".ingredients li, .ings tr, .ingredient-list li");
        for (Element el : ingEls) {
            String line = el.text();
            if (line == null || line.isBlank()) continue;
            // 过滤明显的分组标题（如「主料」「辅料」）
            if (looksLikeGroupHeader(line)) continue;
            r.ingredients.add(normalizeIngredientLine(line));
        }

        // 步骤
        r.steps = extractSteps(doc, ".steps .step", ".recipe-step",
                ".step .text", ".steps li");
        return r;
    }

    // ===== 美食杰 =====

    private ParsedRecipe parseMeishij(Document doc) {
        ParsedRecipe r = new ParsedRecipe();
        r.name = firstText(doc, ".recipe-title", "h1.recipe_title", "h1");
        r.coverUrl = firstAttr(doc, "meta[property=og:image]", "content",
                ".recipe_img img", "src");

        Elements ingEls = doc.select(".recipe_ingredients .right li, .recip-detail a, .ingredients li");
        for (Element el : ingEls) {
            String line = el.text();
            if (line == null || line.isBlank() || looksLikeGroupHeader(line)) continue;
            r.ingredients.add(normalizeIngredientLine(line));
        }

        r.steps = extractSteps(doc, ".recipe_step", ".step_content",
                ".recipe-step", ".steps li");
        return r;
    }

    // ===== 豆果美食 =====

    private ParsedRecipe parseDouguo(Document doc) {
        ParsedRecipe r = new ParsedRecipe();
        r.name = firstText(doc, ".title", ".recipe-title", "h1");
        r.coverUrl = firstAttr(doc, "meta[property=og:image]", "content",
                ".recipe-img img", "src", ".main_img", "src");

        Elements ingEls = doc.select(".recipe .ingredients li, .ingredient-list li, .ingredients tr");
        for (Element el : ingEls) {
            String line = el.text();
            if (line == null || line.isBlank() || looksLikeGroupHeader(line)) continue;
            r.ingredients.add(normalizeIngredientLine(line));
        }

        r.steps = extractSteps(doc, ".step", ".recipe-step",
                ".step .text", ".steps li");
        return r;
    }

    // ===== 兜底 =====

    private ParsedRecipe parseGeneric(Document doc) {
        ParsedRecipe r = new ParsedRecipe();
        r.name = firstText(doc, "h1");
        if (r.name == null || r.name.isBlank()) {
            // 退化到 <title>
            String t = doc.title();
            if (t != null) {
                int idx = t.indexOf('_');
                r.name = idx > 0 ? t.substring(0, idx).trim() : t.trim();
            }
        }
        r.coverUrl = firstAttr(doc, "meta[property=og:image]", "content");

        // 通用：找 class 含 ingredient 的节点
        Elements ingEls = doc.select("[class*=ingredient] li, [class*=ingredients] li, [class*=material] li");
        for (Element el : ingEls) {
            String line = el.text();
            if (line == null || line.isBlank() || looksLikeGroupHeader(line)) continue;
            r.ingredients.add(normalizeIngredientLine(line));
        }

        // 通用：class 含 step 的节点
        r.steps = extractSteps(doc, "[class*=step]", "[class*=procedure]");
        return r;
    }

    // ===== 步骤抽取（多选择器兜底，text + 第一张图） =====

    private List<ParsedStep> extractSteps(Document doc, String... selectors) {
        List<ParsedStep> out = new ArrayList<>();
        for (String sel : selectors) {
            Elements els = doc.select(sel);
            if (els.isEmpty()) continue;
            for (Element el : els) {
                String text = el.text();
                if (text == null || text.isBlank()) continue;
                ParsedStep s = new ParsedStep();
                s.text = text.trim();
                Element img = el.selectFirst("img");
                if (img != null) {
                    String src = img.absUrl("src");
                    if (src == null || src.isEmpty()) src = img.attr("src");
                    s.imageUrl = src;
                }
                out.add(s);
            }
            if (!out.isEmpty()) break; // 命中一个选择器即可，避免重复
        }
        return out;
    }

    private static boolean looksLikeGroupHeader(String line) {
        String t = line.trim();
        return t.length() <= 6
                && (t.contains("主料") || t.contains("辅料") || t.contains("调料")
                || t.contains("配料") || t.contains("原料"));
    }

    // ===== 食材行：拆 名 + 用量 =====

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(克|g|kg|千克|斤|两|毫升|ml|个|只|根|片|勺|少许|适量)?",
                    Pattern.CASE_INSENSITIVE);

    /** 「番茄 300g」 → {name="番茄", amount=300}；「盐 适量」→ {name="盐", amount=null, unit="适量"}。 */
    private static ParsedIngredient normalizeIngredientLine(String raw) {
        String line = raw.replaceAll("\\s+", " ").trim();

        // 先识别「适量/少许」等非数字量词（单独成段时），剥离出来
        String fuzzyUnit = null;
        for (String kw : new String[]{"适量", "少许", "一点点", "一小撮"}) {
            int idx = line.indexOf(kw);
            if (idx >= 0) {
                fuzzyUnit = kw;
                line = (line.substring(0, idx) + " " + line.substring(idx + kw.length())).replaceAll("\\s+", " ").trim();
                break;
            }
        }

        Matcher m = AMOUNT_PATTERN.matcher(line);
        BigDecimal amount = null;
        String unit = null;
        int amountStart = -1, amountEnd = -1;
        if (m.find()) {
            try {
                amount = new BigDecimal(m.group(1));
                unit = m.group(2);
                amountStart = m.start();
                amountEnd = m.end();
            } catch (NumberFormatException ignored) {
            }
        }
        String name;
        if (amountStart >= 0) {
            // 名字 = 量词前/后去掉用量那一段的剩余
            String before = amountStart > 0 ? line.substring(0, amountStart).trim() : "";
            String after = amountEnd < line.length() ? line.substring(amountEnd).trim() : "";
            name = before.isEmpty() ? after : before;
        } else {
            name = line;
        }
        name = name.replaceAll("[、,，:：]", "").trim();

        ParsedIngredient p = new ParsedIngredient();
        p.name = name;
        p.amount = amount;
        p.unit = unit != null ? unit : fuzzyUnit;
        return p;
    }

    // ===== 装配 DishSaveDTO =====

    private DishSaveDTO assemble(ParsedRecipe parsed, String sourceUrl) {
        Dish dish = new Dish();
        dish.setName(parsed.name);
        dish.setCoverUrl(parsed.coverUrl);

        StringBuilder note = new StringBuilder();
        if (parsed.unknownSite) {
            note.append("【URL 导入】该网站结构未专门适配，解析可能不全。");
        } else {
            note.append("【URL 导入】");
        }
        note.append(" 来源：").append(sourceUrl);

        List<DishStep> steps = new ArrayList<>();
        if (parsed.steps != null && !parsed.steps.isEmpty()) {
            for (int i = 0; i < parsed.steps.size(); i++) {
                ParsedStep ps = parsed.steps.get(i);
                DishStep s = new DishStep();
                s.setSeq(i + 1);
                s.setSortOrder(i + 1);
                s.setText(ps.text);
                if (ps.imageUrl != null && !ps.imageUrl.isBlank()) {
                    s.setImages(ps.imageUrl);
                }
                steps.add(s);
            }
        }

        // 食材：按 name 查 ingredient 表，命中的关联 ingredientId；未命中的写入 note 清单
        List<DishIngredient> matched = new ArrayList<>();
        List<String> unmatchedNames = new ArrayList<>();
        if (parsed.ingredients != null) {
            for (ParsedIngredient ing : parsed.ingredients) {
                if (ing.name == null || ing.name.isBlank()) continue;
                Ingredient hit = lookupIngredient(ing.name);
                if (hit != null) {
                    DishIngredient di = new DishIngredient();
                    di.setIngredientId(hit.getId());
                    di.setAmount(ing.amount);
                    matched.add(di);
                } else {
                    // 把用量带进未匹配清单，避免信息丢失
                    if (ing.amount != null) {
                        unmatchedNames.add(ing.name + " " + ing.amount + (ing.unit != null ? ing.unit : ""));
                    } else {
                        unmatchedNames.add(ing.name);
                    }
                }
            }
        }
        if (!matched.isEmpty()) {
            note.append(" | 已匹配食材 ").append(matched.size()).append(" 项");
        }
        if (!unmatchedNames.isEmpty()) {
            note.append(" | 未匹配食材：").append(String.join("、", unmatchedNames));
        }

        dish.setNote(note.toString());

        DishSaveDTO dto = new DishSaveDTO();
        dto.setDish(dish);
        dto.setSteps(steps);
        dto.setIngredients(matched);
        return dto;
    }

    /** 先精确 name 命中；找不到则 LIKE 匹配（食材名常含修饰词如「番茄 圣女果」）。 */
    private Ingredient lookupIngredient(String name) {
        try {
            Ingredient exact = ingredientMapper.selectOne(
                    new QueryWrapper<Ingredient>().eq("name", name).last("LIMIT 1"));
            if (exact != null) return exact;
            return ingredientMapper.selectOne(
                    new QueryWrapper<Ingredient>().like("name", name).last("LIMIT 1"));
        } catch (Exception e) {
            log.debug("lookupIngredient 失败 name={} err={}", name, e.toString());
            return null;
        }
    }

    // ===== 小工具 =====

    private static String firstText(Document doc, String... selectors) {
        for (String sel : selectors) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                String t = el.text();
                if (t != null && !t.isBlank()) return t.trim();
            }
        }
        return null;
    }

    /** 多选择器兜底取属性：selectors 形如 sel1, attr1, sel2, attr2 ... */
    private static String firstAttr(Document doc, String... selectorAttrPairs) {
        for (int i = 0; i + 1 < selectorAttrPairs.length; i += 2) {
            String sel = selectorAttrPairs[i];
            String attr = selectorAttrPairs[i + 1];
            Element el = doc.selectFirst(sel);
            if (el != null) {
                String v;
                if ("src".equals(attr)) {
                    v = el.absUrl("src");
                    if (v == null || v.isEmpty()) v = el.attr("src");
                } else {
                    v = el.attr(attr);
                }
                if (v != null && !v.isBlank()) return v.trim();
            }
        }
        return null;
    }

    // ===== 内部数据载体 =====

    private static class ParsedRecipe {
        String name;
        String coverUrl;
        List<ParsedIngredient> ingredients = new ArrayList<>();
        List<ParsedStep> steps = new ArrayList<>();
        boolean unknownSite = false;
    }

    private static class ParsedIngredient {
        String name;
        BigDecimal amount;
        String unit;
    }

    private static class ParsedStep {
        String text;
        String imageUrl;
    }
}
