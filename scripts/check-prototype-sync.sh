#!/usr/bin/env bash
# 检查 Flutter 代码与 44829 原型的同步状态
# 用法: bash scripts/check-prototype-sync.sh
# 原理: 对比代码文件和对应原型的最后修改时间，代码新于原型时提醒

set -euo pipefail

cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

FLUTTER_DIR="menu-flutter/lib"
PROTO_DIR=".superpowers/brainstorm/44829-1783002708/content"

# 代码文件 → 对应原型的映射
# 格式: "代码路径|原型文件名|说明"
declare -a MAPPING=(
  "pages/dish/list_page.dart|cookbook-search.html|菜谱列表/找菜"
  "pages/dish/detail_page.dart|cookbook-search.html|菜品详情"
  "pages/menu/detail_page.dart|menu-detail-cai.html|食集详情"
  "pages/menu/list_page.dart|menu-detail-cai.html|食集列表"
  "pages/pantry/list_page.dart|pantry-page.html|我家余量"
  "pages/dailylog/daily_log_page.dart|dailylog.html|食记"
  "pages/shopping/shopping_page.dart|menu-detail-caigou.html|采购"
  "pages/ingredient/list_page.dart|ingredient-manage.html|食材管理"
  "pages/ingredient/create_page.dart|ingredient-manage.html|食材编辑"
  "pages/ai/recommend_page.dart|home-aisho.html|推荐页(原型已暂停)"
  "pages/more_page.dart|home-aisho.html|更多页"
)

count=0

echo "检查 Flutter 代码与 44829 原型同步状态..."
echo ""

for entry in "${MAPPING[@]}"; do
  IFS='|' read -r code_path proto_name desc <<< "$entry"
  full_code="$FLUTTER_DIR/$code_path"
  full_proto="$PROTO_DIR/$proto_name"

  # 跳过不存在的文件
  [[ ! -f "$full_code" ]] && continue
  [[ ! -f "$full_proto" ]] && continue

  # 获取最后修改时间（秒级时间戳）
  code_ts=$(stat -f %m "$full_code" 2>/dev/null || stat -c %Y "$full_code" 2>/dev/null || continue)
  proto_ts=$(stat -f %m "$full_proto" 2>/dev/null || stat -c %Y "$full_proto" 2>/dev/null || continue)

  # 代码新于原型（且差距超过 1 小时，避免刚同步的误报）
  gap=$((code_ts - proto_ts))
  if [ "$gap" -gt 3600 ]; then
    code_date=$(date -r "$code_ts" "+%Y-%m-%d %H:%M" 2>/dev/null || date -d @"$code_ts" "+%Y-%m-%d %H:%M" 2>/dev/null)
    proto_date=$(date -r "$proto_ts" "+%Y-%m-%d %H:%M" 2>/dev/null || date -d @"$proto_ts" "+%Y-%m-%d %H:%M" 2>/dev/null)

    echo "⚠️  $desc"
    echo "   代码: $code_path ($code_date)"
    echo "   原型: $proto_name ($proto_date)"
    echo "   → 代码比原型新 $((gap / 3600)) 小时，检查界面改动是否已回写原型"
    echo ""
    count=$((count + 1))
  fi
done

if [ "$count" -eq 0 ]; then
  echo "✅ 所有代码文件与原型同步状态正常。"
else
  echo "共 $count 处需确认。详见 docs/DEVELOPMENT.md。"
fi
