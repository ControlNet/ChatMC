#!/usr/bin/env python3

import argparse
import os
import signal
import socket
import subprocess
import sys
import threading
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = [
    "empty",
    "chat_short",
    "suggestions_visible",
    "proposal_pending",
    "executing",
    "error_state",
    "http_result",
    "session_list_dense",
    "input_item_token",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Capture MineAgent UI preview screenshots."
    )
    parser.add_argument("--loader", choices=["forge", "fabric"], required=True)
    parser.add_argument("--scenario", default="all", help="Scenario id or 'all'.")
    parser.add_argument("--display", default=":1")
    parser.add_argument("--output-dir", default=str(ROOT / "artifacts" / "ui-captures"))
    parser.add_argument("--client-timeout", type=int, default=240)
    return parser.parse_args()


def module_path(loader: str) -> str:
    return f":base:{loader}-1.20.1"


def run_dir(loader: str) -> Path:
    return ROOT / "base" / f"{loader}-1.20.1" / "run" / "ui-capture-server"


def server_dir(loader: str) -> Path:
    return run_dir(loader)


def server_task(loader: str) -> str:
    return module_path(loader) + ":runUiCaptureServer"


def ensure_eula(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)
    (path / "eula.txt").write_text("eula=true\n", encoding="utf-8")


def ensure_server_properties(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)
    properties_path = path / "server.properties"
    lines = []
    if properties_path.exists():
        lines = properties_path.read_text(encoding="utf-8").splitlines()

    replacements = {
        "online-mode": "false",
        "server-port": "25565",
        "enable-status": "true",
    }

    seen = set()
    updated = []
    for line in lines:
        if "=" not in line or line.lstrip().startswith("#"):
            updated.append(line)
            continue
        key, _value = line.split("=", 1)
        if key in replacements:
            updated.append(f"{key}={replacements[key]}")
            seen.add(key)
        else:
            updated.append(line)

    for key, value in replacements.items():
        if key not in seen:
            updated.append(f"{key}={value}")

    properties_path.write_text("\n".join(updated) + "\n", encoding="utf-8")


def wait_for_port(port: int, timeout_seconds: int = 90) -> None:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.settimeout(1.0)
            try:
                sock.connect(("127.0.0.1", port))
                return
            except OSError:
                time.sleep(1.0)
    raise RuntimeError(f"Timed out waiting for localhost:{port}")


def start_server(loader: str, output_dir: Path) -> tuple[subprocess.Popen, object]:
    ensure_eula(server_dir(loader))
    ensure_server_properties(server_dir(loader))
    output_dir.mkdir(parents=True, exist_ok=True)
    log_file = open(output_dir / f"{loader}-server.log", "w", encoding="utf-8")
    command = [
        "./gradlew",
        "--no-daemon",
        server_task(loader),
    ]
    process = subprocess.Popen(
        command,
        cwd=ROOT,
        stdout=log_file,
        stderr=subprocess.STDOUT,
        text=True,
        preexec_fn=os.setsid,
    )
    return process, log_file


def stop_process_group(process: subprocess.Popen | None) -> None:
    if process is None or process.poll() is not None:
        return
    os.killpg(os.getpgid(process.pid), signal.SIGTERM)
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        os.killpg(os.getpgid(process.pid), signal.SIGKILL)
        process.wait(timeout=10)


def _wait_for_client_marker(
    process: subprocess.Popen, marker: str, timeout_seconds: int
) -> bool:
    """Read client stdout line-by-line, return True when *marker* appears."""
    found = threading.Event()

    def _reader() -> None:
        try:
            while True:
                line = process.stdout.readline()
                if not line:
                    break
                sys.stdout.write(line)
                sys.stdout.flush()
                if marker in line:
                    found.set()
                    return
        except (OSError, ValueError):
            pass

    thread = threading.Thread(target=_reader, daemon=True)
    thread.start()
    return found.wait(timeout=timeout_seconds)


def run_capture(
    loader: str, scenario: str, output_dir: Path, display: str, timeout_seconds: int
) -> Path:
    scenario_output = output_dir / loader / f"{scenario}.png"
    scenario_output.parent.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env["DISPLAY"] = display
    env["MINEAGENT_UI_CAPTURE_SCENARIO"] = scenario
    env["MINEAGENT_UI_CAPTURE_OUTPUT_DIR"] = str(scenario_output.parent)
    env["MINEAGENT_UI_CAPTURE_BASENAME"] = scenario
    env["MINEAGENT_UI_CAPTURE_SETTLE_TICKS"] = "30"
    command = [
        "./gradlew",
        "--no-daemon",
        module_path(loader) + ":runUiCaptureClient",
        "--args=--quickPlayMultiplayer 127.0.0.1 --width 1365 --height 768",
    ]
    process = subprocess.Popen(
        command,
        cwd=ROOT,
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        preexec_fn=os.setsid,
    )
    try:
        marker_seen = _wait_for_client_marker(
            process, "[mineagent-ui-capture] saved ", timeout_seconds
        )
    finally:
        stop_process_group(process)
    if not marker_seen:
        raise RuntimeError(
            f"runClient for {loader}/{scenario} did not produce the success marker "
            f"within {timeout_seconds}s"
        )
    if not scenario_output.is_file():
        raise RuntimeError(f"Expected screenshot not found: {scenario_output}")
    return scenario_output


def main() -> int:
    args = parse_args()
    scenarios = SCENARIOS if args.scenario == "all" else [args.scenario]
    output_dir = Path(args.output_dir).resolve()
    server = None
    server_log = None
    try:
        server, server_log = start_server(args.loader, output_dir)
        wait_for_port(25565)
        captured = []
        for scenario in scenarios:
            captured.append(
                run_capture(
                    args.loader, scenario, output_dir, args.display, args.client_timeout
                )
            )
        print("Captured screenshots:")
        for path in captured:
            print(path)
        return 0
    finally:
        stop_process_group(server)
        if server_log is not None:
            server_log.close()


if __name__ == "__main__":
    raise SystemExit(main())
