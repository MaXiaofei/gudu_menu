package com.gudu.xsd.modules.community.domain.service;

/** 模板文案（无模型依赖，确定性输出，供降级与单测使用）。 */
public final class TemplatePostTextWriter implements PostTextWriter {

    @Override
    public String write(Material m, String style) {
        String dish = m == null || m.dishName() == null || m.dishName().isBlank() ? "这道家常菜" : m.dishName().trim();
        String when = m == null || m.cookedWhen() == null || m.cookedWhen().isBlank() ? "今天" : m.cookedWhen().trim();
        String mins = m != null && m.minutes() != null && m.minutes() > 0 ? m.minutes() + " 分钟" : null;
        String diff = m == null || m.difficulty() == null || m.difficulty().isBlank() ? null : m.difficulty().trim();
        String s = style == null ? "default" : style;
        return switch (s) {
            case "short" -> dish + "，" + (mins == null ? "简单好做" : mins + "搞定") + "，全家都爱吃。";
            case "casual" -> when + "做了" + dish + "，" + (mins == null ? "没想到这么快" : "前后就 " + mins)
                    + "，端上桌一会儿就光盘了哈哈～下次还做！";
            default -> when + "做了" + dish + "。" + (mins == null ? "" : "从备菜到出锅大约 " + mins + "，")
                    + (diff == null ? "" : "难度" + diff + "，") + "火候是关键，出锅前尝一下再调味。"
                    + "分享给同样想吃得清淡又省事的朋友。";
        };
    }
}
