package com.gudu.xsd.modules.vision.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.vision.domain.model.RawItem;
import com.gudu.xsd.modules.vision.domain.model.RawRecipe;
import com.gudu.xsd.modules.vision.domain.model.RecognitionResult;
import com.gudu.xsd.modules.vision.domain.model.VisionMode;
import com.gudu.xsd.modules.vision.domain.service.ChineseQuantityParser;
import com.gudu.xsd.modules.vision.domain.service.ConfidencePolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型输出健壮解析：容忍 ```json 围栏与前后废话、顶层直接是数组、字段缺失/类型不对、
 * confidence 为百分制或字符串（"92%"）、amount 为中文（「两个」「半盒」）或带单位文本。
 * 名称为空的项丢弃；RECIPE 模式下 items 缺失时由用料行补出（confidence 记 0.9）。
 */
public final class VisionOutputParser {

    static final double DEFAULT_CONFIDENCE = 0.6;
    static final double RECIPE_LINE_CONFIDENCE = 0.9;
    private static final int MAX_ITEMS = 50;
    private static final int MAX_NAME = 20;
    private static final ObjectMapper JSON = new ObjectMapper();

    private VisionOutputParser() {
    }

    /** @throws IllegalArgumentException 文本里找不到可解析的 JSON */
    public static RecognitionResult parse(String modelText, VisionMode mode) {
        JsonNode root = readJson(modelText);
        JsonNode itemsNode = root.isArray() ? root : root.path("items");
        List<RawItem> items = new ArrayList<>();
        if (itemsNode.isArray()) {
            for (JsonNode n : itemsNode) {
                RawItem it = item(n);
                if (it != null) items.add(it);
                if (items.size() >= MAX_ITEMS) break;
            }
        }
        RawRecipe recipe = null;
        if (mode == VisionMode.RECIPE && root.isObject()) {
            recipe = recipe(root);
            if (items.isEmpty()) {
                for (RawRecipe.Line l : recipe.ingredients()) {
                    ChineseQuantityParser.Quantity q = ChineseQuantityParser.parse(l.amount());
                    items.add(new RawItem(l.name(), q.amount(), l.unit() != null ? l.unit() : q.unit(),
                            RECIPE_LINE_CONFIDENCE, List.of(), null));
                }
            }
        }
        return new RecognitionResult(items, recipe);
    }

    static JsonNode readJson(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("模型没有返回内容");
        String t = text.trim();
        int fence = t.indexOf("```");
        if (fence >= 0) {
            int start = t.indexOf('\n', fence);
            int end = t.indexOf("```", start < 0 ? fence + 3 : start);
            if (start > 0 && end > start) t = t.substring(start + 1, end).trim();
        }
        int obj = t.indexOf('{');
        int arr = t.indexOf('[');
        int begin = obj < 0 ? arr : (arr < 0 ? obj : Math.min(obj, arr));
        if (begin < 0) throw new IllegalArgumentException("模型返回的不是 JSON");
        char close = t.charAt(begin) == '{' ? '}' : ']';
        int last = t.lastIndexOf(close);
        if (last <= begin) throw new IllegalArgumentException("模型返回的 JSON 不完整");
        try {
            return JSON.readTree(t.substring(begin, last + 1));
        } catch (Exception e) {
            throw new IllegalArgumentException("模型返回的 JSON 无法解析", e);
        }
    }

    static RawItem item(JsonNode n) {
        if (n == null) return null;
        String name = n.isTextual() ? n.asText() : text(n, "name");
        if (name == null || name.isBlank()) return null;
        name = name.trim();
        if (name.length() > MAX_NAME) name = name.substring(0, MAX_NAME);
        BigDecimal amount;
        String unit = text(n, "unit");
        JsonNode a = n.path("amount");
        if (a.isNumber() && a.decimalValue().signum() > 0) {
            amount = a.decimalValue().setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        } else {
            ChineseQuantityParser.Quantity q = ChineseQuantityParser.parse(a.isTextual() ? a.asText() : text(n, "quantity"));
            amount = q.amount();
            if (unit == null) unit = q.unit();
        }
        List<String> alternatives = new ArrayList<>();
        JsonNode alt = n.path("alternatives");
        if (alt.isArray()) {
            alt.forEach(x -> {
                if (x.isTextual() && !x.asText().isBlank() && alternatives.size() < 3) alternatives.add(x.asText().trim());
            });
        }
        return new RawItem(name, amount, unit, confidence(n.path("confidence")), alternatives, priceFen(n.path("price")));
    }

    static double confidence(JsonNode c) {
        if (c.isNumber()) return ConfidencePolicy.normalize(c.asDouble());
        if (c.isTextual()) {
            String s = c.asText().trim().replace("%", "");
            try {
                return ConfidencePolicy.normalize(Double.parseDouble(s));
            } catch (NumberFormatException e) {
                return switch (s) {
                    case "高", "high" -> 0.9;
                    case "中", "medium" -> 0.65;
                    case "低", "low" -> 0.3;
                    default -> DEFAULT_CONFIDENCE;
                };
            }
        }
        return DEFAULT_CONFIDENCE;
    }

    static Integer priceFen(JsonNode p) {
        try {
            BigDecimal yuan = p.isNumber() ? p.decimalValue()
                    : p.isTextual() ? new BigDecimal(p.asText().replaceAll("[^0-9.]", "")) : null;
            if (yuan == null || yuan.signum() < 0) return null;
            return yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static RawRecipe recipe(JsonNode root) {
        String title = text(root, "title");
        List<RawRecipe.Line> lines = new ArrayList<>();
        JsonNode ings = root.path("ingredients");
        if (ings.isArray()) {
            for (JsonNode n : ings) {
                String name = n.isTextual() ? n.asText() : text(n, "name");
                if (name == null || name.isBlank()) continue;
                String amount = n.path("amount").isNumber() ? n.path("amount").asText() : text(n, "amount");
                lines.add(new RawRecipe.Line(name.trim(), amount, text(n, "unit")));
            }
        }
        List<String> steps = new ArrayList<>();
        JsonNode st = root.path("steps");
        if (st.isArray()) {
            for (JsonNode s : st) {
                String v = s.isTextual() ? s.asText() : text(s, "text");
                if (v != null && !v.isBlank()) steps.add(v.trim().replaceFirst("^\\d+[.、)）]\\s*", ""));
            }
        }
        return new RawRecipe(title == null ? "未命名菜谱" : title.trim(), lines, steps);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s.trim();
    }
}
