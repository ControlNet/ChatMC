#!/usr/bin/env python3

import argparse
import json
import os
import signal
import socket
import subprocess
import sys
import threading
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MCP_ALIAS = "statusdocs"
MCP_TOOL_NAME = "search"
MCP_TOOL_DESCRIPTION = "Search MCP docs for the status screen capture runtime."
MCP_TOOL_COUNT = 10
SCENARIOS = [
    "status_button",
    "status_panel",
    "status_panel_scrolled",
]
MCP_SERVER_SCRIPT = r"""#!/usr/bin/env python3
import json
import os
import sys

SERVER_ALIAS = os.environ.get("CHATMC_SERVER_ALIAS", "statusdocs")
TOOL_NAME = os.environ.get("CHATMC_TOOL_NAME", "search")
TOOL_DESCRIPTION = os.environ.get("CHATMC_TOOL_DESCRIPTION", "Search MCP docs")
TOOL_COUNT = int(os.environ.get("CHATMC_TOOL_COUNT", "1"))


def send(message):
    sys.stdout.write(json.dumps(message) + "\n")
    sys.stdout.flush()


def list_response(message):
    tools = []
    for i in range(TOOL_COUNT):
        name = TOOL_NAME if i == 0 else f"{TOOL_NAME}_{i}"
        desc = TOOL_DESCRIPTION if i == 0 else f"MCP fixture tool variant {i}."
        tools.append({
            "name": name,
            "description": desc,
            "inputSchema": {
                "type": "object",
                "properties": {"query": {"type": "string"}},
                "required": ["query"]
            }
        })
    return {
        "jsonrpc": "2.0",
        "id": message["id"],
        "result": {"tools": tools}
    }


def call_response(message):
    query = ""
    params = message.get("params") or {}
    arguments = params.get("arguments") or {}
    if isinstance(arguments, dict):
        query = arguments.get("query") or ""
    return {
        "jsonrpc": "2.0",
        "id": message["id"],
        "result": {
            "content": [
                {
                    "type": "text",
                    "text": f"status fixture result for {query}".strip()
                }
            ],
            "structuredContent": {
                "serverAlias": SERVER_ALIAS,
                "query": query,
                "result": "status fixture result"
            },
            "isError": False
        }
    }


for raw_line in sys.stdin:
    line = raw_line.rstrip("\n")
    if not line:
        continue
    message = json.loads(line)
    method = message.get("method")
    if method == "initialize":
        send({
            "jsonrpc": "2.0",
            "id": message["id"],
            "result": {
                "protocolVersion": message["params"]["protocolVersion"],
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "status-fixture", "version": "1.0.0"}
            }
        })
    elif method == "notifications/initialized":
        continue
    elif method == "tools/list":
        send(list_response(message))
    elif method == "tools/call":
        send(call_response(message))
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Capture MineAgent Status UI screenshots."
    )
    parser.add_argument("--loader", choices=["forge", "fabric"], required=True)
    parser.add_argument("--scenario", default="all", help="Scenario id or 'all'.")
    parser.add_argument("--display", default=":1")
    parser.add_argument("--output-dir", default=str(ROOT / "artifacts" / "ui-captures"))
    parser.add_argument("--client-timeout", type=int, default=360)
    return parser.parse_args()


def module_path(loader: str) -> str:
    return f":ext-ae:{loader}-1.20.1"


def run_dir(loader: str) -> Path:
    return ROOT / "ext-ae" / f"{loader}-1.20.1" / "run"


def server_task(loader: str) -> str:
    return module_path(loader) + ":runServer"


def client_task(loader: str) -> str:
    return module_path(loader) + ":runClient"


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


def write_mcp_fixture(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    script_path = path / f"{MCP_ALIAS}-fixture-server.py"
    script_path.write_text(MCP_SERVER_SCRIPT, encoding="utf-8")
    script_path.chmod(0o755)
    return script_path


def write_mcp_config(path: Path, fixture_script: Path) -> None:
    config_dir = path / "config" / "mineagent"
    config_dir.mkdir(parents=True, exist_ok=True)
    config = {
        "mcpServers": {
            MCP_ALIAS: {
                "type": "stdio",
                "command": "python3",
                "args": [str(fixture_script)],
                "env": {
                    "CHATMC_SERVER_ALIAS": MCP_ALIAS,
                    "CHATMC_TOOL_NAME": MCP_TOOL_NAME,
                    "CHATMC_TOOL_DESCRIPTION": MCP_TOOL_DESCRIPTION,
                    "CHATMC_TOOL_COUNT": str(MCP_TOOL_COUNT),
                },
                "cwd": str(path),
            }
        }
    }
    (config_dir / "mcp.json").write_text(
        json.dumps(config, indent=2) + "\n", encoding="utf-8"
    )


def wait_for_port(port: int, timeout_seconds: int = 120) -> None:
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
    server_run_dir = run_dir(loader)
    ensure_eula(server_run_dir)
    ensure_server_properties(server_run_dir)
    write_mcp_config(server_run_dir, write_mcp_fixture(server_run_dir / "mcp-fixtures"))
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
    env["MINEAGENT_UI_CAPTURE_SETTLE_TICKS"] = "40"
    command = [
        "./gradlew",
        "--no-daemon",
        client_task(loader),
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
