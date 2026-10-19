package com.gudu.xsd.modules.agent.domain.model;

/**
 * 一次工具调用的记录（消息里展示「查了余量 · 29 样」，评测里比对名称与参数）。
 *
 * @param argsJson 模型给的原始参数 JSON
 */
public record ToolCallRecord(String name, String label, String argsJson, String summary, long ms, boolean ok,
                             boolean write) {
}
