package com.gudu.xsd.modules.agent.application.tools;

import com.gudu.xsd.modules.agent.domain.port.Kitchen;

/**
 * 工具执行上下文。
 *
 * @param userText 本轮用户原话（服务端据此强制合并被点名成员的画像约束）
 * @param ledger   本会话的动作台账（撤销 / 取消待确认卡片）
 */
public record ToolContext(Kitchen kitchen, Long memberId, String sessionId, String userText, ActionLedger ledger) {
}
