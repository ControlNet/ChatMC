# MineAgent Full Repository Rename

## TL;DR
> **Summary**: Perform a clean-cut repository-wide rename from ChatMC-era identity to MineAgent across project branding, Java packages, mod IDs, resource namespaces, runtime identity strings, build/release metadata, documentation, and tracked planning artifacts, with no compatibility shims and no retained old names.
> **Deliverables**:
> - Canonical rename matrix for base, AE, and Matrix modules
> - Updated Java package roots under `space.controlnet.mineagent...`
> - Updated mod IDs / resource namespaces / artifact names using `mineagent*`
> - Updated CI, packaging, release metadata, docs, localization, and tracked repo text
> - Full verification evidence from JUnit, Forge/Fabric GameTests, packaging, and residue scan
> **Effort**: XL
> **Parallel**: YES - 3 waves
> **Critical Path**: Task 1 → Task 2 → Tasks 5/6/7/8 → Task 10 → Task 11 → Task 12

## Context
### Original Request
- Rename the project to `MineAgent`.
- Replace package name segments containing `chatmc` with `mineagent`.
- Do not preserve compatibility; after the rename there must be no old names left.

### Interview Summary
- User confirmed the rename must also cover Gradle and release scripts, Fabric/Forge metadata, resource namespaces, docs/localization, tests, and CI.
- User confirmed mod IDs and resource namespaces must move to `mineagent*` forms.
- User confirmed jar basenames, Maven coordinates, and release artifact prefixes must also be renamed.
- User explicitly rejected compatibility shims; legacy identifiers must be removed rather than aliased.
- User chose full verification depth: JUnit + Forge/Fabric GameTests + `./scripts/build-dist.sh`.
- User explicitly authorized updating `AGENTS.md` so no old names remain there.

### Metis Review (gaps addressed)
- Added a mandatory canonical rename matrix so base/AE/Matrix identities cannot drift.
- Added tracked-file residue scanning as a hard acceptance gate to prevent partial old-name residue.
- Added explicit runtime-identity coverage for command roots, config paths, saved-data keys, prompt/system-property prefixes, and GameTest namespaces.
- Added tracked historical docs/planning artifacts to scope because the user requires zero retained old names.
- Added guardrail to exclude project-level rule changes unless explicitly requested; the user has now explicitly authorized identity-only updates inside `AGENTS.md`.

## Work Objectives
### Core Objective
Rename every tracked ChatMC-era identity in the repository to the MineAgent naming family in one clean-cut change set, leaving zero retained legacy names in tracked files and zero release/test outputs using the old identifiers.

### Deliverables
- A completed rename matrix defining target names for base, AE, and Matrix identities.
- Updated source and test packages under `space.controlnet.mineagent...`.
- Updated Fabric/Forge metadata and resource directories using `mineagent`, `mineagentae`, and `mineagentmatrix`.
- Updated build, packaging, CI, workflow, and release naming.
- Updated `AGENTS.md` identity references to MineAgent-era naming without changing unrelated repo rules.
- Updated README, REPO, localization, pack metadata, and tracked `.sisyphus` markdown/log artifacts that still mention old names.
- Verification evidence under `.sisyphus/evidence/` for all required checks.

### Definition of Done (verifiable conditions with commands)
- `git grep -nE 'chatmc|chatmcae|chatmcmatrix|ChatMC|ChatMCAe|ChatMCMatrix|ChatAE|chatae'` returns no matches in tracked files that remain in scope after the rename.
- `./gradlew --no-daemon --configure-on-demand :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test` succeeds.
- `timeout 25m ./gradlew --no-daemon --configure-on-demand :base:forge-1.20.1:runGameTestServer --stacktrace` and `timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:forge-1.20.1:runGameTestServer --stacktrace` are first proven free of the documented blocked-runtime condition.
- `timeout 25m ./gradlew --no-daemon --configure-on-demand :base:forge-1.20.1:runGameTestServer --stacktrace` succeeds.
- `timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:forge-1.20.1:runGameTestServer --stacktrace` succeeds.
- `timeout 25m ./gradlew --no-daemon --configure-on-demand :base:fabric-1.20.1:runGametest --stacktrace` succeeds.
- `timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:fabric-1.20.1:runGametest --stacktrace -Dfabric-api.gametest.filter=ae_smoke` succeeds.
- `./scripts/build-dist.sh` succeeds and `dist/` contains only MineAgent-era jar prefixes.

### Must Have
- One canonical naming scheme used consistently across base, AE, and Matrix.
- Clean-cut rename of brand, code, metadata, runtime identity, and release surfaces.
- Explicit residue checks for both lowercase and PascalCase legacy names.
- Updated docs and tracked planning artifacts so the repo itself no longer contains old names.
- No compatibility aliases, redirects, duplicated IDs, or fallback behavior.

### Must NOT Have (guardrails, AI slop patterns, scope boundaries)
- Must NOT keep `chatmc`, `chatmcae`, `chatmcmatrix`, `ChatMC`, `ChatMCAe`, `ChatMCMatrix`, `ChatAE`, or `chatae` in tracked files after completion.
- Must NOT change unrelated project-level rules in `AGENTS.md`; only rename identity/path references needed to remove legacy names.
- Must NOT split the rename into “brand-only now, runtime later” phases inside implementation; this is a single clean-cut rename.
- Must NOT add compatibility shims, aliases, parallel mod IDs, duplicate resources, or migration code.
- Must NOT treat generated or ignored directories as proof of success; only tracked-file residue checks count for the acceptance gate.

## Verification Strategy
> ZERO HUMAN INTERVENTION — all verification is agent-executed.
- Test decision: tests-after + existing Gradle/JUnit/GameTest framework
- QA policy: Every task includes agent-executed implementation validation and an old-name residue/failure-path check relevant to that task.
- Evidence: `.sisyphus/evidence/task-{N}-{slug}.{ext}`

## Execution Strategy
### Parallel Execution Waves
> Target: 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks for max parallelism.

Wave 1: naming matrix + build/release identity + metadata target mapping + CI/release gates
Wave 2: base/AE/Matrix code/runtime/resource/docs execution tasks in parallel
Wave 3: residue hardening + full verification + artifact audit

### Dependency Matrix (full, all tasks)
- Task 1 blocks Tasks 2-9 and defines the canonical rename table.
- Task 2 blocks Tasks 5-9 where code/resources/docs must conform to the final artifact/group/ID targets.
- Task 3 blocks Tasks 5-8 by locking final mod IDs, namespaces, display names, and loader metadata fields.
- Task 4 blocks Task 11 by aligning workflow/report expectations with renamed outputs.
- Tasks 5, 6, 7, and 8 run in parallel after Tasks 1-3 complete.
- Task 9 runs after Tasks 1-4 and in parallel with Tasks 5-8.
- Task 10 runs after Tasks 5-9 complete.
- Task 11 runs after Task 10 and must clear the documented Forge blocker before Task 12.
- Task 12 runs after Task 11 and before the final verification wave.

### Agent Dispatch Summary (wave → task count → categories)
- Wave 1 → 4 tasks → deep / unspecified-high / writing
- Wave 2 → 5 tasks → deep / unspecified-high / writing
- Wave 3 → 3 tasks → deep / unspecified-high

## TODOs
> Implementation + Test = ONE task. Never separate.
> EVERY task MUST have: Agent Profile + Parallelization + QA Scenarios.

- [ ] 1. Freeze the canonical rename matrix and residue baseline

  **What to do**: Create a single authoritative rename table for base, AE, and Matrix covering display names, Java package roots, class/type prefixes, mod IDs, resource namespaces, Maven group, jar basenames, command root, config directory, saved-data key, prompt/system-property prefixes, release artifact names, and any tracked repo aliases that currently use ChatMC-era names. Record the exact old→new mapping in the executor’s working notes or implementation context, then run a tracked-file baseline scan so the executor knows every residue family that must go to zero.
  **Must NOT do**: Must NOT begin ad-hoc renames before the matrix is fixed. Must NOT allow different naming styles for base vs AE vs Matrix. Must NOT exclude tracked `.sisyphus` markdown/log artifacts from the residue baseline if they contain old names.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: This task defines the non-negotiable naming contract for every downstream change.
  - Skills: `[]` — No extra skill is required; this is repo synthesis, not domain-specialized implementation.
  - Omitted: `["git-master"]` — No git operation is required at this stage.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: [2, 3, 4, 5, 6, 7, 8, 9] | Blocked By: []

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `settings.gradle` — Root project identity currently uses ChatMC-era naming.
  - Pattern: `gradle.properties` — Maven group and archive base name define global artifact identity.
  - Pattern: `build.gradle` — Root naming logic fans out to base/AE/Matrix module groups and basenames.
  - Pattern: `README.md` — Current public naming for base/AE/Matrix and jar outputs.
  - Pattern: `REPO.md` — Canonical repo identity and build/release documentation.
  - Pattern: `base/forge-1.20.1/src/main/resources/META-INF/mods.toml` — Base mod display name and modid surface.
  - Pattern: `ext-ae/forge-1.20.1/src/main/resources/META-INF/mods.toml` — AE extension identity surface.
  - Pattern: `ext-matrix/forge-1.20.1/src/main/resources/META-INF/mods.toml` — Matrix extension identity surface.
  - Pattern: `.sisyphus/plans/chatmc-test-pyramid-gametest-adoption.md` — Tracked planning artifact proving historical residue is in scope.

  **Acceptance Criteria** (agent-executable only):
  - [ ] The executor produces a complete old→new rename table covering every identity category listed above.
  - [ ] `git grep -nE 'chatmc|chatmcae|chatmcmatrix|ChatMC|ChatMCAe|ChatMCMatrix|ChatAE|chatae'` is run and its baseline output is captured for later zero-residue comparison.
  - [ ] The chosen target family is internally consistent: base=`mineagent`, AE=`mineagentae`, Matrix=`mineagentmatrix`, package root=`space.controlnet.mineagent...`.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Rename matrix is complete and internally consistent
    Tool: Bash
    Steps: Compile the rename matrix; then run `git grep -nE 'chatmc|chatmcae|chatmcmatrix|ChatMC|ChatMCAe|ChatMCMatrix|ChatAE|chatae'` and annotate each hit family against a row in the matrix.
    Expected: Every residue family found by grep has a corresponding old→new mapping with no ambiguous targets.
    Evidence: .sisyphus/evidence/task-1-rename-matrix.md

  Scenario: Matrix misses a legacy identifier family
    Tool: Bash
    Steps: Re-run the residue grep after drafting the matrix and compare the hit families to the matrix categories.
    Expected: If any legacy family lacks a mapped target, the task is failed and the matrix is extended before any downstream rename begins.
    Evidence: .sisyphus/evidence/task-1-rename-matrix-error.md
  ```

  **Commit**: NO | Message: `refactor(rename): freeze mineagent naming matrix` | Files: []

- [ ] 2. Rename build, package, and release identity surfaces

  **What to do**: Apply the canonical rename matrix to `settings.gradle`, `gradle.properties`, root `build.gradle`, module build scripts, and `scripts/build-dist.sh` so project name, Maven group, archive basenames, jar naming, packaging copy logic, and release artifact expectations all emit MineAgent-era names only. Ensure root and module build files agree on the new base/AE/Matrix naming family instead of partially inheriting old values.
  **Must NOT do**: Must NOT leave root properties renamed while module build logic still hardcodes old AE/Matrix names. Must NOT keep old jar prefixes in `dist/` copy logic. Must NOT change unrelated build behavior outside identity naming.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: Multiple Gradle and packaging surfaces must be updated consistently without broad architecture redesign.
  - Skills: `[]` — Existing repo patterns are sufficient.
  - Omitted: `["git-master"]` — Commiting is not part of this task.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: [5, 6, 7, 8, 9, 11] | Blocked By: [1]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `settings.gradle` — Root project name and module inclusion surface.
  - Pattern: `gradle.properties` — `maven_group`, `archives_base_name`, versioned release identity.
  - Pattern: `build.gradle` — Root module group/base-name routing and final jar filename logic.
  - Pattern: `base/common-1.20.1/build.gradle` — Base common namespace/build wiring.
  - Pattern: `base/fabric-1.20.1/build.gradle` — Base Fabric namespace/GameTest wiring.
  - Pattern: `base/forge-1.20.1/build.gradle` — Base Forge mod namespace and GameTest namespace surface.
  - Pattern: `ext-ae/common-1.20.1/build.gradle` — AE extension namespace/build wiring.
  - Pattern: `ext-ae/fabric-1.20.1/build.gradle` — AE Fabric namespace surface.
  - Pattern: `ext-ae/forge-1.20.1/build.gradle` — AE Forge namespace surface.
  - Pattern: `ext-matrix/common-1.20.1/build.gradle` — Matrix extension namespace/build wiring.
  - Pattern: `ext-matrix/fabric-1.20.1/build.gradle` — Matrix Fabric namespace surface.
  - Pattern: `ext-matrix/forge-1.20.1/build.gradle` — Matrix Forge namespace surface.
  - Pattern: `scripts/build-dist.sh` — Dist artifact copy/rename logic.

  **Acceptance Criteria** (agent-executable only):
  - [ ] Root and module Gradle files emit only `mineagent*` artifact/group/basename values.
  - [ ] `./scripts/build-dist.sh` is updated to collect and output only MineAgent-era jar names.
  - [ ] A tracked-file grep for `chatmc|chatmcae|chatmcmatrix|ChatMC|ChatMCAe|ChatMCMatrix` over build and script files returns zero matches.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Packaging identity is fully renamed
    Tool: Bash
    Steps: Run `git grep -nE 'chatmc|chatmcae|chatmcmatrix|ChatMC|ChatMCAe|ChatMCMatrix' -- settings.gradle gradle.properties build.gradle base ext-ae ext-matrix scripts/build-dist.sh`.
    Expected: No build/script identity files retain old names.
    Evidence: .sisyphus/evidence/task-2-build-identity.log

  Scenario: AE or Matrix basename remains hardcoded to an old prefix
    Tool: Bash
    Steps: Run `./scripts/build-dist.sh` after the rename and inspect `dist/` jar prefixes.
    Expected: Task fails if any output jar still begins with `chatmc`, `chatmcae`, or `chatmcmatrix`.
    Evidence: .sisyphus/evidence/task-2-build-identity-error.log
  ```

  **Commit**: NO | Message: `build(rename): move build and dist identity to mineagent` | Files: [settings.gradle, gradle.properties, build.gradle, base/**/build.gradle, ext-ae/**/build.gradle, ext-matrix/**/build.gradle, scripts/build-dist.sh]

- [ ] 3. Rename loader metadata, display names, and resource namespace targets

  **What to do**: Update Fabric and Forge metadata for base, AE, and Matrix so display names, mod IDs, dependency references, namespace declarations, and pack metadata all align with the canonical MineAgent naming family. Rename resource namespace directories and in-file namespace references together so metadata, assets, and data paths are consistent across loaders.
  **Must NOT do**: Must NOT rename only display names while keeping old mod IDs. Must NOT split folder renames from in-file `namespace:path` updates. Must NOT leave dependency edges pointing at old mod IDs.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: Loader metadata, resource directories, and namespace strings must agree exactly across multiple modules and runtimes.
  - Skills: `[]` — No extra skill is needed.
  - Omitted: `["git-master"]` — No git operation is required.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: [5, 6, 7, 8, 11] | Blocked By: [1]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `base/fabric-1.20.1/src/main/resources/fabric.mod.json` — Base Fabric modid/display name/entrypoint metadata.
  - Pattern: `base/forge-1.20.1/src/main/resources/META-INF/mods.toml` — Base Forge modid/display name/dependency metadata.
  - Pattern: `ext-ae/fabric-1.20.1/src/main/resources/fabric.mod.json` — AE Fabric modid/dependency metadata.
  - Pattern: `ext-ae/forge-1.20.1/src/main/resources/META-INF/mods.toml` — AE Forge modid/dependency metadata.
  - Pattern: `ext-matrix/fabric-1.20.1/src/main/resources/fabric.mod.json` — Matrix Fabric modid/dependency metadata.
  - Pattern: `ext-matrix/forge-1.20.1/src/main/resources/META-INF/mods.toml` — Matrix Forge modid/dependency metadata.
  - Pattern: `base/common-1.20.1/src/main/resources/assets/chatmc/lang/en_us.json` — Base namespace/lang key surface.
  - Pattern: `base/common-1.20.1/src/main/resources/assets/chatmc/lang/zh_cn.json` — Base localized namespace/text surface.
  - Pattern: `ext-ae/common-1.20.1/src/main/resources/assets/chatmcae/lang/en_us.json` — AE namespace/lang surface.
  - Pattern: `ext-ae/common-1.20.1/src/main/resources/assets/chatmcae/lang/zh_cn.json` — AE localized namespace/text surface.
  - Pattern: `base/common-1.20.1/src/main/resources/pack.mcmeta` — Base resource-pack identity text.
  - Pattern: `ext-ae/common-1.20.1/src/main/resources/pack.mcmeta` — AE resource-pack identity text.
  - Pattern: `ext-matrix/common-1.20.1/src/main/resources/pack.mcmeta` — Matrix resource-pack identity text.

  **Acceptance Criteria** (agent-executable only):
  - [ ] All Fabric/Forge metadata files reference only `mineagent*` identities and correct inter-mod dependencies.
  - [ ] Resource namespace folders and in-file namespace strings are aligned for base, AE, and Matrix.
  - [ ] A tracked-file grep across resource and metadata files returns zero ChatMC-era identifiers.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Loader metadata and resources agree on MineAgent namespaces
    Tool: Bash
    Steps: Run `git grep -nE 'chatmc|chatmcae|chatmcmatrix|ChatMC|ChatMCAe|ChatMCMatrix' -- base/common-1.20.1/src/main/resources base/fabric-1.20.1/src/main/resources base/forge-1.20.1/src/main/resources ext-ae/common-1.20.1/src/main/resources ext-ae/fabric-1.20.1/src/main/resources ext-ae/forge-1.20.1/src/main/resources ext-matrix/common-1.20.1/src/main/resources ext-matrix/fabric-1.20.1/src/main/resources ext-matrix/forge-1.20.1/src/main/resources`.
    Expected: No metadata or resource file retains old names.
    Evidence: .sisyphus/evidence/task-3-metadata-namespace.log

  Scenario: Dependency metadata still points to an old modid
    Tool: Bash
    Steps: Run the same grep and inspect Fabric/Forge metadata fields for dependency references after renaming mod IDs.
    Expected: Task fails if any dependency, namespace, or display-name field still uses a ChatMC-era identifier.
    Evidence: .sisyphus/evidence/task-3-metadata-namespace-error.log
  ```

  **Commit**: NO | Message: `refactor(rename): move loader metadata and namespaces to mineagent` | Files: [base/**/src/main/resources/**, ext-ae/**/src/main/resources/**, ext-matrix/**/src/main/resources/**]

- [ ] 4. Update CI, workflow, and release gating to the MineAgent outputs

  **What to do**: Rename workflow, policy, and release expectations so CI lanes, packaged artifact collection, and release automation look for MineAgent-era names only. Ensure workflow steps, report paths, artifact names, and release upload/checksum logic agree with the renamed build outputs and do not preserve legacy prefixes anywhere in tracked CI configuration.
  **Must NOT do**: Must NOT leave CI green only because it still searches for old artifact prefixes. Must NOT rename outputs without aligning workflow/report consumers. Must NOT change unrelated branch policy behavior.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: This is configuration-heavy work with multiple coupled workflow files and policies.
  - Skills: `[]` — Existing repo workflows provide the needed patterns.
  - Omitted: `["git-master"]` — No git operation is needed.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: [9, 11] | Blocked By: [1]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `.github/workflows/layered-testing.yml` — CI lane commands, report collection, and task naming.
  - Pattern: `.github/workflows/release.yml` — Release artifact collection and publication naming.
  - Pattern: `ci/layered-testing-policy.json` — Machine-enforced report and blocker policy surface.
  - Pattern: `gradle.properties` — Source of version and base archive identity used by workflows.
  - Pattern: `build.gradle` — Source of final jar naming used by workflow artifact matching.
  - Pattern: `scripts/build-dist.sh` — Packaging output consumed by release workflow.

  **Acceptance Criteria** (agent-executable only):
  - [ ] Workflow and policy files reference only MineAgent-era artifact and namespace expectations.
  - [ ] Release automation no longer looks for `chatmc*` jar prefixes or labels.
  - [ ] A tracked-file grep across `.github/workflows` and `ci/` returns zero ChatMC-era identifiers.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Workflow and policy files match renamed outputs
    Tool: Bash
    Steps: Run `git grep -nE 'chatmc|chatmcae|chatmcmatrix|ChatMC|ChatMCAe|ChatMCMatrix|ChatAE|chatae' -- .github/workflows ci`.
    Expected: No workflow or policy file retains old names.
    Evidence: .sisyphus/evidence/task-4-ci-release.log

  Scenario: Release workflow still expects a legacy artifact prefix
    Tool: Bash
    Steps: Run `./scripts/build-dist.sh`, then compare the generated `dist/` jar names against `.github/workflows/release.yml` artifact matchers.
    Expected: Task fails if any workflow matcher still points at a ChatMC-era prefix or misses a MineAgent-era output.
    Evidence: .sisyphus/evidence/task-4-ci-release-error.log
  ```

  **Commit**: NO | Message: `ci(rename): align workflows and release gates with mineagent` | Files: [.github/workflows/layered-testing.yml, .github/workflows/release.yml, ci/layered-testing-policy.json]

- [ ] 5. Rename base module source, tests, and runtime identity strings

  **What to do**: Rename the base-module Java and test package tree from `space.controlnet.chatmc...` to `space.controlnet.mineagent...`, and update all base-specific runtime identity strings to the MineAgent equivalents. This includes entrypoint classes, command roots, config path references, saved-data identifiers, prompt/system-property prefixes, GameTest namespaces, reflection strings, and tests/assertions that embed old package or product names.
  **Must NOT do**: Must NOT leave mixed old/new packages in the base module. Must NOT update package declarations without fixing imports, reflection strings, or test expectations. Must NOT add compatibility aliases such as parallel old command roots or config fallbacks.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: This task combines package refactors with stringly-typed runtime identity updates inside the base mod.
  - Skills: `[]` — Existing repo patterns are enough.
  - Omitted: `["git-master"]` — No commit action belongs here.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [10, 11] | Blocked By: [1, 2, 3]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMC.java` — Base module root class and naming anchor.
  - Pattern: `base/fabric-1.20.1/src/main/java/space/controlnet/chatmc/fabric/ChatMCFabric.java` — Base Fabric entrypoint package root.
  - Pattern: `base/forge-1.20.1/src/main/java/space/controlnet/chatmc/forge/ChatMCForge.java` — Base Forge entrypoint package root.
  - Pattern: `base/forge-1.20.1/src/main/java/space/controlnet/chatmc/forge/gametest/ChatMCGameTestBootstrap.java` — Forge GameTest bootstrap identifier surface.
  - Pattern: `base/fabric-1.20.1/src/main/java/space/controlnet/chatmc/fabric/gametest/ChatMCFabricGameTestEntrypoint.java` — Fabric GameTest entrypoint identifier surface.
  - Pattern: `base/core/src/test/java/space/controlnet/chatmc/core/session/ServerSessionManagerStateMachineRegressionTest.java` — Base-core package/test rename surface.
  - Pattern: `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/network/NetworkProposalLifecycleBehaviorTest.java` — Base-common package/test rename surface.
  - Test: `base/core/build.gradle` — Base core JUnit command source.
  - Test: `base/common-1.20.1/build.gradle` — Base common JUnit command source.

  **Acceptance Criteria** (agent-executable only):
  - [ ] No tracked file under `base/` contains ChatMC-era package names or product identifiers after the rename.
  - [ ] Base JUnit suites pass with renamed packages and string identifiers.
  - [ ] Base Forge/Fabric GameTest entrypoints and namespace strings are updated to MineAgent-era identities.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Base code and tests are fully renamed and still pass targeted checks
    Tool: Bash
    Steps: Run `git grep -nE 'chatmc|ChatMC|ChatAE|chatae' -- base` then run `./gradlew --no-daemon --configure-on-demand :base:core:test :base:common-1.20.1:test`.
    Expected: Grep returns no legacy names inside `base/`, and both JUnit tasks succeed.
    Evidence: .sisyphus/evidence/task-5-base-runtime.log

  Scenario: A stringly-typed base runtime identity is missed
    Tool: Bash
    Steps: Run the same grep over `base/` after package refactors are complete.
    Expected: Task fails if any command root, GameTest identifier, config key, saved-data key, or legacy package string still matches the old-name pattern.
    Evidence: .sisyphus/evidence/task-5-base-runtime-error.log
  ```

  **Commit**: NO | Message: `refactor(rename): move base module to mineagent identities` | Files: [base/**]

- [ ] 6. Rename AE extension source, tests, and runtime identity strings

  **What to do**: Rename the AE extension Java and test package tree from `space.controlnet.chatmc.ae...` to `space.controlnet.mineagent.ae...`, and update all AE-specific runtime identity strings, entrypoints, namespaces, and test assertions to the `mineagentae` family. Ensure the AE extension still references the renamed base identity consistently after the clean cutover.
  **Must NOT do**: Must NOT keep the AE extension on `chatmcae` while the base module moves to `mineagent`. Must NOT retain old dependency references to the base mod. Must NOT leave AE GameTest or entrypoint classes on legacy names.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: AE couples package renames to extension-specific namespaces, tests, and runtime wiring.
  - Skills: `[]` — No extra skill is needed.
  - Omitted: `["git-master"]` — No commit action is required.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [10, 11] | Blocked By: [1, 2, 3]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `ext-ae/common-1.20.1/src/main/java/space/controlnet/chatmc/ae/common/ChatMCAe.java` — AE module root class and naming anchor.
  - Pattern: `ext-ae/fabric-1.20.1/src/main/java/space/controlnet/chatmc/ae/fabric/ChatMCAeFabric.java` — AE Fabric entrypoint package root.
  - Pattern: `ext-ae/forge-1.20.1/src/main/java/space/controlnet/chatmc/ae/forge/ChatMCAeForge.java` — AE Forge entrypoint package root.
  - Pattern: `ext-ae/forge-1.20.1/src/main/java/space/controlnet/chatmc/ae/forge/gametest/ChatMCAeGameTestBootstrap.java` — AE Forge GameTest identifier surface.
  - Pattern: `ext-ae/fabric-1.20.1/src/main/java/space/controlnet/chatmc/ae/fabric/gametest/ChatMCAeFabricGameTestEntrypoint.java` — AE Fabric GameTest identifier surface.
  - Test: `ext-ae/common-1.20.1/src/test/java/space/controlnet/chatmc/ae/common/tools/AeThreadConfinementRegressionTest.java` — AE package/test rename surface.
  - Test: `ext-ae/common-1.20.1/build.gradle` — AE common JUnit wiring.
  - Test: `ext-ae/fabric-1.20.1/build.gradle` — AE Fabric GameTest wiring.
  - Test: `ext-ae/forge-1.20.1/build.gradle` — AE Forge GameTest wiring.

  **Acceptance Criteria** (agent-executable only):
  - [ ] No tracked file under `ext-ae/` contains ChatMC-era package names or AE product identifiers after the rename.
  - [ ] AE common JUnit tests pass with renamed packages and extension identities.
  - [ ] AE GameTest entrypoints and metadata align with the `mineagentae` family.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: AE code and tests are fully renamed and still pass targeted checks
    Tool: Bash
    Steps: Run `git grep -nE 'chatmc|chatmcae|ChatMC|ChatMCAe|ChatAE|chatae' -- ext-ae` then run `./gradlew --no-daemon --configure-on-demand :ext-ae:common-1.20.1:test`.
    Expected: Grep returns no legacy names inside `ext-ae/`, and the AE common JUnit task succeeds.
    Evidence: .sisyphus/evidence/task-6-ae-runtime.log

  Scenario: AE still references a legacy base or extension identity
    Tool: Bash
    Steps: Re-run the same grep after package and namespace updates.
    Expected: Task fails if any AE dependency string, GameTest identifier, or package/import path still references `chatmc` or `chatmcae`.
    Evidence: .sisyphus/evidence/task-6-ae-runtime-error.log
  ```

  **Commit**: NO | Message: `refactor(rename): move ae extension to mineagentae identities` | Files: [ext-ae/**]

- [ ] 7. Rename Matrix extension source and runtime identity strings

  **What to do**: Rename the Matrix extension package tree from `space.controlnet.chatmc.matrix...` to `space.controlnet.mineagent.matrix...`, and update all Matrix-specific runtime identifiers, metadata references, display names, and cross-module dependency strings to the `mineagentmatrix` family. Treat the Matrix scaffold as first-class scope even if it has lighter test coverage than base and AE.
  **Must NOT do**: Must NOT leave Matrix as a partially renamed exception. Must NOT retain old package roots or metadata identifiers because the module is scaffolded. Must NOT skip Matrix docs/resources just because runtime coverage is lighter.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: Matrix is smaller but still spans multiple loaders and metadata surfaces.
  - Skills: `[]` — No extra skill is required.
  - Omitted: `["git-master"]` — No commit action belongs here.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [10, 11] | Blocked By: [1, 2, 3]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `ext-matrix/common-1.20.1/src/main/java/space/controlnet/chatmc/matrix/common/ChatMCMatrix.java` — Matrix root class and naming anchor.
  - Pattern: `ext-matrix/fabric-1.20.1/src/main/java/space/controlnet/chatmc/matrix/fabric/ChatMCMatrixFabric.java` — Matrix Fabric entrypoint package root.
  - Pattern: `ext-matrix/forge-1.20.1/src/main/java/space/controlnet/chatmc/matrix/forge/ChatMCMatrixForge.java` — Matrix Forge entrypoint package root.
  - Pattern: `ext-matrix/fabric-1.20.1/src/main/resources/fabric.mod.json` — Matrix Fabric identity/dependency metadata.
  - Pattern: `ext-matrix/forge-1.20.1/src/main/resources/META-INF/mods.toml` — Matrix Forge identity/dependency metadata.
  - Pattern: `ext-matrix/common-1.20.1/src/main/resources/pack.mcmeta` — Matrix resource-pack identity text.

  **Acceptance Criteria** (agent-executable only):
  - [ ] No tracked file under `ext-matrix/` contains ChatMC-era package names or Matrix product identifiers after the rename.
  - [ ] Matrix entrypoints, metadata, and display names align with the `mineagentmatrix` family.
  - [ ] Matrix module references do not point at legacy base or extension IDs.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Matrix code and metadata are fully renamed
    Tool: Bash
    Steps: Run `git grep -nE 'chatmc|chatmcmatrix|ChatMC|ChatMCMatrix|ChatAE|chatae' -- ext-matrix`.
    Expected: No legacy names remain under `ext-matrix/`.
    Evidence: .sisyphus/evidence/task-7-matrix-runtime.log

  Scenario: Matrix remains a partially renamed exception
    Tool: Bash
    Steps: Re-run the same grep after source, metadata, and docs updates for Matrix.
    Expected: Task fails if any package root, modid, display name, or dependency string still references a ChatMC-era identity.
    Evidence: .sisyphus/evidence/task-7-matrix-runtime-error.log
  ```

  **Commit**: NO | Message: `refactor(rename): move matrix extension to mineagentmatrix identities` | Files: [ext-matrix/**]

- [ ] 8. Rename scripted asset-generation and cross-resource path surfaces

  **What to do**: Update helper scripts and any non-loader resource/path generation logic that still emits ChatMC-era namespaces or file paths, especially the AI terminal texture generator and any scripted resource outputs. Ensure generated or copied resources land under the MineAgent-era namespace family and that script comments, constants, and destination paths no longer reference old names.
  **Must NOT do**: Must NOT update resource folders manually while leaving scripts to regenerate old paths later. Must NOT preserve old namespace strings in generator constants or comments that are still tracked in the repo.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: Helper scripts are easy to miss and can silently reintroduce old names after an otherwise successful rename.
  - Skills: `[]` — No extra skill required.
  - Omitted: `["git-master"]` — No commit action is needed.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [10, 11] | Blocked By: [1, 2, 3]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `scripts/generate_ai_terminal_textures.py` — Generates AE resource outputs under the current `chatmcae` namespace.
  - Pattern: `ext-ae/common-1.20.1/src/main/resources/assets/chatmcae/lang/en_us.json` — AE namespace consumer.
  - Pattern: `ext-ae/common-1.20.1/src/main/resources/assets/chatmcae/lang/zh_cn.json` — AE localized namespace consumer.
  - Pattern: `base/common-1.20.1/src/main/resources/assets/chatmc/lang/en_us.json` — Base namespace consumer.
  - Pattern: `base/common-1.20.1/src/main/resources/assets/chatmc/lang/zh_cn.json` — Base localized namespace consumer.

  **Acceptance Criteria** (agent-executable only):
  - [ ] Tracked helper scripts and script-generated path constants use only MineAgent-era namespaces and destinations.
  - [ ] Resource path producers and consumers agree on the renamed namespace family.
  - [ ] A tracked-file grep across `scripts/` and relevant resource consumers returns zero ChatMC-era identifiers.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Helper scripts no longer generate legacy namespace paths
    Tool: Bash
    Steps: Run `git grep -nE 'chatmc|chatmcae|chatmcmatrix|ChatMC|ChatMCAe|ChatMCMatrix|ChatAE|chatae' -- scripts base/common-1.20.1/src/main/resources ext-ae/common-1.20.1/src/main/resources`.
    Expected: No script or tracked resource consumer retains a legacy namespace/path string.
    Evidence: .sisyphus/evidence/task-8-scripted-paths.log

  Scenario: Script constants still point at legacy output folders
    Tool: Bash
    Steps: Re-run the same grep after script updates.
    Expected: Task fails if any generator constant, output directory, or tracked comment still references ChatMC-era names.
    Evidence: .sisyphus/evidence/task-8-scripted-paths-error.log
  ```

  **Commit**: NO | Message: `refactor(rename): update scripted resource paths to mineagent` | Files: [scripts/generate_ai_terminal_textures.py, base/common-1.20.1/src/main/resources/**, ext-ae/common-1.20.1/src/main/resources/**]

- [ ] 9. Rewrite docs, localization, and tracked planning artifacts to remove all old names

  **What to do**: Update README, REPO, `AGENTS.md`, localization text, pack descriptions, and tracked `.sisyphus` markdown/log artifacts so the repository text itself contains no ChatMC-era names after the rename. Rewrite historical tracked planning files where necessary so the tracked-file residue gate can legitimately pass without exceptions. In `AGENTS.md`, limit changes to identity/path references required by the rename; do not alter unrelated policy content.
  **Must NOT do**: Must NOT leave README/REPO, `AGENTS.md`, or tracked `.sisyphus` docs on the old brand while code is renamed. Must NOT alter unrelated `AGENTS.md` rules beyond rename-driven identity/path cleanup. Must NOT rely on grep exclusions for tracked docs that still mention old names.

  **Recommended Agent Profile**:
  - Category: `writing` — Reason: This is primarily a documentation and tracked-text normalization task with strict scope boundaries.
  - Skills: `[]` — No extra writing skill is required.
  - Omitted: `["git-master"]` — No commit action is needed.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [10, 11] | Blocked By: [1, 2, 4]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `README.md` — Public project/module/artifact naming and usage text.
  - Pattern: `REPO.md` — Canonical architecture and release documentation.
  - Pattern: `AGENTS.md` — Project guidance file containing tracked identity/path references that must be renamed without changing unrelated rules.
  - Pattern: `base/common-1.20.1/src/main/resources/assets/chatmc/lang/en_us.json` — Base in-game text surface.
  - Pattern: `base/common-1.20.1/src/main/resources/assets/chatmc/lang/zh_cn.json` — Base localized text surface.
  - Pattern: `ext-ae/common-1.20.1/src/main/resources/assets/chatmcae/lang/en_us.json` — AE in-game text surface.
  - Pattern: `ext-ae/common-1.20.1/src/main/resources/assets/chatmcae/lang/zh_cn.json` — AE localized text surface.
  - Pattern: `.sisyphus/plans/chatmc-test-pyramid-gametest-adoption.md` — Tracked plan file with legacy naming.
  - Pattern: `.sisyphus/notepads/chatmc-test-pyramid-gametest-adoption/decisions.md` — Tracked notepad artifact with legacy naming.
  - Pattern: `.sisyphus/evidence/f2-code-test-quality.md` — Representative tracked evidence/doc artifact that may need normalization if it still contains old names.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `README.md`, `REPO.md`, `AGENTS.md`, tracked localization text, and tracked `.sisyphus` artifacts contain no ChatMC-era names.
  - [ ] No tracked documentation file relies on “historical note” exceptions to keep old names.
  - [ ] `AGENTS.md` changes are limited to rename-driven identity/path cleanup and do not modify unrelated repo rules.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Tracked docs and localization are fully rewritten to MineAgent naming
    Tool: Bash
    Steps: Run `git grep -nE 'chatmc|chatmcae|chatmcmatrix|ChatMC|ChatMCAe|ChatMCMatrix|ChatAE|chatae' -- README.md REPO.md AGENTS.md .sisyphus base/common-1.20.1/src/main/resources ext-ae/common-1.20.1/src/main/resources ext-matrix/common-1.20.1/src/main/resources`.
    Expected: No tracked documentation or localization file retains a legacy name.
    Evidence: .sisyphus/evidence/task-9-docs-residue.log

  Scenario: Legacy names survive only in tracked planning/history files
    Tool: Bash
    Steps: Re-run the same grep after documentation updates.
    Expected: Task fails if any remaining hit comes from tracked `.sisyphus` markdown/log artifacts or public docs.
    Evidence: .sisyphus/evidence/task-9-docs-residue-error.log
  ```

  **Commit**: NO | Message: `docs(rename): remove legacy chatmc naming from tracked text` | Files: [README.md, REPO.md, AGENTS.md, .sisyphus/**, base/**/src/main/resources/**, ext-ae/**/src/main/resources/**, ext-matrix/**/src/main/resources/**]

- [ ] 10. Run a repository-wide tracked-file residue sweep and remove all legacy identifiers

  **What to do**: After all module/build/docs changes land, perform a repository-wide residue sweep across tracked files only and eliminate every remaining ChatMC-era identifier family, including case variants and ChatAE-era leftovers. Any remaining hit is a blocker and must be fixed in-place before final verification begins.
  **Must NOT do**: Must NOT waive tracked-file hits as “acceptable history.” Must NOT rely on ignored-directory cleanup to claim success. Must NOT start final verification until the tracked-file residue scan is completely clean.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: This is the final convergence gate that catches cross-cutting residue from every previous task.
  - Skills: `[]` — No extra skill is needed.
  - Omitted: `["git-master"]` — No commit action is required.

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: [11, 12, F1, F2, F3, F4] | Blocked By: [5, 6, 7, 8, 9]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `README.md` — Public docs residue surface.
  - Pattern: `REPO.md` — Architecture/release docs residue surface.
  - Pattern: `.github/workflows/layered-testing.yml` — CI residue surface.
  - Pattern: `.github/workflows/release.yml` — Release residue surface.
  - Pattern: `settings.gradle` — Root identity residue surface.
  - Pattern: `gradle.properties` — Artifact/group residue surface.
  - Pattern: `build.gradle` — Central naming residue surface.
  - Pattern: `.sisyphus/` — Tracked planning/history residue surface.
  - Pattern: `base/` — Base code/resource residue surface.
  - Pattern: `ext-ae/` — AE code/resource residue surface.
  - Pattern: `ext-matrix/` — Matrix code/resource residue surface.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `git grep -nE 'chatmc|chatmcae|chatmcmatrix|ChatMC|ChatMCAe|ChatMCMatrix|ChatAE|chatae'` returns zero matches.
  - [ ] Any new residue family discovered during this sweep is fixed before moving to Task 11.
  - [ ] The clean scan output is captured as evidence.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Repository-wide tracked-file residue scan is completely clean
    Tool: Bash
    Steps: Run `git grep -nE 'chatmc|chatmcae|chatmcmatrix|ChatMC|ChatMCAe|ChatMCMatrix|ChatAE|chatae'`.
    Expected: The command returns no matches.
    Evidence: .sisyphus/evidence/task-10-global-residue.log

  Scenario: A legacy name survives in a tracked file
    Tool: Bash
    Steps: Run the same residue grep after all prior tasks complete.
    Expected: Task fails immediately on any hit; no final verification command is allowed until the residue scan is clean.
    Evidence: .sisyphus/evidence/task-10-global-residue-error.log
  ```

  **Commit**: NO | Message: `chore(rename): clear final legacy identifier residue` | Files: [repo-wide tracked files]

- [ ] 11. Unblock Forge GameTest runtime verification if the known blocker is still present

  **What to do**: Explicitly test the documented Forge GameTest runtime path after the rename work lands, classify whether the known Forge blocker still occurs, and fix the blocker if it is still present. This task exists because the repository already documents a known blocked-runtime condition, so the plan cannot assume Forge verification is currently attainable without first clearing that condition. The task is complete only when both Forge GameTest commands are runnable as normal pass/fail verification steps rather than immediately terminating in the known blocked state.
  **Must NOT do**: Must NOT hand-wave the blocked Forge state as acceptable. Must NOT skip this task and proceed straight to Task 12. Must NOT redefine “blocked” as success.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: Existing runtime blockers require diagnosis, targeted repair, and proof that the final verification path is now actually executable.
  - Skills: `[]` — Repo-specific evidence is sufficient.
  - Omitted: `["git-master"]` — No commit action belongs here.

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: [12, F1, F2, F3, F4] | Blocked By: [4, 5, 6, 8, 10]

  **References** (executor has NO interview context — be exhaustive):
  - Pattern: `AGENTS.md:94-100` — Documents the known Forge runtime blocker behavior on the dev lane.
  - Pattern: `.sisyphus/evidence/f2-code-test-quality.md:25-28` — Evidence that Forge GameTest runs were still blocked in prior work.
  - Pattern: `base/forge-1.20.1/build.gradle` — Base Forge GameTest task source.
  - Pattern: `ext-ae/forge-1.20.1/build.gradle` — AE Forge GameTest task source.
  - Pattern: `base/forge-1.20.1/src/main/java/space/controlnet/chatmc/forge/gametest/ChatMCGameTestBootstrap.java` — Base Forge bootstrap surface affected by rename.
  - Pattern: `ext-ae/forge-1.20.1/src/main/java/space/controlnet/chatmc/ae/forge/gametest/ChatMCAeGameTestBootstrap.java` — AE Forge bootstrap surface affected by rename.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `timeout 25m ./gradlew --no-daemon --configure-on-demand :base:forge-1.20.1:runGameTestServer --stacktrace` no longer exits in the documented blocked-runtime state.
  - [ ] `timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:forge-1.20.1:runGameTestServer --stacktrace` no longer exits in the documented blocked-runtime state.
  - [ ] Evidence captures whether the blocker reproduced, what was fixed, and the first successful post-fix Forge runs.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Forge runtime blocker is cleared before final verification
    Tool: Bash
    Steps: Run `timeout 25m ./gradlew --no-daemon --configure-on-demand :base:forge-1.20.1:runGameTestServer --stacktrace`; run `timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:forge-1.20.1:runGameTestServer --stacktrace`; inspect outputs against the documented blocked-runtime signature from `AGENTS.md` and prior evidence.
    Expected: Both commands execute as normal verification runs and do not terminate in the known blocked state.
    Evidence: .sisyphus/evidence/task-11-forge-unblock.log

  Scenario: Known Forge blocked-runtime condition still reproduces
    Tool: Bash
    Steps: Re-run the two Forge commands before final verification.
    Expected: Task fails if either command still reproduces the documented blocked signature or equivalent blocked exit path.
    Evidence: .sisyphus/evidence/task-11-forge-unblock-error.log
  ```

  **Commit**: NO | Message: `fix(rename): unblock forge gametest verification for mineagent rename` | Files: [forge-related runtime and metadata files as needed]

- [ ] 12. Execute the full verification matrix and audit final dist outputs

  **What to do**: Run the complete post-rename verification matrix exactly as approved: targeted JUnit suites, Forge GameTests for base and AE, Fabric GameTests for base and AE smoke, and `./scripts/build-dist.sh`. After successful execution, audit `dist/` so every packaged jar prefix matches the MineAgent naming family and no legacy artifact names remain.
  **Must NOT do**: Must NOT skip Forge/Fabric GameTests because JUnit passed. Must NOT treat a successful build as sufficient without packaging. Must NOT accept `dist/` outputs that still use old prefixes anywhere.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: This is a high-effort execution and audit gate across all approved verification channels.
  - Skills: `[]` — Repo commands are already known.
  - Omitted: `["git-master"]` — Commiting is not part of this verification task.

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: [F1, F2, F3, F4] | Blocked By: [2, 3, 4, 5, 6, 7, 8, 9, 10, 11]

  **References** (executor has NO interview context — be exhaustive):
  - Test: `base/core/build.gradle` — Base core JUnit task source.
  - Test: `base/common-1.20.1/build.gradle` — Base common JUnit task source.
  - Test: `ext-ae/common-1.20.1/build.gradle` — AE common JUnit task source.
  - Test: `base/forge-1.20.1/build.gradle` — Base Forge GameTest task source.
  - Test: `ext-ae/forge-1.20.1/build.gradle` — AE Forge GameTest task source.
  - Test: `base/fabric-1.20.1/build.gradle` — Base Fabric GameTest task source.
  - Test: `ext-ae/fabric-1.20.1/build.gradle` — AE Fabric GameTest task source.
  - Pattern: `scripts/build-dist.sh` — Final packaging command.
  - Pattern: `.github/workflows/release.yml` — Dist/release artifact expectation source.
  - Pattern: `README.md` — Expected jar naming examples that should now match MineAgent-era outputs.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `./gradlew --no-daemon --configure-on-demand :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test` succeeds.
  - [ ] `timeout 25m ./gradlew --no-daemon --configure-on-demand :base:forge-1.20.1:runGameTestServer --stacktrace` succeeds.
  - [ ] `timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:forge-1.20.1:runGameTestServer --stacktrace` succeeds.
  - [ ] `timeout 25m ./gradlew --no-daemon --configure-on-demand :base:fabric-1.20.1:runGametest --stacktrace` succeeds.
  - [ ] `timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:fabric-1.20.1:runGametest --stacktrace -Dfabric-api.gametest.filter=ae_smoke` succeeds.
  - [ ] `./scripts/build-dist.sh` succeeds.
  - [ ] `dist/` contains only MineAgent-era jar prefixes and no `chatmc*` legacy artifacts.

  **QA Scenarios** (MANDATORY — task incomplete without these):
  ```
  Scenario: Full post-rename verification matrix passes
    Tool: Bash
    Steps: Run `./gradlew --no-daemon --configure-on-demand :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test`; run `timeout 25m ./gradlew --no-daemon --configure-on-demand :base:forge-1.20.1:runGameTestServer --stacktrace`; run `timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:forge-1.20.1:runGameTestServer --stacktrace`; run `timeout 25m ./gradlew --no-daemon --configure-on-demand :base:fabric-1.20.1:runGametest --stacktrace`; run `timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:fabric-1.20.1:runGametest --stacktrace -Dfabric-api.gametest.filter=ae_smoke`; then run `./scripts/build-dist.sh`.
    Expected: All commands exit successfully and produce MineAgent-era outputs only.
    Evidence: .sisyphus/evidence/task-12-full-verification.log

  Scenario: Packaging or runtime verification still emits legacy outputs
    Tool: Bash
    Steps: After running the full matrix, inspect `dist/` and rerun `git grep -nE 'chatmc|chatmcae|chatmcmatrix|ChatMC|ChatMCAe|ChatMCMatrix|ChatAE|chatae'`.
    Expected: Task fails if any jar prefix, packaged artifact, or tracked file residue still uses a legacy identifier.
    Evidence: .sisyphus/evidence/task-12-full-verification-error.log
  ```

  **Commit**: YES | Message: `refactor(rename): rename ChatMC identities to MineAgent` | Files: [repo-wide tracked files]

## Final Verification Wave (MANDATORY — after ALL implementation tasks)
> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.
> **Do NOT auto-proceed after verification. Wait for user's explicit approval before marking work complete.**
> **Never mark F1-F4 as checked before getting user's okay.** Rejection or user feedback -> fix -> re-run -> present again -> wait for okay.
- [ ] F1. Plan Compliance Audit — oracle
- [ ] F2. Code Quality Review — unspecified-high
- [ ] F3. Real Manual QA — unspecified-high (+ playwright if UI)
- [ ] F4. Scope Fidelity Check — deep

  **Final Verification QA Scenarios**:
  ```
  Scenario: F1 Plan Compliance Audit is executable
    Tool: task(subagent_type="oracle")
    Steps: Review implemented diff, evidence files, and `.sisyphus/plans/mineagent-full-rename.md`; verify every completed implementation task matches the plan’s required scope, guardrails, and acceptance criteria.
    Expected: Oracle returns explicit PASS/FAIL with any deviations listed against task numbers.
    Evidence: .sisyphus/evidence/f1-plan-compliance.md

  Scenario: F2 Code Quality Review is executable
    Tool: task(category="unspecified-high")
    Steps: Review the final diff for naming consistency, unnecessary churn, broken boundaries, dead compatibility leftovers, and incomplete residue cleanup.
    Expected: Reviewer returns explicit PASS/FAIL with file-specific findings and no unreviewed rename residue.
    Evidence: .sisyphus/evidence/f2-code-quality.md

  Scenario: F3 Real Manual QA is executable without human intervention
    Tool: Bash
    Steps: Replay the approved command-based verification evidence from Tasks 10-12, inspect `dist/` outputs, and verify that user-facing names in tracked lang/docs/metadata artifacts are MineAgent-era only.
    Expected: Reviewer returns explicit PASS/FAIL based on executable evidence and artifact inspection, despite the label “Real Manual QA”.
    Evidence: .sisyphus/evidence/f3-manual-qa.md

  Scenario: F4 Scope Fidelity Check is executable
    Tool: task(category="deep")
    Steps: Review final scope against the original request and interview decisions; verify no compatibility layer was introduced and no old tracked names remain.
    Expected: Deep reviewer returns explicit PASS/FAIL with any out-of-scope or missing-scope findings.
    Evidence: .sisyphus/evidence/f4-scope-fidelity.md
  ```

## Commit Strategy
- Preferred outcome: one primary breaking-change commit after all implementation tasks and before/after the final verification wave, depending on executor workflow requirements.
- Commit message target: `refactor(rename): rename ChatMC identities to MineAgent`
- If the executor must split commits, use boundary-based commits only: build/metadata, source/runtime, docs/residue. Do not create per-file micro-commits.

## Success Criteria
- The repository compiles, test suites pass, packaging succeeds, and tracked files contain no legacy names.
- Base, AE, and Matrix use a single MineAgent naming family with no mixed old/new identity combinations.
- Release outputs, workflow expectations, runtime metadata, and documentation all agree on the same renamed identities.
