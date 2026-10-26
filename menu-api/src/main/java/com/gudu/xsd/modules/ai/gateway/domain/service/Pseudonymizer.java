package com.gudu.xsd.modules.ai.gateway.domain.service;

import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatResult;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply;
import com.gudu.xsd.modules.ai.gateway.domain.model.ToolCall;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 发给模型的个人信息最小化（纯函数，按请求作用域）：隐私政策第三节「成员称呼替换为成员A、成员B等代号；
 * 昵称、手机号、openid 不发送给模型服务商」的唯一实现。
 *
 * <ul>
 *   <li>{@link #mask}：出站。成员称呼 → 「成员A / 成员B …」（按传入顺序稳定分配，同一请求内一致）；
 *       手机号、{@code wx_} 占位手机号、openid 形态的串 → 固定占位符（单向，不还原）。</li>
 *   <li>{@link #restore}：入站。模型输出里的代号 → 真实称呼（回复文本、思考内容、工具调用参数）。</li>
 *   <li>{@link #streamRestorer()}：流式入站。代号可能被拆在两个 chunk 里（「成员」|「A」），尾部可能是代号前缀的部分先扣住，
 *       凑齐或确认不是代号后再放出。</li>
 * </ul>
 *
 * <p>替换是一次扫描（正则交替、长词优先），不会把已替换出的代号再次替换。特殊饮食人群标签等受保护词
 * （如成员恰好叫「宝宝」时的「宝宝辅食」）原样保留，标签按隐私政策允许发送。
 */
public final class Pseudonymizer {

    /** 代号前缀。 */
    public static final String ALIAS_PREFIX = "成员";
    /** 手机号占位。 */
    public static final String PHONE_PLACEHOLDER = "[手机号已隐藏]";
    /** openid / wx_ 占位手机号等账号标识占位。 */
    public static final String ID_PLACEHOLDER = "[账号标识已隐藏]";

    /** 受保护词：含常见称呼但本身是允许发送的标签 / 约束词，整体保留不拆。 */
    static final List<String> PROTECTED = List.of("宝宝辅食", "宝宝可吃", "宝宝辅食版", "宝宝餐", "辅食版");

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern WX_PLACEHOLDER = Pattern.compile("wx_[A-Za-z0-9_-]+");
    /** 微信 openid：o 开头 28 位（base64url 字符）。 */
    private static final Pattern OPENID = Pattern.compile("(?<![A-Za-z0-9_-])o[A-Za-z0-9_-]{27}(?![A-Za-z0-9_-])");

    // 注意：必须声明在 PROTECTED 与各 Pattern 之后（静态初始化顺序）
    /** 只做账号标识脱敏、不做称呼替换（无成员上下文的调用点 / 网关兜底）。 */
    public static final Pseudonymizer NONE = new Pseudonymizer(List.of());

    private final Map<String, String> aliasByName;
    private final Map<String, String> nameByAlias;
    /** 出站查找表：称呼本身 + JSON 转义形态（工具结果是 JSON，称呼含引号时以转义形态出现）。 */
    private final Map<String, String> maskLookup;
    private final Pattern maskPattern;
    private final Pattern restorePattern;

    private Pseudonymizer(List<String> names) {
        this.aliasByName = new LinkedHashMap<>();
        this.nameByAlias = new LinkedHashMap<>();
        for (String raw : names) {
            if (raw == null) continue;
            String n = raw.trim();
            if (n.isEmpty() || aliasByName.containsKey(n)) continue;
            String alias = aliasOf(aliasByName.size());
            aliasByName.put(n, alias);
            nameByAlias.put(alias, n);
        }
        this.maskLookup = new LinkedHashMap<>(aliasByName);
        aliasByName.forEach((n, a) -> maskLookup.putIfAbsent(jsonEscape(n), a));
        this.maskPattern = alternation(concat(maskLookup.keySet(), PROTECTED));
        this.restorePattern = alternation(nameByAlias.keySet());
    }

    /** 按成员顺序（同一家庭每次相同）分配代号。 */
    public static Pseudonymizer of(Collection<String> memberNames) {
        return memberNames == null || memberNames.isEmpty() ? NONE : new Pseudonymizer(List.copyOf(
                memberNames.stream().filter(java.util.Objects::nonNull).toList()));
    }

    /** 第 i 位成员的代号：成员A … 成员Z，第 27 位起 成员27、成员28 …（互不为前缀）。 */
    static String aliasOf(int i) {
        return i < 26 ? ALIAS_PREFIX + (char) ('A' + i) : ALIAS_PREFIX + (i + 1);
    }

    /** 真实称呼 → 代号（只读视图）。 */
    public Map<String, String> aliases() {
        return Collections.unmodifiableMap(aliasByName);
    }

    public String aliasOfName(String name) {
        return name == null ? null : aliasByName.get(name.trim());
    }

    // ================================================================== 出站

    /** 文本出站：称呼 → 代号，手机号 / openid / wx_ 占位 → 占位符。 */
    public String mask(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = text;
        if (maskPattern != null) {
            Matcher m = maskPattern.matcher(out);
            StringBuilder sb = new StringBuilder(out.length());
            while (m.find()) {
                String hit = m.group();
                String alias = maskLookup.get(hit);
                m.appendReplacement(sb, Matcher.quoteReplacement(alias != null ? alias : hit));
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        return redactIdentifiers(out);
    }

    /** 账号标识脱敏（单向）：openid、wx_ 占位手机号、11 位手机号。 */
    public static String redactIdentifiers(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = WX_PLACEHOLDER.matcher(text).replaceAll(Matcher.quoteReplacement(ID_PLACEHOLDER));
        out = OPENID.matcher(out).replaceAll(Matcher.quoteReplacement(ID_PLACEHOLDER));
        return PHONE.matcher(out).replaceAll(Matcher.quoteReplacement(PHONE_PLACEHOLDER));
    }

    /** 请求出站：所有消息的文本、思考内容、工具调用参数（图片不动）。 */
    public ChatRequest mask(ChatRequest request) {
        List<ChatMessage> out = new ArrayList<>(request.messages().size());
        for (ChatMessage m : request.messages()) {
            List<ToolCall> calls = m.toolCalls().stream()
                    .map(c -> new ToolCall(c.id(), c.name(), mask(c.argumentsJson()))).toList();
            out.add(new ChatMessage(m.role(), mask(m.content()), m.imageUrl(), calls, m.toolCallId(),
                    mask(m.reasoningContent())));
        }
        return request.withMessages(out);
    }

    // ================================================================== 入站

    /** 文本入站：代号 → 真实称呼。 */
    public String restore(String text) {
        if (text == null || text.isEmpty() || restorePattern == null) return text;
        Matcher m = restorePattern.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(nameByAlias.get(m.group())));
        m.appendTail(sb);
        return sb.toString();
    }

    /** JSON 文本入站：同 {@link #restore(String)}，称呼按 JSON 字符串转义（称呼里含引号也不破坏 JSON）。 */
    public String restoreJson(String json) {
        if (json == null || json.isEmpty() || restorePattern == null) return json;
        Matcher m = restorePattern.matcher(json);
        StringBuilder sb = new StringBuilder(json.length());
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(jsonEscape(nameByAlias.get(m.group()))));
        m.appendTail(sb);
        return sb.toString();
    }

    /** 结果入站：回复文本、思考内容、工具调用参数。 */
    public ChatResult restore(ChatResult result) {
        if (restorePattern == null || result == null) return result;
        ModelReply r = result.reply();
        List<ToolCall> calls = r.toolCalls().stream()
                .map(c -> new ToolCall(c.id(), c.name(), restoreJson(c.argumentsJson()))).toList();
        ModelReply restored = new ModelReply(restore(r.content()), restore(r.reasoningContent()), calls, r.usage());
        return new ChatResult(restored, result.provider(), result.model(), result.degraded(), result.latencyMs(),
                result.costFen(), result.attempts());
    }

    /** 新建一个流式还原器（每条 SSE 流一个）。 */
    public StreamRestorer streamRestorer() {
        return new StreamRestorer(this);
    }

    /**
     * 流式还原：{@link #push} 返回可以安全下发的已还原文本，尾部可能是代号前缀的部分暂扣；流结束时 {@link #flush}。
     * 非线程安全，一条流一个实例。
     */
    public static final class StreamRestorer {
        private final Pseudonymizer p;
        private final List<String> aliases;
        private final StringBuilder pending = new StringBuilder();

        StreamRestorer(Pseudonymizer p) {
            this.p = p;
            this.aliases = List.copyOf(p.nameByAlias.keySet());
        }

        public String push(String chunk) {
            if (chunk == null || chunk.isEmpty()) return "";
            if (aliases.isEmpty()) return chunk;
            pending.append(chunk);
            int hold = holdBack(pending);
            String ready = pending.substring(0, pending.length() - hold);
            pending.delete(0, pending.length() - hold);
            return p.restore(ready);
        }

        public String flush() {
            String rest = p.restore(pending.toString());
            pending.setLength(0);
            return rest;
        }

        /** 尾部需要扣住的长度：最长的、是某个代号真前缀（或本身是代号且还可能是更长代号前缀）的后缀。 */
        private int holdBack(CharSequence buf) {
            int max = 0;
            for (String a : aliases) max = Math.max(max, a.length());
            int limit = Math.min(buf.length(), max);
            for (int len = limit; len > 0; len--) {
                String tail = buf.subSequence(buf.length() - len, buf.length()).toString();
                for (String a : aliases) {
                    if (a.startsWith(tail) && (a.length() > len || hasLongerAlias(a))) return len;
                }
            }
            return 0;
        }

        private boolean hasLongerAlias(String a) {
            for (String b : aliases) if (b.length() > a.length() && b.startsWith(a)) return true;
            return false;
        }
    }

    // ================================================================== 内部

    private static Pattern alternation(Collection<String> words) {
        if (words.isEmpty()) return null;
        List<String> sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder sb = new StringBuilder();
        for (String w : sorted) {
            if (!sb.isEmpty()) sb.append('|');
            sb.append(Pattern.quote(w));
        }
        return Pattern.compile(sb.toString());
    }

    private static List<String> concat(Collection<String> a, Collection<String> b) {
        List<String> out = new ArrayList<>(a);
        for (String x : b) if (!out.contains(x)) out.add(x);
        return out;
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
