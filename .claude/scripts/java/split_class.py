"""Split a Java class into several classes by moving whole members, so no method body is retyped.

Usage (from the repo root):
    python .claude/scripts/java/split_class.py SPEC.json

SPEC.json:
{
  "source": "backend/src/main/java/.../TrackService.java",
  "drop": ["createTrack"],                 # members deliberately not moved (rewritten by hand)
  "targets": [
    {"path": "backend/src/main/java/.../TrackLikeService.java",
     "header": "package ...;\\n\\n{{IMPORTS}}\\n\\n/** ... */\\n@Service\\n...\\npublic class TrackLikeService {\\n\\n    TrackRepository trackRepository;\\n",
     "members": ["likeTrack", "unlikeTrack", "getLikedTracks"],
     "extra": "    private void requireUser(UUID userId) { ... }"}
  ]
}
A member is a method (with its annotations and Javadoc), found by name; overloads are not supported.
{{IMPORTS}} is replaced by all imports of the source. A target may be the source path itself (written last),
which replaces the source. Afterwards run prune_imports.py on every target, then compile.
Members of the source that are neither moved nor dropped are printed, so nothing is lost silently.
"""
import json
import re
import sys

MEMBER = re.compile(r'    (?:public |private |protected |static |final |synchronized )*[\w<>\[\], ?.]+\s+(\w+)\s*\(')


def members(src):
    class_start = re.search(r'^(?:public |final |abstract )*(?:class|interface|record) ', src, re.M).start()
    i = src.index('{', class_start) + 1
    out, pending = {}, None
    while True:
        end_of_line = src.find('\n', i)
        if end_of_line == -1:
            break
        line = src[i:end_of_line]
        stripped = line.strip()
        top_level = line.startswith('    ') and not line.startswith('     ')
        if pending is None and top_level and stripped.startswith(('@', '/**')):
            pending = i
        if stripped.startswith('/**') and '*/' not in stripped:
            i = src.index('\n', src.index('*/', i)) + 1
            continue
        m = MEMBER.match(line)
        if m and top_level and not stripped.startswith(('return', 'new ', 'throw')):
            start = pending if pending is not None else i
            depth, j = 0, src.index('{', i)
            while True:
                if src[j] == '{':
                    depth += 1
                elif src[j] == '}':
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            end = src.index('\n', j) + 1
            out[m.group(1)] = src[start:end]
            pending, i = None, end
            continue
        if stripped and not stripped.startswith(('@', '/**', '*')):
            pending = None
        i = end_of_line + 1
    return out


def main():
    spec = json.load(open(sys.argv[1], encoding='utf-8'))
    src = open(spec['source'], encoding='utf-8').read().replace('\r\n', '\n')
    found = members(src)
    imports = '\n'.join(l for l in src.split('\n') if l.startswith('import '))
    used = set()
    for target in spec['targets']:
        parts = []
        for name in target['members']:
            if name not in found:
                sys.exit(f'member {name} not found; members are {sorted(found)}')
            used.add(name)
            parts.append(found[name].rstrip('\n'))
        body = '\n\n'.join(parts)
        if target.get('extra'):
            body += '\n\n' + target['extra'].rstrip('\n')
        text = target['header'].replace('{{IMPORTS}}', imports).rstrip('\n') + '\n\n' + body + '\n}\n'
        open(target['path'], 'w', encoding='utf-8', newline='').write(text)
    left = sorted(set(found) - used - set(spec.get('drop', [])))
    if left:
        print('NOT MOVED:', left)
    print(f'wrote {len(spec["targets"])} classes')


if __name__ == '__main__':
    main()
