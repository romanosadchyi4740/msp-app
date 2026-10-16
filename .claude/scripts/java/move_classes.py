"""Move Java classes to new packages with `git mv`, then fix package lines and imports in every Java file.

Usage (from the repo root):
    python .claude/scripts/java/move_classes.py backend MOVES.json

MOVES.json is a list of [old FQN, new FQN, "main" | "test"], for example
    [["com.romanosadchyi.smp.music_app.model.entity.Track",
      "com.romanosadchyi.smp.music_app.music.internal.entity.Track", "main"]]
The file name follows the simple name, so a move can also rename a class's package only (not the class).

After moving it:
  * rewrites imports (and static imports) of every moved class in all main and test sources;
  * adds imports to classes that used a moved class without one (they shared its old package);
  * adds imports to moved classes for types they used from their old package;
  * replaces wildcard imports of a package whose classes moved, where needed.
Run prune_imports.py and the compiler afterwards; review the diff before committing.
"""
import json
import pathlib
import re
import subprocess
import sys

COMMENTS_AND_STRINGS = re.compile(r'//[^\n]*|/\*.*?\*/|"[^"\n]*"', re.S)


def main():
    backend = pathlib.Path(sys.argv[1]).resolve()
    moves = json.load(open(sys.argv[2], encoding='utf-8'))
    roots = {'main': backend / 'src/main/java', 'test': backend / 'src/test/java'}

    def path_of(fq, kind):
        return roots[kind] / (fq.replace('.', '/') + '.java')

    renames = {}
    for old, new, kind in moves:
        src, dst = path_of(old, kind), path_of(new, kind)
        dst.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(['git', 'mv', str(src), str(dst)], check=True, cwd=backend)
        renames[old] = new

    old_pkg_of = {new: old.rsplit('.', 1)[0] for old, new in renames.items()}
    simple_new = {new.rsplit('.', 1)[1]: new for new in renames.values()}

    for root in roots.values():
        for p in root.rglob('*.java'):
            raw = p.read_bytes().decode('utf-8')
            newline = '\r\n' if '\r\n' in raw else '\n'
            s = raw.replace('\r\n', '\n')
            original = s
            fq_self = str(p.relative_to(root).with_suffix('')).replace('\\', '/').replace('/', '.')
            pkg = fq_self.rsplit('.', 1)[0]
            s = re.sub(r'^package [\w.]+;', f'package {pkg};', s, count=1, flags=re.M)
            for old, new in renames.items():
                s = re.sub(r'(^import (?:static )?)' + re.escape(old) + r'\b', lambda m: m.group(1) + new, s, flags=re.M)

            body = '\n'.join(l for l in s.split('\n') if not l.startswith(('import ', 'package ')))
            code = COMMENTS_AND_STRINGS.sub('', body) + ' ' + ' '.join(re.findall(r'\{@(?:link|code)\s+(\w+)', s))
            used = set(re.findall(r'(?<![\w.])([A-Z]\w*)\b', code))
            imported_simple = {i.rsplit('.', 1)[1] for i in re.findall(r'^import ([\w.]+);', s, re.M)}
            wildcard = set(re.findall(r'^import ([\w.]+)\.\*;', s, re.M))
            own_old_pkg = old_pkg_of.get(fq_self)
            needed = set()
            for name in used:
                if name in imported_simple or name == p.stem:
                    continue
                target = simple_new.get(name)
                if target:
                    target_pkg = target.rsplit('.', 1)[0]
                    if target_pkg != pkg and target_pkg not in wildcard:
                        needed.add(target)
                    continue
                if own_old_pkg and own_old_pkg != pkg:
                    for r in roots.values():
                        if (r / own_old_pkg.replace('.', '/') / f'{name}.java').exists():
                            needed.add(f'{own_old_pkg}.{name}')
            for w in wildcard:
                for name in used:
                    if f'{w}.{name}' in renames and name not in imported_simple:
                        needed.add(renames[f'{w}.{name}'])
            for imp in sorted(needed):
                line = f'import {imp};'
                if line in s:
                    continue
                first = re.search(r'^import [^\n]*\n', s, re.M)
                if first:
                    s = s[:first.start()] + line + '\n' + s[first.start():]
                else:
                    s = re.sub(r'^(package [\w.]+;\n)', r'\1\n' + line + '\n', s, count=1, flags=re.M)
            if s != original:
                p.write_bytes(s.replace('\n', newline).encode('utf-8'))
    print(f'moved {len(renames)} classes')


if __name__ == '__main__':
    main()
