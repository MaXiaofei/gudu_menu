package com.gudu.xsd.modules.dailylog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日饮食记录：某就餐成员某日的一条饮食记录(备注为主)，明细见 {@link DailyLogItem}。
 * 营养汇总由后端按 items 实时聚合，不在此表冗余存储。
 */
@Data
@TableName("daily_log")
public class DailyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭（租户，multi-tenant-family）：租户插件按当前家庭自动写入与过滤，客户端不可写、不可改。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    private Long memberId;

    private LocalDate date;

    private String note;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
