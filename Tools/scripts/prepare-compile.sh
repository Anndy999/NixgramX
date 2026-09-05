#!/usr/bin/env bash
# Prepare a compile tree: merge bugfix lane, apply ChatActivity patch, check secrets.
# Does not run Gradle unless --build is passed AND an Android SDK is configured.
set -euo pipefail
root="$(git rev-parse --show-toplevel)"
cd "$root"

build=0
if [[ "${1:-}" == "--build" ]]; then
  build=1
fi

echo "== merge lane =="
echo "current: $(git rev-parse --abbrev-ref HEAD) @ $(git rev-parse --short HEAD)"
echo "expected compile base: upstream-sync/12.10.1"
echo "expected merge:        fix/user-requested-bugs"
echo
echo "  git checkout upstream-sync/12.10.1"
echo "  git merge --no-ff fix/user-requested-bugs"
echo "  Tools/scripts/apply-user-bugfixes.sh"
echo

if [[ -x "$root/Tools/scripts/apply-user-bugfixes.sh" ]]; then
  "$root/Tools/scripts/apply-user-bugfixes.sh" || true
fi

ks="$root/TMessagesProj/release.keystore"
if [[ ! -f "$ks" ]]; then
  echo "MISSING keystore: $ks" >&2
  echo "run Tools/scripts/generate-nixgramx-keystore.sh" >&2
  exit 1
fi
echo "keystore: $ks ($(wc -c < "$ks") bytes)"

lp="$root/local.properties"
if [[ ! -f "$lp" ]]; then
  echo "MISSING local.properties — copy from local.properties.example and fill:" >&2
  echo "  TELEGRAM_APP_ID / TELEGRAM_APP_HASH  from https://my.telegram.org/apps" >&2
  echo "  KEYSTORE_PASS / ALIAS_NAME=nixgramx / ALIAS_PASS" >&2
  exit 1
fi

need=(TELEGRAM_APP_ID TELEGRAM_APP_HASH KEYSTORE_PASS ALIAS_NAME ALIAS_PASS)
missing=0
for k in "${need[@]}"; do
  if ! grep -qE "^${k}=.+" "$lp"; then
    echo "MISSING $k in local.properties" >&2
    missing=1
  elif grep -qE "^${k}=YOUR_" "$lp"; then
    echo "PLACEHOLDER $k still set to YOUR_*" >&2
    missing=1
  fi
done
if grep -qE '^TELEGRAM_APP_ID=6$' "$lp" || grep -qE '^TELEGRAM_APP_ID=4$' "$lp"; then
  echo "WARNING: TELEGRAM_APP_ID looks like the official Telegram id — get your own at my.telegram.org/apps" >&2
fi
if [[ $missing -ne 0 ]]; then
  exit 1
fi

echo "local.properties: ok (secrets present, not printed)"
echo
echo "Gradle:"
echo "  ./gradlew TMessagesProj:assembleRelease"
echo "  NIXGRAMX_BASE=true ./gradlew TMessagesProj:assembleRelease   # _base flavor"
echo

if [[ $build -eq 1 ]]; then
  if [[ ! -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" && ! grep -q '^sdk.dir=' "$lp" ]]; then
    echo "no Android SDK configured; skip build" >&2
    exit 2
  fi
  exec ./gradlew TMessagesProj:assembleRelease
fi
