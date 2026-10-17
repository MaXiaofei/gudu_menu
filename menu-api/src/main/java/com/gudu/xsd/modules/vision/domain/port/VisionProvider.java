package com.gudu.xsd.modules.vision.domain.port;

import com.gudu.xsd.modules.vision.domain.model.RecognitionResult;
import com.gudu.xsd.modules.vision.domain.model.VisionMode;

/** 识别能力端口：Mock（默认）与模型网关实现。 */
public interface VisionProvider {

    /**
     * @param imageUrl 本站上传地址（/gudu/uploads/…）
     * @throws RuntimeException 识别失败（任务记为 FAILED）
     */
    RecognitionResult recognize(VisionMode mode, String imageUrl);

    /** 实现标识（mock / gateway），写入日志。 */
    String name();
}
