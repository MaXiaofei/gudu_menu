package com.gudu.xsd.modules.ai.gateway;

import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.GatewayCallRecord;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelProvider;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelRoute;
import com.gudu.xsd.modules.ai.gateway.domain.port.ModelTransport;
import com.gudu.xsd.modules.ai.gateway.domain.port.SecretCipher;
import com.gudu.xsd.modules.ai.gateway.domain.repository.GatewayAuditRepository;
import com.gudu.xsd.modules.ai.gateway.domain.repository.GatewayCallLogRepository;
import com.gudu.xsd.modules.ai.gateway.domain.repository.ModelProviderRepository;
import com.gudu.xsd.modules.ai.gateway.domain.repository.ModelRouteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** 网关单测用内存实现。 */
final class GatewayFakes {

    private GatewayFakes() {
    }

    static class Providers implements ModelProviderRepository {
        final Map<Long, ModelProvider> data = new LinkedHashMap<>();
        final AtomicLong seq = new AtomicLong(100);
        int reads;

        Providers(List<ModelProvider> init) {
            init.forEach(p -> data.put(p.id(), p));
        }

        @Override
        public List<ModelProvider> findAll() {
            reads++;
            return new ArrayList<>(data.values());
        }

        @Override
        public Optional<ModelProvider> findById(Long id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public Optional<ModelProvider> findByName(String name) {
            return data.values().stream().filter(p -> p.name().equals(name)).findFirst();
        }

        @Override
        public void save(ModelProvider p) {
            if (p.id() == null) p.assignId(seq.incrementAndGet());
            data.put(p.id(), p);
        }

        @Override
        public void delete(Long id) {
            data.remove(id);
        }
    }

    static final class Routes implements ModelRouteRepository {
        final Map<Capability, ModelRoute> data = new LinkedHashMap<>();

        Routes(List<ModelRoute> init) {
            init.forEach(r -> data.put(r.capability(), r));
        }

        @Override
        public List<ModelRoute> findAll() {
            return new ArrayList<>(data.values());
        }

        @Override
        public Optional<ModelRoute> find(Capability capability) {
            return Optional.ofNullable(data.get(capability));
        }

        @Override
        public void save(ModelRoute route) {
            data.put(route.capability(), route);
        }
    }

    static final class Calls implements GatewayCallLogRepository {
        final List<GatewayCallRecord> rows = new ArrayList<>();

        @Override
        public void append(GatewayCallRecord record) {
            rows.add(record);
        }

        @Override
        public List<GatewayCallRecord> since(LocalDateTime from) {
            return rows;
        }
    }

    static final class Audit implements GatewayAuditRepository {
        final List<String> rows = new ArrayList<>();

        @Override
        public void append(String actor, String action, String target, String detailJson) {
            rows.add(action + " " + target + " " + detailJson);
        }
    }

    /** 记录调用的传输层，按供应商名返回预设行为。 */
    static final class Transport implements ModelTransport {
        final List<String> calls = new ArrayList<>();
        final List<com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode> thinking = new ArrayList<>();
        final Map<String, java.util.function.Function<String, com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply>> behavior =
                new LinkedHashMap<>();

        @Override
        public com.gudu.xsd.modules.ai.gateway.domain.model.ModelReply call(Endpoint endpoint, String apiKey,
                com.gudu.xsd.modules.ai.gateway.domain.model.ChatRequest request,
                com.gudu.xsd.modules.ai.gateway.domain.model.ThinkingMode t, int timeoutMs) {
            calls.add(endpoint.providerName() + "/" + endpoint.model());
            thinking.add(t);
            var b = behavior.get(endpoint.providerName());
            if (b == null) throw new IllegalStateException("未预设 " + endpoint.providerName());
            return b.apply(apiKey);
        }
    }

    static final SecretCipher NO_CIPHER = new SecretCipher() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String encrypt(String plain) {
            throw new IllegalStateException();
        }

        @Override
        public String decrypt(String cipherText) {
            throw new IllegalStateException();
        }
    };
}
