# Delta: Operations（发布、备份与观测）

**Change ID:** `production-ops-baseline`

## ADDED

### Requirement: 测试门禁与审批发布
生产发布必须经过 CI 全绿与人工审批，并按不可变镜像 tag 部署。

#### Scenario: 测试失败
- GIVEN PR 中后端单测失败
- WHEN 合并到 main
- THEN 不构建发布镜像，不部署任何环境

#### Scenario: 回滚
- GIVEN 生产当前为 sha B，上一版本为 sha A
- WHEN 执行 `deploy.sh rollback A` 或 B 健康检查失败
- THEN 服务切回 A 镜像，2 分钟内恢复

### Requirement: 每日备份与恢复演练
数据库、向量库与上传文件每日加密备份到对象存储，每月自动恢复演练。

#### Scenario: 备份失败
- WHEN 当日备份任务失败
- THEN 企业微信告警，含失败步骤

### Requirement: 迁移版本可追溯
数据库结构变更由 Flyway 管理，任意环境可查询当前版本。

#### Scenario: 重复部署
- WHEN 同一版本连续部署两次
- THEN 第二次不执行任何迁移

### Requirement: 生产不暴露接口文档与内部端点
#### Scenario: 访问 Swagger
- WHEN 公网请求 `/gudu/swagger-ui/index.html` 或 `/actuator/**`
- THEN 返回 404
