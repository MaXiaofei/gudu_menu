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
  HEALTH_CMD="docker exec gudu-nginx sh -c 'curl -s -o /dev/null -w \"%{http_code}\" -m 5 http://menu-api-prod:8080/gudu/doc.html'"
else
  COMPOSE_FILE="docker-compose.staging.yml"
  PROJECT_OPT="-p gudu-staging"
  HEALTH_CMD="docker exec gudu-nginx sh -c 'curl -s -o /dev/null -w \"%{http_code}\" -m 5 http://menu-api-staging:8080/gudu/doc.html'"
fi

echo "============================================================"
echo "  部署环境: $ENV"
echo "  服务:     $SERVICES"
echo "  compose:  $COMPOSE_FILE $PROJECT_OPT"
echo "  时间:     $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================================"

# 1. 构建新镜像（多阶段构建：容器内 maven 打包，复用 layer 缓存）
#    注意：小程序 H5（menu-mini）只在 staging 暴露（prod compose 无该 service），prod 环境跳过
if [ "$ENV" = "prod" ]; then
  SERVICES=$(echo "$SERVICES" | tr ' ' '\n' | grep -v '^menu-mini$' | tr '\n' ' ')
fi
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

# 2.5 向量库扩展确保（幂等）：pgvector 容器存在但 vector 扩展缺失时
#     Spring AI PgVectorStore 启动 CREATE EXTENSION 会失败导致整个应用起不来。
if echo "$SERVICES" | grep -qw menu-api; then
  echo "→ 确保 pgvector vector 扩展..."
  docker compose $PROJECT_OPT -f "$COMPOSE_FILE" exec -T gudu-pgvector \
    psql -U gudu -d gudu -c 'CREATE EXTENSION IF NOT EXISTS vector;' \
    || echo "  （pgvector 容器未就绪，跳过——若启动失败请检查 gudu-pgvector 服务）"
fi

# 2.6 Ollama embedding 模型确保（幂等）：bge-m3 缺失时语义找菜/推荐 404。
#     首次拉取 ~1.2GB（网络慢时几分钟），之后 ollama list 命中即跳过。
if echo "$SERVICES" | grep -qw menu-api; then
  echo "→ 确保 Ollama bge-m3 模型..."
  if docker compose $PROJECT_OPT -f "$COMPOSE_FILE" exec -T gudu-ollama \
      ollama list 2>/dev/null | grep -q "bge-m3"; then
    echo "  bge-m3 已存在，跳过"
  else
    echo "  拉取 bge-m3（首次 ~1.2GB）..."
    docker compose $PROJECT_OPT -f "$COMPOSE_FILE" exec -T gudu-ollama ollama pull bge-m3 \
      || echo "  （拉取失败——语义检索将 404，请检查 gudu-ollama 服务/网络）"
  fi
fi

# 2.6 基础服务确保在位（幂等）：pgvector/ollama 等新依赖服务可能从未在服务器创建过
if echo "$SERVICES" | grep -qw menu-api; then
  echo "→ 确保基础服务在位（mysql/redis/pgvector/ollama）..."
  docker compose $PROJECT_OPT -f "$COMPOSE_FILE" up -d --no-recreate gudu-mysql gudu-redis gudu-pgvector gudu-ollama
fi

# 2.7 nginx 实配确保（幂等）：conf 文件不在 git 中（由 template 生成），
#    服务器 git 重置/清理后需重建，否则 gudu-nginx 重启即丢失反代配置。
if echo "$SERVICES" | grep -qw menu-api || echo "$SERVICES" | grep -qw menu-admin; then
  echo "→ 确保 nginx 实配（template → conf，缺则生成）..."
  CONF_DIR="nginx/conf.d"
  [ -f "$CONF_DIR/app.conf" ] || cp "$CONF_DIR/app-https.conf.template" "$CONF_DIR/app.conf"
  [ -f "$CONF_DIR/staging.conf" ] || cp "$CONF_DIR/staging-https.conf.template" "$CONF_DIR/staging.conf"
  if docker exec gudu-nginx nginx -t 2>/dev/null; then
    docker exec gudu-nginx nginx -s reload && echo "  nginx reloaded"
  else
    echo "  （gudu-nginx 不可达或配置未就绪，跳过 reload——请检查 front-nginx 容器）"
  fi
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
    HTTP_CODE=$(eval "$HEALTH_CMD" 2>/dev/null || echo "000")
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
