package com.gudu.xsd.modules.dict;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.modules.dict.mapper.DictMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 字典服务单元测试：mock DictMapper，验证 group 过滤 + sort 排序 + 分页。
 * 范式照 DishServiceTest：手动 mock + 反射注入 ServiceImpl.baseMapper。
 */
class DictServiceTest {

    private DictMapper dictMapper;
    private DictService svc;

    @BeforeEach
    void setUp() {
        dictMapper = Mockito.mock(DictMapper.class);
        svc = new DictService();
        injectBaseMapper(svc, dictMapper);
    }

    private static void injectBaseMapper(DictService svc, DictMapper mapper) {
        try {
            var f = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
            f.setAccessible(true);
            f.set(svc, mapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static SysDict dict(long id, String group, String name, int sort) {
        SysDict d = new SysDict();
        d.setId(id);
        d.setDictGroup(group);
        d.setName(name);
        d.setSort(sort);
        return d;
    }

    /** listByGroup：按 group 过滤，返回该组字典（service 用 list() → selectList）。 */
    @Test
    void listByGroup_按组过滤_返回该组全部() {
        when(dictMapper.selectList(any())).thenReturn(List.of(
                dict(1L, "unit", "克", 1),
                dict(2L, "unit", "个", 2)));

        List<SysDict> result = svc.listByGroup("unit");

        assertThat(result).extracting(SysDict::getName).containsExactly("克", "个");
    }

    /** pageByGroup：返回分页结果，total/records 正确。 */
    @Test
    void pageByGroup_返回分页() {
        Page<SysDict> mp = new Page<>(1, 10, 2L);
        mp.setRecords(List.of(dict(1L, "cuisine", "川菜", 1), dict(2L, "cuisine", "粤菜", 2)));
        when(dictMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(mp);

        PageQuery q = new PageQuery();
        q.setPageNum(1);
        q.setPageSize(10);
        IPage<SysDict> page = svc.pageByGroup("cuisine", q);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(SysDict::getName).containsExactly("川菜", "粤菜");
    }

    /** pageByGroup：用 ArgumentCaptor 验证传入的分页参数（pageNum=2 / pageSize=5）。 */
    @Test
    void pageByGroup_分页参数透传正确() {
        Page<SysDict> mp = new Page<>(2, 5, 0L);
        mp.setRecords(List.of());
        when(dictMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(mp);

        PageQuery q = new PageQuery();
        q.setPageNum(2);
        q.setPageSize(5);
        svc.pageByGroup("tag", q);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<SysDict>> captor = ArgumentCaptor.forClass(Page.class);
        Mockito.verify(dictMapper).selectPage(captor.capture(), any(Wrapper.class));
        assertThat(captor.getValue().getCurrent()).isEqualTo(2);
        assertThat(captor.getValue().getSize()).isEqualTo(5);
    }
}
