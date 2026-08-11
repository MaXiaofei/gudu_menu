package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishQueryService;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MenuService extends ServiceImpl<MenuMapper, Menu> {

    private final MenuDishMapper menuDishMapper;
    private final DishMapper dishMapper;
    private final DishQueryService dishQueryService;
    private final MenuCalcService menuCalc;

    /**
     * 分页查食集列表。按创建时间倒序。
     * status 非空时过滤（ACTIVE 进行中 / DONE 已完成）；缺省查全部。
     * 回填 dishCount + covers（前 3 个菜的封面，列表缩略堆叠用）。
     */
    public IPage<Menu> page(MenuPageQuery q) {
        QueryWrapper<Menu> w = new QueryWrapper<Menu>().orderByDesc("create_time");
        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            w.eq("status", q.getStatus());
        }
        IPage<Menu> result = page(new Page<>(q.getPageNum(), q.getPageSize()), w);
        fillDishCountAndCovers(result.getRecords());
        return result;
    }

    /**
     * 批量回填当前页各食集的菜数 + 前 3 个菜的封面。
     * 一次查所有关联 menu_dish，再一次批量查 dish 取 coverUrl，消除 N+1。
     */
    private void fillDishCountAndCovers(List<Menu> menus) {
        if (menus == null || menus.isEmpty()) return;
        for (Menu m : menus) {
            m.setDishCount(0);
            m.setCovers(List.of());
        }
        List<Long> menuIds = menus.stream().map(Menu::getId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (menuIds.isEmpty()) return;
        // 当前页所有 menu_dish 关联，按 menuId 分组保留顺序
        List<MenuDish> mds = menuDishMapper.selectList(
                new QueryWrapper<MenuDish>().in("menu_id", menuIds).orderByAsc("id"));
        if (mds.isEmpty()) return;
        // 批量查涉及的菜，取 coverUrl
        List<Long> dishIds = mds.stream().map(MenuDish::getDishId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Dish> dishById = dishIds.isEmpty() ? Map.of()
                : dishMapper.selectBatchIds(dishIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Dish::getId, d -> d, (a, b) -> a));
        // 按 menuId 分组，算菜数、取前 3 个有封面的 coverUrl
        Map<Long, List<MenuDish>> byMenu = mds.stream()
                .collect(java.util.stream.Collectors.groupingBy(MenuDish::getMenuId));
        for (Menu m : menus) {
            List<MenuDish> rels = byMenu.getOrDefault(m.getId(), List.of());
            m.setDishCount(rels.size());
            List<String> covers = rels.stream()
                    .map(MenuDish::getDishId)
                    .map(dishById::get)
                    .filter(java.util.Objects::nonNull)
                    .map(Dish::getCoverUrl)
                    .filter(c -> c != null && !c.isBlank())
                    .limit(3)
                    .toList();
            m.setCovers(covers);
        }
    }

    /** 保存菜单并整体替换其菜品关联。 */
    @Transactional
    public void saveWithDishes(MenuSaveDTO dto) {
        Menu menu = dto.getMenu();
        if (menu.getServingCount() == null) {
            menu.setServingCount(1);
        }
        // 显式判断：id 非 null 走 update，避免 saveOrUpdate 智能判断在
        // 某些场景错走 insert（Duplicate entry for key 'menu.PRIMARY'）
        if (menu.getId() != null) {
            updateById(menu);
        } else {
            save(menu);
        }
        menuDishMapper.delete(new QueryWrapper<MenuDish>().eq("menu_id", menu.getId()));
        if (dto.getDishes() != null) {
            for (MenuDish md : dto.getDishes()) {
                md.setId(null);
                md.setMenuId(menu.getId());
                menuDishMapper.insert(md);
            }
        }
    }

    /** 详情：菜单 + 关联菜品列表（每项冗余菜名/封面/备注，避免前端逐菜 GET /dish/{id} 取名 N+1）。
     *  totalMinutes = Σ 各菜烹饪时间（原型副标题「约 N 分钟」）。 */
    public MenuDetail detail(Long id) {
        Menu menu = getById(id);
        List<MenuDish> mds = menuDishMapper.selectList(
                new QueryWrapper<MenuDish>().eq("menu_id", id).orderByAsc("id"));
        if (mds.isEmpty()) return new MenuDetail(menu, List.of(), 0);
        // 一次批量查所有菜名/封面，消除前端 N+1（评审 gap）。
        List<Long> dishIds = mds.stream().map(MenuDish::getDishId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Dish> dishById = dishIds.isEmpty() ? Map.of()
                : dishMapper.selectBatchIds(dishIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Dish::getId, d -> d, (a, b) -> a));
        int totalMinutes = 0;
        List<MenuDishVO> dishes = new ArrayList<>();
        for (MenuDish md : mds) {
            Dish d = dishById.get(md.getDishId());
            if (d != null && d.getCookTime() != null) totalMinutes += d.getCookTime();
            // V45：自定义菜名（dish_id 空）用 customName 作展示名；朋友点的带 addedByNickname
            dishes.add(new MenuDishVO(md.getId(), md.getMenuId(), md.getDishId(), md.getServingFactor(),
                    d != null ? d.getName() : md.getCustomName(),
                    d != null ? d.getCoverUrl() : null,
                    md.getNote(), md.getAddedByNickname(), md.getCustomName()));
        }
        return new MenuDetail(menu, dishes, totalMinutes);
    }

    /**
     * 修改/删除食集中某道菜的备注。
     * 按 menu_id + dish_id 定位行，只 update note 字段，不动其它行
     * （不能走 saveWithDishes 整单替换——那会丢全部备注）。
     * note 为 null 或空串 = 删除备注。
     */
    public boolean updateDishNote(Long menuId, Long dishId, String note) {
        if (menuId == null || dishId == null) return false;
        List<MenuDish> rows = menuDishMapper.selectList(new QueryWrapper<MenuDish>()
                .eq("menu_id", menuId).eq("dish_id", dishId));
        if (rows.isEmpty()) return false;
        String value = (note == null || note.isBlank()) ? null : note.trim();
        for (MenuDish md : rows) {
            // 用 LambdaUpdateWrapper.set 强制写 note（含 null 也要 SET，
            // updateById 默认跳过 null 字段，删除备注会失效）
            menuDishMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MenuDish>()
                    .eq(MenuDish::getId, md.getId())
                    .set(MenuDish::getNote, value));
        }
        return true;
    }

    /**
     * 从食集中移除某道菜（删除 menu_dish 行，不动其它菜和食集本身）。
     * 行级删除，不走 saveWithDishes 整单替换（那会重建全部关联行）。
     */
    public boolean removeDish(Long menuId, Long dishId) {
        if (menuId == null || dishId == null) return false;
        int deleted = menuDishMapper.delete(new QueryWrapper<MenuDish>()
                .eq("menu_id", menuId).eq("dish_id", dishId));
        return deleted > 0;
    }

    /** 一起吃 tab 数量（占位：协同点菜功能待建，先返回 0 让前端可显示汇总数量）。 */
    public int getTogetherCount(Long menuId) {
        return 0;
    }

    /**
     * 再做一次（食记单条详情）：复制食集 + 菜品关联（份数/备注/customName），
     * 新建为 ACTIVE（进行中）食集，added_by 归新 owner。返回新食集 id。
     */
    @Transactional
    public Long copyMenu(Long menuId, Long memberId) {
        Menu src = getById(menuId);
        if (src == null) {
            throw new BizException("食集不存在");
        }
        MenuSaveDTO dto = new MenuSaveDTO();
        Menu copy = new Menu();
        copy.setName(src.getName());
        copy.setTypeId(src.getTypeId());
        copy.setServingCount(src.getServingCount());
        copy.setStatus("ACTIVE");
        copy.setFinishedAt(null);
        dto.setMenu(copy);

        List<MenuDish> mds = menuDishMapper.selectList(
                new QueryWrapper<MenuDish>().eq("menu_id", menuId).orderByAsc("id"));
        List<MenuDish> copies = mds.stream().map(d -> {
            MenuDish nd = new MenuDish();
            nd.setDishId(d.getDishId());
            nd.setCustomName(d.getCustomName());
            nd.setServingFactor(d.getServingFactor());
            nd.setNote(d.getNote());
            nd.setAddedByMemberId(memberId); // 复制后统一归新 owner
            nd.setAddedByNickname(null);
            return nd;
        }).toList();
        dto.setDishes(copies);
        saveWithDishes(dto);
        return copy.getId();
    }

    /** 菜单汇总：各菜份数营养（复用 NutritionCalcService），调 MenuCalcService 纯函数。
     *  V55：价格链路整体删除（食材去单位后不再按克计价）。 */
    public MenuSummary summary(Long id) {
        MenuDetail md = detail(id);
        List<MenuCalcService.MenuLine> lines = new ArrayList<>();
        for (MenuDishVO d : md.dishes()) {
            Map<Long, BigDecimal> nut = dishQueryService.nutrition(d.dishId(), BigDecimal.ONE);
            BigDecimal factor = (d.servingFactor() != null) ? d.servingFactor() : BigDecimal.ONE;
            lines.add(new MenuCalcService.MenuLine(nut, factor));
        }
        return new MenuSummary(menuCalc.totalNutrition(lines));
    }

    /** MenuDish 视图：冗余菜名/封面/备注，避免前端逐菜 GET /dish/{id} 取名（评审 N+1）。 */
    public record MenuDishVO(Long id, Long menuId, Long dishId, BigDecimal servingFactor,
                             String dishName, String coverUrl, String note,
                             String addedByNickname, String customName) {}

    /** 食集详情：menu + 菜列表 + 总烹饪分钟（Σ 各菜 cookTime，原型副标题「约 N 分钟」）。 */
    public record MenuDetail(Menu menu, List<MenuDishVO> dishes, int totalMinutes) {}

    public record MenuSummary(Map<Long, BigDecimal> totalNutrition) {}
}
