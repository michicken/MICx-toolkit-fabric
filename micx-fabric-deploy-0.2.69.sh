#!/usr/bin/env bash
set -euo pipefail

INST="/Users/micx/Library/Application Support/PrismLauncher/instances/26.2"
JAR="/Users/micx/MICx-toolkit/fabric/build/libs/micx-fabric-0.2.69.jar"
MODS_DIR="$INST/minecraft/mods"
BACKUP_ROOT="/Users/micx/MICx-toolkit/.zcode/backups/fabric-deploy-0.2.69-$(date +%Y%m%d-%H%M%S)"

printf '%s\n' '== 检查 Minecraft 是否运行...'
if pgrep -f '[n]et\.minecraft\.client\.main\.Main' >/dev/null 2>&1; then
    printf '%s\n' '错误: Minecraft 正在运行，请先关闭'
    exit 1
fi
printf '%s\n' 'OK: Minecraft 未运行'

if [[ ! -d "$MODS_DIR" ]]; then
    printf '错误: Prism 目标目录不存在: %s\n' "$MODS_DIR"
    exit 1
fi
if [[ ! -f "$JAR" ]]; then
    printf '错误: 构建产物不存在: %s\n' "$JAR"
    exit 1
fi
if ! unzip -p "$JAR" fabric.mod.json 2>/dev/null \
        | grep -q '"version"[[:space:]]*:[[:space:]]*"0.2.69"'; then
    printf '%s\n' '错误: JAR 内 fabric.mod.json 不是 0.2.69'
    exit 1
fi

SOURCE_SHA=$(shasum -a 256 "$JAR" | awk '{print $1}')
printf '源 JAR: %s\n' "$JAR"
printf '源 SHA-256: %s\n' "$SOURCE_SHA"

mkdir -p "$BACKUP_ROOT"
while IFS= read -r -d '' old; do
    mv "$old" "$BACKUP_ROOT/$(basename "$old")"
done < <(find "$MODS_DIR" -maxdepth 1 -type f \( \
    -name 'micx-fabric-*.jar*' -o -name 'MICx-toolkit-*.jar*' \
    \) -print0)

TARGET="$MODS_DIR/micx-fabric-0.2.69.jar"
cp -p "$JAR" "$TARGET"
cmp -s "$JAR" "$TARGET"
TARGET_SHA=$(shasum -a 256 "$TARGET" | awk '{print $1}')
[[ "$SOURCE_SHA" == "$TARGET_SHA" ]]

printf '%s\n' '== Prism 部署完成 =='
printf '目标: %s\n' "$TARGET"
printf '目标 SHA-256: %s\n' "$TARGET_SHA"
printf '旧 MICx JAR 备份: %s\n' "$BACKUP_ROOT"
