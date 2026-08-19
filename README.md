# 咕嘟小食单

家里掌勺的那个人，每天得琢磨「今天给全家做什么」。

咕嘟小食单就是给这个场景做的：一个家庭菜谱、菜单管理小后台。把家里常做的菜录进去，排菜单、管库存、生成采购清单，让备菜采购心里有数。

名字取自小火慢炖时锅里「咕嘟」的声。

## 在线体验

- 管理后台：https://imxf.cloud
- 接口文档：https://imxf.cloud/gudu/doc.html

## 这一版（MVP）能做什么

Web 管理后台 + 小程序端（H5）+ App 端三个入口：

- 账号登录
- 配置中心：菜系、分类、标签、计量单位、采购品类等字典
- 食材库
- 菜品：步骤图文、食材用量
- 烹饪记录
- 食材库存管理
- 采购清单

小程序端（H5）还提供：

- 菜谱浏览与详情
- AI 菜单推荐（「为你推荐」）
- 餐后点评
- 聚餐协同点菜
- 饮食日志
- 食材、库存与采购清单
- 家庭成员与个人中心

App 端还提供：

- AI 菜单推荐（描述口味 → 找菜 + 组合推荐）
- 菜谱浏览与维护（列表 / 详情 / 新建 / 草稿）
- 食集（菜单）与烹饪确认
- 食材、库存与采购清单
- 饮食日志
- 餐后点评
- 家庭成员与个人中心

## 规划中

- 食材营养数据与菜单营养汇总
- 家庭成员档案管理（身高体重、过敏等标签）
- 特殊人群饮食约束（高血压、高血糖、高血脂、宝宝辅食等）
- 菜品历史版本
- 菜品按份数缩放
- 菜单计划（模板一键套用）
- 数据全量备份 / 恢复

## 技术栈

| | |
|---|---|
| 后端 `menu-api/` | Java 17、Spring Boot 3.2、MyBatis-Plus、MySQL 8、Redis、Sa-Token、Knife4j |
| 管理后台 `menu-admin/` | Vue 3、TypeScript、Vite、Element Plus、Pinia、Axios、ECharts |
| 小程序端 `menu-mini/` | uni-app（Vue 3）+ uview-plus，H5 |
| App 端 `menu-flutter/` | Flutter |
| 依赖服务 | MySQL / Redis / MinIO，用根目录 `docker-compose.yml` 起 |

## 目录结构

```
menu-new/
├── menu-api/          后端
├── menu-admin/        Web 管理后台
├── menu-mini/         小程序端（H5）
├── menu-flutter/      App 端
├── nginx/             nginx 配置与模板
├── scripts/           部署与数据脚本
├── docs/              设计文档和实现计划
├── openspec/          产品规格
└── docker-compose*.yml  各环境编排（test / staging / prod）
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

接口文档：http://localhost:8080/gudu/doc.html

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
