"""从 design/app-icon-source.png 生成 Android 启动图标。

用法（需要 Pillow）：
    python tools/make_icons.py

源图是「成品图标」：1254×1254 RGB，画面自带圆角 + 黑边。
直接塞进 mipmap 会在 Android 8+ 的自适应遮罩下露出黑角，所以这里：

1. 放大 1.18 倍后中心裁切 —— 把四周的黑色圆角边框挤出画面，得到满幅画面；
2. 自适应图标前景：把画面缩到 108dp 画布的中间 72dp（安全区），
   这样圆形 / 方圆形遮罩只会裁掉四角，主要元素都保留；
3. 传统图标：方形直接用源图（它就是为此设计的），圆形用满幅画面做圆裁 + 边缘色填充；
4. 背景色取画面最外圈平均色，保证自适应图标的安全区与画面无缝。

输出：
  app/src/main/res/drawable-nodpi/ic_launcher_foreground.png
  app/src/main/res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher.png
  app/src/main/res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher_round.png
  app/src/main/res/values/colors.xml 里的 ic_launcher_background
"""

import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "design", "app-icon-source.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")

CONTENT_DP = 72     # 自适应图标安全区（108dp 画布里居中 72dp）
CANVAS_DP = 108
SCALE_UP = 1.18     # 裁掉源图自带的圆角黑边
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main() -> None:
    src = Image.open(SRC).convert("RGB")
    side = min(src.size)
    src = src.crop(
        ((src.width - side) // 2, (src.height - side) // 2,
         (src.width + side) // 2, (src.height + side) // 2)
    )

    big = src.resize((round(side * SCALE_UP), round(side * SCALE_UP)), Image.LANCZOS)
    offset = (big.width - side) // 2
    art = big.crop((offset, offset, offset + side, offset + side))

    px = art.load()
    edge = []
    for i in range(0, side, 3):
        edge += [px[i, 0], px[i, side - 1], px[0, i], px[side - 1, i]]
    bg = tuple(sum(c[j] for c in edge) // len(edge) for j in range(3))
    print("artwork", art.size, "background", "#%02X%02X%02X" % bg)

    # 自适应图标前景
    canvas_px = CANVAS_DP * 4
    content_px = round(canvas_px * CONTENT_DP / CANVAS_DP)
    fg = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    fg.paste(
        art.resize((content_px, content_px), Image.LANCZOS),
        ((canvas_px - content_px) // 2, (canvas_px - content_px) // 2),
    )
    os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
    fg.save(os.path.join(RES, "drawable-nodpi", "ic_launcher_foreground.png"), optimize=True)

    # 传统图标
    for dpi, size in LEGACY.items():
        folder = os.path.join(RES, f"mipmap-{dpi}")
        os.makedirs(folder, exist_ok=True)
        src.resize((size, size), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher.png"), optimize=True
        )
        rounded = Image.new("RGB", (size, size), bg)
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        rounded.paste(art.resize((size, size), Image.LANCZOS), (0, 0), mask)
        rounded.save(os.path.join(folder, "ic_launcher_round.png"), optimize=True)

    print("done. 记得把 values/colors.xml 里的 ic_launcher_background 改成上面的背景色")


if __name__ == "__main__":
    main()
