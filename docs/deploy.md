# 咕嘟小食单 · 部署文档

## 〇、环境划分

项目有两套独立环境，**互不混淆**：

| 环境 | 位置 | 用途 | 访问地址 | 项目目录 | 部署方式 |
|------|------|------|----------|----------|----------|
| **测试环境** | 腾讯云 `49.232.3.201`（与生产同机） | 日常部署验证、联调 | `http://49.232.3.201:9090` | `/root/gudu-staging/` | `docker compose -p gudu-staging -f docker-compose.staging.yml up -d --build` |
| **生产环境** | 腾讯云 `49.232.3.201` | 正式上线 | **http://49.232.3.201** | `/root/gudu/` | `docker compose -f docker-compose.prod.yml up -d --build` |

> **部署规则**：说「部署到测试」→ 部署到 `/root/gudu-staging/`；说「上生产」→ 部署到 `/root/gudu/`。两者在同一台服务器，但通过**独立 project/网络/容器名/端口**完全隔离（见下）。

> **数据库规则**：两套环境都**保留现有数据，不清库**。除非明确说「清库」并经二次确认，否则禁止 `down -v` 或 DROP DATABASE。

> **⚠️ compose 文件区分**：测试用 **`docker-compose.staging.yml`**（HTTP，独立 project=gudu-staging，menu-admin 直挂 9090，无 front-nginx/certbot）；生产用 **`docker-compose.prod.yml`**（HTTPS，front-nginx + certbot，80/443）。两者 mysql/redis/menu-api 用各自的网络与卷，**互不影响**，一套 down 不影响另一套。

> **⚠️ 同机隔离要点**（测试与生产在同一台 49.232.3.201）：
> | 维度 | 生产 | 测试 | 冲突 |
> |------|------|------|------|
> | project name | `gudu`（目录名） | `gudu-staging`（`-p` 指定） | ✅ 错开 |
> | Docker 网络 | `gudu_default` | `gudu-staging_net`（显式命名） | ✅ 错开 |
> | 容器名 | `*-prod` / `gudu-nginx` 等 | `*-staging` | ✅ 错开 |
> | 数据卷 | `gudu_mysql-data` | `gudu-staging_mysql-data` | ✅ project 前缀天然隔离 |
> | 宿主端口 | 80/443/3306/6379 | 9090/13306/16379 | ✅ 全错开 |
> ⭐ menu-api 通过 service name（`gudu-mysql`/`gudu-redis`）连库，service name 解析依赖所在 Docker 网络。两套环境网络隔离，故同一份镜像、同一份 `application-prod.yml` 可零改动跑两套，各自连各自网络的 db。

> **密钥/口令**统一放 `.env.dev`（本地、已被 `.gitignore` 忽略），本文档不写明文密钥。
>
> ### .env 文件管理规则（重要）
> | 文件 | 位置 | 用途 | git |
> |------|------|------|-----|
> | `.env.example` | 项目根 | 变量模板，全部 `<>` 占位符，**绝无真实值** | ✅ 提交 |
> | `.env.dev` | 项目根（本地 Mac） | 真实密钥/密码，运维脚本从此读取 | ❌ gitignore |
> | `.env` | 服务器 `/root/gudu/.env` | **唯一共享真相源**，含生产+测试全部变量 | ❌ 服务器 |
>
> **服务器上**：`/root/gudu/.env` 是唯一环境变量文件，`/root/gudu-staging/.env` 是它的软链接。两个 docker compose 各自通过本目录的 `.env`（或链接）读取同一份变量。禁止在服务器创建独立的 `.env` 副本。
>
> **本地 `.env.dev`**：必须与服务器 `/root/gudu/.env` 保持一致（变量名和值），新增变量两边同步。`.env.example` 同步新增变量的占位符。

---

## 一、测试环境（与生产同机隔离）

> ### ⏸️ 当前状态：staging 已封存（2026-10-22）
>
> 容器已全部停止（`docker compose -p gudu-staging down` + nacos-staging 同停），**数据卷完整保留**（`gudu-staging_mysql-data / pgvector-data / ollama-data（含 bge-m3 模型）/ uploads` 及 `nacos-staging_*`）。deploy.yml 已移除 feat/mvp 的 push 触发，冻结期间推代码不会自动部署；staging.imxf.cloud 无法访问（超时）属预期（证书照常续期）。
>
> **恢复步骤（两步）：**
> 1. GitHub Actions 页面手动触发 Deploy 工作流，ref 选 `feat/mvp`（或把 `feat/mvp` 加回 deploy.yml 的 `on.push.branches` 恢复常态自动部署）；
> 2. 服务器执行 `cd /root/gudu-staging && git pull && docker compose -p gudu-staging -f docker-compose.staging.yml up -d`（nacos-staging 同理 up）。
>
> deploy.sh 会自动构建最新代码、按 `schema_migration_log` 补齐冻结期间的增量迁移、健康检查，无需手动建任何东西。
> ⚠️ 封存期间清理服务器时，**不要删 `gudu-staging_*` / `nacos-staging_*` 数据卷**。

### 1.1 服务器信息

| 项目 | 值 |
|------|-----|
| 公网 IP | `49.232.3.201`（与生产同机） |
| 系统 | CentOS Linux 7 (Core) |
| 架构 | x86_64 |
| SSH 用户 | `root`（密码见 `.env.dev` 的 `STAGING_SSH_PASS`，与生产同机同账号） |
| 项目目录 | `/root/gudu-staging/`（**独立于生产 `/root/gudu/`**） |
| 备份目录 | `/root/gudu-staging/backups/` |
| 数据卷 | `gudu-staging_mysql-data`（**全新空库，与生产卷 `gudu-deploy_mysql-data` 隔离**） |
| 网络 | 与生产服务器一致（腾讯云，有外网，docker build 正常） |

### 1.2 容器与端口

| 容器（container_name） | 端口(宿主机) | 说明 |
|------|--------------|------|
| `menu-api-staging` | 8080(容器内) | 后端，仅 staging-net 内可达，经 nginx 代理 |
| `menu-admin-staging` | `9090->80` | 前端 Nginx（测试后台入口：http://49.232.3.201:9090） |
| `menu-mini-staging` | `9091->80` | 小程序 H5（测试小程序入口：http://49.232.3.201:9091） |
| `gudu-mysql-staging` | `13306->3306` | MySQL 8.0，库 `gudu`，密码见 `.env`（与生产隔离） |
| `gudu-redis-staging` | `16379->6379` | Redis 7（与生产隔离） |

> 测试环境**未配置 HTTPS**（无 certbot），走 HTTP。`GUDU_AI_PROVIDER` 默认 `mock`（测试环境既定状态，不烧 key；如需真实 AI，配 `STAGING_DEEPSEEK_API_KEY` 并改 `STAGING_AI_PROVIDER`）。
>
> ⚠️ 所有端口与生产（80/443/3306/6379）错开，互不冲突。容器名统一带 `-staging` 后缀，与生产的 `-prod`/无后缀容器名全局唯一。

### 1.3 SSH 连接与部署

root 直接 SSH（密码见 `.env.dev`）：
```bash
ssh root@49.232.3.201
```

部署（**注意 `-p gudu-staging` 必须带，否则卷名/网络会与生产冲突**）：
```bash
cd /root/gudu-staging
docker compose -p gudu-staging -f docker-compose.staging.yml ps
docker compose -p gudu-staging -f docker-compose.staging.yml up -d --build
```

### 1.4 与生产的隔离验证

部署后建议执行以下命令确认隔离生效（生产容器不应出现在 staging 网络，反之亦然）：
```bash
# 1. 容器名隔离：应看到 *-staging 一组，*-prod / gudu-nginx 另一组，无重名
docker ps --format '{{.Names}}\t{{.Ports}}' | grep -E 'gudu|menu'

# 2. 网络隔离：staging 网络里只有 *-staging 容器
docker network inspect gudu-staging_net --format '{{range .Containers}}{{.Name}} {{end}}'
docker network inspect gudu-deploy_default --format '{{range .Containers}}{{.Name}} {{end}}'

# 3. 卷隔离：两个独立的 mysql-data 卷
docker volume ls | grep mysql-data
```

### 1.5 数据库现状

测试环境为**全新空库**（独立卷 `gudu-staging_mysql-data`），首启自动执行 `./menu-api/sql/V01~V30` 初始化表结构 + demo 数据。与生产数据完全隔离，可随意清库（`docker compose -p gudu-staging -f docker-compose.staging.yml down -v` 后重启即重建）。

### 1.6 ⚠️ 腾讯云安全组

测试环境对外用 9090 端口，需在**腾讯云控制台 → 安全组入站规则**放行：
| 协议 | 端口 | 来源 | 说明 |
|------|------|------|------|
| TCP | 9090 | 0.0.0.0/0 或限定来源 IP | 测试环境前端入口 |

> MySQL(13306)/Redis(16379) 仅容器间通信，**不建议放行公网**；如需本地工具连测试库排查，临时放行并限定来源 IP。

---

## 二、生产环境（腾讯云）

### 2.1 服务器信息

| 项目 | 值 |
|------|-----|
| 公网 IP | `49.232.3.201` |
| 系统 | CentOS Linux 7 (Core) |
| 内核 | 3.10.0-1160.119.1.el7.x86_64 |
| 架构 | x86_64 (KVM 虚拟机) |
| 磁盘 | 59GB (已用 ~7GB) |
| 访问地址 | **http://49.232.3.201** |
| 项目目录 | `/root/yanhuo/` |

### 2.2 运行环境 & 安装位置

### 2.2.1 Git

| 项目 | 值 |
|------|-----|
| 安装方式 | `yum install -y git` |
| 版本 | 1.8.3.1 |
| 用途 | 代码版本管理 |

### 2.2.2 Docker CE

| 项目 | 值 |
|------|-----|
| 安装方式 | 阿里云镜像源 RPM |
| 版本 | 26.1.4 |
| 配置文件 | `/etc/docker/daemon.json` |
| 数据目录 | `/var/lib/docker/` |
| 镜像仓库 | 阿里云 / DaoCloud / 腾讯云等国内镜像加速 |

**`/etc/docker/daemon.json` 配置内容：**
```json
{
  "registry-mirrors": [
    "https://hub.rat.dev",
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me",
    "https://docker.m.daocloud.io",
    "https://dockerproxy.com",
    "https://mirror.ccs.tencentyun.com"
  ]
}
```

修改后执行 `systemctl restart docker` 生效。

### 2.2.3 Docker Compose

| 项目 | 值 |
|------|-----|
| 安装方式 | Docker CE 捆绑安装 (docker-compose-plugin) |
| 版本 | v2.27.1 |
| 命令 | `docker compose` (插件模式，非独立 `docker-compose`) |

### 2.2.4 应用组件（Docker 容器内）

| 组件 | 镜像 | 说明 |
|------|------|------|
| MySQL 8.0 | `mysql:8.0` | 持久化数据卷 `mysql-data` |
| Redis 7 | `redis:7` | 缓存服务 |
| Java 17 JRE | `eclipse-temurin:17-jre` | 后端运行环境 |
| Maven 3.9 + JDK 17 | `maven:3.9-eclipse-temurin-17` | 后端构建环境（仅构建阶段） |
| Node.js 20 | `node:20-alpine` | 前端构建环境（仅构建阶段） |
| Nginx Alpine | `nginx:alpine` | 前端静态文件服务 + API 反代 |

---

### 2.3 目录结构

```
/root/yanhuo/
├── .env                        # 生产环境变量（API_KEY 等）
├── .env.dev                    # 开发环境变量（仅供参考）
├── docker-compose.yml          # 开发环境 compose（仅 DB 依赖，端口映射到宿主机）
├── docker-compose.prod.yml     # ★ 生产环境 compose（全栈一键部署）
├── menu-api/                   # 后端 Spring Boot 项目
│   ├── Dockerfile              # 多阶段构建（Maven → JRE）
│   ├── settings.xml            # Maven 国内镜像（阿里云）
│   ├── pom.xml
│   ├── sql/                    # 数据库迁移脚本（V1~V30，首启自动执行）
│   └── src/
├── menu-admin/                 # 前端 Vue 3 管理后台
│   ├── Dockerfile              # 多阶段构建（Node → Nginx）
│   ├── nginx.conf              # Nginx 配置（SPA 路由 + /api 反代）
│   └── src/
├── menu-mini/                  # uni-app 小程序（不在服务器运行，仅源码）
└── menu-flutter/               # Flutter App（不在服务器运行，仅源码）
```

---

### 2.4 Docker 镜像构建配置

#### 4.1 后端 `menu-api/Dockerfile`

```dockerfile
# === 构建阶段：Maven 编译 ===
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY settings.xml /root/.m2/settings.xml   # 阿里云 Maven 镜像加速
COPY pom.xml .
RUN mvn -B -q dependency:go-offline        # 预下载依赖
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# === 运行阶段：JRE ===
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 4.2 前端 `menu-admin/Dockerfile`

```dockerfile
# === 构建阶段：Node 编译 ===
FROM node:20-alpine AS build
WORKDIR /build
COPY package.json package-lock.json* ./
RUN npm config set registry https://registry.npmmirror.com && \
    (npm ci || npm install)                # 淘宝 npm 镜像加速
COPY . .
RUN npm run build

# === 运行阶段：Nginx 托管 ===
FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

#### 4.3 Maven 镜像 `menu-api/settings.xml`

```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun-maven</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven Mirror</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

#### 4.4 Nginx 反代配置 `menu-admin/nginx.conf`

```nginx
server {
    listen 80;
    server_name _;
    charset utf-8;

    root /usr/share/nginx/html;
    index index.html;

    # SPA 路由：history 模式回退到 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反代到后端容器
    location /api/ {
        proxy_pass http://menu-api:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        client_max_body_size 50m;
    }
}
```

---

### 2.5 环境变量

> 真实密钥见 `.env.dev`（本地，已被 `.gitignore` 忽略）。生产 `/root/yanhuo/.env` 示例：

```bash
DEEPSEEK_API_KEY=<DeepSeek API Key,见 .env.dev>
GLM_API_KEY=<智谱 GLM API Key,可选>
GUDU_AI_PROVIDER=deepseek
```

| 变量 | 说明 | 必填 |
|------|------|------|
| `DEEPSEEK_API_KEY` | DeepSeek API Key（provider=deepseek 时需配） | 否* |
| `GLM_API_KEY` | 智谱 GLM API Key（provider=glm 时需配） | 否* |
| `GUDU_AI_PROVIDER` | AI 提供商启动初值：deepseek(默认)/glm/mock | 否 |

\* 选哪个 provider 就配哪个 key；都不配则 provider 会降级 mock（规则表兜底）。
key 未配时该 provider 标记为未就绪（`GET /ai/provider` 的 `ready=false`），但仍可被选中——调用时自动降级 mock。

> **变量名兼容**：DeepSeek key 推荐用 `DEEPSEEK_API_KEY`。若旧部署机器仍写 `API_KEY`，
> docker-compose 与 yml 已做回退兼容（`${DEEPSEEK_API_KEY:-${API_KEY:-}}`），新老名都能识别，无需立即改部署机。

> **运行时热切换（无需重启）**：除改 `.env` 重建容器外，也可在线切换并立即生效：
> ```bash
> # 查看当前 provider + 各 provider 就绪状态
> curl http://<host>:8080/gudu/ai/provider -H "Authorization: <sa-token>"
> # 切到 glm（写 Redis，重启不丢）
> curl -X PUT http://<host>:8080/gudu/ai/provider \
>   -H "Authorization: <sa-token>" -H "Content-Type: application/json" \
>   -d '{"provider":"glm"}'
> ```
> `GUDU_AI_PROVIDER` 是启动初值；一旦经接口切过，选择状态存 Redis，重启后以 Redis 为准。

> **注意**：修改 `.env` 后需重建容器才能生效（`docker compose up -d` 即可读取新值，但 menu-api 需重新创建）。

### 2.5.1 安全加固上线步骤（security-hardening，2026-10）

> 仅生产；staging/test 按决定保持现状（端口开放、Redis 无密码、登录校验走 `.env`）。

1. **生产 `.env` 补 `REDIS_PASSWORD`**（`openssl rand -hex 24` 生成，勿入库）。`AUTH_LOGIN_CHECK` 无需再配：`docker-compose.prod.yml` 已写死 `"true"`，同时注入 `GUDU_CORS_ALLOWED_ORIGINS=https://imxf.cloud`、`GUDU_EXPOSE_ERROR_DETAIL=false`。
2. **Redis 密码与 menu-api 必须一起重建**（requirepass 只在容器创建时生效；只重建一边会导致 menu-api 连 Redis 报 `NOAUTH`/`WRONGPASS`，有秒级不可用窗口，安排低峰）：
   ```bash
   docker compose -f docker-compose.prod.yml up -d --force-recreate gudu-redis menu-api
   docker exec gudu-redis-prod redis-cli ping                # 应返回 NOAUTH（无密码被拒）
   ```
   之后改密码同理：改 `.env` → 两个服务一起 `--force-recreate`。Redis 无持久化卷，重建会清空缓存/会话（用户需重新登录）。
3. **端口改绑 127.0.0.1**（MySQL 3306 / Redis 6379 / pgvector 25432；Ollama 本就不对外）。需 `up -d` 重建 gudu-mysql/gudu-pgvector 才生效（数据在卷里不丢）。验证：
   ```bash
   ss -lnt | grep -E ':(3306|6379|25432)\b'   # 只应出现 127.0.0.1:xxxx，不得有 0.0.0.0 / [::]
   ```
   本机工具连库改走 SSH 隧道：`ssh -L 3306:127.0.0.1:3306 root@<host>`。
4. **首次部署自动登记迁移**：`scripts/deploy.sh` 发现 `schema_migration_log` 为空时，把 `menu-api/sql/V*.sql` 全部登记（文件名 + sha256），**不执行**；日志会打印「一次性登记」。之后每次部署只执行未登记文件；已登记文件内容被改则中止部署（已执行的迁移禁止修改，请新增 V 文件）。登记以文件名为键，同号文件（V43/V45/V50 各两份）互不影响。新空库：initdb 首启已跑全部文件（含 V63 建表）→ 首次部署同样只登记。
   ```bash
   docker exec gudu-mysql-prod sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" gudu -e "SELECT COUNT(*) FROM schema_migration_log"'
   ```
5. **验证匿名访问被拒**：
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' https://imxf.cloud/gudu/menu/page   # 应为 401（或业务码 401）
   ```

---

### 2.6 数据库

#### 6.1 连接信息

| 项目 | 值 |
|------|-----|
| 数据库类型 | MySQL 8.0 |
| 数据库名 | `yanhuo` |
| 用户名 | `root` |
| 密码 | `<见 .env.dev / compose 配置>` |
| 内网访问地址 | `yanhuo-mysql-prod:3306`（容器间） |
| 公网访问 | ❌ 生产仅绑 `127.0.0.1:3306`，走 SSH 隧道（见 2.5.1） |

#### 6.2 表结构初始化

首次启动时，`./menu-api/sql/` 目录下的 Flyway 迁移脚本会自动执行，包括：

- `V01~V13`：核心业务表（auth, dict, dish, menu, cookbook, cooking, review 等）
- `V14~V15`：演示数据
- `V16~V20`：meal_plan, pantry, dailylog, member_perm, shopping
- `V21~V30`：功能扩展（shopping_redesign, ai, ingredients_ext 等）

#### 6.3 Redis

| 项目 | 值 |
|------|-----|
| Redis 版本 | 7 |
| 密码 | 生产：`.env` `REDIS_PASSWORD`（requirepass）；staging：无 |
| 内网访问地址 | `gudu-redis:6379`（容器间） |
| 公网访问 | ❌ 生产仅绑 `127.0.0.1:6379`，走 SSH 隧道（见 2.5.1） |

#### 6.4 数据持久化

MySQL 数据存储在 Docker 卷 `yanhuo_mysql-data` 中，位置：
```
/var/lib/docker/volumes/yanhuo_mysql-data/_data/
```

---

### 2.7 网络架构

```
                        ┌─────────────────────────────────────┐
                        │          Docker Network              │
                        │                                      │
   Internet ── :80 ──→  │  ┌──────────┐     ┌──────────────┐ │
   Internet ── :3306 →  │  │menu-admin│────→│   menu-api   │ │
   Internet ── :6379 →  │  │ (Nginx)  │     │ (Spring Boot)│ │
                        │  │  :80     │     │    :8080     │ │
                        │  └──────────┘     └──┬───────┬───┘ │
                        │                      │       │      │
                        │                 ┌────▼──┐ ┌──▼───┐ │
                        │                 │ MySQL │ │Redis │ │
                        │                 │ :3306 │ │:6379 │ │
                        │                 └───────┘ └──────┘ │
                        └─────────────────────────────────────┘
```

- **menu-admin**：唯一对外端口 `80`，提供前端页面 + API 反代
- **menu-api**：仅内网访问，不暴露端口到宿主机
- **MySQL / Redis**：仅内网访问，容器间通过 service name 通信

---

### 2.8 常用运维命令

#### 8.1 服务管理

```bash
# 进入项目目录
cd /root/yanhuo

# 启动所有服务（如果镜像未变）
docker compose -f docker-compose.prod.yml up -d

# 重新构建镜像并启动（代码有变更时）
docker compose -f docker-compose.prod.yml up -d --build

# 停止所有服务
docker compose -f docker-compose.prod.yml down

# 停止并删除数据卷（⚠️ 会删除数据库数据）
docker compose -f docker-compose.prod.yml down -v

# 重启所有服务
docker compose -f docker-compose.prod.yml restart

# 重启单个服务
docker compose -f docker-compose.prod.yml restart menu-api
docker compose -f docker-compose.prod.yml restart menu-admin
```

#### 8.2 状态查看

```bash
# 查看容器运行状态
docker compose -f docker-compose.prod.yml ps

# 查看所有容器（含停止的）
docker ps -a

# 查看镜像列表
docker images

# 查看数据卷
docker volume ls

# 查看资源占用
docker stats

# 磁盘使用
df -h
```

#### 8.3 日志查看

```bash
# 实时查看所有服务日志
docker compose -f docker-compose.prod.yml logs -f

# 查看最近 100 行
docker compose -f docker-compose.prod.yml logs --tail 100

# 只看后端日志
docker logs -f menu-api

# 只看前端日志
docker logs -f menu-admin

# 只看数据库日志
docker logs -f yanhuo-mysql-prod

# 查看最近 5 分钟日志
docker logs --since 5m menu-api
```

#### 8.4 数据库操作

```bash
# 进入 MySQL 容器
docker exec -it yanhuo-mysql-prod mysql -uroot -p'<密码,见 .env.dev>'

# 执行 SQL（不进入交互模式）
docker exec yanhuo-mysql-prod mysql -uroot -p'<密码>' -e "USE yanhuo; SHOW TABLES;"

# 备份数据库
docker exec yanhuo-mysql-prod mysqldump -uroot -p'<密码>' yanhuo > /root/backup_$(date +%Y%m%d).sql

# 恢复数据库
docker exec -i yanhuo-mysql-prod mysql -uroot -p'<密码>' yanhuo < /root/backup_20260623.sql
```

#### 8.5 进入容器调试

```bash
# 进入后端容器
docker exec -it menu-api sh

# 进入前端容器
docker exec -it menu-admin sh

# 进入 MySQL 容器
docker exec -it yanhuo-mysql-prod bash
```

#### 8.6 更新部署

```bash
cd /root/yanhuo

# 方式一：如果有 Git（需能访问 GitHub）
git pull
docker compose -f docker-compose.prod.yml up -d --build

# 方式二：从本地 rsync 代码到服务器
# （在本地 Mac 执行）
sshpass -p '<生产 SSH 密码,见 .env.dev>' rsync -avz \
  --exclude='node_modules' --exclude='target' --exclude='.git' \
  -e 'ssh -o StrictHostKeyChecking=no' \
  /Users/maxiaofei/mygithub/menu-new/ \
  root@49.232.3.201:/root/yanhuo/

# 然后在服务器上重建
ssh root@49.232.3.201 'cd /root/yanhuo && docker compose -f docker-compose.prod.yml up -d --build'
```

#### 8.7 清理

```bash
# 清理未使用的镜像
docker image prune -a

# 清理未使用的卷
docker volume prune

# 清理构建缓存
docker builder prune

# 全面清理（谨慎）
docker system prune -a --volumes
```

---

### 2.9 首次部署流程

如果需要在全新服务器上从零部署，按以下步骤操作：

#### Step 1：安装基础依赖

```bash
# Git
yum install -y git

# Docker CE（CentOS 7）
yum install -y yum-utils
yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
yum install -y docker-ce docker-ce-cli containerd.io
systemctl start docker && systemctl enable docker
```

#### Step 2：配置 Docker 镜像加速

```bash
cat > /etc/docker/daemon.json << 'EOF'
{
  "registry-mirrors": [
    "https://hub.rat.dev",
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me",
    "https://docker.m.daocloud.io",
    "https://dockerproxy.com",
    "https://mirror.ccs.tencentyun.com"
  ]
}
EOF
systemctl restart docker
```

#### Step 3：上传项目代码

```bash
# 在本地 Mac 执行
sshpass -p '<密码>' rsync -avz \
  --exclude='node_modules' --exclude='target' --exclude='.git' \
  --exclude='menu-flutter' --exclude='.idea' --exclude='.claude' \
  -e 'ssh -o StrictHostKeyChecking=no' \
  /Users/maxiaofei/mygithub/menu-new/ \
  root@49.232.3.201:/root/yanhuo/
```

#### Step 4：配置环境变量

```bash
# 在服务器上
cat > /root/yanhuo/.env << 'EOF'
DEEPSEEK_API_KEY=<DeepSeek API Key,见 .env.dev>
GLM_API_KEY=<智谱 GLM API Key,可选>
GUDU_AI_PROVIDER=deepseek
EOF
```

#### Step 5：启动服务

```bash
cd /root/yanhuo
docker compose -f docker-compose.prod.yml up -d --build
```

#### Step 6：开放防火墙端口

在**腾讯云控制台 → 安全组**中，添加入站规则：

| 协议 | 端口 | 来源 | 说明 |
|------|------|------|------|
| TCP | 80 | 0.0.0.0/0 | 前端页面 |
| TCP | 3306 | 按需限定来源 IP | MySQL 公网访问 |
| TCP | 6379 | 按需限定来源 IP | Redis 公网访问 |
| TCP | 22 | 0.0.0.0/0 | SSH（通常已开放） |

#### Step 7：验证

```bash
# 检查容器状态
docker ps

# 检查后端是否启动成功
docker logs menu-api | grep "Started"

# 本地测试
curl http://localhost/
curl http://localhost/gudu/auth/login -i

# 公网测试
curl http://49.232.3.201/
```

---

### 2.10 把测试环境迁移到生产服务器（同机隔离）

测试环境已从内网 `192.168.100.248` 迁移到生产服务器 `49.232.3.201`，与生产通过独立 project/网络/容器名/端口完全隔离。以下是一次性迁移操作：

#### Step 1：同步代码到生产服务器的新目录（本地 Mac 执行）

```bash
# rsync 到新目录，绝不碰生产 /root/gudu/
sshpass -p '<生产SSH密码,见.env.dev>' rsync -avz \
  --exclude='node_modules' --exclude='target' --exclude='.git' \
  --exclude='menu-flutter' --exclude='.idea' --exclude='.claude' \
  --exclude='.env' --exclude='.env.dev' --exclude='logs' \
  -e 'ssh -o StrictHostKeyChecking=no' \
  /Users/maxiaofei/mygithub/menu-new/ \
  root@49.232.3.201:/root/gudu-staging/
```

#### Step 2：配置测试环境变量

```bash
ssh root@49.232.3.201
cd /root/gudu-staging
cat > .env <<'EOF'
STAGING_MYSQL_ROOT_PASSWORD=<测试库密码,建议与生产不同>
STAGING_AI_PROVIDER=mock
# 如需真实 AI，取消下面注释：
# STAGING_DEEPSEEK_API_KEY=<key>
# STAGING_AI_PROVIDER=deepseek
EOF
```

#### Step 3：启动测试环境（全新空库自动初始化）

```bash
cd /root/gudu-staging
docker compose -p gudu-staging -f docker-compose.staging.yml up -d --build

# 等待 mysql 初始化 + menu-api 启动（看到 "Started ... in Xs"）
docker compose -p gudu-staging -f docker-compose.staging.yml logs -f menu-api | grep -m1 "Started"
```

#### Step 4：腾讯云安全组放行 9090

在**腾讯云控制台 → 安全组**入站规则加：

| 协议 | 端口 | 来源 | 说明 |
|------|------|------|------|
| TCP | 9090 | 0.0.0.0/0 或限定来源 IP | 测试环境前端入口 |

#### Step 5：验证（关键验证点）

```bash
# 1. context-path 生效（应返回 405/JSON，非 404）
curl http://localhost:9090/gudu/auth/login -i

# 2. 容器名隔离正确（*-staging 与生产容器并存，无重名）
docker ps --format '{{.Names}}' | grep -E 'gudu|menu'

# 3. 网络隔离（staging 网络里只有 *-staging 容器）
docker network inspect gudu-staging_net --format '{{range .Containers}}{{.Name}} {{end}}'

# 4. 卷隔离（两个独立 mysql-data 卷）
docker volume ls | grep mysql-data

# 5. 浏览器访问 http://49.232.3.201:9090，登录 admin + 上传图片显示正常
```

#### Step 6：停掉旧测试机（迁移收尾）

确认腾讯云测试环境正常后，停掉内网旧测试机：
```bash
ssh john@192.168.100.248
cd /home/john/gudu-deploy
docker compose -f docker-compose.test.yml down   # 停容器，保留卷以防万一
# 如确认彻底不再用：docker compose -f docker-compose.test.yml down -v
```

#### 日常更新测试环境代码

```bash
# 本地 Mac rsync + 服务器重建（只重建应用容器，不动 mysql/redis）
sshpass -p '<生产SSH密码>' rsync -avz \
  --exclude='node_modules' --exclude='target' --exclude='.git' \
  --exclude='menu-flutter' --exclude='.idea' --exclude='.claude' \
  --exclude='.env' --exclude='.env.dev' --exclude='logs' \
  -e 'ssh -o StrictHostKeyChecking=no' \
  /Users/maxiaofei/mygithub/menu-new/ \
  root@49.232.3.201:/root/gudu-staging/

ssh root@49.232.3.201 \
  'cd /root/gudu-staging && docker compose -p gudu-staging -f docker-compose.staging.yml up -d --build menu-api menu-admin'
```

---

### 2.11 常见问题

#### Q：容器启动失败？
```bash
# 生产
docker compose -f docker-compose.prod.yml logs
# 测试
docker compose -p gudu-staging -f docker-compose.staging.yml logs
docker ps -a                                      # 查看退出容器
```

#### Q：数据库连接失败？
确认 `menu-api` 的 `application-prod.yml` 中数据库地址使用了容器 service name `gudu-mysql`（非 IP，非旧名 `yanhuo-mysql`）。容器间靠 Docker 网络解析 service name，测试/生产各自在自己的网络里解析到各自的 db。

#### Q：测试与生产端口冲突？
测试用 9090/13306/16379，生产用 80/443/3306/6379，已全部错开。若仍冲突，检查是否有遗留容器：`docker ps -a | grep -E '3306|6379|9090'`。

#### Q：端口被占用？
```bash
ss -tlnp | grep :80    # 查看 80 端口占用
```

#### Q：修改代码后如何更新？
参见 [第八节第 6 条](#86-更新部署)。

#### Q：如何重置数据库？
```bash
docker compose -f docker-compose.prod.yml down -v   # 删除容器和数据卷
docker compose -f docker-compose.prod.yml up -d      # 重新创建（自动初始化表结构）
```
