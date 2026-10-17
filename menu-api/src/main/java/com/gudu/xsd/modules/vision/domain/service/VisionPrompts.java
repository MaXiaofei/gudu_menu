package com.gudu.xsd.modules.vision.domain.service;

import com.gudu.xsd.modules.vision.domain.model.VisionMode;

/**
 * 多模态识别提示词与 JSON Schema（三种模式，可复用常量）。模型网关 VISION 能力按此调用，
 * 返回再由 infrastructure 的健壮解析器处理（缺字段 / 非法 confidence / 中文数量）。
 */
public final class VisionPrompts {

    private VisionPrompts() {
    }

    public static final String COMMON_RULES = """
            你是家庭厨房助手「咕嘟」的图像识别模块。只输出一个 JSON 对象，不要输出解释、Markdown 或代码块。
            名称用中文常用食材名（如「番茄」「鸡蛋」「北豆腐」），不要带品牌和规格；看不清的不要编造。
            confidence 为 0 到 1 的小数，表示你对该项名称判断的把握。
            """;

    public static final String FRIDGE_PROMPT = COMMON_RULES + """
            任务：识别照片中冰箱隔层或台面上的食材，估计每样的数量。
            同一种食材合并为一项；数量写数字，单位用「个/盒/把/根/块/颗/袋/瓶/斤/克」之一。
            不确定的项给出 1–2 个 alternatives（也可能是）。忽略容器、包装盒本身和非食物物品。
            """;

    public static final String FRIDGE_SCHEMA = """
            {"type":"object","required":["items"],"properties":{"items":{"type":"array","items":{"type":"object",
            "required":["name","confidence"],"properties":{"name":{"type":"string"},"amount":{"type":"number"},
            "unit":{"type":"string"},"confidence":{"type":"number","minimum":0,"maximum":1},
            "alternatives":{"type":"array","items":{"type":"string"}}}}}}}
            """;

    public static final String RECEIPT_PROMPT = COMMON_RULES + """
            任务：识别购物小票上的商品行，只保留食材和调味品（去掉购物袋、日用品、折扣行、合计行）。
            name 写通用食材名（「海天生抽 500ml」→「生抽」）；amount 为数量；price 为该行实付金额（元）。
            """;

    public static final String RECEIPT_SCHEMA = """
            {"type":"object","required":["items"],"properties":{"items":{"type":"array","items":{"type":"object",
            "required":["name","confidence"],"properties":{"name":{"type":"string"},"amount":{"type":"number"},
            "unit":{"type":"string"},"price":{"type":"number"},
            "confidence":{"type":"number","minimum":0,"maximum":1}}}}}}
            """;

    public static final String RECIPE_PROMPT = COMMON_RULES + """
            任务：识别菜谱截图，提取菜名、用料和步骤。用料逐行列出（amount 保留原文用量，如「2 个」「适量」）；
            步骤按顺序，每步一句完整中文，去掉序号。另在 items 中列出用料对应的食材（用于对齐食材库）。
            """;

    public static final String RECIPE_SCHEMA = """
            {"type":"object","required":["title","ingredients","steps"],"properties":{"title":{"type":"string"},
            "ingredients":{"type":"array","items":{"type":"object","required":["name"],"properties":{
            "name":{"type":"string"},"amount":{"type":"string"},"unit":{"type":"string"}}}},
            "steps":{"type":"array","items":{"type":"string"}},
            "items":{"type":"array","items":{"type":"object","properties":{"name":{"type":"string"},
            "confidence":{"type":"number"}}}}}}
            """;

    public static String promptOf(VisionMode mode) {
        return switch (mode) {
            case FRIDGE -> FRIDGE_PROMPT;
            case RECEIPT -> RECEIPT_PROMPT;
            case RECIPE -> RECIPE_PROMPT;
        };
    }

    public static String schemaOf(VisionMode mode) {
        return switch (mode) {
            case FRIDGE -> FRIDGE_SCHEMA;
            case RECEIPT -> RECEIPT_SCHEMA;
            case RECIPE -> RECIPE_SCHEMA;
        };
    }
}
