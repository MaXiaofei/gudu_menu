#!/bin/bash
# ============================================================
#  咕嘟小食单 HTTPS 证书初始化脚本
#  用法:
#    1. 先确保域名 DNS 已解析到本机 IP
#    2. 替换下面两行为你自己的信息
#    3. chmod +x init-letsencrypt.sh && ./init-letsencrypt.sh
# ============================================================
set -e

DOMAIN="gudxsd.top"       # ← 你的域名
EMAIL="xinyuejunxin@126.com"  # ← 你的邮箱

PROJECT_DIR="/root/gudu"

# -------- 1. 停止现有服务 --------
echo "=== 停止现有服务 ==="
cd "$PROJECT_DIR"
docker compose -f docker-compose.prod.yml down front-nginx 2>/dev/null || true

# -------- 2. 准备 HTTP-only 配置 --------
echo "=== 准备 HTTP 临时配置 ==="
sed "s/your-domain.com/$DOMAIN/g" nginx/conf.d/app-http.conf > nginx/conf.d/app.conf

# -------- 3. 创建必要目录 --------
mkdir -p certbot/www certbot/conf

# -------- 4. 启动 Nginx（HTTP only） --------
echo "=== 启动 Nginx（HTTP-only） ==="
docker compose -f docker-compose.prod.yml up -d front-nginx
sleep 3

# -------- 5. 申请证书 --------
echo "=== 申请 Let's Encrypt 证书 ==="
docker compose -f docker-compose.prod.yml run --rm \
  certbot certonly \
  --webroot -w /var/www/certbot \
  --email "$EMAIL" \
  --domain "$DOMAIN" \
  --agree-tos \
  --no-eff-email \
  --force-renewal

# -------- 6. 切换到 HTTPS 配置 --------
echo "=== 切换到 HTTPS 配置 ==="
sed "s/your-domain.com/$DOMAIN/g" nginx/conf.d/app-https.conf.template > nginx/conf.d/app.conf
docker compose -f docker-compose.prod.yml exec front-nginx nginx -s reload

echo ""
echo "========================================"
echo "  ✅ HTTPS 证书签发成功！"
echo "  访问: https://$DOMAIN"
echo "  证书路径: $PROJECT_DIR/certbot/conf/live/$DOMAIN/"
echo "  后续自动续期: docker compose logs certbot"
echo "========================================"
