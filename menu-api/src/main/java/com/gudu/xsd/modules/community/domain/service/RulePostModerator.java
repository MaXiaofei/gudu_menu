package com.gudu.xsd.modules.community.domain.service;

import com.gudu.xsd.common.domain.DomainException;
import com.gudu.xsd.modules.community.domain.model.ModerationVerdict;
import com.gudu.xsd.modules.community.domain.model.RiskLevel;
import com.gudu.xsd.modules.community.domain.model.RiskScore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 规则预审（纯 JDK）：
 * <ul>
 *   <li>{@link #assertPublishable} 发布时同步执行：命中禁发词直接拒绝；</li>
 *   <li>{@link #review} 分级：联系方式（手机号 / 微信号 / QQ / 外部链接）或 ≥2 个广告词 → HIGH；
 *       1 个广告词 → MEDIUM；否则 LOW。</li>
 * </ul>
 */
public final class RulePostModerator implements PostModerator {

    /** 禁发词：命中即拒绝发布（示例词表，生产应接入运营可维护的词库）。 */
    static final List<String> BLOCKED = List.of("赌博", "博彩", "毒品", "代开发票", "枪支");

    /** 广告/导流词。 */
    static final List<String> AD_WORDS = List.of("加微信", "加v", "加我v", "私聊", "代购", "优惠券", "返现", "低价出",
            "团购价", "下单链接", "进群", "淘口令", "扫码领");

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern WECHAT = Pattern.compile("(微信|vx|v信|wx|威信|加我?v)[:：\\s]*[A-Za-z][-_A-Za-z0-9]{5,19}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QQ = Pattern.compile("(qq|扣扣)[:：\\s]*\\d{5,11}", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK = Pattern.compile("(https?://|www\\.)\\S+", Pattern.CASE_INSENSITIVE);

    @Override
    public void assertPublishable(String title, String text) {
        String all = join(title, text);
        for (String w : BLOCKED) {
            if (all.contains(w)) throw new DomainException("内容包含不允许发布的词，请修改后再发");
        }
    }

    @Override
    public ModerationVerdict review(String title, String text, List<String> images) {
        String all = join(title, text);
        String lower = all.toLowerCase(Locale.ROOT);
        List<String> reasons = new ArrayList<>();
        if (PHONE.matcher(all).find()) reasons.add("疑似手机号");
        if (WECHAT.matcher(all).find()) reasons.add("疑似微信号");
        if (QQ.matcher(all).find()) reasons.add("疑似 QQ 号");
        if (LINK.matcher(all).find()) reasons.add("包含外部链接");
        boolean contact = !reasons.isEmpty();
        int ads = 0;
        for (String w : AD_WORDS) {
            if (lower.contains(w.toLowerCase(Locale.ROOT))) {
                ads++;
                reasons.add("广告词「" + w + "」");
            }
        }
        RiskLevel risk = contact || ads >= 2 ? RiskLevel.HIGH : ads == 1 ? RiskLevel.MEDIUM : RiskLevel.LOW;
        List<RiskScore> scores = List.of(
                new RiskScore("引流广告", ads == 0 ? 0.05 : Math.min(1.0, 0.1 + 0.4 * ads)),
                new RiskScore("联系方式", contact ? 0.95 : 0.03),
                new RiskScore("违禁内容", 0.02));
        return new ModerationVerdict(risk, reasons, scores);
    }

    private static String join(String title, String text) {
        return (title == null ? "" : title) + "\n" + (text == null ? "" : text);
    }
}
