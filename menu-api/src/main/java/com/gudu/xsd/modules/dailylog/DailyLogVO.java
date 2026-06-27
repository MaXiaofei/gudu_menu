package com.gudu.xsd.modules.dailylog;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 某日饮食记录返回体：日志 + 摄入明细。
 */
@Data
public class DailyLogVO {

    private Long id;
    private Long memberId;
    private LocalDate date;
    private String note;
    private LocalDateTime createTime;
    private List<DailyLogItem> items;
}
