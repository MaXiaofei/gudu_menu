package com.gudu.xsd.modules.community.domain.repository;

import com.gudu.xsd.modules.community.domain.model.CommunitySwitch;

import java.util.List;
import java.util.Optional;

/** 厨房圈紧急关停开关的持久化（sys_switch / sys_switch_log，V85）。 */
public interface CommunitySwitchRepository {

    Optional<CommunitySwitch> load();

    /** 保存当前状态并追加一条修改日志（同一事务内）。 */
    void save(CommunitySwitch state);

    /** 最近的修改日志（新→旧）。 */
    List<CommunitySwitch> recentChanges(int limit);
}
