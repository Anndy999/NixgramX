#!/usr/bin/env bash
# Apply user-requested code patches onto the current tree.
# Run from repo root after merging fix/user-requested-bugs into upstream-sync/12.10.1.
set -euo pipefail
root="$(git rev-parse --show-toplevel)"
patch="$root/patches/0001-fix-chatactivity-doubletap-reaction-brace.patch"
if [[ ! -f "$patch" ]]; then
  echo "missing $patch" >&2
  exit 1
fi
if git apply --check --whitespace=nowarn "$patch"; then
  git apply --whitespace=nowarn "$patch"
  echo "applied $patch"
else
  echo "patch already applied or context changed: $patch" >&2
  git apply --check --whitespace=nowarn "$patch" || true
fi
