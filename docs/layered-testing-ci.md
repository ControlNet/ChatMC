# Layered Testing CI Lanes

This repository uses three explicit CI lanes for layered testing. Lane policy is machine-enforced by:

- `scripts/ci_collect_reports.py`
- `ci/layered-testing-policy.json`
- `.github/workflows/layered-testing.yml`

## Lane Commands and Artifacts

| Lane | Trigger | Exact commands | Collected artifacts |
|---|---|---|---|
| **PR** | `pull_request` (or `workflow_dispatch lane=pr`) | `./gradlew --no-daemon :base:core:test` (matrix)<br>`./gradlew --no-daemon :base:common-1.20.1:test` (matrix)<br>`./gradlew --no-daemon :ext-ae:common-1.20.1:test` (matrix) | `**/build/test-results/test/*.xml` + `ci-reports/pr/*-summary.json` |
| **Main** | `push` to `main` (or `workflow_dispatch lane=main`) | `./gradlew --no-daemon :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test`<br>`timeout 25m ./gradlew --no-daemon :base:forge-1.20.1:runGameTestServer --stacktrace` | JUnit XML + Forge log `ci-reports/main/forge-gametest.log` + Forge XML reports under `base/forge-1.20.1/build/reports/**` (when available) + `ci-reports/main/summary.json` |
| **Nightly** | `schedule` (or `workflow_dispatch lane=nightly`) | `./gradlew --no-daemon :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test`<br>`timeout 25m ./gradlew --no-daemon :base:fabric-1.20.1:runGametest --stacktrace`<br>`timeout 25m ./gradlew --no-daemon :ext-ae:fabric-1.20.1:runGametest --stacktrace -Dfabric-api.gametest.filter=ae_smoke` | JUnit XML + Fabric GameTest XML:<br>`base/fabric-1.20.1/build/reports/gametest/runGametest.xml`<br>`ext-ae/fabric-1.20.1/build/reports/gametest/runGametest.xml`<br> + `ci-reports/nightly/summary.json` |

## Forge blocker handling (explicit, not hidden)

Known workspace blocker signatures are codified in `ci/layered-testing-policy.json` (`main` lane):

- `InvalidModFileException: Illegal version number specified version (main)`
- `Failed to find system mod: minecraft`

Behavior:

1. Main lane still **runs** Forge GameTest command and captures the raw log.
2. The parser returns **exit code `3`** with status `blocked` when only the known blocker is present.
3. Workflow emits an explicit warning: `Main lane is BLOCKED by known Forge runtime issue`.
4. This is visible in artifacts and JSON summary instead of being silently treated as a pass.

## Retry / quarantine / flaky policy

The policy file is authoritative (`ci/layered-testing-policy.json`).

### Numeric thresholds

- **PR lane**
  - retries: JUnit `0`, GameTest `0`
  - `max_non_quarantined_failures = 0`
  - `max_quarantined_failures = 0`
  - `max_flaky_recovered = 0`
- **Main lane**
  - retries: JUnit `0`, GameTest `0`
  - `max_non_quarantined_failures = 0`
  - `max_quarantined_failures = 0`
  - `max_flaky_recovered = 0`
  - known Forge blocker allowed only as `blocked` (exit `3`) and must match policy signatures
- **Nightly lane**
  - retries: JUnit `0`, GameTest `1`
  - `max_non_quarantined_failures = 0`
  - `max_quarantined_failures = 0`
  - `max_flaky_recovered = 0`

### Quarantine rules

- Quarantine is explicit and machine-applied through `quarantined_tests` entries in `ci/layered-testing-policy.json`.
- Entry format:
  - exact key: `fully.qualified.Class::testName`
  - regex key: `re:<regex>`
- Quarantine never bypasses parser enforcement: if failures exceed configured thresholds, parser exits non-zero.

### Promotion criteria (quarantine -> normal)

A quarantined test can be promoted only when all criteria hold:

1. **14 consecutive nightly passes** for that test (no fail/error/flaky transitions).
2. **3 consecutive main-lane passes** for that test with no blocker-related masking.
3. test removed from `quarantined_tests` in `ci/layered-testing-policy.json` and merged via PR.

No manual-only acceptance is allowed: promotion must be reflected in policy/config changes and parser output.

## Parser exit codes

- `0`: pass (within policy)
- `1`: fail (policy exceeded or required artifacts missing)
- `3`: blocked (known Forge runtime blocker detected and explicitly allowed for that lane)

All lanes emit machine-consumable JSON summaries at `ci-reports/<lane>/summary.json` (PR matrix uses `ci-reports/pr/*-summary.json`).
