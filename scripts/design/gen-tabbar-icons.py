# 生成小程序 tabBar 线性图标（81x81，4x 超采样抗锯齿），每套皮肤 × 5 个 tab × 常态/选中。
# 颜色取自 docs/design/tokens.json `color.theme`：常态 = caption，选中 = primaryDeep
# （与 menu-mini/src/utils/theme.ts applyTabBar 的文字色一致）。新增皮肤后运行：
#   python3 scripts/design/gen-tabbar-icons.py            # 默认输出 menu-mini/src/static/tabbar
# 依赖 Pillow（pip install pillow）。
import json, os, sys
from PIL import Image, ImageDraw
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'menu-mini', 'src', 'static', 'tabbar')
os.makedirs(out, exist_ok=True)
S = 4; N = 81 * S; W = 6 * S
with open(os.path.join(ROOT, 'docs', 'design', 'tokens.json'), encoding='utf-8') as f:
    _skins = json.load(f)['color']['theme']
THEMES = {
  k: {'off': v['caption'], 'on': v['primaryDeep']}
  for k, v in _skins.items() if not k.startswith('$')
}
def rgb(h): h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4)) + (255,)
def p(v): return v * S

def dish(d, c):  # 菜谱：翻开的书
    d.rounded_rectangle([p(14), p(16), p(67), p(65)], radius=p(6), outline=c, width=W)
    d.line([p(40.5), p(16), p(40.5), p(65)], fill=c, width=W)
    for y in (30, 42):
        d.line([p(22), p(y), p(33), p(y)], fill=c, width=W)
        d.line([p(48), p(y), p(59), p(y)], fill=c, width=W)
def menu(d, c):  # 食集：清单
    d.rounded_rectangle([p(17), p(14), p(64), p(67)], radius=p(7), outline=c, width=W)
    for y in (30, 41, 52):
        d.ellipse([p(25), p(y-3), p(31), p(y+3)], fill=c)
        d.line([p(36), p(y), p(56), p(y)], fill=c, width=W)
def recommend(d, c):  # 智荐：碗 + 热气
    d.chord([p(12), p(22), p(69), p(74)], 0, 180, outline=c, width=W)
    d.line([p(12), p(48), p(69), p(48)], fill=c, width=W)
    for x in (30, 41, 52):
        d.arc([p(x-5), p(14), p(x+5), p(26)], 270, 90, fill=c, width=W)
        d.arc([p(x-5), p(25), p(x+5), p(37)], 90, 270, fill=c, width=W)
def pantry(d, c):  # 我家余量：冰箱
    d.rounded_rectangle([p(21), p(10), p(60), p(71)], radius=p(7), outline=c, width=W)
    d.line([p(21), p(32), p(60), p(32)], fill=c, width=W)
    d.line([p(29), p(19), p(29), p(25)], fill=c, width=W)
    d.line([p(29), p(41), p(29), p(52)], fill=c, width=W)
def profile(d, c):  # 我的：人像
    d.ellipse([p(28), p(12), p(53), p(37)], outline=c, width=W)
    d.arc([p(14), p(44), p(67), p(96)], 180, 360, fill=c, width=W)
    d.line([p(14), p(70), p(67), p(70)], fill=c, width=W)

ICONS = {'dish': dish, 'menu': menu, 'recommend': recommend, 'pantry': pantry, 'profile': profile}
for theme, cols in THEMES.items():
    for state in ('off', 'on'):
        for name, fn in ICONS.items():
            im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
            fn(ImageDraw.Draw(im), rgb(cols[state]))
            im = im.resize((81, 81), Image.LANCZOS)
            suffix = f'-{theme}' + ('-on' if state == 'on' else '')
            im.save(os.path.join(out, f'{name}{suffix}.png'), optimize=True)
print('ok')
