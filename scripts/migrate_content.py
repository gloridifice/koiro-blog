#!/usr/bin/env python3
"""Migrate local Markdown to multilingual content packages. Dry-run unless --apply."""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime
import hashlib
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import tomllib
from urllib.parse import quote, unquote, urlsplit, urlunsplit

PLAN = Path(__file__).with_name("content_migration_plan.json")
HEADER = re.compile(r"\A\s*```toml[^\S\r\n]*\r?\n(.*?)^```[^\S\r\n]*(?:\r?\n|\Z)", re.S | re.M)
SNAKE = re.compile(r"[a-z][a-z0-9]*(?:_[a-z0-9]+)*\Z")
LFS = b"version https://git-lfs.github.com/spec/v1"


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def value(v):
    if isinstance(v, bool): return "true" if v else "false"
    if isinstance(v, str): return json.dumps(v, ensure_ascii=False)
    if isinstance(v, list): return "[" + ", ".join(value(x) for x in v) + "]"
    if isinstance(v, (int, float)): return str(v)
    raise ValueError(f"Unsupported metadata value: {v!r}")


def document(fields: dict) -> str:
    return "".join(f"{k if re.fullmatch(r'[A-Za-z0-9_-]+', k) else json.dumps(k, ensure_ascii=False)} = {value(v)}\n" for k, v in fields.items())


def safe_title(title: str) -> str:
    s = re.sub(r'[\\/:*?"<>|\x00-\x09\x0b\x0c\x0e-\x1f\x7f]', '', title)
    if re.fullmatch(r"(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\..*)?", s): s = "_" + s
    # Kotlin's limit is UTF-16 code units, not Unicode code points.
    s = s.encode('utf-16-le')[:240].decode('utf-16-le', errors='ignore').rstrip('. ')
    return s if s.strip() else "_"


def parse_header(text: str, path: Path, report: list):
    m = HEADER.match(text)
    if not m: raise ValueError(f"Missing TOML header: {path}")
    header = m[1]
    # Historic ktoml accepted repeated date and used the final one. Preserve that
    # effective value, retaining overridden values in legacy_dates metadata.
    dates = list(re.finditer(r'(?m)^date\s*=.*$', header))
    earlier = []
    if len(dates) > 1:
        for duplicate in dates[:-1]:
            earlier.append(tomllib.loads(duplicate[0].strip())["date"])
        for duplicate in reversed(dates[:-1]): header = header[:duplicate.start()] + header[duplicate.end():]
        report.append(f"{path}: duplicate dates; retained final effective date")
    parsed = tomllib.loads(header)
    if earlier: parsed['legacy_dates'] = earlier
    return parsed, text[m.end():]


def inline_targets(line: str):
    stack = []
    for i, char in enumerate(line):
        if i and line[i - 1] == '\\': continue
        if char == '[': stack.append((i, i > 0 and line[i - 1] == '!'))
        elif char == ']' and stack:
            _, media = stack.pop()
            if line[i + 1:i + 2] != '(': continue
            a = i + 2
            while a < len(line) and line[a].isspace(): a += 1
            if a >= len(line): continue
            if line[a] == '<':
                a += 1; b = line.find('>', a)
                if b >= 0: yield a, b, line[a:b], media
                continue
            b, depth = a, 0
            while b < len(line):
                if line[b] == '(' and (b == 0 or line[b - 1] != '\\'): depth += 1
                if line[b] == ')' and (b == 0 or line[b - 1] != '\\'):
                    if depth == 0: break
                    depth -= 1
                if line[b].isspace() and depth == 0: break
                b += 1
            if b > a: yield a, b, line[a:b], media


def targets(markdown: str):
    """Yield URL spans outside fenced/inline code; source text is never reserialized."""
    offset = 0
    fence = None
    for line in markdown.splitlines(keepends=True):
        marker = re.match(r'^ {0,3}(`{3,}|~{3,})', line)
        if marker:
            marks = marker[1]
            if fence is None: fence = marks
            elif marks[0] == fence[0] and len(marks) >= len(fence): fence = None
            offset += len(line); continue
        if fence is not None: offset += len(line); continue
        codes = [(m.start(), m.end()) for m in re.finditer(r'(`+).*?\1(?!`)', line)]
        found = []
        found.extend(inline_targets(line))
        for m in re.finditer(r'^ {0,3}\[[^\]]+\]:\s*(?:<([^>]+)>|(\S+))', line):
            group = 1 if m[1] is not None else 2
            found.append((m.start(group), m.end(group), m[group], True))
        # Media/links in trusted raw HTML, preserving exact source ranges.
        class Tags(HTMLParser):
            def handle_starttag(self, tag, attrs):
                raw = self.get_starttag_text()
                _, column = self.getpos()
                for m in re.finditer(r'\b(src|href|poster)\s*=\s*([\'"])(.*?)\2', raw, re.I):
                    found.append((column + m.start(3), column + m.end(3), m[3], m[1].lower() != 'href'))
        Tags(convert_charrefs=False).feed(line)
        occupied = set()
        for a, b, url, media in sorted(found):
            if (a, b) in occupied or any(x <= a < y for x, y in codes): continue
            occupied.add((a, b))
            yield offset + a, offset + b, url, media
        offset += len(line)


def migrate(root: Path, apply=False, plan_path=PLAN):
    if Path(root).is_symlink(): raise ValueError(f"Symlink data root: {root}")
    root = Path(root).resolve()
    plan = json.loads(Path(plan_path).read_text(encoding='utf-8'))
    if not root.is_dir() or root.is_symlink(): raise ValueError(f"Expected real data directory: {root}")
    for path in root.rglob('*'):
        if path.is_symlink(): raise ValueError(f"Symlink: {path}")
        if path.is_file():
            with path.open('rb') as stream:
                if stream.read(len(LFS)) == LFS: raise ValueError(f"Unresolved Git LFS pointer: {path}")
    manifest = root / 'migration-map.toml'
    if manifest.exists():
        records = tomllib.loads(manifest.read_text(encoding='utf-8'))
        if records.get('version') != 1: raise ValueError('Unsupported migration map')
        for item in records['contents']:
            if plan.get(item['old-source']) != item['directory']: raise ValueError('Migration plan differs from applied map')
            package = root / item['directory']
            if (root / item['old-source']).is_file() or not (package / 'meta.toml').is_file() or not (package / 'zh-CN.md').is_file():
                raise ValueError('Mixed or incomplete migration')
        if len(records['contents']) != len(plan): raise ValueError('Incomplete migration map')
        return {'already_migrated': True, 'changed': False}
    actual = {p.relative_to(root).as_posix() for p in root.rglob('*.md')}
    if actual != plan.keys(): raise ValueError(f"Unmapped or stale Markdown: {sorted(actual ^ plan.keys())}")
    destinations = list(plan.values())
    if len({s.casefold() for s in destinations}) != len(destinations): raise ValueError('Destination collision')
    ids = set()
    for src, dest in plan.items():
        target = root / dest
        if not target.resolve().is_relative_to(root) or target.exists(): raise ValueError(f"Unsafe/existing target: {dest}")
        name = target.name
        if not SNAKE.fullmatch(name) or re.fullmatch(r'(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])', name): raise ValueError(f"Invalid id: {name}")
        key = (Path(dest).parts[0], name)
        if key in ids: raise ValueError(f'Duplicate content id: {key}')
        ids.add(key)
    original_hashes = {p.relative_to(root).as_posix(): digest(p) for p in root.rglob('*') if p.is_file()}
    notes, contents, assets, consumed, hashes = [], [], {}, set(), {}
    headers = {}
    for src in plan:
        text = (root / src).read_bytes().decode('utf-8-sig')
        headers[src] = parse_header(text, root / src, notes)
    aliases = set()
    with tempfile.TemporaryDirectory(prefix='koiro-content-migration-') as temporary:
        staged = Path(temporary) / 'data'
        shutil.copytree(root, staged)
        for src, dest in plan.items():
            source = root / src
            package = staged / dest
            package.mkdir(parents=True)
            old, body = headers[src]
            identity = Path(dest).name
            kind = {'blogs':'blog','devlogs':'devlog','actives':'active','portfolio':'portfolio'}[Path(src).parts[0]]
            record = {'kind':kind, 'old-source':src, 'directory':dest, 'id':identity}
            if old.get('published') and kind in ('blog','devlog'):
                old_url = f'/pages/{Path(src).parts[0]}/{safe_title(old["title"])}.html'
                if old_url in aliases: raise ValueError(f'Duplicate legacy URL: {old_url}')
                aliases.add(old_url); record['old-url'] = old_url
            contents.append(record)
            local_images = {}
            def rewrite(url, media=False):
                parsed = urlsplit(url)
                if parsed.scheme or parsed.netloc or not parsed.path or parsed.path.startswith('/'):
                    return url
                original = (source.parent / unquote(parsed.path)).resolve()
                if not original.is_relative_to(root): raise ValueError(f'{source}: escaping URL {url}')
                relative = original.relative_to(root).as_posix()
                if original.suffix.lower() == '.md':
                    if relative not in plan: raise ValueError(f'{source}: unknown article target {url}')
                    new = Path(os.path.relpath(staged / plan[relative] / 'zh-CN.md', package)).as_posix()
                else:
                    if not original.is_file(): raise ValueError(f'{source}: missing attachment {url}')
                    content_hash = digest(original)
                    new = 'images/' + original.name
                    if new.casefold() in local_images and local_images[new.casefold()] != original:
                        new = 'images/' + original.stem + '_' + content_hash[:8] + original.suffix
                    if new.casefold() in local_images and local_images[new.casefold()] != original:
                        raise ValueError(f'Attachment collision: {source}: {url}')
                    local_images[new.casefold()] = original
                    output = package / new
                    output.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(original, output)
                    if digest(output) != content_hash: raise ValueError('Attachment hash mismatch')
                    hashes[f'{dest}/{new}'] = content_hash
                    consumed.add(relative)
                    if old.get('published'):
                        old_url = '/images/' + relative
                        if old_url.lower().startswith('/images/content/'):
                            raise ValueError(f'Legacy image conflicts with reserved content namespace: {old_url}')
                        assets.setdefault(old_url, f'{dest}/{new}')
                return urlunsplit(('', '', quote(new, safe='/._-'), parsed.query, parsed.fragment))
            updates = [(a, b, rewrite(url, media)) for a, b, url, media in targets(body)]
            for a, b, url in reversed(updates): body = body[:a] + url + body[b:]
            common, language = {'id': identity}, {}
            for key, v in old.items():
                if key in ('title', 'description', 'last_edited_date', 'english_title', 'ai-translation'):
                    language[key] = v
                elif key == 'slug': language['summary'] = v
                elif key == 'preview':
                    if not v and not old.get('published'):
                        notes.append(f'{src}: omitted empty draft preview')
                    else: common[key] = rewrite(v, True)
                else: common[key] = v
            language.setdefault('ai-translation', False)
            (package / 'meta.toml').write_text(document(common), encoding='utf-8')
            (package / 'zh-CN.md').write_bytes(('```toml\n' + document(language) + '```\n' + body).encode('utf-8'))
            (staged / src).unlink()
        for relative in consumed:
            # Every parsed reference is now package-local. Unknown/unreferenced files stay.
            (staged / relative).unlink(missing_ok=True)
        # On case-insensitive Windows, mkdir('art') would otherwise retain the
        # pre-existing spelling 'Art'. Rename through a neutral temporary name.
        for old, new in [('Art','art'), ('Chore','chore'), ('Graphics','graphics'), ('Life','life'), ('Programming','programming')]:
            old_path, new_path = staged / 'blogs' / old, staged / 'blogs' / new
            if old_path.exists() and new_path.exists() and old_path.samefile(new_path) and old != new:
                neutral = staged / 'blogs' / (new + '_case_migration')
                old_path.rename(neutral); neutral.rename(new_path)
        for path in sorted(staged.rglob('*'), key=lambda p:len(p.parts), reverse=True):
            if path.is_dir() and not any(path.iterdir()): path.rmdir()
        manifest_text = 'version = 1\n'
        for item in contents: manifest_text += '\n[[contents]]\n' + document(item)
        for old_url, src in sorted(assets.items()): manifest_text += '\n[[assets]]\n' + document({'old-url': old_url, 'source':src})
        tomllib.loads(manifest_text)
        (staged / 'migration-map.toml').write_text(manifest_text, encoding='utf-8')
        for dest, expected in hashes.items():
            if digest(staged / dest) != expected: raise ValueError(f'Changed attachment: {dest}')
        for dest in destinations:
            package = staged / dest
            meta = tomllib.loads((package / 'meta.toml').read_text(encoding='utf-8'))
            md = (package / 'zh-CN.md').read_text(encoding='utf-8')
            h = HEADER.match(md)
            if not h: raise ValueError(f'Invalid staged header: {dest}')
            tomllib.loads(h[1])
            urls = [url for _, _, url, _ in targets(md[h.end():])]
            if meta.get('preview'): urls.append(meta['preview'])
            for url in urls:
                parsed = urlsplit(url)
                if not parsed.scheme and not parsed.netloc and parsed.path and not parsed.path.startswith('/'):
                    file = (package / unquote(parsed.path)).resolve()
                    if not file.is_relative_to(staged) or not file.is_file(): raise ValueError(f'Broken staged reference: {dest}: {url}')
        unreferenced = sorted(p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file() and p.suffix.lower() != '.md' and p.relative_to(root).as_posix() not in consumed)
        report = {'contents':len(contents), 'asset_aliases':len(assets), 'attachment_copies':len(hashes), 'unreferenced':unreferenced, 'notes':notes, 'changed':bool(apply)}
        if apply:
            current = {p.relative_to(root).as_posix(): digest(p) for p in root.rglob('*') if p.is_file()}
            if current != original_hashes: raise ValueError('Content changed during migration; nothing replaced')
            backup = Path(tempfile.mkdtemp(prefix='koiro-data-backup-')) / 'data'
            shutil.copytree(root, backup)
            if {p.relative_to(backup).as_posix(): digest(p) for p in backup.rglob('*') if p.is_file()} != original_hashes:
                raise ValueError(f'Backup verification failed; original data retained. Backup: {backup}')
            sibling = Path(tempfile.mkdtemp(prefix='.koiro-migration-', dir=root.parent))
            incoming = sibling / 'data'
            shutil.copytree(staged, incoming)
            displaced = sibling / 'original'
            try:
                root.rename(displaced)
                incoming.rename(root)
            except BaseException:
                if not root.exists() and displaced.exists(): displaced.rename(root)
                raise
            finally:
                if root.exists(): shutil.rmtree(sibling)
            report['backup'] = str(backup)
        return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', nargs='?', default='data')
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--plan', type=Path, default=PLAN)
    args = parser.parse_args()
    print(json.dumps(migrate(Path(args.directory), args.apply, args.plan), ensure_ascii=False, indent=2))

if __name__ == '__main__': main()
