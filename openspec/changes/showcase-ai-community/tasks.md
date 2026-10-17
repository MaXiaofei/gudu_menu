# Implementation Tasks: 咕嘟 Agent、厨房圈与 AI 能力扩展

**Change ID:** `showcase-ai-community`

## S0 原型与设计
- [x] 0.1 原型导航改版：5 个主 Tab 改为 菜谱 / 食集 / 咕嘟 / 厨房圈 / 我家余量，「我的」移到右上角头像；Smart 改为二级页
- [x] 0.2 咕嘟 Agent 原型：Agent、AgentChat、AgentVoice
- [x] 0.3 厨房圈原型：Community、Post、PostCreate、Topic
- [x] 0.4 AI 能力原型：Scan、ScanResult、WeekPlan、Nutrition、CookMode
- [x] 0.5 后台原型：AdminAgentTrace、AdminAgentEval、AdminCommunity
- [x] 0.6 技术架构总览 Architecture.html（面试讲解用）
- [x] 0.7 index.html / all.html 收录新页面；全部页面零坏链、零外链
- [ ] 0.8 用户评审 design.md 与原型

## S1 Agent 只读
- [ ] 1.1 `AiClient` 支持 tools / function calling（DeepSeek；GLM 降级）+ 单测
- [ ] 1.2 `agent` 上下文四层骨架；ToolRegistry；只读工具 6 个
- [ ] 1.3 SSE 事件协议（token / tool_start / tool_end / card / done）
- [ ] 1.4 agent_trace / agent_span 落表；V7x 迁移 + e2e-seed 清理
- [ ] 1.5 Flutter / 小程序：咕嘟首屏 + 对话页 + 卡片渲染；导航改版
- [ ] 1.6 E2E：会话创建、流式消息、工具调用越权（跨家庭 403）

## S2 Agent 写操作
- [ ] 2.1 PendingAction（Redis TTL）+ confirm / cancel；undoToken
- [ ] 2.2 写工具 4 个（加食集 / 加采购 / 做完了 / 入库）
- [ ] 2.3 客户端确认卡与撤销
- [ ] 2.4 后台 AdminAgentTrace

## S3 厨房圈
- [ ] 3.1 community 上下文：post / comment / reaction / follow / topic / report
- [ ] 3.2 Feed（推荐 / 关注 / 话题）+ 游标分页 + Redis 计数回刷
- [ ] 3.3 照着做 → 复制到我家
- [ ] 3.4 内容治理：同步检查 + 异步 AI 预审 + 举报阈值；后台 AdminCommunity
- [ ] 3.5 客户端：Feed、详情、发布（含从食记分享）、话题

## S4 拍照识别与跟做
- [ ] 4.1 视觉供应商选型（需用户决策）
- [ ] 4.2 vision_job + 三种模式 + 字典对齐 + 置信度分级
- [ ] 4.3 客户端 Scan / ScanResult
- [ ] 4.4 跟做模式：大字步骤、常亮、多计时器、语音关键词、步骤内问 Agent

## S5 餐程、营养与评测
- [ ] 5.1 mealplan：规则过滤 → LLM 排布 → 校验回环；换一道；一键生成食集 + 采购
- [ ] 5.2 营养报告接口 + 客户端
- [ ] 5.3 评测集、评测运行、发布门禁；后台 AdminAgentEval

## Quality Gate
- [ ] 四件套全绿（功能 + 后端 E2E + Flutter + 小程序）
- [ ] ArchUnit：新上下文零违规
- [ ] Agent 评测集工具正确率 ≥ 90%
