package com.gudu.xsd.modules.featureswitch.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.featureswitch.infrastructure.po.FeatureSwitchPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** sys_switch（V85）。 */
public interface FeatureSwitchMapper extends BaseMapper<FeatureSwitchPO> {

    @Insert("INSERT INTO sys_switch(switch_key, enabled, updated_by, updated_by_name, updated_at, reason) "
            + "VALUES (#{p.switchKey}, #{p.enabled}, #{p.updatedBy}, #{p.updatedByName}, #{p.updatedAt}, #{p.reason}) "
            + "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), updated_by = VALUES(updated_by), "
            + "updated_by_name = VALUES(updated_by_name), updated_at = VALUES(updated_at), reason = VALUES(reason)")
    int upsert(@Param("p") FeatureSwitchPO p);
}
