package com.yanhuo.xsd.modules.dish;

import com.yanhuo.xsd.modules.nutrition.Ingredient;
import com.yanhuo.xsd.modules.nutrition.mapper.IngredientMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 离线测试：用固定 HTML 片段 Jsoup.parse（不联网），
 * 通过反射调用各站 parse* 方法 + assemble，验证选择器提取正确。
 */
class RecipeImporterTest {

    private IngredientMapper ingredientMapper;
    private RecipeImporter importer;

    @BeforeEach
    void setUp() {
        ingredientMapper = Mockito.mock(IngredientMapper.class);
        // 默认：所有食材名都不命中（selectOne 返回 null）
        when(ingredientMapper.selectOne(any())).thenReturn(null);
        importer = new RecipeImporter(ingredientMapper);
    }

    // 反射调用 private parse 方法（不依赖网络）
    private Object invokeParse(String methodName, String html) throws Exception {
        Document doc = Jsoup.parse(html, "https://example.com/");
        var m = RecipeImporter.class.getDeclaredMethod(methodName, Document.class);
        m.setAccessible(true);
        return m.invoke(importer, doc);
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Object> stepsRaw(Object parsed) throws Exception {
        var f = parsed.getClass().getDeclaredField("steps");
        f.setAccessible(true);
        return (java.util.List<Object>) f.get(parsed);
    }

    /** 步骤视图：从内部 ParsedStep 反射读 text/imageUrl。 */
    private java.util.List<ParsedStepView> steps(Object parsed) throws Exception {
        return stepsRaw(parsed).stream().map(o -> {
            ParsedStepView v = new ParsedStepView();
            try {
                var ft = o.getClass().getDeclaredField("text");
                ft.setAccessible(true);
                v.text = (String) ft.get(o);
                var fi = o.getClass().getDeclaredField("imageUrl");
                fi.setAccessible(true);
                v.imageUrl = (String) fi.get(o);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return v;
        }).toList();
    }

    private String name(Object parsed) throws Exception {
        var f = parsed.getClass().getDeclaredField("name");
        f.setAccessible(true);
        return (String) f.get(parsed);
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Object> ingredients(Object parsed) throws Exception {
        var f = parsed.getClass().getDeclaredField("ingredients");
        f.setAccessible(true);
        return (java.util.List<Object>) f.get(parsed);
    }

    private String ingName(Object ing) throws Exception {
        var f = ing.getClass().getDeclaredField("name");
        f.setAccessible(true);
        return (String) f.get(ing);
    }

    private BigDecimal ingAmount(Object ing) throws Exception {
        var f = ing.getClass().getDeclaredField("amount");
        f.setAccessible(true);
        return (BigDecimal) f.get(ing);
    }

    /** 下厨房：菜名 + 食材 + 步骤 + 步骤图。 */
    @Test
    void 下厨房_解析菜名食材步骤() throws Exception {
        String html = """
            <html><body>
              <h1 class="recipe-show-name">番茄炒蛋</h1>
              <img class="recipe-show-img" src="/cover.jpg"/>
              <div class="ingredients">
                <li>番茄 300g</li>
                <li>鸡蛋 3个</li>
                <li>盐 适量</li>
              </div>
              <div class="steps">
                <div class="step"><div class="text">番茄切块</div><img src="/s1.jpg"/></div>
                <div class="step"><div class="text">鸡蛋打散炒熟</div><img src="/s2.jpg"/></div>
              </div>
            </body></html>
            """;
        Object parsed = invokeParse("parseXiachufang", html);

        assertThat(name(parsed)).isEqualTo("番茄炒蛋");
        var ings = ingredients(parsed);
        assertThat(ingName(ings.get(0))).isEqualTo("番茄");
        assertThat(ingAmount(ings.get(0))).isEqualByComparingTo("300");
        var st = steps(parsed);
        assertThat(st).hasSize(2);
        assertThat(st.get(0).text).isEqualTo("番茄切块");
        assertThat(st.get(0).imageUrl).contains("/s1.jpg");
    }

    /** 美食杰结构。 */
    @Test
    void 美食杰_解析菜名食材步骤() throws Exception {
        String html = """
            <html><body>
              <h1 class="recipe-title">红烧肉</h1>
              <div class="recipe_ingredients">
                <div class="right"><ul><li>五花肉 500g</li><li>酱油 2勺</li></ul></div>
              </div>
              <div class="recipe_step">焯水去腥</div>
              <div class="recipe_step">加冰糖炒色</div>
            </body></html>
            """;
        Object parsed = invokeParse("parseMeishij", html);

        assertThat(name(parsed)).isEqualTo("红烧肉");
        var ings = ingredients(parsed);
        assertThat(ingName(ings.get(0))).isEqualTo("五花肉");
        assertThat(ingAmount(ings.get(0))).isEqualByComparingTo("500");
        assertThat(steps(parsed)).hasSize(2);
    }

    /** 豆果美食结构。 */
    @Test
    void 豆果_解析菜名步骤() throws Exception {
        String html = """
            <html><body>
              <h1 class="title">宫保鸡丁</h1>
              <div class="recipe">
                <div class="ingredients"><li>鸡胸肉 200g</li><li>花生米 50g</li></div>
              </div>
              <div class="step">鸡肉切丁</div>
              <div class="step">爆香干辣椒</div>
              <div class="step">下鸡丁翻炒</div>
            </body></html>
            """;
        Object parsed = invokeParse("parseDouguo", html);

        assertThat(name(parsed)).isEqualTo("宫保鸡丁");
        assertThat(steps(parsed)).hasSize(3);
    }

    /** 兜底站：title 作菜名，[class*=ingredient] 提食材。 */
    @Test
    void 兜底站_title作菜名_class匹配食材步骤() throws Exception {
        String html = """
            <html><head><title>清蒸鲈鱼_美食天下</title></head><body>
              <div class="ingredient-list"><li>鲈鱼 1只</li></div>
              <div class="step-container">鱼处理干净</div>
              <div class="step-container">上锅蒸 8 分钟</div>
            </body></html>
            """;
        Object parsed = invokeParse("parseGeneric", html);

        // h1 没有时退化到 title（取 _ 前半段）
        assertThat(name(parsed)).isEqualTo("清蒸鲈鱼");
        assertThat(ingredients(parsed)).hasSize(1);
        assertThat(steps(parsed)).isNotEmpty();
    }

    /** 用量解析：「盐 适量」无数字 → amount=null；「糖 10g」→ 10。 */
    @Test
    void 食材用量_适量无数字_g正常解析() throws Exception {
        String html = """
            <html><body>
              <h1 class="recipe-show-name">测试</h1>
              <div class="ingredients"><li>盐 适量</li><li>糖 10g</li></div>
            </body></html>
            """;
        Object parsed = invokeParse("parseXiachufang", html);
        var ings = ingredients(parsed);
        assertThat(ingName(ings.get(0))).isEqualTo("盐");
        assertThat(ingAmount(ings.get(0))).isNull();
        assertThat(ingAmount(ings.get(1))).isEqualByComparingTo("10");
    }

    /** 组装：未命中食材写入 note；命中的关联 ingredientId。 */
    @Test
    void assemble_命中关联_未命中写入note() throws Exception {
        Ingredient tomato = new Ingredient();
        tomato.setId(7L);
        tomato.setName("番茄");
        // 第一次精确查询（name=番茄）返回命中
        when(ingredientMapper.selectOne(ArgumentMatchers.any())).thenReturn(tomato);

        String html = """
            <html><body>
              <h1 class="recipe-show-name">番茄炒蛋</h1>
              <div class="ingredients"><li>番茄 300g</li><li>魔幻食材X 5g</li></div>
              <div class="steps"><div class="step">炒</div></div>
            </body></html>
            """;
        Object parsed = invokeParse("parseXiachufang", html);

        // 反射调用 private assemble
        var assembleM = RecipeImporter.class.getDeclaredMethod("assemble",
                parsed.getClass(), String.class);
        assembleM.setAccessible(true);
        DishSaveDTO dto = (DishSaveDTO) assembleM.invoke(importer, parsed, "https://xiachufang.com/recipe/1");

        assertThat(dto.getDish().getName()).isEqualTo("番茄炒蛋");
        assertThat(dto.getIngredients()).hasSize(2); // selectOne 桩每次返回 tomato，两项都命中
        assertThat(dto.getIngredients().get(0).getIngredientId()).isEqualTo(7L);
        assertThat(dto.getIngredients().get(0).getAmount()).isEqualByComparingTo("300");
        assertThat(dto.getSteps()).hasSize(1);
        assertThat(dto.getDish().getNote()).contains("URL 导入");
    }

    /** 步骤图片 absUrl 转绝对地址。 */
    static class ParsedStepView {
        String text;
        String imageUrl;
    }
}
