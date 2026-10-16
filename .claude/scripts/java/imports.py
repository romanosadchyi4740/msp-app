"""Add or remove imports in one Java file, then sort its import block.

Usage (from the repo root):
    python .claude/scripts/java/imports.py FILE [+fq.Name ...] [-fq.Name ...]
    python .claude/scripts/java/imports.py FILE "+static org.mockito.Mockito.when"

The block is written as: other imports (sorted), blank line, java.* and javax.* (sorted), blank line,
static imports (sorted) -- the layout used in this repository. Line endings are preserved.
"""
import sys


def main():
    path = sys.argv[1]
    raw = open(path, encoding='utf-8', newline='').read()
    newline = '\r\n' if '\r\n' in raw else '\n'
    lines = raw.replace('\r\n', '\n').split('\n')
    adds = [a[1:] for a in sys.argv[2:] if a.startswith('+')]
    removes = {a[1:] for a in sys.argv[2:] if a.startswith('-')}

    idx = [i for i, l in enumerate(lines) if l.startswith('import ')]
    if not idx:
        if not adds:
            return
        package_line = next(i for i, l in enumerate(lines) if l.startswith('package '))
        lines[package_line + 1:package_line + 1] = ['', '']
        idx = [package_line + 2]
        lines[idx[0]] = f'import {adds.pop(0)};'
    start, end = idx[0], idx[-1]

    block = [l for l in lines[start:end + 1] if l.strip()]
    block = [l for l in block if l[len('import '):].rstrip(';') not in removes]
    block += [f'import {a};' for a in adds]
    block = list(dict.fromkeys(block))

    def is_java(line):
        return line[len('import '):].startswith(('java.', 'javax.'))

    static = sorted(l for l in block if l.startswith('import static '))
    plain = [l for l in block if not l.startswith('import static ')]
    groups = [sorted(l for l in plain if not is_java(l)), sorted(l for l in plain if is_java(l)), static]

    out = []
    for group in groups:
        if group:
            out += group + ['']
    lines[start:end + 1] = out[:-1]
    open(path, 'w', encoding='utf-8', newline='').write(newline.join(lines))


if __name__ == '__main__':
    main()
