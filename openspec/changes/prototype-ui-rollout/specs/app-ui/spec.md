# Delta: App UI（四端界面）

**Change ID:** `prototype-ui-rollout`

## ADDED

### Requirement: 有图的位置必须显示图片
菜谱卡片、食集、智荐、食记、点菜、后台菜谱表格显示菜品图；用料、余量、备菜、采购显示食材插画；成员显示头像。

#### Scenario: 菜谱有封面
- GIVEN 菜谱 coverUrl 非空
- WHEN 打开菜谱列表
- THEN 该行显示封面缩略图（`/thumbnail/` 版本），不显示首字占位

#### Scenario: 封面加载失败
- GIVEN 菜谱 coverUrl 指向 404
- WHEN 打开菜谱列表
- THEN 该行回退为首字占位（secondary 底），不留白、不显示破图

#### Scenario: 食材插画
- GIVEN 余量中「番茄」iconKey=tomato，「秋葵」iconKey 为空且品类为蔬菜
- WHEN 打开我家余量
- THEN 番茄显示番茄插画；秋葵显示蔬菜品类兜底插画

### Requirement: 数据页三态
每个数据页有骨架屏、空态（插画 + 下一步动作）、错误态（重试 + 报错编号）。

#### Scenario: 接口失败
- GIVEN `/pantry` 返回 500 且响应头 X-Trace-Id=7f3a91c2…
- WHEN 打开我家余量
- THEN 显示「没加载出来」、重试按钮和报错编号 7f3a-91c2；点「意见反馈」时自动附带该编号

### Requirement: 离线不挡操作
离线时顶部显示离线条，已缓存数据可看，写操作排队。

#### Scenario: 离线改余量
- GIVEN 设备离线
- WHEN 把番茄改为「用完」
- THEN 页面立即更新并标「待同步」；联网后自动提交，离线条消失

### Requirement: 食集详情四个 Tab
食集详情包含 菜 / 备菜 / 采购 / 一起吃。

#### Scenario: 采购在食集内
- WHEN 打开食集详情并切到「采购」
- THEN 按「家里没有 / 有但不够 / 家里够」分组显示本食集用料，可「买到的放进余量」

### Requirement: 登录前协议同意
用户勾选协议前不发起任何登录请求（含小程序静默登录）。

#### Scenario: 未勾选
- GIVEN 首次打开小程序
- WHEN 未勾选协议
- THEN 不调用 `wx.login` 与 `/auth/wx-login`；登录按钮点击时提示先勾选

### Requirement: 注销冷静期
申请注销后 15 天内登录可撤回，到期删除账号与个人资料。

#### Scenario: 冷静期内撤回
- GIVEN 用户 10-01 申请注销
- WHEN 10-10 登录并点「撤回注销」
- THEN 注销申请取消，账号正常

#### Scenario: 家庭主人注销
- GIVEN 用户是某家庭唯一主人且家庭有其他成员
- WHEN 申请注销
- THEN 提示先转让家庭，申请不成立
