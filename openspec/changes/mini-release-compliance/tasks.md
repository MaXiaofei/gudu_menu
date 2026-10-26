# Implementation Tasks: 小程序正式版发布合规

**Change ID:** `mini-release-compliance`

---

## Phase A：可独立先做（不依赖多家庭）
- [x] A.1 小程序关闭社区：`FEATURE_COMMUNITY` 开关，隐藏入口，`pkg-community` 不打包；后端识别 `X-Client: mp-weixin` 拒绝社区写接口
- [x] A.2 prod 关闭 Swagger/knife4j（`application-prod.yml`），放行名单随 profile
  - `springdoc.api-docs/swagger-ui.enabled` 随 `GUDU_API_DOCS_ENABLED`（prod 默认 false；staging/test compose 设 true）；关闭时 SaTokenConfig 不放行文档路径（未登录 401、已登录 404）；knife4j 早已弃用，删掉残留配置
- [x] A.3 上传：UUID 文件名、去 EXIF；`/uploads/**` 改**短时签名 URL**（小程序 `<image>`、微信拉图带不了登录头）
  - 格式 `?e=过期秒&s=base64url(HMAC-SHA256(secret, 规范路径+"\n"+e))[:16B]`，规范路径把 thumbnail/ 归一为 original/（缩略图共用签名）；默认 24h、过期取整到整点（同小时 URL 稳定可缓存）
  - 出参签名、入参验签剥签名集中在 HTTP Jackson 转换器（`UploadUrlJacksonModule`，不影响入库/Redis 的 ObjectMapper）；入参未签名地址拒绝（防拿猜到的旧时间戳文件名「引用」他人图片）；微信 media_url 同样签名
  - 公开内容不另设免签目录：公共菜谱图在 nginx `/images/`（本就不经 /uploads）；用户/后台上传图都经接口下发、统一签名；存量时间戳文件同样须签名
  - 上线：`.env` 配 `GUDU_UPLOAD_SIGN_SECRET`（prod compose 必填）；dev/test/staging 默认不校验
- [x] A.4 AI 标识组件（mini `ui-ai-badge` / Flutter `AiBadge`）接入 Agent 对话、拍照识别结果、营养报告、智荐、周餐程；营养/特殊人群页加免责提示；Agent 首屏 AI 身份说明；AI 内容反馈入口
- [x] A.5 发给模型的家庭信息最小化：成员称呼假名化，仅传特殊人群标签 + 过敏原；单测断言 prompt 不含真实称呼/手机号/openid
- [x] A.6 `ContentSecurity` 端口 + 微信实现（msgSecCheck v2 / mediaCheckAsync + 回调）+ 规则兜底；接入 AI 输入输出、昵称、头像、上传图片、反馈
  - 上线前手动：公众平台「消息推送」URL `https://imxf.cloud/gudu/wx/push`、Token=WX_TOKEN、明文模式 + JSON；`/uploads` 需对微信服务器可拉取（A.3 已改短时签名 URL，提交微信的 media_url 自带签名）
- [x] A.7 首次登录完善资料：昵称仍为「微信用户」的账号登录后弹一次头像昵称设置（`chooseAvatar` + `type="nickname"`，可跳过、跳过后不再弹）；保存前过 A.6 内容安全；Flutter 端不涉及

- [x] A.8 个性化推荐开关（个人信息保护法第 24 条）：「我的 → 设置」可关闭，关闭后智荐只给不基于成员档案与饮食记录的通用推荐；mini + 后端偏好字段（Flutter 随 App 上线再做）
- [x] A.9 小程序隐藏语音入口（后端语音识别仍是 Mock）：只保留文字对话；隐私指引不再声明麦克风
- [x] A.10 聚餐邀请链接需要登录：未登录/未同意协议点开链接 → 登录页 → 登录后自动回到点菜页（保留邀请参数）；访客免登录入口在小程序端关闭
- [x] A.11 意见反馈闭环：反馈加类型（general / ai / complaint）与来源引用；后台可回复并标记处理；回复以站内消息推到小程序「消息与提醒」；小程序「我的反馈」看处理状态与回复
- [x] A.12 厨房圈紧急关停开关（只用于出现内容风险时一键关闭，不用于审核后开放；小程序仍按 A.1 构建期不打包）：
  后端运行时开关 `community.enabled`（V85 `sys_switch` + `sys_switch_log`，进程内缓存 15 秒），关闭时 `/community/**` 读写统一业务码 5031「厨房圈暂停服务」，`/admin/community/**` 审核不受影响，每次修改记操作人/时间/原因；
  menu-admin 社区审核页顶部开关（二次确认、关停必填原因、显示状态与最后操作人、修改记录）；H5（menu-mini）与 App 收到 5031 整页显示暂停提示不报错

> 2026-10-26 用户决策：协议与功能范围只写**微信小程序**（App 未上架、生产无用户网页端）；聚餐邀请需登录；语音先隐藏。

## Phase B：同意与敏感信息
- [x] B.1 `consent_record` 表 + `POST /consent` + 登录流程上报协议同意（含版本）
  - 复用 V88 `consent_record`（privacy 上下文），doc_type 扩展 `terms` / `privacy`（单独同意状态接口仍只返回 sensitive_health / child_info）；`POST /consent {docTypes?, docVersion, granted?, channel?}`：默认两份各记一条，`granted=false` 记撤回（只留痕，不触发数据删除）；账号合并改指沿用 privacy 的 `ConsentAccountMergedListener`
  - mini：登录页勾选后、微信 / 账号登录成功即 `reportConsent()` 上报当前 `LEGAL_VERSION`（静默，失败下次登录再报）
- [x] B.2 健康档案 / 不满 14 周岁成员单独同意弹窗（mini + Flutter），撤回即删除对应字段
  - 后端 privacy 上下文：`consent_record`（V88 自建，B.1 合入时复用；doc_type = sensitive_health / child_info）+ `GET/POST /consent/sensitive`、`POST /consent/sensitive/withdraw`；`/member` 新增/编辑含健康档案或不满 14 周岁信息时无同意记录 → 428 `CONSENT_REQUIRED`（data.docTypes），不保存
  - 撤回即删除：`member.health_consent_account_id` 记录提供人；撤回 sensitive_health 清空本人档案 + 其提供的全部档案的健康档案/目标/活动水平，撤回 child_info 只清其中儿童档案；称呼/头像保留；`AccountMerged` 改指提供人与同意记录
  - mini：`saveMemberProfile` 保存前按缺项弹原生单独同意窗（儿童场景「监护人单独同意」），428 兜底重弹重试；「家庭成员」页「敏感信息授权」可撤回。成员档案编辑页本身属 multi-tenant-family F.1，须调用 `saveMemberProfile`；Flutter 端随 App 上线再做
- [x] B.3 健康档案、手机号字段级加密（TypeHandler + 存量加密迁移脚本 + 回滚）
  - AES-256-GCM，密文 `ENC:v<版本>:…`，密钥 `GUDU_FIELD_CRYPTO_KEYS`（多版本，`GUDU_FIELD_CRYPTO_ACTIVE` 选加密版本）；手机号盲索引 `account.phone_hash`（HMAC-SHA256，`GUDU_FIELD_CRYPTO_HMAC_KEY`，唯一索引，登录按它查，兼容未迁移明文）；openid 明文
  - V88 列改造（health_profile JSON→TEXT、phone 加宽、phone_hash）；存量迁移/轮换/回滚用一次性进程 `--gudu.field-crypto.migrate=encrypt|decrypt`（MySQL 无 AES-GCM），回滚 SQL `sql/rollback/V88-V89_privacy.sql`
  - dev/test/staging 共用固定测试密钥（共库）；e2e-seed 用 SQL 版 HMAC 还原 E2E 手机号

## Phase C：数据生命周期
- [x] C.1 定时清理：agent 对话 180 天、ai_call_log 180 天、反馈 1 年
  - `DataRetentionJob` 每天 03:30，`RetentionPurger` 各上下文实现（agent 会话/消息、agent_trace/span + ai_call_log、DONE 反馈按 update_time）；每批 500 行独立提交、单次批数上限、系统模式；`gudu.retention.days.<策略>` 可覆盖；V89 补时间索引
- [x] C.2 `GET /account/me/export` 个人数据导出（依赖 multi-tenant-family Account）
  - 各上下文 `PersonalDataSection`：账号、所属家庭+本人档案（不含其他成员）、本人创建的菜谱（V89 `dish.created_by`，存量无归属）、做菜记录、饮食日志、点评、AI 对话、反馈、同意记录；每天 3 次
  - 交付：`POST /account/me/export/link` 签发 30 分钟下载凭证（Redis，不落盘），mini `createExportLink` + `copyExportLink` 复制到浏览器下载；页面入口另做

## Phase D：协议与平台材料
- [x] D.1 重写 `docs/legal/privacy.md`、`terms.md`（个人主体：马晓飞 / maxiaofeidev@gmail.com）并同步 `static/legal/*.ts` 与 Flutter assets，升级 `LEGAL_VERSION`；文本中依赖未完成任务的承诺列在 `docs/legal/README.md`
- [ ] D.2 小程序后台「用户隐私保护指引」填写清单（chooseMedia/chooseImage、保存相册、麦克风、剪贴板、头像昵称）
- [ ] D.3 AI 功能审核材料清单（类目、模型备案号来源、内容安全措施说明）

## Phase F：后台运营配套（上线前必须，依赖 multi-tenant-family 合入）
- [x] F.1 账号列表与封禁：按昵称/openid 尾号/家庭查询，查看账号状态与所属家庭（不展示家庭私有数据）；封禁/解封带原因，封禁账号登录与写接口拒绝；操作留痕
  - `GET /admin/accounts`（只读全局表 account / account_ban / family_membership / family；手机号脱敏、openid 只出末 6 位、只给家庭数）；`POST /admin/accounts/{id}/ban|unban {reason}`；封禁表 `account_ban`（V86）；封禁即 `StpUtil.logout` 下线，登录（手机号 / 微信）与写接口（`BannedAccountInterceptor`）统一 **423 ACCOUNT_BANNED**，小程序弹窗提示原因回登录页；超管不可封、运营只能由超管封
  - 后台操作日志 `admin_operation_log`（V86）+ `GET /admin/operation-logs`（封禁、解封、恢复、确认违规、重试注销）
- [x] F.2 注销申请处理：查看注销记录与匿名化执行结果，失败重试；隐私政策「15 个工作日」时限提醒
  - `account_deactivation`（V86）：注销同事务受理（第一步「账号匿名化与退出家庭」），提交后执行各上下文 `AccountErasureStep`（系统模式、幂等；当前：AI 对话、拍照识别任务、用户偏好、导出链接凭证、通知、意见反馈联系方式与截图（与账号解绑）、头像文件；同意记录法定留存保留，登记条数），失败 FAILED 可 `POST /admin/deactivations/{id}/retry`；截止 = 受理后第 15 个工作日（只跳周末，偏严），超期高亮 + 顶部告警
- [x] F.3 内容安全违规记录：查看文本/图片违规记录（来源、用途、结论、时间），人工复核「恢复」（还原隔离文件、恢复头像/帖子）或「确认违规」
  - 文本：拦截即写 `content_text_violation`（V86，独立事务不随 451 回滚，只存前 100 字摘录）；图片：`content_media_check` 加复核字段（V87），`MediaCheck.restore/confirmViolation`；恢复发 `MediaRestoredEvent`（头像恢复到上传账号各家庭档案中被清空的头像、帖子恢复公开）后 `UploadQuarantine.release` 还原文件；隔离原图可在后台查看
  - 顺带修复：图片违规回调无登录态（游客租户），订阅方改在系统模式执行，否则多家庭后清不到头像；openid 解析改读 account 表
- [x] F.4 同意记录查询：按账号查询协议版本与同意时间（依赖 B.1）——`GET /admin/accounts/{id}/consents`（协议与单独同意全部记录，倒序），后台账号列表「同意记录」抽屉
- [x] F.5 下线家庭私有数据页面：库存、家庭成员、食集、采购、周计划（周计划页调用的 /mealplan、/mealplan/templates 等接口后端不存在）从路由与菜单删除；运营端只保留只读统计（即 multi-tenant-family F.3）
  - menu-admin 删除 views/member、menu、mealplan、pantry、shopping 与 api/member、menu、mealplan、pantry、shopping；后端无「仅后台使用」的家庭数据接口（这些页面调用的都是按家庭过滤的通用接口）
  - 后台鉴权改 OPERATOR / SUPER_ADMIN：`scope=admin` 登录接受平台运营；新增 `@RequireOperator`（运营后台、仪表盘、意见反馈、社区审核），模型网关 / AI 日志 / Agent 调用链与评测 / 配置中心仍 `@RequireAdmin`，前端对运营隐藏

## Phase G：上线后一个月内
- [ ] G.1 站内公告推送（停服、协议升级等通知到「消息与提醒」）
- [ ] G.2 功能开关中心（远程关闭功能，复用 A.12 开关机制）
- [ ] G.3 AI 每日限额在后台可改（现只能改服务端配置 `gudu.ai.daily-limit`）
- [ ] G.4 操作审计日志（封禁、复核、开关、回复等后台操作可追溯）
- [ ] G.5 系统健康与告警（先用企业微信机器人告警）；后台系统健康页去掉占位
- [ ] G.6 公共菜谱投稿审核（菜谱库待审核/已驳回/已下架页）；食材 AI 估算审核状态字段
- [ ] G.7 openspec 任务状态校正：feature-gaps-closure 5.2、showcase-ai-community 1.0/2.4/3.4/5.3 实际已完成，核对后勾选

## Phase E：测试与发布
- [ ] E.1 后端 E2E：内容安全拦截、同意记录、上传鉴权、社区写接口对小程序 403
- [ ] E.2 mini unit + Playwright：首次登录资料弹窗（设置/跳过）、协议同意、单独同意弹窗、AI 标识可见、社区入口不存在
- [ ] E.3 Flutter widget/integration：AI 标识、单独同意
- [ ] E.4 依赖 multi-tenant-family 上线后：重新构建上传 → 体验版走查 → 提交审核
