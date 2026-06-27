package com.gudu.xsd.common;

import lombok.Data;

/**
 * 分页查询基类。
 */
@Data
public class PageQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
