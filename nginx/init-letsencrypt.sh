#!/bin/bash
# ============================================================
#  咕嘟小食单 HTTPS 证书初始化脚本
#  在服务器 /root/gudu 下执行（DNS 已生效后）：
#    chmod +x init-letsencrypt.sh && ./init-letsencrypt.sh
#  签发：imxf.cloud + www.imxf.cloud（Let's Encrypt，webroot 方式）
# ------------------------------------------------------------
#  设计要点（修复旧版 3 个 bug）：
#    1. 不再用失效的 sed 占位符替换 —— 模板内已写死域名，直接 cp
#    2. 唯一生效配置为 app.conf；app-*.conf.template 不被 nginx 加载，避免冲突
#    3. certbot 服务在 compose 里是常驻循环 entrypoint，run 时用 --entrypoint certbot 覆盖
# ============================================================
set -e

DOMAIN="imxf.cloud"                     # ← 主域名
WWW_DOMAIN="www.${DOMAIN}"             # ← www 子域
EMAIL="xinyuejunxin@126.com"           # ← 证书注册邮箱（Let's Encrypt 过期提醒）

SERVER_IP="49.232.3.201"
PROJECT_DIR="/root/gudu"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.prod.yml"
CONF_DIR="$PROJECT_DIR/nginx/conf.d"

cd "$PROJECT_DIR"

# -------- 0. 前置检查：DNS 必须已解析到本机 --------
echo "=== [0/4] 前置检查：DNS ==="
RESOLVED_IP=$(dig +short "$DOMAIN" | grep -E '^[0-9.]+$' | tail -n1)
if [ "$RESOLVED_IP" != "$SERVER_IP" ]; then
  echo "❌ $DOMAIN 未解析到 $SERVER_IP（当前: ${RESOLVED_IP:-空}）。请先在阿里云 DNS 加 A 记录并等生效。"
  exit 1
fi
echo "✅ $DOMAIN -> $RESOLVED_IP"

# -------- 1. 部署 HTTP-only 配置（ACME 验证 + 反代）--------
echo "=== [1/4] 部署 HTTP 临时配置（app.conf）==="
cp "$CONF_DIR/app-http.conf.template" "$CONF_DIR/app.conf"
mkdir -p certbot/www certbot/conf

# -------- 2. 启动 / 热加载 Nginx --------
echo "=== [2/4] 启动 Nginx（HTTP-only）==="
docker compose -f "$COMPOSE_FILE" up -d front-nginx
sleep 3
docker exec gudu-nginx nginx -t
docker exec gudu-nginx nginx -s reload

# -------- 3. 申请证书（主域 + www）--------
echo "=== [3/4] 申请 Let's Encrypt 证书（$DOMAIN + $WWW_DOMAIN）==="
docker compose -f "$COMPOSE_FILE" run --rm --entrypoint "certbot" \
  certbot certonly \
  --webroot -w /var/www/certbot \
  --email "$EMAIL" \
  --domain "$DOMAIN" \
  --domain "$WWW_DOMAIN" \
  --agree-tos \
  --no-eff-email \
  --force-renewal

# -------- 4. 切换到 HTTPS 配置 --------
echo "=== [4/4] 切换到 HTTPS 配置 ==="
cp "$CONF_DIR/app-https.conf.template" "$CONF_DIR/app.conf"
docker exec gudu-nginx nginx -t
docker exec gudu-nginx nginx -s reload

echo ""
echo "========================================"
echo "  ✅ HTTPS 证书签发成功！"
echo "  访问: https://$DOMAIN"
echo "  证书目录: $PROJECT_DIR/certbot/conf/live/$DOMAIN/"
echo "  自动续期: gudu-certbot 容器每 12h 检查"
echo "  注意: 续期后需 reload nginx 使新证书生效"
echo "        (可加宿主机 cron: docker exec gudu-nginx nginx -s reload)"
echo "========================================"
