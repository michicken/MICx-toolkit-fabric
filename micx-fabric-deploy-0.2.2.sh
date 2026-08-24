#!/bin/bash
set -euo pipefail

INST="/Users/micx/Library/Application Support/PrismLauncher/instances/26.2"
JAR="/Users/micx/MICx-toolkit/fabric/build/libs/micx-fabric-0.2.2.jar"
BACKUP_ROOT="/Users/micx/MICx-toolkit/.zcode/backups/fabric-deploy-0.2.2-$(date +%Y%m%d-%H%M%S)"

printf '%s\n' '== 检查游戏是否运行...'
if pgrep -f '[P]ris[m]Launcher|[M]inecraft' >/dev/null 2>&1; then
    printf '%s\n' '错误: PrismLauncher/Minecraft 正在运行，请先关闭'
    exit 1
fi
printf '%s\n' 'OK: 游戏未运行'

printf '%s\n' '== 检查 0.2.2 Fabric JAR...'
if [[ ! -f "$JAR" ]]; then
    printf '错误: %s 不存在\n' "$JAR"
    exit 1
fi
if ! unzip -p "$JAR" fabric.mod.json 2>/dev/null | grep -q '"version"[[:space:]]*:[[:space:]]*"0.2.2"'; then
    printf '%s\n' '错误: JAR 内 fabric.mod.json 不是 0.2.2'
    exit 1
fi
SHA=$(shasum -a 256 "$JAR" | awk '{print $1}')
printf 'OK: %s bytes\n' "$(wc -c < "$JAR" | tr -d ' ')"
printf 'SHA-256: %s\n' "$SHA"

mkdir -p "$BACKUP_ROOT"
MODS_DIR="$INST/minecraft/mods"
[[ -d "$MODS_DIR" ]] || MODS_DIR="$INST/mods"
mkdir -p "$MODS_DIR"
# 只匹配 MICx 的 jar（micx-fabric-*.jar / MICx-toolkit-*.jar），防误删 ZBHelpStart 等第三方
while IFS= read -r -d '' old; do
    mv "$old" "$BACKUP_ROOT/$(basename "$old")"
done < <(find "$MODS_DIR" -maxdepth 1 -type f \( -name 'micx-fabric-*.jar*' -o -name 'MICx-toolkit-*.jar*' \) -print0)
cp -p "$JAR" "$MODS_DIR/micx-fabric-0.2.2.jar"
cp -p "$JAR" "$HOME/Desktop/micx-fabric-0.2.2.jar"

printf '%s\n' '== 部署完成 =='
printf 'mods: %s/micx-fabric-0.2.2.jar\n' "$MODS_DIR"
printf 'Desktop: %s\n' "$HOME/Desktop/micx-fabric-0.2.2.jar"
printf '旧文件备份: %s\n' "$BACKUP_ROOT"
printf 'SHA-256: %s\n' "$SHA"
