package com.gudu.xsd.modules.agent.application.assess;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测数据集（classpath {@code agent-eval/}）：VERSION、fixtures/*.json、cases/*.jsonl（一行一条）。
 */
public record AssessDataset(String version, Map<String, Map<String, Object>> fixtures, List<Map<String, Object>> cases) {

    public static final String ROOT = "agent-eval";

    public static AssessDataset load(ObjectMapper om) {
        return load(om, ROOT);
    }

    /**
     * @param root classpath 目录名（如 agent-eval），或文件系统绝对路径（以 / 开头，本地调试用）
     */
    public static AssessDataset load(ObjectMapper om, String root) {
        if (root.startsWith("/")) return load(om, "file:" + root, "file:" + root);
        return load(om, "classpath:" + root, "classpath*:" + root);
    }

    private static AssessDataset load(ObjectMapper om, String single, String multi) {
        PathMatchingResourcePatternResolver rr = new PathMatchingResourcePatternResolver();
        try {
            String version = "unknown";
            Resource v = rr.getResource(single + "/VERSION");
            if (v.exists()) {
                try (InputStream in = v.getInputStream()) {
                    version = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                }
            }
            Map<String, Map<String, Object>> fixtures = new LinkedHashMap<>();
            for (Resource f : sorted(rr.getResources(multi + "/fixtures/*.json"))) {
                String name = f.getFilename() == null ? "fixture" : f.getFilename().replace(".json", "");
                try (InputStream in = f.getInputStream()) {
                    fixtures.put(name, om.readValue(in, new TypeReference<LinkedHashMap<String, Object>>() { }));
                }
            }
            List<Map<String, Object>> cases = new ArrayList<>();
            for (Resource f : sorted(rr.getResources(multi + "/cases/*.jsonl"))) {
                try (InputStream in = f.getInputStream()) {
                    for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                        if (line.isBlank()) continue;
                        cases.add(om.readValue(line, new TypeReference<LinkedHashMap<String, Object>>() { }));
                    }
                }
            }
            return new AssessDataset(version, fixtures, cases);
        } catch (IOException e) {
            throw new IllegalStateException("读取评测数据集失败：" + e.getMessage(), e);
        }
    }

    private static List<Resource> sorted(Resource[] rs) {
        List<Resource> l = new ArrayList<>(List.of(rs));
        l.sort(Comparator.comparing(r -> r.getFilename() == null ? "" : r.getFilename()));
        return l;
    }
}
