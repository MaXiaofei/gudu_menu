package com.gudu.xsd.modules.feedback.domain.repository;

import com.gudu.xsd.modules.feedback.domain.model.Feedback;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackPage;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackStatus;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FeedbackRepository {

    void save(Feedback feedback);

    Optional<Feedback> findById(Long id);

    long countByMemberSince(Long memberId, LocalDateTime since);

    /** status 为 null 不过滤；按创建时间倒序。 */
    FeedbackPage page(FeedbackStatus status, int pageNum, int pageSize);

    /** 账号合并（AccountMerged）：把归属 {@code from} 账号的记录改指 {@code to}。返回改动条数。 */
    int reassignAccount(Long from, Long to);

    /** 账号注销（mini-release-compliance F.2）：清除该账号反馈里的联系方式与设备信息（幂等）。返回改动条数。 */
    int anonymizeAccount(Long accountId);
    /** 保存期限清理（C.1）：物理删除已处理（DONE）且最后更新早于 cutoff 的一批反馈，返回删除条数。 */
    int deleteDoneBefore(LocalDateTime cutoff, int limit);

    /** 某账号提交的全部反馈（导出我的数据，按时间正序）。 */
    java.util.List<Feedback> listByMember(Long memberId);
}
