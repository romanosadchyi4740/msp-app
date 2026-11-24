"""Fix and check the repository's mechanical Java style rules (CLAUDE.md section 4).

Usage (from the repo root):
    python .claude/scripts/java/java_style.py FILE...          # fix what can be fixed, report the rest
    python .claude/scripts/java/java_style.py --check FILE...  # report only; exit 1 if anything is found
    python .claude/scripts/java/java_style.py $(git diff --name-only --diff-filter=AMR development -- '*.java')

Fixed automatically:
  * fully qualified type names in code -> import + simple name (skipped when the simple name clashes);
  * the file ends with the last `}` plus exactly one newline, with no blank line before that `}`.
Reported only:
  * fields written with `private` or `private final` (use Lombok @FieldDefaults; `static final` constants are fine);
  * methods in @Entity classes (entities hold data only);
  * files that do not start with `package` (except package-info.java), e.g. a file damaged by an IDE save.
"""
import pathlib
import re
import subprocess
import sys

FQN = re.compile(r'(?<![\w.])((?:com\.romanosadchyi|java|javax|jakarta|org|io|lombok)(?:\.[a-z0-9_]+)+)\.([A-Z]\w*)')
FIELD = re.compile(r'^    private (?:final )?(?!static)[A-Z][\w<>, ?\[\]]*\s+\w+\s*(?:=[^;]*)?;', re.M)
METHOD = re.compile(r'^    (?:public |protected |private |static |final )*[\w<>\[\], ?.]+\s+(\w+)\s*\([^;{]*\)\s*(?:throws [\w., ]+)?\{',
                    re.M)


def fix_fqns(text, path):
    lines = text.split('\n')
    pkg = next((l.split()[1].rstrip(';') for l in lines if l.startswith('package ')), '')
    imported = {l[7:].rstrip(';').strip() for l in lines if l.startswith('import ') and 'static ' not in l}
    simple = {i.rsplit('.', 1)[1]: i for i in imported if not i.endswith('*')}
    declared = set(re.findall(r'\b(?:class|interface|record|enum)\s+(\w+)', text))
    same_pkg = {p.stem for p in path.parent.glob('*.java')}
    new_imports, in_block = set(), False
    for n, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith(('import ', 'package ')):
            continue
        if in_block or stripped.startswith('//'):
            in_block = in_block and '*/' not in stripped
            continue
        if stripped.startswith('/*'):
            in_block = '*/' not in stripped
            continue

        def replace(m):
            owner, name = m.group(1), m.group(2)
            fq = f'{owner}.{name}'
            if fq in imported or fq in new_imports or owner in (pkg, 'java.lang'):
                return name
            if (simple.get(name) not in (None, fq)) or name in declared or name in same_pkg:
                return m.group(0)
            new_imports.add(fq)
            simple[name] = fq
            return name

        lines[n] = FQN.sub(replace, line)
    text = '\n'.join(lines)
    for fq in sorted(new_imports):
        first_import = re.search(r'^import [^\n]*\n', text, re.M)
        if first_import:
            text = text[:first_import.start()] + f'import {fq};\n' + text[first_import.start():]
        else:
            package_line = re.search(r'^package [^\n]*\n', text, re.M)
            text = text[:package_line.end()] + f'\nimport {fq};\n' + text[package_line.end():]
    return text, sorted(new_imports)


def fix_ending(text):
    text = text.rstrip() + '\n'
    if text.endswith('}\n'):
        text = text[:-2].rstrip(' \t\n') + '\n}\n'
    return text


def problems(text, path):
    found = []
    if not text.lstrip().startswith('package ') and path.name != 'package-info.java':
        found.append('does not start with a package declaration')
    found += [f'explicit field modifier: {m.group(0).strip()}' for m in FIELD.finditer(text)]
    if re.search(r'^@Entity\b', text, re.M):
        found += [f'method in an entity: {m.group(1)}()' for m in METHOD.finditer(text)]
    found += [f'fully qualified name left: {m.group(0)}' for line in text.split('\n')
              if not line.lstrip().startswith(('import ', 'package ', '*', '//', '/*'))
              for m in FQN.finditer(line)]
    return found


def main():
    args = sys.argv[1:]
    check_only = '--check' in args
    files = [pathlib.Path(a) for a in args if a != '--check' and a.endswith('.java')]
    exit_code = 0
    for path in files:
        if not path.exists():
            continue
        raw = path.read_bytes().decode('utf-8')
        newline = '\r\n' if '\r\n' in raw else '\n'
        text = raw.replace('\r\n', '\n')
        if not check_only:
            fixed, added = fix_fqns(text, path)
            fixed = fix_ending(fixed)
            if fixed != text:
                path.write_bytes(fixed.replace('\n', newline).encode('utf-8'))
                if added:
                    subprocess.run([sys.executable, str(pathlib.Path(__file__).with_name('imports.py')), str(path)],
                                   check=True)
                print(f'{path}: fixed' + (f' (imported {", ".join(added)})' if added else ''))
                text = path.read_bytes().decode('utf-8').replace('\r\n', '\n')
        elif fix_ending(text) != text:
            print(f'{path}: file ending')
            exit_code = 1
        for problem in problems(text, path):
            print(f'{path}: {problem}')
            exit_code = 1
    sys.exit(exit_code)


if __name__ == '__main__':
    main()
