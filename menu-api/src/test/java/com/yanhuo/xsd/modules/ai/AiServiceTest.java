package com.yanhuo.xsd.modules.ai;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanhuo.xsd.modules.ai.dto.CandidateDish;
import com.yanhuo.xsd.modules.ai.dto.MenuCandidate;
import com.yanhuo.xsd.modules.ai.dto.MenuRecommendRequest;
import com.yanhuo.xsd.modules.ai.mapper.AiCallLogMapper;
import com.yanhuo.xsd.modules.dish.Dish;
import com.yanhuo.xsd.modules.dish.DishSearchDTO;
import com.yanhuo.xsd.modules.dish.DishService;
import com.yanhuo.xsd.modules.dish.DishQueryService;
import com.yanhuo.xsd.modules.dish.mapper.DishIngredientMapper;
import com.yanhuo.xsd.modules.member.Member;
import com.yanhuo.xsd.modules.member.mapper.MemberMapper;
import com.yanhuo.xsd.modules.nutrition.mapper.IngredientMapper;
import com.yanhuo.xsd.modules.nutrition.IngredientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiService 单元测试：验证菜单推荐现在调 aiClient.recommendMenu（而非直接 MenuRecommender），
 * 且候选上下文 + 健康约束被正确组装回填进 req。
 *
 * <p>所有依赖均 mock（mapper / service），AiService 直接 new（@RequiredArgsConstructor 范式）。
 */
class AiServiceTest {

    private AiClient aiClient;
    private AiService svc;
    private DishService dishService;
    private DishQueryService dishQueryService;
    private MemberMapper memberMapper;
    private AiCallLogMapper aiCallLogMapper;
    private DishIngredientMapper dishIngredientMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        aiClient = mock(AiClient.class);
        IngredientService ingredientService = mock(IngredientService.class);
        IngredientMapper ingredientMapper = mock(IngredientMapper.class);
        dishService = mock(DishService.class);
        dishQueryService = mock(DishQueryService.class);
        dishIngredientMapper = mock(DishIngredientMapper.class);
        memberMapper = mock(MemberMapper.class);
        aiCallLogMapper = mock(AiCallLogMapper.class);

        svc = new AiService(aiClient, ingredientService, ingredientMapper,
                dishService, dishQueryService, dishIngredientMapper,
                memberMapper, aiCallLogMapper, new ObjectMapper());
    }

    private Dish dish(long id, String name, String price) {
        Dish d = new Dish();
        d.setId(id);
        d.setName(name);
        d.setPrice(new BigDecimal(price));
        return d;
    }

    @Test
    void 菜单推荐_调aiClient_不再直接MenuRecommender() {
        // mock 候选池
        IPage<Dish> page = mock(IPage.class);
        when(page.getRecords()).thenReturn(List.of(dish(1, "番茄炒蛋", "10"), dish(2, "黄瓜", "5")));
        when(dishService.search(any(DishSearchDTO.class))).thenReturn(page);
        when(dishQueryService.nutrition(anyLong(), any(BigDecimal.class)))
                .thenReturn(Map.of(2L, new BigDecimal("10")));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of());
        when(memberMapper.selectById(anyLong())).thenReturn(null); // 无健康档案

        when(aiClient.recommendMenu(any())).thenReturn(List.of());

        var req = new MenuRecommendRequest(1L, new BigDecimal("100"), "DAY",
                null, null, null, null, null);
        svc.recommendMenu(req);

        // 关键断言：aiClient.recommendMenu 被调用
        verify(aiClient).recommendMenu(any());
    }

    @Test
    void 菜单推荐_候选上下文与健康约束被回填() {
        Member m = new Member();
        m.setId(1L);
        m.setHealthProfile(Map.of(
                "constraints", Map.of("sugarMax", 25, "calMax", 600),
                "allergies", List.of("花生")));
        when(memberMapper.selectById(1L)).thenReturn(m);

        IPage<Dish> page = mock(IPage.class);
        when(page.getRecords()).thenReturn(List.of(dish(1, "番茄炒蛋", "10")));
        when(dishService.search(any(DishSearchDTO.class))).thenReturn(page);
        when(dishQueryService.nutrition(eq(1L), any(BigDecimal.class)))
                .thenReturn(Map.of(2L, new BigDecimal("12"), 5L, new BigDecimal("3")));
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.recommendMenu(any())).thenReturn(List.of());

        var req = new MenuRecommendRequest(1L, new BigDecimal("100"), "DAY",
                null, null, null, null, null);
        svc.recommendMenu(req);

        // 捕获传给 aiClient 的 req，断言 candidates + healthConstraints 已回填
        ArgumentCaptor<MenuRecommendRequest> cap = ArgumentCaptor.forClass(MenuRecommendRequest.class);
        verify(aiClient).recommendMenu(cap.capture());
        MenuRecommendRequest enriched = cap.getValue();
        assertThat(enriched.candidates()).hasSize(1);
        CandidateDish c = enriched.candidates().get(0);
        assertThat(c.dishId()).isEqualTo(1L);
        assertThat(c.name()).isEqualTo("番茄炒蛋");
        assertThat(c.price()).isEqualByComparingTo("10");
        assertThat(c.nutrition()).containsEntry(2L, new BigDecimal("12"));
        // healthConstraints 含 sugarMax / calMax / allergies
        assertThat(enriched.healthConstraints()).containsKey("sugarMax");
        assertThat(enriched.healthConstraints()).containsKey("allergies");
    }

    @Test
    void 菜单推荐_返回aiClient结果() {
        IPage<Dish> page = mock(IPage.class);
        when(page.getRecords()).thenReturn(List.of(dish(1, "番茄炒蛋", "10")));
        when(dishService.search(any(DishSearchDTO.class))).thenReturn(page);
        when(dishQueryService.nutrition(anyLong(), any(BigDecimal.class))).thenReturn(Map.of());
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of());
        when(memberMapper.selectById(anyLong())).thenReturn(null);

        MenuCandidate expected = new MenuCandidate(
                List.of(new MenuCandidate.DishItem(1L, "番茄炒蛋", BigDecimal.ONE, new BigDecimal("10"))),
                new BigDecimal("10"), Map.of(), 0.0, List.of("清淡"), "deepseek");
        when(aiClient.recommendMenu(any())).thenReturn(List.of(expected));

        var req = new MenuRecommendRequest(1L, new BigDecimal("100"), "DAY",
                null, null, null, null, null);
        var out = svc.recommendMenu(req);
        assertThat(out).containsExactly(expected);
        // 日志记录一次
        verify(aiCallLogMapper, atLeastOnce()).insert(any());
    }
}
