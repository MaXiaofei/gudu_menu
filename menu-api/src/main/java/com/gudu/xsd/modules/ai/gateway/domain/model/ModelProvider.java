package com.gudu.xsd.modules.ai.gateway.domain.model;

import com.gudu.xsd.common.domain.AggregateRoot;
import com.gudu.xsd.common.domain.DomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 模型供应商（聚合根）：协议、地址、key 来源、下属模型与健康状态。
 *
 * <p>不变量：名称非空；同一供应商下模型名唯一；OPENAI_COMPATIBLE 必须有 baseUrl；
 * key 只以密文（{@link KeySource#DB}）或环境变量名（{@link KeySource#ENV}）形式持有，聚合内从不出现明文。
 */
public class ModelProvider extends AggregateRoot<Long> {

    private Long id;
    private String name;
    private final ProviderType type;
    private String baseUrl;
    private String encryptedKey;
    private KeySource keySource;
    private String keyEnv;
    private boolean enabled;
    private List<ModelSpec> models;
    private ProviderHealth health;

    private ModelProvider(Long id, String name, ProviderType type, String baseUrl, String encryptedKey,
                          KeySource keySource, String keyEnv, boolean enabled, List<ModelSpec> models,
                          ProviderHealth health) {
        this.id = id;
        this.type = type == null ? ProviderType.OPENAI_COMPATIBLE : type;
        this.keySource = keySource == null ? KeySource.NONE : keySource;
        this.keyEnv = keyEnv;
        this.encryptedKey = encryptedKey;
        this.health = health == null ? ProviderHealth.UNKNOWN : health;
        rename(name);
        changeEndpoint(baseUrl);
        replaceModels(models);
        this.enabled = enabled;
    }

    /** 新建（管理后台）：key 由应用层加密后传入；没有 key 时 keySource=NONE。 */
    public static ModelProvider create(String name, ProviderType type, String baseUrl, String encryptedKey,
                                       boolean enabled, List<ModelSpec> models) {
        KeySource source = encryptedKey == null || encryptedKey.isBlank() ? KeySource.NONE : KeySource.DB;
        return new ModelProvider(null, name, type, baseUrl, encryptedKey, source, null, enabled, models, null);
    }

    /** 从持久化重建。 */
    public static ModelProvider restore(Long id, String name, ProviderType type, String baseUrl, String encryptedKey,
                                        KeySource keySource, String keyEnv, boolean enabled, List<ModelSpec> models,
                                        ProviderHealth health) {
        return new ModelProvider(id, name, type, baseUrl, encryptedKey, keySource, keyEnv, enabled, models, health);
    }

    @Override
    public Long id() {
        return id;
    }

    /** 仓储保存后回填 id。 */
    public void assignId(Long newId) {
        if (this.id != null && !this.id.equals(newId)) throw new DomainException("供应商 id 不可变更");
        this.id = newId;
    }

    // ---------------- 行为 ----------------

    public final void rename(String newName) {
        if (newName == null || newName.isBlank()) throw new DomainException("供应商名称不能为空");
        if (newName.trim().length() > 64) throw new DomainException("供应商名称过长（≤64）");
        this.name = newName.trim();
    }

    public final void changeEndpoint(String newBaseUrl) {
        String url = newBaseUrl == null ? null : newBaseUrl.trim();
        if (type == ProviderType.OPENAI_COMPATIBLE) {
            if (url == null || url.isEmpty()) throw new DomainException("OpenAI 兼容供应商必须填写 baseUrl");
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                throw new DomainException("baseUrl 必须以 http(s):// 开头");
            }
            while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;
    }

    public final void replaceModels(List<ModelSpec> newModels) {
        List<ModelSpec> list = newModels == null ? List.of() : List.copyOf(newModels);
        Set<String> names = new LinkedHashSet<>();
        for (ModelSpec m : list) {
            if (!names.add(m.model())) throw new DomainException("模型重复：" + m.model());
        }
        this.models = list;
    }

    /** 换成库内密文 key（null/空 = 不修改）。 */
    public void rotateKey(String newEncryptedKey) {
        if (newEncryptedKey == null || newEncryptedKey.isBlank()) return;
        this.encryptedKey = newEncryptedKey;
        this.keySource = KeySource.DB;
        this.keyEnv = null;
    }

    public void enable(boolean on) {
        this.enabled = on;
    }

    public void recordHealth(boolean ok, Integer latencyMs, LocalDateTime at) {
        this.health = new ProviderHealth(ok, latencyMs, at);
    }

    // ---------------- 查询 ----------------

    public Optional<ModelSpec> model(String modelName) {
        if (modelName == null) return Optional.empty();
        return models.stream().filter(m -> m.model().equals(modelName.trim())).findFirst();
    }

    public boolean supports(String modelName, Capability capability) {
        return model(modelName).map(m -> m.supports(capability)).orElse(false);
    }

    /** 第一个具备该能力的模型（兼容旧「按供应商切换」用）。 */
    public Optional<ModelSpec> firstModelFor(Capability capability) {
        return models.stream().filter(m -> m.supports(capability)).findFirst();
    }

    public boolean isMock() {
        return type == ProviderType.MOCK;
    }

    public String name() {
        return name;
    }

    public ProviderType type() {
        return type;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String encryptedKey() {
        return encryptedKey;
    }

    public KeySource keySource() {
        return keySource;
    }

    public String keyEnv() {
        return keyEnv;
    }

    public boolean enabled() {
        return enabled;
    }

    public List<ModelSpec> models() {
        return new ArrayList<>(models);
    }

    public ProviderHealth health() {
        return health;
    }
}
