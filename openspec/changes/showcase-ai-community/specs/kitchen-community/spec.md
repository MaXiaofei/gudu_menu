# Delta: Kitchen Community（厨房圈）

**Change ID:** `showcase-ai-community`

## ADDED

### Requirement: Feed 使用游标分页
推荐、关注、话题三种 Feed 都按 `(score, id)` 游标分页，翻页过程中新发布内容不导致重复或遗漏。

#### Scenario: 翻页期间有新帖
- GIVEN 用户已加载推荐流第一页
- WHEN 期间有 3 条新帖发布，用户继续上拉
- THEN 第二页与第一页无重复帖

### Requirement: 点赞幂等与计数最终一致
同一用户对同一帖子点赞只记一次；计数在 Redis 累加并定时回刷数据库。

#### Scenario: 重复点赞
- GIVEN 用户已赞过帖子 P
- WHEN 客户端因重试再次提交点赞
- THEN 点赞数不变，接口返回成功

### Requirement: 照着做复制到我家
用户可把帖子关联的菜谱复制为家庭私有菜谱。

#### Scenario: 复制
- GIVEN 帖子关联公共菜谱 D
- WHEN 用户点「照着做」
- THEN 当前家庭新增一条源自 D 的私有菜谱；帖子「照着做」数 +1；作者收到通知

### Requirement: 分级内容治理
发布时同步检查敏感词与外链，异步 AI 预审分级；举报达阈值自动隐藏。

#### Scenario: 高风险内容
- GIVEN AI 预审判定为高风险
- WHEN 预审完成
- THEN 帖子对他人不可见，进入后台人工审核队列，作者看到「审核中」

#### Scenario: 举报阈值
- GIVEN 帖子已被 2 个不同用户举报
- WHEN 第 3 个不同用户举报
- THEN 帖子自动隐藏并进入人工复审

### Requirement: 分享食记不泄露家庭信息
从食记分享到厨房圈时不带成员名与余量信息。

#### Scenario: 分享食记
- GIVEN 食记中标注「朵朵、妈妈吃了」
- WHEN 用户一键分享
- THEN 生成的帖子只包含菜品、照片与用户填写的文字
