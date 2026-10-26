package com.gudu.xsd.modules.consent.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** consent_record 表持久化对象（V86，全局表）。 */
@Data
@TableName("consent_record")
public class ConsentRecordPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private String docType;
    private String docVersion;
    private Integer granted;
    private String channel;
    private String clientIp;
    private LocalDateTime createdAt;
}
