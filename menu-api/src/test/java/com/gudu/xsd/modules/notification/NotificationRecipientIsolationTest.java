package com.gudu.xsd.modules.notification;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.gudu.xsd.modules.notification.mapper.NotificationMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通知收件人隔离（V91）：真实 SQL（H2 MySQL 模式 + MyBatis-Plus）验证——
 * 账号 id 与另一人的档案 id <b>数值相同</b>时，两人的通知互不可见（列表 / 未读数 / 已读 / 注销 / 合并）。
 *
 * <p>场景数字：账号 120（A）与档案 120（属于另一个人 B 的就餐档案）撞号；A 自己的档案是 7，B 的账号是 900。
 */
class NotificationRecipientIsolationTest {

    static final long ACC_A = 120L;      // 账号 A 的 id
    static final long PROFILE_A = 7L;    // A 本人的就餐档案
    static final long PROFILE_B = 120L;  // 别人（B）的档案，数值与 A 的账号 id 相同
    static final long ACC_B = 900L;      // B 的账号

    JdbcTemplate jdbc;
    NotificationMapper mapper;
    NotificationService svc;
    InAppChannel inApp;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE notification (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "recipient_account_id BIGINT NULL, recipient_profile_id BIGINT NULL, "
                + "type VARCHAR(32) NOT NULL, channel VARCHAR(32) NOT NULL, title VARCHAR(128), content VARCHAR(1024), "
                + "ref_id VARCHAR(64), is_read TINYINT DEFAULT 0, create_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
        MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
        fb.setDataSource(ds);
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.addMapper(NotificationMapper.class);
        fb.setConfiguration(cfg);
        SqlSessionFactory f = fb.getObject();
        mapper = new SqlSessionTemplate(f).getMapper(NotificationMapper.class);
        inApp = new InAppChannel(mapper);
        svc = new NotificationService(List.of(inApp), mapper);
    }

    private void send(NotificationRecipient to, String type, String title) {
        svc.send(new NotificationPayload(to, type, title, "内容"), "in_app");
    }

    /** 账号 A（120）与档案 B（120）同号：A 只看见发给账号 120 与自己档案 7 的消息，看不到发给别人档案 120 的。 */
    @Test
    void 账号id与别人档案id同号_互不可见() {
        send(NotificationRecipient.account(ACC_A), "feedback_reply", "A 的反馈回复");
        send(NotificationRecipient.profile(PROFILE_A), "expiry", "A 档案的临期提醒");
        send(NotificationRecipient.profile(PROFILE_B), "expiry", "B 档案的临期提醒（档案 id = 120）");
        send(NotificationRecipient.account(ACC_B), "feedback_reply", "B 的反馈回复");

        assertThat(svc.list(ACC_A, PROFILE_A)).extracting(Notification::getTitle)
                .containsExactlyInAnyOrder("A 的反馈回复", "A 档案的临期提醒");
        // B 当前就餐档案 120、账号 900：只看到自己的两条，看不到 A 的账号级反馈回复
        assertThat(svc.list(ACC_B, PROFILE_B)).extracting(Notification::getTitle)
                .containsExactlyInAnyOrder("B 档案的临期提醒（档案 id = 120）", "B 的反馈回复");
        assertThat(svc.unreadCount(ACC_A, PROFILE_A)).isEqualTo(2);
        assertThat(svc.unreadCount(ACC_B, PROFILE_B)).isEqualTo(2);
    }

    /** A 切到别人没有的档案（或没有档案）时，账号级消息仍可见；档案级只看当前档案。 */
    @Test
    void 账号级消息跟着人走_档案级消息跟着档案走() {
        send(NotificationRecipient.account(ACC_A), "feedback_reply", "账号级");
        send(NotificationRecipient.profile(PROFILE_A), "shopping", "档案 7");
        send(NotificationRecipient.profile(8L), "shopping", "档案 8（A 切换到的家人）");

        assertThat(svc.list(ACC_A, 8L)).extracting(Notification::getTitle)
                .containsExactlyInAnyOrder("账号级", "档案 8（A 切换到的家人）");
        assertThat(svc.list(ACC_A, null)).extracting(Notification::getTitle).containsExactly("账号级");
    }

    /** 标记已读：账号 120 不能把别人档案 120 的通知标成已读（旧实现 in(member_id) 会误标）。 */
    @Test
    void 已读只能标自己的_同号的别人通知不受影响() {
        send(NotificationRecipient.profile(PROFILE_B), "expiry", "B 档案的临期提醒");
        Long bId = svc.list(ACC_B, PROFILE_B).get(0).getId();

        svc.markRead(bId, ACC_A, PROFILE_A); // A 试图标 B 的通知（账号 120 == 档案 120 数值相同）

        assertThat(svc.unreadCount(ACC_B, PROFILE_B)).isEqualTo(1);
        svc.markRead(bId, ACC_B, PROFILE_B);
        assertThat(svc.unreadCount(ACC_B, PROFILE_B)).isZero();
    }

    /** 注销账号 120：只删发给账号 120 的与 A 本人档案(7)的通知；别人档案 120 的通知必须保留。 */
    @Test
    void 注销按账号和本人档案分别删_不误删同号档案的通知() {
        send(NotificationRecipient.account(ACC_A), "feedback_reply", "A 账号级");
        send(NotificationRecipient.profile(PROFILE_A), "expiry", "A 档案");
        send(NotificationRecipient.profile(PROFILE_B), "expiry", "B 档案（id = 120）");

        int deleted = svc.deleteForAccount(ACC_A, List.of(PROFILE_A));

        assertThat(deleted).isEqualTo(2);
        assertThat(svc.list(ACC_B, PROFILE_B)).extracting(Notification::getTitle).containsExactly("B 档案（id = 120）");
        // 无本人档案时只删账号级，且不会拿账号 id 去比档案列
        send(NotificationRecipient.account(ACC_A), "feedback_reply", "A 账号级2");
        assertThat(svc.deleteForAccount(ACC_A, List.of())).isEqualTo(1);
        assertThat(svc.list(ACC_B, PROFILE_B)).hasSize(1);
    }

    /** 账号合并：账号级通知 W→P；同号的档案级通知不动。 */
    @Test
    void 合并只改账号级收件人_不动同号档案级通知() {
        long w = 120L;
        long p = 5000L;
        send(NotificationRecipient.account(w), "feedback_reply", "W 的反馈回复");
        send(NotificationRecipient.profile(w), "expiry", "别人档案 120");

        assertThat(svc.reassignAccount(w, p)).isEqualTo(1);

        assertThat(svc.list(p, null)).extracting(Notification::getTitle).containsExactly("W 的反馈回复");
        assertThat(svc.list(w, null)).isEmpty();
        assertThat(svc.list(999L, w)).extracting(Notification::getTitle).containsExactly("别人档案 120");
    }

    /** 收件人必须且只能指定账号或档案之一；查询也必须带身份。 */
    @Test
    void 收件人二选一_且查询必须带身份() {
        assertThatThrownBy(() -> new NotificationRecipient(1L, 2L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationRecipient(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.list(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 落库形态：账号级只填账号列，档案级只填档案列（两列恰好一个非空）。 */
    @Test
    void 落库_两列恰好一个非空() {
        send(NotificationRecipient.account(ACC_A), "feedback_reply", "a");
        send(NotificationRecipient.profile(PROFILE_A), "expiry", "b");
        Integer bad = jdbc.queryForObject("SELECT COUNT(*) FROM notification "
                + "WHERE (recipient_account_id IS NULL) = (recipient_profile_id IS NULL)", Integer.class);
        assertThat(bad).isZero();
    }
}
