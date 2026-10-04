"""Smoke-test the plugin's MCP launcher exactly as VS Code starts it.

Starts ``java <plugin>/servers/NeqsimMcpLauncher.java`` over stdio, sends the
MCP ``initialize`` handshake and ``tools/list``, and reports the tool count.
Run from a marketplace checkout::

    python devtools/smoke_test_plugin_mcp.py ../neqsim-copilot-plugin/neqsim [--no-plugin-root]

``--no-plugin-root`` leaves ``PLUGIN_ROOT`` unset so the launcher's own
source-path fallback is exercised. The launcher downloads the pinned server
jar on first use (~85 MB) unless ``NEQSIM_MCP_JAR`` points at a local build.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import threading
import time
from pathlib import Path


def _reader(stream, sink):
    for line in iter(stream.readline, b""):
        sink.append(line)


def main(argv=None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    keep_root = "--no-plugin-root" not in argv
    argv = [a for a in argv if not a.startswith("--")]
    plugin = Path(argv[0] if argv else "../neqsim-copilot-plugin/neqsim").resolve()
    launcher = plugin / "servers" / "NeqsimMcpLauncher.java"
    if not launcher.exists():
        print("launcher missing: {}".format(launcher))
        return 2
    env = dict(os.environ)
    if keep_root:
        env["PLUGIN_ROOT"] = str(plugin)
    else:
        env.pop("PLUGIN_ROOT", None)
    proc = subprocess.Popen(["java", str(launcher)], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, env=env)
    out: list = []
    err: list = []
    threading.Thread(target=_reader, args=(proc.stdout, out), daemon=True).start()
    threading.Thread(target=_reader, args=(proc.stderr, err), daemon=True).start()

    def send(msg):
        proc.stdin.write((json.dumps(msg) + "\n").encode("utf-8"))
        proc.stdin.flush()

    def wait_for(msg_id, timeout):
        deadline = time.time() + timeout
        while time.time() < deadline:
            for line in list(out):
                try:
                    obj = json.loads(line.decode("utf-8"))
                except ValueError:
                    continue
                if obj.get("id") == msg_id:
                    return obj
            if proc.poll() is not None:
                break
            time.sleep(0.2)
        return None

    send({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
        "protocolVersion": "2024-11-05", "capabilities": {},
        "clientInfo": {"name": "smoke", "version": "0"}}})
    init = wait_for(1, 600)  # first run downloads the jar
    if init is None:
        print("no initialize response; launcher stderr:\n" + b"".join(err).decode("utf-8", "replace"))
        proc.kill()
        return 1
    send({"jsonrpc": "2.0", "method": "notifications/initialized"})
    send({"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}})
    tools = wait_for(2, 60)
    proc.kill()
    if tools is None:
        print("no tools/list response; stderr:\n" + b"".join(err).decode("utf-8", "replace"))
        return 1
    names = sorted(t["name"] for t in tools["result"]["tools"])
    server = init["result"].get("serverInfo", {})
    print("server: {} {}".format(server.get("name"), server.get("version")))
    print("tools: {} ({} ...)".format(len(names), ", ".join(names[:6])))
    for line in err[-5:]:
        print("stderr: " + line.decode("utf-8", "replace").rstrip())
    return 0 if names else 1


if __name__ == "__main__":
    sys.exit(main())
