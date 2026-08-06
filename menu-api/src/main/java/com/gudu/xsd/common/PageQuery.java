package com.gudu.xsd.common;

import lombok.Data;

/**
 * 分页查询基类。
 */
@Data
public class PageQuery {

    private Integer pageNum = 1;

    /** 每页条数，默认 15（DESIGN.md §12.2 列表分页约定）。 */
    private Integer pageSize = 15;
}
