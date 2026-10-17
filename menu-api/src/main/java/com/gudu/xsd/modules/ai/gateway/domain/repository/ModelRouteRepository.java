package com.gudu.xsd.modules.ai.gateway.domain.repository;

import com.gudu.xsd.modules.ai.gateway.domain.model.Capability;
import com.gudu.xsd.modules.ai.gateway.domain.model.ModelRoute;

import java.util.List;
import java.util.Optional;

/** 能力路由仓储。 */
public interface ModelRouteRepository {

    List<ModelRoute> findAll();

    Optional<ModelRoute> find(Capability capability);

    void save(ModelRoute route);
}
