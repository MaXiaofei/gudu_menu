# 咕嘟小食单 · 部署文档

## 〇、环境划分

项目有两套独立环境，**互不混淆**：

| 环境 | 位置 | 用途 | 访问地址 | 项目目录 | 部署方式 |
|------|------|------|----------|----------|----------|
| **测试环境** | 内网服务器 `192.168.100.248` | 日常部署验证、联调 | `http://192.168.100.248:8080` | `/home/claude/yanhuo-deploy/` | `docker compose -f docker-compose.test.yml up -d --build` |
| **生产环境** | 腾讯云 `49.232.3.201` | 正式上线 | **http://49.232.3.201** | `/root/yanhuo/` | `docker compose -f docker-compose.prod.yml up -d --build` |

> **部署规则**：说「部署到测试」→ 部署到内网 `192.168.100.248`；说「上生产」→ 才部署到腾讯云 `49.232.3.201`。
>
> **数据库规则**：两套环境都**保留现有数据，不清库**。除非明确说「清库」并经二次确认，否则禁止 `down -v` 或 DROP DATABASE。

> 两套环境都通过 `docker-compose.prod.yml` 编排，区别在连接信息和部署目录。

> **⚠️ compose 文件区分**：测试环境用 **`docker-compose.test.yml`**（HTTP，menu-admin 直挂 8080，无 front-nginx/certbot）；生产用 **`docker-compose.prod.yml`**（HTTPS，front-nginx + certbot）。两者 mysql/redis/menu-api 的 service 名与卷名严格一致（`yanhuo-deploy_mysql-data`），切换时不重建卷、不丢数据。**测试环境宿主机的 80 端口被原生 nginx 占用，不能用 prod.yml。**

> **密钥/口令**统一放 `.env.dev`（本地、已被 `.gitignore` 忽略），本文档不写明文密钥。

---

## 一、测试环境（内网）

### 1.1 服务器信息

| 项目 | 值 |
|------|-----|
| 内网 IP | `192.168.100.248` |
| 主机名 | `home-fn` |
| SSH 用户 | `john`（属 Administrators 组，可 sudo，**不在 docker 组**，docker 命令需 `sudo`） |
| 系统架构 | x86_64 |
| Docker | 28.5.2 / Compose v2.40.3 |
| 项目目录 | `/home/claude/yanhuo-deploy/`（属主 `claude`，john 无写权限，部署需 `sudo`） |
| 备份目录 | `/home/claude/yanhuo-deploy/backups/` |
| 数据卷 | `yanhuo-deploy_mysql-data`（**保留，不清库**） |

### 1.2 容器与端口

| 容器 | 端口(宿主机) | 说明 |
|------|--------------|------|
| `menu-api` | 8080(容器内) | 后端，不直接暴露，经 nginx 代理 |
| `menu-admin` | `8080->80` | 前端 Nginx（注意：测试环境前端走 8080，非 80） |
| `yanhuo-mysql-prod` | `3306` | MySQL 8.0，库 `yanhuo` |
| `yanhuo-redis-prod` | `6379` | Redis 7 |

> 测试环境**未配置 HTTPS**（无 certbot），走 HTTP。`menu-api` 的 `API_KEY` 当前为空，AI 功能降级为 mock（这是测试环境既定状态）。

### 1.3 SSH 连接

john 可直接 SSH（密码见 `.env.dev` 的 `SSH_PASS`/`SSH_HOST`）：
```bash
ssh john@192.168.100.248
```
但 docker 命令需 sudo，且测试环境用 **`docker-compose.test.yml`**：
```bash
sudo docker compose --project-directory /home/claude/yanhuo-deploy -f docker-compose.test.yml ps
sudo docker compose --project-directory /home/claude/yanhuo-deploy -f docker-compose.test.yml up -d --build
```
> john 无 `cd` 进 `/home/claude/yanhuo-deploy` 的权限（目录属主 claude），故用 `--project-directory` 指定工作目录解析相对路径，配合 sudo 执行。

### 1.4 数据库现状（部署前核查，2026-06-27）

`yanhuo` 库 27 张表，已有真实测试数据：
- `dish` 18 条 / `member` 6 条 / `ingredient` 445 条 / `menu` 3 条

部署**只重建应用容器，不动数据卷**。

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
API_KEY=<DeepSeek API Key,见 .env.dev>
YANHUO_AI_PROVIDER=deepseek
```

| 变量 | 说明 | 必填 |
|------|------|------|
| `API_KEY` | DeepSeek API Key（AI 功能） | 是 |
| `YANHUO_AI_PROVIDER` | AI 提供商，默认 deepseek | 否 |

> **注意**：修改 `.env` 后需重建容器才能生效（`docker compose up -d` 即可读取新值，但 menu-api 需重新创建）。

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
| 公网访问 | ✅ `49.232.3.201:3306`（需安全组放行） |

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
| 密码 | 无 |
| 内网访问地址 | `yanhuo-redis-prod:6379`（容器间） |
| 公网访问 | ✅ `49.232.3.201:6379`（需安全组放行） |

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
API_KEY=<DeepSeek API Key,见 .env.dev>
YANHUO_AI_PROVIDER=deepseek
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
curl http://localhost/api/

# 公网测试
curl http://49.232.3.201/
```

---

### 2.10 常见问题

#### Q：容器启动失败？
```bash
docker compose -f docker-compose.prod.yml logs   # 查看错误日志
docker ps -a                                      # 查看退出容器
```

#### Q：数据库连接失败？
确认 `menu-api` 的 `application-prod.yml` 中数据库地址使用了容器名 `yanhuo-mysql`（非 IP）。

#### Q：镜像拉取太慢？
检查 `/etc/docker/daemon.json` 中的镜像加速配置，`systemctl restart docker` 后重试。

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
