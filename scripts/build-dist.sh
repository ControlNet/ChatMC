#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist"

cd "$ROOT_DIR"

MOD_VERSION="$(grep -E '^mod_version=' "$ROOT_DIR/gradle.properties" | cut -d= -f2-)"
MC_VERSION="$(grep -E '^minecraft_version=' "$ROOT_DIR/gradle.properties" | cut -d= -f2-)"

./gradlew --no-daemon --configure-on-demand --max-workers=1 \
  :base:common-1.20.1:build \
  :base:fabric-1.20.1:build \
  :base:forge-1.20.1:build

./gradlew --no-daemon --configure-on-demand --max-workers=1 \
  :ext-ae:common-1.20.1:build \
  :ext-ae:fabric-1.20.1:build \
  :ext-ae:forge-1.20.1:build

./gradlew --no-daemon --configure-on-demand --max-workers=1 \
  :ext-matrix:common-1.20.1:build \
  :ext-matrix:fabric-1.20.1:build \
  :ext-matrix:forge-1.20.1:build

mkdir -p "$DIST_DIR"
rm -f "$DIST_DIR"/*.jar

declare -a jars=(
  "$ROOT_DIR/base/fabric-1.20.1/build/libs/mineagent-${MOD_VERSION}-fabric-${MC_VERSION}.jar"
  "$ROOT_DIR/base/forge-1.20.1/build/libs/mineagent-${MOD_VERSION}-forge-${MC_VERSION}.jar"
  "$ROOT_DIR/ext-ae/fabric-1.20.1/build/libs/mineagentae-${MOD_VERSION}-fabric-${MC_VERSION}.jar"
  "$ROOT_DIR/ext-ae/forge-1.20.1/build/libs/mineagentae-${MOD_VERSION}-forge-${MC_VERSION}.jar"
  "$ROOT_DIR/ext-matrix/fabric-1.20.1/build/libs/mineagentmatrix-${MOD_VERSION}-fabric-${MC_VERSION}.jar"
  "$ROOT_DIR/ext-matrix/forge-1.20.1/build/libs/mineagentmatrix-${MOD_VERSION}-forge-${MC_VERSION}.jar"
)

for jar in "${jars[@]}"; do
  if [[ -f "$jar" ]]; then
    cp -f "$jar" "$DIST_DIR/"
  fi
done

printf "Copied jars to %s\n" "$DIST_DIR"
