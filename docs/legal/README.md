# 法律文本

- `terms.md` 用户协议、`privacy.md` 隐私政策。运营者：马晓飞（个人开发者），联系邮箱 maxiaofeidev@gmail.com。无法务审阅，按个人信息处理者为个人的口径起草（2026-10-26）。
- 三端引用位置：menu-mini `src/static/legal/*.ts`（`node scripts/sync-legal.mjs` 生成）、menu-flutter `assets/legal/`（同一脚本复制）；修改本目录后必须同步，并升级 menu-mini `src/utils/consent.ts` 的 `LEGAL_VERSION`，老用户需重新同意。
- 数据清单变更（新增收集项 / 设备权限 / 第三方 / AI 服务商 / 保存期限）必须同步更新 `privacy.md` 第二、三、五、六节。

## ⚠️ 发布前置条件

文本按**正式版上线时的目标状态**撰写，以下承诺依赖尚未完成的任务，**全部完成前不得作为正式版发布**：

| 承诺（位置） | 依赖任务 |
|---|---|
| 按家庭隔离数据访问权限（privacy §六.3） | multi-tenant-family |
| 注销账号（privacy §七.6、terms §二.5） | multi-tenant-family E.6 |
| 账号合并（privacy §七.5、terms §二.2） | multi-tenant-family E.7 |
| 健康档案与儿童信息单独同意（privacy §二、§四） | mini-release-compliance B.2 |
| 同意记录（privacy §二） | mini-release-compliance B.1 |
| 健康档案、手机号加密存储（privacy §六.3） | mini-release-compliance B.3 |
| 发给模型的成员称呼用代号（privacy §三.1） | mini-release-compliance A.5 |
| 「AI 生成」标识（privacy §三.4、terms §五.1） | mini-release-compliance A.4 |
| 内容安全检测（privacy §三.5、terms §三） | mini-release-compliance A.6 |
| 关闭个性化推荐（privacy §三.6） | mini-release-compliance A.8 |
| 签名链接访问上传文件、去除图片元数据（privacy §二、§六.3） | mini-release-compliance A.3 |
| AI 对话 180 天、反馈 1 年自动删除（privacy §三.3、§六.2） | mini-release-compliance C.1 |
| 导出我的数据（privacy §七.4） | mini-release-compliance C.2 |
| 协议只覆盖小程序：厨房圈、语音不在小程序提供 | mini-release-compliance A.1、A.9 |
| 聚餐需登录（privacy §二） | mini-release-compliance A.10 |
| 意见反馈答复经「消息与提醒」通知（privacy §九） | mini-release-compliance A.11 |

范围：2026-10-26 起协议只覆盖**微信小程序**；App 上架时需扩写并升级 LEGAL_VERSION。

另需人工核对：第三方公司全称（尤其智谱）以对方官网和合同为准；AI 服务若接入第三方语音识别，需更新 privacy §三.2。
