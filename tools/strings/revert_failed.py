#!/usr/bin/env python3
"""
Откат замен, которые не собрались.

`stringResource` — composable-функция, и из обработчика нажатия, из `remember`
или из обычной лямбды её не вызвать. Разметить такие места заранее нельзя:
это зависит не от отступа, а от того, чем является окружающий блок.

Поэтому решение принимает компилятор. Скрипт читает его жалобы, возвращает
литерал на место и убирает из ресурсов ключи, на которые больше никто не
ссылается.

    python3 tools/strings/revert_failed.py errors.txt
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
STRINGS = ROOT / 'app/src/main/res/values/strings.xml'

# Строки, которые вынести нельзя: они стоят в обработчиках, в `remember` и
# прочих не-composable местах. Список ведёт сам скрипт — руками его не пишут.
SKIP = pathlib.Path(__file__).with_name('skip.txt')


def load_refused():
    if not SKIP.exists():
        return set()
    return {l for l in SKIP.read_text(encoding='utf-8').split('\n') if l.strip()}

ERROR = re.compile(r'^e: file://(\S+?\.kt):(\d+):(\d+) @Composable invocations', re.M)
CALL = re.compile(r'stringResource\(\s*R\.string\.(\w+)((?:,\s*[^()]*(?:\([^()]*\))?)*)\s*\)')


def resources():
    text = STRINGS.read_text(encoding='utf-8')
    out = {}
    for m in re.finditer(r'<string name="(\w+)"[^>]*>(.*?)</string>', text, re.S):
        out[m.group(1)] = m.group(2)
    return out


def to_literal(value, args):
    """Ресурс обратно в котлиновский литерал."""
    text = (value.replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>')
                 .replace("\\'", "'").replace('\\"', '\\"'))
    for i, arg in enumerate(args, start=1):
        placeholder = '%%%d$s' % i
        replacement = '$' + arg if re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', arg) else '${%s}' % arg
        text = text.replace(placeholder, replacement)
    return '"%s"' % text


def split_args(raw):
    if not raw.strip():
        return []
    out, depth, cur = [], 0, ''
    for c in raw.lstrip(', '):
        if c in '([': depth += 1
        elif c in ')]': depth -= 1
        if c == ',' and depth == 0:
            out.append(cur.strip()); cur = ''
        else:
            cur += c
    if cur.strip():
        out.append(cur.strip())
    return out


def main(log):
    res = resources()
    targets = {}
    for path, line, _ in ERROR.findall(pathlib.Path(log).read_text(encoding='utf-8')):
        targets.setdefault(path, set()).add(int(line) - 1)

    reverted = 0
    refused = load_refused()
    for path, lines_no in targets.items():
        p = pathlib.Path(path)
        lines = p.read_text(encoding='utf-8').split('\n')
        for i in sorted(lines_no):
            def back(m):
                nonlocal reverted
                key = m.group(1)
                if key not in res:
                    return m.group(0)
                reverted += 1
                literal = to_literal(res[key], split_args(m.group(2)))
                # Запоминаем: иначе следующий прогон вынесет ту же строку
                # обратно, а откат вернёт её назад — и так по кругу
                refused.add('%s\t%s' % (p.relative_to(ROOT), literal))
                return literal
            lines[i] = CALL.sub(back, lines[i])
        p.write_text('\n'.join(lines), encoding='utf-8')

    SKIP.write_text('\n'.join(sorted(refused)) + '\n', encoding='utf-8')
    drop_unused(res)
    print('возвращено литералов: %d' % reverted)


def drop_unused(res):
    """Ключи, на которые не осталось ссылок, из ресурсов убираем."""
    # Читаем всё дерево, а не только Kotlin: на строки ссылаются манифест
    # (заголовки виджетов и плиток), разметка виджетов и тесты. Ключ, снесённый
    # потому, что его не нашли в .kt, ломает сборку ресурсов, а не код —
    # и ошибка приходит из AAPT, где её меньше всего ждёшь
    sources = []
    for base in ('app/src/main', 'app/src/test', 'app/src/androidTest'):
        root = ROOT / base
        if not root.exists():
            continue
        for f in root.rglob('*'):
            if f.is_file() and f.suffix in ('.kt', '.java', '.xml') and f != STRINGS:
                sources.append(f.read_text(encoding='utf-8', errors='ignore'))
    sources = '\n'.join(sources)
    text = STRINGS.read_text(encoding='utf-8')
    removed = 0
    for key in res:
        if re.search(r'\bR\.string\.%s\b' % key, sources) or ('@string/%s' % key) in sources:
            continue
        new = re.sub(r'\n\s*<string name="%s"[^>]*>.*?</string>' % key, '', text, flags=re.S)
        if new != text:
            text, removed = new, removed + 1
    STRINGS.write_text(text, encoding='utf-8')
    print('убрано осиротевших ключей: %d' % removed)


if __name__ == '__main__':
    main(sys.argv[1])
