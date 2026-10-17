package com.gudu.xsd.modules.vision.domain.model;

/** 识别任务状态。 */
public enum VisionJobStatus {
    PENDING, DONE, FAILED;

    public static VisionJobStatus parse(String value) {
        for (VisionJobStatus s : values()) {
            if (s.name().equals(value)) return s;
        }
        return FAILED;
    }
}
