# Task 1 — Unmapped Legacy Family Failure Check

## Failure rule

Task 1 fails immediately if the tracked baseline grep returns any legacy identifier family that does not map cleanly to the canonical rename matrix in `.sisyphus/evidence/task-1-rename-matrix.md`.

Exact comparison command:

```bash
git grep -nE 'c[h]atmc|c[h]atmcae|c[h]atmcmatrix|C[h]atMC|C[h]atMCAe|C[h]atMCMatrix|C[h]atAE|c[h]atae'
```

## Current comparison result

Current observed families from the tracked baseline:

- `c[h]atmc`
- `c[h]atmcae`
- `c[h]atmcmatrix`
- `C[h]atMC`
- `C[h]atMCAe`
- `C[h]atMCMatrix`
- `C[h]atAE`
- `c[h]atae`

Current status: **PASS / no unmapped family detected**.

Each observed family is mapped as follows:

| Legacy family | Canonical target family | Matrix coverage status |
| --- | --- | --- |
| `c[h]atmc` | `mineagent` / `MineAgent` / `space.controlnet.mineagent...` | mapped |
| `c[h]atmcae` | `mineagentae` / `MineAgent AE` / `space.controlnet.mineagent.ae...` | mapped |
| `c[h]atmcmatrix` | `mineagentmatrix` / `MineAgent Matrix` / `space.controlnet.mineagent.matrix...` | mapped |
| `C[h]atMC` | `MineAgent*` base display/type family | mapped |
| `C[h]atMCAe` | `MineAgentAe*` AE display/type family | mapped |
| `C[h]atMCMatrix` | `MineAgentMatrix*` Matrix display/type family | mapped |
| `C[h]atAE` | AE historical alias collapsed into the `MineAgent AE` / `MineAgentAe*` family | mapped |
| `c[h]atae` | AE historical lowercase alias collapsed into the `mineagentae` / `space.controlnet.mineagent.ae...` family | mapped |

## How this fails later

The task must be treated as failed, and the matrix must be extended before any rename implementation proceeds, if **either** of the following happens on a future rerun:

1. A new legacy family appears in tracked files and is not covered by an existing matrix row.
2. An existing family appears on a surface where the target is ambiguous or inconsistent with the frozen target family (`mineagent`, `mineagentae`, `mineagentmatrix`, `space.controlnet.mineagent...`).

## Required remediation on failure

1. Re-run the exact tracked grep above.
2. Extract the unmatched family (for example, a new case variant, alias, docs label, artifact prefix, or runtime key).
3. Add a new canonical row or widen an existing row in `task-1-rename-matrix.md` so the target is explicit.
4. Re-run the comparison until every returned legacy family is mapped without ambiguity.

Downstream rename tasks are blocked until that check passes.
