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

    private Long memberId;

    private LocalDate date;

    private String note;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
