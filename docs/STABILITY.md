# Stability

## Priority

Second project priority (after upstream tracking): fix crashes, compatibility, and UX issues inherited from NagramX 12.9.2.1260.

## Day-1 baseline

- No new large features.
- Policy removals reduce Ghost Mode / typing-hide / online-enhance surface area.
- `_base` disables Save Deleted family for ToS-friendlier builds.

## Process

1. Reproduce on full and `_base` when relevant.
2. Prefer minimal patches; cherry-pick bugfixes from forks only with attribution (`BUG_FIX_SOURCES.md`).
3. Gate releases with `TEST_MATRIX.md`.

## Current blockers for stability validation

- Device CI / real FCM needs secrets + workflow push scope.
- Native full build not executed in this Day-1 environment.
