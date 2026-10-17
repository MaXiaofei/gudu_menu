# 新导航（showcase-ai-community）的两个 tabBar 图标：agent（咕嘟，中间实心圆模拟凸起按钮）、community（厨房圈）。
# 风格与 scripts/design/gen-tabbar-icons.py 一致（81x81，4x 超采样），颜色取 docs/design/tokens.json color.theme。
# 原生 tabBar 不支持凸起：咕嘟用实心圆底 + 白色锅与对话气泡，常态 primary、选中 primaryDeep，保证中间项醒目。
#   python3 menu-mini/scripts/gen-tabbar-extra.py
import json, os
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'menu-mini', 'src', 'static', 'tabbar')
S = 4; N = 81 * S; W = 6 * S
with open(os.path.join(ROOT, 'docs', 'design', 'tokens.json'), encoding='utf-8') as f:
    skins = {k: v for k, v in json.load(f)['color']['theme'].items() if not k.startswith('$')}
def rgb(h): h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4)) + (255,)
def p(v): return v * S

def agent(d, c, white):
    d.ellipse([p(2), p(2), p(79), p(79)], fill=c)
    # 锅
    d.chord([p(19), p(30), p(62), p(70)], 0, 180, fill=white)
    d.line([p(15), p(50), p(66), p(50)], fill=white, width=W)
    # 对话气泡（锅上方热气 → 气泡）
    d.rounded_rectangle([p(33), p(14), p(61), p(36)], radius=p(8), fill=white)
    d.polygon([p(38), p(34), p(36), p(42), p(45), p(35)], fill=white)
    for x in (40, 47, 54):
        d.ellipse([p(x - 2), p(23), p(x + 2), p(27)], fill=c)

def community(d, c, _):
    d.ellipse([p(15), p(16), p(37), p(38)], outline=c, width=W)
    d.arc([p(5), p(44), p(47), p(86)], 180, 360, fill=c, width=W)
    d.ellipse([p(45), p(20), p(63), p(38)], outline=c, width=W)
    d.arc([p(42), p(46), p(76), p(80)], 230, 360, fill=c, width=W)
    d.line([p(5), p(65), p(47), p(65)], fill=c, width=W)

for theme, v in skins.items():
    for state in ('off', 'on'):
        for name, fn in (('agent', agent), ('community', community)):
            col = v['primary'] if (name == 'agent' and state == 'off') else (v['primaryDeep'] if state == 'on' else v['caption'])
            im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
            fn(ImageDraw.Draw(im), rgb(col), (255, 255, 255, 255))
            im = im.resize((81, 81), Image.LANCZOS)
            im.save(os.path.join(OUT, f'{name}-{theme}' + ('-on' if state == 'on' else '') + '.png'), optimize=True)
print('ok', list(skins))
