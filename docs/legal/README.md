# 法律文本（草稿）

- `terms.md` 用户协议、`privacy.md` 隐私政策：**草稿，上线前须由运营主体核对并建议法务审阅**。
- 待填占位：`【运营主体】`（公司全称或个人姓名）、`【联系邮箱】`、`【生效日期】`、`【注册地址】`。
- 三端引用位置：menu-mini `src/static/legal/`、menu-flutter `assets/legal/`；修改本目录后需同步。
- 数据清单变更（新增收集项 / 新接入第三方 SDK / AI 服务商）必须同步更新 `privacy.md` 第二、四节。

## ⚠️ 发布前置条件
文本中以下承诺依赖尚未上线的能力，**`multi-tenant-family` 上线前不得作为正式版发布**：
- 「按家庭隔离数据访问权限」（privacy §五.3）
- 「注销账号」入口与流程（privacy §六.4、terms §二.3）
- Sentry 错误上报（privacy §四，随 `production-ops-baseline` 5.3 接入）
