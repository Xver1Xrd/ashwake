#!/usr/bin/env python3
"""
То же самое, но для вью-моделей.

`stringResource` там не работает — композиции нет. Зато у этих трёх уже
внедрён `@ApplicationContext`, и текст берётся через `context.getString`.
Отдельный скрипт, а не флаг в основном: правило «где брать строку» разное,
и мешать их в одном проходе значит каждый раз выяснять, какой режим включён.

    python3 tools/strings/extract_vm.py app/src/.../CharacterViewModel.kt …
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from extract import (CYRILLIC, ROOT, STRINGS, STRING, code_lines, escape_xml,
                     load_strings, slug, strip_noise, to_format, unescape)


def gather_all(text):
    """Как в основном скрипте, но без ограничения на composable-функции."""
    lines = text.split('\n')
    allowed = code_lines(text)
    offsets, pos = [], 0
    for line in lines:
        offsets.append(pos)
        pos += len(line) + 1

    found = []
    for i in sorted(allowed):
        for m in STRING.finditer(strip_noise(lines[i])):
            found.append((offsets[i] + m.start(), offsets[i] + m.end(), m.group(0)))

    out, used = [], set()
    for idx, (start, end, literal) in enumerate(found):
        if idx in used:
            continue
        parts, last_end, k = [literal], end, idx
        while k + 1 < len(found):
            if not re.fullmatch(r'\s*\+\s*', text[last_end:found[k + 1][0]]):
                break
            k += 1
            used.add(k)
            parts.append(found[k][2])
            last_end = found[k][1]
        value = ''.join(unescape(p) for p in parts)
        if CYRILLIC.search(value):
            out.append((start, last_end, value))
    return out


def main(argv):
    # У каждого места свой способ добраться до Context: у вью-модели он
    # внедрён полем, у сервиса он и есть `this`, у воркера — applicationContext,
    # у Glance-виджета берётся из композиции. Поэтому получатель задаётся
    # снаружи, а не угадывается.
    accessor = 'context.getString'
    paths = []
    for arg in argv:
        if arg.startswith('--accessor='):
            accessor = arg.split('=', 1)[1]
        else:
            paths.append(arg)
    return run(paths, accessor)


def run(paths, accessor):
    _, by_value, keys = load_strings()
    added, replaced = [], 0

    for arg in paths:
        path = pathlib.Path(arg)
        text = path.read_text(encoding='utf-8')
        module = path.stem.replace('ViewModel', '').lower() or 'app'

        edits = []
        for start, end, value in gather_all(text):
            converted = to_format(value)
            if converted is None:
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
            call = '%s(R.string.%s%s)' % (accessor,
                key, ''.join(', ' + a for a in call_args))
            edits.append((start, end, call))

        for start, end, call in sorted(edits, reverse=True):
            text = text[:start] + call + text[end:]
        replaced += len(edits)
        if 'import dev.ashwake.R\n' not in text:
            text = re.sub(r'^import ', 'import dev.ashwake.R\nimport ', text, count=1, flags=re.M)
        path.write_text(text, encoding='utf-8')

    if added:
        res = STRINGS.read_text(encoding='utf-8')
        block = ['', '    <!-- Сообщения вью-моделей -->']
        for key, value, has_args in added:
            attrs = ' name="%s"' % key
            if not has_args and '%' in value:
                attrs += ' formatted="false"'
            block.append('    <string%s>%s</string>' % (attrs, escape_xml(value)))
        STRINGS.write_text(res.replace('</resources>', '\n'.join(block) + '\n</resources>'),
                           encoding='utf-8')

    print('вынесено: %d, новых ключей: %d' % (replaced, len(added)))


if __name__ == '__main__':
    main(sys.argv[1:])
