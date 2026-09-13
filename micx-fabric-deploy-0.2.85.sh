#!/usr/bin/env bash
set -euo pipefail

INST="/Users/micx/Library/Application Support/PrismLauncher/instances/26.2"
JAR="/Users/micx/MICx-toolkit/fabric/build/libs/micx-fabric-0.2.85.jar"
MODS_DIR="$INST/minecraft/mods"
DESKTOP_MODS="$HOME/Desktop/mods"
BACKUP_ROOT="/Users/micx/MICx-toolkit/.zcode/backups/fabric-deploy-0.2.85-$(date +%Y%m%d-%H%M%S)"
FORCE="${1:-}"

printf '%s\n' '== 检查 Minecraft 是否运行...'
RUNNING=0
if pgrep -f '[n]et\.minecraft\.client\.main\.Main' >/dev/null 2>&1; then RUNNING=1; fi
if pgrep -f '[o]rg\.prismlauncher\.EntryPoint' >/dev/null 2>&1; then RUNNING=1; fi
if [[ "$RUNNING" == 1 ]]; then
    if [[ "$FORCE" != "--force" ]]; then
        printf '%s\n' '错误: Minecraft 正在运行，请先关闭（或显式传 --force）'
        exit 1
    fi
    printf '%s\n' '警告: Minecraft 正在运行，但收到 --force；新 JAR 需重启游戏才会加载'
else
    printf '%s\n' 'OK: Minecraft 未运行'
fi

if [[ ! -d "$MODS_DIR" ]]; then
    printf '错误: Prism 目标目录不存在: %s\n' "$MODS_DIR"
    exit 1
fi
if [[ ! -f "$JAR" ]]; then
    printf '错误: 构建产物不存在: %s\n' "$JAR"
    exit 1
fi
if ! unzip -p "$JAR" fabric.mod.json 2>/dev/null \
        | grep -q '"version"[[:space:]]*:[[:space:]]*"0.2.85"'; then
    printf '%s\n' '错误: JAR 内 fabric.mod.json 不是 0.2.85'
    exit 1
fi

SOURCE_SHA=$(shasum -a 256 "$JAR" | awk '{print $1}')
printf '源 JAR: %s\n' "$JAR"
printf '源 SHA-256: %s\n' "$SOURCE_SHA"

mkdir -p "$BACKUP_ROOT"

# 先落新 JAR：运行中的实例仍持有旧 JAR 的 inode，路径不会被提前断开。
TARGET="$MODS_DIR/micx-fabric-0.2.85.jar"
cp -p "$JAR" "$TARGET"
cmp -s "$JAR" "$TARGET"
TARGET_SHA=$(shasum -a 256 "$TARGET" | awk '{print $1}')
[[ "$SOURCE_SHA" == "$TARGET_SHA" ]]
printf '%s\n' '== Prism 部署完成 =='
printf '目标: %s\n' "$TARGET"
printf '目标 SHA-256: %s\n' "$TARGET_SHA"

# 再归档旧版：Prism 与被软链到外置 SSD，跨设备不能用 mv，改为 cp + rm。
ARCHIVED=0
while IFS= read -r -d '' old; do
    [[ "$old" == "$TARGET" ]] && continue
    cp -p "$old" "$BACKUP_ROOT/$(basename "$old")"
    rm -f "$old"
    printf '已归档并移出 mods: %s\n' "$(basename "$old")"
    ARCHIVED=$((ARCHIVED + 1))
done < <(find "$MODS_DIR" -maxdepth 1 -type f \( \
    -name 'micx-fabric-*.jar*' -o -name 'MICx-toolkit-*.jar*' \
    \) -print0)
printf '旧 MICx JAR 备份: %s（共 %d 个）\n' "$BACKUP_ROOT" "$ARCHIVED"

# 桌面保留全量历史副本，只追加不删除。
if [[ -d "$DESKTOP_MODS" ]]; then
    cp -p "$JAR" "$DESKTOP_MODS/micx-fabric-0.2.85.jar"
    DESKTOP_SHA=$(shasum -a 256 "$DESKTOP_MODS/micx-fabric-0.2.85.jar" | awk '{print $1}')
    [[ "$SOURCE_SHA" == "$DESKTOP_SHA" ]]
    printf '%s\n' '== 桌面副本完成 =='
    printf '目标: %s\n' "$DESKTOP_MODS/micx-fabric-0.2.85.jar"
    printf '目标 SHA-256: %s\n' "$DESKTOP_SHA"
else
    printf '警告: 桌面 mods 目录不存在，跳过: %s\n' "$DESKTOP_MODS"
fi

printf '%s\n' '== 三向 SHA 一致性 =='
printf 'build   %s\n' "$SOURCE_SHA"
printf 'prism   %s\n' "$(shasum -a 256 "$TARGET" | awk '{print $1}')"
if [[ -f "$DESKTOP_MODS/micx-fabric-0.2.85.jar" ]]; then
    printf 'desktop %s\n' "$(shasum -a 256 "$DESKTOP_MODS/micx-fabric-0.2.85.jar" | awk '{print $1}')"
fi
