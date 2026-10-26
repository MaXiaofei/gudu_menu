# Implementation Tasks: 小程序正式版发布合规

**Change ID:** `mini-release-compliance`

---

## Phase A：可独立先做（不依赖多家庭）
- [ ] A.1 小程序关闭社区：`FEATURE_COMMUNITY` 开关，隐藏入口，`pkg-community` 不打包；后端识别 `X-Client: mp-weixin` 拒绝社区写接口
- [ ] A.2 prod 关闭 Swagger/knife4j（`application-prod.yml`），放行名单随 profile
- [ ] A.3 上传：UUID 文件名、去 EXIF；`/uploads/**` 改登录可读 + 公共/分享图例外
- [x] A.4 AI 标识组件（mini `ui-ai-badge` / Flutter `AiBadge`）接入 Agent 对话、拍照识别结果、营养报告、智荐、周餐程；营养/特殊人群页加免责提示；Agent 首屏 AI 身份说明；AI 内容反馈入口
- [x] A.5 发给模型的家庭信息最小化：成员称呼假名化，仅传特殊人群标签 + 过敏原；单测断言 prompt 不含真实称呼/手机号/openid
- [ ] A.6 `ContentSecurity` 端口 + 微信实现（msgSecCheck v2 / mediaCheckAsync + 回调）+ 规则兜底；接入 AI 输入输出、昵称、头像、上传图片、反馈
- [ ] A.7 首次登录完善资料：昵称仍为「微信用户」的账号登录后弹一次头像昵称设置（`chooseAvatar` + `type="nickname"`，可跳过、跳过后不再弹）；保存前过 A.6 内容安全；Flutter 端不涉及

- [ ] A.8 个性化推荐开关（个人信息保护法第 24 条）：「我的 → 设置」可关闭，关闭后智荐只给不基于成员档案与饮食记录的通用推荐；mini + Flutter + 后端偏好字段

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

## Phase E：测试与发布
- [ ] E.1 后端 E2E：内容安全拦截、同意记录、上传鉴权、社区写接口对小程序 403
- [ ] E.2 mini unit + Playwright：首次登录资料弹窗（设置/跳过）、协议同意、单独同意弹窗、AI 标识可见、社区入口不存在
- [ ] E.3 Flutter widget/integration：AI 标识、单独同意
- [ ] E.4 依赖 multi-tenant-family 上线后：重新构建上传 → 体验版走查 → 提交审核
