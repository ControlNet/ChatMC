#!/usr/bin/env python3
"""Generate Forge-vs-Fabric GameTest parity report for Task 18."""

from __future__ import annotations

import argparse
import json
import re
import xml.etree.ElementTree as et
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, cast


@dataclass(frozen=True)
class ForgeScenario:
    module: str
    class_name: str
    method_name: str
    batch: str | None

    @property
    def scenario_id(self) -> str:
        return f"{self.class_name}::{self.method_name}"


@dataclass(frozen=True)
class FabricWrapper:
    module: str
    class_name: str
    method_name: str
    batch: str | None
    mapped_forge_class: str | None

    @property
    def wrapper_id(self) -> str:
        return f"{self.class_name}::{self.method_name}"


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _tokenize(name: str) -> set[str]:
    spaced = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", name)
    chunks = re.split(r"[^A-Za-z0-9]+", spaced)
    return {c.lower() for c in chunks if c}


def _jaccard(left: set[str], right: set[str]) -> float:
    if not left and not right:
        return 1.0
    if not left or not right:
        return 0.0
    overlap = left & right
    union = left | right
    return len(overlap) / len(union)


def _parse_fabric_xml(xml_path: Path, module: str) -> list[dict[str, object]]:
    root = et.parse(xml_path).getroot()
    parsed: list[dict[str, object]] = []
    for case in root.iter():
        if _local_name(case.tag) != "testcase":
            continue
        children = [_local_name(child.tag) for child in list(case)]
        status = "passed"
        if "failure" in children:
            status = "failed"
        elif "error" in children:
            status = "error"
        elif "skipped" in children:
            status = "skipped"

        name = case.attrib.get("name", "")
        method_name = name.rsplit(".", 1)[-1] if "." in name else name
        parsed.append(
            {
                "module": module,
                "classname": case.attrib.get("classname", ""),
                "name": name,
                "method_lc": method_name.lower(),
                "time": float(case.attrib.get("time", "0") or 0),
                "status": status,
            }
        )
    return parsed


def _parse_forge_scenarios(gametest_dir: Path, module: str) -> list[ForgeScenario]:
    scenarios: list[ForgeScenario] = []
    method_pattern = re.compile(
        r"@GameTest\((?P<anno>.*?)\)\s*public\s+static\s+void\s+(?P<method>\w+)\s*\(",
        re.DOTALL,
    )
    batch_pattern = re.compile(r'batch\s*=\s*"(?P<batch>[^"]+)"')

    for java_path in sorted(gametest_dir.glob("*.java")):
        if "Bootstrap" in java_path.name:
            continue
        text = java_path.read_text(encoding="utf-8")
        class_name = java_path.stem
        for match in method_pattern.finditer(text):
            anno = match.group("anno")
            batch_match = batch_pattern.search(anno)
            scenarios.append(
                ForgeScenario(
                    module=module,
                    class_name=class_name,
                    method_name=match.group("method"),
                    batch=batch_match.group("batch") if batch_match else None,
                )
            )
    return scenarios


def _parse_fabric_wrappers(
    java_paths: Iterable[Path], module: str
) -> list[FabricWrapper]:
    wrappers: list[FabricWrapper] = []
    method_pattern = re.compile(r"public\s+static\s+void\s+(?P<method>\w+)\s*\(")
    batch_pattern = re.compile(r'batch\s*=\s*"(?P<batch>[^"]+)"')
    direct_map_pattern = re.compile(r"Forge scenario:\s*(?P<class_name>\w+)")
    mirror_map_pattern = re.compile(r"Mirrors\s+(?P<class_name>\w+)")

    for java_path in sorted(java_paths):
        lines = java_path.read_text(encoding="utf-8").splitlines()
        class_name = java_path.stem
        for idx, line in enumerate(lines):
            if "@GameTest(" not in line:
                continue

            method_name = None
            method_line_idx = None
            for scan in range(idx + 1, min(idx + 5, len(lines))):
                method_match = method_pattern.search(lines[scan])
                if method_match:
                    method_name = method_match.group("method")
                    method_line_idx = scan
                    break
            if method_name is None:
                continue

            assert method_line_idx is not None
            method_end_idx = method_line_idx
            brace_depth = 0
            seen_open_brace = False
            for pointer in range(method_line_idx, len(lines)):
                line_text = lines[pointer]
                open_count = line_text.count("{")
                close_count = line_text.count("}")
                if open_count > 0:
                    seen_open_brace = True
                brace_depth += open_count
                brace_depth -= close_count
                method_end_idx = pointer
                if seen_open_brace and brace_depth <= 0:
                    break

            mapped_forge_class = None
            comment_window_start = max(0, idx - 4)
            comment_window_end = min(len(lines), method_end_idx + 1)
            for pointer in range(comment_window_start, comment_window_end):
                comment_line = lines[pointer]
                direct = direct_map_pattern.search(comment_line)
                if direct:
                    mapped_forge_class = direct.group("class_name")
                mirror = mirror_map_pattern.search(comment_line)
                if mirror:
                    mapped_forge_class = mirror.group("class_name")

            batch_match = batch_pattern.search(line)
            wrappers.append(
                FabricWrapper(
                    module=module,
                    class_name=class_name,
                    method_name=method_name,
                    batch=batch_match.group("batch") if batch_match else None,
                    mapped_forge_class=mapped_forge_class,
                )
            )
    return wrappers


def _detect_forge_runtime_blocked(
    log_paths: list[Path], blocker_patterns: list[str]
) -> tuple[bool, list[dict[str, str]]]:
    hits: list[dict[str, str]] = []
    compiled = [re.compile(pattern) for pattern in blocker_patterns]
    for log_path in log_paths:
        if not log_path.exists():
            continue
        content = log_path.read_text(encoding="utf-8", errors="replace")
        for regex in compiled:
            match = regex.search(content)
            if match:
                hits.append(
                    {
                        "file": str(log_path),
                        "pattern": regex.pattern,
                        "excerpt": match.group(0)[:240],
                    }
                )
    return (len(hits) > 0, hits)


def _load_blocker_patterns(policy_file: Path) -> list[str]:
    data = json.loads(policy_file.read_text(encoding="utf-8"))
    return list(data["lanes"]["main"].get("known_forge_blocker_patterns", []))


def generate_report(args: argparse.Namespace) -> dict[str, object]:
    fabric_cases = _parse_fabric_xml(Path(args.base_fabric_xml), module="base-fabric")
    fabric_cases.extend(
        _parse_fabric_xml(Path(args.ae_fabric_xml), module="ext-ae-fabric")
    )

    forge_scenarios = _parse_forge_scenarios(
        Path(args.base_forge_gametest_dir), module="base-forge"
    )
    forge_scenarios.extend(
        _parse_forge_scenarios(Path(args.ae_forge_gametest_dir), module="ext-ae-forge")
    )

    wrapper_paths = [
        Path(args.base_fabric_entrypoint),
        Path(args.ae_fabric_entrypoint),
    ]
    wrappers = _parse_fabric_wrappers(wrapper_paths, module="fabric")

    forge_by_class: dict[str, list[ForgeScenario]] = {}
    for scenario in forge_scenarios:
        forge_by_class.setdefault(scenario.class_name, []).append(scenario)

    case_by_method_lc = {case["method_lc"]: case for case in fabric_cases}
    matched: list[dict[str, object]] = []
    wrapper_only: list[dict[str, object]] = []
    mapped_forge_scenario_ids: set[str] = set()

    for wrapper in wrappers:
        method_lc = wrapper.method_name.lower()
        case = case_by_method_lc.get(method_lc)
        wrapper_row = {
            "wrapper_id": wrapper.wrapper_id,
            "wrapper_batch": wrapper.batch,
            "wrapper_testcase": case["name"] if case else None,
            "wrapper_status": case["status"] if case else "not_found",
            "mapped_forge_class": wrapper.mapped_forge_class,
        }

        if (
            not wrapper.mapped_forge_class
            or wrapper.mapped_forge_class not in forge_by_class
        ):
            wrapper_only.append(wrapper_row)
            continue

        candidates = forge_by_class[wrapper.mapped_forge_class]
        wrapper_tokens = _tokenize(wrapper.method_name)
        best: ForgeScenario | None = None
        best_score = -1.0
        for candidate in candidates:
            score = _jaccard(wrapper_tokens, _tokenize(candidate.method_name))
            if score > best_score:
                best = candidate
                best_score = score

        if best is None:
            wrapper_only.append(wrapper_row)
            continue

        mapped_forge_scenario_ids.add(best.scenario_id)
        matched.append(
            {
                **wrapper_row,
                "forge_scenario_id": best.scenario_id,
                "forge_batch": best.batch,
                "name_similarity": round(best_score, 3),
            }
        )

    forge_expected = [
        {
            "module": scenario.module,
            "scenario_id": scenario.scenario_id,
            "class_name": scenario.class_name,
            "method_name": scenario.method_name,
            "batch": scenario.batch,
        }
        for scenario in forge_scenarios
    ]

    missing = [
        row
        for row in forge_expected
        if row["scenario_id"] not in mapped_forge_scenario_ids
    ]

    blocker_patterns = _load_blocker_patterns(Path(args.policy_file))
    forge_blocked, blocker_hits = _detect_forge_runtime_blocked(
        [Path(path) for path in args.forge_blocker_log],
        blocker_patterns,
    )

    runtime_blocked = forge_expected if forge_blocked else []

    actionable_gaps: list[str] = []
    if missing:
        actionable_gaps.append(
            "Add Fabric wrappers (or loader-neutral shared provider wiring) for missing Forge scenarios, especially ServerThreadConfinementGameTest::timeoutAndFailureContractsRemainStableUnderForcedDelay."
        )
    if wrapper_only:
        actionable_gaps.append(
            "Keep wrapper-only entries explicit in parity dashboards (currently smoke bootstrap), and avoid counting them as Forge parity coverage."
        )
    if forge_blocked:
        actionable_gaps.append(
            "Unblock Forge runtime startup (`InvalidModFileException ... version (main)` + `Failed to find system mod: minecraft`) so Forge scenario execution status can be compared beyond wrapper-level coverage."
        )

    return {
        "report_type": "forge-vs-fabric-gametest-parity",
        "fabric_discovered_testcases": fabric_cases,
        "forge_expected_scenarios": forge_expected,
        "categories": {
            "matched": matched,
            "missing": missing,
            "wrapper_only": wrapper_only,
            "runtime_blocked": runtime_blocked,
        },
        "forge_runtime": {
            "blocked": forge_blocked,
            "blocker_hits": blocker_hits,
        },
        "actionable_gaps": actionable_gaps,
    }


def write_outputs(
    report: dict[str, object], markdown_out: Path, json_out: Path
) -> None:
    json_out.parent.mkdir(parents=True, exist_ok=True)
    markdown_out.parent.mkdir(parents=True, exist_ok=True)

    json_out.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    matched = report["categories"]["matched"]  # type: ignore[index]
    missing = report["categories"]["missing"]  # type: ignore[index]
    wrapper_only = report["categories"]["wrapper_only"]  # type: ignore[index]
    runtime_blocked = report["categories"]["runtime_blocked"]  # type: ignore[index]
    forge_runtime = report["forge_runtime"]  # type: ignore[index]

    lines: list[str] = []
    lines.append("# Fabric-vs-Forge GameTest Parity Report (Task 18)")
    lines.append("")
    lines.append("## Summary")
    lines.append(f"- Matched: {len(matched)}")
    lines.append(f"- Missing: {len(missing)}")
    lines.append(f"- Wrapper-only: {len(wrapper_only)}")
    lines.append(f"- Runtime-blocked: {len(runtime_blocked)}")
    lines.append(
        f"- Forge runtime blocked in workspace: **{str(forge_runtime['blocked']).lower()}**"  # type: ignore[index]
    )
    lines.append("")
    lines.append("## Discovered Fabric testcases")
    for case in report["fabric_discovered_testcases"]:  # type: ignore[index]
        lines.append(
            f"- `{case['name']}` ({case['module']}, status={case['status']}, time={case['time']}s)"  # type: ignore[index]
        )
    lines.append("")
    lines.append("## Expected Forge scenario set")
    for scenario in report["forge_expected_scenarios"]:  # type: ignore[index]
        lines.append(
            f"- `{scenario['scenario_id']}` ({scenario['module']}, batch={scenario['batch']})"  # type: ignore[index]
        )
    lines.append("")
    lines.append("## Category: matched")
    for row in matched:
        lines.append(
            f"- `{row['forge_scenario_id']}` <- `{row['wrapper_testcase']}` (wrapper `{row['wrapper_id']}`, status={row['wrapper_status']}, similarity={row['name_similarity']})"  # type: ignore[index]
        )
    lines.append("")
    lines.append("## Category: missing")
    for row in missing:
        lines.append(f"- `{row['scenario_id']}`")  # type: ignore[index]
    lines.append("")
    lines.append("## Category: wrapper-only")
    for row in wrapper_only:
        lines.append(
            f"- `{row['wrapper_id']}` (testcase `{row['wrapper_testcase']}`, status={row['wrapper_status']})"  # type: ignore[index]
        )
    lines.append("")
    lines.append("## Category: runtime-blocked")
    if forge_runtime["blocked"]:  # type: ignore[index]
        lines.append(
            "Forge runtime execution is blocked by known signatures in this workspace:"
        )
        for hit in forge_runtime["blocker_hits"]:  # type: ignore[index]
            lines.append(
                f"- `{hit['pattern']}` in `{hit['file']}` (excerpt: `{hit['excerpt']}`)"  # type: ignore[index]
            )
    else:
        lines.append("- No known Forge runtime blocker signatures detected.")
    lines.append("")
    lines.append("## Actionable parity gaps")
    for gap in report["actionable_gaps"]:  # type: ignore[index]
        lines.append(f"- {gap}")
    lines.append("")

    markdown_out.write_text("\n".join(lines), encoding="utf-8")


def verify_report(report: dict[str, object]) -> tuple[bool, list[str]]:
    errors: list[str] = []
    expected = cast(list[dict[str, Any]], report["forge_expected_scenarios"])
    categories = cast(dict[str, list[dict[str, Any]]], report["categories"])
    matched = categories["matched"]
    missing = categories["missing"]
    runtime_blocked = categories["runtime_blocked"]
    forge_runtime = cast(dict[str, Any], report["forge_runtime"])

    expected_ids = {row["scenario_id"] for row in expected}
    matched_ids = {row["forge_scenario_id"] for row in matched}
    missing_ids = {row["scenario_id"] for row in missing}

    if matched_ids & missing_ids:
        errors.append("Matched and missing overlap detected.")
    if (matched_ids | missing_ids) != expected_ids:
        errors.append("Matched+missing set does not equal expected Forge scenarios.")
    if forge_runtime["blocked"] and len(runtime_blocked) != len(expected):  # type: ignore[index]
        errors.append("Runtime blocked is true but runtime_blocked list is incomplete.")
    if not report["fabric_discovered_testcases"]:  # type: ignore[index]
        errors.append("No Fabric testcases discovered from XML reports.")
    return (len(errors) == 0, errors)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate Forge-vs-Fabric parity report"
    )
    parser.add_argument("--base-fabric-xml", required=True)
    parser.add_argument("--ae-fabric-xml", required=True)
    parser.add_argument("--base-forge-gametest-dir", required=True)
    parser.add_argument("--ae-forge-gametest-dir", required=True)
    parser.add_argument("--base-fabric-entrypoint", required=True)
    parser.add_argument("--ae-fabric-entrypoint", required=True)
    parser.add_argument("--policy-file", default="ci/layered-testing-policy.json")
    parser.add_argument("--forge-blocker-log", action="append", default=[])
    parser.add_argument("--markdown-out", required=True)
    parser.add_argument("--json-out", required=True)
    parser.add_argument("--verify", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    report = generate_report(args)
    markdown_out = Path(args.markdown_out)
    json_out = Path(args.json_out)
    write_outputs(report, markdown_out, json_out)

    print(f"Parity report JSON: {json_out.as_posix()}")
    print(f"Parity report Markdown: {markdown_out.as_posix()}")
    categories = cast(dict[str, list[dict[str, Any]]], report["categories"])
    print(
        "Parity categories | "
        f"matched={len(categories['matched'])} "
        f"missing={len(categories['missing'])} "
        f"wrapper_only={len(categories['wrapper_only'])} "
        f"runtime_blocked={len(categories['runtime_blocked'])}"
    )

    if args.verify:
        ok, errors = verify_report(report)
        if not ok:
            print("Verification failed:")
            for error in errors:
                print(f"- {error}")
            return 1
        print("Verification passed: parity report invariants hold.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
