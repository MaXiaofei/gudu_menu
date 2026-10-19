# 咕嘟小食单 · 生产化路线图（OpenSpec）

> **目标**：从「单家庭自用 MVP」演进为**对外开放、多用户多家庭、生产级**的成熟产品，后端以领域模型（DDD）建模。
> **起点评估**（2026-10-16 全栈审计）：功能完整度 ~80%，生产就绪度 ~45%。安全 2/10、运维 3/10、上架准备 3/10。
> **已确认的产品决策**（2026-10-16）：
> 1. 菜谱 = **平台公共菜谱库 + 家庭私有菜谱**（公共菜谱可收藏、可「复制到我家」改编）
> 2. 账号可**加入多个家庭并切换**；家庭内角色 OWNER / ADMIN / MEMBER；无账号的人（宝宝等）作为家庭「成员档案」
> 3. 存量数据：**菜谱进公共库，其余进「默认家庭」**，现有成员全部加入默认家庭
> 4. 推进顺序：**安全止血 → 多租户 + DDD → 补功能缺口 → 运维基线 → 视觉收敛**
> 5. 测试/本地开发环境保持放开（端口/鉴权不收紧），仅生产收紧

## Change 清单与依赖

| # | Change ID | 主题 | 依赖 | 状态 |
|---|-----------|------|------|------|
| 1 | [`security-hardening`](changes/security-hardening/proposal.md) | P0 安全止血（鉴权、提权、SSRF、部署重跑 SQL、端口、小程序线上 bug） | — | Draft |
| 2 | [`backend-ddd-architecture`](changes/backend-ddd-architecture/proposal.md) | 后端 DDD 四层 + 领域事件 + ArchUnit 守护（Phase 0 已完成） | — | In Progress |
| 3 | [`multi-tenant-family`](changes/multi-tenant-family/proposal.md) | 账号 / 家庭 / 成员档案领域模型 + 全量租户隔离 + 公共菜谱库 | 1, 2(Phase 1 基建) | Draft |
| 4 | [`feature-gaps-closure`](changes/feature-gaps-closure/proposal.md) | 补齐功能缺口（单菜直做、临期主推、余量覆盖率、最近做过、各端占位功能） | 3 | Draft |
| 5 | [`production-ops-baseline`](changes/production-ops-baseline/proposal.md) | Flyway、CI 门禁、备份、监控、回滚、nginx、App/小程序上架合规 | 1 | Draft |
| 6 | [`design-system-convergence`](changes/design-system-convergence/proposal.md) | 三端视觉与设计系统收敛（token 化、共享组件、状态页、无障碍） | 4（页面稳定后） | Draft |
| 7 | [`prototype-ui-rollout`](changes/prototype-ui-rollout/proposal.md) | 按高保真原型（`docs/design/prototype/`）改版四端：P0 素材与组件 → P1 主链路 → P2 家庭 → P3 生产级能力 | 6(组件)、3(P2) | Draft |
| 8 | [`recipe-content-seed`](changes/recipe-content-seed/proposal.md) | 下厨房导入数据可重复种子脚本 + staging 演示家庭（**仅测试环境**，生产硬拒绝） | — | Draft |
| 9 | [`showcase-ai-community`](changes/showcase-ai-community/proposal.md) | 咕嘟 Agent（对话操作 App）、厨房圈社区、拍照识别、一周餐程 + 营养报告、跟做模式、Agent 可观测与评测；导航改为 菜谱/食集/咕嘟/厨房圈/余量 | 3、7(P0)、2 | Draft |
| — | [`cookbook-search-revamp`](changes/cookbook-search-revamp/proposal.md) | 历史 change：App 菜谱页重构 | — | 待归档核对 |

```
security-hardening ──► multi-tenant-family ──► feature-gaps-closure ──► design-system-convergence
        │                      ▲
        │      backend-ddd-architecture (Phase 1 基建先行，新上下文原生四层)
        └────► production-ops-baseline（可与 3/4 并行）
```

## 每个 change 的统一交付标准（Definition of Done）

1. **四件套**（见 CLAUDE.md）：功能代码 + 后端 E2E + Flutter 测试 + 小程序测试，全部跑绿
2. 后端：`./mvnw test` 全绿（含 ArchUnit 守护），跨模块 Mapper 冻结基线**只减不增**
3. 新增/变更的接口：Swagger 文档 + 越权测试（跨家庭访问必须 403/404）
4. 迁移脚本：Flyway 管理（change 5 落地前沿用幂等 SQL），附回滚说明
5. tasks.md 全部勾选 → spec delta 合入 `openspec/specs/<capability>/spec.md` → change 移入 `openspec/changes/archive/`
