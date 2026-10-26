package com.gudu.xsd.modules.privacy.application;

import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.ConsentRequiredException;
import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.privacy.application.dto.ConsentStatusView;
import com.gudu.xsd.modules.privacy.domain.event.SensitiveConsentWithdrawn;
import com.gudu.xsd.modules.privacy.domain.model.ConsentDocType;
import com.gudu.xsd.modules.privacy.domain.model.ConsentRecord;
import com.gudu.xsd.modules.privacy.domain.repository.ConsentRepository;
import com.gudu.xsd.modules.privacy.domain.service.SensitiveInfoClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 敏感个人信息单独同意（mini-release-compliance B.2）。
 *
 * <ul>
 *   <li>同意 / 撤回都追加一条 consent_record（不修改历史），当前状态 = 最新一条；</li>
 *   <li>保存成员档案前由 {@link #assertAllowed} 强制校验：缺同意抛 428 {@link ConsentRequiredException}；</li>
 *   <li>撤回 = 记录 granted=0 + 发布 {@link SensitiveConsentWithdrawn}，family 上下文同事务删除对应字段。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ConsentAppService {

    /** 单独同意弹窗文案版本（文案变更时升级；记录在 doc_version）。小程序 api/privacy.ts 同名常量。 */
    public static final String SENSITIVE_DOC_VERSION = "2026-10-26";

    private final ConsentRepository repository;
    private final DomainEventPublisher events;
    private final Clock clock;

    public ConsentStatusView status(Long accountId) {
        Map<String, ConsentStatusView.Item> items = new LinkedHashMap<>();
        for (ConsentDocType t : ConsentDocType.values()) {
            items.put(t.code(), repository.latest(requireAccount(accountId), t)
                    .map(r -> new ConsentStatusView.Item(r.granted(), r.docVersion(), r.createdAt()))
                    .orElse(new ConsentStatusView.Item(false, null, null)));
        }
        return new ConsentStatusView(items);
    }

    public boolean isGranted(Long accountId, ConsentDocType type) {
        return accountId != null && repository.latest(accountId, type).map(ConsentRecord::granted).orElse(false);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConsentStatusView grant(Long accountId, Collection<String> docTypes, String docVersion, String channel,
                                   String clientIp) {
        requireAccount(accountId);
        if (docTypes == null || docTypes.isEmpty()) throw new BizException("请选择同意的事项");
        String version = docVersion == null || docVersion.isBlank() ? SENSITIVE_DOC_VERSION : docVersion.trim();
        LocalDateTime now = LocalDateTime.now(clock);
        for (ConsentDocType t : docTypes.stream().map(ConsentDocType::of).distinct().toList()) {
            repository.append(ConsentRecord.grant(accountId, t, version, channel, clientIp, now));
        }
        return status(accountId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConsentStatusView withdraw(Long accountId, String docType, String channel, String clientIp) {
        requireAccount(accountId);
        ConsentDocType t = ConsentDocType.of(docType);
        String version = repository.latest(accountId, t).map(ConsentRecord::docVersion).orElse(SENSITIVE_DOC_VERSION);
        repository.append(ConsentRecord.withdraw(accountId, t, version, channel, clientIp, LocalDateTime.now(clock)));
        events.publish(new SensitiveConsentWithdrawn(accountId, t));
        return status(accountId);
    }

    /**
     * 保存成员档案前校验：本次要写的健康 / 儿童信息，操作账号必须已单独同意；否则抛 428（data.docTypes = 缺少的类型）。
     */
    public void assertAllowed(Long accountId, Map<String, Object> health, String goal, String activityLevel) {
        Set<ConsentDocType> required = SensitiveInfoClassifier.required(health, goal, activityLevel);
        if (required.isEmpty()) return;
        List<String> missing = required.stream().filter(t -> !isGranted(accountId, t)).map(ConsentDocType::code).toList();
        if (missing.isEmpty()) return;
        throw new ConsentRequiredException(missing.contains(ConsentDocType.CHILD_INFO.code())
                ? "录入不满 14 周岁儿童的信息需要监护人单独同意" : "填写健康档案需要您的单独同意", missing);
    }

    /** 本次写入是否含敏感信息（含则记录提供人账号，供撤回时删除）。 */
    public static boolean containsSensitive(Map<String, Object> health, String goal, String activityLevel) {
        return !SensitiveInfoClassifier.required(health, goal, activityLevel).isEmpty();
    }

    private static Long requireAccount(Long accountId) {
        if (accountId == null) throw new BizException(401, "未登录");
        return accountId;
    }
}
