package com.gudu.xsd.modules.ai.gateway.domain.repository;

import com.gudu.xsd.modules.ai.gateway.domain.model.ModelProvider;

import java.util.List;
import java.util.Optional;

/** 供应商仓储（含下属模型整体保存）。 */
public interface ModelProviderRepository {

    List<ModelProvider> findAll();

    Optional<ModelProvider> findById(Long id);

    Optional<ModelProvider> findByName(String name);

    /** 新建回填 id；更新时整体替换下属模型。 */
    void save(ModelProvider provider);

    void delete(Long id);
}
