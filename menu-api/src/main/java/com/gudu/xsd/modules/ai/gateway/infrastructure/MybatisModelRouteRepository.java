package com.gudu.xsd.modules.ai.gateway.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelRoute;
import com.gudu.xsd.modules.ai.gateway.domain.model.RouteTarget;
import com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode;
import com.gudu.xsd.modules.ai.gateway.domain.repository.ModelRouteRepository;
import com.gudu.xsd.modules.ai.gateway.infrastructure.mapper.AiRouteMapper;
import com.gudu.xsd.modules.ai.gateway.infrastructure.po.AiRoutePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 能力路由仓储（ai_route，fallbacks 存 JSON）。 */
@Repository
@RequiredArgsConstructor
public class MybatisModelRouteRepository implements ModelRouteRepository {

    private final AiRouteMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<ModelRoute> findAll() {
        return mapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ModelRoute> find(Capability capability) {
        AiRoutePO po = mapper.selectById(capability.name());
        return po == null ? Optional.empty() : Optional.of(toDomain(po));
    }

    @Override
    public void save(ModelRoute r) {
        AiRoutePO po = new AiRoutePO();
        po.setCapability(r.capability().name());
        po.setPrimaryProviderId(r.primary().providerId());
        po.setPrimaryModel(r.primary().model());
        po.setFallbacks(writeTargets(r.fallbacks()));
        po.setTimeoutMs(r.timeoutMs());
        po.setMaxRetries(r.maxRetries());
        po.setThinking(r.thinking().name());
        po.setUpdatedAt(r.updatedAt());
        po.setUpdatedBy(r.updatedBy());
        if (mapper.selectById(po.getCapability()) == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    private ModelRoute toDomain(AiRoutePO po) {
        return ModelRoute.restore(Capability.of(po.getCapability()),
                new RouteTarget(po.getPrimaryProviderId(), po.getPrimaryModel()), readTargets(po.getFallbacks()),
                po.getTimeoutMs() == null ? 30_000 : po.getTimeoutMs(),
                po.getMaxRetries() == null ? 1 : po.getMaxRetries(), ThinkingMode.of(po.getThinking()),
                po.getUpdatedAt(), po.getUpdatedBy());
    }

    private List<RouteTarget> readTargets(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() { });
            return raw.stream().map(m -> new RouteTarget(((Number) m.get("providerId")).longValue(),
                    String.valueOf(m.get("model")))).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeTargets(List<RouteTarget> targets) {
        try {
            return objectMapper.writeValueAsString(targets.stream()
                    .map(t -> Map.of("providerId", t.providerId(), "model", t.model())).toList());
        } catch (Exception e) {
            return "[]";
        }
    }
}
