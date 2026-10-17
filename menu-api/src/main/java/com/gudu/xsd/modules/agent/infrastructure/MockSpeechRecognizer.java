package com.gudu.xsd.modules.agent.infrastructure;

import com.gudu.xsd.modules.agent.domain.port.SpeechRecognizer;
import org.springframework.stereotype.Component;

/** ASR 占位：返回固定转写（原型 AgentVoice 的示例语句），待 ASR 供应商选型后替换。 */
@Component
public class MockSpeechRecognizer implements SpeechRecognizer {

    public static final String FIXED = "麻婆豆腐做完了，我们三个人吃的";

    @Override
    public String transcribe(String audioUrl) {
        return FIXED;
    }

    @Override
    public boolean real() {
        return false;
    }
}
