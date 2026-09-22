---
applyTo: "**"
---

# Answering a NeqSim calculation request

## Prefer the MCP tool for a one-off calculation

A single property, flash, phase-envelope, sizing, PVT, or standards lookup is
served by a curated `mcp_neqsim_*` tool when the NeqSim MCP server is available.
Use it first: it needs no local Python, no terminal, and no Java, and it returns
a schema-checked result with provenance. Write Python or Java only when the task
needs loops, plotting, a notebook, a report, or state inspected between steps.

```text
Calculate methane density at 20 C and 20 bara
-> mcp_neqsim_runFlash {"components":"{\"methane\": 1.0}", "eos":"SRK",
                        "flashType":"TP", "temperature":20, "temperatureUnit":"C",
                        "pressure":20, "pressureUnit":"bara"}
```

## Java is often installed but hidden — find it, do not give up

`JVMNotFoundException`, "No JVM shared library file (jvm.dll) found", or a JPype
failure on `import neqsim` usually means the JVM is not *discoverable*, not that
Java is missing. On managed corporate machines Java is regularly installed with
no `java` on PATH and no `JAVA_HOME`.

Do not hand-scan `C:\Program Files` and do not tell the user to install a JDK
before checking. Run the locator, which searches vendor installs, the user
profile, the Windows registry, and the JRE bundled with the VS Code Java
extension:

```powershell
python devtools/java_locator.py        # source checkout: lists every usable Java
neqsim doctor                          # pip install of the CLI: names the found Java
```

If `neqsim` is not on PATH, run `python -m neqsim_cli doctor` with the same
arguments. With only the agent plugin installed (no checkout, no CLI) neither
command exists — but the plugin's session hook resolves a JDK 21+ for the MCP
server on its own, so the `mcp_neqsim_*` tools still work and remain the route
to prefer.

In Python, make a hidden JVM usable for the current process before touching
JPype or NeqSim:

```python
import neqsim_dev_setup, sys, pathlib
sys.path.insert(0, str(pathlib.Path(neqsim_dev_setup.__file__).parent))
from java_locator import ensure_java_home
ensure_java_home()          # sets JAVA_HOME + PATH for this process only
import neqsim
```

The `sys.path` line matters: an editable devtools install resolves modules
through a map frozen at install time, so a bare `import java_locator` can fail
even though the file is present.

`devtools/neqsim_dev_setup.py` already does all of this, so notebooks and runner
jobs that use `neqsim_init(...)` need no extra step.

Report "no Java installed" only after the locator finds nothing. The remedy
then is a portable JDK unpacked into the user profile, which needs no admin
rights — never a system-wide installer the user cannot run.
