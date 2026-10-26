package com.gudu.xsd.modules.vision.application;

import com.gudu.xsd.modules.identity.application.AccountErasureStep;
import com.gudu.xsd.modules.vision.domain.model.VisionJob;
import com.gudu.xsd.modules.vision.domain.port.ImageStore;
import com.gudu.xsd.modules.vision.domain.repository.VisionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 账号注销后续匿名化（隐私政策 §七.6）：删除该账号的拍照识别任务——先删仍在的识别原图，再删任务记录。
 * 幂等；图片删除抛错时任务记录保留，后台重试可再次处理。
 */
@Component
@RequiredArgsConstructor
public class VisionAccountErasureStep implements AccountErasureStep {

    private final VisionJobRepository jobs;
    private final ImageStore images;

    @Override
    public String name() {
        return "拍照识别任务删除";
    }

    @Override
    public String erase(Long accountId) {
        List<VisionJob> list = jobs.findByMember(accountId);
        int withImage = 0;
        for (VisionJob j : list) {
            if (j.imageUrl() != null && !j.imageUrl().isBlank()) {
                images.delete(j.imageUrl());
                withImage++;
            }
        }
        return "删除任务 " + jobs.deleteByMember(accountId) + " 个，原图 " + withImage + " 张";
    }
}
