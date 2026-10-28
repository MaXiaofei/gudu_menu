package com.gudu.xsd.modules.featureswitch.domain.repository;

import com.gudu.xsd.modules.featureswitch.domain.model.FeatureSwitch;

import java.util.List;
import java.util.Optional;

/** 紧急关停开关的持久化（sys_switch / sys_switch_log，V85）。 */
public interface FeatureSwitchRepository {

    Optional<FeatureSwitch> load(String key);

    /** 保存当前状态并追加一条修改日志（同一事务内）。 */
    void save(FeatureSwitch state);

    /** 某开关最近的修改日志（新→旧）。 */
    List<FeatureSwitch> recentChanges(String key, int limit);
}
