# Delta: Theming（换肤与 design token）

**Change ID:** `design-system-convergence`

## ADDED

### Requirement: 皮肤单一来源
所有皮肤定义在 `docs/design/tokens.json` 的 `color.theme`，三端代码由生成器产出，不得手抄色值。

#### Scenario: 新增一套皮肤
- GIVEN 设计在 tokens.json 新增 `ocean` 皮肤（字段与现有皮肤一致）
- WHEN 运行 `node scripts/design/gen-tokens.mjs`
- THEN 小程序、App、管理后台的主题选择列表都出现该皮肤，无需改业务代码

#### Scenario: 产物过期
- GIVEN 有人改了 tokens.json 但没重新生成
- WHEN CI 运行 `gen-tokens.mjs --check`
- THEN 构建失败并提示过期文件

### Requirement: 皮肤可读性门禁
每套皮肤必须通过 WCAG 对比度校验后才能生成。

#### Scenario: 正文对比度不足
- GIVEN 某皮肤 body 色在 bg 上对比度 < 4.5
- WHEN 运行生成器
- THEN 拒绝生成并指出字段与实际对比度

### Requirement: 换肤即时生效且持久化
用户在「我的 → 主题外观」切换皮肤后，当前页面与后续页面立即使用新皮肤，重启后保持。

#### Scenario: 切换后重启
- GIVEN 用户选择「抹茶禅意」
- WHEN 关闭并重新打开应用
- THEN 仍为抹茶禅意（含状态栏、tabBar 选中色）

#### Scenario: 旧版本偏好兼容
- GIVEN 管理后台 localStorage 存的是旧 key `green`
- WHEN 打开新版后台
- THEN 识别为抹茶禅意
