"""에뮬레이터 화면 캡처(2560×1440)에서 앱 창만 잘라 App Market IVI 스크린샷 규격(1700×1212)으로 저장한다.

앱 창은 (853,90)–(2535,1300), 1682×1210 (`dumpsys activity`의 mAppBounds). 늘리거나 줄이지 않고
앱 창만 잘라 1700×1212 캔버스 가운데에 놓는다. 남는 가장자리(가로 9px, 세로 1px)는 앱 배경색으로 채운다
(시스템 창 경계·크기 조절 손잡이가 찍히지 않게).
사용: python docs/app-market-assets/make_screenshots.py <캡처 폴더>
"""
import sys
from pathlib import Path

from PIL import Image

WIDTH, HEIGHT = 1700, 1212
APP_LEFT, APP_TOP, APP_RIGHT, APP_BOTTOM = 853, 90, 2535, 1300
box = (APP_LEFT, APP_TOP, APP_RIGHT, APP_BOTTOM)
offset = ((WIDTH - (APP_RIGHT - APP_LEFT)) // 2, (HEIGHT - (APP_BOTTOM - APP_TOP)) // 2)

# 마켓에서 넘겨 보는 순서: 핵심 기능(정차 후 원탭 분류) → 주행 중 → 홈 → 기록부 → 설정(음성 안내)
SHOTS = [
    ("05-classify.png", "1-classify.png"),
    ("04b-driving-distance.png", "2-driving.png"),
    ("06-home-after.png", "3-home.png"),
    ("02-logbook.png", "4-logbook.png"),
    ("03-settings.png", "5-settings.png"),
]

source = Path(sys.argv[1])
out_dir = Path(__file__).parent
for raw, name in SHOTS:
    app = Image.open(source / raw).convert("RGB").crop(box)
    # 앱 화면 왼쪽 위 구석의 색 = 그 화면의 배경색 (주행 중 화면은 더 어둡다)
    image = Image.new("RGB", (WIDTH, HEIGHT), app.getpixel((4, 4)))
    image.paste(app, offset)
    out = out_dir / f"screenshot-{name}"
    image.save(out, optimize=True)
    print(out.name, image.size, out.stat().st_size // 1024, "KB")
