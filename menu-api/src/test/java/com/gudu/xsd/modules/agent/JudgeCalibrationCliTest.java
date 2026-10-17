package com.gudu.xsd.modules.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gudu.xsd.modules.agent.application.assess.AssessGateways;
import com.gudu.xsd.modules.agent.application.assess.LlmJudge;
import com.gudu.xsd.modules.ai.gateway.infrastructure.OpenAiCompatibleTransport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 裁判校准（默认不随 mvn test 跑）：用当前裁判对 {@code agent-eval/judge-calibration.jsonl} 的人工标注逐条判，
 * 输出要点级与运行级一致率到 {@code target/judge-calibration-report.json}。
 * <pre>
 * DEEPSEEK_API_KEY=... ./mvnw test -Dgroups=eval -Dtest=JudgeCalibrationCliTest [-Djudge.runs=1]
 * </pre>
 */
@Tag("eval")
class JudgeCalibrationCliTest {

    @Test
    @SuppressWarnings("unchecked")
    void run() throws Exception {
        ObjectMapper om = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
        List<Map<String, Object>> samples = new ArrayList<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("agent-eval/judge-calibration.jsonl");
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = br.readLine()) != null; ) {
                if (!line.isBlank()) samples.add(om.readValue(line, Map.class));
            }
        }
        assertThat(samples).isNotEmpty();
        int repeat = Integer.parseInt(System.getProperty("judge.runs", "1"));
        AssessGateways.Factory gateways = AssessGateways.defaults(new OpenAiCompatibleTransport(om), System::getenv);
        LlmJudge judge = new LlmJudge(gateways.create(null), om);

        int items = 0, itemAgree = 0, runs = 0, runAgree = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> s : samples) {
            Map<String, Object> rubric = (Map<String, Object>) s.get("rubric");
            Map<String, Object> label = (Map<String, Object>) s.get("label");
            List<String> must = (List<String>) rubric.get("must");
            List<String> mustNot = (List<String>) rubric.get("mustNot");
            for (int k = 0; k < repeat; k++) {
                LlmJudge.Verdict v = judge.judge(String.valueOf(s.get("dialogue")), String.valueOf(s.get("reply")),
                        String.valueOf(s.get("toolsAndCards")), must, mustNot);
                List<Boolean> wantMust = (List<Boolean>) label.get("must");
                List<Boolean> wantNot = (List<Boolean>) label.get("mustNot");
                int agree = 0;
                for (int i = 0; i < wantMust.size(); i++) {
                    if (v.judged() && i < v.mustFlags().size() && v.mustFlags().get(i).equals(wantMust.get(i))) agree++;
                }
                for (int i = 0; i < wantNot.size(); i++) {
                    if (v.judged() && i < v.mustNotFlags().size() && v.mustNotFlags().get(i).equals(wantNot.get(i))) agree++;
                }
                int total = wantMust.size() + wantNot.size();
                items += total;
                itemAgree += agree;
                runs++;
                if (agree == total) runAgree++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("caseId", s.get("caseId"));
                row.put("runIndex", s.get("runIndex"));
                row.put("label", label);
                row.put("judged", v.judged());
                row.put("mustFlags", v.mustFlags());
                row.put("mustNotFlags", v.mustNotFlags());
                row.put("agree", agree == total);
                row.put("notes", v.reasons());
                rows.add(row);
            }
        }
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("judgeVersion", LlmJudge.VERSION);
        rep.put("samples", samples.size());
        rep.put("repeat", repeat);
        rep.put("itemAgreement", items == 0 ? 0 : (double) itemAgree / items);
        rep.put("runAgreement", runs == 0 ? 0 : (double) runAgree / runs);
        rep.put("rows", rows);
        new File("target").mkdirs();
        om.writeValue(new File("target/judge-calibration-report.json"), rep);
        System.out.printf("judge %s：要点一致 %d/%d，运行一致 %d/%d%n", LlmJudge.VERSION, itemAgree, items, runAgree, runs);
    }
}
