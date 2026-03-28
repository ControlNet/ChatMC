# ChatMC Test Pyramid + GameTest Adoption Plan

## TL;DR

> **Quick Summary**: 在保持现有 JUnit 快速反馈能力的前提下，为 ChatMC 建立“Unit → Integration → GameTest”三层测试体系，优先落地 Forge 运行时场景，再做 Fabric 对齐与 AE2 深度场景。
>
> **Deliverables**:
> - Forge/Fabric GameTest 基础设施（共享场景逻辑 + loader 薄壳）
> - 首批 5 个高价值运行时场景（绑定审批、INDEXING 恢复、viewer 同步、线程约束、65536 边界）
> - AE2 运行时生命周期场景（request/status/cancel）
> - 分层 CI 执行与报告产物规范
>
> **Estimated Effort**: Large
> **Parallel Execution**: YES — 4 waves + Final Verification
> **Critical Path**: 1 → 2/3 → 6/7/8/9/10 → 13/14 → 16/17/18 → F1-F4

---

## Context

### Original Request
- 用户要求开始制定测试计划，重点是：应该写哪些测试、模拟哪些关键场景、是否能在 Forge/Fabric 复用测试代码。

### Interview Summary
**Key Discussions**:
- 当前项目测试以 JUnit 为主；`base/common-1.20.1` 存在部分 source-string 合同型回归。
- 需要把“真实运行时行为”从仅源码断言迁移到行为验证，GameTest 是关键补位。
- Forge/Fabric 不应双写整套测试，采用“共享场景逻辑 + loader 特定注册壳层”。

**Research Findings**:
- 当前仓库尚未完成 GameTest 任务与 entrypoint 配置。
- 高风险路径集中在：
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java`
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/ServerSessionManager.java`
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/session/ChatMCSessionsSavedData.java`
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java`
  - `ext-ae/common-1.20.1/src/main/java/space/controlnet/chatmc/ae/common/part/AiTerminalPartOperations.java`

### Metis Review
**Identified Gaps** (addressed in this plan):
- 缺少 phase 边界与 CI 预算策略 → 已分层为 PR/Main/Nightly。
- 缺少“必须 runtime 验证”的强制清单 → 已在任务与验收标准中固定。
- 缺少 loader 分歧 guardrail → 已定义共享逻辑与 wrapper 边界。
- 缺少 flake 策略 → 已加入 deterministic harness 与稳定性门槛任务。

---

## Work Objectives

### Core Objective
建立可执行、可扩展、可并行的测试体系：保留 JUnit 快速回归优势，同时引入 GameTest 覆盖 JUnit 无法可信证明的 Minecraft 运行时不变量。

### Concrete Deliverables
- [x] Forge/Fabric GameTest 基础配置与发现机制（base + ext-ae）
- [x] 共享场景逻辑层（scenario IDs、断言约定、deterministic helper）
- [x] Base 首批 5 个运行时场景 GameTest
- [x] AE2 生命周期 GameTest
- [x] CI 分层执行命令与报告落盘规范（JUnit XML + GameTest report）

### Definition of Done
- [x] `./gradlew :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test` 通过
- [x] Forge GameTest 任务可运行并产出可解析报告（0 failures/0 errors）
- [x] Fabric GameTest 任务可发现并执行共享场景壳层（至少 smoke 套件通过）
- [x] `REPO.md` 测试策略章节与执行命令保持同步

### Must Have
- 共享测试场景逻辑（避免 Forge/Fabric 业务断言重复）
- 对以下场景给出运行时验证：绑定审批、INDEXING 恢复、viewer 同步、线程约束、65536 边界、AE2 job 生命周期
- 自动化可执行验收（零人工手点 UI）

### Must NOT Have (Guardrails)
- 不引入 `ext-matrix` 范围
- 不把 client UI 自动化纳入第一阶段
- 不以“重构生产代码”为主要目标（仅为测试可执行性做最小必要改动）
- 不以 source-string 断言替代运行时行为验证

### Default Policy Decisions (auto-applied)
- **Phase scope**: 本计划单文件覆盖 base + ext-ae；base 运行时场景先落地，AE 场景在 Wave 3 完成。
- **GameTest shape**: 第一阶段仅做 server-side GameTest（不引入 client UI 自动化）。
- **CI budget default**:
  - PR lane: 仅 JUnit（不增加 GameTest 时长）
  - Main lane: Forge GameTest，目标 < 20 分钟
  - Nightly lane: Fabric + AE heavy，目标 < 45 分钟
- **Parity rule**: Forge 首批共享场景在 Wave 4 必须形成 Fabric parity 报告（逐场景状态映射）。
- **Flake promotion rule**: 进入门禁的 GameTest 需至少连续 2 次全绿。

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** — 所有验收与 QA 场景均由 agent 执行命令完成。

### Test Decision
- **Infrastructure exists**: YES（JUnit 5）
- **Automated tests**: YES（TDD for new GameTest + tests-after for migration items）
- **Framework**: JUnit 5 + Forge GameTest + Fabric GameTest API
- **TDD policy**: 新增 GameTest/Integration 测试先 RED（失败）再 GREEN（修复）

### QA Policy
- Frontend/UI：不在本计划首阶段作为主验证手段。
- Runtime/API/Module：全部通过 Bash/Gradle + 报告解析进行验证。
- Evidence 输出路径：`.sisyphus/evidence/task-{N}-{scenario}.log|xml|json`

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Foundation + Harness, 5 tasks):
├── Task 1: Shared scenario contract + deterministic test support [deep]
├── Task 2: Forge base GameTest wiring + namespace properties [quick]
├── Task 3: Fabric base GameTest entrypoint + run args/report path [quick]
├── Task 4: Source-string regressions → behavior integration tests (base/common) [unspecified-high]
└── Task 5: Async/indexing/thread test utilities for stable orchestration [deep]

Wave 2 (Base runtime scenarios, 6 tasks):
├── Task 6: GameTest — proposal binding unavailable path [deep]
├── Task 7: GameTest — INDEXING gate + recovery across reload [deep]
├── Task 8: GameTest — multi-viewer snapshot consistency [unspecified-high]
├── Task 9: GameTest — server-thread confinement on tool execution [deep]
├── Task 10: GameTest — 65536 boundary E2E parse/network/persist [unspecified-high]
└── Task 11: Integration — SavedData reload normalization behavior [quick]

Wave 3 (AE + parity + CI shape, 4 tasks):
├── Task 12: Forge AE GameTest — request/status/cancel lifecycle [deep]
├── Task 13: Fabric wrappers for shared base GameTest scenarios [quick]
├── Task 14: Fabric wrappers for AE GameTest scenarios [quick]
└── Task 15: CI lanes + report collection + flake policy wiring [unspecified-high]

Wave 4 (Execution hardening + evidence, 3 tasks):
├── Task 16: Run and fix full JUnit matrix [quick]
├── Task 17: Run and stabilize Forge GameTests (base+AE) [deep]
└── Task 18: Run Fabric GameTests + parity diff report [unspecified-high]

Wave FINAL (After ALL tasks — independent review, 4 parallel):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality + test quality review (unspecified-high)
├── Task F3: End-to-end scenario replay validation (unspecified-high)
└── Task F4: Scope fidelity check (deep)

Critical Path: 1 → 2/3 → 6 → 12 → 17 → 18 → F1/F2/F3/F4
Parallel Speedup: ~65%
Max Concurrent: 6 (Wave 2)
```

### Dependency Matrix (FULL)

- **1**: Blocked By — None | Blocks — 6,7,8,9,10,12,13,14
- **2**: Blocked By — 1 | Blocks — 6,7,8,9,10,17
- **3**: Blocked By — 1 | Blocks — 13,14,18
- **4**: Blocked By — None | Blocks — 16
- **5**: Blocked By — 1 | Blocks — 7,9,17
- **6**: Blocked By — 1,2 | Blocks — 13,17
- **7**: Blocked By — 1,2,5 | Blocks — 13,17
- **8**: Blocked By — 1,2 | Blocks — 13,17
- **9**: Blocked By — 1,2,5 | Blocks — 13,17
- **10**: Blocked By — 1,2 | Blocks — 13,17
- **11**: Blocked By — 4 | Blocks — 16
- **12**: Blocked By — 1,2 | Blocks — 14,17
- **13**: Blocked By — 3,6,7,8,9,10 | Blocks — 18
- **14**: Blocked By — 3,12 | Blocks — 18
- **15**: Blocked By — 2,3,12,13,14 | Blocks — 17,18
- **16**: Blocked By — 4,11 | Blocks — F2
- **17**: Blocked By — 2,5,6,7,8,9,10,12,15 | Blocks — F1,F2,F3
- **18**: Blocked By — 3,13,14,15,17 | Blocks — F1,F3,F4

### Agent Dispatch Summary

- **Wave 1**: 5 agents
  - T1 `deep`, T2 `quick`, T3 `quick`, T4 `unspecified-high`, T5 `deep`
- **Wave 2**: 6 agents
  - T6 `deep`, T7 `deep`, T8 `unspecified-high`, T9 `deep`, T10 `unspecified-high`, T11 `quick`
- **Wave 3**: 4 agents
  - T12 `deep`, T13 `quick`, T14 `quick`, T15 `unspecified-high`
- **Wave 4**: 3 agents
  - T16 `quick`, T17 `deep`, T18 `unspecified-high`
- **FINAL**: 4 agents
  - F1 `oracle`, F2 `unspecified-high`, F3 `unspecified-high`, F4 `deep`

---

## TODOs

- [x] 1. Build shared GameTest scenario contract and deterministic helpers

  **What to do**:
  - Add shared scenario IDs + invariant assertions for runtime scenarios (binding, indexing, viewers, thread, boundary).
  - Add deterministic tick/barrier utilities to avoid sleep-based flake patterns.
  - Add unit tests validating unique scenario IDs and helper timeout semantics.

  **Must NOT do**:
  - Do not add loader-specific APIs in shared helper code.
  - Do not modify production runtime logic in this task.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: needs deterministic async orchestration abstractions used by many downstream tests.
  - **Skills**: [`beads`]
    - `beads`: track multi-step test infrastructure changes and evidence trail.
  - **Skills Evaluated but Omitted**:
    - `playwright`: no browser interaction.
    - `github-cli`: no GitHub API interaction.

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 1 (foundation gate)
  - **Blocks**: 6, 7, 8, 9, 10, 12, 13, 14
  - **Blocked By**: None

  **References**:
  - `REPO.md:813-821` - runtime invariants that must become executable scenarios.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:1123-1230` - approval/execute/reloop path.
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/SessionOrchestrator.java:128-168` - broadcast + indexing gating behavior.
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/ServerSessionManager.java:210-306` - state transition guards to assert.

  **Acceptance Criteria**:
  - [x] Shared scenario contract files exist and compile in `base/common-1.20.1` test sources.
  - [x] Deterministic helper tests pass with bounded timeout assertions.
  - [x] No loader package import appears in shared scenario/helper files.

  **QA Scenarios**:
  ```
  Scenario: Shared helper deterministic timeout behavior
    Tool: Bash (Gradle)
    Preconditions: New helper + tests added
    Steps:
      1. Run `./gradlew :base:common-1.20.1:test --tests "*Deterministic*"`
      2. Assert output contains `BUILD SUCCESSFUL`
      3. Assert report XML has failures=0, errors=0
    Expected Result: Deterministic helper tests green and stable
    Failure Indicators: timeout hangs or flaky failures in repeated runs
    Evidence: .sisyphus/evidence/task-1-deterministic-helper.log

  Scenario: Duplicate scenario-id protection
    Tool: Bash (Gradle)
    Preconditions: Add temporary duplicate ID in test fixture branch
    Steps:
      1. Run `./gradlew :base:common-1.20.1:test --tests "*ScenarioId*"`
      2. Verify duplicate-id test fails with explicit assertion message
    Expected Result: Duplicate IDs are detected and rejected
    Evidence: .sisyphus/evidence/task-1-duplicate-id-error.log
  ```

  **Commit**: YES (group with 2)

- [x] 2. Wire Forge base GameTest execution and namespace discovery

  **What to do**:
  - Configure Forge run configuration/task for GameTest execution (`runGameTestServer`).
  - Add explicit namespace enablement for `chatmc` tests.
  - Add minimal Forge test registration bootstrap class for shared scenarios.

  **Must NOT do**:
  - Do not add Fabric-specific flags here.
  - Do not introduce AE test providers in base Forge task.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: mostly build/run wiring in a small number of files.
  - **Skills**: [`beads`]
    - `beads`: keep infra changes traceable with discrete checkpoints.
  - **Skills Evaluated but Omitted**:
    - `git-master`: no commit/rebase work required inside task.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with 3,4,5)
  - **Blocks**: 6, 7, 8, 9, 10, 17
  - **Blocked By**: 1

  **References**:
  - `base/forge-1.20.1/build.gradle:7-12,33-58` - existing Forge loom/dependency context to extend.
  - `base/forge-1.20.1/src/main/resources/META-INF/mods.toml:6-10` - `chatmc` mod namespace.
  - Forge docs (gametest): `https://docs.minecraftforge.net/en/latest/misc/gametest/` - `runGameTestServer`, namespace properties.

  **Acceptance Criteria**:
  - [x] Forge module exposes runnable GameTest server task.
  - [x] Namespace configuration ensures `chatmc` tests are discovered.
  - [x] Dry-run/real-run output shows discovered tests count > 0 once providers added.

  **QA Scenarios**:
  ```
  Scenario: Forge GameTest task available and runnable
    Tool: Bash (Gradle)
    Preconditions: Forge wiring complete
    Steps:
      1. Run `./gradlew :base:forge-1.20.1:tasks --all`
      2. Assert output contains `runGameTestServer`
      3. Run `./gradlew :base:forge-1.20.1:runGameTestServer --dry-run`
    Expected Result: Task exists and is schedulable without config errors
    Failure Indicators: unknown task / missing namespace property errors
    Evidence: .sisyphus/evidence/task-2-forge-task-discovery.log

  Scenario: Namespace misconfiguration fails discovery
    Tool: Bash (Gradle)
    Preconditions: Override namespace to invalid value in temp run
    Steps:
      1. Run GameTest with invalid namespace property
      2. Assert discovered tests count is zero or explicit namespace warning appears
    Expected Result: Misconfig is visible and diagnosable
    Evidence: .sisyphus/evidence/task-2-forge-namespace-error.log
  ```

  **Commit**: YES (group with 1)

- [x] 3. Wire Fabric base GameTest entrypoint, flags, and report output

  **What to do**:
  - Add `fabric-gametest` entrypoint class for base shared scenario providers.
  - Add Fabric run configuration flags (`-Dfabric-api.gametest`, report file path, optional filter support).
  - Ensure report output is CI-consumable (JUnit XML).

  **Must NOT do**:
  - Do not duplicate scenario assertions from shared layer.
  - Do not couple entrypoint to Forge classes.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: focused loader wrapper + run config changes.
  - **Skills**: [`beads`]
    - `beads`: maintains parity-tracking between Forge/Fabric wrappers.
  - **Skills Evaluated but Omitted**:
    - `playwright`: irrelevant to server-side runtime tests.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with 2,4,5)
  - **Blocks**: 13, 14, 18
  - **Blocked By**: 1

  **References**:
  - `base/fabric-1.20.1/build.gradle:28-49` - existing Fabric run/dependency context.
  - `base/fabric-1.20.1/src/main/resources/fabric.mod.json:10-13` - entrypoints map to extend.
  - Fabric docs sample `fabric-gametest` entrypoint + VM args pattern.

  **Acceptance Criteria**:
  - [x] Fabric gametest entrypoint compiles and loads.
  - [x] Fabric gametest run command supports enable flag + report output.
  - [x] Smoke scenario discovery works through shared provider mapping.

  **QA Scenarios**:
  ```
  Scenario: Fabric GameTest entrypoint discovery
    Tool: Bash (Gradle)
    Preconditions: Fabric entrypoint and run args added
    Steps:
      1. Run `./gradlew :base:fabric-1.20.1:runGametest -Dfabric-api.gametest.filter=smoke`
      2. Assert task output shows test discovery and execution summary
      3. Assert report file exists under configured build path
    Expected Result: Fabric wrapper discovers shared scenarios and writes report
    Failure Indicators: missing entrypoint / no tests discovered / no report file
    Evidence: .sisyphus/evidence/task-3-fabric-smoke.log

  Scenario: Missing gametest flag disables suite
    Tool: Bash (Gradle)
    Preconditions: run without `-Dfabric-api.gametest`
    Steps:
      1. Execute configured run without enable flag
      2. Assert tests are skipped or explicit disabled message appears
    Expected Result: flag gating behavior is deterministic and documented
    Evidence: .sisyphus/evidence/task-3-fabric-flag-disabled.log
  ```

  **Commit**: YES (group with 2)

- [x] 4. Replace high-risk source-string contracts with behavior integration tests

  **What to do**:
  - Convert selected `base/common` source-string regressions to behavior tests that execute code paths.
  - Cover proposal lifecycle and boundary rejection behavior via executable assertions.
  - Keep only minimal source-contract checks that cannot be behaviorally observed.

  **Must NOT do**:
  - Do not remove coverage before equivalent behavior tests are green.
  - Do not broaden into unrelated network/UI refactors.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: careful migration to avoid regression gaps.
  - **Skills**: [`beads`]
    - `beads`: tracks migration checklist and one-to-one coverage replacement.
  - **Skills Evaluated but Omitted**:
    - `oracle`: not required for straightforward test migration execution.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with 2,3,5)
  - **Blocks**: 16
  - **Blocked By**: None

  **References**:
  - `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/network/NetworkProposalLifecycleRegressionTest.java`
  - `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/network/NetworkAgentErrorRegressionTest.java`
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:1170-1230` - behavior target.

  **Acceptance Criteria**:
  - [x] Replacement behavior tests pass and cover original assertions.
  - [x] Removed source-string checks are either replaced or explicitly justified.
  - [x] Targeted suite run remains stable across two consecutive executions.

  **QA Scenarios**:
  ```
  Scenario: Proposal lifecycle behavior assertion
    Tool: Bash (Gradle)
    Preconditions: migrated behavior test class added
    Steps:
      1. Run `./gradlew :base:common-1.20.1:test --tests "*ProposalLifecycle*Behavior*"`
      2. Assert expected lifecycle transitions are verified by runtime object state
      3. Re-run same command once more to check stability
    Expected Result: two consecutive green runs
    Failure Indicators: transition mismatch or flaky second run
    Evidence: .sisyphus/evidence/task-4-proposal-behavior.log

  Scenario: Boundary rejection behavior
    Tool: Bash (Gradle)
    Preconditions: oversize payload behavior test exists
    Steps:
      1. Run `./gradlew :base:common-1.20.1:test --tests "*Boundary*Behavior*"`
      2. Assert oversize input yields boundary error contract
    Expected Result: deterministic rejection with stable error signal
    Evidence: .sisyphus/evidence/task-4-boundary-behavior-error.log
  ```

**Commit**: YES (standalone)

- [x] 5. Add async/indexing/thread deterministic orchestration utilities

  **What to do**:
  - Add helper utilities for tick-accurate sequencing (barrier/release, deterministic waits, bounded retries).
  - Provide reusable assertions for server-thread execution and non-blocking completion.
  - Cover helper behavior with focused tests.

  **Must NOT do**:
  - Do not use unbounded `Thread.sleep` in new tests.
  - Do not embed Forge/Fabric loader APIs in shared utility classes.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: async determinism directly affects GameTest flake rate.
  - **Skills**: [`beads`]
    - `beads`: tracks utility consumers and stabilization checklist.
  - **Skills Evaluated but Omitted**:
    - `git-master`: no history surgery needed for this task.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with 2,3,4)
  - **Blocks**: 7, 9, 17
  - **Blocked By**: 1

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java` - timeout/server-thread constraints.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/recipes/RecipeIndexService.java` - async index lifecycle.
  - `base/core/src/main/java/space/controlnet/chatmc/core/recipes/RecipeIndexManager.java:50-60` - async rebuild contract.

  **Acceptance Criteria**:
  - [x] Utility tests validate bounded wait/release and timeout error messaging.
  - [x] At least two downstream scenario tests use the new utilities.
  - [x] No raw sleep-based polling remains in migrated scenario tests.

  **QA Scenarios**:
  ```
  Scenario: Deterministic barrier release ordering
    Tool: Bash (Gradle)
    Preconditions: utility tests implemented
    Steps:
      1. Run `./gradlew :base:common-1.20.1:test --tests "*DeterministicBarrier*"`
      2. Assert order-sensitive assertions pass
    Expected Result: deterministic order guaranteed
    Failure Indicators: intermittent ordering mismatch
    Evidence: .sisyphus/evidence/task-5-barrier-order.log

  Scenario: Timeout path emits stable error
    Tool: Bash (Gradle)
    Preconditions: timeout helper test exists
    Steps:
      1. Run `./gradlew :base:common-1.20.1:test --tests "*Timeout*Utility*"`
      2. Assert timeout exception code/message matches contract
    Expected Result: stable timeout diagnostics
    Evidence: .sisyphus/evidence/task-5-timeout-error.log
  ```

  **Commit**: YES (group with 1)

- [x] 6. Implement GameTest: proposal binding unavailable failure path

  **What to do**:
  - Add runtime scenario where proposal is approved after bound terminal becomes unavailable.
  - Assert transition to failed path and error message contract.
  - Ensure no tool execution occurs after binding invalidation.

  **Must NOT do**:
  - Do not validate via source-string checks only.
  - Do not use AE-specific terminal dependencies in base scenario.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: requires real-world state manipulation and transition validation.
  - **Skills**: [`beads`]
    - `beads`: tracks scenario evidence and edge-case checklist.
  - **Skills Evaluated but Omitted**:
    - `playwright`: irrelevant to server runtime scenario.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with 7,8,9,10,11)
  - **Blocks**: 13, 17
  - **Blocked By**: 1, 2

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:1175-1185` - binding lookup + unavailable failure.
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/ServerSessionManager.java:281-306` - fail/resolve transition behavior.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/terminal/TerminalContextRegistry.java:31-36` - binding resolution entry.

  **Acceptance Criteria**:
  - [ ] Scenario fails execution when binding disappears and logs expected failure reason.
  - [ ] Session transitions to expected terminal state with no stale executing state.
  - [ ] Evidence includes scenario log + snapshot diff.

  **QA Scenarios**:
  ```
  Scenario: Approve after terminal removal
    Tool: Bash (Gradle GameTest)
    Preconditions: Forge GameTest harness active, scenario registered
    Steps:
      1. Run `./gradlew :base:forge-1.20.1:runGameTestServer --tests "*BindingUnavailable*"`
      2. During scenario, remove/invalidates bound terminal before approval handling
      3. Assert session records `bound terminal unavailable` and no tool payload appended
    Expected Result: deterministic failure, no execution side effects
    Failure Indicators: tool runs despite invalid binding or state remains EXECUTING
    Evidence: .sisyphus/evidence/task-6-binding-unavailable.log

  Scenario: Stale approval packet rejected
    Tool: Bash (Gradle GameTest)
    Preconditions: proposal invalidated before decision packet
    Steps:
      1. Inject stale approval decision
      2. Assert no state mutation beyond rejection path
    Expected Result: stale approval ignored/rejected without execution
    Evidence: .sisyphus/evidence/task-6-stale-approval-error.log
  ```

  **Commit**: YES (group with 7-10)

- [x] 7. Implement GameTest: INDEXING gate and recovery across reload

  **What to do**:
  - Create scenario that triggers indexing-not-ready gate then recovery after rebuild/reload.
  - Assert `INDEXING` is non-sticky and returns to actionable state.
  - Verify snapshot broadcasts reflect state transitions correctly.

  **Must NOT do**:
  - Do not rely solely on direct setter calls bypassing runtime flow.
  - Do not skip reload/rebuild trigger path.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: async lifecycle + state transition interactions.
  - **Skills**: [`beads`]
    - `beads`: records deterministic sequencing and evidence.
  - **Skills Evaluated but Omitted**:
    - `github-cli`: no external API operations.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with 6,8,9,10,11)
  - **Blocks**: 13, 17
  - **Blocked By**: 1, 2, 5

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:585-589` - thinking gate on indexing state.
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/SessionOrchestrator.java:162-167` - indexing state enforcement.
  - `base/core/src/main/java/space/controlnet/chatmc/core/recipes/RecipeIndexManager.java:50-60` - async rebuild contract.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/recipes/RecipeIndexReloadListener.java:28-31` - reload trigger.

  **Acceptance Criteria**:
  - [ ] During rebuild, request path is gated and state reports `INDEXING`.
  - [ ] After rebuild completion, state returns to non-indexing and request path opens.
  - [ ] No sticky `INDEXING` observed after second open/request cycle.

  **QA Scenarios**:
  ```
  Scenario: Indexing gate then recovery
    Tool: Bash (Gradle GameTest)
    Preconditions: indexing scenario with deterministic barrier
    Steps:
      1. Run `./gradlew :base:forge-1.20.1:runGameTestServer --tests "*IndexingGateRecovery*"`
      2. Assert gate active while rebuild pending
      3. Release rebuild barrier and assert recovery to actionable state
    Expected Result: non-sticky INDEXING with successful recovery
    Failure Indicators: state remains INDEXING after rebuild complete
    Evidence: .sisyphus/evidence/task-7-indexing-recovery.log

  Scenario: Reload during active indexing
    Tool: Bash (Gradle GameTest)
    Preconditions: reload triggered while indexing in progress
    Steps:
      1. Trigger reload mid-indexing
      2. Assert no deadlock and eventual stable ready state
    Expected Result: graceful recovery without stuck futures
    Evidence: .sisyphus/evidence/task-7-reload-mid-indexing-error.log
  ```

  **Commit**: YES (group with 6-10)

- [x] 8. Implement GameTest: multi-viewer snapshot consistency under churn

  **What to do**:
  - Add scenario with multiple viewers opening/closing the same session during updates.
  - Assert snapshot sequence consistency and viewer subscription cleanup.
  - Validate no duplicate/missing terminal snapshot pushes for active viewers.

  **Must NOT do**:
  - Do not use manual inspection only; enforce machine-checkable counters.
  - Do not tie assertions to UI rendering details.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: concurrency-heavy networking assertions with viewer churn.
  - **Skills**: [`beads`]
    - `beads`: tracks scenario matrix and evidence completeness.
  - **Skills Evaluated but Omitted**:
    - `playwright`: scenario is server/network state, not browser UI.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with 6,7,9,10,11)
  - **Blocks**: 13, 17
  - **Blocked By**: 1, 2

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:337-386` - terminal open/close + broadcast hooks.
  - `base/core/src/main/java/space/controlnet/chatmc/core/session/SessionOrchestrator.java:58-95` - viewer subscribe/unsubscribe model.
  - `base/core/src/main/java/space/controlnet/chatmc/core/net/s2c/S2CSessionSnapshotPacket.java:5` - snapshot payload boundary.

  **Acceptance Criteria**:
  - [ ] Viewer churn scenario passes with deterministic subscription counts.
  - [ ] Active viewers receive consistent snapshot updates (no duplicate sequence IDs).
  - [ ] Closed viewers are removed and receive no further updates.

  **QA Scenarios**:
  ```
  Scenario: Two-viewer churn consistency
    Tool: Bash (Gradle GameTest)
    Preconditions: two simulated server players and churn script
    Steps:
      1. Run `./gradlew :base:forge-1.20.1:runGameTestServer --tests "*ViewerChurnConsistency*"`
      2. Execute alternating open/close operations while session updates occur
      3. Assert monotonic snapshot sequence for each active viewer
    Expected Result: no duplicates/missing updates for active subscriptions
    Failure Indicators: stale subscribers, duplicate pushes, inconsistent sequence IDs
    Evidence: .sisyphus/evidence/task-8-viewer-churn.log

  Scenario: Closed viewer receives no updates
    Tool: Bash (Gradle GameTest)
    Preconditions: viewer unsubscribed before broadcast
    Steps:
      1. Close viewer terminal
      2. Trigger broadcast
      3. Assert viewer-specific update counter unchanged
    Expected Result: unsubscribe is effective immediately
    Evidence: .sisyphus/evidence/task-8-unsubscribe-error.log
  ```

**Commit**: YES (group with 6-10)

- [x] 9. Implement GameTest: server-thread confinement in tool execution flow

  **What to do**:
  - Add runtime scenario that invokes tool execution via async path and validates server-thread dispatch.
  - Assert timeout/failure mapping contracts remain stable when server thread is unavailable/delayed.
  - Capture thread identity and execution timing evidence.

  **Must NOT do**:
  - Do not assert only by source substring checks.
  - Do not bypass `AgentRunner`/`ToolRegistry` path with direct fake calls.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: concurrency and timeout correctness under runtime scheduling.
  - **Skills**: [`beads`]
    - `beads`: keep per-scenario evidence/flake history for thread tests.
  - **Skills Evaluated but Omitted**:
    - `oracle`: optional; execution task is concrete and bounded.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with 6,7,8,10,11)
  - **Blocks**: 13, 17
  - **Blocked By**: 1, 2, 5

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/agent/AgentRunner.java` - server execute + timeout contract.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/tools/ToolRegistry.java` - execution entry.
  - `base/common-1.20.1/src/test/java/space/controlnet/chatmc/common/agent/ThreadConfinementRegressionTest.java` - prior regression target.

  **Acceptance Criteria**:
  - [ ] Runtime scenario proves world/tool mutation executes on server thread.
  - [ ] Timeout path yields expected code/message contract under forced delay.
  - [ ] Scenario stable across two consecutive runs.

  **QA Scenarios**:
  ```
  Scenario: Async invocation marshals to server thread
    Tool: Bash (Gradle GameTest)
    Preconditions: thread-trace instrumentation in test harness
    Steps:
      1. Run `./gradlew :base:forge-1.20.1:runGameTestServer --tests "*ServerThreadConfinement*"`
      2. Trigger tool call from async context
      3. Assert execution thread marker equals server thread marker
    Expected Result: execution always marshaled to server thread
    Failure Indicators: off-thread execution detected
    Evidence: .sisyphus/evidence/task-9-thread-confinement.log

  Scenario: Forced timeout yields stable failure contract
    Tool: Bash (Gradle GameTest)
    Preconditions: delayed server-thread action beyond timeout threshold
    Steps:
      1. Trigger delayed execution
      2. Assert timeout signal and state transition match expected contract
    Expected Result: deterministic timeout handling
    Evidence: .sisyphus/evidence/task-9-timeout-contract-error.log
  ```

  **Commit**: YES (group with 6-10)

- [x] 10. Implement GameTest: tool-args 65536 boundary end-to-end

  **What to do**:
  - Add runtime scenarios for payload sizes 65535/65536/65537 across parse, network, and persistence paths.
  - Assert error codes/messages match boundary contracts.
  - Verify multi-byte UTF payload handling does not bypass boundary semantics.

  **Must NOT do**:
  - Do not test only one layer; must cover parse + network + persisted write path.
  - Do not use human/manual validation for payload outcomes.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: cross-layer boundary validation with encoding edge cases.
  - **Skills**: [`beads`]
    - `beads`: tracks scenario matrix and boundary corpus versions.
  - **Skills Evaluated but Omitted**:
    - `secret-guard`: no secret-audit scope in this task.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with 6,7,8,9,11)
  - **Blocks**: 13, 17
  - **Blocked By**: 1, 2

  **References**:
  - `base/core/src/main/java/space/controlnet/chatmc/core/agent/ToolCallArgsParseBoundary.java` - parse limit source.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java:63,668,727,763-767` - network read/write and boundary checks.
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/session/ChatMCSessionsSavedData.java:32,304-308` - persistence boundary checks.

  **Acceptance Criteria**:
  - [ ] 65536 accepted where contract says allowed.
  - [ ] 65537 rejected with expected boundary signal at each layer.
  - [ ] UTF-8 multibyte edge corpus results are deterministic and documented.

  **QA Scenarios**:
  ```
  Scenario: 65535/65536 accepted, 65537 rejected
    Tool: Bash (Gradle GameTest)
    Preconditions: boundary corpus fixtures available
    Steps:
      1. Run `./gradlew :base:forge-1.20.1:runGameTestServer --tests "*ToolArgsBoundaryE2E*"`
      2. Assert acceptance for <=65536 and rejection for 65537
      3. Assert rejection carries expected boundary error code/message
    Expected Result: strict and consistent boundary behavior
    Failure Indicators: inconsistent pass/fail across layers
    Evidence: .sisyphus/evidence/task-10-boundary-e2e.log

  Scenario: UTF multibyte edge payload handling
    Tool: Bash (Gradle GameTest)
    Preconditions: corpus includes emoji/surrogate edge strings
    Steps:
      1. Execute UTF edge suite
      2. Assert no decoder desync and contract-consistent boundary outcomes
    Expected Result: encoding-safe boundary enforcement
    Evidence: .sisyphus/evidence/task-10-utf-boundary-error.log
  ```

  **Commit**: YES (group with 6-10)

- [x] 11. Add behavior integration test for SavedData reload normalization

  **What to do**:
  - Add integration tests for persisted session reload from transient states (`THINKING/EXECUTING/WAIT_APPROVAL`).
  - Assert normalization and active-session mapping recovery behavior after load.
  - Ensure persisted decisions/messages remain intact.

  **Must NOT do**:
  - Do not simulate reload by direct setter shortcuts only.
  - Do not alter production state machine to satisfy test expectations.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: focused integration test additions around persisted contracts.
  - **Skills**: [`beads`]
    - `beads`: tracks migration from source contracts to behavior checks.
  - **Skills Evaluated but Omitted**:
    - `playwright`: irrelevant.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with 6,7,8,9,10)
  - **Blocks**: 16
  - **Blocked By**: 4

  **References**:
  - `base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/session/ChatMCSessionsSavedData.java` - save/load behavior.
  - `base/core/src/test/java/space/controlnet/chatmc/core/session/ServerSessionManagerIndexingRecoveryRegressionTest.java:45-62` - reload precedent.

  **Acceptance Criteria**:
  - [x] Reload tests validate transient normalization outcomes.
  - [x] Active session mapping and histories survive round-trip.
  - [x] Tests green in two consecutive runs.

  **QA Scenarios**:
  ```
  Scenario: Transient-state reload normalization
    Tool: Bash (Gradle)
    Preconditions: new reload integration tests added
    Steps:
      1. Run `./gradlew :base:common-1.20.1:test --tests "*SavedData*Reload*"`
      2. Assert transient states normalize per contract after load
    Expected Result: deterministic normalized state and preserved history
    Failure Indicators: stale transient states persist after reload
    Evidence: .sisyphus/evidence/task-11-saveddata-reload.log

  Scenario: Corrupt/oversize persisted tool args rejected
    Tool: Bash (Gradle)
    Preconditions: oversize fixture present
    Steps:
      1. Execute negative fixture reload test
      2. Assert boundary rejection and safe fallback behavior
    Expected Result: no crash and explicit rejection signal
    Evidence: .sisyphus/evidence/task-11-saveddata-boundary-error.log
  ```

  **Commit**: YES (group with 4)

- [x] 12. Implement Forge AE GameTest: craft job lifecycle request/status/cancel

  **What to do**:
  - Add AE runtime scenario for request craft, poll status, cancel job.
  - Assert stable state transitions and error outcomes when network/CPU unavailable.
  - Ensure scenario uses terminal binding path, not direct bypass helpers.

  **Must NOT do**:
  - Do not mock away AE runtime lifecycle entirely.
  - Do not couple scenario to Fabric-only APIs.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: AE runtime behavior has asynchronous and environment-specific complexity.
  - **Skills**: [`beads`]
    - `beads`: track AE-specific scenario evidence and known instability notes.
  - **Skills Evaluated but Omitted**:
    - `oracle`: analysis complete; implementation is concrete.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with 13,14,15)
  - **Blocks**: 14, 17
  - **Blocked By**: 1, 2

  **References**:
  - `ext-ae/common-1.20.1/src/main/java/space/controlnet/chatmc/ae/common/part/AiTerminalPartOperations.java:120-208` - request/status/cancel lifecycle.
  - `ext-ae/common-1.20.1/src/main/java/space/controlnet/chatmc/ae/common/part/AiTerminalPart.java:136-152` - terminal-host lifecycle entry.
  - `ext-ae/common-1.20.1/src/main/java/space/controlnet/chatmc/ae/common/terminal/AeTerminalContextResolver.java:44-79` - binding resolution behavior.

  **Acceptance Criteria**:
  - [ ] Forge AE GameTest covers success and failure branches (CPU unavailable/network unavailable).
  - [ ] Job lifecycle transitions are asserted with deterministic waits.
  - [ ] Reports/evidence capture full lifecycle traces.

  **QA Scenarios**:
  ```
  Scenario: AE craft lifecycle happy path
    Tool: Bash (Gradle GameTest)
    Preconditions: AE integration runtime available
    Steps:
      1. Run `./gradlew :ext-ae:forge-1.20.1:runGameTestServer --tests "*AeCraftLifecycle*"`
      2. Assert request returns jobId, status progresses, cancel transitions to canceled/done contract
    Expected Result: lifecycle contract holds without stuck jobs
    Failure Indicators: job never transitions or invalid state sequence
    Evidence: .sisyphus/evidence/task-12-ae-lifecycle.log

  Scenario: CPU unavailable branch
    Tool: Bash (Gradle GameTest)
    Preconditions: request with unavailable CPU name
    Steps:
      1. Trigger request with missing CPU
      2. Assert explicit `CPU unavailable` error behavior
    Expected Result: deterministic failure path and no orphan job
    Evidence: .sisyphus/evidence/task-12-ae-cpu-unavailable-error.log
  ```

**Commit**: YES (standalone)

- [x] 13. Add Fabric wrappers for shared base GameTest scenarios

  **What to do**:
  - Register shared base scenario providers via Fabric gametest entrypoint wrapper.
  - Ensure wrappers only perform registration/gating logic, no duplicated assertions.
  - Add smoke suite naming/filter conventions aligned with Forge scenario IDs.

  **Must NOT do**:
  - Do not re-implement base scenario assertions in Fabric wrapper code.
  - Do not couple wrapper to AE modules.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: thin registration layer and task wiring.
  - **Skills**: [`beads`]
    - `beads`: maintains parity checklist across loaders.
  - **Skills Evaluated but Omitted**:
    - `github-cli`: no external PR metadata flow required.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with 12,14,15)
  - **Blocks**: 18
  - **Blocked By**: 3, 6, 7, 8, 9, 10

  **References**:
  - `base/fabric-1.20.1/src/main/resources/fabric.mod.json:10-13` - entrypoint location.
  - Shared scenario contract from Task 1.
  - Fabric gametest property contract (`fabric-api.gametest`, report-file, filter).

  **Acceptance Criteria**:
  - [x] Fabric wrapper discovers shared base scenarios.
  - [x] Scenario IDs/naming parity with Forge is documented.
  - [x] Smoke run succeeds and writes report artifact.

  **QA Scenarios**:
  ```
  Scenario: Fabric wrapper discovers shared base suite
    Tool: Bash (Gradle)
    Preconditions: wrapper registration complete
    Steps:
      1. Run `./gradlew :base:fabric-1.20.1:runGametest -Dfabric-api.gametest.filter=base_smoke`
      2. Assert discovered tests include base shared scenario prefixes
    Expected Result: shared suite loaded via wrapper
    Failure Indicators: zero discovered tests / duplicate wrapper logic failures
    Evidence: .sisyphus/evidence/task-13-fabric-base-wrapper.log

  Scenario: Wrapper-only scope enforcement
    Tool: Bash (Grep)
    Preconditions: code changes complete
    Steps:
      1. Search wrapper files for duplicated assertion bodies from shared scenarios
      2. Assert wrappers only call registration/provider methods
    Expected Result: thin wrapper architecture preserved
    Evidence: .sisyphus/evidence/task-13-wrapper-scope-error.log
  ```

  **Commit**: YES (group with 14)

- [x] 14. Add Fabric wrappers for AE GameTest scenarios

  **What to do**:
  - Register AE lifecycle shared scenario providers for Fabric gametest entrypoint.
  - Add loader-specific environment gating and expected-skip handling for missing AE runtime.
  - Ensure report output contains explicit status for skipped vs failed scenarios.

  **Must NOT do**:
  - Do not silently skip failures as “pass”.
  - Do not embed Forge-only assumptions in Fabric AE wrappers.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: wrapper-level parity work with explicit skip/fail semantics.
  - **Skills**: [`beads`]
    - `beads`: tracks parity and known environment caveats.
  - **Skills Evaluated but Omitted**:
    - `oracle`: deep architecture review not required for wrapper implementation.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with 12,13,15)
  - **Blocks**: 18
  - **Blocked By**: 3, 12

  **References**:
  - `ext-ae/fabric-1.20.1/src/main/resources/fabric.mod.json:10-13` - extension entrypoint map.
  - `ext-ae/fabric-1.20.1/build.gradle:44-61` - AE Fabric runtime dependencies.
  - `ext-ae/common-1.20.1/src/main/java/space/controlnet/chatmc/ae/common/part/AiTerminalPartOperations.java:120-208` - lifecycle behavior to assert.

  **Acceptance Criteria**:
  - [x] Fabric AE wrappers register and discover AE lifecycle scenarios.
  - [x] Missing AE runtime reports explicit skip/blocked status (not hidden pass).
  - [x] Available AE runtime executes lifecycle assertions successfully.

  **QA Scenarios**:
  ```
  Scenario: Fabric AE lifecycle wrapper smoke
    Tool: Bash (Gradle)
    Preconditions: AE runtime available in fabric test env
    Steps:
      1. Run `./gradlew :ext-ae:fabric-1.20.1:runGametest -Dfabric-api.gametest.filter=ae_smoke`
      2. Assert AE lifecycle scenarios are discovered and executed
    Expected Result: wrapper parity with Forge scenario set
    Failure Indicators: no discovery or mismatched scenario IDs
    Evidence: .sisyphus/evidence/task-14-fabric-ae-wrapper.log

  Scenario: Missing AE dependency behavior
    Tool: Bash (Gradle)
    Preconditions: run in env without AE runtime
    Steps:
      1. Execute AE wrapper suite
      2. Assert explicit skipped/blocked diagnostics, no false-pass
    Expected Result: transparent dependency gating
    Evidence: .sisyphus/evidence/task-14-ae-missing-error.log
  ```

  **Commit**: YES (group with 13)

- [x] 15. Add CI lanes, report collection, and flake policy for layered testing

  **What to do**:
  - Define lane policy: PR (JUnit), Main (Forge GameTest), Nightly (Fabric + AE heavy).
  - Add report collection/parsing scripts for JUnit XML and GameTest outputs.
  - Document retry/quarantine policy for flaky GameTests with explicit thresholds.

  **Must NOT do**:
  - Do not require manual verification as acceptance gate.
  - Do not merge flaky suites into PR gate without stabilization rule.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: cross-lane orchestration, artifact policy, and reliability controls.
  - **Skills**: [`beads`]
    - `beads`: tracks rollout criteria and gate promotion state.
  - **Skills Evaluated but Omitted**:
    - `playwright`: no browser automation in CI policy task.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with 12,13,14)
  - **Blocks**: 17, 18
  - **Blocked By**: 2, 3, 12, 13, 14

  **References**:
  - `REPO.md:702-713` - existing evaluation protocol baseline.
  - `REPO.md:827-841` - documented lane intention and command expectations.
  - Module build files for task naming alignment (`base/forge-1.20.1/build.gradle`, `base/fabric-1.20.1/build.gradle`, `ext-ae/*/build.gradle`).

  **Acceptance Criteria**:
  - [x] CI lane definitions committed and reproducible via documented commands.
  - [x] Report parsing script validates failures/errors == 0 for gated lanes.
  - [x] Flake policy states promotion criteria (e.g., N consecutive green runs).

  **QA Scenarios**:
  ```
  Scenario: CI report parser enforces failure gates
    Tool: Bash (script + Gradle)
    Preconditions: sample report files generated
    Steps:
      1. Run lane command (or sample parser invocation)
      2. Parse junit/xml outputs
      3. Assert non-zero failures produce non-zero exit status
    Expected Result: hard gate on failed suites
    Failure Indicators: parser exits 0 despite failures
    Evidence: .sisyphus/evidence/task-15-report-gate.log

  Scenario: Flake quarantine policy trigger
    Tool: Bash
    Preconditions: simulate intermittent-fail marker
    Steps:
      1. Feed parser/policy script with mixed pass/fail history
      2. Assert suite is routed to quarantine lane and not PR gate
    Expected Result: deterministic policy outcome
    Evidence: .sisyphus/evidence/task-15-flake-policy-error.log
  ```

**Commit**: YES (standalone)

- [x] 16. Execute and stabilize full JUnit matrix (unit + integration)

  **What to do**:
  - Run full JUnit matrix for core/common/ext-ae common modules.
  - Fix deterministic test failures introduced by migration tasks.
  - Capture final pass reports as baseline evidence.

  **Must NOT do**:
  - Do not skip failing tests or mark unstable failures as pass.
  - Do not mix GameTest execution in this task.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: execution and triage on established JUnit suites.
  - **Skills**: [`beads`]
    - `beads`: maintains issue list for failing tests and resolutions.
  - **Skills Evaluated but Omitted**:
    - `git-master`: commit management not in scope of this task.

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 4
  - **Blocks**: F2
  - **Blocked By**: 4, 11

  **References**:
  - `base/core/build.gradle:19-24` - JUnit platform/test props.
  - `base/common-1.20.1/build.gradle:39-41` - common JUnit config.
  - `ext-ae/common-1.20.1/build.gradle:48-50` - ext-ae common JUnit config.

  **Acceptance Criteria**:
  - [x] JUnit matrix command passes cleanly.
  - [x] Reports exist for each module and failures/errors are zero.
  - [x] Evidence includes command logs and report summary.

  **QA Scenarios**:
  ```
  Scenario: Full JUnit matrix pass
    Tool: Bash (Gradle)
    Preconditions: Tasks 1-15 complete
    Steps:
      1. Run `./gradlew :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test`
      2. Assert output contains `BUILD SUCCESSFUL`
      3. Parse XML reports and assert failures=0, errors=0
    Expected Result: all JUnit suites pass
    Failure Indicators: non-zero failure/error counts or missing reports
    Evidence: .sisyphus/evidence/task-16-junit-matrix.log

  Scenario: Failure gate check
    Tool: Bash
    Preconditions: inject or replay known failure report fixture
    Steps:
      1. Run report parser on failing fixture
      2. Assert parser exits non-zero and prints failing suite
    Expected Result: strict failure gate behavior
    Evidence: .sisyphus/evidence/task-16-junit-gate-error.log
  ```

  **Commit**: NO

- [x] 17. Execute Forge GameTest suites (base + AE) and eliminate flake sources

  **What to do**:
  - Run Forge base and AE GameTest suites with report output enabled.
  - Triage failures into deterministic fixes vs environment dependency issues.
  - Enforce stabilization rule (consecutive green runs before gate promotion).

  **Must NOT do**:
  - Do not hide flaky tests by default skip without rationale.
  - Do not merge unstable suite into PR lane.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: runtime flake triage and deterministic hardening across async scenarios.
  - **Skills**: [`beads`]
    - `beads`: tracks flake taxonomy and stabilization progress.
  - **Skills Evaluated but Omitted**:
    - `oracle`: optional review, not execution dependency.

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 4
  - **Blocks**: F1, F2, F3, 18
  - **Blocked By**: 2, 5, 6, 7, 8, 9, 10, 12, 15

  **References**:
  - Forge task wiring from Task 2.
  - Base scenarios from Tasks 6-10 and AE scenario from Task 12.
  - `REPO.md:839-841` - command/report alignment expectation.

  **Acceptance Criteria**:
  - [ ] Forge base GameTest suite passes with zero failures/errors.
  - [ ] Forge AE GameTest suite passes (or explicit blocked dependency classification).
  - [ ] At least two consecutive green runs for promoted gate scenarios.

  **QA Scenarios**:
  ```
  Scenario: Forge base + AE gametest execution
    Tool: Bash (Gradle)
    Preconditions: tasks 2,6-10,12,15 complete
    Steps:
      1. Run `./gradlew :base:forge-1.20.1:runGameTestServer :ext-ae:forge-1.20.1:runGameTestServer`
      2. Parse reports and assert failures=0, errors=0
      3. Re-run once more for stability confirmation
    Expected Result: two consecutive green runs
    Failure Indicators: intermittent failures between run1/run2
    Evidence: .sisyphus/evidence/task-17-forge-gametest.log

  Scenario: Flake classification output
    Tool: Bash
    Preconditions: one known intermittent scenario fixture
    Steps:
      1. Run flake classifier script/report pass
      2. Assert output tags cause as deterministic bug vs environment issue
    Expected Result: actionable classification with next-step labels
    Evidence: .sisyphus/evidence/task-17-flake-classification-error.log
  ```

  **Commit**: NO

- [x] 18. Execute Fabric GameTest suites and produce parity diff report

  **What to do**:
  - Run Fabric base/AE GameTest wrappers for shared scenarios.
  - Compare scenario outcomes and IDs against Forge results.
  - Produce parity report classifying: identical / expected loader variance / regression.

  **Must NOT do**:
  - Do not treat skipped suites as pass without classification.
  - Do not change scenario semantics only to force superficial parity.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: cross-loader parity analysis and report synthesis.
  - **Skills**: [`beads`]
    - `beads`: tracks parity table and unresolved deltas.
  - **Skills Evaluated but Omitted**:
    - `github-cli`: no remote API need.

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 4
  - **Blocks**: F1, F3, F4
  - **Blocked By**: 3, 13, 14, 15, 17

  **References**:
  - Fabric wrappers from Tasks 13-14.
  - Forge baseline evidence from Task 17.
  - `base/fabric-1.20.1/src/main/resources/fabric.mod.json:10-13` and `ext-ae/fabric-1.20.1/src/main/resources/fabric.mod.json:10-13`.

  **Acceptance Criteria**:
  - [x] Fabric base GameTest suite executes and reports results.
  - [x] Fabric AE suite executes or clearly reports blocked dependency conditions.
  - [x] Parity diff report maps every shared scenario to status + rationale.

  **QA Scenarios**:
  ```
  Scenario: Fabric parity execution
    Tool: Bash (Gradle)
    Preconditions: tasks 3,13,14,15,17 complete
    Steps:
      1. Run `./gradlew :base:fabric-1.20.1:runGametest :ext-ae:fabric-1.20.1:runGametest`
      2. Parse reports and collect scenario-level statuses
      3. Compare with Forge scenario status table
    Expected Result: parity report with explicit variance classification
    Failure Indicators: unclassified mismatch or missing scenario rows
    Evidence: .sisyphus/evidence/task-18-fabric-parity.log

  Scenario: Missing-report hard fail
    Tool: Bash
    Preconditions: remove/rename expected report path in test env
    Steps:
      1. Execute parity pipeline
      2. Assert pipeline fails with explicit missing-report diagnostic
    Expected Result: hard fail on incomplete evidence chain
    Evidence: .sisyphus/evidence/task-18-report-missing-error.log
  ```

  **Commit**: NO

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

- [x] F1. **Plan Compliance Audit** — `oracle`
  - Validate each Must Have deliverable against actual files, commands, and report artifacts.
  - Confirm Must NOT Have violations are absent.
  - Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT`

- [x] F2. **Code + Test Quality Review** — `unspecified-high`
  - Run build/lint/test quality checks for touched modules.
  - Inspect for flaky patterns (implicit sleeps, non-deterministic waits, hidden global state coupling).
  - Output: `Build/Lint/Test summary | Flake risks | VERDICT`

- [x] F3. **Scenario Replay Validation** — `unspecified-high`
  - Re-run representative GameTest scenarios with evidence capture and deterministic assertions.
  - Ensure reports and evidence files are complete and parseable.
  - Output: `Scenario pass ratio | Evidence completeness | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  - Verify changes stay in testing infra/scenario scope.
  - Flag any unintended functional feature expansion.
  - Output: `Scope CLEAN/ISSUES | Unaccounted changes | VERDICT`

---

## Commit Strategy

- **Commit 1**: `test(core): add shared scenario contracts and deterministic helpers`
- **Commit 2**: `build(test): wire forge and fabric gametest harnesses`
- **Commit 3**: `test(base): add runtime gametests for binding/indexing/viewers/thread/boundary`
- **Commit 4**: `test(ae): add ae lifecycle gametests and fabric wrappers`
- **Commit 5**: `ci(test): add layered lanes and report collection policy`

---

## Success Criteria

### Verification Commands
```bash
./gradlew :base:core:test :base:common-1.20.1:test :ext-ae:common-1.20.1:test
# Expected: BUILD SUCCESSFUL

./gradlew :base:forge-1.20.1:runGameTestServer
# Expected: exit 0 and junit/game test reports generated with 0 failures

./gradlew :ext-ae:forge-1.20.1:runGameTestServer
# Expected: exit 0 and AE lifecycle scenarios pass

./gradlew :base:fabric-1.20.1:runGametest :ext-ae:fabric-1.20.1:runGametest
# Expected: shared scenario wrappers discovered and pass (or explicit known-skips documented)
```

### Final Checklist
- [x] Unit/Integration/GameTest layers all active and green
- [x] Runtime-only invariants moved off source-string-only assertions
- [x] Forge/Fabric shared logic + thin wrappers proven
- [x] CI lane policy documented and reproducible
- [x] Evidence artifacts present under `.sisyphus/evidence/`
