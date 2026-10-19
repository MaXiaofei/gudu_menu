# Delta: Security（访问控制与输入防护）

**Change ID:** `security-hardening`

## ADDED

### Requirement: 默认登录 + 游客只读白名单
生产环境所有接口默认要求登录；仅公共只读接口（菜谱浏览/搜索/详情/营养、字典、食材查询）允许游客访问。

#### Scenario: 匿名访问家庭数据
- GIVEN 请求未携带有效 token
- WHEN 调用 `GET /menu/page`
- THEN 返回 401

#### Scenario: 游客浏览菜谱（小程序审核）
- GIVEN 请求未携带 token
- WHEN 调用 `GET /dish/search`
- THEN 返回 200 与菜谱列表

### Requirement: 身份切换不得越权
当前就餐成员只能切换为本人或无登录账号的成员档案，且不得为超管。

#### Scenario: 普通用户切换为超管
- GIVEN 登录账号 A（isAdmin=0），超管成员 id=1
- WHEN `POST /member/current?memberId=1`
- THEN 返回 403，session 不变

### Requirement: 平台管理接口仅超管
AI 供应商/调用日志/用量、向量重建、字典与食材写操作仅超管可调用；管理后台仅超管可登录。

#### Scenario: 普通用户切换 AI 供应商
- GIVEN 登录账号 isAdmin=0
- WHEN `PUT /ai/provider`
- THEN 返回 403

#### Scenario: 普通用户登录后台
- WHEN `POST /auth/login` 携带 `scope=admin` 且账号非超管
- THEN 返回「无后台权限」，不签发 token

### Requirement: 外链抓取防 SSRF
`POST /dish/import-url` 仅抓取公网 http/https 地址，重定向逐跳复检。

#### Scenario: 抓取内网地址
- WHEN url 为 `http://127.0.0.1:11434/api/tags` 或解析到 10/172.16/192.168/169.254 网段，或重定向到上述地址
- THEN 拒绝并返回「不支持的链接」，不发起连接

### Requirement: 错误信息不外泄
生产环境未知异常只返回通用提示与 traceId，根因仅写日志。

#### Scenario: SQL 异常
- GIVEN 某接口抛出 `BadSqlGrammarException`
- WHEN 生产环境响应
- THEN body 不含表名/SQL 片段，含 traceId；日志含完整堆栈与同一 traceId

### Requirement: AI 调用按账号限流
所有 AI 接口按登录账号统计每日次数，客户端无法通过参数绕过。

#### Scenario: 伪造 memberId 绕过限流
- GIVEN 账号 A 当日已达上限
- WHEN 请求体传入其他 memberId 再次调用 `/ai/menu/recommend`
- THEN 返回 429

### Requirement: 已执行的迁移不重复执行
部署时数据库迁移按「文件名 + checksum」登记，已执行的跳过。

#### Scenario: 连续两次部署
- GIVEN V61/V62 已执行且用户为「饭」标签新增了关联
- WHEN 再次执行 deploy.sh
- THEN V61/V62 不再执行，关联保持不变
