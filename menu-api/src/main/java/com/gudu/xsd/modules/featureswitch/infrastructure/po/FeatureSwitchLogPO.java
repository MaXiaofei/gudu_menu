package com.gudu.xsd.modules.featureswitch.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** sys_switch_log（V85）开关修改日志。 */
@Data
@TableName("sys_switch_log")
public class FeatureSwitchLogPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String switchKey;
    private Integer enabled;
    private Long operatorId;
    private String operatorName;
    private String reason;
    private LocalDateTime createTime;
}
