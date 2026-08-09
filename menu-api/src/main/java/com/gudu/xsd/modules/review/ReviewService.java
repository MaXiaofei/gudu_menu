package com.gudu.xsd.modules.review;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.mapper.DishMapper;
import com.gudu.xsd.modules.menu.Menu;
import com.gudu.xsd.modules.menu.MenuDish;
import com.gudu.xsd.modules.menu.mapper.MenuDishMapper;
import com.gudu.xsd.modules.menu.mapper.MenuMapper;
import com.gudu.xsd.modules.review.mapper.ReviewMapper;
import com.gudu.xsd.modules.review.mapper.ReviewScoreMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 点评服务（V43：支持食集评价）。
 *
 * <p>食集整体评价复用单菜四维度（review_score），review 表 dish_id/menu_id 二选一；
 * 单菜评价不关联食集（纯菜品维度）。
 *
 * <p>已评状态按「当前就餐成员」判定（评价记录 member 维度，食集本身不归属成员）。
 */
@Service
public class ReviewService {

    static final String MENU_STATUS_DONE = "DONE";

    private final ReviewMapper reviewMapper;
    private final ReviewScoreMapper reviewScoreMapper;
    private final MenuMapper menuMapper;
    private final MenuDishMapper menuDishMapper;
    private final DishMapper dishMapper;

    public ReviewService(ReviewMapper reviewMapper, ReviewScoreMapper reviewScoreMapper,
                         MenuMapper menuMapper, MenuDishMapper menuDishMapper, DishMapper dishMapper) {
        this.reviewMapper = reviewMapper;
        this.reviewScoreMapper = reviewScoreMapper;
        this.menuMapper = menuMapper;
        this.menuDishMapper = menuDishMapper;
        this.dishMapper = dishMapper;
    }

    /** 提交点评（菜品或食集）：当前就餐成员 + 级联维度分。 */
    @Transactional
    public Long submit(ReviewSaveDTO dto) {
        if ((dto.getDishId() == null) == (dto.getMenuId() == null)) {
            throw new BizException("菜品和食集评价二选一");
        }
        Long memberId = StpUtil.getSession().getLong("currentMemberId");
        if (memberId == null) throw new BizException("请先选择就餐成员");
        Review r = new Review();
        r.setDishId(dto.getDishId());
        r.setMenuId(dto.getMenuId());
        r.setMemberId(memberId);
        r.setStarRating(dto.getStarRating());
        r.setText(dto.getText());
        r.setImages(dto.getImages() == null ? null : String.join(",", dto.getImages()));
        reviewMapper.insert(r);
        if (dto.getDimensionScores() != null) {
            dto.getDimensionScores().forEach((dimId, score) -> {
                if (score == null || score < 1 || score > 5) throw new BizException("维度分需为 1-5");
                ReviewScore s = new ReviewScore();
                s.setReviewId(r.getId());
                s.setDimensionId(dimId);
                s.setScore(score);
                reviewScoreMapper.insert(s);
            });
        }
        return r.getId();
    }

    /** 某菜的所有点评（最新优先）。 */
    public List<Review> listByDish(Long dishId) {
        return reviewMapper.selectList(
            new QueryWrapper<Review>().eq("dish_id", dishId).orderByDesc("create_time"));
    }

    /** 某食集的所有整体评价（最新优先）。V43。 */
    public List<Review> listByMenu(Long menuId) {
        return reviewMapper.selectList(
            new QueryWrapper<Review>().eq("menu_id", menuId).orderByDesc("create_time"));
    }

    /** 统一评价页数据：食集信息 + 食集整体已评状态（我的）+ 每道菜已评状态（我的）。V43。 */
    public MenuReviewOverviewVO menuOverview(Long menuId) {
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null) throw new BizException("食集不存在");
        List<Long> dishIds = menuDishMapper.selectList(new QueryWrapper<MenuDish>().eq("menu_id", menuId))
                .stream().map(MenuDish::getDishId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Dish> dishMap = dishIds.isEmpty() ? Map.of()
                : dishMapper.selectBatchIds(dishIds).stream()
                        .collect(Collectors.toMap(Dish::getId, Function.identity(), (a, b) -> a));
        Long memberId = currentMemberId();

        // 食集整体评价（我的最近一条）
        MenuReviewOverviewVO.MenuReviewStatus menuReview = null;
        if (memberId != null) {
            Review mine = latestOne(reviewMapper.selectList(new QueryWrapper<Review>()
                    .eq("menu_id", menuId).eq("member_id", memberId).orderByDesc("create_time")));
            if (mine != null) {
                Map<Long, Integer> dims = reviewScoreMapper.selectList(
                                new QueryWrapper<ReviewScore>().eq("review_id", mine.getId())).stream()
                        .collect(Collectors.toMap(ReviewScore::getDimensionId, ReviewScore::getScore, (a, b) -> a));
                menuReview = new MenuReviewOverviewVO.MenuReviewStatus(true, mine.getStarRating(),
                        dims, mine.getCreateTime());
            }
        }
        // 每道菜已评状态（我的最近一条）
        Map<Long, Integer> starByDish = new LinkedHashMap<>();
        if (memberId != null && !dishIds.isEmpty()) {
            reviewMapper.selectList(new QueryWrapper<Review>()
                            .in("dish_id", dishIds).eq("member_id", memberId).orderByDesc("create_time"))
                    .forEach(r -> starByDish.putIfAbsent(r.getDishId(), r.getStarRating()));
        }
        List<MenuReviewOverviewVO.DishReviewStatus> dishes = new ArrayList<>();
        for (Long dishId : dishIds) {
            Dish d = dishMap.get(dishId);
            if (d == null) continue;
            dishes.add(new MenuReviewOverviewVO.DishReviewStatus(
                    dishId, d.getName(), d.getCoverUrl(), starByDish.get(dishId)));
        }
        return new MenuReviewOverviewVO(menuId, menu.getName(), menu.getFinishedAt(),
                dishes.size(), menuReview, dishes);
    }

    /** 我的评价：评价历史（食集+菜品）+ 待评价食集（已完成且没评完）。V43。 */
    public MyReviewsVO mine() {
        Long memberId = currentMemberId();
        if (memberId == null) {
            return new MyReviewsVO(List.of(), List.of());
        }
        // 评价历史
        List<Review> reviews = reviewMapper.selectList(new QueryWrapper<Review>()
                .eq("member_id", memberId).orderByDesc("create_time"));
        Set<Long> dishIds = reviews.stream().map(Review::getDishId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> menuIds = reviews.stream().map(Review::getMenuId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> dishName = dishIds.isEmpty() ? Map.of()
                : dishMapper.selectBatchIds(dishIds).stream()
                        .collect(Collectors.toMap(Dish::getId, Dish::getName, (a, b) -> a));
        Map<Long, String> menuName = menuIds.isEmpty() ? Map.of()
                : menuMapper.selectBatchIds(menuIds).stream()
                        .collect(Collectors.toMap(Menu::getId, Menu::getName, (a, b) -> a));
        List<MyReviewsVO.ReviewEntry> entries = reviews.stream()
                .map(r -> new MyReviewsVO.ReviewEntry(
                        r.getId(), r.getDishId(), r.getMenuId(),
                        r.getDishId() != null ? dishName.get(r.getDishId()) : menuName.get(r.getMenuId()),
                        r.getStarRating(), r.getCreateTime()))
                .filter(e -> e.name() != null)
                .toList();

        // 待评价食集：DONE 且（食集整体没评 或 还有菜没评）
        List<Menu> doneMenus = menuMapper.selectList(new QueryWrapper<Menu>()
                .eq("status", MENU_STATUS_DONE).orderByDesc("finished_at"));
        List<MyReviewsVO.PendingMenu> pending = new ArrayList<>();
        if (!doneMenus.isEmpty()) {
            List<Long> doneMenuIds = doneMenus.stream().map(Menu::getId).toList();
            List<MenuDish> allMds = menuDishMapper.selectList(
                    new QueryWrapper<MenuDish>().in("menu_id", doneMenuIds));
            Map<Long, List<MenuDish>> mdsByMenu = allMds.stream()
                    .collect(Collectors.groupingBy(MenuDish::getMenuId));
            Set<Long> myReviewedMenus = reviewMapper.selectList(new QueryWrapper<Review>()
                            .eq("member_id", memberId).in("menu_id", doneMenuIds)).stream()
                    .map(Review::getMenuId).collect(Collectors.toSet());
            Set<Long> allDishIds = allMds.stream().map(MenuDish::getDishId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Set<Long> myReviewedDishes = allDishIds.isEmpty() ? Set.of()
                    : reviewMapper.selectList(new QueryWrapper<Review>()
                            .eq("member_id", memberId).in("dish_id", allDishIds)).stream()
                            .map(Review::getDishId).collect(Collectors.toSet());
            for (Menu m : doneMenus) {
                List<MenuDish> mds = mdsByMenu.getOrDefault(m.getId(), List.of());
                int dishCount = (int) mds.stream().map(MenuDish::getDishId).filter(Objects::nonNull).distinct().count();
                int reviewedCount = (int) mds.stream().map(MenuDish::getDishId)
                        .filter(Objects::nonNull).filter(myReviewedDishes::contains).distinct().count();
                boolean menuReviewed = myReviewedMenus.contains(m.getId());
                if (!menuReviewed || reviewedCount < dishCount) {
                    pending.add(new MyReviewsVO.PendingMenu(m.getId(), m.getName(),
                            m.getFinishedAt(), dishCount, reviewedCount, menuReviewed));
                }
            }
        }
        return new MyReviewsVO(entries, pending);
    }

    // ===== 纯函数：平均分（TDD 覆盖） =====

    /** 总评星级均分（保留 1 位）。 */
    public BigDecimal averageStar(List<Integer> stars) {
        if (stars == null || stars.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = stars.stream().map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(stars.size()), 1, RoundingMode.HALF_UP);
    }

    /** 各维度均分：dimensionId -> "x.x"。 */
    public Map<Long, String> averageByDimension(List<? extends DimensionScore> rows) {
        Map<Long, List<Integer>> grouped = rows.stream()
            .filter(r -> r.dimensionId() != null && r.score() != null)
            .collect(Collectors.groupingBy(DimensionScore::dimensionId,
                Collectors.mapping(DimensionScore::score, Collectors.toList())));
        return grouped.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> averageStar(e.getValue()).toPlainString()));
    }

    /** 维度分行接口。 */
    public interface DimensionScore {
        Long dimensionId();
        Integer score();
    }

    // ===== 内部辅助 =====

    /** 当前就餐成员（未登录/未选择返回 null，mine/overview 容错）。 */
    private Long currentMemberId() {
        try {
            return StpUtil.getSession().getLong("currentMemberId");
        } catch (Exception e) {
            return null;
        }
    }

    /** 已按 create_time 倒序的列表 → 最近一条。 */
    private Review latestOne(List<Review> rowsDesc) {
        return rowsDesc == null || rowsDesc.isEmpty() ? null : rowsDesc.get(0);
    }
}
