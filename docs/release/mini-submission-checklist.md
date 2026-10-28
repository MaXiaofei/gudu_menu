# 咕嘟小食单 微信小程序 提审材料清单

适用：feat/release-compliance 起的正式版小程序（个人主体：马晓飞，联系邮箱 maxiaofeidev@gmail.com）。
对应 openspec `mini-release-compliance` 的 D.2、D.3。

> 约定：带「（推断）」的条目来自对微信平台规则的记忆，不是本仓库证据，**以公众平台后台的实际提示为准**。
> 其余条目均已对照仓库代码核实，括号内给出出处。

---

## 一、「用户隐私保护指引」逐项填写（D.2）

路径：公众平台 → 设置 → 服务内容声明 → 用户隐私保护指引。
原则：**声明的接口必须和代码实际调用一致**，多声明、少声明都可能被打回。用途文案与 `docs/legal/privacy.md` 第二节保持一致。

### 1.1 需要声明的

| 隐私接口 / 信息 | 代码里的调用位置 | 建议填写的用途说明 |
|---|---|---|
| 选中的照片或视频（相册） | `uni.chooseMedia`（`src/api/upload.ts`）、`uni.chooseImage`（`src/pkg-ai/Scan.vue`） | 用于上传菜谱封面与步骤图片、食记照片、头像，以及拍照识别食材时选取图片。仅在你主动选择图片时使用。 |
| 摄像头 | `<camera>` 组件（`src/pkg-ai/Scan.vue`）；`chooseMedia` / `chooseImage` 的拍照来源 | 用于拍摄冰箱、食材或小票以识别食材，以及拍摄菜品照片。仅在你使用对应功能时开启。 |
| 保存图片到相册（仅写入） | `uni.saveImageToPhotosAlbum`（`src/utils/album.ts`） | 用于把你生成的分享图片保存到手机相册。 |
| 剪贴板（写入） | `uni.setClipboardData`（`pkg-family/Index.vue` 邀请码、`api/privacy.ts` 导出链接、`pages/shopping/parts/ShareView.vue` 采购清单、`pages/menu/parts/TogetherTab.vue` 聚餐邀请） | 用于复制采购清单、家庭邀请码、聚餐邀请和数据导出下载链接，方便你粘贴分享。 |
| 微信头像 | `<button open-type="chooseAvatar">`（`components/ui-profile-form.vue`） | 用于你主动选择的头像，在家庭成员与聚餐中展示。可跳过，不影响使用。 |
| 微信昵称 | `<input type="nickname">`（`components/ui-profile-form.vue`） | 用于你主动填写的昵称，在家庭成员与聚餐中展示。可跳过，不影响使用。 |

### 1.2 开发者处理的个人信息（「开发者收集的信息」一栏，与 privacy.md 第二节一致）

| 信息 | 用途 | 说明 |
|---|---|---|
| 微信 openid | 识别账号、保持登录 | 必需 |
| 昵称、头像 | 家庭与聚餐中展示 | 可选 |
| 成员健康档案：身高、体重、年龄、性别、活动水平、忌口与过敏、特殊饮食人群 | 营养计算、菜品过滤、个性化推荐 | 敏感信息，首次填写时单独同意，可撤回；加密存储 |
| 上传的图片（菜谱、食记、拍照识别） | 提供菜谱与食记功能；拍照识别原图 24 小时内删除 | 上传时去除拍摄地点等元数据 |
| 您输入给 AI 助手的文字、AI 对话记录 | 回答问题；对话保存 180 天，可自行删除 | 发送给模型服务商的范围见 privacy.md 第三节 |
| 意见反馈内容、联系方式（可选）、设备型号与版本 | 处理并答复反馈 | 保存 1 年 |
| 同意记录（协议版本、时间、IP） | 证明已取得同意 | 必需 |
| 请求日志（时间、接口、IP、错误信息） | 安全与排障 | 保存不超过 6 个月 |

存储地点：中国境内（腾讯云）。保存期限、联系方式、用户权利按 privacy.md 第六、七、九节填写。

### 1.3 不要声明的

代码里**没有**以下调用，声明了反而与实际不符：

- 麦克风 / 录音：语音页 `pkg-agent/Voice` 已在 mp-weixin 构建中剔除（`pages.json` 的 `#ifndef MP-WEIXIN`），`FEATURES.voice = false`
- 位置信息（`getLocation` 等）
- 通讯录、微信好友关系
- 微信绑定手机号（`getPhoneNumber` 没有使用；手机号只在「账号与安全 → 合并手机号账号」里由用户手输并校验密码）
- 蓝牙、运动数据、发票等

### 1.4 其他设置

- 版本设置里把最低基础库设为 **2.32.3 及以上**（隐私授权弹窗依赖）（推断）。
- 本工程 `manifest.json` 没有 `__usePrivacyCheck__`，不加也可以；但体验版必须在真机上确认：首次触发相册、相机、剪贴板时，微信的系统隐私弹窗能正常弹出。

---

## 二、服务器域名与消息推送

### 2.1 合法域名（开发管理 → 开发设置 → 服务器域名）

小程序里所有出网地址只有一个基址：`src/config/env.ts` 的 `ENV='prod'` → `https://imxf.cloud/gudu`。
全工程除该文件外没有其他 http(s) 地址（`staging.imxf.cloud` 仅 `ENV='staging'` 时使用，**不要**配到正式版）。

| 类型 | 填写 | 代码依据 |
|---|---|---|
| request 合法域名 | `https://imxf.cloud` | 全部接口 `/gudu/...`；AI 对话 SSE 也走 `uni.request` + `enableChunked`（`utils/sse.ts`），同属 request 域名 |
| uploadFile 合法域名 | `https://imxf.cloud` | `uni.uploadFile` → `/gudu/file/upload`（`api/upload.ts`；`api/agent.ts` 的语音上传在小程序包内不可达） |
| downloadFile 合法域名 | 可不配（代码里没有 `uni.downloadFile`） | 图片用 `<image src>` 加载，不受 downloadFile 域名限制；若以后改用 downloadFile 再补 `https://imxf.cloud` |
| socket 合法域名 | 不需要 | 没有 WebSocket |

前置：`imxf.cloud` 已完成 ICP 备案，且备案主体与小程序主体一致（需用户自行确认，仓库里没有备案信息）；HTTPS 证书有效。

### 2.2 消息推送（图片内容安全异步结果）

路径：开发管理 → 开发设置 → 消息推送。

| 项 | 值 |
|---|---|
| URL | `https://imxf.cloud/gudu/wx/push` |
| Token | 与服务器环境变量 `WX_TOKEN` 相同 |
| EncodingAESKey | 随机生成即可（明文模式不使用） |
| 消息加密方式 | **明文模式** |
| 数据格式 | **JSON** |

- 保存时微信会向该 URL 发 GET 做地址验证，后端 `WxPushController` 的 `GET /wx/push` 负责回显（签名校验用 `WX_TOKEN`）；此接口在 `SaTokenConfig` 里免登录。
- 回调处理代码只识别 `Event=wxa_media_check`，其他事件忽略；数据格式选 XML 会解析失败。
- 同时确认生产已注入 `WX_APPID`、`WX_APPSECRET`，且 `GUDU_CONTENT_SECURITY_WX=true`（生产 compose 默认 true）、`GUDU_MEDIA_BASE_URL=https://imxf.cloud`（prod 配置默认值）。

---

## 三、AI 功能审核材料清单（D.3）

### 3.1 功能清单（小程序内真正由大模型生成内容的）

| 功能 | 入口 | AI 标识与反馈 |
|---|---|---|
| 咕嘟助手（自由对话，可调用工具修改家庭数据） | 首页 tab「咕嘟」、`pkg-agent/Chat` | 首屏「我是 AI 助手」说明；每条回复「AI 生成」胶囊 + 反馈入口 + 好评差评 |
| 跟做模式里的「问咕嘟」 | `pkg-ai/CookMode` | 每条回复「AI 生成」胶囊 + 免责提示 + 反馈入口 |
| 拍照识别（食材 / 小票 / 菜谱） | `pkg-ai/Scan`、`ScanResult` | 结果页「AI 生成」胶囊 + 反馈入口 |
| 一周餐程 | `pkg-ai/WeekPlan` | 「AI 生成」胶囊 + 免责提示 + 反馈入口 |

不是大模型的：智荐推荐、营养报告（规则与本地算法，页面只显示「仅供参考」类提示，不标 AI）。
后端另有「菜品营养估算」「营养补全」接口，**小程序没有入口**，提审说明里不要把它当作功能列出。

### 3.2 使用的模型与服务商

- 由后端 `application-prod.yml` 的 `gudu.ai.provider` 决定，环境变量 `GUDU_AI_PROVIDER`，默认 `deepseek`；备选 `glm`。
- 服务商：杭州深度求索人工智能基础技术研究有限公司（DeepSeek）或北京智谱华章科技股份有限公司（智谱 GLM），按运行配置使用其一，已写入 privacy.md 第五节。**公司全称请用户核对一次**（docs/legal/README.md 的人工核对项）。
- 模型备案号 / 登记号：本仓库没有。需要用户向服务商索取其面向公众服务的备案或登记信息（推断：微信可能要求填写；以平台提示为准）。
- 向量召回使用本地 Ollama（bge-m3），数据不出主机；若线上把 `OLLAMA_BASE_URL` 指到第三方云服务，需补充披露。
- 发给模型的信息：用户本次输入的文字或照片、特殊饮食人群标签、库存与菜谱信息、年龄；成员称呼替换为「成员A」等代号；昵称、手机号、openid 不发送；忌口与过敏文本只在服务端过滤，不发送。

### 3.3 内容安全措施

- 输入：AI 对话输入先过规则（共享禁发词表）再过微信 `msgSecCheck`，违规直接回提示、不调用模型，库里只存占位文本。
- 输出：AI 对话回复放出前复核，违规替换为安全提示；拍照识别结果（食材名、菜谱标题与步骤）、一周餐程的每日提示与校验说明、营养估算说明同样做输出检测，违规项剔除或替换。
- 用户可编辑并展示的文本：昵称、成员档案称呼、家庭名称、菜谱（名称、简介、步骤文字）、点评文字、食集名称、意见反馈，保存前检测，违规返回 451。
- 图片：上传后异步提交微信 `mediaCheckAsync`，违规文件隔离、头像清空；后台「内容安全」页可人工复核恢复或确认。
- 失败策略：昵称、头像在微信检测不可用时拒绝保存；其他文本降级到本地规则。
- 频率与限额：AI 接口每日调用上限默认 50 次（`GUDU_AI_DAILY_LIMIT`）；AI 写操作需用户确认卡确认后才执行，可撤销。
- 紧急关停：咕嘟助手、问咕嘟有管理后台「功能开关」页的紧急关停开关（出现内容风险时一键关闭，迁移 V94，修改留痕、关停必须填原因），默认开启、提审版本即线上版本；关闭后只拦发新消息，已有会话与历史仍可查看、删除，其他功能不受影响。

### 3.4 AI 标识与免责

- 文案：「AI 生成」；免责「仅供参考，不能替代医生或营养师建议」（`src/utils/aiLabel.ts`，与 Flutter 一致）。
- 对应《人工智能生成合成内容标识办法》的显式标识要求（推断，以平台与法规实际要求为准）。
- 用户协议 §五、隐私政策 §三 已写明。

### 3.5 投诉与反馈渠道

- 产品内：「我的 → 意见反馈」，AI 内容旁的「反馈」入口会带上来源（type=ai、sourceRef），处理结果经「消息与提醒」通知；承诺 15 个工作日内处理。
- 邮件：maxiaofeidev@gmail.com。
- 后台：menu-admin 意见反馈页、内容安全违规记录页。

### 3.6 日志与数据留存

| 数据 | 期限 | 实现 |
|---|---|---|
| AI 对话记录 | 180 天，用户可随时自行删除（单个会话或清空） | `AgentConversationPurger`；`DELETE /agent/sessions[/{id}]` |
| AI 调用日志 / 调用链 | 180 天 | `AgentTracePurger`、`AiCallLogRetentionPurger` |
| 拍照识别原图 | 识别后 24 小时 | `VisionJobAppService` 定时清理 |
| 意见反馈 | 处理完成后 1 年 | `FeedbackRetentionPurger` |
| 应用日志 | 30 天滚动（logback），满足「不超过 6 个月」 | 生产 logback 配置 |
| 同意记录 | 法定留存，注销时保留 | `consent_record` |

### 3.7 需要用户自行确认的资质与类目项（均为推断，以平台提示为准）

1. 个人主体能否在「服务类目」里选到与 AI 对话 / 生成相关的类目。很可能受限；若选不到，只能关闭对话类 AI 入口（咕嘟助手、跟做问咕嘟），或换企业主体。这一项决定 AI 功能能否随首版上线，**建议先确认再提审**。
2. 如类目要求模型备案号 / 大模型应用登记号：由用户向所用服务商核实并填写。
3. 其余功能类目（建议选工具类或生活服务类中与菜谱、饮食记录相关的项）不应涉及医疗 / 健康咨询类资质。产品含「高血压、高血糖、高血脂、宝宝辅食」等标签，已按敏感信息处理，并在 AI 与营养页面标注「不构成医疗建议」。
4. 域名 ICP 备案主体与小程序主体一致。
5. 隐私政策、用户协议中的第三方公司全称已核对（深度求索、智谱华章、腾讯相关主体）。
6. 提审版本描述不要出现「厨房圈」「语音」（正式版小程序不提供）。
7. 提审测试说明可写：「微信一键登录即可；首屏勾选协议后登录；无需账号密码」。

---

## 四、生产上线前的环境变量与命令

### 4.1 必配环境变量（docker-compose.prod.yml 已用 `:?` 强制，缺失则 compose 起不来）

| 变量 | 作用 | 生成建议 |
|---|---|---|
| `GUDU_UPLOAD_SIGN_SECRET` | 上传文件签名链接密钥（缺失启动失败） | `openssl rand -base64 48` |
| `GUDU_FIELD_CRYPTO_KEYS` | 字段加密密钥，格式 `1:<base64 32字节>`，轮换时追加 `,2:<...>`，旧版本必须保留 | `echo "1:$(openssl rand -base64 32)"` |
| `GUDU_FIELD_CRYPTO_HMAC_KEY` | 手机号盲索引密钥，≥ 32 字节字符串，**不随加密密钥轮换** | `openssl rand -base64 32` |
| `WX_TOKEN` | 消息推送 Token，须与公众平台填写一致 | 自定义 3–32 位字母数字 |
| `WX_APPID`、`WX_APPSECRET` | 微信登录与内容安全 | 公众平台获取 |
| `DEEPSEEK_API_KEY` 或 `GLM_API_KEY`，`GUDU_AI_PROVIDER` | AI 模型 | 服务商获取 |

加密密钥和 HMAC 密钥**丢失即无法解密 / 无法按手机号登录**，务必离线备份；不要改 HMAC 密钥。
另：`GUDU_API_DOCS_ENABLED` 在生产保持 `false`；`GUDU_EXPOSE_ERROR_DETAIL=false`。

### 4.2 数据库迁移 V75–V91 的执行顺序

迁移由 `scripts/deploy.sh` 在 menu-api 重启前按 `schema_migration_log` 补齐未登记文件，**按文件名顺序**执行：

```
V75__account_family.sql                 账号 / 家庭 / 成员关系表
V76__tenant_family_columns.sql          业务表加 family_id
V77__tenant_data_migration.sql          存量数据并入默认家庭、登录凭证迁到 account
V78__tenant_family_not_null.sql         family_id 改 NOT NULL
V79__account_merge_log.sql              账号合并日志
V80__content_media_check.sql            图片检测记录
V83__feedback_reply.sql                 反馈答复
V84__user_preference.sql                个性化推荐开关
V85__community_switch.sql               厨房圈紧急开关
V86__release_compliance_ops.sql         封禁 / 注销 / 违规记录 / 操作日志
V87__content_media_review.sql           图片违规复核字段
V88__consent_field_encryption.sql       同意记录、字段加密列（phone_hash 等）
V89__data_retention_export.sql          保存期限索引、dish.created_by
V90__menu_invite_expiry.sql             食集邀请有效期
V91__id_space_notification_recipient.sql 账号 / 档案 id 段错开、通知收件人拆列
```

注意：
- 仓库里**没有 V81、V82**，这是编号空缺，不是漏文件（`deploy.sh` 按存在的文件处理）。
- 已登记的迁移禁止修改（checksum 不一致会中止部署）。
- 执行前：**全量备份数据库**；先在 staging 用生产快照演练一遍（multi-tenant-family 的 G.3 迁移演练还没做）。
- 参考校验脚本：`menu-api/sql/verify/tenant_migration_check.sql`（迁移后检查租户归属）。

### 4.3 存量字段加密（一次性）

V88 只加列，不转换存量数据；应用能读取没有 `ENC:` 前缀的明文，所以应用先起来、再加密是安全的。顺序：

1. 配好 4.1 的密钥后部署新版本（迁移 V75–V91 随部署执行）。
2. 跑一次性加密进程（跑完自动退出，可重复执行，只处理需要变化的行）：

```bash
docker compose -f docker-compose.prod.yml run --rm --no-deps menu-api \
  --gudu.field-crypto.migrate=encrypt
```

   （等价于 `java -jar app.jar --spring.profiles.active=prod --gudu.field-crypto.migrate=encrypt`。**不要加 `--spring.main.web-application-type=none`**：非 Web 模式下 `TogetherController` 注入不到 `HttpServletRequest`，启动即失败（2026-10-27 生产实测）；以 Web 模式启动在独立容器里、无端口暴露，迁移完成后进程自行退出。环境变量与 menu-api 服务一致。若 compose 里 profile 不是 prod，请加 `--spring.profiles.active=prod`。）
3. 校验（应全部为密文，占位登录名如 `admin` 保持明文属预期）：

```sql
SELECT COUNT(*) FROM account WHERE phone REGEXP '^1[0-9]{10}$';          -- 应为 0
SELECT COUNT(*) FROM member  WHERE health_profile IS NOT NULL AND health_profile NOT LIKE 'ENC:v%';  -- 应为 0（或仅剩迁移器视为空的 '{}' 一类值，逐条核对）
SELECT COUNT(*) FROM account WHERE phone IS NOT NULL AND phone REGEXP '^1[0-9]{10}$' AND phone_hash IS NULL;  -- 应为 0
```

### 4.4 回滚方式

| 范围 | 脚本 | 要点 |
|---|---|---|
| V88–V89（加密、索引、dish.created_by） | `menu-api/sql/rollback/V88-V89_privacy.sql` | **先**用新版本 jar 跑 `--gudu.field-crypto.migrate=decrypt`（需相同密钥，含历史版本）还原明文；再执行脚本，第一条 SELECT 的 3 个计数必须全 0；最后部署旧版本应用。`consent_record` 保留 |
| V75–V79（多家庭） | `menu-api/sql/rollback/V75-V79_multi_tenant_family.sql` | 迁移后新建的账号 / 家庭数据会丢失家庭归属；合并过的账号微信登录会落回旧 member 行；执行前备份并先演练 |
| V90（邀请有效期） | `menu-api/sql/rollback/V90_menu_invite_expiry.sql` | 删除 `menu_invite` 的 `expires_at`、`revoked` 两列 |
| V91（id 段、通知收件人） | `menu-api/sql/rollback/V91_id_space_notification_recipient.sql` | `account.AUTO_INCREMENT` 不回退；须同时回退应用版本；账号 / 档案级区分不可逆 |
| V80、V83–V87 | 无回滚脚本 | 均为新增表 / 列，旧版本不读，保留无副作用 |

回滚一律：停新版本应用 → 执行对应步骤 → 部署旧版本应用。V75–V79、V88–V89、V90、V91 的回滚脚本末尾都会删除 `schema_migration_log` 里的对应登记（以脚本内容为准），之后重新部署才会重跑这些迁移。

---

## 五、提审前真机验证清单（体验版，非开发者工具）

- [ ] 上传包来自新构建：`cd menu-mini && npm run build:mp-weixin`，`ENV='prod'`，未设置 `VITE_API_BASE`；`dist/build/mp-weixin/app.json` 里没有 community、Voice，tabBar 为 4 项
- [ ] 冷启动已登录：杀掉小程序重新打开，**直接进首页**，不停在登录页
- [ ] 分享链接登录后回跳：用分享卡片（菜谱、食集、采购清单）在未登录状态打开，登录后回到目标页而不是首页
- [ ] 邀请卡片冷启动：家庭邀请卡片在小程序已被杀掉时点开，登录后进入加入家庭流程
- [ ] 解散家庭：弹出需要**输入家庭名称**确认的弹窗，输错不能解散
- [ ] 首次引导：新微信用户首次登录后出现资料完善（头像、昵称，可跳过）和家庭引导
- [ ] 单独同意弹窗：首次保存成员健康档案（身高体重、过敏、特殊人群、宝宝）时弹出单独同意；拒绝则不保存；成员档案页可撤回
- [ ] 登录页**没有**「账号密码登录」入口，只有「微信一键登录」；未勾选协议点登录有提示且不发起登录
- [ ] 导出链接复制：「我的 → 账号与安全 → 导出我的数据」，链接已复制到剪贴板，能在浏览器下载（30 分钟有效）
- [ ] 厨房圈与语音入口不可见：tab、食记「晒到厨房圈」、咕嘟页麦克风、跟做模式的语音提示都没有
- [ ] 隐私弹窗：首次触发相册 / 相机 / 保存到相册 / 剪贴板时，微信系统隐私弹窗正常出现；点「拒绝」后页面有明确提示，不是无反应（选图失败已统一提示，`utils/chooseImage.ts`）
- [ ] AI 标识：咕嘟对话、跟做模式问咕嘟、拍照识别结果、一周餐程均可见「AI 生成」；点「反馈」进入意见反馈页且类型为 AI
- [ ] AI 对话删除：咕嘟对话页右上角「⋯」→ 删除本对话 / 清空全部对话记录，二次确认后记录消失
- [ ] 注销、合并手机号账号入口可用（账号与安全页）；合并需输入手机号和密码
- [ ] 封禁账号登录或写操作弹出原因并回登录页（后台封禁一个测试号验证）

---

## 六、安全自查：默认管理员口令（只写方法，**不要在此文档里直接执行**）

### 6.1 事实

- `menu-api/sql/V01__init_auth.sql` 种子账号 `admin / admin123`（BCrypt `$2a$10$UM6Ql8.rTKA1aGBSup2ACu4ol4WikGIlfyAPPSciymgdPMpWqa/Ba`）；
- `V29` 把它迁到 `member`（`phone='admin'`、`is_admin=1`）；`V75/V77` 起凭证迁到 `account` 表，`platform_role='SUPER_ADMIN'`；
- 测试代码与 E2E 也用 `admin/admin123` 登录 staging，说明 staging 上口令就是默认值。**生产必须改。**
- `admin` 这个占位登录名属于非个人信息，字段加密迁移会保持明文。

### 6.2 检查（生产库只读查询，由用户在运维窗口执行）

```sql
-- 1) 超管 / 运营账号及其口令是否仍是种子值（is_default = 1 即未改）
SELECT id, phone, platform_role, status,
       password_hash = '$2a$10$UM6Ql8.rTKA1aGBSup2ACu4ol4WikGIlfyAPPSciymgdPMpWqa/Ba' AS is_default
FROM account
WHERE platform_role IN ('SUPER_ADMIN', 'OPERATOR') OR phone = 'admin';

-- 2) 旧 member 表里遗留的凭证列（V75 后不再使用，但可能仍保存着种子哈希）
SELECT id, phone, is_admin,
       password_hash = '$2a$10$UM6Ql8.rTKA1aGBSup2ACu4ol4WikGIlfyAPPSciymgdPMpWqa/Ba' AS is_default
FROM member WHERE phone = 'admin' OR is_admin = 1;
```

另外在登录页用 `admin/admin123` 试登录管理后台（应失败），并确认后台域名没有对公网裸露（nginx 访问控制 / 仅内网或 IP 白名单）。

### 6.3 修改（二选一，推荐 A）

**A. 用接口改，不接触数据库**：用 admin 账号登录管理后台（或调用接口）后调用 `PUT /gudu/account/password`，body：`{"oldPassword":"admin123","newPassword":"<新强口令>"}`。后端只要求 6–64 位（`Account.checkPassword`），建议用 16 位以上随机字符。

**B. 直接写库**（接口不可用时）：本机生成 BCrypt 哈希，再更新 `account.password_hash`：

```bash
htpasswd -bnBC 10 "" '<新强口令>' | tr -d ':\n' | sed 's/^\$2y/\$2a/'
```
```sql
UPDATE account SET password_hash = '<上面生成的哈希>' WHERE phone = 'admin' AND platform_role = 'SUPER_ADMIN';
```
同时把 `member` 表遗留行的 `password_hash` 一并更新或置空（6.2 第 2 条）。

改完后：重新跑 6.2 的查询确认 `is_default = 0`；用新口令登录后台；检查后台操作日志里没有陌生登录。建议再新建一个个人实名的 SUPER_ADMIN 账号并停用 `admin` 占位名（可选）。
