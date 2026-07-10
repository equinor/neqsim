---
title: "Using NeqSim in Seeq Data Lab"
description: "Tutorial for running NeqSim thermodynamic and process calculations inside Seeq Data Lab: installing a JDK, installing NeqSim, pulling signals with SPy, running NeqSim calculations on plant data, and pushing results back to a Seeq Workbook."
---

# Using NeqSim in Seeq Data Lab

This tutorial shows how to run [NeqSim](https://equinor.github.io/neqsim/) inside
a [Seeq Data Lab](https://www.seeq.com/) project, and how to bridge live plant
data from Seeq into NeqSim calculations using the official `seeq.spy` library.

The only extra step compared with a normal Python environment is getting a
working **Java Development Kit (JDK)** in the project, because NeqSim runs on the
JVM (via `jpype`). Seeq Data Lab lets you install Python packages with `pip`, but
**not** OS/system packages with `apt`/`yum`, so the JDK is installed as a
Python-managed download rather than a system package.

> **Note on data governance.** Everything below runs inside your authenticated
> Seeq Data Lab session. Do not hard-code access keys, passwords, tokens, or MFA
> codes in notebook cells. Treat raw pulled data and pushed results as
> intermediate values, not reviewed engineering conclusions.

---

## Prerequisites

- A Seeq Data Lab project you can open (you are already authenticated to SPy
  inside Data Lab).
- Network access from the project to PyPI (for `pip install`).

---

## Step 1 — Repair the JDK (must be the FIRST cell)

Seeq Data Lab ships a JVM that is often a **broken/partial cached JDK** under
`/home/datalab/.jdk/` (for example `jdk-23.0.2+7`). NeqSim runs on the JVM via
`jpype`, so this must be fixed **before the first NeqSim import** or any
`jpype.startJVM` / `from neqsim import jneqsim`.

**Copy this exact block into the first cell of your Seeq notebook** (it is a
no-op when you run the same notebook locally, so it is safe to keep):

```python
import os

IN_DATALAB = os.path.isdir("/home/datalab")

if IN_DATALAB:
    # Step 1: install the Python helper package if needed
    %pip install -q install-jdk

    # Step 2: remove the broken cached JDK install (folder name may vary)
    import shutil

    broken_jdk = "/home/datalab/.jdk/jdk-23.0.2+7"
    if os.path.exists(broken_jdk):
        shutil.rmtree(broken_jdk, ignore_errors=True)

    # Step 3: install JDK again
    import jdk

    java_path = jdk.install("23")

    # Step 4: set JAVA_HOME and PATH
    os.environ["JAVA_HOME"] = java_path
    os.environ["PATH"] = f"{java_path}/bin:" + os.environ["PATH"]
    print("JAVA_HOME =", os.environ["JAVA_HOME"])

    # Step 5: verify the JVM shared library exists
    libjvm = os.path.join(java_path, "lib", "server", "libjvm.so")
    print("libjvm exists:", os.path.exists(libjvm))
    print("libjvm path:", libjvm)
else:
    print("Not in Seeq Data Lab — skipping JDK repair (assuming a working local JVM).")
```

If `libjvm exists:` prints `True`, the JVM is ready.

> **This must be the first cell — before any NeqSim import.** `jpype` starts the
> JVM the first time NeqSim is imported. If you already imported NeqSim in this
> kernel before running the block above, restart the kernel and run this cell
> first.

Notes:

- The broken-folder name can vary between projects — recheck the printed
  `java_path` if the JDK 23 folder name differs from `jdk-23.0.2+7`.
- Use `jdk.install("21")` or `jdk.install("17")` only if `"23"` gives trouble in
  your project; keep the rest of the block unchanged.
- On Linux (Data Lab) the JVM library is at `lib/server/libjvm.so`.

---

## Step 2 — Install NeqSim

Install NeqSim (the JDK is already handled by Step 1):

```python
%pip install -q neqsim
```

If the kernel had already imported an older NeqSim in this session, **restart the
kernel**, then re-run Step 1 (the JDK repair cell) before continuing.

---

## Step 3 — Verify NeqSim works

```python
from neqsim.thermo import fluid

fluid1 = fluid("srk")
fluid1.addComponent("methane", 1.0)
fluid1.prettyPrint()
```

If this prints a fluid property table, NeqSim is working in Data Lab.

---

## Step 4 — Pull process data from Seeq with SPy

Inside Data Lab you are already authenticated, so `spy.search` and `spy.pull`
work directly. Replace the signal name and time window with your own.

```python
from seeq import spy

# Find the signal.
search_results = spy.search(
    {"Name": "GRA-TIC-23-0221X.PV", "Type": "Signal"},
    old_asset_format=False,
)
display(search_results)

# Pull a time series.
data = spy.pull(
    search_results,
    start="2025-04-01T00:00:00+02:00",
    end="2025-04-02T00:00:00+02:00",
    grid=None,   # None = raw samples; or "5min" for a fixed grid
)
data.head()
```

This `search` → `pull` pattern is the usual bridge between Seeq data and NeqSim
calculations.

---

## Step 5 — Use Seeq values inside a NeqSim model

Take a value pulled from Seeq and use it to set the state of a NeqSim fluid.

```python
from seeq import spy
from neqsim.thermo import fluid

# 1. Search for a temperature signal.
items = spy.search(
    {"Name": "GRA-TIC-23-0221X.PV", "Type": "Signal"},
    old_asset_format=False,
)

# 2. Pull data from Seeq.
df = spy.pull(
    items,
    start="2025-04-01T00:00:00+02:00",
    end="2025-04-01T12:00:00+02:00",
    grid=None,
)

# 3. Take one representative sample value (assumed to be in degrees C).
temp_c = float(df.iloc[0, 0])

# 4. Build a simple NeqSim fluid.
gas = fluid("srk")
gas.addComponent("methane", 0.9)
gas.addComponent("ethane", 0.1)

# 5. Set the state using the Seeq value.
gas.setTemperature(temp_c, "C")
gas.setPressure(10.0, "bara")

# 6. Run a TP flash and read properties.
from neqsim.thermo import TPflash
TPflash(gas)
gas.initProperties()   # MANDATORY before reading transport properties
gas.prettyPrint()

print("Gas density [kg/m3] :", gas.getDensity("kg/m3"))
```

> **Property initialization.** After any flash (`TPflash`, `PHflash`, …) call
> `fluid.initProperties()` before reading density, viscosity, or thermal
> conductivity. `init(1)`/`init(2)` alone do not initialize transport
> properties and those getters may return zero.

### Applying a NeqSim calculation over a whole series

To compute a derived property for every timestamp, loop (or vectorize) over the
pulled DataFrame and build a result series:

```python
import pandas as pd
from neqsim.thermo import fluid, TPflash

def gas_density_kg_m3(temp_c, pressure_bara):
    gas = fluid("srk")
    gas.addComponent("methane", 0.9)
    gas.addComponent("ethane", 0.1)
    gas.setTemperature(temp_c, "C")
    gas.setPressure(pressure_bara, "bara")
    TPflash(gas)
    gas.initProperties()
    return gas.getDensity("kg/m3")

signal_col = df.columns[0]
result = df[signal_col].apply(lambda t: gas_density_kg_m3(float(t), 10.0))
result_df = result.to_frame(name="NeqSim Gas Density")
result_df.head()
```

---

## Step 6 — Push calculated results back to Seeq

Publish a computed series back to a Seeq Workbook so it appears alongside the
plant signals.

```python
from seeq import spy

# result_df must have a DatetimeIndex and one or more numeric columns.
spy.push(
    data=result_df,
    workbook="Data Lab >> Data Lab Analysis",
    worksheet="From Data Lab",
)
```

---

## Recommended workflow

1. `pip install install-jdk neqsim`
2. **Restart the kernel**
3. Install and configure the JDK (Step 2), verify `libjvm.so` exists
4. Test NeqSim (Step 3)
5. Pull data with `spy.search()` and `spy.pull()`
6. Run NeqSim calculations on the pulled data
7. Push results back with `spy.push()`

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `JVMNotFoundException` | Java not installed, or `JAVA_HOME` points at a bad folder | Re-run Step 2; confirm `libjvm.so` exists; restart kernel |
| `Permission denied: .../classes.jsa` or `JdkError` | Cached JDK install is partial/corrupted | `shutil.rmtree` the cached `~/.jdk/...` folder, then `jdk.install(...)` again |
| `apt-get` / `yum` fails | System package installs are not allowed in Data Lab | Use `pip` only; the JDK is installed via `install-jdk`, not apt |
| Package installed but `import` still fails | Kernel not restarted after `pip install` | Restart the kernel, then re-run the imports |
| NeqSim imported but JVM uses wrong Java | `JAVA_HOME` set after the first NeqSim import | Restart the kernel, set `JAVA_HOME` (Step 2) **before** importing NeqSim |
| Density/viscosity return `0` | Transport properties not initialized | Call `fluid.initProperties()` after the flash |
| JDK 23 install unstable | Version-specific packaging issue | Use `jdk.install("21")` or `jdk.install("17")` |

---

## Related documentation

- [Python Quickstart](../quickstart/python-quickstart) — installing and using NeqSim in Python
- [Google Colab Quickstart](../quickstart/colab-quickstart) — a similar zero-install cloud workflow
- [Real-Time Integration Guide](REAL_TIME_INTEGRATION_GUIDE) — connecting NeqSim to live data systems
- [Plant Data Integration](ml_integration) — machine learning and data workflows
- NeqSim project site: <https://equinor.github.io/neqsim/>
