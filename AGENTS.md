# AGENTS.md

This file is for agentic coding tools working in `/home/zhixi/GitRepos/ChatMC`.
It summarizes the repo's real build/test entrypoints, release automation, and code style.
Follow this file before making changes.

## Repository overview

- Project type: Java 17 Minecraft mod workspace with Architectury/Loom.
- Main branches: `dev` for integration, `master` for releases.
- Modules:
  - `base/core`: pure Java domain logic
  - `base/common-1.20.1`: shared MC-facing layer
  - `base/fabric-1.20.1`, `base/forge-1.20.1`: loader wiring
  - `ext-ae/*`: AE2 extension
  - `ext-matrix/*`: Matrix scaffold
- Packaging script: `scripts/build-dist.sh`.
- CI workflows: `.github/workflows/layered-testing.yml`, `.github/workflows/release.yml`.

## Rules sources checked

- No repo-root AGENTS file existed before this one.
- No `.cursorrules` file was found.
- No `.cursor/rules/` rules were found.
- No `.github/copilot-instructions.md` file was found.
- Therefore this file is the primary repo-local agent guidance.

## Environment and tooling

- Use Java 17.
- Use the Gradle wrapper: `./gradlew`.
- In CI and local automation, prefer `--no-daemon`.
- For JUnit-only tasks, prefer `--configure-on-demand` to avoid eager configuration of unrelated Fabric/Forge platform modules.
- Use `python3` for repo utility scripts in `scripts/`.

## High-value commands

- List tasks for a module: `./gradlew --no-daemon :base:core:tasks --all`
- Build distributable jars: `./scripts/build-dist.sh`

### JUnit test commands

- Base core: `./gradlew --no-daemon --configure-on-demand :base:core:test`
- Base common: `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test`
- AE common: `./gradlew --no-daemon --configure-on-demand :ext-ae:common-1.20.1:test`
- CI-style JUnit suite:
  - `./gradlew --no-daemon --configure-on-demand :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test`

### Run a single JUnit test class

- Base core:
  - `./gradlew --no-daemon --configure-on-demand :base:core:test --tests 'space.controlnet.chatmc.core.session.ServerSessionManagerStateMachineRegressionTest'`
- Base common:
  - `./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.chatmc.common.network.NetworkProposalLifecycleBehaviorTest'`
- AE common:
  - `./gradlew --no-daemon --configure-on-demand :ext-ae:common-1.20.1:test --tests 'space.controlnet.chatmc.ae.common.tools.AeThreadConfinementRegressionTest'`

### Run a single JUnit test method

- Pattern:
  - `./gradlew --no-daemon --configure-on-demand <module>:test --tests 'fully.qualified.ClassName.methodName'`
- Example:
  - `./gradlew --no-daemon --configure-on-demand :base:core:test --tests 'space.controlnet.chatmc.core.session.ServerSessionManagerStateMachineRegressionTest.task12_stateMachine_legalAndIllegalTransitions_areDeterministic'`

### GameTest commands

- Forge base GameTests:
  - `timeout 25m ./gradlew --no-daemon --configure-on-demand :base:forge-1.20.1:runGameTestServer --stacktrace`
- Forge AE GameTests:
  - `timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:forge-1.20.1:runGameTestServer --stacktrace`
- Fabric base GameTests:
  - `timeout 25m ./gradlew --no-daemon --configure-on-demand :base:fabric-1.20.1:runGametest --stacktrace`
- Fabric AE smoke GameTests:
  - `timeout 25m ./gradlew --no-daemon --configure-on-demand :ext-ae:fabric-1.20.1:runGametest --stacktrace -Dfabric-api.gametest.filter=ae_smoke`

### Single GameTest guidance

- There is no stable repo-local wrapper for running one specific Forge GameTest method.
- Forge GameTests are registered by bootstrap classes:
  - `base/forge-1.20.1/.../ChatMCGameTestBootstrap.java`
  - `ext-ae/forge-1.20.1/.../ChatMCAeGameTestBootstrap.java`
- Fabric currently has an existing smoke filter only for the AE smoke path: `-Dfabric-api.gametest.filter=ae_smoke`.
- Prefer single JUnit class/method execution before touching GameTest scope.

### Build commands

- Pure Java module: `./gradlew --no-daemon :base:core:build`
- Common module: `./gradlew --no-daemon :base:common-1.20.1:build`
- Platform modules: `./gradlew --no-daemon :base:fabric-1.20.1:build`, `./gradlew --no-daemon :base:forge-1.20.1:build`
- Full packaging: `./scripts/build-dist.sh`

### CI / release behavior

- `dev` push runs layered testing.
- `master` push runs `.github/workflows/release.yml` automatically.
- Manual dispatch exists for the release workflow, but the publish job still only runs on `refs/heads/master`.
- Releases are built from `gradle.properties` values `mod_version` and `minecraft_version`.
- GitHub prerelease behavior is automatic for `0.*`, `alpha`, `beta`, and `rc` versions.
- The dev integration lane is triggered by the `dev` branch, but the report/policy parser still uses the internal lane key `main`.
- Known Forge runtime blocker signatures on the dev lane are surfaced as `blocked` (exit code `3`), not silently treated as normal passes.

## Artifacts and reports

- Dist jars land in `dist/`; the packaging script copies final platform jars there, not the intermediate common-module jars.
- JUnit XML lands under `**/build/test-results/test/*.xml`.
- Fabric GameTest XML lands under `base/fabric-1.20.1/build/reports/gametest/` and `ext-ae/fabric-1.20.1/build/reports/gametest/`.
- PR summaries land under `ci-reports/pr/*-summary.json`; dev and nightly summaries are `ci-reports/dev/summary.json` and `ci-reports/nightly/summary.json`.

## Code style: structure and formatting

- Language in code, comments, docs, and identifiers is English.
- Use 4 spaces for indentation.
- Keep one top-level public type per file.
- Prefer small, focused edits over broad refactors.
- Preserve module boundaries; do not pull loader-specific code into `base/core`.
- `base/core` must stay free of Minecraft, AE2, Architectury, Fabric, and Forge runtime dependencies.

## Code style: imports

- Keep `package` declaration first, then a blank line.
- Group imports in blocks with a blank line between logical groups.
- A common pattern is: third-party / Minecraft / test imports, then `space.controlnet.chatmc...`, then `java.*` last.
- Preserve the local import grouping of the file you are editing instead of reordering imports to fit a stricter rule.
- Do not reorder imports unnecessarily in untouched files.
- Prefer explicit imports in new or substantially edited files, but do not churn existing files solely to remove wildcard imports.

## Code style: naming

- Classes are PascalCase.
- Methods and fields are camelCase.
- Constants are `UPPER_SNAKE_CASE`.
- Tests use long descriptive method names, often with scenario identifiers like `task7_...` or `task12_...`.
- Preserve existing module and mod identifiers exactly: `chatmc`, `chatmcae`, `chatmcmatrix`.

## Code style: types and data modeling

- Prefer `record` for small immutable data carriers when the repo already models that concept as a record.
- Prefer `Optional<T>` for optional values instead of nullable return values.
- Normalize null collections defensively in constructors/compact record constructors when a type is exposed across boundaries.
- Prefer immutable copies (`List.copyOf`, `Map.copyOf`) when storing incoming collections.

## Code style: control flow and error handling

- Validate inputs early and return clear, deterministic error messages.
- Prefer explicit validation helpers over silent coercion.
- Do not swallow exceptions.
- Empty catch blocks are forbidden.
- In tests, convert reflection/setup failures into `AssertionError` with a stable scenario-specific message.
- Keep runtime failure messages actionable and stable because tests assert on them.

## Code style: comments and docs

- Add comments only when the code is not obvious.
- Short Javadoc on public records/factories is common and acceptable.
- Do not add noisy “what this line does” comments.
- Keep docs aligned with current branch model (`dev` / `master`) and current artifact names.

## Testing expectations

- Prefer targeted JUnit first.
- Use GameTests for runtime / loader / server-thread / lifecycle behavior.
- If changing behavior covered by an existing regression test, update or extend that exact test area instead of adding an unrelated new one.
- If touching async/session/proposal logic, expect to update regression coverage.

## Module boundary guidance

- `base/core`: pure domain logic only.
- `base/common-1.20.1`: shared MC-facing logic, serialization, networking glue, common behavior tests.
- `base/fabric-1.20.1` and `base/forge-1.20.1`: loader bootstrap and platform runtime wiring.
- `ext-ae/*`: AE2-specific logic and tests.
- `ext-matrix/*`: keep changes minimal unless intentionally expanding Matrix support.

## LLM / agent-specific guidance

- The repo currently supports only the OpenAI provider.
- `baseUrl` is an OpenAI-compatible endpoint override, not a signal to add more provider branches.
- Keep prompt/config/runtime changes aligned with the OpenAI-only design.

## Before you finish a coding task

- Run the narrowest relevant test command first.
- If you changed packaging or release logic, run `./scripts/build-dist.sh`.
- If you changed CI commands, make sure the command examples in docs still match the workflow files.
- Do not commit local-only files such as `.claude/` or ad hoc planning notes unless the user explicitly asks.
