# F4 Scope Fidelity Check, deep

Date: 2026-02-21

## Guardrails checked

- Test pyramid and GameTest adoption focused on `base` + `ext-ae`
- No `ext-matrix` expansion
- No unrelated product/runtime feature implementation

## Evidence inputs

- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git ls-files --others --exclude-standard`
- Targeted reads for changed workflow, CI policy, GameTest entrypoints/bootstrap files, test helper additions, and parity artifacts
- `grep -R` equivalent via tool search for `ext-matrix|chatmcmatrix` to confirm matrix references are pre-existing and not part of changed paths

## Major changed areas and classification

| Area | Representative paths | Classification | Rationale |
|---|---|---|---|
| Common test migrations (regression-contract to behavior/runtime-oriented tests) | `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/**` | in-scope | Test-only changes and helper abstractions (`DeterministicBarrier`, `TimeoutUtility`, behavior tests) support deterministic scenario validation. |
| Forge GameTest wiring, base | `base/forge-1.20.1/build.gradle`, `base/forge-1.20.1/src/main/java/.../gametest/**` | in-scope | Directly adds/maintains GameTest registration and run configuration under `chatmc` namespace. |
| Fabric GameTest wiring, base | `base/fabric-1.20.1/build.gradle`, `base/fabric-1.20.1/src/main/resources/fabric.mod.json`, `.../fabric/gametest/ChatMCFabricGameTestEntrypoint.java` | in-scope | Loader-level test harness configuration and wrapper entrypoint for parity/testing lanes. |
| Forge/Fabric GameTest wiring, ext-ae | `ext-ae/forge-1.20.1/build.gradle`, `ext-ae/forge-1.20.1/src/main/java/.../gametest/**`, `ext-ae/fabric-1.20.1/**` | in-scope | Explicitly constrained to `ext-ae` test adoption, aligns with guardrail scope. |
| CI lane policy, collection scripts, parity reports, docs | `.github/workflows/layered-testing.yml`, `ci/layered-testing-policy.json`, `scripts/ci_collect_reports.py`, `scripts/gametest_parity_report.py`, `docs/layered-testing-ci.md`, `ci-reports/parity/**` | in-scope | Testing infrastructure, policy enforcement, and evidence generation only. |
| Misc workspace notes outside `.sisyphus/notepads` | `learnings/issues/decisions.md` | uncertain | Not runtime/product code and not matrix expansion, but this path is not part of required F4 deliverables and appears as extra collateral. |

## Out-of-scope check

- `ext-matrix`: no changed paths under `ext-matrix/**` detected
- Runtime/business logic feature code: no changed non-test feature modules detected in changed path set

## Unaccounted changes

1. `learnings/issues/decisions.md`, collateral documentation path outside required F4 artifact/notepad targets, scope relevance is indirect

Scope ISSUES | Unaccounted changes: 1 | VERDICT: Mostly within testing infra and scenario scope, with one extra collateral documentation path to review
