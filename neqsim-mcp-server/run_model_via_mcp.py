"""Drive the local MCP server over stdio against a converted UniSim model.

Exercises the full path an agent would take: discover the model, register it as
a handle, run it, then inspect units and variables through the handle instead of
resending the flowsheet.

Usage:
    python neqsim-mcp-server/run_model_via_mcp.py C:\\temp\\neqsim_model_pretty.json
"""
import json
import subprocess
import sys
import threading
import time
from pathlib import Path

JAR = Path(__file__).parent / "target" / "neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"

_proc = None
_msg_id = 0
_stderr_tail = []


def _drain_stderr(stream):
    """Consume stderr continuously; an undrained pipe blocks the JVM."""
    for line in stream:
        _stderr_tail.append(line)
        del _stderr_tail[:-40]


def start():
    """Start the server and complete the MCP handshake."""
    global _proc
    _proc = subprocess.Popen(
        ["java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
         "-Dquarkus.http.port=0", "-jar", str(JAR)],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    threading.Thread(target=_drain_stderr, args=(_proc.stderr,), daemon=True).start()
    send({
        "jsonrpc": "2.0", "id": next_id(), "method": "initialize",
        "params": {"protocolVersion": "2025-11-25", "capabilities": {},
                   "clientInfo": {"name": "model-runner", "version": "1.0"}},
    })
    recv()
    send({"jsonrpc": "2.0", "method": "notifications/initialized"})
    time.sleep(0.3)


def stop():
    """Shut the server down."""
    if _proc:
        try:
            _proc.stdin.close()
            _proc.wait(timeout=60)
        except Exception:
            _proc.kill()


def next_id():
    """Return the next JSON-RPC message id."""
    global _msg_id
    _msg_id += 1
    return _msg_id


def send(msg):
    """Write one JSON-RPC message."""
    _proc.stdin.write(json.dumps(msg) + "\n")
    _proc.stdin.flush()


def recv():
    """Read one JSON-RPC response."""
    line = _proc.stdout.readline()
    return json.loads(line) if line.strip() else None


def call(name, arguments):
    """Invoke an MCP tool and return the parsed payload."""
    send({"jsonrpc": "2.0", "id": next_id(), "method": "tools/call",
          "params": {"name": name, "arguments": arguments}})
    response = recv()
    if response is None:
        exit_code = _proc.poll()
        return {"status": "error",
                "message": f"no response from server (exit={exit_code}). stderr tail:\n"
                           + "".join(_stderr_tail[-15:])}
    content = response.get("result", {}).get("content", [])
    if not content:
        return {"status": "error", "message": json.dumps(response)[:2000]}
    text = content[0].get("text", "{}")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return {"status": "error", "message": text[:2000]}


def show(label, payload, keys=()):
    """Print a compact result summary."""
    status = payload.get("status", "?")
    print(f"\n=== {label} -> status={status}")
    if status == "error":
        print("    message:", str(payload.get("message") or payload.get("errors"))[:600])
        return
    for key in keys:
        if key in payload:
            value = payload[key]
            if isinstance(value, (dict, list)):
                value = json.dumps(value)[:400]
            print(f"    {key}: {value}")


def main():
    """Run the model through the MCP server and report the outcome."""
    model_path = sys.argv[1] if len(sys.argv) > 1 else r"C:\temp\neqsim_model_pretty.json"
    if not JAR.exists():
        raise SystemExit(f"Server jar not found: {JAR}")

    start()
    try:
        print(f"Model file: {model_path}")

        validation = call("validateInput", {"inputJson": model_path})
        show("validateInput (file path)", validation,
             ("valid", "message", "summary", "errors", "warnings", "issues", "validation"))

        definition = Path(model_path).read_text(encoding="utf-8")
        registered = call("manageModel", {
            "modelJson": json.dumps({
                "action": "register",
                "name": "UniSim Case 2 Mid GOR design",
                "version": "1.0.0",
                "processJson": json.loads(definition),
            })
        })
        show("manageModel register", registered, ("modelId", "name", "version", "revision", "tenant"))
        model_id = registered.get("modelId")

        if model_id:
            inspected = call("manageModel", {
                "modelJson": json.dumps({"action": "inspect", "modelId": model_id})
            })
            show("manageModel inspect", inspected, ("equipmentCount", "multiArea"))

        run_arg = model_id or model_path
        print(f"\nRunning process via {'handle' if model_id else 'file path'}: {run_arg}")
        started = time.time()
        result = call("runProcess", {"processJson": run_arg})
        elapsed = time.time() - started
        show(f"runProcess ({elapsed:.1f} s)", result,
             ("processSystemName", "processModelName", "convergenceSummary", "message", "errors", "warnings"))

        if result.get("status") not in ("error", None):
            units = call("listSimulationUnits", {"processJson": run_arg})
            show("listSimulationUnits (via handle)", units, ("unitCount", "count"))
            if isinstance(units.get("units"), list):
                names = [u.get("name", u) if isinstance(u, dict) else u for u in units["units"]]
                print(f"    first units: {names[:10]}")
    finally:
        stop()


if __name__ == "__main__":
    main()
