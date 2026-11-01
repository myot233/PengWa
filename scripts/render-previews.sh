#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
bash ./gradlew :app:updateDebugScreenshotTestDefaultTestSuite --console=plain "$@"
python3 scripts/preview_gallery.py
