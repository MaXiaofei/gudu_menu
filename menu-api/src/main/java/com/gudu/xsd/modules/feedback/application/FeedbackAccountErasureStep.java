package com.gudu.xsd.modules.feedback.application;

import com.gudu.xsd.common.privacy.UploadedFileEraser;
import com.gudu.xsd.modules.feedback.domain.repository.FeedbackRepository;
import com.gudu.xsd.modules.identity.application.AccountErasureStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 账号注销后续匿名化（mini-release-compliance F.2）：意见反馈按账号归属、不随家庭保留——
 * 先删除截图文件，再清除联系方式 / 设备信息 / 截图引用并与账号解绑（反馈正文保留用于产品改进，已无法关联到个人）。
 * 幂等；文件删除失败时反馈仍绑定账号，后台重试可再次处理。
 */
@Component
@RequiredArgsConstructor
public class FeedbackAccountErasureStep implements AccountErasureStep {

    private final FeedbackRepository repository;
    private final UploadedFileEraser files;

    @Override
    public String name() {
        return "意见反馈联系方式与截图清除";
    }

    @Override
    public String erase(Long accountId) {
        int deleted = 0;
        for (String url : repository.imageUrlsOfAccount(accountId)) deleted += files.erase(url);
        int rows = repository.anonymizeAccount(accountId);
        return "反馈 " + rows + " 条已去标识并与账号解绑，截图文件删除 " + deleted + " 个";
    }
}
