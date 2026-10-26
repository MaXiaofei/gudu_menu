package com.gudu.xsd.modules.contentsecurity.domain.model;

/**
 * 微信消息推送里的 wxa_media_check 异步检测结果。
 *
 * @param appid   推送所属小程序 appid（与本服务配置不一致的推送丢弃）
 * @param traceId 提交时返回的 trace_id
 * @param suggest 综合建议
 * @param label   命中标签
 * @param errcode 检测本身的错误码（非 0 表示微信未能完成检测，例如拉图失败）
 */
public record MediaCheckResult(String appid, String traceId, Suggest suggest, int label, int errcode) {
}
