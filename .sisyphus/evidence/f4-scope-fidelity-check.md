# F4 Scope Fidelity Check, deep

Date: 2026-03-30

## Evidence inputs

- `git diff --stat`
- `git diff --name-only`
- `git diff -- .sisyphus/plans/mineagent-full-rename.md`
- Targeted reads for `.github/workflows/release.yml`, `base/common-1.20.1/src/main/resources/pack.mcmeta`, and `ext-ae/fabric-1.20.1/build.gradle`

## Current-diff scope review

The current working diff remains within the approved clean-cut MineAgent rename plus final-wave verification/evidence scope.

Representative checks:

- `.sisyphus/plans/mineagent-full-rename.md` — `git diff -- .sisyphus/plans/mineagent-full-rename.md` is empty, so the active rename plan is not part of the current working diff.
- `.github/workflows/release.yml` — jar expectations were renamed from `chatmc*` to `mineagent*`, which is directly in scope for release/output identity.
- `base/common-1.20.1/src/main/resources/pack.mcmeta` — resource-pack branding changed from `ChatMC resources` to `MineAgent resources`, which is directly in scope.
- `ext-ae/fabric-1.20.1/build.gradle` — the added remap-cache cleanup validates current MineAgent metadata (`mineagent` id and `space.controlnet.mineagent.fabric` entrypoint), which is in scope for the final verification path and no longer preserves legacy identifiers in tracked logic.

## Out-of-scope / unexplained collateral

None identified in the current diff.

The remaining changed-file set is consistent with:

- repo-wide source/resource/package renames from `chatmc*` to `mineagent*`
- build/release/workflow identity updates
- tracked documentation and `.sisyphus` artifact cleanup required by the zero-residue rename objective
- final-wave verification and evidence refreshes

No exact current offending out-of-scope paths remain after rechecking the live diff.

VERDICT: APPROVE
