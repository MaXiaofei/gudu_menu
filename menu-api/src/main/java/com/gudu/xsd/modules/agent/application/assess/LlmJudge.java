package com.gudu.xsd.modules.agent.application.assess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatMessage;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest;
import com.gudu.xsd.modules.ai.gateway.domain.model.ChatResult;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 裁判：经模型网关 CHAT 路由（思考模式 LOW），逐条判 rubric.must / mustNot 是否命中，输出 JSON 理由。
 * 提示词带版本号 {@link #VERSION}；换裁判模型前需在人工标注样本上校准一致率（≥ 90%，见 eval.md §5）。
 */
public class LlmJudge {

    public static final String VERSION = "judge-v1";

    private static final String SYSTEM = """
            你是对话助手「咕嘟」的评测裁判。给你：用户的对话、咕嘟本轮的回复、咕嘟调用过的工具与生成的待确认卡片。
            逐条判断评分要点是否被满足：
            - must：回复（结合工具与卡片）是否做到了这一条；
            - mustNot：回复是否出现了这一条描述的错误。
            只依据给出的内容判断，不要脑补。只输出 JSON：
            {"must":[{"i":序号,"hit":true|false,"reason":"简短理由"}],"mustNot":[{"i":序号,"hit":true|false,"reason":"简短理由"}]}
            """;

    private final ModelGateway gateway;
    private final ObjectMapper om;

    public LlmJudge(ModelGateway gateway, ObjectMapper om) {
        this.gateway = gateway;
        this.om = om;
    }

    public record Verdict(boolean judged, int mustHit, int mustTotal, boolean mustNotHit, List<String> reasons) {

        static Verdict skipped(int mustTotal) {
            return new Verdict(false, 0, mustTotal, false, List.of());
        }
    }

    public Verdict judge(String dialogue, String reply, String toolsAndCards, List<String> must, List<String> mustNot) {
        if (must.isEmpty() && mustNot.isEmpty()) return new Verdict(true, 0, 0, false, List.of());
        StringBuilder user = new StringBuilder();
        user.append("【对话】\n").append(dialogue).append("\n\n【咕嘟回复】\n").append(reply)
                .append("\n\n【工具与卡片】\n").append(toolsAndCards).append("\n\n【must】\n");
        for (int i = 0; i < must.size(); i++) user.append(i + 1).append(". ").append(must.get(i)).append('\n');
        user.append("【mustNot】\n");
        for (int i = 0; i < mustNot.size(); i++) user.append(i + 1).append(". ").append(mustNot.get(i)).append('\n');
        try {
            ChatResult r = gateway.chat(new ChatRequest(Capability.CHAT, "agent_judge",
                    List.of(ChatMessage.system(SYSTEM), ChatMessage.user(user.toString())), List.of(), true, null, null,
                    null, false, ThinkingMode.LOW));
            JsonNode n = om.readTree(strip(r.content()));
            int hit = 0;
            List<String> reasons = new ArrayList<>();
            for (JsonNode x : n.path("must")) {
                if (x.path("hit").asBoolean(false)) hit++;
                else reasons.add("未满足 must " + x.path("i").asText() + "：" + x.path("reason").asText());
            }
            boolean bad = false;
            for (JsonNode x : n.path("mustNot")) {
                if (x.path("hit").asBoolean(false)) {
                    bad = true;
                    reasons.add("命中 mustNot " + x.path("i").asText() + "：" + x.path("reason").asText());
                }
            }
            return new Verdict(true, Math.min(hit, must.size()), must.size(), bad, reasons);
        } catch (Exception e) {
            return Verdict.skipped(must.size());
        }
    }

    private static String strip(String s) {
        if (s == null) return "{}";
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            t = nl > 0 ? t.substring(nl + 1) : t;
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }
}
