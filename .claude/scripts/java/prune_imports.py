"""Remove unused imports from Java files and sort their import blocks.

Usage (from the repo root):
    python .claude/scripts/java/prune_imports.py FILE...

A single-type import is unused when its simple name appears nowhere in the code outside comments and strings
(names in Javadoc {@link}/{@code} and @throws count as used). A static import is unused when its member name
does not appear. Wildcard imports are left alone. Files without imports are skipped.
"""
import pathlib
import re
import subprocess
import sys

COMMENTS_AND_STRINGS = re.compile(r'//[^\n]*|/\*.*?\*/|"[^"\n]*"', re.S)
IMPORTS = pathlib.Path(__file__).with_name('imports.py')


def unused_imports(source):
    code_lines = [l for l in source.split('\n') if not l.startswith(('import ', 'package '))]
    code = '\n'.join(code_lines)
    javadoc = re.findall(r'\{@(?:link|code)\s+([\w#.]+)', code) + re.findall(r'@throws\s+(\w+)', code)
    code = COMMENTS_AND_STRINGS.sub('', code) + ' ' + ' '.join(javadoc)
    removals = []
    for fq, simple in re.findall(r'^import ([\w.]+\.([A-Z]\w*));', source, re.M):
        if not re.search(r'(?<![\w])' + simple + r'\b', code):
            removals.append('-' + fq)
    for fq, member in re.findall(r'^import static ([\w.]+\.([a-z]\w*));', source, re.M):
        if not re.search(r'(?<![\w.])' + member + r'\b', code):
            removals.append('-static ' + fq)
    return removals


def main():
    for path in sys.argv[1:]:
        source = open(path, encoding='utf-8').read()
        if not re.search(r'^import ', source, re.M):
            continue
        removals = unused_imports(source)
        subprocess.run([sys.executable, str(IMPORTS), path] + removals, check=True)
        if removals:
            print(f'{path}: removed {len(removals)}')


if __name__ == '__main__':
    main()
