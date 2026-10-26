# Delta: Family Tenancy（账号、家庭与数据隔离）

**Change ID:** `multi-tenant-family`

## ADDED

### Requirement: 家庭是数据隔离单元
菜单、库存、采购、食记、点评、草稿、成员档案、私有菜谱等数据归属于唯一家庭，只有该家庭成员可访问。

#### Scenario: 跨家庭读取
- GIVEN 账号 X 仅属于家庭 B，家庭 A 有菜单 id=10
- WHEN X 调用 `GET /menu/10`
- THEN 返回 404

#### Scenario: 跨家庭删除
- WHEN X 调用 `DELETE /shopping/{A 家庭清单 id}`
- THEN 返回 404，数据不变

#### Scenario: 被移出后会话失效
- GIVEN X 的当前家庭为 A，随后被 OWNER 移出
- WHEN X 发起任意家庭数据请求
- THEN 返回 403 FAMILY_ACCESS_DENIED，前端回到家庭选择

### Requirement: 公共菜谱库与本家菜谱
平台公共菜谱所有家庭可读；本家菜谱仅本家可读写；公共菜谱可复制到本家。

#### Scenario: 浏览菜谱
- WHEN 家庭 B 成员搜索菜谱
- THEN 结果包含公共菜谱与 B 的私有菜谱，不包含 A 的私有菜谱

#### Scenario: 修改公共菜谱
- GIVEN 普通用户（PlatformRole=USER）
- WHEN `PUT /dish` 修改公共菜谱
- THEN 返回 403

#### Scenario: 复制到我家
- WHEN `POST /dish/{公共菜谱id}/fork`
- THEN 生成一份 family_id=当前家庭、source_dish_id=原菜谱 的新菜谱，含全部用料与步骤

### Requirement: 家庭恰好一个主理人
任何操作后家庭有且仅有一个 OWNER；OWNER 不能直接退出。

#### Scenario: 主理人退出
- WHEN OWNER 调用 `POST /family/{id}/leave`
- THEN 拒绝，提示先转让主理人或解散家庭

#### Scenario: 转让主理人
- WHEN OWNER 转让给成员 Y
- THEN Y 为 OWNER，原 OWNER 变为 ADMIN

### Requirement: 邀请加入
OWNER/ADMIN 可生成 7 天有效的邀请码；过期、撤销、用尽或家庭满员时拒绝加入。

#### Scenario: 过期邀请
- GIVEN 邀请码已超过 expiresAt
- WHEN `POST /family/join`
- THEN 拒绝「邀请已过期」

#### Scenario: 重复加入
- GIVEN 账号已是家庭成员
- WHEN 再次凭码加入
- THEN 拒绝「已在该家庭中」，不产生重复成员关系

#### Scenario: 加入数量上限
- GIVEN 账号已加入 5 个家庭
- WHEN 凭码加入第 6 个
- THEN 拒绝

### Requirement: 多家庭切换
账号可加入多个家庭，并在会话中切换当前家庭；切换前校验成员关系。

#### Scenario: 切换到未加入的家庭
- WHEN `POST /family/current {familyId: 非成员家庭}`
- THEN 返回 404，当前家庭不变

### Requirement: 账号注销
用户可注销账号；OWNER 须先转让或解散家庭；注销后个人身份信息匿名化。

#### Scenario: 主理人注销
- GIVEN 账号是某家庭 OWNER 且家庭还有其他成员
- WHEN `DELETE /account/me`
- THEN 拒绝并提示先转让

#### Scenario: 注销成功
- WHEN 普通成员注销
- THEN 账号 status=DEACTIVATED，手机号/openid/昵称清空，退出全部家庭，token 失效

### Requirement: 微信账号并入手机号账号
同一个人的微信账号与手机号账号可以合并，以手机号账号为主；合并后微信登录直接进入手机号账号，原微信账号的家庭与数据都可见。

#### Scenario: 合并成功
- GIVEN 微信账号 W（属于家庭 F1）已登录，手机号账号 P（属于家庭 F2）
- WHEN `POST /account/merge-phone` 提交 P 的手机号和正确密码
- THEN P 绑定 W 的 openid，P 同属 F1 与 F2，W 状态为 MERGED 且 token 失效，响应返回 P 的新 token

#### Scenario: 合并后微信登录
- WHEN 用 W 的 openid 调用 `/auth/wx-login`
- THEN 登录到 P

#### Scenario: 手机号账号已绑定其他微信
- GIVEN P 已绑定另一个 openid
- WHEN 发起合并
- THEN 拒绝（ACCOUNT_WECHAT_CONFLICT），两个账号都不变

#### Scenario: 密码错误
- WHEN 提交错误密码
- THEN 拒绝并计入登录失败限流，不泄露该手机号是否存在

### Requirement: 新用户自动建家
首次登录且未通过邀请进入的账号自动创建「我的家」并成为 OWNER。

#### Scenario: 微信首次登录
- WHEN 新 openid 调用 `/auth/wx-login`
- THEN 创建 Account + Family(OWNER) + 关联成员档案，响应 currentFamilyId 为新家庭
