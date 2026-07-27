#!/usr/bin/env python3
"""
Build a single changed source module and publish its updated apk/jar/icon +
index.json/index.min.json entry, without rebuilding the full multi-module repo index.

Used by .github/workflows/publish-changed-sources.yml — for a repo maintained by one
person doing occasional single-source fixes, rebuilding all ~90 extensions (the `repo`
Gradle task's approach) on every push is unnecessary; this only touches what changed.

Only updates an EXISTING source's index entry (apk/code/version fields) — adding a
brand-new source still needs a full `./gradlew repo` run once to create its first entry.

Usage:
    python scripts/publish_source.py <lang> <name>
    e.g. python scripts/publish_source.py en freewebnovel
"""

import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def read_version(build_gradle: Path) -> tuple[int, str]:
    content = build_gradle.read_text(encoding="utf-8")
    version_code = re.search(r"versionCode\s*=\s*(\d+)", content)
    lib_version = re.search(r'libVersion\s*=\s*"([^"]+)"', content)
    if not version_code or not lib_version:
        raise ValueError(f"Could not find versionCode/libVersion in {build_gradle}")
    code = int(version_code.group(1))
    return code, f"{lib_version.group(1)}.{code}"


def run(cmd: list[str]):
    print(f"$ {' '.join(cmd)}")
    subprocess.run(cmd, cwd=ROOT, check=True)


def publish_source(lang: str, name: str):
    module_dir = ROOT / "sources" / lang / name
    build_gradle = module_dir / "build.gradle.kts"
    if not build_gradle.exists():
        print(f"Skipping {lang}/{name}: no build.gradle.kts (not a leaf source module)")
        return

    pkg = f"ireader.{name}.{lang}"
    index_min_path = ROOT / "index.min.json"
    index_path = ROOT / "index.json"
    index_min = json.loads(index_min_path.read_text(encoding="utf-8"))
    entry = next((e for e in index_min if e.get("pkg") == pkg), None)
    if entry is None:
        print(f"Skipping {pkg}: no existing index entry (new sources need a full `./gradlew repo` run)")
        return

    code, version = read_version(build_gradle)
    if code == entry.get("code"):
        print(f"Skipping {pkg}: versionCode unchanged ({code})")
        return

    gradle_module = f":extensions:individual:{lang}:{name}"
    flavor = lang[0].upper() + lang[1:]
    run(["./gradlew", f"{gradle_module}:assemble{flavor}Release", "--no-configuration-cache"])

    apk_dir = module_dir / "build" / "outputs" / "apk" / lang / "release"
    built_apks = list(apk_dir.glob("*.apk"))
    if len(built_apks) != 1:
        raise RuntimeError(f"Expected exactly one release apk in {apk_dir}, found {built_apks}")
    built_apk = built_apks[0]

    old_apk_name = entry["apk"]
    new_apk_name = built_apk.name
    old_icon_name = old_apk_name.replace(".apk", ".png")
    new_icon_name = new_apk_name.replace(".apk", ".png")
    old_jar_name = old_apk_name.replace(".apk", ".jar")
    new_jar_name = new_apk_name.replace(".apk", ".jar")

    (ROOT / "apk" / old_apk_name).unlink(missing_ok=True)
    (ROOT / "apk" / new_apk_name).write_bytes(built_apk.read_bytes())

    old_icon = ROOT / "icon" / old_icon_name
    new_icon = ROOT / "icon" / new_icon_name
    if old_icon.exists():
        new_icon.write_bytes(old_icon.read_bytes())
        old_icon.unlink()
    else:
        print(f"Warning: no existing icon at {old_icon}, leaving icon unchanged")

    (ROOT / "jar" / old_jar_name).unlink(missing_ok=True)
    run([
        "./gradlew", "regenerateSourceJar", "--no-configuration-cache",
        f"-PsourceApk={built_apk.relative_to(ROOT)}",
        f"-PsourceJar=jar/{new_jar_name}",
    ])

    entry["apk"] = new_apk_name
    entry["code"] = code
    entry["version"] = version
    index_min_path.write_text(json.dumps(index_min, separators=(",", ":")), encoding="utf-8")
    index_path.write_text(json.dumps(index_min, indent=2) + "\n", encoding="utf-8")

    print(f"Published {pkg}: {old_apk_name} -> {new_apk_name} (code {entry['code']} -> {code})")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    publish_source(sys.argv[1], sys.argv[2])
