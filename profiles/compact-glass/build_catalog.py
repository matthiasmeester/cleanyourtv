#!/usr/bin/env python3
"""Validate licensed online photo metadata and generate the bounded-cache catalogue.

Requires Pillow for actual pixel/header verification. Never edits or resizes photographs.
Run from any directory: python profiles/compact-glass/build_catalog.py [--check]
"""
import argparse
import collections
import csv
import hashlib
import html
import io
import json
import re
from urllib.parse import unquote, urlsplit
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
LICENSE = re.compile(r"(?:CC BY(?:-SA)? (?:1\.0|2\.0|2\.5|3\.0|4\.0)(?: [a-z]{2})?|CC0|Public domain)", re.I)
NAME = re.compile(r'[A-Za-z0-9][A-Za-z0-9_-]*\.jpg')
SEED_LIMIT = 20_000_000


def safe_url(value, download=False):
    if not isinstance(value, str) or any(ord(c) <= 32 or ord(c) == 127 for c in value):
        return False
    try:
        parsed = urlsplit(value)
        if parsed.scheme not in {'https', 'http'} or not parsed.hostname or parsed.username is not None or parsed.password is not None:
            return False
        port = parsed.port  # Also rejects malformed and out-of-range ports.
        if download:
            return (parsed.scheme == 'https' and parsed.hostname in {'upload.wikimedia.org', 'thumb.wikimedia.org'}
                    and port in (None, 443) and not parsed.fragment and parsed.path.startswith('/wikipedia/'))
        return True
    except ValueError:
        return False


def validate_seed_files(root):
    manifest = json.loads((root/'manifest.json').read_text(encoding='utf8'))
    declared = manifest['wallpapers']['offline_seed_files']
    if (not isinstance(declared, list) or any(not isinstance(n, str) or not NAME.fullmatch(n) for n in declared)
            or len(set(declared)) != len(declared)):
        raise ValueError('Invalid or duplicate offline seed names')
    files = [p for p in (root/'wallpapers').iterdir() if p.suffix.lower() in {'.jpg','.jpeg','.png','.webp'}]
    if any(not p.is_file() or p.is_symlink() for p in files):
        raise ValueError('Seed images must be regular files, not directories or symlinks')
    actual = {p.name for p in files}
    if actual != set(declared):
        raise ValueError(f'Offline seed set differs from manifest: {actual ^ set(declared)}')
    if sum(p.stat().st_size for p in files) > SEED_LIMIT:
        raise ValueError('Offline seed images exceed 20,000,000 bytes')
    return actual




def collection(root=ROOT):
    seeds = validate_seed_files(root)
    images = []
    seen_names, seen_hashes, seen_pages = set(), set(), set()
    for manifest, period in (("wallpapers.sources.json", "day"),
                             ("night-wallpapers.sources.json", "night")):
        entries = json.loads((root / manifest).read_text(encoding="utf-8"))["images"]
        if len(entries) < 100:
            raise ValueError(f"{period}: requires at least 100 photos, found {len(entries)}")
        for item in entries:
            name = item["file"]
            if len(name)>150 or not NAME.fullmatch(name):
                raise ValueError(f"Unsafe filename: {name}")
            for key in ("title", "location", "author", "license", "license_url", "page", "download_url"):
                if not isinstance(item.get(key), str) or not item[key].strip():
                    raise ValueError(f"{name}: missing {key}")
            if not LICENSE.fullmatch(item["license"]):
                raise ValueError(f"{name}: unreviewed licence")
            for key in ("license_url", "page", "download_url", "original_url"):
                if not safe_url(item.get(key)):
                    raise ValueError(f"{name}: invalid {key}")
            if item.get('story_source') and not safe_url(item['story_source']):
                raise ValueError(f'{name}: invalid story_source')
            if not safe_url(item['download_url'], download=True):
                raise ValueError(f'{name}: download must use an approved Wikimedia HTTPS host')
            path = root / "wallpapers" / name
            digest = item.get('sha256') or ''
            if digest and not re.fullmatch(r'[0-9a-f]{64}',digest):
                raise ValueError(f'{name}: invalid SHA256')
            identity=unquote(item['original_url'].split('?')[0])
            if name in seen_names or (digest and digest in seen_hashes) or identity in seen_pages:
                raise ValueError(f"{name}: duplicate file, content, or source photograph")
            seen_names.add(name)
            if digest:seen_hashes.add(digest)
            seen_pages.add(identity)
            for dims in [item['dimensions'],item['original_dimensions']]:
                if dims[0]<2560 or dims[1]<1440 or dims[0]<=dims[1]:
                    raise ValueError(f'{name}: metadata must describe landscape >=2560x1440')
            if path.exists():
                if digest and hashlib.sha256(path.read_bytes()).hexdigest()!=digest:
                    raise ValueError(f'{name}: checksum mismatch')
                with Image.open(path) as image:
                    if image.format!='JPEG' or list(image.size)!=item['dimensions']:
                        raise ValueError(f'{name}: seed dimensions disagree')
                    image.verify()
            item = dict(item, period=period)
            images.append(item)
    if not seeds.issubset(seen_names):
        raise ValueError(f'Uncatalogued seed JPEGs: {seeds - seen_names}')
    return images


def outputs(images, root=ROOT):
    captions, credits, catalog = [], [], []
    buffer = io.StringIO(newline="")
    fields = ["file", "period", "category", "title", "location", "story", "author", "license", "license_url", "page", "download_url"]
    writer = csv.DictWriter(buffer, fieldnames=fields, extrasaction="ignore")
    writer.writeheader()
    cards = {"day": [], "night": []}
    for item in images:
        name = item["file"]
        # Tabs/newlines delimit TV metadata; literal backslash-n is decoded only for credits.
        clean = lambda value: " ".join(str(value).split())
        caption=clean(item['location'])
        if len(caption)>80:raise ValueError(f'{name}: caption exceeds 80 characters')
        captions.append(name + "\t" + caption)
        catalog.append('\t'.join([name,item['download_url'],item.get('sha256') or '',item['period']]))
        parts = [item["title"], item["location"], item.get("story", ""),
                 f'{item["author"]} — {item["license"]}', item["license_url"], item["page"]]
        if item.get("story_source") and item["story_source"] != item["page"]:
            parts.append(item["story_source"])
        credit='\\n'.join(dict.fromkeys(clean(p) for p in parts if p))
        if len(credit)>4096:raise ValueError(f'{name}: credit exceeds 4096 characters')
        credits.append(name + "\t" + credit)
        writer.writerow({key: clean(value) for key, value in item.items()})
        esc = lambda value: html.escape(clean(value), quote=True)
        width, height = item["dimensions"]
        story = esc(item.get("story", ""))
        story_link = item.get("story_source", item["page"])
        cards[item["period"]].append(
            f'<article id="{esc(name)}"><a href="{esc(item["page"])}">'
            f'<img loading="lazy" referrerpolicy="no-referrer" src="{esc(item["download_url"])}" alt="{esc(item["title"])}"></a>'
            f'<h3>{esc(item["title"])}</h3><p class="location">{esc(item["location"])}</p>'
            f'<p>{story}</p><p class="meta">{esc(name)} · {width} × {height} · {esc(item["category"])}</p>'
            f'<p class="meta">{esc(item["author"])} · <a href="{esc(item["license_url"])}">{esc(item["license"])}</a></p>'
            f'<p><a href="{esc(item["page"])}">Original source</a> · '
            f'<a href="{esc(story_link)}">Story source</a></p></article>')
    counts = collections.Counter(item["period"] for item in images)
    sections = "".join(f'<section id="{period}"><h2>{period.title()} — {counts[period]} photos</h2>'
                       f'<div class="grid">{"".join(cards[period])}</div></section>' for period in ("day", "night"))
    page = '''<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https://upload.wikimedia.org https://thumb.wikimedia.org; style-src 'unsafe-inline'">
<title>TV photo collection — locations and stories</title>
<style>body{margin:0;background:#111820;color:#e9eef3;font:16px/1.5 system-ui,sans-serif}
header,main{max-width:1500px;margin:auto;padding:24px}h1{font-size:30px}h2{margin-top:36px}
a{color:#a9d9f7}nav a{margin-right:24px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:24px}
article{background:#1c2732;border:1px solid #344453;border-radius:12px;overflow:hidden}
article img{width:100%;height:210px;object-fit:contain;background:#080c10}article p,article h3{margin:12px 18px}
.location{color:#bfe7e0}.meta{font-size:12px;color:#b1bec8}h3{font-size:17px}section{scroll-margin-top:20px}
</style><header><h1>TV photo collection</h1><p>Every photograph is at least 2560 × 1440 pixels.
These are original or official source derivatives, never locally upscaled. Some dimensions are source metadata rather than verified downloads; the TV validates actual image dimensions before display.
Click a photo to open its source page. Locations refer to the scene or venue; unknown archival locations are explicitly labelled.
Day and night are display moods: the night set also includes dark concert and space photographs, without artificially darkening daytime scenes.</p>
<nav><a href="#day">Day collection</a><a href="#night">Night collection</a><a href="catalog.csv">Spreadsheet index (CSV)</a></nav>
<p>This catalogue loads images from Wikimedia as you scroll and therefore requires internet access. Wikimedia receives your IP address and requested image paths. No scripts or analytics are included. This browser page is not the TV cache and can use more than 100 MB if you browse the entire catalogue.</p></header><main>'''
    page += sections + "</main></html>\n"
    config = json.loads((root / "config.json").read_text(encoding="utf-8"))
    day = [item["file"] for item in images if item["period"] == "day"]
    night = [item["file"] for item in images if item["period"] == "night"]
    config["wallpaper_moods"] = {"morning": day, "evening": day, "night": night}
    config.update(fade_rows=False,advanced_effects=False,reduced_motion=True)
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    manifest["wallpapers"].update(count=len(images), day_count=len(day), night_count=len(night),
        minimum_width_pixels=min(i["dimensions"][0] for i in images),
        minimum_height_pixels=min(i["dimensions"][1] for i in images),
        maximum_width_pixels=max(i["dimensions"][0] for i in images),
        storage='online catalogue with bounded local cache',cache_limit_bytes=100000000,
        cache_directory='wallpaper-cache',maximum_single_image_bytes=20000000,
        included_seed_bytes=sum((root/'wallpapers'/name).stat().st_size for name in validate_seed_files(root)),
        network_permission='android.permission.INTERNET',
        allowed_image_hosts=['upload.wikimedia.org','thumb.wikimedia.org'],
        download_mode='on-demand while rotating Home wallpapers',api_key_required=False,
        catalogue="catalog.html", index="catalog.csv",online_catalog='wallpapers/catalog.tsv',
        metadata_only_count=sum(not i.get('download_verified',False) for i in images),
        credits="wallpapers/credits.txt", locations="wallpapers/captions.txt")
    manifest['wallpapers'].pop('total_bytes',None)
    manifest['wallpapers'].setdefault('offline_seed_files',['001.jpg','002.jpg','013.jpg','culture-001.jpg'])
    online='\n'.join(catalog)+'\n'
    if len(online.encode('utf8'))>1024*1024:raise ValueError('Catalogue exceeds 1 MiB')
    return {"wallpapers/catalog.tsv":online,"wallpapers/captions.txt": "\n".join(captions) + "\n",
            "wallpapers/credits.txt": "\n".join(credits) + "\n", "catalog.csv": buffer.getvalue(),
            "catalog.html": page, "config.json": json.dumps(config, ensure_ascii=False, indent=2) + "\n",
            "manifest.json": json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify without changing generated files")
    args = parser.parse_args()
    images = collection()
    for name, content in outputs(images).items():
        path = ROOT / name
        if args.check:
            if not path.is_file() or path.read_text(encoding="utf-8") != content.replace("\r\n", "\n"):
                raise SystemExit(f"Stale generated file: {name}; run build_catalog.py")
        else:
            path.write_text(content, encoding="utf-8", newline="")
    print(f'Validated {len(images)} unique HD catalogue entries: {dict(collections.Counter(i["period"] for i in images))}; TV image cache capped at 100,000,000 bytes')


if __name__ == "__main__":
    main()
