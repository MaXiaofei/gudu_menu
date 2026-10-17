package com.gudu.xsd.modules.agent.domain.port;

/**
 * 语音转文字。DeepSeek 没有语音识别能力，ASR 供应商待选型（模型网关将新增 ASR 能力）；
 * 当前只有 Mock 实现，返回固定转写。
 */
public interface SpeechRecognizer {

    /** @param audioUrl /file/upload 返回的语音地址 */
    String transcribe(String audioUrl);

    /** 是否为真实识别（false = Mock）。 */
    boolean real();
}
