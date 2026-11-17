from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import json
import shutil
import zipfile

S = 2
W, H = 1080, 1440
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "dist" / "酷安首发图-v1.4.3"
OUT.mkdir(parents=True, exist_ok=True)

MIST = "#EEF3F0"
PAPER = "#FAFCFB"
INK = "#17241E"
SECONDARY = "#5B6B63"
FOREST = "#27644F"
SOFT = "#DFEBE4"
DIVIDER = "#D7E1DB"
MUTED = "#89968F"
WHITE = "#FFFFFF"
FONT_DIR = Path(r"C:\Windows\Fonts")
FONT_FILES = {
    "regular": FONT_DIR / "Noto Sans SC (TrueType).otf",
    "medium": FONT_DIR / "Noto Sans SC Medium (TrueType).otf",
    "bold": FONT_DIR / "Noto Sans SC Bold (TrueType).otf",
}
for path in FONT_FILES.values():
    if not path.exists():
        raise FileNotFoundError(path)
_font_cache = {}


def font(size, weight="regular"):
    key = (size, weight)
    if key not in _font_cache:
        _font_cache[key] = ImageFont.truetype(str(FONT_FILES[weight]), round(size * S))
    return _font_cache[key]


def cubic(p0, p1, p2, p3, steps=12):
    points = []
    for i in range(1, steps + 1):
        t = i / steps
        u = 1 - t
        points.append((
            u**3*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t**3*p3[0],
            u**3*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t**3*p3[1],
        ))
    return points


def map_points(points, x, y, size):
    k = size / 48
    return [(x + px*k, y + py*k) for px, py in points]


class Card:
    def __init__(self, width=W, height=H):
        self.width = width
        self.height = height
        self.image = Image.new("RGBA", (width*S, height*S), MIST)
        self.draw = ImageDraw.Draw(self.image)
        self.text_bounds = []

    @staticmethod
    def box(values):
        return tuple(round(v*S) for v in values)

    def polygon(self, points, fill):
        self.draw.polygon([(round(x*S), round(y*S)) for x, y in points], fill=fill)

    def line(self, points, fill, width=1):
        self.draw.line([(round(x*S), round(y*S)) for x, y in points], fill=fill, width=max(1, round(width*S)), joint="curve")

    def rr(self, bounds, radius, fill, outline=None, width=1, shadow=False):
        if shadow:
            layer = Image.new("RGBA", self.image.size, (0, 0, 0, 0))
            ld = ImageDraw.Draw(layer)
            x1, y1, x2, y2 = bounds
            ld.rounded_rectangle(self.box((x1, y1+9, x2, y2+9)), radius=round(radius*S), fill=(23, 36, 30, 24))
            layer = layer.filter(ImageFilter.GaussianBlur(round(18*S)))
            self.image = Image.alpha_composite(self.image, layer)
            self.draw = ImageDraw.Draw(self.image)
        self.draw.rounded_rectangle(self.box(bounds), radius=round(radius*S), fill=fill, outline=outline, width=max(1, round(width*S)))

    def ellipse(self, bounds, fill, outline=None, width=1):
        self.draw.ellipse(self.box(bounds), fill=fill, outline=outline, width=max(1, round(width*S)))

    def text(self, x, y, value, size, fill=INK, weight="regular", anchor="la"):
        f = font(size, weight)
        pos = (round(x*S), round(y*S))
        bounds = self.draw.textbbox(pos, value, font=f, anchor=anchor)
        if bounds[0] < 20*S or bounds[1] < 20*S or bounds[2] > (self.width-20)*S or bounds[3] > (self.height-20)*S:
            raise ValueError(f"Text outside safe canvas: {value!r} {bounds}")
        self.draw.text(pos, value, font=f, fill=fill, anchor=anchor)
        self.text_bounds.append({"text": value, "bounds": [round(v/S, 1) for v in bounds]})
        return bounds

    def icon(self, x, y, size):
        outer = [(8, 10)]
        outer += cubic((8, 10), (8, 7.8), (9.8, 6), (12, 6))
        outer += [(36, 6)]
        outer += cubic((36, 6), (38.2, 6), (40, 7.8), (40, 10))
        outer += [(40, 30)]
        outer += cubic((40, 30), (40, 36.6), (34.6, 42), (28, 42))
        outer += [(20, 42)]
        outer += cubic((20, 42), (13.4, 42), (8, 36.6), (8, 30))
        self.polygon(map_points(outer, x, y, size), FOREST)
        notch = [(13, 15), (35, 15), (35, 20), (29, 20)]
        notch += cubic((29, 20), (28.2, 20), (27.5, 20.5), (27.2, 21.2), 8)
        notch += cubic((27.2, 21.2), (26.4, 23.2), (25.3, 24), (24, 24), 8)
        notch += cubic((24, 24), (22.7, 24), (21.6, 23.2), (20.8, 21.2), 8)
        notch += cubic((20.8, 21.2), (20.5, 20.5), (19.8, 20), (19, 20), 8)
        notch += [(13, 20)]
        self.polygon(map_points(notch, x, y, size), SOFT)
        self.polygon(map_points([(23, 27), (25, 27), (25, 34), (23, 34)], x, y, size), PAPER)

    def save(self, filename):
        result = self.image.convert("RGB").resize((self.width, self.height), Image.Resampling.LANCZOS)
        result.save(OUT / filename, optimize=True)
        return self.text_bounds


def base(number, title, subtitle):
    c = Card()
    c.ellipse((830, -170, 1190, 190), SOFT)
    c.ellipse((-150, 1270, 180, 1600), SOFT)
    c.icon(72, 56, 58)
    c.text(148, 62, "通知收纳", 32, INK, "bold")
    c.text(1008, 70, f"{number:02d} / 04", 21, SECONDARY, "medium", "ra")
    c.text(72, 188, title, 64, INK, "bold")
    c.text(72, 290, subtitle, 28, SECONDARY)
    c.rr((56, 404, 1024, 1272), 36, PAPER, shadow=True)
    c.text(72, 1350, "界面示意 · 通知内容均为示例", 20, MUTED)
    c.text(1008, 1350, "v1.4.3", 20, MUTED, "regular", "ra")
    return c


def app_header(c, active="current"):
    c.icon(100, 444, 52)
    c.text(168, 443, "通知收纳", 31, INK, "bold")
    c.text(168, 490, "点一下长期隐藏 · 长按可选时长", 18, SECONDARY)
    c.rr((856, 448, 972, 504), 16, SOFT)
    c.text(914, 476, "关于", 21, FOREST, "medium", "mm")
    c.ellipse((102, 548, 112, 558), FOREST)
    c.text(126, 541, "已授权，可管理通知", 20, SECONDARY)
    hidden_color = FOREST if active == "hidden" else MUTED
    current_color = FOREST if active == "current" else MUTED
    c.text(318, 616, "已隐藏", 23, hidden_color, "bold" if active == "hidden" else "regular", "mm")
    c.text(762, 616, "当前通知", 23, current_color, "bold" if active == "current" else "regular", "mm")
    if active == "hidden":
        c.rr((294, 649, 342, 653), 2, FOREST)
    else:
        c.rr((738, 649, 786, 653), 2, FOREST)
    c.line([(96, 676), (984, 676)], DIVIDER, 1)


def notification(c, top, app, title, body, meta, action):
    c.ellipse((104, top, 156, top+52), SOFT)
    c.text(130, top+26, "示", 21, FOREST, "bold", "mm")
    c.text(178, top-2, app, 18, FOREST, "bold")
    c.text(178, top+32, title, 28, INK, "bold")
    c.text(178, top+77, body, 21, SECONDARY)
    c.text(178, top+113, meta, 17, MUTED)
    c.rr((824, top+82, 968, top+138), 16, SOFT)
    c.text(896, top+110, action, 21, FOREST, "medium", "mm")


def bottom_note(c, value):
    c.rr((100, 1160, 980, 1222), 18, SOFT)
    c.text(540, 1191, value, 23, FOREST, "medium", "mm")


def make_one():
    c = base(1, "把一直挂着的通知收起来", "留着对应功能，只处理你选中的那一条")
    app_header(c, "current")
    notification(c, 716, "示例后台工具", "辅助服务正在运行", "一条用于展示的后台状态通知", "持续显示", "隐藏")
    c.line([(104, 900), (976, 900)], DIVIDER, 1)
    notification(c, 930, "示例互联服务", "设备已连接", "一条用于展示的连接状态通知", "刚刚", "隐藏")
    bottom_note(c, "点一下「隐藏」 · 默认长期（约 10 年）")
    return c.save("01-只收你选中的通知.png")


def make_two():
    c = base(2, "隐藏多久，由你决定", "点一下长期 · 长按可选 24 小时或 1 小时")
    app_header(c, "current")
    layer = Image.new("RGBA", c.image.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ld.rounded_rectangle(Card.box((56, 404, 1024, 1272)), radius=round(36*S), fill=(23, 36, 30, 24))
    c.image = Image.alpha_composite(c.image, layer)
    c.draw = ImageDraw.Draw(c.image)
    c.rr((180, 612, 900, 1118), 30, PAPER, shadow=True)
    c.text(232, 666, "隐藏多久？", 34, INK, "bold")
    c.text(232, 724, "选择后，只处理当前这条通知", 20, SECONDARY)
    options = [("长期（约 10 年）", 796), ("24 小时", 906), ("1 小时", 1016)]
    for index, (label, y) in enumerate(options):
        if index:
            c.line([(232, y-16), (848, y-16)], DIVIDER, 1)
        c.text(248, y+14, label, 29, INK, "medium")
        c.text(832, y+32, "›", 34, MUTED, "regular", "mm")
    bottom_note(c, "「长期」约 10 年，不是永久")
    return c.save("02-隐藏多久由你决定.png")


def make_three():
    c = base(3, "想提前找回，也可以", "在「已隐藏」中点恢复，结果以系统确认为准")
    app_header(c, "hidden")
    notification(c, 716, "示例后台工具", "辅助服务正在运行", "一条用于展示的后台状态通知", "长期隐藏 · 可提前恢复", "恢复")
    c.line([(104, 900), (976, 900)], DIVIDER, 1)
    notification(c, 930, "示例互联服务", "设备已连接", "一条用于展示的连接状态通知", "剩余约 22 小时", "恢复")
    bottom_note(c, "想提前找回，点「恢复」即可")
    return c.save("03-想提前找回也可以.png")


def shield(c, cx, cy, size):
    points = [(cx, cy), (cx+size*.38, cy+size*.13), (cx+size*.32, cy+size*.58),
              (cx+size*.20, cy+size*.79), (cx, cy+size*.94),
              (cx-size*.20, cy+size*.79), (cx-size*.32, cy+size*.58), (cx-size*.38, cy+size*.13)]
    c.polygon(points, SOFT)
    c.line(points + [points[0]], FOREST, 4)
    c.line([(cx-size*.17, cy+size*.43), (cx-size*.04, cy+size*.57), (cx+size*.20, cy+size*.29)], FOREST, 5)


def check_row(c, y, value):
    c.rr((130, y, 950, y+78), 20, MIST)
    c.ellipse((154, y+20, 192, y+58), SOFT)
    c.line([(164, y+39), (172, y+47), (184, y+32)], FOREST, 3)
    c.text(216, y+21, value, 25, INK, "medium")


def make_four():
    c = base(4, "本机处理，不申请联网权限", "开源、无广告、无统计、无强制登录")
    c.text(100, 450, "隐私与权限", 30, INK, "bold")
    shield(c, 540, 492, 112)
    c.text(540, 650, "你的通知，留在本机", 34, INK, "bold", "mm")
    check_row(c, 730, "不保存通知标题、正文或列表")
    check_row(c, 828, "通知使用权可随时在系统设置撤销")
    check_row(c, 926, "不含广告、第三方统计或账号 SDK")
    check_row(c, 1024, "核心功能完整免费")
    bottom_note(c, "Android 8.1+ · 无需 Root / Shizuku / ADB")
    return c.save("04-本机处理不申请联网权限.png")


def make_cover():
    c = Card(1600, 900)
    c.ellipse((1280, -230, 1740, 230), SOFT)
    c.ellipse((-210, 690, 190, 1090), SOFT)

    # Decorative notification sheets stay outside the central crop-safe area.
    c.rr((-100, 560, 360, 770), 34, PAPER, shadow=True)
    c.ellipse((42, 616, 104, 678), SOFT)
    c.rr((132, 614, 292, 634), 10, DIVIDER)
    c.rr((132, 655, 318, 673), 9, MIST)
    c.rr((132, 700, 236, 736), 12, SOFT)

    c.rr((1240, 168, 1690, 382), 34, PAPER, shadow=True)
    c.ellipse((1300, 224, 1362, 286), SOFT)
    c.rr((1390, 222, 1560, 242), 10, DIVIDER)
    c.rr((1390, 263, 1610, 281), 9, MIST)
    c.rr((1470, 316, 1580, 352), 12, SOFT)

    c.icon(756, 62, 88)
    c.text(800, 184, "通知收纳", 42, FOREST, "bold", "ma")
    c.text(800, 286, "把一直挂着的通知收起来", 72, INK, "bold", "ma")
    c.text(800, 420, "后台状态、连接提醒，只收你选中的那一条", 32, SECONDARY, "regular", "ma")

    badges = [(500, 640, "开源"), (660, 900, "无联网权限"), (920, 1100, "无广告")]
    for x1, x2, label in badges:
        c.rr((x1, 522, x2, 588), 20, PAPER)
        c.text((x1+x2)/2, 555, label, 26, FOREST, "medium", "mm")
    c.text(800, 670, "不自动处理新通知 · 不关闭对应应用或后台功能", 26, SECONDARY, "regular", "ma")
    c.text(800, 758, "开源 Android 小工具", 23, MUTED, "regular", "ma")

    # A centered 1:1 crop spans x=350..1250; all wording must survive it.
    for item in c.text_bounds:
        x1, y1, x2, y2 = item["bounds"]
        if x1 < 350 or x2 > 1250 or y1 < 20 or y2 > 880:
            raise ValueError(f"Cover text outside central crop-safe area: {item}")
    c.save("00-题图-只收你选中的通知.png")
    return c.text_bounds


manifest = {
    "size": [W, H],
    "cover_size": [1600, 900],
    "format": "RGB PNG",
    "illustrations_not_raw_device_screenshots": True,
    "generic_demo_notifications_only": True,
    "files": [],
}
manifest["cover"] = {"file": "00-题图-只收你选中的通知.png", "text_bounds": make_cover()}
for maker in (make_one, make_two, make_three, make_four):
    bounds = maker()
    manifest["files"].append({"text_elements": len(bounds), "text_bounds": bounds})

readme = """通知收纳 v1.4.3 · 酷安首发宣传图

题图：1 张，1600 × 900，16:9 RGB PNG；关键文字位于中央裁剪安全区。
配图：4 张，1080 × 1440，RGB PNG。
顺序：题图 → 核心功能 → 时长选择 → 恢复入口 → 隐私权限。
使用场景：后台运行、设备连接、系统服务等长期驻留的状态通知。

这些图片是根据 App 实际界面与配色绘制的宣传示意图，不是原始设备截图。
所有通知应用、标题和正文均为通用示例，不包含真实通知、联系人或支付信息。
图片未放赞赏码、网盘二维码、下载返利或机型适配承诺。

适合酷安动态和首发文章配图。如果平台后台强制要求原始应用截图，
请另取无隐私的实机截图，不要把这些宣传示意图标记为原机截图。
"""
(OUT / "使用说明.txt").write_text(readme, encoding="utf-8")
(OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

zip_path = ROOT / "dist" / "酷安首发图-v1.4.3.zip"
with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as z:
    for path in sorted(OUT.glob("*.png")):
        z.write(path, arcname=path.name)
    z.write(OUT / "使用说明.txt", arcname="使用说明.txt")

for path in sorted(OUT.glob("*.png")):
    expected_size = (1600, 900) if path.name.startswith("00-") else (W, H)
    with Image.open(path) as image:
        if image.size != expected_size or image.mode != "RGB":
            raise ValueError(f"Unexpected output: {path} {image.size} {image.mode}")
        image.verify()
    print(f"{path.name}\t{path.stat().st_size} bytes")

PUBLIC_OUT = ROOT / "docs" / "promotion" / "coolapk" / "v1.4.3"
PUBLIC_OUT.mkdir(parents=True, exist_ok=True)
PUBLIC_NAMES = {
    "00-题图-只收你选中的通知.png": "00-题图.png",
    "01-只收你选中的通知.png": "01-核心功能.png",
    "02-隐藏多久由你决定.png": "02-时长选择.png",
    "03-想提前找回也可以.png": "03-恢复入口.png",
    "04-本机处理不申请联网权限.png": "04-隐私权限.png",
}
for source_name, public_name in PUBLIC_NAMES.items():
    shutil.copyfile(OUT / source_name, PUBLIC_OUT / public_name)

print(f"ZIP\t{zip_path}\t{zip_path.stat().st_size} bytes")
print(f"Public images\t{PUBLIC_OUT}")
print("Text bounds and image decoding checks passed.")
