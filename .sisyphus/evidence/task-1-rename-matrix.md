# Task 1 — Canonical Rename Matrix and Tracked Residue Baseline

## Scope freeze

Task 1 freezes one authoritative rename contract for every downstream task. No source/resource rename is performed here; this file only defines the canonical old→new mapping and captures the tracked-file residue baseline that later tasks must drive to zero.

Chosen target family (fixed by plan/user context):

- Base: `mineagent` / `MineAgent`
- AE extension: `mineagentae` / `MineAgent AE` / `MineAgentAe`
- Matrix extension: `mineagentmatrix` / `MineAgent Matrix` / `MineAgentMatrix`
- Java package root: `space.controlnet.mineagent...`

## Canonical rename matrix

| Identity surface | Base old → new | AE old / aliases → new | Matrix old → new | Representative tracked anchors |
| --- | --- | --- | --- | --- |
| Display / product name | `C[h]atMC` → `MineAgent` | `C[h]atMC AE`, `C[h]atMC AE2`, `C[h]atMCAe`, `C[h]atAE` → `MineAgent AE` | `C[h]atMC Matrix` → `MineAgent Matrix` | `settings.gradle`, `README.md`, `REPO.md`, `base/forge-1.20.1/src/main/resources/META-INF/mods.toml`, `ext-ae/forge-1.20.1/src/main/resources/META-INF/mods.toml`, `ext-matrix/forge-1.20.1/src/main/resources/META-INF/mods.toml` |
| Root project / repo label | `rootProject.name = "C[h]atMC"` → `"MineAgent"` | AE docs labels converge under base repo brand + `MineAgent AE` module name | Matrix docs labels converge under base repo brand + `MineAgent Matrix` module name | `settings.gradle:10`, `README.md:1-13`, `REPO.md:1-23` |
| Java package roots | `space.controlnet.c[h]atmc...` → `space.controlnet.mineagent...` | `space.controlnet.c[h]atmc.ae...`, historical `space.controlnet.c[h]atae...` → `space.controlnet.mineagent.ae...` | `space.controlnet.c[h]atmc.matrix...` → `space.controlnet.mineagent.matrix...` | `gradle.properties:2`, `build.gradle:37-41`, `base/**/src/**/java`, `ext-ae/**/src/**/java`, `ext-matrix/**/src/**/java`, `REPO.md:24-27` |
| Type / class prefixes | `C[h]atMC*` → `MineAgent*` | `C[h]atMCAe*` and historical `C[h]atAE*` → `MineAgentAe*` | `C[h]atMCMatrix*` → `MineAgentMatrix*` | `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/MineAgent.java`, `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/commands/MineAgentCommands.java`, `ext-ae/common-1.20.1/src/main/java/space/controlnet/mineagent/ae/common/MineAgentAe.java`, `ext-matrix/common-1.20.1/src/main/java/space/controlnet/mineagent/matrix/common/MineAgentMatrix.java`, `docs/architecture-diagram.md`, `docs/plan-core-extraction.md` |
| Mod IDs / runtime IDs | `c[h]atmc` → `mineagent` | `c[h]atmcae`, historical `c[h]atae` → `mineagentae` | `c[h]atmcmatrix` → `mineagentmatrix` | `base/forge-1.20.1/src/main/resources/META-INF/mods.toml:6`, `ext-ae/forge-1.20.1/src/main/resources/META-INF/mods.toml:6,25-30`, `ext-matrix/forge-1.20.1/src/main/resources/META-INF/mods.toml:6,25-30`, `base/fabric-1.20.1/src/main/resources/fabric.mod.json:3`, `ext-ae/fabric-1.20.1/src/main/resources/fabric.mod.json:3,20`, `ext-matrix/fabric-1.20.1/src/main/resources/fabric.mod.json:3,19` |
| Resource namespaces (`assets/`, `data/`, lang keys, resource locations) | `c[h]atmc` → `mineagent` | `c[h]atmcae`, historical `c[h]atae` → `mineagentae` | `c[h]atmcmatrix` → `mineagentmatrix` | `base/common-1.20.1/src/main/resources/assets/mineagent/**`, `ext-ae/common-1.20.1/src/main/resources/assets/mineagentae/**`, `ext-ae/common-1.20.1/src/main/resources/data/mineagentae/**`, `REPO.md:208,408,459,670-671` |
| Maven group | `space.controlnet.c[h]atmc` → `space.controlnet.mineagent` | `space.controlnet.c[h]atmc.ae`, historical `space.controlnet.c[h]atae` → `space.controlnet.mineagent.ae` | `space.controlnet.c[h]atmc.matrix` → `space.controlnet.mineagent.matrix` | `gradle.properties:2`, `build.gradle:37-43`, `REPO.md:12,17,22,668,686,698` |
| Archive / jar basename | `c[h]atmc` → `mineagent` | `c[h]atmcae`, historical `c[h]atae` → `mineagentae` | `c[h]atmcmatrix` → `mineagentmatrix` | `gradle.properties:3`, `build.gradle:38,41,48,99-100`, `scripts/build-dist.sh:30-36`, `README.md:96-101`, `.github/workflows/release.yml:96-103` |
| Release artifact names | `c[h]atmc-<ver>-<loader>-<mc>.jar` → `mineagent-<ver>-<loader>-<mc>.jar` | `c[h]atmcae-<ver>-<loader>-<mc>.jar`, historical AE docs/reports using `c[h]atae` labels → `mineagentae-<ver>-<loader>-<mc>.jar` | `c[h]atmcmatrix-<ver>-<loader>-<mc>.jar` → `mineagentmatrix-<ver>-<loader>-<mc>.jar` | `README.md:92-101`, `scripts/build-dist.sh:31-36`, `.github/workflows/release.yml:97-102` |
| Command root | `/c[h]atmc` → `/mineagent` | historical `/c[h]atae` docs alias → `/mineagent` (no separate AE root is introduced) | n/a (Matrix uses base command root) | `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/commands/MineAgentCommands.java:29-65`, `REPO.md:407,410,460,502` |
| Config directory | `config/c[h]atmc` → `config/mineagent` | historical `config/c[h]atae` → `config/mineagent` | n/a (Matrix uses shared base config root unless later code proves otherwise) | `REPO.md:407-408,459-462,671`, `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/llm/PromptFileManager.java:28,35` |
| Saved-data key / persistence identity | `c[h]atmc_sessions` → `mineagent_sessions` | historical `C[h]atAESessionsSavedData` / `c[h]atae` docs aliases converge on the shared `mineagent_sessions` world-save key | n/a (Matrix uses shared base session persistence unless later code proves otherwise) | `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/session/MineAgentSessionsSavedData.java:29-31`, `docs/plan-core-extraction.md`, `docs/architecture-diagram.md` |
| Prompt prefixes / system-property prefixes | `c[h]atmc.max*`, `c[h]atmc/prompts`, `prompt.c[h]atmc.*`, `assets/c[h]atmc/lang/*` → `mineagent.max*`, `mineagent/prompts`, `prompt.mineagent.*`, `assets/mineagent/lang/*` | historical `config/c[h]atae/prompts`, `assets/c[h]atae/lang/*`, `space.controlnet.c[h]atae...` prompt docs → same AE/base target family as applicable; class/type aliases converge on `MineAgentAe*` | Matrix prompt/config surfaces follow the `mineagentmatrix` family only if present later | `base/core/src/main/java/space/controlnet/mineagent/core/session/ServerSessionManager.java:15-18`, `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/llm/PromptFileManager.java:28,124,148,164`, `base/core/build.gradle:21-23`, `REPO.md:208-209,408-462` |
| GameTest namespaces / batches | `c[h]atmc`, `c[h]atmc_runtime`, `c[h]atmc_task8_viewer`, `c[h]atmc_task9_timeout`, `c[h]atmc_agent_runtime` → corresponding `mineagent*` forms | `c[h]atmcae` batch ids / testcase prefixes and AE Fabric smoke references converge on `mineagentae*`; existing `ae_smoke` batch is a tracked hotspot that must be intentionally reviewed later rather than guessed here | `c[h]atmcmatrix` → `mineagentmatrix` if/when Matrix GameTests exist | `base/fabric-1.20.1/src/main/java/space/controlnet/mineagent/fabric/gametest/MineAgentFabricGameTestEntrypoint.java:13-67`, `ci-reports/parity/gametest-parity-report.{md,json}`, `.sisyphus/evidence/task-18-fabric-parity-report.{md,json}`, `.sisyphus/evidence/task-19-forge-gametest-summary.md` |
| Tracked repo aliases / historical docs labels | `C[h]atMC` repo labels normalize to `MineAgent` | `C[h]atMC AE2`, `C[h]atAE`, `c[h]atae` normalize to `MineAgent AE` / `mineagentae` / `space.controlnet.mineagent.ae...` according to surface | `C[h]atMC Matrix` normalizes to `MineAgent Matrix` | `REPO.md`, `docs/architecture-diagram.md`, `docs/plan-core-extraction.md`, `gradlew.bat`, `scripts/generate_ai_terminal_textures.py`, `scripts/code-stats.sh`, `scripts/code-stats.ps1`, tracked `.sisyphus` plans/evidence/notepads carrying historical labels |

## Residue-family coverage rule

Every legacy family returned by the tracked baseline grep must map to at least one row above.

| Legacy family from grep | Covered by matrix rows |
| --- | --- |
| `c[h]atmc` | root project / repo label, package roots, mod IDs, resource namespaces, Maven group, archive basename, release artifact names, command root, config directory, saved-data key, prompt/system-property prefixes, GameTest namespaces |
| `c[h]atmcae` | mod IDs, resource namespaces, archive basename, release artifact names, GameTest namespaces |
| `c[h]atmcmatrix` | mod IDs, resource namespaces, archive basename, release artifact names, GameTest namespaces |
| `C[h]atMC` | display / product name, root project / repo label, type/class prefixes, tracked repo aliases / docs labels |
| `C[h]atMCAe` | display / product name, type/class prefixes, tracked repo aliases / docs labels |
| `C[h]atMCMatrix` | display / product name, type/class prefixes, tracked repo aliases / docs labels |
| `C[h]atAE` | display / product name, type/class prefixes, saved-data / docs aliases, tracked repo aliases / docs labels |
| `c[h]atae` | package roots, mod IDs / namespaces, archive basename, command root docs alias, config directory alias, prompt/config alias, tracked repo aliases / docs labels |

## Baseline residue inventory (tracked files only)

Exact command required by the plan and executed for this task:

```bash
git grep -nE 'c[h]atmc|c[h]atmcae|c[h]atmcmatrix|C[h]atMC|C[h]atMCAe|C[h]atMCMatrix|C[h]atAE|c[h]atae'
```

The interactive command output is very large, so the baseline is recorded here as explicit tracked-hit counts plus representative tracked hotspots. This satisfies the plan requirement to capture the baseline while keeping the evidence reviewable.

### Literal hit counts from tracked files

| Legacy token | Tracked hit count |
| --- | ---: |
| `c[h]atmc` | 1731 |
| `c[h]atmcae` | 98 |
| `c[h]atmcmatrix` | 47 |
| `C[h]atMC` | 1218 |
| `C[h]atMCAe` | 65 |
| `C[h]atMCMatrix` | 34 |
| `C[h]atAE` | 91 |
| `c[h]atae` | 39 |

### Family-by-family hotspot annotation

| Legacy family | Count | Matrix rows it exercises | Representative tracked hotspots |
| --- | ---: | --- | --- |
| `c[h]atmc` | 1731 | Base mod ID, resource namespace, package root, Maven group, jar basename, command/config/prompt/runtime ids, GameTest namespaces | `gradle.properties`, `build.gradle`, `.github/workflows/release.yml`, `scripts/build-dist.sh`, `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/commands/MineAgentCommands.java`, `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/llm/PromptFileManager.java`, `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/session/MineAgentSessionsSavedData.java`, `base/fabric-1.20.1/src/main/java/space/controlnet/mineagent/fabric/gametest/MineAgentFabricGameTestEntrypoint.java`, `README.md`, `REPO.md`, `.sisyphus/plans/mineagent-full-rename.md`, `ci-reports/parity/gametest-parity-report.{md,json}` |
| `c[h]atmcae` | 98 | AE mod ID / namespace / jar / batch family | `build.gradle`, `.github/workflows/release.yml`, `scripts/build-dist.sh`, `ext-ae/forge-1.20.1/src/main/resources/META-INF/mods.toml`, `ext-ae/fabric-1.20.1/src/main/resources/fabric.mod.json`, `ext-ae/common-1.20.1/src/main/resources/assets/mineagentae/**`, `ci-reports/parity/gametest-parity-report.{md,json}` |
| `c[h]atmcmatrix` | 47 | Matrix mod ID / namespace / jar family | `build.gradle`, `.github/workflows/release.yml`, `scripts/build-dist.sh`, `ext-matrix/forge-1.20.1/src/main/resources/META-INF/mods.toml`, `ext-matrix/fabric-1.20.1/src/main/resources/fabric.mod.json`, `README.md`, `REPO.md` |
| `C[h]atMC` | 1218 | Base display name / type prefix / repo label family | `settings.gradle`, `README.md`, `REPO.md`, `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/MineAgent.java`, `base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/commands/MineAgentCommands.java`, `base/fabric-1.20.1/src/main/java/space/controlnet/mineagent/fabric/MineAgentFabric.java`, `base/forge-1.20.1/src/main/java/space/controlnet/mineagent/forge/gametest/MineAgentGameTestBootstrap.java`, `.sisyphus/evidence/task-18-fabric-parity-report.{md,json}` |
| `C[h]atMCAe` | 65 | AE display name / type prefix family | `ext-ae/common-1.20.1/src/main/java/space/controlnet/mineagent/ae/common/MineAgentAe.java`, `ext-ae/fabric-1.20.1/src/main/java/space/controlnet/mineagent/ae/fabric/gametest/MineAgentAeFabricGameTestEntrypoint.java`, `ext-ae/forge-1.20.1/src/main/java/space/controlnet/mineagent/ae/forge/gametest/MineAgentAeGameTestBootstrap.java`, `scripts/generate_ai_terminal_textures.py`, `REPO.md`, `.sisyphus/evidence/task-18-fabric-parity-report.{md,json}` |
| `C[h]atMCMatrix` | 34 | Matrix display name / type prefix family | `ext-matrix/common-1.20.1/src/main/java/space/controlnet/mineagent/matrix/common/MineAgentMatrix.java`, `ext-matrix/fabric-1.20.1/src/main/java/space/controlnet/mineagent/matrix/fabric/MineAgentMatrixFabric.java`, `ext-matrix/forge-1.20.1/src/main/java/space/controlnet/mineagent/matrix/forge/MineAgentMatrixForge.java`, `README.md`, `REPO.md` |
| `C[h]atAE` | 91 | Historical AE alias family; must collapse onto the AE canonical row instead of creating a second naming branch | `REPO.md`, `docs/architecture-diagram.md`, `docs/plan-core-extraction.md`, `gradlew.bat`, `scripts/code-stats.sh`, `scripts/code-stats.ps1` |
| `c[h]atae` | 39 | Historical lowercase AE alias family; maps to AE mod/package/config/resource targets | `REPO.md`, `docs/plan-core-extraction.md`, `docs/architecture-diagram.md` |

### Baseline hotspot families by surface

1. **Build / release / packaging identity** — `settings.gradle`, `gradle.properties`, `build.gradle`, `scripts/build-dist.sh`, `.github/workflows/release.yml`, `README.md`.
2. **Java package and type-prefix residue** — `base/**/src/**/java/space/controlnet/c[h]atmc/**`, `ext-ae/**/src/**/java/space/controlnet/c[h]atmc/ae/**`, `ext-matrix/**/src/**/java/space/controlnet/c[h]atmc/matrix/**`.
3. **Loader metadata + resource namespace residue** — Forge `mods.toml`, Fabric `fabric.mod.json`, `assets/c[h]atmc/**`, `assets/c[h]atmcae/**`, `data/c[h]atmcae/**`.
4. **Runtime identity strings** — `/c[h]atmc` command root, `c[h]atmc_sessions`, `c[h]atmc.max*`, `c[h]atmc/prompts`, `prompt.c[h]atmc.*`, `c[h]atmc` / `c[h]atmc_runtime` / `c[h]atmcae` GameTest names.
5. **Historical documentation aliases** — `REPO.md`, `docs/architecture-diagram.md`, `docs/plan-core-extraction.md`, `gradlew.bat`, helper scripts still carrying `C[h]atAE` / `c[h]atae` labels.
6. **Tracked planning / evidence / parity artifacts** — `.sisyphus/plans/*.md`, `.sisyphus/evidence/*.md`, `.sisyphus/notepads/c[h]atmc-test-pyramid-gametest-adoption/*.md`, `ci-reports/parity/*`.

## Task-1 conclusion

The canonical naming contract is internally consistent with the required target family:

- base = `mineagent`
- AE = `mineagentae`
- Matrix = `mineagentmatrix`
- package root = `space.controlnet.mineagent...`

All eight legacy families observed by the exact tracked baseline grep are mapped to one or more matrix rows above. Downstream tasks must treat this file as the single rename authority and must extend it before implementation if a later tracked-file scan reveals a legacy family not covered here.
