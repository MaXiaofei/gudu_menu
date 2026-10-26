package com.gudu.xsd.modules.feedback.domain.repository;

import com.gudu.xsd.modules.feedback.domain.model.Feedback;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackPage;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackStatus;
import com.gudu.xsd.modules.feedback.domain.model.FeedbackType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository {

    void save(Feedback feedback);

    Optional<Feedback> findById(Long id);

    long countByMemberSince(Long memberId, LocalDateTime since);

    /** type / status 为 null 不过滤；按创建时间倒序。 */
    FeedbackPage page(FeedbackType type, FeedbackStatus status, int pageNum, int pageSize);

    /** 某账号自己提交的反馈，按创建时间倒序。 */
    FeedbackPage pageByMember(Long memberId, int pageNum, int pageSize);

    /** 账号合并（AccountMerged）：把归属 {@code from} 账号的记录改指 {@code to}。返回改动条数。 */
    int reassignAccount(Long from, Long to);

    /** 账号注销：该账号反馈里的全部截图地址（先删文件再去标识，文件删除失败时记录仍绑定账号可重试）。 */
    List<String> imageUrlsOfAccount(Long accountId);

    /**
     * 账号注销（mini-release-compliance F.2）：清除该账号反馈里的联系方式、设备信息与截图，
     * 并把反馈与账号解绑（member_id 置为 {@link #ANONYMOUS_ACCOUNT}，正文保留用于产品改进）。幂等。返回改动条数。
     */
    int anonymizeAccount(Long accountId);

    /** 已与账号解绑的反馈使用的占位账号 id。 */
    long ANONYMOUS_ACCOUNT = 0L;
    /** 保存期限清理（C.1）：物理删除已处理（DONE）且最后更新早于 cutoff 的一批反馈，返回删除条数。 */
    int deleteDoneBefore(LocalDateTime cutoff, int limit);

    /** 某账号提交的全部反馈（导出我的数据，按时间正序）。 */
    java.util.List<Feedback> listByMember(Long memberId);
}
