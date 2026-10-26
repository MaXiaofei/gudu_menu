package com.gudu.xsd.modules.consent.application;

import com.gudu.xsd.modules.consent.application.dto.ConsentView;
import com.gudu.xsd.modules.consent.domain.model.ConsentChannel;
import com.gudu.xsd.modules.consent.domain.model.ConsentDocType;
import com.gudu.xsd.modules.consent.domain.model.ConsentRecord;
import com.gudu.xsd.modules.consent.domain.repository.ConsentRecordRepository;
import com.gudu.xsd.modules.identity.domain.event.AccountMerged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 协议同意记录（mini-release-compliance B.1）：登录页勾选后、登录成功即上报当前协议版本；
 * 撤回同样记一行（granted=false），只追加不修改。账号合并时随 AccountMerged 改指主账号（multi-tenant-family E.7）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentAppService {

    /** 后台 / 本人查询的条数上限。 */
    static final int HISTORY_LIMIT = 200;

    private final ConsentRecordRepository repository;
    private final Clock clock;

    /**
     * 记录同意或撤回。
     *
     * @param docTypes 为空时默认用户协议 + 隐私政策（登录页勾选的两份）
     * @return 写入的记录
     */
    @Transactional(rollbackFor = Exception.class)
    public List<ConsentView> record(Long accountId, List<String> docTypes, String docVersion, boolean granted,
                                    String channel, String clientIp) {
        Set<ConsentDocType> types = new LinkedHashSet<>();
        if (docTypes == null || docTypes.isEmpty()) {
            types.add(ConsentDocType.TERMS);
            types.add(ConsentDocType.PRIVACY);
        } else {
            for (String t : docTypes) types.add(ConsentDocType.of(t));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ConsentChannel ch = ConsentChannel.of(channel);
        List<ConsentView> out = new ArrayList<>();
        for (ConsentDocType t : types) {
            ConsentRecord r = ConsentRecord.of(accountId, t, docVersion, granted, ch, clientIp, now);
            repository.save(r);
            out.add(view(r));
        }
        return out;
    }

    /** 某账号的同意 / 撤回历史（倒序）。 */
    public List<ConsentView> history(Long accountId) {
        return repository.listByAccount(accountId, HISTORY_LIMIT).stream().map(ConsentAppService::view).toList();
    }

    /** 账号合并：同意记录随账号改指主账号 P（与发布方同事务）。 */
    @EventListener
    public void onAccountMerged(AccountMerged e) {
        int n = repository.reassignAccount(e.sourceAccountId(), e.targetAccountId());
        if (n > 0) log.info("账号合并：同意记录 {} 条 {} → {}", n, e.sourceAccountId(), e.targetAccountId());
    }

    static ConsentView view(ConsentRecord r) {
        return new ConsentView(r.id(), r.accountId(), r.docType().name(), r.docVersion(), r.granted(),
                r.channel().name(), r.clientIp(), r.createdAt());
    }
}
