#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist"

cd "$ROOT_DIR"

MOD_VERSION="$(grep -E '^mod_version=' "$ROOT_DIR/gradle.properties" | cut -d= -f2-)"
MC_VERSION="$(grep -E '^minecraft_version=' "$ROOT_DIR/gradle.properties" | cut -d= -f2-)"

./gradlew --no-daemon --max-workers=1 \
  :common-1.20.1:build \
  :fabric-1.20.1:build \
  :forge-1.20.1:build

mkdir -p "$DIST_DIR"
rm -f "$DIST_DIR"/*.jar

declare -a jars=(
  "$ROOT_DIR/fabric-1.20.1/build/libs/chatae-${MOD_VERSION}-fabric-${MC_VERSION}.jar"
  "$ROOT_DIR/forge-1.20.1/build/libs/chatae-${MOD_VERSION}-forge-${MC_VERSION}.jar"
  "$ROOT_DIR/neoforge-1.20.1/build/libs/chatae-${MOD_VERSION}-neoforge-${MC_VERSION}.jar"
)

for jar in "${jars[@]}"; do
  if [[ -f "$jar" ]]; then
    cp -f "$jar" "$DIST_DIR/"
  fi
done

printf "Copied jars to %s\n" "$DIST_DIR"
