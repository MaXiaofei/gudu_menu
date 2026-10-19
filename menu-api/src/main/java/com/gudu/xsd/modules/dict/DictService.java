package com.gudu.xsd.modules.dict;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DictService extends ServiceImpl<DictMapper, SysDict> {

    public List<SysDict> listByGroup(String group) {
        return list(new QueryWrapper<SysDict>().eq("dict_group", group).orderByAsc("sort"));
    }

    /** 某 group 下指定名称的字典 id 集合（如 purchase_category/调味料 → 调料判定）。 */
    public java.util.Set<Long> idsByName(String group, String name) {
        return list(new QueryWrapper<SysDict>().eq("dict_group", group).eq("name", name)).stream()
                .map(SysDict::getId).collect(java.util.stream.Collectors.toSet());
    }

    /** 分页查某 group 字典（后台管理）。按 sort 升序。 */
    public IPage<SysDict> pageByGroup(String group, PageQuery q) {
        return page(new Page<>(q.getPageNum(), q.getPageSize()),
                new QueryWrapper<SysDict>().eq("dict_group", group).orderByAsc("sort"));
    }
}
