#!/bin/bash
# ============================================================
# 咕嘟小食单 · 服务器端部署脚本
# 由 GitHub Actions 通过 SSH 调用，负责将新 jar 部署到 Docker 容器。
#
# 用法：
#   ENV=staging ./deploy.sh    # 部署到测试环境 (menu-api-staging, 端口 9090)
#   ENV=prod ./deploy.sh       # 部署到生产环境 (menu-api, 端口 80)
#
# 前提：jar 文件已由 GitHub Actions scp 到服务器 /tmp/menu-api-deploy.jar
# ============================================================
set -euo pipefail

ENV="${ENV:-staging}"
JAR_SRC="/tmp/menu-api-deploy.jar"

# 根据环境选择容器名
if [ "$ENV" = "prod" ]; then
  CONTAINER="menu-api"
  HEALTH_URL="http://localhost:80/gudu/doc.html"
else
  CONTAINER="menu-api-staging"
  HEALTH_URL="http://localhost:9090/gudu/doc.html"
fi

echo "============================================================"
echo "  部署环境: $ENV"
echo "  容器名:   $CONTAINER"
echo "  时间:     $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================================"

# 1. 检查 jar 文件
if [ ! -f "$JAR_SRC" ]; then
  echo "❌ jar 文件不存在: $JAR_SRC"
  exit 1
fi

JAR_SIZE=$(du -h "$JAR_SRC" | cut -f1)
echo "✅ jar 文件就绪 ($JAR_SIZE)"

# 2. docker cp 替换容器内的 jar
echo "→ 替换容器内 jar..."
docker cp "$JAR_SRC" "$CONTAINER:/app/app.jar"
echo "✅ jar 已复制到容器"

# 3. 重启容器（加载新 jar）
echo "→ 重启容器 $CONTAINER..."
docker restart "$CONTAINER"
echo "✅ 容器已重启"

# 4. 健康检查（等待 Spring Boot 启动，最多 120 秒）
echo "→ 等待应用启动（健康检查）..."
MAX_WAIT=24  # 24 × 5s = 120s
for i in $(seq 1 $MAX_WAIT); do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 5 "$HEALTH_URL" 2>/dev/null || echo "000")
  if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ 应用已启动 (HTTP $HTTP_CODE, 等待 $((i*5))s)"
    # 清理临时文件
    rm -f "$JAR_SRC"
    echo "✅ 部署完成: $ENV @ $(date '+%Y-%m-%d %H:%M:%S')"
    exit 0
  fi
  echo "  [$((i*5))s] 启动中... (HTTP $HTTP_CODE)"
  sleep 5
done

echo "❌ 健康检查超时（120s 内未启动）"
echo "  查看日志: docker logs $CONTAINER --tail 30"
exit 1
