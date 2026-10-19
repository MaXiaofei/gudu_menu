#!/usr/bin/env python3
"""菜谱图片压缩 + 缩略图（Pillow）。

源：xiachufang/scraper/images/xc_<id>.{jpg,png}（封面）、xc_<id>_s<n>.*（步骤）
产物：scripts/seed/.cache/images/dish/{original,thumbnail}/<key>.jpg
  封面：original 最长边 1080 / thumbnail 360；步骤：720 / 240；JPEG q80 渐进式
与客户端缩略图规则一致：URL 中 /original/ 换成 /thumbnail/。
增量：产物比源文件新则跳过。用法：python3 images.py [--force]
"""
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "xiachufang/scraper/images"
OUT = Path(__file__).resolve().parent / ".cache/images/dish"
SIZES = {"cover": (1080, 360), "step": (720, 240)}


def save(im, dst, edge, src=None):
    """缩放到最长边 edge 后存 JPEG；源本身是 JPEG 且不超尺寸时，取「原文件 / 重编码」中较小的一份。"""
    dst.parent.mkdir(parents=True, exist_ok=True)
    small = im.copy()
    small.thumbnail((edge, edge), Image.LANCZOS)
    small.save(dst, "JPEG", quality=80, optimize=True, progressive=True)
    if src is not None and src.suffix.lower() in (".jpg", ".jpeg") and max(im.size) <= edge \
            and src.stat().st_size < dst.stat().st_size:
        shutil.copyfile(src, dst)


def main():
    force = "--force" in sys.argv
    done = skipped = failed = 0
    before = after = 0
    for src in sorted(SRC.iterdir()):
        if src.suffix.lower() not in (".jpg", ".jpeg", ".png") or not src.name.startswith("xc_"):
            continue
        key = src.stem
        kind = "step" if "_s" in key else "cover"
        orig = OUT / "original" / f"{key}.jpg"
        thumb = OUT / "thumbnail" / f"{key}.jpg"
        before += src.stat().st_size
        if not force and orig.exists() and thumb.exists() and orig.stat().st_mtime >= src.stat().st_mtime:
            skipped += 1
            after += orig.stat().st_size + thumb.stat().st_size
            continue
        try:
            im = ImageOps.exif_transpose(Image.open(src))
            if im.mode in ("RGBA", "LA", "P"):
                im = im.convert("RGBA")
                bg = Image.new("RGB", im.size, (255, 255, 255))
                bg.paste(im, mask=im.split()[-1])
                im = bg
            else:
                im = im.convert("RGB")
            big, small = SIZES[kind]
            save(im, orig, big, src)
            save(im, thumb, small, src)
            after += orig.stat().st_size + thumb.stat().st_size
            done += 1
        except Exception as e:  # 单张坏图不影响整体
            failed += 1
            print(f"  [失败] {src.name}: {e}", file=sys.stderr)
    print(f"图片：处理 {done}，跳过 {skipped}，失败 {failed}；源 {before/1e6:.1f}MB → 产物 {after/1e6:.1f}MB（含缩略图）")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
