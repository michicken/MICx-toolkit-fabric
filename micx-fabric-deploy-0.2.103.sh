#!/usr/bin/env bash
set -euo pipefail

INST="/Users/micx/Library/Application Support/PrismLauncher/instances/26.2"
JAR="/Users/micx/MICx-toolkit/fabric/build/libs/micx-fabric-0.2.103.jar"
MODS_DIR="$INST/minecraft/mods"
DESKTOP_MODS="$HOME/Desktop/mods"
BACKUP_ROOT="/Users/micx/MICx-toolkit/.zcode/backups/fabric-deploy-0.2.103-$(date +%Y%m%d-%H%M%S)"
FORCE="${1:-}"

printf '%s\n' '== 检查 Minecraft 是否运行...'
RUNNING=0
# LunarClient 的命令行里带着 -DTrickNvidiaDriversForPerformance=' net.minecraft.client.main.Main '，
# 按子串直接判会误报成「游戏在跑」。这里只看真正跑原版主类、且命令行不含 lunarclient 的进程。
if pgrep -f '[n]et\.minecraft\.client\.main\.Main' 2>/dev/null \
        | xargs -I{} ps -o command= -p {} 2>/dev/null \
        | grep -v 'lunarclient' \
        | grep -q 'net\.minecraft\.client\.main\.Main'; then RUNNING=1; fi
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
        | grep -q '"version"[[:space:]]*:[[:space:]]*"0.2.103"'; then
    printf '%s\n' '错误: JAR 内 fabric.mod.json 不是 0.2.103'
    exit 1
fi

SOURCE_SHA=$(shasum -a 256 "$JAR" | awk '{print $1}')
printf '源 JAR: %s\n' "$JAR"
printf '源 SHA-256: %s\n' "$SOURCE_SHA"

mkdir -p "$BACKUP_ROOT"

# 同名旧版（上一次部署的 0.2.103）会被 rename 覆盖，下面的归档循环又会跳过 TARGET，
# 所以这里先单独留档，否则旧产物静默消失（2026-09-15 实际踩到）。
TARGET="$MODS_DIR/micx-fabric-0.2.103.jar"
if [[ -f "$TARGET" ]]; then
    cp -p "$TARGET" "$BACKUP_ROOT/micx-fabric-0.2.103.prev.jar"
    printf '旧同版本留档: %s\n' "$BACKUP_ROOT/micx-fabric-0.2.103.prev.jar"
fi

# 原子换代：先写临时文件、比对无误后 rename 覆盖。
# 运行中的实例持的是旧 JAR 的 inode，rename 之后它继续读旧 JAR，路径瞬时指向新文件；
# 直接对目标 cp 是 O_TRUNC 原地截断同一个 inode（macOS 的 cp 不换 inode），
# 边玩边覆盖会让 Fabric 类加载器读到半个 jar。
TMP="$MODS_DIR/.micx-fabric-0.2.103.jar.new"
cp -p "$JAR" "$TMP"
cmp -s "$JAR" "$TMP"
mv -f "$TMP" "$TARGET"
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
    cp -p "$JAR" "$DESKTOP_MODS/micx-fabric-0.2.103.jar"
    DESKTOP_SHA=$(shasum -a 256 "$DESKTOP_MODS/micx-fabric-0.2.103.jar" | awk '{print $1}')
    [[ "$SOURCE_SHA" == "$DESKTOP_SHA" ]]
    printf '%s\n' '== 桌面副本完成 =='
    printf '目标: %s\n' "$DESKTOP_MODS/micx-fabric-0.2.103.jar"
    printf '目标 SHA-256: %s\n' "$DESKTOP_SHA"
else
    printf '警告: 桌面 mods 目录不存在，跳过: %s\n' "$DESKTOP_MODS"
fi

printf '%s\n' '== 三向 SHA 一致性 =='
printf 'build   %s\n' "$SOURCE_SHA"
printf 'prism   %s\n' "$(shasum -a 256 "$TARGET" | awk '{print $1}')"
if [[ -f "$DESKTOP_MODS/micx-fabric-0.2.103.jar" ]]; then
    printf 'desktop %s\n' "$(shasum -a 256 "$DESKTOP_MODS/micx-fabric-0.2.103.jar" | awk '{print $1}')"
fi
