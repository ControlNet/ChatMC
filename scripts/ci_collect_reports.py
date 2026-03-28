#!/usr/bin/env python3
"""Aggregate layered testing reports and enforce CI policy."""

from __future__ import annotations

import argparse
import glob
import json
import re
import xml.etree.ElementTree as et
from pathlib import Path
from typing import Any, Iterable


def _expand(patterns: Iterable[str]) -> list[str]:
    files: set[str] = set()
    for pattern in patterns:
        for match in glob.glob(pattern, recursive=True):
            path = Path(match)
            if path.is_file():
                files.add(path.as_posix())
    return sorted(files)


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _parse_junit_like(xml_files: list[str]) -> dict:
    totals = {
        "tests": 0,
        "passed": 0,
        "failed": 0,
        "errors": 0,
        "skipped": 0,
    }
    statuses_by_test: dict[str, set[str]] = {}
    parse_errors: list[str] = []

    for file_path in xml_files:
        try:
            root = et.parse(file_path).getroot()
        except Exception as exc:  # noqa: BLE001
            parse_errors.append(f"{file_path}: {exc}")
            continue

        for case in root.iter():
            if _local_name(case.tag) != "testcase":
                continue

            classname = case.attrib.get("classname", "<no-class>")
            name = case.attrib.get("name", "<no-name>")
            key = f"{classname}::{name}"

            children = [_local_name(child.tag) for child in list(case)]
            has_failure = "failure" in children
            has_error = "error" in children
            is_skipped = "skipped" in children

            totals["tests"] += 1
            if has_failure:
                totals["failed"] += 1
                status = "failed"
            elif has_error:
                totals["errors"] += 1
                status = "error"
            elif is_skipped:
                totals["skipped"] += 1
                status = "skipped"
            else:
                totals["passed"] += 1
                status = "passed"

            statuses_by_test.setdefault(key, set()).add(status)

    flaky_recovered = sorted(
        test_key
        for test_key, statuses in statuses_by_test.items()
        if "passed" in statuses and ("failed" in statuses or "error" in statuses)
    )
    still_failing = sorted(
        test_key
        for test_key, statuses in statuses_by_test.items()
        if ("failed" in statuses or "error" in statuses) and "passed" not in statuses
    )

    return {
        "totals": totals,
        "parse_errors": parse_errors,
        "statuses_by_test": {
            key: sorted(value) for key, value in statuses_by_test.items()
        },
        "flaky_recovered": flaky_recovered,
        "still_failing": still_failing,
    }


def _match_any(test_key: str, patterns: list[str]) -> bool:
    for pattern in patterns:
        if pattern.startswith("re:"):
            if re.search(pattern[3:], test_key):
                return True
        elif pattern == test_key:
            return True
    return False


def _scan_forge_logs(log_files: list[str], blocker_patterns: list[str]) -> dict:
    blocker_hits: list[dict[str, str]] = []
    parse_errors: list[str] = []
    compiled = [re.compile(pattern) for pattern in blocker_patterns]

    for file_path in log_files:
        try:
            content = Path(file_path).read_text(encoding="utf-8", errors="replace")
        except Exception as exc:  # noqa: BLE001
            parse_errors.append(f"{file_path}: {exc}")
            continue

        for regex in compiled:
            match = regex.search(content)
            if match:
                excerpt = match.group(0)
                blocker_hits.append(
                    {
                        "file": file_path,
                        "pattern": regex.pattern,
                        "excerpt": excerpt[:240],
                    }
                )

    return {
        "blocker_detected": len(blocker_hits) > 0,
        "blocker_hits": blocker_hits,
        "parse_errors": parse_errors,
    }


def _load_policy(policy_file: Path, lane: str) -> dict:
    policy_data = json.loads(policy_file.read_text(encoding="utf-8"))
    lanes = policy_data.get("lanes", {})
    if lane not in lanes:
        available = ", ".join(sorted(lanes.keys()))
        raise ValueError(f"Unknown lane '{lane}'. Available lanes: {available}")
    return lanes[lane]


def _int(value: Any, default: int = 0) -> int:
    if value is None:
        return default
    return int(value)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Collect CI reports and enforce lane policy"
    )
    parser.add_argument("--lane", required=True, help="Lane name (pr|main|nightly)")
    parser.add_argument("--policy-file", required=True, help="Policy JSON path")
    parser.add_argument(
        "--summary-json", required=True, help="Output summary JSON path"
    )
    parser.add_argument(
        "--junit-glob", action="append", default=[], help="Glob for JUnit XML"
    )
    parser.add_argument(
        "--fabric-gametest-glob",
        action="append",
        default=[],
        help="Glob for Fabric GameTest XML",
    )
    parser.add_argument(
        "--forge-report-glob",
        action="append",
        default=[],
        help="Glob for Forge XML reports when available",
    )
    parser.add_argument(
        "--forge-log-glob",
        action="append",
        default=[],
        help="Glob for Forge logs",
    )
    parser.add_argument(
        "--forge-exit-code",
        type=int,
        default=0,
        help="Exit code from Forge GameTest command",
    )
    parser.add_argument(
        "--known-forge-blocker-pattern",
        action="append",
        default=[],
        help="Additional regex to detect known Forge blockers",
    )
    args = parser.parse_args()

    policy_path = Path(args.policy_file)
    summary_path = Path(args.summary_json)

    lane_policy = _load_policy(policy_path, args.lane)
    required = lane_policy.get("required", {})

    junit_files = _expand(args.junit_glob)
    fabric_files = _expand(args.fabric_gametest_glob)
    forge_report_files = _expand(args.forge_report_glob)
    forge_log_files = _expand(args.forge_log_glob)

    junit_parsed = _parse_junit_like(junit_files)
    fabric_parsed = _parse_junit_like(fabric_files)
    forge_report_parsed = _parse_junit_like(forge_report_files)

    known_blocker_patterns = list(lane_policy.get("known_forge_blocker_patterns", []))
    known_blocker_patterns.extend(args.known_forge_blocker_pattern)
    forge_log_scan = _scan_forge_logs(forge_log_files, known_blocker_patterns)

    all_flaky = sorted(
        set(junit_parsed["flaky_recovered"])
        | set(fabric_parsed["flaky_recovered"])
        | set(forge_report_parsed["flaky_recovered"])
    )
    all_failing = sorted(
        set(junit_parsed["still_failing"])
        | set(fabric_parsed["still_failing"])
        | set(forge_report_parsed["still_failing"])
    )

    quarantined_patterns = lane_policy.get("quarantined_tests", [])
    quarantined_failures = sorted(
        test_key
        for test_key in all_failing
        if _match_any(test_key, quarantined_patterns)
    )
    non_quarantined_failures = sorted(set(all_failing) - set(quarantined_failures))

    max_non_quarantined_failures = _int(
        lane_policy.get("max_non_quarantined_failures"), 0
    )
    max_quarantined_failures = _int(lane_policy.get("max_quarantined_failures"), 0)
    max_flaky_recovered = _int(lane_policy.get("max_flaky_recovered"), 0)
    allow_known_forge_blocker = bool(
        lane_policy.get("allow_known_forge_blocker", False)
    )
    blocked_exit_code = _int(lane_policy.get("blocked_exit_code"), 3)

    failures: list[str] = []

    parse_error_count = (
        len(junit_parsed["parse_errors"])
        + len(fabric_parsed["parse_errors"])
        + len(forge_report_parsed["parse_errors"])
        + len(forge_log_scan["parse_errors"])
    )
    if parse_error_count > 0:
        failures.append(f"report_parse_errors present ({parse_error_count})")

    if len(non_quarantined_failures) > max_non_quarantined_failures:
        failures.append(
            "non_quarantined_failures "
            f"{len(non_quarantined_failures)} > {max_non_quarantined_failures}"
        )
    if len(quarantined_failures) > max_quarantined_failures:
        failures.append(
            "quarantined_failures "
            f"{len(quarantined_failures)} > {max_quarantined_failures}"
        )
    if len(all_flaky) > max_flaky_recovered:
        failures.append(f"flaky_recovered {len(all_flaky)} > {max_flaky_recovered}")

    if len(junit_files) < _int(required.get("junit_min_files"), 0):
        failures.append(
            "required junit files missing "
            f"({len(junit_files)} < {_int(required.get('junit_min_files'), 0)})"
        )
    if len(fabric_files) < _int(required.get("fabric_report_min_files"), 0):
        failures.append(
            "required fabric reports missing "
            f"({len(fabric_files)} < {_int(required.get('fabric_report_min_files'), 0)})"
        )
    if len(forge_log_files) < _int(required.get("forge_log_min_files"), 0):
        failures.append(
            "required forge logs missing "
            f"({len(forge_log_files)} < {_int(required.get('forge_log_min_files'), 0)})"
        )

    if args.forge_exit_code != 0 and not (
        allow_known_forge_blocker and forge_log_scan["blocker_detected"]
    ):
        failures.append(f"forge command exited with code {args.forge_exit_code}")

    status = "pass"
    exit_code = 0
    if failures:
        status = "fail"
        exit_code = 1
    elif forge_log_scan["blocker_detected"] and allow_known_forge_blocker:
        status = "blocked"
        exit_code = blocked_exit_code

    summary = {
        "lane": args.lane,
        "status": status,
        "exit_code": exit_code,
        "failures": failures,
        "thresholds": {
            "max_non_quarantined_failures": max_non_quarantined_failures,
            "max_quarantined_failures": max_quarantined_failures,
            "max_flaky_recovered": max_flaky_recovered,
            "allow_known_forge_blocker": allow_known_forge_blocker,
            "blocked_exit_code": blocked_exit_code,
            "retry_attempts": lane_policy.get("retry_attempts", {}),
            "required": required,
        },
        "files": {
            "junit_xml": junit_files,
            "fabric_gametest_xml": fabric_files,
            "forge_report_files": forge_report_files,
            "forge_log_files": forge_log_files,
        },
        "junit": junit_parsed,
        "fabric_gametest": fabric_parsed,
        "forge_reports": forge_report_parsed,
        "forge_logs": forge_log_scan,
        "classification": {
            "flaky_recovered": all_flaky,
            "still_failing": all_failing,
            "quarantined_failures": quarantined_failures,
            "non_quarantined_failures": non_quarantined_failures,
        },
        "forge_exit_code": args.forge_exit_code,
    }

    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    print(
        "CI report summary | "
        f"lane={summary['lane']} status={summary['status']} "
        f"non_quarantined_failures={len(non_quarantined_failures)} "
        f"flaky_recovered={len(all_flaky)}"
    )
    print(f"Summary JSON: {summary_path.as_posix()}")
    if failures:
        print("Policy failures:")
        for failure in failures:
            print(f"- {failure}")
    if forge_log_scan["blocker_detected"]:
        print("Known Forge blocker signatures detected:")
        for hit in forge_log_scan["blocker_hits"]:
            print(f"- [{hit['file']}] {hit['pattern']} => {hit['excerpt']}")

    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
