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
  HEALTH_CMD="docker exec gudu-nginx sh -c 'curl -s -o /dev/null -w \"%{http_code}\" -m 5 http://menu-api-prod:8080/gudu/swagger-ui/index.html'"
else
  COMPOSE_FILE="docker-compose.staging.yml"
  PROJECT_OPT="-p gudu-staging"
  HEALTH_CMD="docker exec gudu-nginx sh -c 'curl -s -o /dev/null -w \"%{http_code}\" -m 5 http://menu-api-staging:8080/gudu/swagger-ui/index.html'"
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
  # pipefail 下 grep 无匹配退出 1 会触发 set -e，加 || true 容忍
  SERVICES=$(echo "$SERVICES" | tr ' ' '\n' | grep -v '^menu-mini$' | tr '\n' ' ' || true)
fi
if [ -z "${SERVICES// /}" ]; then
  echo "✅ 无服务需要部署（prod 跳过 menu-mini）"
  exit 0
fi
for s in $SERVICES; do
  echo "→ docker compose build $s..."
  docker compose $PROJECT_OPT -f "$COMPOSE_FILE" build "$s"
done

# 2. 增量迁移（登记表 schema_migration_log，V63 建表）——仅 menu-api 部署时执行，先于 API 重启
#    背景：MySQL 官方镜像 /docker-entrypoint-initdb.d 只在空库首启执行一次（跑全部 V*.sql），
#    存量库的后续迁移靠这里补。过去每次部署重跑 V42+ 全部 SQL（S12：V61/V62 非幂等清数据），
#    现改为按「文件名」登记 + sha256 校验：
#      - 登记表为空（存量库首次启用 / 新空库 initdb 刚跑完全部文件）→ 一次性登记现有全部文件，不执行；
#      - 未登记文件 → 执行后登记（V01~V41 仅登记不执行：只应由 initdb 在空库首启时执行）；
#      - 已登记且 checksum 一致 → 跳过；checksum 不一致 → 报错中止部署（已执行的迁移禁止改，请新增 V 文件）。
#    以文件名为键：仓库存在同号文件（V43/V45/V50 各两份），互不冲突。
#    密码从容器内 MYSQL_ROOT_PASSWORD 环境变量取，不落盘。
mysql_gudu() {
  # SQL 从 stdin 读入；-N -B 输出无表头、tab 分隔，便于脚本解析
  docker compose $PROJECT_OPT -f "$COMPOSE_FILE" exec -T gudu-mysql \
    sh -c 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" gudu' 2> >(grep -v 'Using a password' >&2)
}
file_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

if echo "$SERVICES" | grep -qw menu-api; then
  echo "→ 迁移登记检查（schema_migration_log）..."
  SQL_DIR="menu-api/sql"
  LOG_DDL="$SQL_DIR/V63__schema_migration_log.sql"

  # 2.0 先确保登记表存在（V63 幂等 CREATE TABLE IF NOT EXISTS）
  mysql_gudu < "$LOG_DDL"

  # 迁移文件清单（版本号自然排序；文件名只允许安全字符，防拼 SQL 出错）
  MIGRATIONS=$(printf '%s\n' "$SQL_DIR"/V*.sql | xargs -n1 basename | sort -V)
  for name in $MIGRATIONS; do
    if ! [[ "$name" =~ ^V[0-9]+__[A-Za-z0-9_]+\.sql$ ]]; then
      echo "❌ 非法迁移文件名: $name（仅允许 V<数字>__<字母数字下划线>.sql）"
      exit 1
    fi
  done

  LOG_COUNT=$(echo "SELECT COUNT(*) FROM schema_migration_log;" | mysql_gudu)
  if [ "$LOG_COUNT" = "0" ]; then
    # 2.1 一次性登记：存量库首次启用（V42~V62 已被历次部署执行过），
    #     或新空库（initdb 首启已执行全部文件，含 V63）。两种情况都只登记、不执行。
    echo "  ⚠️  登记表为空 → 一次性登记现有全部迁移文件（不执行）。"
    echo "     若本次部署同时带有【尚未在本库执行过】的新迁移，请登记后手动执行该文件并核对。"
    {
      echo "INSERT IGNORE INTO schema_migration_log(filename, checksum) VALUES"
      first=1
      for name in $MIGRATIONS; do
        sum=$(file_sha256 "$SQL_DIR/$name")
        if [ $first -eq 1 ]; then first=0; else echo ","; fi
        printf "('%s','%s')" "$name" "$sum"
      done
      echo ";"
    } | mysql_gudu
    echo "  已登记 $(echo "$MIGRATIONS" | wc -l | tr -d ' ') 个文件"
  else
    # 2.2 增量：仅执行未登记文件；已登记校验 checksum
    REGISTERED=$(echo "SELECT filename, checksum FROM schema_migration_log;" | mysql_gudu)
    applied=0
    for name in $MIGRATIONS; do
      sum=$(file_sha256 "$SQL_DIR/$name")
      reg_sum=$(echo "$REGISTERED" | awk -F'\t' -v f="$name" '$1==f {print $2}')
      if [ -n "$reg_sum" ]; then
        if [ "$reg_sum" != "$sum" ]; then
          echo "❌ 迁移文件 $name 已执行过但内容被修改（checksum 不一致），中止部署。"
          echo "   已登记: $reg_sum"
          echo "   当前:   $sum"
          echo "   已执行的迁移禁止修改，请改为新增 V 文件；确属注释类改动可人工核对后："
          echo "   UPDATE schema_migration_log SET checksum='<当前>' WHERE filename='$name';"
          exit 1
        fi
        continue
      fi
      v=$(echo "$name" | sed -E 's/^V([0-9]+)__.*/\1/')
      if [ "$((10#$v))" -lt 42 ]; then
        # V01~V41 只由 initdb 在空库首启执行，这里仅补登记
        echo "  register-only $name（V<42 由 initdb 负责）"
      else
        echo "  apply $name"
        mysql_gudu < "$SQL_DIR/$name"
        applied=$((applied + 1))
      fi
      printf "INSERT INTO schema_migration_log(filename, checksum) VALUES ('%s','%s');\n" "$name" "$sum" \
        | mysql_gudu
    done
    echo "  本次执行 $applied 个新迁移"
  fi
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

# 2.6 基础服务确保在位（幂等）：pgvector/ollama 等新依赖服务可能从未在服务器创建过；
#     按 compose 实际声明的服务动态 up（生产方案 B 已注释 ollama → 不 up，避免 no such service）
if echo "$SERVICES" | grep -qw menu-api; then
  echo "→ 确保基础服务在位（按 compose 声明动态）..."
  BASE_SERVICES=$(docker compose $PROJECT_OPT -f "$COMPOSE_FILE" config --services 2>/dev/null \
    | grep -E 'gudu-(mysql|redis|pgvector|ollama)' | tr '\n' ' ')
  echo "  基础服务: $BASE_SERVICES"
  if [ -n "$BASE_SERVICES" ]; then
    # shellcheck disable=SC2086
    docker compose $PROJECT_OPT -f "$COMPOSE_FILE" up -d --no-recreate $BASE_SERVICES
  fi
fi

# 2.7 nginx 实配确保（幂等）：conf 文件不在 git 中（由 template 生成），
#    服务器 git 重置/清理后需重建，否则 gudu-nginx 重启即丢失反代配置。
if echo "$SERVICES" | grep -qw menu-api || echo "$SERVICES" | grep -qw menu-admin; then
  echo "→ 确保 nginx 实配（template → conf，缺则生成）..."
  CONF_DIR="nginx/conf.d"
  cp -f "$CONF_DIR/app-https.conf.template" "$CONF_DIR/app.conf"
  cp -f "$CONF_DIR/staging-https.conf.template" "$CONF_DIR/staging.conf"
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
