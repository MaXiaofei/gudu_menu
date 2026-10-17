package com.gudu.xsd.modules.community.application.ai;

import com.gudu.xsd.modules.ai.gateway.application.ModelGateway;
import com.gudu.xsd.modules.community.domain.service.PostTextWriter;
import com.gudu.xsd.modules.community.domain.service.TemplatePostTextWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 「AI 帮我写」：经模型网关 CHAT 生成发帖文案（三种 style），≤200 字；网关失败、输出为空时退回模板。
 * 素材只含菜名 / 用时 / 做菜时间 / 难度，不含家庭成员信息；提示词也要求不写具体人名。
 */
@Slf4j
@Component
@Primary
public class LlmPostTextWriter implements PostTextWriter {

    static final String SCENE = "community_text";
    static final int MAX_LEN = 200;
    static final String SYSTEM = "你是美食社区的文案助手，帮用户写晒菜动态。要求：中文，真诚口语，不夸张不营销；"
            + "不写具体人名（家人只用「家里人」「宝宝」等称呼）；不写联系方式、链接、价格；不用话题标签和 emoji 堆砌；"
            + "只输出正文，不要标题、引号或解释。";

    private final ModelGateway gateway;
    private final TemplatePostTextWriter template = new TemplatePostTextWriter();

    public LlmPostTextWriter(ModelGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String write(Material m, String style) {
        try {
            String out = clean(gateway.chatText(SCENE, SYSTEM, userPrompt(m, style)));
            if (!out.isEmpty()) return out;
        } catch (RuntimeException e) {
            log.info("AI 文案不可用，使用模板: {}", e.getMessage());
        }
        return template.write(m, style);
    }

    static String userPrompt(Material m, String style) {
        StringBuilder sb = new StringBuilder("帮我写一段晒菜文案。\n");
        sb.append("菜名：").append(m == null || m.dishName() == null ? "家常菜" : m.dishName()).append('\n');
        if (m != null && m.minutes() != null && m.minutes() > 0) sb.append("用时：").append(m.minutes()).append(" 分钟\n");
        if (m != null && m.cookedWhen() != null && !m.cookedWhen().isBlank()) sb.append("做菜时间：").append(m.cookedWhen()).append('\n');
        if (m != null && m.difficulty() != null && !m.difficulty().isBlank()) sb.append("难度：").append(m.difficulty()).append('\n');
        sb.append(switch (style == null ? "default" : style) {
            case "short" -> "风格：简短，一两句话，40 字以内。";
            case "casual" -> "风格：更口语、轻松俏皮，80 字以内。";
            default -> "风格：自然分享做法心得和小技巧，120 字以内。";
        });
        return sb.toString();
    }

    /** 去掉引号 / Markdown 标记 / 首尾空白，超长按句号截断到 200 字内。 */
    static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replaceAll("^[\"“「]+|[\"”」]+$", "").replaceAll("[*#`]", "").trim();
        if (s.length() <= MAX_LEN) return s;
        String cut = s.substring(0, MAX_LEN);
        int stop = Math.max(cut.lastIndexOf('。'), Math.max(cut.lastIndexOf('！'), cut.lastIndexOf('～')));
        return stop > MAX_LEN / 2 ? cut.substring(0, stop + 1) : cut;
    }
}
