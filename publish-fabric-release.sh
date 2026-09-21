#!/usr/bin/env bash
# 发布一版 26.2 Fabric，让玩家的客户端自动更新到它。
#
#   ./publish-fabric-release.sh                 # 用 gradle.properties 里的当前版本
#   ./publish-fabric-release.sh 0.2.126         # 发指定版本（必须已经是 gradle.properties 里的值）
#   ./publish-fabric-release.sh --delay-minutes 60
#   ./publish-fabric-release.sh --dry-run       # 只构建 + 算校验值，不碰服务器
#
# 这个脚本**不部署到本机实例**：本机那份也交给自动更新自己去换，否则就测不到真实路径了。
#
# 服务器侧（64.83.26.126，nginx）：
#   /var/www/zombies/version.json                清单，location 精确匹配 + alias，Cache-Control no-store
#   /var/www/zombies/dl/<版本>/<文件名>            安装包，location ^~ /zombies/dl/ → alias
# 清单里顶层 version 就写本版 Fabric 版本号（同时条目里也写一份）：
#   * 1.8.9 门控读顶层字段。它的比较是「首位不同就按首位判」——本地 2.x vs 远端 0.2.x，
#     首位 2>0 直接判 LATEST，所以 1.8.9 老用户既不会被拦也不会看到升级提示（正合适：
#     Forge 已停止开发，没有新版可给）。**只要 Fabric 版本号还是 0.x，这条就成立。**
#   * 0.2.125 之前的客户端不认条目里的 version，只读顶层。顶层必须跟着走，
#     否则老客户端会拿 0.0.0 跟自己比，判成「已是最新」而永远不更新。
#   * 0.2.125 起客户端优先用条目里的 version；两个字段保持一致最省事。
set -euo pipefail

HOST="${MICX_PUBLISH_HOST:-root@64.83.26.126}"
SITE="https://zombie.nienie.fun"
WEB_ROOT="/var/www/zombies"
KEY="fabric-26.2"
JDK25="${JDK25:-$HOME/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.4+7/Contents/Home}"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DELAY_MINUTES=30
DRY_RUN=0
VERSION=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --delay-minutes) DELAY_MINUTES="$2"; shift 2 ;;
        --dry-run) DRY_RUN=1; shift ;;
        -h|--help) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) VERSION="$1"; shift ;;
    esac
done

cd "$PROJECT_DIR"
PROP_VERSION="$(grep -E '^mod_version=' gradle.properties | cut -d= -f2 | tr -d '[:space:]')"
VERSION="${VERSION:-$PROP_VERSION}"
if [[ "$VERSION" != "$PROP_VERSION" ]]; then
    printf '错误: 传入版本 %s 与 gradle.properties 的 %s 不一致；先改 gradle.properties 再发\n' "$VERSION" "$PROP_VERSION" >&2
    exit 1
fi

JAR="build/libs/micx-fabric-${VERSION}.jar"
FILE_NAME="$(basename "$JAR")"

printf '== 备份当前线上清单 ==\n'
PREV="$(curl -sS --max-time 15 "${SITE}/zombies/version.json" || true)"
if [[ -z "$PREV" ]]; then
    printf '警告: 拿不到当前清单（首次发布或端点异常），将新建一份\n'
    PREV='{"version":"0.0.0","severeDelayMinutes":60,"outdatedDelayMinutes":10}'
fi

printf '== 构建 %s ==\n' "$VERSION"
JAVA_HOME="$JDK25" ./gradlew clean build --console=plain -q
if [[ ! -f "$JAR" ]]; then
    printf '错误: 构建产物不存在: %s\n' "$JAR" >&2
    exit 1
fi
SIZE="$(stat -f%z "$JAR")"
SHA="$(shasum -a 256 "$JAR" | awk '{print $1}')"
PUBLISHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
URL="/zombies/dl/${VERSION}/${FILE_NAME}"
printf '产物: %s\n大小: %s\nSHA-256: %s\n发布时间: %s\n' "$JAR" "$SIZE" "$SHA" "$PUBLISHED_AT"

printf '== 生成新清单 ==\n'
NEW_MANIFEST="$(PREV="$PREV" KEY="$KEY" VER="$VERSION" FILE_NAME="$FILE_NAME" URL="$URL" \
    SHA="$SHA" SIZE="$SIZE" PUBLISHED_AT="$PUBLISHED_AT" DELAY="$DELAY_MINUTES" \
    python3 - <<'PY'
import json, os
prev = json.loads(os.environ["PREV"])
manifest = {
    # 顶层那份给 1.8.9 门控和 0.2.125 之前的客户端读，见脚本头注释。两边保持一致。
    "version": os.environ["VER"],
    "publishedAt": os.environ["PUBLISHED_AT"],
    "severeDelayMinutes": prev.get("severeDelayMinutes", 60),
    "outdatedDelayMinutes": prev.get("outdatedDelayMinutes", 10),
    "installDelayMinutes": int(os.environ["DELAY"]),
    "files": dict(prev.get("files", {})),
}
manifest["files"][os.environ["KEY"]] = {
    "version": os.environ["VER"],
    "fileName": os.environ["FILE_NAME"],
    "url": os.environ["URL"],
    "sha256": os.environ["SHA"],
    "size": int(os.environ["SIZE"]),
}
print(json.dumps(manifest, ensure_ascii=False, indent=2))
PY
)"
printf '%s\n' "$NEW_MANIFEST"

if [[ "$DRY_RUN" == 1 ]]; then
    printf '%s\n' '== dry-run：没有碰服务器 =='
    exit 0
fi

printf '== 上传 ==\n'
ssh -o BatchMode=yes "$HOST" "mkdir -p ${WEB_ROOT}/dl/${VERSION} && chmod 755 ${WEB_ROOT}/dl ${WEB_ROOT}/dl/${VERSION}"
scp -q -o BatchMode=yes "$JAR" "${HOST}:${WEB_ROOT}/dl/${VERSION}/"
printf '%s\n' "$NEW_MANIFEST" > build/version.json
scp -q -o BatchMode=yes build/version.json "${HOST}:${WEB_ROOT}/version.json"

printf '== 公网复核 ==\n'
SERVER_SHA="$(ssh -o BatchMode=yes "$HOST" "sha256sum ${WEB_ROOT}/dl/${VERSION}/${FILE_NAME} | awk '{print \$1}'")"
[[ "$SERVER_SHA" == "$SHA" ]] || { printf '错误: 服务器上的 jar 摘要不一致\n' >&2; exit 1; }

LIVE="$(curl -sS --max-time 20 "${SITE}/zombies/version.json")"
DL_SHA="$(curl -sS --max-time 120 "${SITE}${URL}" | shasum -a 256 | awk '{print $1}')"
[[ "$DL_SHA" == "$SHA" ]] || { printf '错误: 公网下到的 jar 摘要不一致\n' >&2; exit 1; }

printf '%s\n' "$LIVE" | python3 -c '
import json, sys
m = json.load(sys.stdin)["files"]["fabric-26.2"]
print("清单已生效: version=%s size=%s" % (m["version"], m["size"]))
'
printf '%s\n' '== 发布完成 =='
printf '客户端(26.2)下次启动 15 秒后会自己发现并换装；不需要手动放进 mods/。\n'
printf '撤回窗口 %s 分钟：窗口内把清单改回去就没人装上。\n' "$DELAY_MINUTES"
