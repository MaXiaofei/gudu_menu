package com.gudu.xsd.modules.agent.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.agent.application.tools.AddPantryTool;
import com.gudu.xsd.modules.agent.application.tools.AddToMenuTool;
import com.gudu.xsd.modules.agent.application.tools.AddToShoppingTool;
import com.gudu.xsd.modules.agent.application.tools.CancelActionTool;
import com.gudu.xsd.modules.agent.application.tools.GetFamilyProfileTool;
import com.gudu.xsd.modules.agent.application.tools.GetPantryTool;
import com.gudu.xsd.modules.agent.application.tools.GetRecipeTool;
import com.gudu.xsd.modules.agent.application.tools.GetShoppingListTool;
import com.gudu.xsd.modules.agent.application.tools.ListMenusTool;
import com.gudu.xsd.modules.agent.application.tools.MarkCookedTool;
import com.gudu.xsd.modules.agent.application.tools.PlanWeekTool;
import com.gudu.xsd.modules.agent.application.tools.RecommendTonightTool;
import com.gudu.xsd.modules.agent.application.tools.RemoveFromMenuTool;
import com.gudu.xsd.modules.agent.application.tools.SearchRecipesTool;
import com.gudu.xsd.modules.agent.application.tools.UndoActionTool;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.ai.gateway.domain.model.RuleResponder;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolCall;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则意图解析：模型网关降级链的最后一环（Mock），也用于配额用尽、评测 mock 模式。
 *
 * <p>只看本轮用户原话 + 本轮已执行的工具结果，按关键词识别意图，逐步给出下一次工具调用；
 * 全部执行完后用工具摘要拼回复。确定性：同一输入同一输出。
 */
public class RuleIntentPlanner implements RuleResponder {

    private static final Pattern NUM = Pattern.compile("([一二两三四五六七八九十\\d]+)\\s*(个人|人份|个|人|口)");
    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*分钟");
    private static final Pattern MEMBERS_LINE = Pattern.compile("【成员】([^\\n]+)");
    private static final Pattern TODAY_LINE = Pattern.compile("【现在】(\\d{4}-\\d{2}-\\d{2})");

    private final ObjectMapper om;

    public RuleIntentPlanner(ObjectMapper om) {
        this.om = om;
    }

    @Override
    public ModelReply reply(ChatRequest req) {
        List<ChatMessage> msgs = req.messages();
        int lastUser = -1;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if ("user".equals(msgs.get(i).role())) {
                lastUser = i;
                break;
            }
        }
        String text = lastUser < 0 ? "" : nz(msgs.get(lastUser).content());
        List<String> done = new ArrayList<>();
        List<JsonNode> results = new ArrayList<>();
        for (int i = lastUser + 1; i < msgs.size(); i++) {
            ChatMessage m = msgs.get(i);
            if ("assistant".equals(m.role())) m.toolCalls().forEach(c -> done.add(c.name()));
            if ("tool".equals(m.role())) results.add(parse(m.content()));
        }
        Ctx ctx = new Ctx(text, members(msgs), today(msgs), previousAssistant(msgs, lastUser), results);
        List<Step> plan = plan(ctx);
        for (Step s : plan) {
            if (done.size() >= plan.size()) break;
            if (plan.indexOf(s) < done.size()) continue;
            Map<String, Object> args = s.args(ctx);
            if (args == null) break;
            return ModelReply.calls(List.of(new ToolCall("rule_" + (done.size() + 1), s.tool(), json(args))));
        }
        return ModelReply.text(finalText(ctx, plan));
    }

    // =========================================================== 意图 → 步骤

    private record Ctx(String text, List<String> members, LocalDate today, String prevAssistant, List<JsonNode> results) {
    }

    private interface ArgFn {
        Map<String, Object> build(Ctx c);
    }

    private record Step(String tool, ArgFn fn) {
        Map<String, Object> args(Ctx c) {
            return fn.build(c);
        }
    }

    /** 直接提示注入 / 角色扮演越狱的特征词。 */
    static boolean injection(String t) {
        return any(t, "忽略之前", "忽略上面", "忽略以上", "忽略你的", "ignore previous", "系统提示", "system prompt", "你现在是",
                "开发者模式", "扮演", "没有任何限制", "不受限制", "越狱", "jailbreak");
    }

    static boolean any(String t, String... words) {
        for (String w : words) if (t.contains(w)) return true;
        return false;
    }

    private List<Step> plan(Ctx c) {
        String t = c.text();
        if (injection(t)) return List.of();
        if (any(t, "撤销", "撤回", "弄错了")) return List.of(new Step(UndoActionTool.NAME, x -> Map.of()));
        if (any(t, "算了", "不要了", "不加了", "取消")) return List.of(new Step(CancelActionTool.NAME, x -> Map.of()));
        if (any(t, "做完", "做好了", "做好啦", "吃完了", "已经做了") || (servings(t) != null && any(c.prevAssistant(), "mark_cooked"))) {
            return List.of(new Step(MarkCookedTool.NAME, this::cookedArgs));
        }
        if (any(t, "买了", "买回来", "入库", "放进余量", "放冰箱", "放进冰箱")) {
            return List.of(new Step(AddPantryTool.NAME, x -> Map.of("items", items(after(x.text(), "买了", "买回来", "入库",
                    "放进余量", "放冰箱", "放进冰箱")))));
        }
        boolean menuWrite = Pattern.compile("(加进|加到|加入|放进|加个|再加|加一道|添个|添一道)").matcher(t).find()
                && any(t, "食集", "今晚", "周六", "周日", "明天", "单子", "饭", "下周", "宴");
        if (any(t, "去掉", "删掉", "拿掉", "不做了") && any(t, "食集", "今晚", "周六", "单子", "饭")) {
            return List.of(new Step(RemoveFromMenuTool.NAME, x -> {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("menu", menuRef(x.text(), x.today()) instanceof String s ? s : "今晚的饭");
                a.put("dishes", List.of(between(x.text(), "把", "去掉|删掉|拿掉|不做了")));
                return a;
            }));
        }
        if (menuWrite) {
            List<Step> steps = new ArrayList<>();
            steps.add(new Step(AddToMenuTool.NAME, this::menuArgs));
            return steps;
        }
        if (any(t, "采购", "要买", "买点", "买些", "帮我买")) {
            if (any(t, "采购单", "采购清单") && any(t, "什么", "有啥", "看看")) {
                return List.of(new Step(GetShoppingListTool.NAME, x -> Map.of()));
            }
            return List.of(new Step(AddToShoppingTool.NAME, x -> {
                String seg = between(x.text(), "把", "加到|加进|加入|放进");
                if (seg.isEmpty()) seg = after(x.text(), "加个", "加点", "加些", "再加");
                if (seg.isEmpty()) seg = after(x.text(), "采购", "要买", "买点", "买些", "帮我买");
                seg = seg.replaceAll("(到|进)?(采购单|采购清单|采购)+.*$", "");
                List<Map<String, Object>> items = items(seg);
                return items.isEmpty() ? null : Map.of("items", items);
            }));
        }
        if (any(t, "一周", "这周", "下周", "餐程", "七天")) {
            return List.of(new Step(PlanWeekTool.NAME, x -> {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("weekStart", x.today().getDayOfWeek() == DayOfWeek.MONDAY && !x.text().contains("下周")
                        ? x.today().toString() : x.today().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString());
                List<String> cons = new ArrayList<>();
                if (x.text().contains("少盐")) cons.add("少盐");
                if (x.text().contains("辅食") || x.text().contains("宝宝")) cons.add("宝宝辅食版");
                if (!cons.isEmpty()) a.put("constraints", cons);
                return a;
            }));
        }
        if (any(t, "家里人", "忌口", "谁不能吃", "家庭档案")) return List.of(new Step(GetFamilyProfileTool.NAME, x -> Map.of()));
        if (any(t, "食集", "安排了什么", "哪些单子") || (any(t, "今晚的饭", "单子", "周六") && any(t, "有啥", "有什么", "哪些菜"))) {
            return List.of(new Step(ListMenusTool.NAME, x -> Map.of()));
        }
        if (any(t, "怎么做", "做法", "步骤", "教我做", "怎么弄", "要哪些料", "用什么料", "需要什么料", "配料", "用料")) {
            return List.of(new Step(GetRecipeTool.NAME, x -> Map.of("dish", before(x.text(), "怎么做", "做法", "步骤",
                    "怎么弄", "要哪些料", "用什么料", "需要什么料", "配料", "用料", "的").replaceFirst("^(教我做|教我)", ""))));
        }
        if (any(t, "冰箱", "余量", "快坏", "到期", "过期", "还有", "有没有", "够不够", "剩")) {
            boolean expiring = any(t, "快坏", "到期", "过期", "不新鲜");
            List<Step> steps = new ArrayList<>();
            steps.add(new Step(GetPantryTool.NAME, x -> {
                if (expiring) return Map.of("expiringDays", 3);
                String name = before(x.text(), "还有", "有没有", "够不够", "剩");
                return name.isEmpty() || name.length() > 8 ? Map.of() : Map.of("names", List.of(name));
            }));
            if (any(t, "做", "吃", "能")) {
                steps.add(new Step(SearchRecipesTool.NAME, x -> {
                    Map<String, Object> a = searchArgs(x);
                    List<String> ings = new ArrayList<>();
                    for (JsonNode r : x.results()) {
                        for (JsonNode it : r.path("data").path("items")) {
                            if (!"OUT".equals(it.path("status").asText())) ings.add(it.path("name").asText());
                        }
                    }
                    if (!ings.isEmpty()) a.put("ingredients", ings.stream().limit(5).toList());
                    return a;
                }));
            }
            return steps;
        }
        if (any(t, "吃什么", "吃啥", "推荐", "做什么", "做啥", "晚饭", "午饭", "晚上吃")) {
            List<String> who = mentioned(c);
            if (!who.isEmpty() || any(t, "少盐", "快手", "分钟")) return List.of(new Step(SearchRecipesTool.NAME, this::searchArgs));
            return List.of(new Step(RecommendTonightTool.NAME, x -> Map.of("n", 3)));
        }
        if (foodRelated(t)) return List.of(new Step(SearchRecipesTool.NAME, this::searchArgs));
        return List.of();
    }

    private Map<String, Object> cookedArgs(Ctx c) {
        Map<String, Object> a = new LinkedHashMap<>();
        String dish = before(c.text(), "做完", "做好了", "做好啦", "吃完了", "已经做了")
                .replaceFirst("^(今天|今晚|刚刚|刚才|刚|我|我们|把|已经)+", "").replaceAll("[，,。！!]+$", "");
        if (dish.isEmpty() || dish.length() > 12) {
            Matcher m = Pattern.compile("mark_cooked dish=([^ ）)]+)").matcher(c.prevAssistant());
            if (m.find()) dish = m.group(1);
        }
        a.put("dish", dish);
        Integer n = servings(c.text());
        a.put("servings", n == null ? c.members().size() : n);
        List<String> who = mentioned(c);
        if (!who.isEmpty()) a.put("eaters", who);
        return a;
    }

    private Map<String, Object> menuArgs(Ctx c) {
        Map<String, Object> a = new LinkedHashMap<>();
        Object ref = menuRef(c.text(), c.today());
        if (ref == null) {
            Matcher named = Pattern.compile("(加到|加进|加入|放进)(.+?)(的食集|食集|里|$)").matcher(c.text());
            if (named.find() && !named.group(2).isBlank()) ref = named.group(2).trim();
        }
        if (ref instanceof String s) a.put("menu", s);
        else if (ref instanceof Map<?, ?> m) a.put("newMenu", m);
        List<String> dishes = new ArrayList<>();
        if (any(c.text(), "这三道", "这几道", "这些", "就这", "这两道", "都加")) {
            Matcher m = Pattern.compile("展示过的菜：([^）)]+)").matcher(c.prevAssistant());
            if (m.find()) for (String d : m.group(1).split("、")) dishes.add(d.trim());
        }
        if (dishes.isEmpty()) {
            String d = between(c.text(), "把", "加进|加到|加入|放进");
            if (d.isEmpty()) d = after(c.text(), "加个", "再加", "加一道", "添个", "添一道");
            d = d.replaceAll("(到|进)?(今晚的饭|今晚|周六|周日|明天|食集|单子|里)+.*$", "").replaceAll("[，,。]", "").trim();
            if (!d.isEmpty()) for (String x : d.split("[和、跟]")) if (!x.isBlank()) dishes.add(x.trim());
        }
        if (dishes.isEmpty()) return null;
        a.put("dishes", dishes);
        return a;
    }

    private Map<String, Object> searchArgs(Ctx c) {
        Map<String, Object> a = new LinkedHashMap<>();
        Map<String, Object> filters = new LinkedHashMap<>();
        List<String> who = mentioned(c);
        if (!who.isEmpty()) filters.put("forMembers", who);
        List<String> tags = new ArrayList<>();
        for (String tag : List.of("少盐", "快手菜", "素菜", "汤", "控脂", "宝宝可吃")) {
            if (c.text().contains(tag.replace("菜", ""))) tags.add(tag);
        }
        if (!tags.isEmpty()) filters.put("tags", tags);
        if (any(c.text(), "不辣", "不要辣", "不吃辣")) filters.put("excludeTags", List.of("辣"));
        Matcher m = MINUTES.matcher(c.text());
        if (m.find()) filters.put("maxMinutes", Integer.parseInt(m.group(1)));
        a.put("query", c.text().length() > 40 ? c.text().substring(0, 40) : c.text());
        if (!filters.isEmpty()) a.put("filters", filters);
        return a;
    }

    private String finalText(Ctx c, List<Step> plan) {
        String t = c.text();
        if (plan.isEmpty()) {
            if (injection(t)) return "这个我不能照做。我只帮你做饭相关的事，也只能操作你自己家的数据。";
            if (any(t, "诊断", "吃药", "治疗", "病了")) {
                return "身体的问题建议咨询医生；饮食上我可以按家里的约束帮你挑清淡少盐的菜。";
            }
            return "我是咕嘟，专门帮你做饭的，这个我帮不上忙～要不要看看今晚吃什么？";
        }
        List<String> parts = new ArrayList<>();
        boolean pending = false;
        boolean failed = false;
        for (JsonNode r : c.results()) {
            String s = r.path("summary").asText("");
            if (!r.path("ok").asBoolean(true)) failed = true;
            if (r.path("data").path("pending").asBoolean(false)) pending = true;
            if (!s.isBlank()) parts.add(s);
        }
        if (parts.isEmpty()) return "我还需要更多信息，能具体说说吗？";
        StringBuilder sb = new StringBuilder(failed ? "" : "好的，");
        sb.append(String.join("；", parts)).append("。");
        if (pending) sb.append("点确认后我再执行，5 分钟内有效。");
        return sb.toString();
    }

    // =========================================================== 文本小工具

    private static List<String> mentioned(Ctx c) {
        List<String> out = new ArrayList<>();
        for (String n : c.members()) if (c.text().contains(n)) out.add(n);
        return out;
    }

    static Integer servings(String t) {
        Matcher m = NUM.matcher(t);
        if (!m.find()) return null;
        return cn(m.group(1));
    }

    static Integer cn(String s) {
        if (s.matches("\\d+")) return Integer.parseInt(s);
        String digits = "零一二三四五六七八九";
        if (s.equals("两")) return 2;
        if (s.equals("十")) return 10;
        if (s.startsWith("十")) return 10 + digits.indexOf(s.charAt(1));
        int i = digits.indexOf(s.charAt(0));
        return i < 0 ? null : i;
    }

    private static List<Map<String, Object>> items(String segment) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (segment == null) return out;
        for (String raw : segment.split("[，,、和跟还有。；;]")) {
            String p = raw.replaceAll("^(了|点|些|一下|帮我|加|把|采购单|采购|清单|里|：|:)+", "").trim();
            if (p.isEmpty() || p.length() > 12) continue;
            Matcher m = Pattern.compile("^([一二两三四五六七八九十半\\d.]+)\\s*(斤|克|g|个|把|盒|瓶|袋|根|颗|条|块|打|只|升)?(.+)$").matcher(p);
            Map<String, Object> it = new LinkedHashMap<>();
            if (m.find() && m.group(3) != null && !m.group(3).isBlank()) {
                it.put("name", m.group(3).trim());
                it.put("amount", m.group(1));
                if (m.group(2) != null) it.put("unit", m.group(2));
            } else {
                it.put("name", p);
            }
            out.add(it);
        }
        return out;
    }

    static String before(String t, String... marks) {
        for (String k : marks) {
            int i = t.indexOf(k);
            if (i > 0) return t.substring(0, i).replaceAll("[，,。？?！!]", "").trim();
        }
        return "";
    }

    static String after(String t, String... marks) {
        for (String k : marks) {
            int i = t.indexOf(k);
            if (i >= 0) return t.substring(i + k.length()).trim();
        }
        return "";
    }

    static String between(String t, String start, String endRegex) {
        int s = t.indexOf(start);
        if (s < 0) return "";
        Matcher m = Pattern.compile(endRegex).matcher(t);
        if (!m.find(s + start.length())) return "";
        return t.substring(s + start.length(), m.start()).trim();
    }

    private static Object menuRef(String t, LocalDate today) {
        if (t.contains("今晚") || t.contains("今天")) return "今晚的饭";
        if (t.contains("周六") || t.contains("爸妈")) return "周六";
        if (t.contains("辅食")) return "朵朵一周辅食";
        if (t.contains("下周一")) {
            return Map.of("name", "下周一的饭", "date", today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString());
        }
        if (t.contains("明天")) return Map.of("name", "明天的饭", "date", today.plusDays(1).toString());
        return null;
    }

    private static boolean foodRelated(String t) {
        return any(t, "菜", "肉", "蛋", "鱼", "虾", "汤", "饭", "面", "豆腐", "菠菜", "番茄", "西红柿", "鸡", "排骨", "土豆",
                "吃", "做", "炒", "煮", "蒸", "炖", "辣", "甜", "咸", "少盐", "宝宝");
    }

    private static String previousAssistant(List<ChatMessage> msgs, int lastUser) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.max(0, lastUser); i++) {
            ChatMessage m = msgs.get(i);
            if ("assistant".equals(m.role()) && m.content() != null) sb.append(m.content()).append('\n');
        }
        return sb.toString();
    }

    private static List<String> members(List<ChatMessage> msgs) {
        for (ChatMessage m : msgs) {
            if ("system".equals(m.role()) && m.content() != null) {
                Matcher x = MEMBERS_LINE.matcher(m.content());
                if (x.find()) return List.of(x.group(1).trim().split("\\s*,\\s*"));
            }
        }
        return List.of();
    }

    private static LocalDate today(List<ChatMessage> msgs) {
        for (ChatMessage m : msgs) {
            if ("system".equals(m.role()) && m.content() != null) {
                Matcher x = TODAY_LINE.matcher(m.content());
                if (x.find()) return LocalDate.parse(x.group(1));
            }
        }
        return LocalDate.now();
    }

    private JsonNode parse(String s) {
        try {
            JsonNode n = om.readTree(s == null ? "{}" : s);
            return n.has("result") ? n.get("result") : n;
        } catch (Exception e) {
            return om.createObjectNode();
        }
    }

    private String json(Map<String, Object> m) {
        try {
            return om.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
