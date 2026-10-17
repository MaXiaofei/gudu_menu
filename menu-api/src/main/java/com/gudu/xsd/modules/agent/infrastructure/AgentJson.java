package com.gudu.xsd.modules.agent.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 持久化用 JSON 编解码（解析失败返回空值，不让一条脏数据拖垮列表）。 */
@Component
@RequiredArgsConstructor
public class AgentJson {

    private final ObjectMapper objectMapper;

    public String write(Object o) {
        if (o == null) return null;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    public <T> List<T> list(String json, Class<T> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> maps(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> map(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
