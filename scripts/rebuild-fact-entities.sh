#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [ -f ".uesugi/conf/.env.local" ]; then
  set -a
  # shellcheck disable=SC1091
  source ".uesugi/conf/.env.local"
  set +a
fi

if [ "$#" -eq 0 ]; then
  exec ./gradlew :erii-core:rebuildFactEntities
fi

exec ./gradlew :erii-core:rebuildFactEntities --args="$*"
