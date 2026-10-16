package com.gudu.xsd.common;

import lombok.Data;

/**
 * 分页查询基类。pageNum/pageSize 在 getter 处钳制到合法范围：
 * 负数/0 会让 MyBatis-Plus 跳过分页（等于查全表），必须拦住。
 */
@Data
public class PageQuery {

    /** 单页上限：客户端「拉全量下拉选项」现用 pageSize=1000，暂以此为界（全量选项接口落地后再收紧）。 */
    public static final int MAX_PAGE_SIZE = 1000;

    private Integer pageNum = 1;

    /** 每页条数，默认 15（DESIGN.md §12.2 列表分页约定）。 */
    private Integer pageSize = 15;

    public Integer getPageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public Integer getPageSize() {
        if (pageSize == null || pageSize < 1) return 15;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
