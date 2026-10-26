package com.gudu.xsd.modules.privacy.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.privacy.PersonalDataSection;
import com.gudu.xsd.common.privacy.PersonalDataSubject;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import com.gudu.xsd.modules.privacy.application.dto.ExportLinkView;
import com.gudu.xsd.modules.privacy.domain.repository.ExportGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出我的数据（mini-release-compliance C.2，隐私政策 §七.4「获取副本」）。
 *
 * <ul>
 *   <li>内容：各上下文的 {@link PersonalDataSection}（账号、所属家庭与本人档案、本人创建的菜谱、做菜记录、饮食日志、点评、
 *       AI 对话、反馈、同意记录），不含其他家庭成员的敏感信息；</li>
 *   <li>限频：每账号每天 {@value #DAILY_LIMIT} 次（直接导出与生成下载链接都计数；凭链接下载不再计数）；</li>
 *   <li>交付：小程序里不便保存文件 → {@link #createLink} 签发 {@link #LINK_TTL} 有效的下载凭证，
 *       前端拼成完整地址供用户复制到浏览器打开下载 JSON 文件；服务端不落盘。</li>
 * </ul>
 * 跨用户所属的全部家庭：以系统模式执行，各 section 自带本人归属条件。
 */
@Slf4j
@Service
public class PersonalDataExportAppService {

    public static final int DAILY_LIMIT = 3;
    public static final Duration LINK_TTL = Duration.ofMinutes(30);
    public static final String DOWNLOAD_PATH = "/account/export/download";
    static final int FORMAT_VERSION = 1;

    private final List<PersonalDataSection> sections;
    private final FamilyQueryApi familyQuery;
    private final ExportGuard guard;
    private final Clock clock;

    public PersonalDataExportAppService(List<PersonalDataSection> sections, FamilyQueryApi familyQuery,
                                        ExportGuard guard, Clock clock) {
        this.sections = sections.stream().sorted(Comparator.comparingInt(PersonalDataSection::order)).toList();
        this.familyQuery = familyQuery;
        this.guard = guard;
        this.clock = clock;
    }

    /** 直接导出（计入每日次数）。 */
    public Map<String, Object> export(Long accountId) {
        checkQuota(requireAccount(accountId));
        return build(accountId);
    }

    /** 生成临时下载链接（计入每日次数）。 */
    public ExportLinkView createLink(Long accountId) {
        checkQuota(requireAccount(accountId));
        String token = guard.issueToken(accountId, LINK_TTL);
        return new ExportLinkView(DOWNLOAD_PATH + "?token=" + token, LocalDateTime.now(clock).plus(LINK_TTL));
    }

    /** 凭下载凭证导出（免登录，凭证即授权；不再计数）。 */
    public Map<String, Object> exportByToken(String token) {
        Long accountId = guard.resolveToken(token).orElseThrow(() -> BizException.notFound("下载链接无效或已过期，请重新生成"));
        return build(accountId);
    }

    Map<String, Object> build(Long accountId) {
        return TenantContextHolder.callAsSystem(() -> {
            PersonalDataSubject subject = new PersonalDataSubject(accountId, familyQuery.profileIdsOf(accountId));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("formatVersion", FORMAT_VERSION);
            out.put("exportedAt", LocalDateTime.now(clock));
            out.put("accountId", accountId);
            out.put("note", "咕嘟小食单个人数据副本：仅含您本人的信息；家庭中其他成员的档案与健康信息不在导出范围内。");
            for (PersonalDataSection s : sections) {
                out.put(s.key(), s.export(subject));
            }
            log.info("账号 {} 导出个人数据：{} 节", accountId, sections.size());
            return out;
        });
    }

    private void checkQuota(Long accountId) {
        if (guard.incrementToday(accountId) > DAILY_LIMIT) {
            throw BizException.tooManyRequests("每天最多导出 " + DAILY_LIMIT + " 次，请明天再试");
        }
    }

    private static Long requireAccount(Long accountId) {
        if (accountId == null) throw new BizException(401, "未登录");
        return accountId;
    }
}
