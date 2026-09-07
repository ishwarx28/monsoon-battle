#!/bin/sh
# Source-only, checksum-verified Gradle launcher. No wrapper JAR is required.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
URL=$(sed -n 's/^distributionUrl=//p' "$PROPS" | tr -d '\\')
SHA=$(sed -n 's/^distributionSha256Sum=//p' "$PROPS")
VERSION=$(printf '%s' "$URL" | sed -n 's|.*/gradle-\([0-9.]*\)-bin.zip$|\1|p')
[ -n "$VERSION" ] && [ "${#SHA}" -eq 64 ] || { echo 'Invalid Gradle version/checksum metadata.' >&2; exit 1; }
HOME_DIR=${GRADLE_USER_HOME:-"$HOME/.gradle"}
# Reuse an already installed wrapper distribution, just as the standard wrapper does.
for installed in "$HOME_DIR"/wrapper/dists/gradle-"$VERSION"-*/*/gradle-"$VERSION"/bin/gradle; do
    if [ -x "$installed" ]; then exec "$installed" "$@"; fi
done
CACHE="$HOME_DIR/monsoon-bootstrap/$VERSION"
EXE="$CACHE/gradle-$VERSION/bin/gradle"
if [ ! -x "$EXE" ]; then
    mkdir -p "$(dirname "$CACHE")"
    LOCK="$CACHE.lock"; attempts=0
    until mkdir "$LOCK" 2>/dev/null; do
        if [ -x "$EXE" ]; then exec "$EXE" "$@"; fi
        attempts=$((attempts + 1))
        [ "$attempts" -lt 300 ] || { echo "Bootstrap busy; retry later: $LOCK" >&2; exit 1; }
        sleep 1
    done
    TMP=$(mktemp -d "$(dirname "$CACHE")/.download.XXXXXX")
    trap 'rm -rf "$TMP"; rmdir "$LOCK" 2>/dev/null || true' EXIT HUP INT TERM
    curl --fail --location --retry 2 --max-time 600 "$URL" -o "$TMP/gradle.zip"
    if command -v sha256sum >/dev/null 2>&1; then
        ACTUAL=$(sha256sum "$TMP/gradle.zip" | cut -d ' ' -f 1)
    else ACTUAL=$(shasum -a 256 "$TMP/gradle.zip" | cut -d ' ' -f 1); fi
    [ "$ACTUAL" = "$SHA" ] || { echo 'Gradle checksum mismatch; refusing to execute.' >&2; exit 1; }
    unzip -q "$TMP/gradle.zip" -d "$TMP/unpacked"
    mkdir -p "$CACHE"
    mv "$TMP/unpacked/gradle-$VERSION" "$CACHE/"
    rm -rf "$TMP"; rmdir "$LOCK"; trap - EXIT HUP INT TERM
fi
exec "$EXE" "$@"
