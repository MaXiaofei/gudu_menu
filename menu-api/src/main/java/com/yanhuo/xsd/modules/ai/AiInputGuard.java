package com.yanhuo.xsd.modules.ai;

import com.yanhuo.xsd.common.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 输入预检护栏：在调用外部 AI 前，对用户输入文本（食材名、菜品描述等）做合法性校验。
 *
 * <p>四道关卡（按顺序）：
 * <ol>
 *   <li>空/纯空白 → 拒绝；</li>
 *   <li>长度 &gt; {@value #MAX_LEN} → 拒绝（输入过长）；</li>
 *   <li>命中黑名单关键词（暴力/色情/违法/编程/写作/闲聊/政治等）→ 拒绝（只能回答食物营养）；</li>
 * </ol>
 *
 * <p>黑名单以「明显越界词」为主，避免误杀食物名（如「黄油」「煮蛋」不被误判）。
 * 故意不用单字「黄」「肉」这类会误伤食物的 token。
 */
@Component
public class AiInputGuard {

    /** 用户输入文本上限（食材名 / 菜品描述）。 */
    public static final int MAX_LEN = 200;

    /**
     * 黑名单关键词（小写匹配）。覆盖：暴力/武器、色情、违法、编程、写作、闲聊、政治、隐私。
     * 刻意避免收录会误伤食物的单字（如「黄」「肉」「油」），只收录复合词或明显越界词。
     */
    private static final List<String> BLACKLIST = List.of(
            // 暴力 / 武器
            "炸弹", "武器", "弹药", "枪支", "枪械", "杀伤", "杀人", "袭击",
            "攻击系统", "攻击网站", "入侵系统", "爆炸",
            // 色情
            "色情", "涉黄", "淫秽", "裸体", "av女优", "成人视频",
            // 违法 / 犯罪
            "赌博", "毒品", "吸毒", "贩毒", "黑客", "诈骗", "洗钱", "走私",
            "造假币", "假钞",
            // 编程 / 技术（非食物营养）
            "写代码", "写程序", "写脚本", "编程", "代码实现", "sql注入",
            // 写作 / 创作
            "写诗", "作诗", "写文章", "写一篇文章", "写作文", "写小说", "写故事",
            "笑话",
            // 闲聊
            "陪我聊天", "陪我聊", "你是谁", "你是机器人", "你叫什么", "闲聊",
            // 政治
            "政治", "政府", "国家领导人", "选举",
            // 隐私
            "身份证号", "银行卡号", "密码", "手机号"
    );

    /**
     * 食物相关性白名单（宽松匹配）：输入至少含一个食物/营养相关词才算在范围内。
     * 用于兜底黑名单漏网的无关请求（如「写一首诗」「讲个故事」这类不含黑名单连续词、但也无关食物的）。
     * 刻意用宽泛词，降低误杀（常见食材字、烹饪动作、营养指标名都算）。
     */
    private static final List<String> FOOD_WHITELIST = List.of(
            // 食材/品类字
            "菜", "肉", "蛋", "奶", "乳", "米", "面", "粉", "果", "蔬", "汤", "鱼", "虾", "蟹",
            "豆", "菇", "菌", "瓜", "椒", "葱", "姜", "蒜", "油", "盐", "糖", "醋", "酱", "茶", "酒",
            "粮", "麦", "谷", "粥", "饭", "饼", "包", "饺", "糕", "坚果", "海带", "紫菜",
            // 烹饪/饮食动作
            "吃", "喝", "炒", "煮", "蒸", "炖", "烤", "煎", "炸", "拌", "腌", "卤", "焖", "煲",
            "做菜", "做法", "菜谱", "食谱", "烹饪",
            // 营养/菜单维度
            "营养", "热量", "卡路里", "蛋白", "脂肪", "碳水", "糖", "钙", "铁", "锌", "维生素",
            "菜单", "食材", "份量", "克", "斤", "卡", "饱", "饿",
            // 常见菜名片段
            "番茄", "西红柿", "土豆", "黄瓜", "白菜", "排骨", "鸡", "鸭", "牛", "羊", "猪"
    );

    /** 校验用户输入文本。无效抛 {@link BizException}。 */
    public void validate(String text) {
        if (text == null || text.isBlank()) {
            throw new BizException("输入不能为空");
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_LEN) {
            throw new BizException("输入过长或不合法（上限 " + MAX_LEN + " 字）");
        }
        String lower = trimmed.toLowerCase();
        for (String kw : BLACKLIST) {
            if (lower.contains(kw)) {
                throw new BizException("我只能回答食物和营养相关的问题。");
            }
        }
        // 食物相关性兜底：不含任何食物/营养词 → 判定无关，拒绝（不浪费 AI 调用）。
        boolean foodRelated = false;
        for (String kw : FOOD_WHITELIST) {
            if (lower.contains(kw)) { foodRelated = true; break; }
        }
        if (!foodRelated) {
            throw new BizException("我只能回答食物和营养相关的问题。");
        }
    }
}
