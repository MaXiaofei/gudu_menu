# Implementation Tasks: 小程序正式版发布合规

**Change ID:** `mini-release-compliance`

---

## Phase A：可独立先做（不依赖多家庭）
- [ ] A.1 小程序关闭社区：`FEATURE_COMMUNITY` 开关，隐藏入口，`pkg-community` 不打包；后端识别 `X-Client: mp-weixin` 拒绝社区写接口
- [ ] A.2 prod 关闭 Swagger/knife4j（`application-prod.yml`），放行名单随 profile
- [ ] A.3 上传：UUID 文件名、去 EXIF；`/uploads/**` 改登录可读 + 公共/分享图例外
- [x] A.4 AI 标识组件（mini `ui-ai-badge` / Flutter `AiBadge`）接入 Agent 对话、拍照识别结果、营养报告、智荐、周餐程；营养/特殊人群页加免责提示；Agent 首屏 AI 身份说明；AI 内容反馈入口
- [x] A.5 发给模型的家庭信息最小化：成员称呼假名化，仅传特殊人群标签 + 过敏原；单测断言 prompt 不含真实称呼/手机号/openid
- [x] A.6 `ContentSecurity` 端口 + 微信实现（msgSecCheck v2 / mediaCheckAsync + 回调）+ 规则兜底；接入 AI 输入输出、昵称、头像、上传图片、反馈
  - 上线前手动：公众平台「消息推送」URL `https://imxf.cloud/gudu/wx/push`、Token=WX_TOKEN、明文模式 + JSON；`/uploads` 需对微信服务器可拉取（A.3 收紧时保留例外或改签名 URL）
- [ ] A.7 首次登录完善资料：昵称仍为「微信用户」的账号登录后弹一次头像昵称设置（`chooseAvatar` + `type="nickname"`，可跳过、跳过后不再弹）；保存前过 A.6 内容安全；Flutter 端不涉及

- [ ] A.8 个性化推荐开关（个人信息保护法第 24 条）：「我的 → 设置」可关闭，关闭后智荐只给不基于成员档案与饮食记录的通用推荐；mini + 后端偏好字段（Flutter 随 App 上线再做）
- [ ] A.9 小程序隐藏语音入口（后端语音识别仍是 Mock）：只保留文字对话；隐私指引不再声明麦克风
- [ ] A.10 聚餐邀请链接需要登录：未登录/未同意协议点开链接 → 登录页 → 登录后自动回到点菜页（保留邀请参数）；访客免登录入口在小程序端关闭
- [ ] A.11 意见反馈闭环：反馈加类型（general / ai / complaint）与来源引用；后台可回复并标记处理；回复以站内消息推到小程序「消息与提醒」；小程序「我的反馈」看处理状态与回复

> 2026-10-26 用户决策：协议与功能范围只写**微信小程序**（App 未上架、生产无用户网页端）；聚餐邀请需登录；语音先隐藏。

## Phase B：同意与敏感信息
- [ ] B.1 `consent_record` 表 + `POST /consent` + 登录流程上报协议同意（含版本）
- [ ] B.2 健康档案 / 不满 14 周岁成员单独同意弹窗（mini + Flutter），撤回即删除对应字段
- [ ] B.3 健康档案、手机号字段级加密（TypeHandler + 存量加密迁移脚本 + 回滚）

## Phase C：数据生命周期
- [ ] C.1 定时清理：agent 对话 180 天、ai_call_log 180 天、反馈 1 年
- [ ] C.2 `GET /account/me/export` 个人数据导出（依赖 multi-tenant-family Account）

## Phase D：协议与平台材料
- [x] D.1 重写 `docs/legal/privacy.md`、`terms.md`（个人主体：马晓飞 / maxiaofeidev@gmail.com）并同步 `static/legal/*.ts` 与 Flutter assets，升级 `LEGAL_VERSION`；文本中依赖未完成任务的承诺列在 `docs/legal/README.md`
- [ ] D.2 小程序后台「用户隐私保护指引」填写清单（chooseMedia/chooseImage、保存相册、麦克风、剪贴板、头像昵称）
- [ ] D.3 AI 功能审核材料清单（类目、模型备案号来源、内容安全措施说明）

## Phase F：后台运营配套（上线前必须，依赖 multi-tenant-family 合入）
- [ ] F.1 账号列表与封禁：按昵称/openid 尾号/家庭查询，查看账号状态与所属家庭（不展示家庭私有数据）；封禁/解封带原因，封禁账号登录与写接口拒绝；操作留痕
- [ ] F.2 注销申请处理：查看注销记录与匿名化执行结果，失败重试；隐私政策「15 个工作日」时限提醒
- [ ] F.3 内容安全违规记录：查看文本/图片违规记录（来源、用途、结论、时间），人工复核「恢复」（还原隔离文件、恢复头像/帖子）或「确认违规」
- [ ] F.4 同意记录查询：按账号查询协议版本与同意时间（依赖 B.1）
- [ ] F.5 下线家庭私有数据页面：库存、家庭成员、食集、采购、周计划（周计划页调用的 /mealplan、/mealplan/templates 等接口后端不存在）从路由与菜单删除；运营端只保留只读统计（即 multi-tenant-family F.3）

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
