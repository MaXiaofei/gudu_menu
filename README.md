# 烟火小食单

家里掌勺的那个人，每天得琢磨「今天给全家做什么」。家里要是有三高的老人、要加辅食的宝宝、或者有人忌口，光定菜单还不够，还得惦记每顿的营养对不对得上。

烟火小食单就是给这个场景做的：一个家庭菜谱、菜单加营养管理的小后台。把家里的菜、食材的营养数据、每个人的健康情况录进去，排菜单的时候能自动算出总价和营养汇总，也能照应到高血压、高血糖、高血脂、宝宝辅食这些特殊人群的饮食约束。

名字取自家常菜里的「烟火气」。

## 这一版（MVP）能做什么

纯手工录入，不接 AI：

- 账号登录
- 家庭成员 + 健康档案（身高体重、忌口、特殊人群、营养上限）
- 配置中心：菜系、分类、标签、特殊人群、计量单位、采购品类等几类字典
- 食材库 + 营养数据（每 100g 的热量 / 蛋白 / 脂肪 / 碳水 / 糖 / GI）
- 菜品：步骤图文、菜系标签分类多对多、食材用量、按份数缩放、历史版本
- 菜单：手动排菜 + 设份数，自动汇总总价和营养
- 菜库：收藏、做过、按菜系 / 标签 / 烹饪时间 / 难度搜索
- 烹饪记录
- 数据全量备份 / 恢复

后面会再加小程序端，以及基于智谱 GLM 的 AI 菜谱生成。

## 技术栈

| | |
|---|---|
| 后端 `menu-api/` | Java 17、Spring Boot 3.2、MyBatis-Plus、MySQL 8、Redis、Sa-Token、Knife4j |
| 前端 `menu-admin/` | Vue 3、TypeScript、Vite、Element Plus、Pinia、Axios、ECharts |
| 依赖服务 | MySQL / Redis / MinIO，用根目录 `docker-compose.yml` 起 |

## 目录结构

```
menu-new/
├── menu-api/        后端
├── menu-admin/      Web 管理后台
├── docs/            设计文档和实现计划
└── docker-compose.yml
```

## 本地开发

先起依赖服务：

```bash
docker compose up -d
```

数据源和 Redis 地址在 `menu-api/src/main/resources/application-dev.yml`，按自己的环境改一下（仓库里这份连的是开发服务器的地址）。

后端：

```bash
cd menu-api
./mvnw spring-boot:run
```

接口文档：http://localhost:8080/doc.html

前端：

```bash
cd menu-admin
npm install
npm run dev
```

跑在 http://localhost:5173 ，`/api` 请求会代理到后端 8080。

## 文档

- 设计文档：`docs/superpowers/specs/2026-06-16-yanhuo-xiaoshidan-design.md`
- 实现计划：`docs/superpowers/plans/2026-06-17-yanhuo-mvp.md`
