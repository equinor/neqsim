#!/usr/bin/env python3
"""Check an assembled plugin over its configured, real MCP STDIO transport.

Initializes the server, discovers its tool catalog and calls getCapabilities.
This checks packaging and transport, not scientific accuracy or the VS Code UI.
"""

import argparse
import json
import os
from pathlib import Path
import queue
import subprocess
import tempfile
import threading
import time


def smoke(plugin, timeout=90):
    plugin = plugin.resolve()
    config = json.loads((plugin / "mcp.json").read_text(encoding="utf-8"))
    server = config["mcpServers"]["neqsim"]
    if server["type"] != "stdio":
        raise ValueError("This check requires the plugin's STDIO transport")
    with tempfile.TemporaryDirectory(prefix="neqsim-plugin-smoke-") as temporary:
        env = dict(os.environ, PLUGIN_ROOT=str(plugin), PLUGIN_DATA=temporary)
        def expand(value):
            return value.replace("${PLUGIN_ROOT}", str(plugin)).replace("${PLUGIN_DATA}", temporary)
        env.update({key: expand(value) for key, value in server.get("env", {}).items()})
        command = [server["command"]] + [expand(value) for value in server.get("args", [])]
        messages = queue.Queue()
        with tempfile.TemporaryFile(mode="w+", encoding="utf-8") as errors:
            process = subprocess.Popen(
                command, cwd=expand(server.get("cwd", str(plugin))), env=env,
                stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=errors,
                text=True, encoding="utf-8", bufsize=1,
            )
            def read():
                for line in process.stdout:
                    messages.put(line)
                messages.put(None)
            reader = threading.Thread(target=read, daemon=True)
            reader.start()
            deadline = time.monotonic() + timeout
            def send(message):
                process.stdin.write(json.dumps(dict(jsonrpc="2.0", **message)) + "\n")
                process.stdin.flush()
            def response(identifier):
                while True:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise RuntimeError("MCP smoke check timed out")
                    try:
                        line = messages.get(timeout=remaining)
                    except queue.Empty as error:
                        raise RuntimeError("MCP smoke check timed out") from error
                    if line is None:
                        raise RuntimeError("MCP server exited before responding")
                    message = json.loads(line)
                    if message.get("id") == identifier:
                        if "error" in message or "result" not in message:
                            raise RuntimeError("MCP error: " + str(message))
                        return message["result"]
            try:
                send({"id": 1, "method": "initialize", "params": {
                    "protocolVersion": "2025-11-25", "capabilities": {},
                    "clientInfo": {"name": "neqsim-copilot-plugin-smoke", "version": "1.0.0"},
                }})
                initialized = response(1)
                if "serverInfo" not in initialized:
                    raise RuntimeError("Missing MCP server identity")
                send({"method": "notifications/initialized"})
                send({"id": 2, "method": "tools/list", "params": {}})
                tools = response(2)["tools"]
                names = {tool["name"] for tool in tools}
                if not {"getCapabilities", "runFlash", "runProcess"}.issubset(names):
                    raise RuntimeError("Core NeqSim tools are absent from the plugin server")
                send({"id": 3, "method": "tools/call", "params": {
                    "name": "getCapabilities", "arguments": {},
                }})
                result = response(3)
                if result.get("isError") or not result.get("content"):
                    raise RuntimeError("getCapabilities failed: " + str(result))
                content = [part["text"] for part in result["content"] if part.get("type") == "text"]
                if not content or not isinstance(json.loads(content[0]), dict):
                    raise RuntimeError("getCapabilities did not return a JSON object")
                capabilities = json.loads(content[0])
                if capabilities.get("status") != "success":
                    raise RuntimeError("getCapabilities did not succeed: " + str(capabilities))
                return {"server": initialized["serverInfo"], "toolCount": len(tools),
                        "getCapabilities": "passed"}
            finally:
                process.stdin.close()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=5)
                reader.join(timeout=2)
                process.stdout.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("plugin", type=Path)
    parser.add_argument("--timeout", type=float, default=90)
    args = parser.parse_args()
    if args.timeout <= 0:
        parser.error("--timeout must be positive")
    print(json.dumps(smoke(args.plugin, args.timeout), indent=2))


if __name__ == "__main__":
    main()
