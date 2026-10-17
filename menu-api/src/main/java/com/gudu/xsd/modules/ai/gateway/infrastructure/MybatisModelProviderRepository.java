package com.gudu.xsd.modules.ai.gateway.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.KeySource;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelPrice;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelProvider;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelSpec;
import com.gudu.xsd.modules.ai.gateway.domain.model.PeakHours;
import com.gudu.xsd.modules.ai.gateway.domain.model.ProviderHealth;
import com.gudu.xsd.modules.ai.gateway.domain.model.ProviderType;
import com.gudu.xsd.modules.ai.gateway.domain.repository.ModelProviderRepository;
import com.gudu.xsd.modules.ai.gateway.infrastructure.mapper.AiProviderMapper;
import com.gudu.xsd.modules.ai.gateway.infrastructure.mapper.AiProviderModelMapper;
import com.gudu.xsd.modules.ai.gateway.infrastructure.po.AiProviderModelPO;
import com.gudu.xsd.modules.ai.gateway.infrastructure.po.AiProviderPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** 供应商仓储：ai_provider + ai_provider_model（保存时整体替换下属模型）。 */
@Repository
@RequiredArgsConstructor
public class MybatisModelProviderRepository implements ModelProviderRepository {

    private final AiProviderMapper providerMapper;
    private final AiProviderModelMapper modelMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<ModelProvider> findAll() {
        List<AiProviderPO> pos = providerMapper.selectList(new QueryWrapper<AiProviderPO>().orderByAsc("id"));
        if (pos.isEmpty()) return List.of();
        Map<Long, List<AiProviderModelPO>> models = modelMapper.selectList(new QueryWrapper<AiProviderModelPO>()
                        .in("provider_id", pos.stream().map(AiProviderPO::getId).toList()))
                .stream().collect(Collectors.groupingBy(AiProviderModelPO::getProviderId));
        return pos.stream().map(p -> toDomain(p, models.getOrDefault(p.getId(), List.of()))).toList();
    }

    @Override
    public Optional<ModelProvider> findById(Long id) {
        if (id == null) return Optional.empty();
        AiProviderPO po = providerMapper.selectById(id);
        return po == null ? Optional.empty() : Optional.of(toDomain(po, modelsOf(id)));
    }

    @Override
    public Optional<ModelProvider> findByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        AiProviderPO po = providerMapper.selectOne(new QueryWrapper<AiProviderPO>().eq("name", name.trim())
                .last("LIMIT 1"));
        return po == null ? Optional.empty() : Optional.of(toDomain(po, modelsOf(po.getId())));
    }

    @Override
    public void save(ModelProvider p) {
        AiProviderPO po = new AiProviderPO();
        po.setId(p.id());
        po.setName(p.name());
        po.setType(p.type().name());
        po.setBaseUrl(p.baseUrl());
        po.setApiKeyEnc(p.encryptedKey());
        po.setKeySource(p.keySource().name());
        po.setKeyEnv(p.keyEnv());
        po.setEnabled(p.enabled() ? 1 : 0);
        po.setHealthOk(p.health().checkedAt() == null ? null : (p.health().ok() ? 1 : 0));
        po.setHealthLatencyMs(p.health().latencyMs());
        po.setHealthCheckedAt(p.health().checkedAt());
        if (p.id() == null) {
            providerMapper.insert(po);
            p.assignId(po.getId());
        } else {
            providerMapper.updateById(po);
        }
        modelMapper.delete(new QueryWrapper<AiProviderModelPO>().eq("provider_id", p.id()));
        int sort = 1;
        for (ModelSpec m : p.models()) {
            AiProviderModelPO mp = new AiProviderModelPO();
            mp.setProviderId(p.id());
            mp.setModel(m.model());
            mp.setCapabilities(m.capabilities().stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
            mp.setPriceCacheHitFen(m.price().inputCacheHitFen());
            mp.setPriceInputFen(m.price().inputFen());
            mp.setPriceOutputFen(m.price().outputFen());
            mp.setPeakHours(writePeak(m.peakHours()));
            mp.setSupportsThinking(m.supportsThinking() ? 1 : 0);
            mp.setSortNo(sort++);
            modelMapper.insert(mp);
        }
    }

    @Override
    public void delete(Long id) {
        modelMapper.delete(new QueryWrapper<AiProviderModelPO>().eq("provider_id", id));
        providerMapper.deleteById(id);
    }

    private List<AiProviderModelPO> modelsOf(Long providerId) {
        return modelMapper.selectList(new QueryWrapper<AiProviderModelPO>().eq("provider_id", providerId));
    }

    private ModelProvider toDomain(AiProviderPO p, List<AiProviderModelPO> models) {
        List<ModelSpec> specs = models.stream()
                .sorted(Comparator.comparing(m -> m.getSortNo() == null ? 0 : m.getSortNo()))
                .map(this::toSpec).toList();
        ProviderHealth health = p.getHealthCheckedAt() == null ? ProviderHealth.UNKNOWN
                : new ProviderHealth(Integer.valueOf(1).equals(p.getHealthOk()), p.getHealthLatencyMs(),
                p.getHealthCheckedAt());
        return ModelProvider.restore(p.getId(), p.getName(), ProviderType.valueOf(p.getType()), p.getBaseUrl(),
                p.getApiKeyEnc(), KeySource.valueOf(p.getKeySource()), p.getKeyEnv(),
                Integer.valueOf(1).equals(p.getEnabled()), specs, health);
    }

    private ModelSpec toSpec(AiProviderModelPO m) {
        EnumSet<Capability> caps = EnumSet.noneOf(Capability.class);
        if (m.getCapabilities() != null) {
            Arrays.stream(m.getCapabilities().split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .forEach(s -> caps.add(Capability.of(s)));
        }
        return new ModelSpec(m.getModel(), caps,
                new ModelPrice(m.getPriceCacheHitFen(), m.getPriceInputFen(), m.getPriceOutputFen()),
                readPeak(m.getPeakHours()), Integer.valueOf(1).equals(m.getSupportsThinking()));
    }

    private PeakHours readPeak(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, PeakHoursJson.class).toDomain();
        } catch (Exception e) {
            return null;
        }
    }

    private String writePeak(PeakHours p) {
        if (p == null) return null;
        try {
            return objectMapper.writeValueAsString(new PeakHoursJson(p.timezone(), p.weekdaysOnly(), p.ranges(),
                    p.offPeakRatio()));
        } catch (Exception e) {
            return null;
        }
    }

    /** peak_hours 列 JSON 形状。 */
    record PeakHoursJson(String timezone, boolean weekdaysOnly, List<String> ranges, java.math.BigDecimal offPeakRatio) {
        PeakHours toDomain() {
            return new PeakHours(timezone, weekdaysOnly, ranges, offPeakRatio);
        }
    }
}
