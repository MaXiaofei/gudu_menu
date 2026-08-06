package com.gudu.xsd.modules.menu;

import com.gudu.xsd.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 食集列表查询入参（继承分页）。
 *
 * status 可选过滤：ACTIVE 进行中 / DONE 已完成；缺省查全部。
 * 排序固定按 create_time 倒序。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuPageQuery extends PageQuery {

    /** 状态过滤：ACTIVE 进行中 / DONE 已完成；null = 全部。 */
    private String status;
}
