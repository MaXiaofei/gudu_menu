package com.gudu.xsd.modules.community.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** sys_switch（V85）运行时功能开关。 */
@Data
@TableName("sys_switch")
public class SysSwitchPO {

    @TableId(type = IdType.INPUT)
    private String switchKey;
    private Integer enabled;
    private Long updatedBy;
    private String updatedByName;
    private LocalDateTime updatedAt;
    private String reason;
}
