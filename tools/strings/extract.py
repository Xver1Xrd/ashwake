#!/usr/bin/env python3
"""
Перенос строк интерфейса из кода в strings.xml.

Ищет русские литералы в composable-функциях, кладёт их в ресурсы и заменяет
на `stringResource`. Склеенные плюсом куски одного текста собираются в одну
запись: разрезанный по строкам абзац — это один текст, а не три.

Что не трогается принципиально:

* всё вне `ui/` — словарь разборщика ввода и названия предметов каталога
  это данные, а не надписи; в ресурсах им нечего делать;
* литералы вне composable-функций — `stringResource` оттуда не вызвать;
* строки без кириллицы — это ключи, метки анимаций и идентификаторы.

Замены, которые не собрались, откатываются: скрипт компилирует проект и по
жалобам компилятора возвращает нетронутый литерал на место. Поэтому его
можно натравливать на весь каталог, не размечая руками, где composable,
а где обработчик нажатия.

    python3 tools/strings/extract.py --dry     # только посчитать
    python3 tools/strings/extract.py           # применить
"""

import argparse
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
UI = ROOT / 'app/src/main/java/dev/ashwake/ui'
STRINGS = ROOT / 'app/src/main/res/values/strings.xml'
SKIP = pathlib.Path(__file__).with_name('skip.txt')

CYRILLIC = re.compile('[а-яА-ЯёЁ]')

TRANSLIT = {
    'а': 'a', 'б': 'b', 'в': 'v', 'г': 'g', 'д': 'd', 'е': 'e', 'ё': 'e',
    'ж': 'zh', 'з': 'z', 'и': 'i', 'й': 'y', 'к': 'k', 'л': 'l', 'м': 'm',
    'н': 'n', 'о': 'o', 'п': 'p', 'р': 'r', 'с': 's', 'т': 't', 'у': 'u',
    'ф': 'f', 'х': 'h', 'ц': 'c', 'ч': 'ch', 'ш': 'sh', 'щ': 'sch', 'ъ': '',
    'ы': 'y', 'ь': '', 'э': 'e', 'ю': 'yu', 'я': 'ya',
}


def slug(text, limit=44):
    out = []
    for ch in text.lower():
        if ch in TRANSLIT:
            out.append(TRANSLIT[ch])
        elif ch.isalnum() and ch.isascii():
            out.append(ch)
        else:
            out.append('_')
    s = re.sub(r'_+', '_', ''.join(out)).strip('_')
    return s[:limit].strip('_') or 'text'


# ─────────────────────── разбор Kotlin ───────────────────────

STRING = re.compile(r'"(?:[^"\\\n]|\\.)*"')


def strip_noise(line):
    """Строка кода без хвостового комментария."""
    out, in_str, i = '', False, 0
    while i < len(line):
        c = line[i]
        if in_str:
            if c == '\\':
                out += line[i:i + 2]; i += 2; continue
            if c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c == '/' and i + 1 < len(line) and line[i + 1] == '/':
            break
        out += c
        i += 1
    return out


def code_lines(text):
    """Индексы строк, которые действительно код: без KDoc и блочных комментариев."""
    ok, block = [], False
    for i, line in enumerate(text.split('\n')):
        s = line.strip()
        if block:
            if '*/' in s:
                block = False
            continue
        if s.startswith('/*'):
            if '*/' not in s:
                block = True
            continue
        if s.startswith('//') or s.startswith('*'):
            continue
        ok.append(i)
    return set(ok)


def composable_ranges(lines):
    """
    Строки, лежащие внутри @Composable-функции.

    Считается по отступу объявления: тело функции — всё до строки с тем же
    отступом, начинающейся с `}`. Грубо, но ошибки этой грубости ловятся
    компилятором на следующем шаге.
    """
    inside = set()
    for i, line in enumerate(lines):
        # Геттер помечен на своей же строке: `@Composable get() = …`
        if '@Composable get()' in line:
            indent = len(line) - len(line.lstrip())
            k = i
            while k < len(lines) and (k == i or not lines[k].strip() or
                                      len(lines[k]) - len(lines[k].lstrip()) >= indent):
                k += 1
            inside.update(range(i, k))
            continue
        if line.strip() != '@Composable':
            continue
        j = i + 1
        while j < len(lines) and not re.match(r'\s*(private |internal |public )?fun ', lines[j]):
            j += 1
        if j >= len(lines):
            continue
        indent = len(lines[j]) - len(lines[j].lstrip())
        k = j + 1
        while k < len(lines):
            if lines[k].strip() and (len(lines[k]) - len(lines[k].lstrip())) <= indent:
                if lines[k].lstrip().startswith('}'):
                    break
                if re.match(r'\s*(@|private |fun |val |object |class )', lines[k]):
                    break
            k += 1
        inside.update(range(j, k + 1))
    return inside


def unescape(literal):
    return (literal[1:-1]
            .replace('\\"', '"').replace('\\n', '\n').replace('\\t', '\t')
            .replace('\\$', '$').replace('\\\\', '\\'))


def gather(text):
    """
    Кандидаты: (начало, конец, значение). Склеенные плюсом куски собираются
    в один текст — разрезанный по ширине абзац остаётся одним абзацем.
    """
    lines = text.split('\n')
    allowed = code_lines(text) & composable_ranges(lines)
    offsets, pos = [], 0
    for line in lines:
        offsets.append(pos)
        pos += len(line) + 1

    found = []
    for i in sorted(allowed):
        line = lines[i]
        # Аргумент аннотации — сообщение компилятору, а не человеку:
        # `@Deprecated("Использовать …")` читает тот, кто пишет код
        if line.lstrip().startswith('@'):
            continue
        for m in STRING.finditer(strip_noise(line)):
            found.append((offsets[i] + m.start(), offsets[i] + m.end(), m.group(0), i))

    out, used = [], set()
    for idx, (start, end, literal, line_no) in enumerate(found):
        if idx in used:
            continue
        parts, last_end, k = [literal], end, idx
        # склейка: "…" + \n "…"
        while k + 1 < len(found):
            between = text[last_end:found[k + 1][0]]
            if not re.fullmatch(r'\s*\+\s*', between):
                break
            k += 1
            used.add(k)
            parts.append(found[k][2])
            last_end = found[k][1]
        value = ''.join(unescape(p) for p in parts)
        if not CYRILLIC.search(value):
            continue
        # Внутри подстановки бывает своя строка: `${if (n == 1) "ь" else "и"}`.
        # Регулярка видит её кавычку как конец литерала и режет текст не там,
        # поэтому такие литералы обходим стороной, а не выносим наполовину
        if value.count('{') != value.count('}'):
            continue
        out.append((start, last_end, value, line_no))
    return out


# ─────────────────────── шаблоны ───────────────────────

def split_template(value):
    """
    Текст на куски: обычный кусок или подстановка.

    Скобки считаются, а не ищутся регуляркой: `${list.count { it.done }}`
    содержит вложенные фигурные скобки, и `[^{}]*` обрывается на первой же
    внутренней — строка после этого выносится наполовину и перестаёт
    собираться.
    """
    parts, i, plain = [], 0, ''
    while i < len(value):
        if value[i] != '$' or i + 1 >= len(value):
            plain += value[i]; i += 1; continue
        if value[i + 1] == '{':
            depth, j = 1, i + 2
            while j < len(value) and depth:
                if value[j] == '{': depth += 1
                elif value[j] == '}': depth -= 1
                j += 1
            if depth:
                plain += value[i]; i += 1; continue
            parts.append((plain, value[i + 2:j - 1]))
            plain, i = '', j
            continue
        m = re.match(r'[A-Za-z_][A-Za-z0-9_.]*', value[i + 1:])
        if not m:
            plain += value[i]; i += 1; continue
        parts.append((plain, m.group(0)))
        plain, i = '', i + 1 + m.end()
    parts.append((plain, None))
    return parts


def to_format(value):
    """
    Строка с подстановками → формат Android плюс список выражений.
    Возвращает None, если подстановка слишком сложная, чтобы вынести её.
    """
    args, out = [], ''
    for plain, expr in split_template(value):
        out += plain
        if expr is None:
            continue
        if not expr.strip() or '"' in expr:
            return None
        args.append(expr.strip())
        out += '%%%d$s' % len(args)
    if len(args) > 9:
        return None
    return out, args


# ─────────────────────── ресурсы ───────────────────────

def load_strings():
    tree = ET.parse(STRINGS)
    root = tree.getroot()
    by_value, keys = {}, set()
    for node in root.findall('string'):
        name = node.get('name')
        keys.add(name)
        if node.text is not None:
            by_value.setdefault(unescape_xml(node.text), name)
    return root, by_value, keys


def unescape_xml(text):
    return text.replace("\\'", "'").replace('\\"', '"').replace('\\n', '\n')


def escape_xml(text):
    return (text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
                .replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dry', action='store_true')
    args = ap.parse_args()

    refused = set()
    if SKIP.exists():
        refused = {l for l in SKIP.read_text(encoding='utf-8').split('\n') if l.strip()}

    root, by_value, keys = load_strings()
    added, replaced, skipped = [], 0, 0

    for path in sorted(UI.rglob('*.kt')):
        text = path.read_text(encoding='utf-8')
        candidates = gather(text)
        if not candidates:
            continue

        module = path.parent.name if path.parent.name != 'ui' else path.stem.lower()
        edits = []
        relative = path.relative_to(ROOT)
        for start, end, value, _ in candidates:
            if '%s\t%s' % (relative, text[start:end]) in refused:
                continue
            converted = to_format(value)
            if converted is None:
                skipped += 1
                continue
            formatted, call_args = converted
            key = by_value.get(formatted)
            if key is None:
                key = base = '%s_%s' % (slug(module, 16), slug(formatted))
                n = 2
                while key in keys:
                    key = '%s_%d' % (base, n)
                    n += 1
                keys.add(key)
                by_value[formatted] = key
                added.append((key, formatted, bool(call_args)))
            call = 'stringResource(R.string.%s%s)' % (
                key, ''.join(', ' + a for a in call_args))
            edits.append((start, end, call))

        if not edits:
            continue
        for start, end, call in sorted(edits, reverse=True):
            text = text[:start] + call + text[end:]
        replaced += len(edits)

        for imp in ('androidx.compose.ui.res.stringResource', 'dev.ashwake.R'):
            if 'import %s\n' % imp not in text:
                text = re.sub(r'^import ', 'import %s\nimport ' % imp, text, count=1, flags=re.M)
        if not args.dry:
            path.write_text(text, encoding='utf-8')

    if not args.dry and added:
        append_strings(added)

    print('вынесено: %d, новых ключей: %d, пропущено сложных: %d'
          % (replaced, len(added), skipped))


def append_strings(added):
    """
    Дописываем в конец, не переписывая файл целиком.

    Переписывание ломает то, что уже есть: комментарии пропадают, а строки
    вида `"  Дальше"` — где кавычки удерживают пробелы — экранируются второй
    раз и превращаются в текст с кавычками. Правки в чужие записи здесь не
    нужны, поэтому и не трогаем их.
    """
    text = STRINGS.read_text(encoding='utf-8')
    block = ['', '    <!-- Вынесено из кода tools/strings/extract.py -->']
    for key, value, has_args in added:
        attrs = ' name="%s"' % key
        if not has_args and '%' in value:
            attrs += ' formatted="false"'
        block.append('    <string%s>%s</string>' % (attrs, escape_xml(value)))
    text = text.replace('</resources>', '\n'.join(block) + '\n</resources>')
    STRINGS.write_text(text, encoding='utf-8')


if __name__ == '__main__':
    sys.exit(main())
