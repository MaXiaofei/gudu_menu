package com.gudu.xsd.modules.dish;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.dish.DishDraftDTO.DraftIngredient;
import com.gudu.xsd.modules.dish.DishDraftDTO.Save;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.modules.dish.mapper.DishDraftMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 写菜谱草稿（§16.4）：save 新建/更新、list 轻量计数、detail JSON 解析、delete 归属校验。
 * StpUtil 静态 mock（照 ReviewServiceTest 先例）；ObjectMapper 用真实 Jackson。
 */
class DishDraftServiceTest {

    private DishDraftMapper mapper;
    private DishDraftService svc;

    @BeforeEach
    void setUp() {
        mapper = mock(DishDraftMapper.class);
        svc = new DishDraftService(mapper, new ObjectMapper());
    }

    private Save sample() {
        Save s = new Save();
        s.setName("番茄牛腩");
        s.setPrepTime(15);
        s.setCookTime(60);
        s.setDifficulty(3);
        s.setNote("牛腩炖烂一点");
        s.setIngredients(List.of(ing("番茄", "2 个"), ing("姜", "适量")));
        DishStep step = new DishStep();
        step.setSeq(1);
        step.setText("牛腩切块焯水");
        s.setSteps(List.of(step));
        return s;
    }

    private static DraftIngredient ing(String name, String amount) {
        DraftIngredient i = new DraftIngredient();
        i.setIngredientId(1L);
        i.setIngredientName(name);
        i.setAmountText(amount);
        return i;
    }

    private MockedStatic<StpUtil> mockUser(Long uid) {
        MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
        SaSession session = mock(SaSession.class);
        stp.when(StpUtil::getSession).thenReturn(session);
        when(session.getLong("currentMemberId")).thenReturn(uid);
        return stp;
    }

    @Test
    void saveNew_assignsUserAndReturnsId() {
        try (MockedStatic<StpUtil> stp = mockUser(7L)) {
            when(mapper.insert(any(DishDraft.class))).thenAnswer(inv -> {
                DishDraft d = inv.getArgument(0);
                d.setId(99L);
                return 1;
            });
            Long id = svc.save(sample());
            assertThat(id).isEqualTo(99L);
            verify(mapper).insert(any(DishDraft.class));
        }
    }

    @Test
    void saveUpdate_otherUsersDraft_rejected() {
        try (MockedStatic<StpUtil> stp = mockUser(7L)) {
            DishDraft mine = new DishDraft();
            mine.setId(5L);
            mine.setUserId(8L); // 别人的草稿
            when(mapper.selectById(5L)).thenReturn(mine);
            Save s = sample();
            s.setId(5L);
            assertThatThrownBy(() -> svc.save(s))
                    .isInstanceOf(BizException.class);
        }
    }

    @Test
    void detail_parsesJsonFields() {
        try (MockedStatic<StpUtil> stp = mockUser(7L)) {
            DishDraft d = new DishDraft();
            d.setId(5L);
            d.setUserId(7L);
            d.setName("未命名草稿");
            d.setNote("家常做法");
            d.setTags("[1,2]");
            d.setIngredients(
                    "[{\"ingredientId\":1,\"ingredientName\":\"番茄\",\"amountText\":\"2 个\"}]");
            d.setSteps("[{\"seq\":1,\"text\":\"焯水\"}]");
            when(mapper.selectById(5L)).thenReturn(d);

            var v = svc.detail(5L);
            assertThat(v.getTagIds()).containsExactly(1L, 2L);
            assertThat(v.getIngredients()).hasSize(1);
            assertThat(v.getIngredients().get(0).getAmountText()).isEqualTo("2 个");
            assertThat(v.getSteps()).hasSize(1);
        }
    }

    @Test
    void list_paginatesAndCountsJsonItems() {
        try (MockedStatic<StpUtil> stp = mockUser(7L)) {
            DishDraft d = new DishDraft();
            d.setId(5L);
            d.setUserId(7L);
            d.setName("番茄牛腩");
            d.setIngredients("[{\"ingredientId\":1,\"ingredientName\":\"番茄\",\"amountText\":\"2 个\"},"
                    + "{\"ingredientId\":2,\"ingredientName\":\"姜\",\"amountText\":\"适量\"}]");
            d.setSteps("[{\"seq\":1,\"text\":\"焯水\"}]");
            Page<DishDraft> page = new Page<>(1, 10);
            page.setRecords(List.of(d));
            page.setTotal(1);
            when(mapper.selectPage(any(Page.class), any())).thenReturn(page);

            var result = svc.list(1, 10);
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getIngredientCount()).isEqualTo(2);
            assertThat(result.getRecords().get(0).getStepCount()).isEqualTo(1);
        }
    }

    @Test
    void delete_otherUsersDraft_rejected() {
        try (MockedStatic<StpUtil> stp = mockUser(7L)) {
            DishDraft d = new DishDraft();
            d.setId(5L);
            d.setUserId(9L);
            when(mapper.selectById(5L)).thenReturn(d);
            assertThatThrownBy(() -> svc.delete(5L))
                    .isInstanceOf(BizException.class);
        }
    }
}
