package com.gudu.xsd.modules.contentsecurity.application;

import com.gudu.xsd.common.privacy.UploadedFileEraser;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.contentsecurity.domain.repository.MediaCheckRepository;
import com.gudu.xsd.modules.identity.application.AccountErasureStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 账号注销后续匿名化（隐私政策 §七.6）：删除本人上传过的头像文件（原图、缩略图及违规隔离副本）。
 * 账号表里的头像地址已在注销同事务中清除，这里按图片检测记录（用途=头像、上传人=该账号）找回文件地址。
 * 幂等：文件已不存在视为成功；检测记录保留（只含地址与结论，不含图片内容）。
 */
@Component
@RequiredArgsConstructor
public class AvatarFileErasureStep implements AccountErasureStep {

    private final MediaCheckRepository mediaChecks;
    private final UploadedFileEraser files;

    @Override
    public String name() {
        return "头像文件删除";
    }

    @Override
    public String erase(Long accountId) {
        List<String> urls = mediaChecks.urlsByAccount(accountId, ContentPurpose.AVATAR);
        int deleted = 0;
        for (String url : urls) deleted += files.erase(url);
        return "头像 " + urls.size() + " 张，删除文件 " + deleted + " 个";
    }
}
