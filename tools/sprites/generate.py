#!/usr/bin/env python3
"""
Генератор спрайтов персонажа.

Спрайты рисуются не руками в редакторе, а этим файлом: силуэт задаётся
формой (кривые плеч, длина рукава, вырез, толщина брони), а тени и контур
расставляются одинаково для всех предметов. Так шестьдесят вещей выходят
одной серией, а не шестьюдесятью разными почерками, и правка света — это
правка одной функции, а не шестидесяти картинок.

Результат — таблица символов в `CharacterSprites.kt`. В приложении она
только читается: рисование в рантайме ничего не генерирует.

    python3 tools/sprites/generate.py            # обновить Kotlin
    python3 tools/sprites/generate.py --preview  # ещё и картинку для глаз

Сетка «куклы» 32×56 кладётся на холст 128×128 с шагом 2 пикселя.

Символы: '.' пусто, 'o' контур, 'd' тень, 'm' основной тон,
'l' свет, 'w' блик, 'k' кожа, 'K' тень кожи, 'e' глаз.
"""

import argparse
import math
import pathlib
import sys

DOLL_W, DOLL_H = 32, 56
CENTER = 15.5

EMPTY = '.'


class Grid:
    def __init__(self, w=DOLL_W, h=DOLL_H):
        self.w, self.h = w, h
        self.px = [[EMPTY] * w for _ in range(h)]

    def set(self, x, y, ch):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = ch

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y][x]
        return EMPTY

    def span(self, y, x0, x1, ch):
        for x in range(int(round(x0)), int(round(x1)) + 1):
            self.set(x, y, ch)

    def rows(self):
        return [''.join(r) for r in self.px]

    def bbox(self):
        xs, ys = [], []
        for y in range(self.h):
            for x in range(self.w):
                if self.px[y][x] != EMPTY:
                    xs.append(x); ys.append(y)
        if not xs:
            return None
        return min(xs), min(ys), max(xs), max(ys)

    def crop(self):
        """Обрезка до содержимого: хранить пустые поля в данных незачем."""
        b = self.bbox()
        if b is None:
            return 0, 0, []
        x0, y0, x1, y1 = b
        rows = [''.join(self.px[y][x0:x1 + 1]) for y in range(y0, y1 + 1)]
        return x0, y0, rows


# ─────────────────────────── формы ───────────────────────────

# Пропорции куклы. Держатся здесь все вместе: одежда строится по этим же
# числам, и разъехавшийся на единицу рукав видно сразу на всех шестидесяти
# вещах, а не на той одной, которую правили.
HEAD_TOP, HEAD_BOTTOM = 1, 12
NECK_TOP, NECK_BOTTOM = 13, 14
TORSO_TOP, TORSO_BOTTOM = 15, 34
ARM_TOP, ARM_BOTTOM = 16, 33
LEG_TOP, LEG_BOTTOM = 35, 51
FOOT_TOP, FOOT_BOTTOM = 52, 54

ARM_OFFSET = 7.8
LEG_GAP = 3.6
LEG_HALF = 2.0


def body_half_width(y):
    """
    Полуширина туловища по строке. Плечи шире таза, талия уже обоих —
    без этого перепада фигура читается как столбик.
    """
    y = min(max(y, TORSO_TOP), TORSO_BOTTOM)
    t = (y - TORSO_TOP) / float(TORSO_BOTTOM - TORSO_TOP)
    shoulder, waist, hip = 5.2, 3.8, 4.6
    if t <= 0.45:
        return shoulder + (waist - shoulder) * (t / 0.45)
    return waist + (hip - waist) * ((t - 0.45) / 0.55)


def head_half_width(y):
    """Полуширина головы: скулы шире лба и подбородка."""
    t = (y - HEAD_TOP) / float(HEAD_BOTTOM - HEAD_TOP)
    if t < 0 or t > 1:
        return 0
    return 5.0 * math.sin(math.pi * (0.20 + 0.70 * t))


def arm_half(y):
    """Плечо толще запястья: ровная палка выглядит протезом."""
    if y >= ARM_BOTTOM - 2:
        return 1.7                            # кисть
    t = (y - ARM_TOP) / float(ARM_BOTTOM - ARM_TOP)
    return 1.5 - 0.4 * t


def arm_offset(y):
    return ARM_OFFSET


def shade_for(x, half):
    """
    Свет падает слева сверху: левая треть светлее, правая четверть в тени.
    Одно правило на все предметы — иначе куртка и сапоги освещены по-разному.
    """
    t = x / max(half, 0.001)                 # -1 слева, +1 справа
    if t < -0.45:
        return 'l'
    if t > 0.45:
        return 'd'
    return 'm'


def outline(grid, chars='dmlwkKe'):
    """
    Контур по краю силуэта. Рисуется последним и только снаружи: контур
    внутри съедает и без того небольшую площадь спрайта.
    """
    edge = []
    for y in range(grid.h):
        for x in range(grid.w):
            if grid.get(x, y) != EMPTY:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if grid.get(x + dx, y + dy) in chars:
                    edge.append((x, y))
                    break
    for x, y in edge:
        grid.set(x, y, 'o')
    return grid


# ─────────────────────────── тело ───────────────────────────

def base_body():
    """
    Голая фигура: она рисуется всегда и под всем остальным. Без неё
    персонаж без вещей — это пустое место, а не человек без вещей.
    """
    g = Grid()

    def limb(y, cx, half, lit):
        for x in range(int(round(cx - half)), int(round(cx + half)) + 1):
            g.set(x, y, 'k' if (x - cx < half * 0.3) == lit else 'K')

    for y in range(HEAD_TOP, HEAD_BOTTOM + 1):
        half = head_half_width(y)
        if half >= 0.5:
            limb(y, CENTER, half, True)

    # глаза: два пикселя, иначе на такой голове это уже не глаза, а маска
    g.set(13, 7, 'e')
    g.set(18, 7, 'e')

    for y in range(NECK_TOP, NECK_BOTTOM + 1):
        limb(y, CENTER, 1.8, True)

    for y in range(TORSO_TOP, TORSO_BOTTOM + 1):
        limb(y, CENTER, body_half_width(y), True)

    # руки висят вдоль тела, но не касаются его: между рукой и боком
    # остаётся пустая колонка, иначе фигура читается как один столбик
    for y in range(ARM_TOP, ARM_BOTTOM + 1):
        for side in (-1, 1):
            limb(y, CENTER + side * arm_offset(y), arm_half(y), side < 0)

    for y in range(LEG_TOP, LEG_BOTTOM + 1):
        for side in (-1, 1):
            limb(y, CENTER + side * LEG_GAP, LEG_HALF, side < 0)

    # ступни вытянуты вперёд: без них персонаж стоит на культях
    for y in range(FOOT_TOP, FOOT_BOTTOM + 1):
        for side in (-1, 1):
            cx = CENTER + side * LEG_GAP
            g.span(y, cx - LEG_HALF - 0.4, cx + LEG_HALF + 0.6, 'K')

    return outline(g)


# ─────────────────────── одежда: помощники ───────────────────────

def garment(y0, y1, width_at, chars=True, hem=None):
    """Кусок одежды по строкам: ширина задаётся функцией от строки."""
    g = Grid()
    for y in range(y0, y1 + 1):
        half = width_at(y)
        if half <= 0:
            continue
        for x in range(int(round(CENTER - half)), int(round(CENTER + half)) + 1):
            g.set(x, y, shade_for(x - CENTER, half))
    if hem:
        hem(g)
    return g


def sleeves(g, y0, y1, half_at, thickness=1.6):
    """
    Рукава поверх рук: без них любая кофта выглядит жилетом.

    По шву рукава идёт тёмная колонка. Без неё рукав сливается с боком в
    один прямоугольник, и вся фигура снова читается как коробка — ровно то,
    от чего уходили, когда разводили руки и туловище.
    """
    for y in range(y0, y1 + 1):
        for side in (-1, 1):
            cx = CENTER + side * arm_offset(y)
            half = arm_half(y) + thickness - 1.5
            for x in range(int(round(cx - half)), int(round(cx + half)) + 1):
                g.set(x, y, 'l' if side < 0 else 'd')
            seam = CENTER + side * (body_half_width(y) + 1.0)
            g.set(int(round(seam)), y, 'o')
    return g


def torso_width(extra=0.0):
    def f(y):
        w = body_half_width(min(max(y, 16), 35)) + 0.9 + extra
        return w
    return f


def legs_shape(y0, y1, half=2.6, gap=LEG_GAP, taper=0.0):
    g = Grid()
    for y in range(y0, y1 + 1):
        t = (y - y0) / max(y1 - y0, 1)
        h = half - taper * t
        for side in (-1, 1):
            cx = CENTER + side * gap
            for x in range(int(round(cx - h)), int(round(cx + h)) + 1):
                g.set(x, y, shade_for(x - cx, h) if side < 0 else
                      ('d' if x - cx > 0 else 'm'))
    return g


def stripe(g, y, ch='w', inset=0):
    b = g.bbox()
    if not b:
        return g
    x0, _, x1, _ = b
    for x in range(x0 + inset, x1 - inset + 1):
        if g.get(x, y) != EMPTY:
            g.set(x, y, ch)
    return g


# ─────────────────────────── каталог ───────────────────────────

def build():
    """id предмета → спрайт. Ключи совпадают с items.json."""
    out = {}

    def add(key, g):
        out[key] = g

    # ── волосы ──────────────────────────────────────────────
    def hair(y0, y1, spread, back=0, bun=False, tail=False, shaved=False):
        g = Grid()
        for y in range(y0, y1 + 1):
            half = head_half_width(y) + spread
            if half < 0.5:
                continue
            lo, hi = CENTER - half, CENTER + half
            if shaved and y > y0 + 3:
                lo, hi = CENTER - half * 0.45, CENTER + half * 0.45
            for x in range(int(round(lo)), int(round(hi)) + 1):
                if y > y0 + 2 and abs(x - CENTER) < half - 1.4 and not shaved:
                    continue                      # чёлка, а не шапка волос
                g.set(x, y, shade_for(x - CENTER, half))
        for y in range(y1 + 1, y1 + 1 + back):
            half = head_half_width(min(y, 13)) + spread
            for side in (-1, 1):
                cx = CENTER + side * (half - 0.6)
                for x in range(int(round(cx - 1)), int(round(cx + 1)) + 1):
                    g.set(x, y, 'd' if side > 0 else 'l')
        if bun:
            for y in range(y0 - 3, y0 + 1):
                r = 2.2 - abs(y - (y0 - 1.5)) * 0.6
                g.span(y, CENTER - r, CENTER + r, 'm')
        if tail:
            for y in range(y1, y1 + 8):
                g.span(y, CENTER + 4.4, CENTER + 5.8, 'd')
        return outline(g)

    add('hair_short', hair(1, 7, 0.5))
    add('hair_long', hair(1, 7, 0.7, back=9))
    add('hair_tail', hair(1, 7, 0.5, tail=True))
    add('hair_shaved', hair(1, 7, 0.3, shaved=True))
    add('hair_bun', hair(1, 7, 0.5, bun=True))

    # ── лицо ────────────────────────────────────────────────
    g = Grid()
    g.set(13, 8, 'e'); g.set(18, 8, 'e')
    add('face_clean', g)

    g = Grid()
    g.set(13, 8, 'e'); g.set(18, 8, 'e')
    for y in range(10, 14):
        half = head_half_width(y) - 0.6
        for x in range(int(round(CENTER - half)), int(round(CENTER + half)) + 1):
            g.set(x, y, shade_for(x - CENTER, half))
    add('face_beard', outline(g))

    g = Grid()
    for x, y in ((12, 8), (13, 8), (14, 8), (17, 8), (18, 8), (19, 8)):
        g.set(x, y, 'w')
    g.set(15, 8, 'o'); g.set(16, 8, 'o')
    g.set(13, 8, 'e'); g.set(18, 8, 'e')
    add('face_glasses', g)

    # ── торс ────────────────────────────────────────────────
    def top(y1, extra=0.0, sleeve_to=None, collar=None, plate=False,
            hoodie=False, stripes=(), thickness=1.6):
        g = garment(16, y1, torso_width(extra))
        if sleeve_to:
            sleeves(g, 17, sleeve_to, None, thickness)
        if collar == 'v':
            for y in range(16, 19):
                r = 2.4 - (y - 16) * 0.8
                g.span(y, CENTER - r, CENTER + r, EMPTY)
        if collar == 'round':
            g.span(16, CENTER - 2.2, CENTER + 2.2, EMPTY)
        if hoodie:
            for y in range(13, 17):
                half = head_half_width(min(y, 13)) + 1.6
                for x in range(int(round(CENTER - half)), int(round(CENTER + half)) + 1):
                    g.set(x, y, shade_for(x - CENTER, half))
        if plate:
            # рёбра кирасы: без них латы читаются как свитер стального цвета
            for y in range(18, y1, 4):
                stripe(g, y, 'w', inset=1)
            for y in range(16, y1 + 1):
                g.set(int(CENTER), y, 'l')
        for y in stripes:
            stripe(g, y, 'w', inset=1)
        return outline(g)

    add('chest_tshirt', top(30, 0.0, sleeve_to=22, collar='round'))
    add('chest_hoodie', top(33, 0.6, sleeve_to=32, hoodie=True, thickness=1.9))
    add('chest_sweater', top(32, 0.5, sleeve_to=32, collar='round', thickness=1.8))
    add('chest_jacket', top(33, 0.7, sleeve_to=32, collar='v', thickness=1.9))
    add('chest_sportwear', top(29, 0.0, sleeve_to=21, collar='round', stripes=(20, 21)))
    add('chest_shirt', top(31, 0.1, sleeve_to=32, collar='v'))
    add('chest_pyjama', top(32, 0.4, sleeve_to=31, collar='round', stripes=(23, 27)))
    add('chest_cuirass', top(33, 1.1, sleeve_to=20, plate=True, thickness=2.1))
    add('chest_robe', top(50, 1.4, sleeve_to=33, collar='v', thickness=2.2))
    add('chest_leather', top(32, 0.6, sleeve_to=24, collar='v', thickness=1.8))

    # ── ноги ────────────────────────────────────────────────
    add('legs_jeans', outline(legs_shape(34, 51, 2.7, 2.9)))
    add('legs_sweatpants', outline(stripe(legs_shape(34, 51, 3.0, 3.0), 40, 'w')))
    add('legs_shorts', outline(legs_shape(34, 42, 3.0, 2.9)))
    g = legs_shape(34, 51, 3.1, 3.0)
    for y in range(36, 51, 4):
        stripe(g, y, 'w')
    add('legs_greaves', outline(g))
    add('legs_pyjama', outline(stripe(legs_shape(34, 51, 2.9, 2.9), 45, 'w')))

    # ── обувь ───────────────────────────────────────────────
    def shoe(y0, y1, front=2.8, back=2.4, sole=True):
        g = Grid()
        for y in range(y0, y1 + 1):
            for side in (-1, 1):
                cx = CENTER + side * LEG_GAP
                g.span(y, cx - back, cx + front, shade_for(0, 1))
        for y in range(y0, y1 + 1):
            for side in (-1, 1):
                cx = CENTER + side * LEG_GAP
                for x in range(int(round(cx - back)), int(round(cx + front)) + 1):
                    g.set(x, y, 'l' if side < 0 else 'd')
        if sole:
            for side in (-1, 1):
                cx = CENTER + side * LEG_GAP
                g.span(y1, cx - back, cx + front, 'o')
        return outline(g)

    add('boots_sneakers', shoe(49, 53))
    add('boots_boots', shoe(46, 53, front=2.6, back=2.6))
    add('boots_sabatons', shoe(46, 53, front=3.2, back=2.6))
    add('boots_slippers', shoe(50, 53, front=2.8, back=2.0, sole=False))

    # ── перчатки ────────────────────────────────────────────
    def hands(y0, y1, thickness=1.8):
        g = Grid()
        for y in range(y0, y1 + 1):
            for side in (-1, 1):
                cx = CENTER + side * arm_offset(y)
                half = arm_half(y) + thickness - 1.5
                for x in range(int(round(cx - half)), int(round(cx + half)) + 1):
                    g.set(x, y, 'l' if side < 0 else 'd')
        return outline(g)

    add('gloves_cloth', hands(31, 34))
    add('gloves_leather', hands(30, 34))
    add('gloves_bracers', hands(27, 34, thickness=2.1))

    # ── пояс ────────────────────────────────────────────────
    def belt(y0, y1, buckle=True, pouches=False):
        g = Grid()
        for y in range(y0, y1 + 1):
            half = body_half_width(min(y, 35)) + 1.1
            for x in range(int(round(CENTER - half)), int(round(CENTER + half)) + 1):
                g.set(x, y, shade_for(x - CENTER, half))
        if buckle:
            g.set(int(CENTER), y0, 'w')
            g.set(int(CENTER), y1, 'w')
        if pouches:
            for side in (-1, 1):
                cx = CENTER + side * 4.2
                for y in range(y1 + 1, y1 + 4):
                    g.span(y, cx - 1.2, cx + 1.2, 'd' if side > 0 else 'm')
        return outline(g)

    add('belt_leather', belt(33, 34))
    add('belt_pouches', belt(33, 34, pouches=True))
    add('belt_plate', belt(32, 35))

    # ── наплечники ──────────────────────────────────────────
    def pauldrons(rows=4, spread=1.0, fur=False):
        """
        Накладка на плече — купол, а не полка: прямоугольник поперёк фигуры
        читается как стол, на который персонаж опёрся.
        """
        g = Grid()
        for i in range(rows):
            y = TORSO_TOP + i
            t = i / float(max(rows - 1, 1))
            w = (2.4 + spread) * math.sqrt(max(1.0 - t * t * 0.75, 0.0))
            for side in (-1, 1):
                cx = CENTER + side * (arm_offset(y) - 0.4)
                for x in range(int(round(cx - w)), int(round(cx + w)) + 1):
                    g.set(x, y, shade_for((x - cx) * (1 if side < 0 else -1), w))
                    if side > 0:
                        g.set(x, y, 'd' if x > cx else 'm')
        if fur:
            for y in range(TORSO_TOP - 1, TORSO_TOP + 3):
                half = body_half_width(TORSO_TOP) + spread + 1.6
                for x in range(int(round(CENTER - half)), int(round(CENTER + half)) + 1):
                    if (x + y) % 2 == 0:
                        g.set(x, y, 'm')
        return outline(g)

    add('shoulders_leather', pauldrons(3, 0.6))
    add('shoulders_plate', pauldrons(5, 1.6))
    add('shoulders_fur', pauldrons(3, 1.0, fur=True))

    # ── плащи ───────────────────────────────────────────────
    def cloak(y1, spread=2.4, scarf=False):
        g = Grid()
        if scarf:
            for y in range(15, 20):
                half = body_half_width(16) + 1.2
                for x in range(int(round(CENTER - half)), int(round(CENTER + half)) + 1):
                    g.set(x, y, shade_for(x - CENTER, half))
            for y in range(20, 27):
                g.span(y, CENTER + 1.0, CENTER + 3.0, 'd')
            return outline(g)
        for y in range(15, y1 + 1):
            t = (y - 15) / max(y1 - 15, 1)
            half = body_half_width(min(y, 35)) + 1.2 + spread * t
            for side in (-1, 1):
                cx = CENTER + side * (half - 1.2)
                for x in range(int(round(cx - 2.2)), int(round(cx + 2.2)) + 1):
                    if abs(x - CENTER) < body_half_width(min(y, 35)) - 0.5:
                        continue                  # плащ по бокам, грудь открыта
                    g.set(x, y, 'l' if side < 0 else 'd')
        return outline(g)

    add('cloak_short', cloak(34))
    add('cloak_long', cloak(50, spread=3.4))
    add('cloak_scarf', cloak(0, scarf=True))

    # ── головные уборы ──────────────────────────────────────
    def headwear(y0, y1, spread=1.0, brim=0, closed=False, crown=False, drape=0,
                 open_face=False):
        g = Grid()
        for y in range(y0, y1 + 1):
            half = head_half_width(min(max(y, HEAD_TOP), HEAD_BOTTOM)) + spread
            for x in range(int(round(CENTER - half)), int(round(CENTER + half)) + 1):
                g.set(x, y, shade_for(x - CENTER, half))
        if brim:
            half = head_half_width(6) + spread + brim
            g.span(y1, CENTER - half, CENTER + half, 'd')
            g.span(y1 + 1, CENTER - half + 1, CENTER + half, 'o')
        if closed:
            # Шлем доходит до шеи. Иначе между ним и плечами остаётся дыра,
            # и голова читается как отдельный предмет, висящий над фигурой
            for y in range(y1 + 1, NECK_BOTTOM + 1):
                half = head_half_width(min(y, HEAD_BOTTOM)) + spread * 0.7
                for x in range(int(round(CENTER - half)), int(round(CENTER + half)) + 1):
                    g.set(x, y, shade_for(x - CENTER, half))
            g.span(7, CENTER - 3.4, CENTER + 3.4, 'o')      # смотровая щель
            g.span(8, CENTER - 2.6, CENTER + 2.6, 'd')
        if open_face:
            # Из капюшона должно быть видно лицо: иначе на плечах шар,
            # а не человек, накинувший капюшон
            for y in range(HEAD_TOP + 3, HEAD_BOTTOM - 1):
                half = head_half_width(y) * 0.62
                g.span(y, CENTER - half, CENTER + half, EMPTY)
        if drape:
            # Капюшон падает на плечи по бокам, лицо остаётся открытым
            for y in range(y1 + 1, min(y1 + 1 + drape, TORSO_TOP + 3)):
                half = head_half_width(HEAD_BOTTOM) + spread
                for side in (-1, 1):
                    cx = CENTER + side * (half - 0.8)
                    for x in range(int(round(cx - 1.8)), int(round(cx + 1.8)) + 1):
                        g.set(x, y, 'l' if side < 0 else 'd')
        if crown:
            for i, x in enumerate(range(int(CENTER - 4), int(CENTER + 5), 2)):
                g.set(x, y0 - 2, 'w')
                g.set(x, y0 - 1, 'l')
        return outline(g)

    add('helm_beanie', headwear(1, 6, 1.0))
    add('helm_cap', headwear(1, 6, 1.0, brim=2))
    add('helm_hood', headwear(0, 11, 1.7, drape=6, open_face=True))
    add('helm_knight', headwear(1, 7, 1.2, closed=True))
    add('helm_crown', headwear(2, 4, 1.0, crown=True))

    # ── в руке ──────────────────────────────────────────────
    def in_hand(shape):
        g = Grid()
        shape(g)
        return outline(g)

    def mug(g):
        for y in range(29, 34):
            g.span(y, CENTER + 8.2, CENTER + 11.0, shade_for(0, 1))
        for y in range(29, 34):
            for x in range(int(CENTER + 6), int(CENTER + 10)):
                g.set(x, y, 'm' if x < CENTER + 10 else 'd')
        g.set(int(CENTER + 12), 30, 'm')
        g.set(int(CENTER + 12), 31, 'm')
        stripe(g, 29, 'w')

    def book(g):
        for y in range(28, 35):
            for x in range(int(CENTER + 7), int(CENTER + 12)):
                g.set(x, y, 'm' if x < CENTER + 9 else 'd')
        for y in range(28, 35):
            g.set(int(CENTER + 9), y, 'w')

    def dumbbell(g):
        for y in range(30, 33):
            g.span(y, CENTER + 7.4, CENTER + 11.6, 'm')
        for y in range(28, 35):
            g.span(y, CENTER + 7.4, CENTER + 8.2, 'd')
            g.span(y, CENTER + 11.0, CENTER + 11.6, 'd')

    def torch(g):
        for y in range(24, 35):
            g.span(y, CENTER + 8.4, CENTER + 9.6, 'd')
        for y in range(19, 24):
            r = 2.2 - abs(y - 21) * 0.5
            g.span(y, CENTER + 9 - r, CENTER + 9 + r, 'w' if y < 22 else 'l')

    def sword(g):
        for y in range(14, 33):
            g.span(y, CENTER + 8.4, CENTER + 9.6, 'l')
            g.set(int(CENTER + 9), y, 'w')
        g.span(33, CENTER + 7.4, CENTER + 11.0, 'd')          # гарда
        for y in range(34, 37):
            g.span(y, CENTER + 8.4, CENTER + 9.6, 'd')

    def staff(g):
        for y in range(12, 40):
            g.span(y, CENTER + 8.4, CENTER + 9.6, 'm')
        for y in range(9, 13):
            r = 2.0 - abs(y - 10.5) * 0.5
            g.span(y, CENTER + 9 - r, CENTER + 9 + r, 'w')

    for key, shape in (('main_mug', mug), ('main_book', book),
                       ('main_dumbbell', dumbbell), ('main_torch', torch),
                       ('main_sword', sword), ('main_staff', staff)):
        add(key, in_hand(shape))

    # ── во второй руке (за спиной) ──────────────────────────
    def shield(g):
        for y in range(21, 34):
            t = (y - 21) / 12.0
            half = 3.6 * math.sin(math.pi * (0.15 + 0.7 * (1 - t)))
            cx = CENTER - 8.6
            for x in range(int(round(cx - half)), int(round(cx + half)) + 1):
                g.set(x, y, shade_for(x - cx, half))
        stripe(g, 28, 'w')

    def lantern(g):
        for y in range(28, 34):
            g.span(y, CENTER - 9.5, CENTER - 6.5, 'm')
        g.span(27, CENTER - 9.0, CENTER - 7.0, 'd')
        for y in range(29, 33):
            g.span(y, CENTER - 8.6, CENTER - 7.4, 'w')

    def cat(g):
        for y in range(30, 35):
            g.span(y, CENTER - 10.0, CENTER - 6.0, 'm')
        g.span(29, CENTER - 9.6, CENTER - 8.4, 'm')          # уши
        g.span(29, CENTER - 7.6, CENTER - 6.4, 'm')
        for y in range(26, 31):
            g.span(y, CENTER - 5.6, CENTER - 4.8, 'd')       # хвост

    for key, shape in (('off_shield', shield), ('off_lantern', lantern),
                       ('off_cat', cat)):
        add(key, in_hand(shape))

    # ── за спиной ───────────────────────────────────────────
    def backpack(g):
        for y in range(18, 32):
            half = body_half_width(min(y, 35)) + 1.8
            for x in range(int(round(CENTER - half)), int(round(CENTER + half)) + 1):
                g.set(x, y, shade_for(x - CENTER, half))
        stripe(g, 24, 'w')

    def yogamat(g):
        for y in range(19, 23):
            g.span(y, CENTER - 9.6, CENTER + 8.0, 'm')
        g.span(19, CENTER - 9.6, CENTER + 8.0, 'l')

    def quiver(g):
        for y in range(17, 30):
            g.span(y, CENTER + 4.0, CENTER + 8.4, 'd')
        for y in range(14, 18):
            g.span(y, CENTER + 4.6, CENTER + 7.4, 'w')
            g.span(y, CENTER + 5.6, CENTER + 8.2, 'l')

    for key, shape in (('back_backpack', backpack), ('back_yogamat', yogamat),
                       ('back_quiver', quiver)):
        add(key, in_hand(shape))

    # ── поддоспешник ────────────────────────────────────────
    add('under_shirt', top(30, 0.0, sleeve_to=30, collar='round', thickness=1.4))
    g = top(31, 0.3, sleeve_to=31, collar='round', thickness=1.6)
    for y in range(18, 31, 3):
        stripe(g, y, 'd', inset=1)
    add('under_gambeson', g)

    # ── эффекты ─────────────────────────────────────────────
    g = Grid()
    for x, y in ((6, 20), (25, 24), (8, 34), (24, 38), (5, 28), (27, 30)):
        g.set(x, y, 'w')
        g.set(x, y - 1, 'l')
    add('fx_sparks', g)

    g = Grid()
    for i in range(8):
        a = i * math.pi / 4
        x = int(round(CENTER + math.cos(a) * 12))
        y = int(round(30 + math.sin(a) * 16))
        g.set(x, y, 'l')
        g.set(x, y + 1, 'd')
    add('fx_runes', g)

    return out


# ─────────────────────────── вывод ───────────────────────────

KOTLIN_HEADER = '''package dev.ashwake.ui.character.render

/**
 * Спрайты персонажа.
 *
 * Файл собран `tools/sprites/generate.py` — руками его не правят: силуэты
 * заданы формой в генераторе, и свет там расставляется одним правилом на
 * все предметы. Поправить куртку значит поправить генератор и перегенерировать
 * всё, иначе освещение разъедется от вещи к вещи.
 *
 * Сетка «куклы» %d×%d, шаг на холсте 128×128 — %d пикселя. Спрайт обрезан
 * до содержимого, [x] и [y] — где он лежит на этой сетке.
 *
 * Символы строки: `.` пусто, `o` контур, `d` тень, `m` основной тон,
 * `l` свет, `w` блик, `k` кожа, `K` тень кожи, `e` глаз.
 */
object CharacterSprites {

    const val DOLL_WIDTH = %d
    const val DOLL_HEIGHT = %d

    /** Во сколько раз пиксель куклы больше пикселя холста 128×128. */
    const val DOLL_STEP = %d

    /** Голая фигура: рисуется всегда и под всем остальным. */
    val body: Sprite = %s

    /** id предмета из каталога → спрайт. Нет в таблице — предмет не рисуется. */
    val items: Map<String, Sprite> = mapOf(
%s
    )

    /** Спрайт: строки символов и место на сетке куклы. */
    data class Sprite(val x: Int, val y: Int, val rows: List<String>)
}
'''


def kotlin_sprite(g, indent):
    x, y, rows = g.crop()
    pad = ' ' * indent
    body = ',\n'.join('%s    "%s"' % (pad, r) for r in rows)
    return 'Sprite(\n%s    x = %d,\n%s    y = %d,\n%s    rows = listOf(\n%s\n%s    )\n%s)' % (
        pad, x, pad, y, pad, body, pad, pad)


def emit_kotlin(sprites, path):
    entries = []
    for key in sorted(sprites):
        entries.append('        "%s" to %s' % (key, kotlin_sprite(sprites[key], 8)))
    text = KOTLIN_HEADER % (
        DOLL_W, DOLL_H, 2, DOLL_W, DOLL_H, 2,
        kotlin_sprite(base_body(), 4),
        ',\n'.join(entries),
    )
    path.write_text(text, encoding='utf-8')
    return len(entries)


PALETTE = {
    'o': (24, 20, 30), 'd': (0.55,), 'm': (1.0,), 'l': (1.32,), 'w': (1.62,),
    'k': (226, 190, 160), 'K': (196, 158, 130), 'e': (32, 28, 38),
}


def preview(sprites, path, tint=(110, 122, 168)):
    from PIL import Image, ImageDraw

    def colour(ch):
        v = PALETTE.get(ch)
        if v is None:
            return None
        if len(v) == 3:
            return v
        k = v[0]
        return tuple(min(255, int(c * k)) for c in tint)

    cell, cols = DOLL_W + 2, 10
    keys = sorted(sprites)
    rows_n = (len(keys) + cols - 1) // cols + 1
    scale = 4
    img = Image.new('RGB', (cell * cols * scale, (DOLL_H + 6) * rows_n * scale), (18, 16, 24))
    d = ImageDraw.Draw(img)

    def blit(g, ox, oy, with_body=True):
        layers = [base_body()] if with_body else []
        layers.append(g)
        for layer in layers:
            for y, row in enumerate(layer.rows()):
                for x, ch in enumerate(row):
                    c = colour(ch)
                    if c is None:
                        continue
                    d.rectangle(
                        [(ox + x) * scale, (oy + y) * scale,
                         (ox + x + 1) * scale - 1, (oy + y + 1) * scale - 1], fill=c)

    blit(Grid(), 1, 1)
    for i, key in enumerate(keys):
        cx = ((i + 1) % cols) * cell + 1
        cy = ((i + 1) // cols) * (DOLL_H + 6) + 1
        blit(sprites[key], cx, cy)
    img.save(path)
    return path


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', metavar='PNG', nargs='?', const='sprites.png')
    args = ap.parse_args()

    sprites = build()
    root = pathlib.Path(__file__).resolve().parents[2]
    target = root / 'app/src/main/java/dev/ashwake/ui/character/render/CharacterSprites.kt'
    n = emit_kotlin(sprites, target)
    print('спрайтов: %d → %s' % (n, target.relative_to(root)))
    if args.preview:
        print('картинка:', preview(sprites, pathlib.Path(args.preview)))
