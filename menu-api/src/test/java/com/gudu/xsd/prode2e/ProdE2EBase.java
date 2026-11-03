package com.gudu.xsd.prode2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudu.xsd.modules.auth.WxClient;
import com.gudu.xsd.modules.dish.DishVectorService;
import com.gudu.xsd.modules.family.application.FamilyAppService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.MultiValueMap;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产端到端测试基类。
 *
 * <p><b>安全约束（所有子类必须遵守）</b>：
 * <ul>
 *   <li>总闸 {@link ProdE2ECondition}：缺环境变量 GUDU_PROD_E2E=1 或 Maven profile -Pprod-e2e 则整类跳过；</li>
 *   <li>本机进程不跑任何 {@code @Scheduled}、不写向量库（{@link DishVectorService} 为空实现）、不调微信（{@link WxClient} 为桩）；</li>
 *   <li>所有自建数据（账号昵称、家庭、菜谱、食材、食集、帖子、字典、会话、供应商……）名称一律以 {@link #P} = "E2E_PROD_" 开头；
 *       账号用 {@link #createAccount}/{@link #registerAndLogin}/{@link #wxLogin} 创建（自动记台账）；
 *       手机号用 {@link #phoneFor(String)}（每次运行取不重复的 1390000xxxx）；</li>
 *   <li>不得删除 / 修改任何非 E2E_PROD_ 数据；不得执行 DDL；修改全站配置前先 {@link #snapshot} 快照，finally 里 {@code restoreOrFail()}；</li>
 *   <li>类结束 {@code @AfterAll} 自动按前缀清理并自检残留，有残留则整个类失败。</li>
 * </ul>
 *
 * <p>夹具（@BeforeAll 创建，类结束清理）：一个 SUPER_ADMIN 账号（自带一个家庭，家庭里有一个成员档案、一道自建菜「番茄炒蛋」）。
 * {@link #loginAdmin()} 登录的就是它，等价于旧预发测试里的 admin；公共食材 / 公共菜谱只读取生产里已有的。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod-e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ProdE2ECondition.class)
@Import(ProdE2ETestConfig.class)
public abstract class ProdE2EBase {

    /** 所有自建数据的名称前缀。 */
    protected static final String P = ProdE2ECleaner.PREFIX;

    /** 本次运行所有 E2E 账号共用的随机密码（不落盘、不入库明文）。 */
    protected static final String PW = "Pw" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

    /** 后端 context-path：内嵌 Tomcat 测试期是根 context。 */
    protected static final String CTX = "";

    private static final AtomicInteger PHONE_SEQ = new AtomicInteger(1000 + ThreadLocalRandom.current().nextInt(7000));

    @Autowired
    protected TestRestTemplate http;
    @Autowired
    protected ApplicationContext ctx;
    @Autowired
    protected FamilyAppService familyApp;
    @Autowired
    protected StringRedisTemplate redis;

    /** 微信登录桩：不会真的请求微信。 */
    @MockBean
    protected WxClient wxClient;
    /** 向量库空实现：不往生产向量库写测试菜谱，语义检索返回空。 */
    @MockBean
    protected DishVectorService dishVector;

    /** 业务库（MySQL，@Primary）直连；容器里还有一个 PG 向量库数据源，不能直接注入 JdbcTemplate。 */
    protected JdbcTemplate jdbc;

    @Autowired
    void initJdbc(DataSource primaryDataSource) {
        this.jdbc = new JdbcTemplate(primaryDataSource);
    }

    protected final ObjectMapper om = new ObjectMapper();

    // 夹具
    protected long adminId;
    protected String adminPhone;
    protected long adminFamilyId;
    /** 超管家庭里的成员档案（旧测试的「张爸爸/掌勺」）。 */
    protected long chefMemberId;
    /** 生产里已有的公共食材「番茄」「鸡蛋」与单位 g。 */
    protected long tomatoId;
    protected long eggId;
    protected long unitGId;
    /** 超管家庭里自建的菜「E2E_PROD_番茄炒蛋」（番茄 300g + 鸡蛋 180g），私有。 */
    protected long dishId;
    /** 生产里已有的某道公共菜谱（只读用）。 */
    protected long publicDishId;

    private final Map<String, String> phones = new HashMap<>();

    // ------------------------------------------------------------------ 生命周期

    /** 子类不需要夹具（如纯清理入口）时覆盖为 false。 */
    protected boolean withFixtures() {
        return true;
    }

    @BeforeAll
    void prodE2eSetUp() {
        assertThat(ctx.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class))
                .as("生产端到端测试进程里不能有 @Scheduled 处理器").isEmpty();
        String db = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertThat(db).as("只允许连 gudu 库").isEqualTo("gudu");
        // 上次中途失败留下的 E2E_PROD_ 数据先清掉（只按前缀）
        newCleaner().cleanAll();
        if (withFixtures()) createFixtures();
    }

    @AfterAll
    void prodE2eTearDown() {
        ProdE2ECleaner.Result r = newCleaner().cleanAll();
        System.out.println("[prod-e2e] " + getClass().getSimpleName() + " " + r);
        Map<String, Integer> left = newCleaner().residue();
        assertThat(left).as("E2E_PROD_ 数据清理后仍有残留，请运行清理入口 GuduProdE2ECleanupTest 并人工核对").isEmpty();
    }

    protected ProdE2ECleaner newCleaner() {
        return new ProdE2ECleaner(jdbc, redis);
    }

    private void createFixtures() {
        adminPhone = phoneFor("admin");
        adminId = createAccount(adminPhone, P + "admin");
        // 唯一一处提权：只提自己刚建的、昵称带前缀的账号
        int n = jdbc.update("UPDATE account SET platform_role = 'SUPER_ADMIN' WHERE id = ? AND LOWER(LEFT(nickname, 9)) = 'e2e_prod_'",
                adminId);
        assertThat(n).isEqualTo(1);
        adminFamilyId = jdbc.queryForObject("SELECT family_id FROM family_membership WHERE account_id = ? LIMIT 1", Long.class, adminId);
        chefMemberId = jdbc.queryForObject("SELECT id FROM member WHERE account_id = ? AND family_id = ? AND deleted = 0 "
                + "ORDER BY id LIMIT 1", Long.class, adminId, adminFamilyId);
        tomatoId = publicIngredient("番茄");
        eggId = publicIngredient("鸡蛋");
        unitGId = jdbc.queryForObject("SELECT id FROM sys_dict WHERE dict_group = 'unit' AND name = 'g' ORDER BY id LIMIT 1", Long.class);
        List<Long> pub = jdbc.queryForList("SELECT id FROM dish WHERE family_id IS NULL AND deleted = 0 "
                + "ORDER BY (name = '番茄炒蛋') DESC, id LIMIT 1", Long.class);
        assertThat(pub).as("生产里应至少有一道公共菜谱").isNotEmpty();
        publicDishId = pub.get(0);
        String token = loginAdmin();
        dishId = createDish(token, P + "番茄炒蛋", List.of(
                Map.of("ingredientId", tomatoId, "amount", 300, "unitId", unitGId),
                Map.of("ingredientId", eggId, "amount", 180, "unitId", unitGId)));
    }

    private long publicIngredient(String name) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM ingredient WHERE name = ? AND family_id IS NULL AND deleted = 0 "
                + "ORDER BY id LIMIT 1", Long.class, name);
        assertThat(ids).as("生产里应有公共食材「" + name + "」").isNotEmpty();
        return ids.get(0);
    }

    // ------------------------------------------------------------------ 账号 / 登录

    /** 为 key 分配一个本次运行内唯一的 E2E 手机号（1390000xxxx）；同一 key 多次调用返回同一个。 */
    protected String phoneFor(String key) {
        return phones.computeIfAbsent(key, k -> "1390000" + String.format("%04d", PHONE_SEQ.incrementAndGet() % 10000));
    }

    /** 直接用服务层建手机号账号（自动建「我的家」），记台账，返回账号 id。nickname 必须带前缀。 */
    protected long createAccount(String phone, String nickname) {
        assertThat(nickname).as("E2E 账号昵称必须以 " + P + " 开头").startsWith(P);
        long id = familyApp.registerPhoneAccount(phone, PW, nickname, null);
        ProdE2ELedger.recordAccount(id);
        return id;
    }

    /** 超管经 POST /account 代建账号后用手机号登录，返回登录响应 data（token / accountId / currentFamilyId / families）。 */
    protected JsonNode registerAndLogin(String phone, String nickname) {
        String n = nickname.startsWith(P) ? nickname : P + nickname;
        String admin = loginAdmin();
        JsonNode created = post(admin, "/account", Map.of("phone", phone, "password", PW, "nickname", n));
        assertThat(created.get("code").asInt()).as("代建账号 msg=" + text(created, "msg")).isEqualTo(0);
        ProdE2ELedger.recordAccount(created.get("data").asLong());
        JsonNode login = post(null, "/auth/login", Map.of("username", phone, "password", PW));
        assertThat(login.get("code").asInt()).as("登录 msg=" + text(login, "msg")).isEqualTo(0);
        return login.get("data");
    }

    /** 微信登录（桩）：openid 必须以 E2E_PROD_ 开头；首次登录自动建账号建家并记台账。返回 data。 */
    protected JsonNode wxLogin(String openid, String inviteCode) {
        assertThat(openid).as("E2E 微信 openid 必须以 " + P + " 开头").startsWith(P);
        org.mockito.Mockito.when(wxClient.code2session("code-" + openid)).thenReturn(openid);
        Map<String, String> body = new HashMap<>();
        body.put("code", "code-" + openid);
        if (inviteCode != null) body.put("inviteCode", inviteCode);
        JsonNode r = post(null, "/auth/wx-login", body);
        assertThat(r.get("code").asInt()).as("微信登录 msg=" + text(r, "msg")).isEqualTo(0);
        JsonNode data = r.get("data");
        if (data.hasNonNull("accountId")) ProdE2ELedger.recordAccount(data.get("accountId").asLong());
        return data;
    }

    /** E2E 超管登录（家庭身份）。每次重新登录拿新 token。 */
    protected String loginAdmin() {
        return loginAs(adminPhone, PW);
    }

    /** 管理后台登录（scope=admin）：无家庭上下文，只用于公共数据 / 运营后台接口。 */
    protected String loginAdminScope() {
        Map<String, String> body = new HashMap<>();
        body.put("username", adminPhone);
        body.put("password", PW);
        body.put("scope", "admin");
        JsonNode r = post(null, "/auth/login", body);
        assertThat(r.get("code").asInt()).as("后台登录应成功 msg=" + text(r, "msg")).isEqualTo(0);
        return r.get("data").get("token").asText();
    }

    protected String loginAs(String phone, String password) {
        JsonNode r = post(null, "/auth/login", Map.of("username", phone, "password", password));
        assertThat(r.get("code").asInt()).as("登录 " + phone + " msg=" + text(r, "msg")).isEqualTo(0);
        String token = r.get("data").get("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    /** 建一道私有菜（家庭身份），返回菜谱 id。 */
    protected long createDish(String token, String name, List<Map<String, Object>> ingredients) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dish", Map.of("name", name, "difficulty", 1, "cookTime", 10, "prepTime", 5));
        body.put("ingredients", ingredients);
        body.put("steps", List.of(Map.of("seq", 1, "text", "E2E_PROD_步骤")));
        body.put("cuisineIds", List.of());
        body.put("tagIds", List.of());
        body.put("categoryIds", List.of());
        JsonNode r = post(token, "/dish", body);
        assertThat(r.get("code").asInt()).as("建菜 msg=" + text(r, "msg")).isEqualTo(0);
        return r.get("data").asLong();
    }

    // ------------------------------------------------------------------ 全站配置快照

    /** 全站配置快照：用例改全站配置前调用，finally 里 restoreOrFail()。 */
    protected ProdE2ESnapshot snapshot(String table, String keyColumn) {
        return ProdE2ESnapshot.of(jdbc, table, keyColumn);
    }

    // ------------------------------------------------------------------ HTTP

    protected JsonNode post(String token, String path, Object body) {
        return exchange(token, path, HttpMethod.POST, body, null);
    }

    protected JsonNode get(String token, String path) {
        return exchange(token, path, HttpMethod.GET, null, null);
    }

    protected JsonNode put(String token, String path, Object body) {
        return exchange(token, path, HttpMethod.PUT, body, null);
    }

    protected JsonNode delete(String token, String path) {
        return exchange(token, path, HttpMethod.DELETE, null, null);
    }

    /** 带来源端头 X-Client（如 mp-weixin）的请求；client 为 null 即不带（按 App）。 */
    protected JsonNode withClient(String client, String token, String path, HttpMethod method, Object body) {
        Map<String, String> h = new HashMap<>();
        if (client != null) h.put("X-Client", client);
        return exchange(token, path, method, body, h);
    }

    protected JsonNode exchange(String token, String path, HttpMethod method, Object body, Map<String, String> headers) {
        try {
            ResponseEntity<String> resp = exchangeRaw(token, path, method, body, headers);
            return om.readTree(resp.getBody());
        } catch (Exception e) {
            throw new RuntimeException(method + " " + path + " 失败: " + e.getMessage(), e);
        }
    }

    /** 返回原始响应（下载、状态码、响应头断言用）。 */
    protected ResponseEntity<String> exchangeRaw(String token, String path, HttpMethod method, Object body,
                                                 Map<String, String> headers) {
        HttpHeaders h = new HttpHeaders();
        if (body != null && !(body instanceof MultiValueMap)) h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.set("Authorization", token);
        if (headers != null) headers.forEach(h::set);
        return http.exchange(CTX + path, method, new HttpEntity<>(body, h), String.class);
    }

    protected static String text(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return n == null || n.isNull() ? "" : n.asText();
    }

    protected static List<String> names(JsonNode arr, String field) {
        List<String> out = new ArrayList<>();
        if (arr != null) arr.forEach(n -> out.add(text(n, field)));
        return out;
    }
}
