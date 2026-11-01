#!/usr/bin/env python3
import hashlib
from html import escape
import json
from pathlib import Path
import re
import shutil
import struct
import tempfile
import xml.etree.ElementTree as ET


SCREENS = {"FaceToFaceScreenshots": "碰一碰 / 扫码", "BluetoothScreenshots": "蓝牙", "ContactsScreenshots": "集邮册", "SettingsScreenshots": "设置", "RequestScreenshots": "交换申请", "NameFormScreenshots": "首次命名", "ScannerPermissionScreenshots": "相机权限", "ContactDetailsScreenshots": "名片详情", "ResponsiveScreenshots": "屏幕适配"}
CONFIGURATIONS = {"浅色": "light", "深色": "dark", "窄屏大字体": "large-font"}


def build_gallery(results, project, output):
    results, project, output = map(Path, (results, project, output))
    previews = []
    names = set()
    for report in sorted(results.glob("TEST-*.xml")):
        suite = ET.parse(report).getroot()
        if any(int(suite.get(key, "0")) for key in ("failures", "errors", "skipped")):
            raise ValueError(f"Screenshot run failed or skipped previews: {report}")
        for case in suite.findall("testcase"):
            if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
                raise ValueError(f"Screenshot run failed or skipped previews: {case.get('name')}")
            properties = {p.get("name"): p.get("value") for p in case.findall("properties/property")}
            method = properties["PreviewScreenshot.methodName"]
            configuration = properties["PreviewScreenshot.previewName"]
            source = Path(properties["PreviewScreenshot.newImagePath"])
            source = (project / source).resolve()
            if (results / "rendered").resolve() not in source.parents:
                raise ValueError(f"Image is outside renderer output: {source}")
            if not source.is_file():
                raise ValueError(f"Missing PNG: {source}")
            data = source.read_bytes()
            if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
                raise ValueError(f"Invalid PNG: {source}")
            width, height = struct.unpack(">II", data[16:24])
            parameter_index = re.search(r"_(\d+)$", case.get("name"))
            index = int(parameter_index.group(1)) if parameter_index else 0
            slug = re.sub(r"[^a-z0-9]+", "-", method.removesuffix("Screenshots").lower()).strip("-")
            config_slug = CONFIGURATIONS.get(configuration, hashlib.sha256(configuration.encode()).hexdigest()[:8])
            filename = f"{slug}-{index + 1:02}-{config_slug}.png"
            if not slug or filename in names:
                raise ValueError(f"Duplicate or invalid image name: {filename}")
            names.add(filename)
            screen = SCREENS.get(method, method)
            previews.append(({
                "name": f"{screen} · 场景 {index + 1}",
                "screen": screen,
                "configuration": configuration,
                "image": filename,
                "width": width,
                "height": height,
            }, source, (list(SCREENS).index(method) if method in SCREENS else len(SCREENS), method, index,
                        list(CONFIGURATIONS).index(configuration) if configuration in CONFIGURATIONS else len(CONFIGURATIONS))))
    if not previews:
        raise ValueError(f"No screenshot results in {results}")
    previews.sort(key=lambda entry: entry[2])
    manifest = {"version": 1, "previews": [entry[0] for entry in previews]}
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent, prefix="preview-gallery-") as temporary:
        staging = Path(temporary) / "gallery"
        staging.mkdir()
        for preview, source, _ in previews:
            shutil.copyfile(source, staging / preview["image"])
        (staging / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (staging / "index.html").write_text(gallery_html(manifest["previews"]), encoding="utf-8")
        if output.exists():
            shutil.rmtree(output)
        staging.rename(output)
    return manifest


def gallery_html(previews):
    groups = {}
    for preview in previews:
        groups.setdefault(preview["screen"], []).append(preview)
    sections, navigation = [], []
    first = True
    for number, (screen, images) in enumerate(groups.items()):
        navigation.append(f'<a href="#screen-{number}">{escape(screen)}</a>')
        cards = []
        for item in images:
            label = f'{item["name"]} / {item["configuration"]}'
            priority = 'fetchpriority="high"' if first else 'loading="lazy"'
            first = False
            cards.append(f'''<figure><a href="{item['image']}"><img src="{item['image']}"
                width="{item['width']}" height="{item['height']}" alt="{escape(label, quote=True)}"
                {priority}></a><figcaption><strong>{escape(item['name'])}</strong>
                <span>{escape(item['configuration'])}</span></figcaption></figure>''')
        sections.append(f'<section id="screen-{number}"><h2>{escape(screen)} <small>{len(images)} 张</small></h2><div class="grid">{"".join(cards)}</div></section>')
    return f'''<!doctype html>
<html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>碰碰娃 · Compose 预览图集</title>
<style>
:root {{ color-scheme: light dark; font-family: system-ui, sans-serif; color: #24222b; background: #f5f4f8; }}
* {{ box-sizing: border-box; }}
body {{ max-width: 1240px; margin: auto; padding: clamp(20px, 4vw, 48px); }}
header {{ padding-block: 12px 24px; }}
.eyebrow {{ letter-spacing: .12em; font-size: 12px; font-weight: 700; color: #68528d; }}
h1 {{ font-size: clamp(28px, 5vw, 44px); margin-block: 8px; }}
p {{ line-height: 1.7; }}
nav {{ display: flex; flex-wrap: wrap; gap: 12px; }}
nav a {{ padding: 8px 16px; border: 1px solid #d5d0de; border-radius: 24px; color: inherit; }}
section {{ margin-block: 32px 48px; scroll-margin-top: 20px; }}
h2 {{ font-size: 24px; }}
small {{ font-size: 14px; font-weight: 400; color: #756d80; }}
.grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr)); gap: 20px; }}
figure {{ min-width: 0; margin: 0; background: #fff; border: 1px solid #dfdae6; border-radius: 16px; overflow: hidden; }}
figure a {{ display: block; }}
img {{ display: block; width: 100%; height: auto; }}
figcaption {{ padding: 14px 16px; display: flex; flex-wrap: wrap; gap: 8px; justify-content: space-between; font-size: 14px; }}
figcaption span {{ color: #756d80; }}
a:focus-visible {{ outline: 3px solid #7955ac; outline-offset: 4px; }}
@media (prefers-color-scheme: dark) {{
  :root {{ color: #ece8f2; background: #18151d; }}
  figure {{ background: #24202a; border-color: #4a4156; }}
  nav a {{ border-color: #5d506f; }}
  .eyebrow, small, figcaption span {{ color: #c6b7d9; }}
}}
</style>
<header><p class="eyebrow">PENGWA / UI PREVIEWS</p><h1>Compose 预览图集</h1>
<p>{len(previews)} 张真实 Layoutlib 渲染图片 · 浅色 / 深色 / 窄屏大字体。点击图片查看原图。</p>
<nav aria-label="页面分类">{' '.join(navigation)}</nav></header>
<main>{''.join(sections)}</main></html>'''


if __name__ == "__main__":
    project = Path(__file__).resolve().parent.parent
    results = project / "app/build/intermediates/debug/updateDebugScreenshotTestDefaultTestSuite/results"
    output = project / "app/build/preview-gallery"
    manifest = build_gallery(results, project, output)
    print(f"Rendered gallery: {output / 'index.html'} ({len(manifest['previews'])} PNGs)")
