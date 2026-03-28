# F3 Scenario Replay Validation

## Scenario pass ratio
- Passed scenarios: 2
- Total scenarios assessed: 3
- Ratio: 2/3

Assessment by replay evidence:
- Base Fabric replay: pass (`BUILD SUCCESSFUL`, `EXIT_CODE=0`) from `.sisyphus/evidence/f3-base-fabric-run.log`
- Ext AE Fabric replay: pass (`BUILD SUCCESSFUL`, `EXIT_CODE=0`) from `.sisyphus/evidence/f3-ext-ae-fabric-run.log`
- Base Forge replay: blocked, not pass (`EXIT_CODE=124`, startup blocker signatures) from `.sisyphus/evidence/f3-base-forge-run.log`

## Evidence completeness assessment
- Required evidence files present: 4
- Required evidence files expected: 4
- Completeness ratio: 4/4
- Completeness status: PASS

Required evidence citations:
- `.sisyphus/evidence/f3-base-fabric-run.log`
- `.sisyphus/evidence/f3-ext-ae-fabric-run.log`
- `.sisyphus/evidence/f3-base-forge-run.log`
- `.sisyphus/evidence/f3-evidence-parse.log`

Supporting parity corroboration:
- `ci-reports/parity/gametest-parity-report.md` confirms Forge runtime blocked (`Forge runtime execution is blocked`, `Forge runtime blocked in workspace: true`).

Scenario pass ratio | Evidence completeness | VERDICT
2/3 | 4/4 | FAIL (Forge replay blocked)
