package com.gudu.xsd.modules.agent.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具序列匹配（纯函数）：模式 exact / subsequence / set / none + forbid。
 *
 * <ul>
 *   <li>选择正确：按名称在该模式下匹配成功，且未调用 forbid 中的工具；</li>
 *   <li>参数正确：名称匹配上的期望调用里，参数全部满足的个数（优先选参数也满足的实际调用）。</li>
 * </ul>
 */
public final class ToolSequenceMatcher {

    private ToolSequenceMatcher() {
    }

    public record Call(String name, Map<String, Object> args) {
    }

    /** 期望调用；anyOf 非空时命中任一备选即可（name/args 忽略）。 */
    public record Expected(String name, Map<String, Object> args, List<Expected> anyOf) {

        public Expected(String name, Map<String, Object> args) {
            this(name, args, null);
        }

        String label() {
            return anyOf == null || anyOf.isEmpty() ? name
                    : String.join("|", anyOf.stream().map(Expected::label).toList());
        }
    }

    public record Result(boolean selectionOk, int argsMatched, int argsTotal, List<String> forbiddenCalled,
                         String detail) {

        public boolean argsOk() {
            return argsMatched == argsTotal;
        }

        public boolean pass() {
            return selectionOk && argsOk();
        }
    }

    public static Result match(String mode, List<Expected> expected, List<String> forbid, List<Call> actual) {
        List<Expected> exp = expected == null ? List.of() : expected;
        List<String> forbidden = new ArrayList<>();
        if (forbid != null) {
            for (Call c : actual) if (forbid.contains(c.name()) && !forbidden.contains(c.name())) forbidden.add(c.name());
        }
        String m = mode == null ? "subsequence" : mode;
        Result base = switch (m) {
            case "none" -> new Result(actual.isEmpty(), 0, 0, forbidden,
                    actual.isEmpty() ? "ok" : "不应调用工具，实际调用 " + names(actual));
            case "exact" -> exact(exp, actual);
            case "set" -> set(exp, actual);
            default -> subsequence(exp, actual);
        };
        boolean sel = base.selectionOk() && forbidden.isEmpty();
        String detail = forbidden.isEmpty() ? base.detail() : base.detail() + "；调用了禁止的工具 " + forbidden;
        return new Result(sel, base.argsMatched(), base.argsTotal(), forbidden, detail);
    }

    private static Result exact(List<Expected> exp, List<Call> actual) {
        boolean sameLen = exp.size() == actual.size();
        int argsOk = 0;
        boolean namesOk = sameLen;
        for (int i = 0; i < Math.min(exp.size(), actual.size()); i++) {
            if (!nameMatches(exp.get(i), actual.get(i))) {
                namesOk = false;
                continue;
            }
            if (argsMatch(exp.get(i), actual.get(i))) argsOk++;
        }
        return new Result(namesOk, argsOk, exp.size(), List.of(),
                namesOk ? "ok" : "期望 " + expNames(exp) + "，实际 " + names(actual));
    }

    private static Result subsequence(List<Expected> exp, List<Call> actual) {
        int pos = 0;
        int argsOk = 0;
        for (Expected e : exp) {
            int byName = -1;
            int byArgs = -1;
            for (int j = pos; j < actual.size(); j++) {
                if (!nameMatches(e, actual.get(j))) continue;
                if (byName < 0) byName = j;
                if (argsMatch(e, actual.get(j))) {
                    byArgs = j;
                    break;
                }
            }
            int hit = byArgs >= 0 ? byArgs : byName;
            if (hit < 0) {
                return new Result(false, argsOk, exp.size(), List.of(),
                        "缺少 " + e.label() + "（期望顺序 " + expNames(exp) + "，实际 " + names(actual) + "）");
            }
            if (byArgs >= 0) argsOk++;
            pos = hit + 1;
        }
        return new Result(true, argsOk, exp.size(), List.of(), "ok");
    }

    private static Result set(List<Expected> exp, List<Call> actual) {
        boolean[] used = new boolean[actual.size()];
        int argsOk = 0;
        for (Expected e : exp) {
            int byName = -1;
            int byArgs = -1;
            for (int j = 0; j < actual.size(); j++) {
                if (used[j] || !nameMatches(e, actual.get(j))) continue;
                if (byName < 0) byName = j;
                if (argsMatch(e, actual.get(j))) {
                    byArgs = j;
                    break;
                }
            }
            int hit = byArgs >= 0 ? byArgs : byName;
            if (hit < 0) {
                return new Result(false, argsOk, exp.size(), List.of(), "缺少 " + e.label() + "，实际 " + names(actual));
            }
            used[hit] = true;
            if (byArgs >= 0) argsOk++;
        }
        return new Result(true, argsOk, exp.size(), List.of(), "ok");
    }

    private static boolean nameMatches(Expected e, Call c) {
        if (e.anyOf() != null && !e.anyOf().isEmpty()) return e.anyOf().stream().anyMatch(a -> nameMatches(a, c));
        return c.name().equals(e.name());
    }

    private static boolean argsMatch(Expected e, Call c) {
        if (e.anyOf() != null && !e.anyOf().isEmpty()) {
            return e.anyOf().stream().anyMatch(a -> nameMatches(a, c) && argsMatch(a, c));
        }
        return e.args() == null || ArgMatcher.mismatches(c.args(), e.args()).isEmpty();
    }

    private static List<String> names(List<Call> calls) {
        return calls.stream().map(Call::name).toList();
    }

    private static List<String> expNames(List<Expected> exp) {
        return exp.stream().map(Expected::label).toList();
    }
}
