"""App Market 아이콘(512×512 PNG)을 앱의 adaptive icon과 같은 모양으로 그린다.

원본: app/src/main/res/drawable/ic_launcher_foreground.xml (108dp 캔버스) + 배경색 ic_launcher_background(#0B3D5C).
기기에서 보이는 가운데 72dp(18~90) 영역을 512px로 옮긴다. 4배로 그린 뒤 줄여 가장자리를 부드럽게 한다.
사용: python docs/app-market-assets/make_icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

SIZE = 512
SUPER = 4
VIEW_START, VIEW_SIZE = 18, 72  # adaptive icon에서 보이는 영역 (dp)
BACKGROUND = "#0B3D5C"
PAPER = "#FFFFFF"
LINE = "#0B3D5C"
CHECK = "#2BB673"

scale = SIZE * SUPER / VIEW_SIZE


def p(x, y):
    return ((x - VIEW_START) * scale, (y - VIEW_START) * scale)


def stroke(draw, points, width_dp, color):
    width = width_dp * scale
    pts = [p(*pt) for pt in points]
    draw.line(pts, fill=color, width=round(width), joint="curve")
    r = width / 2
    for x, y in (pts[0], pts[-1]):  # 둥근 끝 (strokeLineCap="round")
        draw.ellipse((x - r, y - r, x + r, y + r), fill=color)


image = Image.new("RGB", (SIZE * SUPER, SIZE * SUPER), BACKGROUND)
draw = ImageDraw.Draw(image)
draw.rounded_rectangle((*p(37, 31), *p(71, 77)), radius=4 * scale, fill=PAPER)
stroke(draw, [(43, 41), (65, 41)], 3, LINE)
stroke(draw, [(43, 49), (65, 49)], 3, LINE)
stroke(draw, [(43, 57), (57, 57)], 3, LINE)
stroke(draw, [(51, 67), (55.5, 71.5), (65, 62)], 3.5, CHECK)

out = Path(__file__).with_name("icon-512.png")
image.resize((SIZE, SIZE), Image.LANCZOS).save(out, optimize=True)
print(out, out.stat().st_size, "bytes")
