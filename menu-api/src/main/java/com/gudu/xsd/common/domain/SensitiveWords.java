package com.gudu.xsd.common.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 禁发词表（共享内核，纯 JDK）：厨房圈发布预审（RulePostModerator）与内容安全规则兜底（RuleContentSecurity）共用一份。
 *
 * <p>只收「明显违法违规」的复合词，刻意不收会误伤食物名的单字（如「黄」「肉」）。
 * 这是微信内容安全不可用时（App/网页账号无 openid、接口故障、本地环境关闭）的最低防线，
 * 生产主防线是微信 msgSecCheck；词表后续可改为运营可维护。
 */
public final class SensitiveWords {

    /** 命中即拒绝。 */
    public static final List<String> BLOCKED = List.of(
            // 赌 / 毒 / 枪
            "赌博", "博彩", "六合彩", "网赌", "毒品", "吸毒", "贩毒", "冰毒", "海洛因", "大麻烟",
            "枪支", "枪械", "弹药", "炸药", "雷管",
            // 色情
            "色情", "淫秽", "裸聊", "约炮", "援交", "成人视频",
            // 诈骗 / 黑产
            "代开发票", "办假证", "假证件", "洗钱", "套现", "刷单返利", "裸贷",
            // 暴恐 / 邪教
            "恐怖袭击", "制造炸弹", "法轮功", "邪教");

    private SensitiveWords() {
    }

    /** 第一个命中的禁发词（忽略大小写）；未命中 empty。 */
    public static Optional<String> firstHit(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String w : BLOCKED) {
            if (lower.contains(w)) return Optional.of(w);
        }
        return Optional.empty();
    }
}
