#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist"

cd "$ROOT_DIR"

./gradlew --no-daemon --max-workers=1 \
  :common-1.20.1:build \
  :fabric-1.20.1:build \
  :forge-1.20.1:build

mkdir -p "$DIST_DIR"
rm -f "$DIST_DIR"/*.jar

shopt -s nullglob
for jar in "$ROOT_DIR"/common-1.20.1/build/libs/*.jar \
           "$ROOT_DIR"/fabric-1.20.1/build/libs/*.jar \
           "$ROOT_DIR"/forge-1.20.1/build/libs/*.jar; do
  cp -f "$jar" "$DIST_DIR/"
done

printf "Copied jars to %s\n" "$DIST_DIR"
