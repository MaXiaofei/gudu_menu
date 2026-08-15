#!/bin/bash
# ============================================================
# 咕嘟小食单 · 服务器端部署脚本（方案 A：服务器自构建）
#
# 由 GitHub Actions 通过 SSH 调用。服务器 git pull 最新代码后，
# 按 SERVICES 列表重建对应服务镜像（多阶段构建，maven 在容器内），
# 不再跨境传 jar。
#
# 按提交目录选择性部署：CI 检测本次 push 改动的目录（menu-api/、
# menu-admin/、menu-mini/），只重建涉及的服务；基础设施改动全量兜底。
#
# 用法（由 CI 调用，ENV/SERVICES 通过环境变量传入）：
#   ENV=staging SERVICES="menu-api menu-admin" bash scripts/deploy.sh
#   ENV=prod    SERVICES="menu-api"           bash scripts/deploy.sh
#   ENV=staging bash scripts/deploy.sh        # 缺省 SERVICES → menu-api（兼容手动调用）
#
# 前提：
#   - 本脚本在仓库根目录下执行（git pull 后 scripts/deploy.sh 即最新）
#   - 对应环境的容器已 docker compose up 起来过（本次只 rebuild + 重启指定服务）
# ============================================================
set -euo pipefail

ENV="${ENV:-staging}"
SERVICES="${SERVICES:-menu-api}"

# 根据环境选 compose 文件 + project 名 + 健康检查 URL
if [ "$ENV" = "prod" ]; then
  COMPOSE_FILE="docker-compose.prod.yml"
  PROJECT_OPT=""
  HEALTH_URL="http://localhost:80/gudu/doc.html"
else
  COMPOSE_FILE="docker-compose.staging.yml"
  PROJECT_OPT="-p gudu-staging"
  HEALTH_URL="http://localhost:9090/gudu/doc.html"
fi

echo "============================================================"
echo "  部署环境: $ENV"
echo "  服务:     $SERVICES"
echo "  compose:  $COMPOSE_FILE $PROJECT_OPT"
echo "  时间:     $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================================"

# 1. 构建新镜像（多阶段构建：容器内 maven 打包，复用 layer 缓存）
for s in $SERVICES; do
  echo "→ docker compose build $s..."
  docker compose $PROJECT_OPT -f "$COMPOSE_FILE" build "$s"
done

# 2. 增量迁移（V42+，幂等 SQL）——仅 menu-api 部署时执行，先于 API 重启
#    背景：MySQL 官方镜像 /docker-entrypoint-initdb.d 只在空库首启执行一次，
#    存量库的后续迁移靠这里补（V42 起全部幂等：IF NOT EXISTS / information_schema 判断）。
#    密码从容器内 MYSQL_ROOT_PASSWORD 环境变量取，不落盘。
if echo "$SERVICES" | grep -qw menu-api; then
  echo "→ 执行增量迁移（V42+）..."
  for f in menu-api/sql/V*.sql; do
    [ -f "$f" ] || continue
    # 只跑 V42 及之后的迁移（V42 起全部幂等；V01~V41 由 initdb 在空库首启执行，勿重复跑）
    v=$(basename "$f" | sed -E 's/^V([0-9]+)_.*/\1/')
    [ "$v" -ge 42 ] 2>/dev/null || continue
    echo "  apply $(basename "$f")"
    docker compose $PROJECT_OPT -f "$COMPOSE_FILE" exec -T gudu-mysql \
      sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" gudu' < "$f"
  done
fi

# 3. 重启容器（加载新镜像，--no-deps 不动 mysql/redis）
for s in $SERVICES; do
  echo "→ 重启 $s..."
  docker compose $PROJECT_OPT -f "$COMPOSE_FILE" up -d --no-deps "$s"
done

# 4. 健康检查（仅 menu-api 有 HTTP 检查；admin/mini 为静态容器，up 成功即可）
if echo "$SERVICES" | grep -qw menu-api; then
  echo "→ 等待应用启动（健康检查）..."
  MAX_WAIT=24  # 24 × 5s = 120s
  for i in $(seq 1 $MAX_WAIT); do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 5 "$HEALTH_URL" 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" = "200" ]; then
      echo "✅ 应用已启动 (HTTP $HTTP_CODE, 等待 $((i*5))s)"
      echo "✅ 部署完成: $ENV（服务: $SERVICES）@ $(date '+%Y-%m-%d %H:%M:%S')"
      exit 0
    fi
    echo "  [$((i*5))s] 启动中... (HTTP $HTTP_CODE)"
    sleep 5
  done

  echo "❌ 健康检查超时（120s 内未启动），打印容器日志定位："
  docker compose $PROJECT_OPT -f "$COMPOSE_FILE" logs --tail 80 menu-api || true
  echo "  容器状态："
  docker compose $PROJECT_OPT -f "$COMPOSE_FILE" ps menu-api || true
  exit 1
fi

echo "✅ 部署完成: $ENV（服务: $SERVICES）@ $(date '+%Y-%m-%d %H:%M:%S')"
