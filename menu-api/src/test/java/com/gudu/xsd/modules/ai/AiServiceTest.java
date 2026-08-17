package com.gudu.xsd.modules.ai;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.ai.dto.CandidateDish;
import com.gudu.xsd.modules.ai.dto.MenuCandidate;
import com.gudu.xsd.modules.ai.dto.MenuRecommendRequest;
import com.gudu.xsd.modules.ai.mapper.AiCallLogMapper;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishSearchDTO;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.dish.DishQueryService;
import com.gudu.xsd.modules.dish.mapper.DishIngredientMapper;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.mapper.MemberMapper;
import com.gudu.xsd.modules.nutrition.mapper.IngredientMapper;
import com.gudu.xsd.modules.nutrition.IngredientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private MenuRecommender menuRecommender;
    private com.gudu.xsd.modules.dish.DishVectorService dishVectorService;
    private TasteProfileService tasteProfileService;

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
        menuRecommender = mock(MenuRecommender.class);
        dishVectorService = mock(com.gudu.xsd.modules.dish.DishVectorService.class);
        tasteProfileService = mock(TasteProfileService.class);

        svc = new AiService(aiClient, ingredientService, ingredientMapper,
                dishService, dishQueryService, dishIngredientMapper,
                memberMapper, aiCallLogMapper, new ObjectMapper(),
                new AiInputGuard(), menuRecommender, dishVectorService, tasteProfileService);
        // @Value 在 new 出来的实例上不生效，手动注入默认额度
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "dailyLimit", 50);
    }

    private Dish dish(long id, String name) {
        Dish d = new Dish();
        d.setId(id);
        d.setName(name);
        return d;
    }

    @Test
    void 菜单推荐_向量召回_组合_理由enrich() {
        // 向量召回：1 道候选（metadata dishId/name + score）
        org.springframework.ai.document.Document doc =
                new org.springframework.ai.document.Document("菜名：番茄炒蛋",
                        java.util.Map.of("dishId", 10, "name", "番茄炒蛋"));
        when(dishVectorService.semanticSearch(any(), anyInt(), any(), any()))
                .thenReturn(List.of(doc));
        when(tasteProfileService.profileText(any())).thenReturn("番茄炒蛋、番茄炒蛋");
        when(tasteProfileService.recentDishIds(any(), anyInt())).thenReturn(List.of());
        when(dishService.getById(10L)).thenReturn(dish(10L, "番茄炒蛋"));
        when(dishQueryService.nutrition(anyLong(), any())).thenReturn(java.util.Map.of());
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of());
        // 组合器返回一组（真实纯函数太重，直接 mock）
        var group = new MenuCandidate(
                List.of(new MenuCandidate.DishItem(10L, "番茄炒蛋", BigDecimal.ONE)),
                java.util.Map.of(), 1.0, List.of("蛋白含量较高，营养均衡"), "rule");
        when(menuRecommender.recommend(any(), any(), any(), any(), anyLong()))
                .thenReturn(List.of(group));

        var req = new MenuRecommendRequest(1L, "DAY", "清淡下饭", null, null, null, null, null);
        var out = svc.recommendMenu(req);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).source()).isEqualTo("vector");
        // 理由 enrich：口味相近在最前，原规则理由保留
        assertThat(out.get(0).reasons().get(0)).contains("番茄炒蛋");
        assertThat(out.get(0).reasons()).anyMatch(r -> r.contains("蛋白"));
        // 召回 query 带偏好 + 画像
        var cq = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(dishVectorService).semanticSearch(cq.capture(), anyInt(), any(), any());
        assertThat(cq.getValue()).contains("清淡下饭").contains("口味类似");
    }

    @Test
    void 菜单推荐_不排除做过的菜_全部进候选() {
        // 2026-08-17 定稿：不排除做过的菜（真爱菜反复出现），召回的所有菜都进候选
        org.springframework.ai.document.Document d1 =
                new org.springframework.ai.document.Document("a", java.util.Map.of("dishId", 10, "name", "A"));
        org.springframework.ai.document.Document d2 =
                new org.springframework.ai.document.Document("b", java.util.Map.of("dishId", 11, "name", "B"));
        when(dishVectorService.semanticSearch(any(), anyInt(), any(), any()))
                .thenReturn(List.of(d1, d2));
        when(tasteProfileService.profileText(any())).thenReturn("");
        when(dishService.getById(10L)).thenReturn(dish(10L, "A"));
        when(dishService.getById(11L)).thenReturn(dish(11L, "B"));
        when(dishQueryService.nutrition(anyLong(), any())).thenReturn(java.util.Map.of());
        when(dishIngredientMapper.selectList(any())).thenReturn(List.of());
        when(menuRecommender.recommend(any(), any(), any(), any(), anyLong()))
                .thenReturn(List.of());

        var req = new MenuRecommendRequest(1L, "DAY", null, null, null, null, null, null);
        svc.recommendMenu(req);

        // 两道召回菜都被查询进候选（无排除）
        verify(dishService).getById(10L);
        verify(dishService).getById(11L);
    }

    @Test
    void 菜单推荐_召回为空_返回空列表() {
        when(dishVectorService.semanticSearch(any(), anyInt(), any(), any()))
                .thenReturn(List.of());
        when(tasteProfileService.profileText(any())).thenReturn("");

        var req = new MenuRecommendRequest(1L, "DAY", null, null, null, null, null, null);
        assertThat(svc.recommendMenu(req)).isEmpty();
    }

    @Test
    void 菜单推荐_今日额度超限_抛BizException() {
        when(aiCallLogMapper.selectCount(any())).thenReturn(50L);
        var req = new MenuRecommendRequest(1L, "DAY", null, null, null, null, null, null);
        assertThatThrownBy(() -> svc.recommendMenu(req))
                .isInstanceOf(com.gudu.xsd.common.BizException.class)
                .hasMessageContaining("上限");
    }

    @Test
    void 菜单推荐_额度未超_正常调用() {
        when(aiCallLogMapper.selectCount(any())).thenReturn(3L);
        when(tasteProfileService.profileText(any())).thenReturn("");
        when(dishVectorService.semanticSearch(any(), anyInt(), any(), any()))
                .thenReturn(List.of());
        var req = new MenuRecommendRequest(1L, "DAY", null, null, null, null, null, null);
        svc.recommendMenu(req);
        verify(aiCallLogMapper).insert(any(AiCallLog.class));
    }

    @Test
    void 菜单推荐_无member_不限额度() {
        when(tasteProfileService.profileText(any())).thenReturn("");
        when(dishVectorService.semanticSearch(any(), anyInt(), any(), any()))
                .thenReturn(List.of());
        var req = new MenuRecommendRequest(null, "DAY", null, null, null, null, null, null);
        svc.recommendMenu(req);
        verify(aiCallLogMapper, never()).selectCount(any());
    }

    @Test
    void 营养补全_黑名单输入_拒绝_不调ai() {
        var req = new com.gudu.xsd.modules.ai.dto.NutritionFillRequest("怎么写代码", null);
        assertThatThrownBy(() -> svc.fillNutrition(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只能回答");
        verifyNoInteractions(aiClient);
    }

    @Test
    void 菜品估算_黑名单输入_拒绝_不调ai() {
        var req = new com.gudu.xsd.modules.ai.dto.DishEstimateRequest("讲个笑话", null);
        assertThatThrownBy(() -> svc.estimateDish(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只能回答");
        verifyNoInteractions(aiClient);
    }
}
