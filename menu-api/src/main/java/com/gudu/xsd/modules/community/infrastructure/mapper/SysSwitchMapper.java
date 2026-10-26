package com.gudu.xsd.modules.community.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gudu.xsd.modules.community.infrastructure.po.SysSwitchPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** sys_switch（V85）。 */
public interface SysSwitchMapper extends BaseMapper<SysSwitchPO> {

    @Insert("INSERT INTO sys_switch(switch_key, enabled, updated_by, updated_by_name, updated_at, reason) "
            + "VALUES (#{p.switchKey}, #{p.enabled}, #{p.updatedBy}, #{p.updatedByName}, #{p.updatedAt}, #{p.reason}) "
            + "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), updated_by = VALUES(updated_by), "
            + "updated_by_name = VALUES(updated_by_name), updated_at = VALUES(updated_at), reason = VALUES(reason)")
    int upsert(@Param("p") SysSwitchPO p);
}
