#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
岁余 · 抖音图文宣传素材生成器
输出 1080×1440（3:4）竖版图文，共 9 张，含封面、6 个功能点、隐私与对比总结。
用法：python3 tools/gen_promo.py
"""
import os
import math
import random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "docs", "screenshots")
OUT = os.path.join(os.path.dirname(ROOT), "岁余-抖音图文")
os.makedirs(OUT, exist_ok=True)

W, H = 1080, 1440
CREAM_TOP = (250, 246, 237)
CREAM_BOTTOM = (243, 236, 223)
PINE = (30, 58, 49)
PINE_SOFT = (66, 92, 80)
GOLD = (176, 134, 72)
GOLD_SOFT = (208, 178, 132)
MUTED = (106, 104, 94)
MUTED_LIGHT = (140, 136, 124)
WHITE = (253, 251, 246)

SONGTI = "/System/Library/Fonts/Supplemental/Songti.ttc"
SANS = "/System/Library/Fonts/Hiragino Sans GB.ttc"
AVENIR = "/System/Library/Fonts/Avenir Next.ttc"

def serif(size, weight="bold"):
    idx = {"black": 0, "bold": 1, "light": 3, "regular": 6}[weight]
    return ImageFont.truetype(SONGTI, size, index=idx)

def sans(size, bold=False):
    return ImageFont.truetype(SANS, size, index=2 if bold else 0)

def latin(size, weight="demi"):
    idx = {"bold": 0, "demi": 2, "medium": 5, "regular": 7}[weight]
    return ImageFont.truetype(AVENIR, size, index=idx)

# ────────────────────────── 基础绘制 ──────────────────────────

def background():
    """奶油纸色 + 轻噪点 + 右上暖光 + 底部淡金弧线"""
    bg = Image.new("RGB", (W, H), CREAM_TOP)
    top = Image.new("RGB", (W, H), CREAM_TOP)
    bottom = Image.new("RGB", (W, H), CREAM_BOTTOM)
    d = ImageDraw.Draw(bottom)
    d.rectangle((0, 0, W, H), fill=CREAM_BOTTOM)
    mask = Image.new("L", (W, H))
    md = ImageDraw.Draw(mask)
    for y in range(H):
        md.line((0, y, W, y), fill=int(255 * (y / H)))
    bg = Image.composite(bottom, top, mask)

    # 噪点，模拟纸张颗粒
    noise = Image.effect_noise((W, H), 22).convert("RGB")
    bg = Image.blend(bg, noise, 0.02)

    # 右上暖光
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((W - 620, -420, W + 320, 520), fill=(*GOLD, 18))
    glow = glow.filter(ImageFilter.GaussianBlur(160))
    bg = Image.alpha_composite(bg.convert("RGBA"), glow)

    # 底部细弧线装饰
    deco = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dd = ImageDraw.Draw(deco)
    for r, alpha in ((760, 26), (860, 18), (960, 12)):
        dd.arc((-260, H - 620 + (960 - r) // 2, 260 + 2 * r, H + 360), 180, 360, fill=(*GOLD, alpha), width=2)
    return Image.alpha_composite(bg, deco)

def text_len(draw, text, font):
    return draw.textlength(text, font=font)

def wrap(draw, text, font, max_width):
    """按「中文逐字、英文/数字整词」换行，避免把 App 拆成 Ap / p。"""
    import re
    lines = []
    for para in text.split("\n"):
        tokens = re.findall(r"[A-Za-z0-9]+(?:[.%/+\-][A-Za-z0-9]+)*|.", para)
        cur = ""
        no_line_start = "，。；：、！？）」』…%,.;:!?)]}"
        for token in tokens:
            if token in no_line_start:
                # 标点不另起一行：能塞就塞，塞不下就贴到上一行末尾
                if cur:
                    cur += token
                elif lines:
                    lines[-1] += token
                else:
                    cur = token
                continue
            if draw.textlength(cur + token, font=font) <= max_width:
                cur += token
            else:
                lines.append(cur)
                cur = token
        lines.append(cur)
    return lines

def draw_paragraph(draw, text, font, fill, x, y, max_width, line_height):
    lines = wrap(draw, text, font, max_width)
    for i, line in enumerate(lines):
        draw.text((x, y + i * line_height), line, font=font, fill=fill)
    return y + len(lines) * line_height

def draw_rich_headline(draw, lines, x, y, size=82, line_height=104, fill=PINE):
    """标题：数字与英文用 Avenir，中文用宋体加粗"""
    font_cn = serif(size, "bold")
    font_num = latin(int(size * 0.92), "bold")
    for i, line in enumerate(lines):
        cy = y + i * line_height
        cx = x
        for ch in line:
            f = font_num if (ch.isascii() and ch.strip()) else font_cn
            draw.text((cx, cy), ch, font=f, fill=fill)
            cx += draw.textlength(ch, font=f)
    return y + len(lines) * line_height

def page_header(draw, index, total=9):
    # 品牌
    draw.ellipse((80, 74, 92, 86), fill=GOLD)
    draw.text((104, 62), "岁余", font=serif(34, "bold"), fill=PINE)
    draw.text((104, 104), "家庭财务账本", font=sans(19), fill=MUTED_LIGHT)
    draw.text((W - 80 - draw.textlength(f"{index:02d} / {total:02d}", font=latin(22, "demi")), 70),
              f"{index:02d} / {total:02d}", font=latin(22, "demi"), fill=MUTED_LIGHT)
    draw.line((80, 150, W - 80, 150), fill=(*GOLD_SOFT, 110), width=2)
    draw.line((80, 150, 240, 150), fill=GOLD, width=3)

def page_footer(draw):
    draw.line((80, 1372, W - 80, 1372), fill=(*GOLD_SOFT, 90), width=1)
    draw.text((80, 1392), "岁余 · 日计不足，岁计有余", font=serif(24, "regular"), fill=MUTED)
    label = "LOCAL FIRST · NO ADS"
    draw.text((W - 80 - draw.textlength(label, font=latin(17, "medium")), 1396),
              label, font=latin(17, "medium"), fill=(*MUTED_LIGHT, 255))

def kicker(draw, text, y=192):
    draw.rectangle((80, y, 86, y + 40), fill=GOLD)
    # 字距拉开一点，更像小标签
    x = 106
    font = sans(24, True)
    for ch in text:
        draw.text((x, y + 2), ch, font=font, fill=GOLD)
        x += draw.textlength(ch, font=font) + 3

def bullets(draw, items, x, y, width=470):
    font = sans(30)
    for i, item in enumerate(items):
        cy = y + i * 62
        draw.ellipse((x, cy + 18, x + 9, cy + 27), fill=GOLD)
        draw.text((x + 24, cy), item, font=font, fill=PINE_SOFT)

def make_phone(path, width=404, top_visible=None):
    """带阴影与边框的手机截图；top_visible 可裁掉底部多余部分"""
    src = Image.open(path).convert("RGB")
    if top_visible:
        src = src.crop((0, 0, src.width, min(src.height, top_visible)))
    ratio = width / src.width
    height = int(src.height * ratio)
    screen = src.resize((width, height), Image.LANCZOS)
    bezel, radius = 9, 38
    fw, fh = width + bezel * 2, height + bezel * 2
    frame = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    fd = ImageDraw.Draw(frame)
    fd.rounded_rectangle((0, 0, fw - 1, fh - 1), radius=radius + bezel, fill=PINE)
    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width - 1, height - 1), radius=radius - 6, fill=255)
    frame.paste(screen, (bezel, bezel), mask)

    shadow = Image.new("RGBA", (fw + 160, fh + 160), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (80, 80, 80 + fw - 1, 80 + fh - 1), radius=radius + bezel, fill=(12, 26, 20, 72)
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(30))
    return shadow, frame

def paste_phone(card, path, x, y, width=380, top_visible=None):
    shadow, frame = make_phone(path, width, top_visible)
    card.alpha_composite(shadow, (x - 80, y - 80))
    card.alpha_composite(frame, (x, y))

def base_card(index, kick, headline, body, items, shot=None, shot_y=500, shot_x=620):
    card = background()
    draw = ImageDraw.Draw(card)
    page_header(draw, index)
    kicker(draw, kick)
    bottom = draw_rich_headline(draw, headline, 80, 258, size=78, line_height=100)
    body_y = bottom + 30
    max_w = 500 if shot else 920
    body_bottom = draw_paragraph(draw, body, sans(33), MUTED, 80, body_y, max_w, 56)
    bullets(draw, items, 80, body_bottom + 34, width=470)
    if shot:
        paste_phone(card, shot, shot_x, shot_y, 380)
    page_footer(draw)
    return card

# ────────────────────────── 九张图文 ──────────────────────────

def card_cover():
    card = background()
    draw = ImageDraw.Draw(card)
    page_header(draw, 1)

    # 上引文
    draw.text((80, 232), "今吾日计之而不足", font=serif(34, "light"), fill=GOLD)
    draw.text((80, 288), "岁计之而有余", font=serif(34, "light"), fill=GOLD)
    draw.text((80, 348), "—— 《庄子 · 庚桑楚》", font=sans(22), fill=MUTED_LIGHT)

    draw_rich_headline(draw, ["记账 App 很多", "能算清「日子」的很少"], 80, 448, size=86, line_height=114)

    draw_paragraph(
        draw,
        "从账单截图到家庭月报；从流水明细到财务体检；\n从个税测算到资产配置，一个 App 把家里的账算清楚。",
        sans(34), MUTED, 80, 718, 900, 58,
    )

    chips = ["截图导入", "家庭月报", "六维财务体检"]
    cw = [176, 176, 236]
    x = 80
    for text, w in zip(chips, cw):
        draw.rounded_rectangle((x, 862, x + w, 922), radius=30, fill=(255, 253, 247), outline=(*GOLD_SOFT, 170), width=2)
        tw = draw.textlength(text, font=sans(27))
        draw.text((x + (w - tw) / 2, 879), text, font=sans(27), fill=PINE)
        x += w + 16

    # 底部三卖点
    points = ["最多 9 张截图一次导入", "六维风险评分与建议", "数据只存本机，不上传"]
    for i, text in enumerate(points):
        cy = 1032 + i * 54
        draw.ellipse((80, cy + 14, 89, cy + 23), fill=GOLD)
        draw.text((104, cy), text, font=sans(27), fill=PINE_SOFT)

    # 印章
    seal = Image.new("RGBA", (132, 132), (0, 0, 0, 0))
    sd = ImageDraw.Draw(seal)
    sd.rounded_rectangle((0, 0, 131, 131), radius=14, fill=(*PINE, 240))
    sd.rectangle((8, 8, 123, 123), outline=(*GOLD_SOFT, 220), width=2)
    sd.text((34, 26), "岁", font=serif(46, "bold"), fill=WHITE)
    sd.text((34, 74), "余", font=serif(46, "bold"), fill=WHITE)
    card.alpha_composite(seal, (W - 80 - 132, 1092))

    draw.text((80, 1252), "不是又一个记账工具", font=sans(30, True), fill=PINE)
    draw.text((80, 1300), "而是一本会替你想一步的家庭账本", font=sans(25), fill=MUTED)
    page_footer(draw)
    return card

def card_import():
    return base_card(
        2, "01 · 账单导入",
        ["微信、支付宝、银行", "截图丢进来就行"],
        "端侧中文 OCR 逐行还原金额、商户与时间，识别结果先变成可编辑草稿，确认后才入账。",
        ["本机识别，图片不上传", "一次最多 9 张账单截图", "疑似重复自动标记"],
        shot=os.path.join(SHOTS, "03-账单导入复核.png"),
    )

def card_family():
    return base_card(
        3, "02 · 家庭账本",
        ["一个人记账", "全家人都看得懂"],
        "每位成员有自己的名字、头像与身份标签，收支按人分摊；谁花得多、谁结余多，一眼看清。",
        ["成员分账与独立统计", "配偶 / 子女 / 父母身份标签", "家庭总览与成员视角切换"],
        shot=os.path.join(SHOTS, "10-家庭与成员.png"),
    )

def card_report():
    return base_card(
        4, "03 · 自动月报",
        ["月底不用做表", "App 自己写完一份月报"],
        "总述、总览、环比、同比、成员分账、财务健康，自动生成；点分享，纯文本发家庭群，对方不装 App 也能看。",
        ["同比 / 环比自动计算", "自动洞察只陈述可核对事实", "纯文本月报，一键分享"],
        shot=os.path.join(SHOTS, "07-月度报表.png"),
    )

def card_ledger():
    return base_card(
        5, "04 · 流水明细",
        ["点开是明细", "长按就能多选删除"],
        "按日分组的完整流水账，找得到每一笔；改金额、改分类、批量删除，不用再翻账单截图。",
        ["按日分组，当日收支小计", "点按一笔直接编辑", "长按多选 / 全选 / 批量删除"],
        shot=os.path.join(SHOTS, "16-流水明细.png"),
    )

def card_health():
    return base_card(
        6, "05 · 财务体检 · 不只是记账",
        ["不止记流水", "还给你家做一次财务体检"],
        "填存款、理财、房产、负债与工作情况，结合固定收支算出净资产、应急金、负债率与六维风险评分。",
        ["资产 / 负债 / 工作画像", "六维雷达 + 综合评级", "按缺口给出可执行建议"],
        shot=os.path.join(SHOTS, "22-财务体检总览.png"),
    )

def card_tax():
    return base_card(
        7, "06 · 个税测算",
        ["工资之外", "年底这笔账也帮你算清"],
        "七项专项附加扣除逐项引导，年终奖两种计税方案对比，附模拟申报表——全部在本机计算。",
        ["子女教育 / 房贷 / 赡养老人等 7 项", "年终奖单独 or 合并，算完再选", "模拟申报表可保存分享"],
        shot=os.path.join(SHOTS, "14-个税测算结果.png"),
    )

def card_privacy():
    return base_card(
        8, "07 · 隐私与本地",
        ["账本不出设备", "也没有开屏广告"],
        "所有数据只存在这台手机：没有账号、没有云同步、没有广告位。支持金额遮罩与 CSV / JSON 导出备份。",
        ["本地存储，无需登录", "一键金额遮罩，公共场合也安全", "CSV + JSON 双格式备份"],
        shot=os.path.join(SHOTS, "11-偏好设置.png"),
    )

def card_compare():
    card = background()
    draw = ImageDraw.Draw(card)
    page_header(draw, 9)
    kicker(draw, "08 · 与常见记账软件的不同")
    draw_rich_headline(draw, ["把「记账」往后", "多走两步"], 80, 254, size=76, line_height=98)

    # 表头
    y = 486
    draw.line((80, y, W - 80, y), fill=(*GOLD_SOFT, 120), width=2)
    draw.text((80, y + 14), "常见做法", font=sans(24, True), fill=MUTED_LIGHT)
    draw.text((520, y + 14), "岁余", font=sans(24, True), fill=GOLD)

    rows = [
        ("手打每一笔", "账单截图离线识别，草稿复核后入账"),
        ("单人账本，共享要会员", "家庭账本 + 成员分账 + 纯文本月报"),
        ("图表很多，结论很少", "自动总述 + 同比环比 + 可分享月报"),
        ("记完就结束了", "资产 · 负债 · 工作 → 六维体检"),
        ("个税另找工具", "专项附加扣除 + 年终奖 + 模拟申报"),
    ]
    row_font_left = sans(27)
    row_font_right = sans(29, True)
    for i, (left, right) in enumerate(rows):
        ry = y + 82 + i * 132
        draw.line((80, ry - 22, W - 80, ry - 22), fill=(*GOLD_SOFT, 70), width=1)
        draw.text((80, ry), left, font=row_font_left, fill=MUTED_LIGHT)
        # 右侧可能两行
        lines = wrap(draw, right, row_font_right, 480)
        for j, line in enumerate(lines):
            draw.text((520, ry + j * 42), line, font=row_font_right, fill=PINE)

    draw.line((80, 1246, W - 80, 1246), fill=(*GOLD_SOFT, 120), width=2)
    draw.text((80, 1270), "本地优先 · 无广告 · 数据不出设备", font=sans(27, True), fill=PINE)
    draw.text((80, 1318), "「岁余」取自《庄子》：今吾日计之而不足，岁计而有余。", font=serif(24, "regular"), fill=MUTED)
    page_footer(draw)
    return card

def main():
    cards = [
        card_cover(),
        card_import(),
        card_family(),
        card_report(),
        card_ledger(),
        card_health(),
        card_tax(),
        card_privacy(),
        card_compare(),
    ]
    names = [
        "01-封面-记账App很多.png",
        "02-截图导入.png",
        "03-家庭账本.png",
        "04-自动月报.png",
        "05-流水明细.png",
        "06-财务体检.png",
        "07-个税测算.png",
        "08-隐私本地.png",
        "09-对比总结.png",
    ]
    for img, name in zip(cards, names):
        img.convert("RGB").save(os.path.join(OUT, name), quality=95, optimize=True)
        print("saved", name)
    print("output:", OUT)

if __name__ == "__main__":
    main()
