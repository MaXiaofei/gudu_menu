package com.gudu.xsd.modules.privacy.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.common.R;
import com.gudu.xsd.modules.privacy.application.PersonalDataExportAppService;
import com.gudu.xsd.modules.privacy.application.dto.ExportLinkView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

/**
 * 导出我的数据（C.2）。
 * <ul>
 *   <li>{@code GET /account/me/export}：当前账号的个人数据 JSON（R 包装，App / 网页直接保存）；</li>
 *   <li>{@code POST /account/me/export/link}：生成 30 分钟有效的下载地址（小程序复制链接到浏览器打开）；</li>
 *   <li>{@code GET /account/export/download?token=}：凭链接下载 JSON 文件（免登录，SaTokenConfig 放行；凭证即授权）。</li>
 * </ul>
 * 前两者合计每账号每天 3 次（超限 code=429）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "导出我的数据")
public class PersonalDataExportController {

    private final PersonalDataExportAppService exportApp;

    @Operation(summary = "导出我的个人数据（JSON，每天 3 次）")
    @GetMapping("/account/me/export")
    public R<Map<String, Object>> export() {
        return R.ok(exportApp.export(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "生成个人数据临时下载链接（30 分钟有效，计入每天 3 次）")
    @PostMapping("/account/me/export/link")
    public R<ExportLinkView> link() {
        return R.ok(exportApp.createLink(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "凭临时链接下载个人数据 JSON 文件")
    @GetMapping(PersonalDataExportAppService.DOWNLOAD_PATH)
    public R<Map<String, Object>> download(@RequestParam("token") String token, HttpServletResponse response) {
        Map<String, Object> body = exportApp.exportByToken(token);
        // 浏览器打开即下载为文件（内容仍是统一响应 R，个人数据在 data 字段）
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("gudu-my-data-" + LocalDate.now() + ".json", StandardCharsets.UTF_8).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return R.ok(body);
    }
}
