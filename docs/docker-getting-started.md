---
title: Building and Running NeqSim with Docker
description: "Complete guide to building NeqSim in a Docker container and using it from Java and Python (jpype) — self-contained image, dev container, and headless usage."
---

# Building and Running NeqSim with Docker

This guide shows how to build NeqSim inside a Docker container and use the freshly
compiled library from both **Java** and **Python** (via jpype). Docker gives you a
reproducible environment with Java 21, Maven, and Python already wired together — no
local JDK, Maven, or Python setup required beyond Docker itself.

There are two container setups in the repository:

| File | Purpose |
|------|---------|
| [Dockerfile](https://github.com/equinor/neqsim/blob/master/Dockerfile) | **Self-contained image** — copies the repo in, compiles and packages NeqSim, and wires Python to the built classes. Best for running NeqSim from Java/Python without a local toolchain. |
| [.devcontainer/Dockerfile](https://github.com/equinor/neqsim/blob/master/.devcontainer/Dockerfile) | **VS Code Dev Container** — a full development environment (Java 21 + Maven + Python 3.12 + JupyterLab) for editing and contributing to NeqSim. |

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| **Docker** | 20.10+ (or Docker Desktop) | The only hard requirement |
| **Git** | Any recent | To clone the repository |

Verify Docker is installed and running:

```bash
docker --version
docker info
```

---

## 1. Building the self-contained image

The root [Dockerfile](https://github.com/equinor/neqsim/blob/master/Dockerfile)
starts from the published NeqSim dev container (Java 21 + Maven + Python already
present), copies the repository in, compiles and packages the Java, and installs the
Python tooling (`jpype1`, `numpy`, `pandas`, `matplotlib`, `python-docx` and the
`devtools/` helper package).

Clone the repository and build the image from the repo root:

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
docker build -t neqsim-dev .
```

The build:

1. Copies the repo into `/workspaces/neqsim` inside the image.
2. Normalizes line endings and the exec bit on the Maven wrapper (`mvnw`).
3. Runs `./mvnw -DskipTests ... clean package` to produce the shaded JAR in `target/`.
4. Installs Python packages and the editable `devtools/` package so Python can load
   the freshly built classes via jpype.

> **Note:** The first build downloads the base image and warms the Maven cache, so it
> can take several minutes. Subsequent builds reuse cached layers and are much faster.

### Pinning the base image (optional)

The base image can be overridden in CI or for reproducible builds via the
`NEQSIM_BASE` build argument:

```bash
docker build --build-arg NEQSIM_BASE=ghcr.io/equinor/neqsim-devcontainer:latest -t neqsim-dev .
```

---

## 2. Running the container

Start an interactive shell in the image:

```bash
docker run -it neqsim-dev bash
```

You land in `/workspaces/neqsim` as the `vscode` user, with NeqSim already compiled
and packaged.

### Run a Java test

```bash
cd /workspaces/neqsim
./mvnw -o test -Dtest=SystemSrkEosTest
```

The `-o` flag runs Maven offline using the cache warmed during the image build.

### Run a quick Python smoke test

```bash
python3 -c "import sys; sys.path.insert(0,'devtools'); \
    from neqsim_dev_setup import neqsim_init, neqsim_classes; \
    ns=neqsim_classes(neqsim_init(project_root='/workspaces/neqsim')); \
    f=ns.SystemSrkEos(298.15,10.0); f.addComponent('methane',1.0); \
    f.setMixingRule('classic'); print('NeqSim OK from Python')"
```

---

## 3. Using NeqSim from Python (jpype)

Inside the container, Python talks to the compiled Java classes through
[jpype](https://jpype.readthedocs.io/). The `devtools/neqsim_dev_setup.py` helper
starts the JVM, puts `target/classes` on the classpath, and exposes the NeqSim
classes via a single namespace object (`ns`).

Create a script — for example `flash.py`:

```python
import sys
sys.path.insert(0, "/workspaces/neqsim/devtools")

from neqsim_dev_setup import neqsim_init, neqsim_classes

# Start the JVM and load NeqSim from the freshly built target/classes
ns = neqsim_init(project_root="/workspaces/neqsim", recompile=False, verbose=True)
ns = neqsim_classes(ns)

# Build a fluid
fluid = ns.SystemSrkEos(273.15 + 25.0, 60.0)  # T in K, P in bara
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")  # NEVER skip

# Run a TP flash
ops = ns.JClass("neqsim.thermodynamicoperations.ThermodynamicOperations")(fluid)
ops.TPflash()
fluid.initProperties()  # initializes thermodynamic AND transport properties

print("Density (kg/m3):", fluid.getDensity("kg/m3"))
print("Number of phases:", fluid.getNumberOfPhases())
```

Run it inside the container:

```bash
python3 flash.py
```

> **Why `neqsim_dev_setup`?** It loads NeqSim from the workspace `target/classes`
> instead of an installed `neqsim` PyPI package, so any Java change you compile is
> picked up immediately. Use `recompile=True` to have it run `./mvnw compile` for you
> before starting the JVM.

### Key Python gotchas

- Temperature is in **Kelvin**, pressure in **bara** by default in the Java API.
- Always call `fluid.setMixingRule("classic")` after adding components.
- Call `fluid.initProperties()` after a flash before reading transport properties
  (viscosity, thermal conductivity) — otherwise they may return zero.

---

## 4. Mounting your own scripts and notebooks

To work on files from your host machine without rebuilding the image, mount a host
directory into the container:

```bash
docker run -it -v "${PWD}/myscripts:/workspaces/neqsim/myscripts" neqsim-dev bash
```

On Windows PowerShell:

```powershell
docker run -it -v "${PWD}\myscripts:/workspaces/neqsim/myscripts" neqsim-dev bash
```

Your scripts in `myscripts/` are now editable on the host and runnable in the
container, while NeqSim stays compiled inside the image.

### Running JupyterLab from the container

Install JupyterLab (or use the dev container, which ships it) and expose its port:

```bash
docker run -it -p 8888:8888 neqsim-dev bash
# inside the container:
python3 -m pip install --user jupyterlab
python3 -m jupyterlab --ip=0.0.0.0 --port=8888 --no-browser --allow-root
```

Open the printed `http://127.0.0.1:8888/...` URL in your host browser. Use the
`neqsim_dev_setup` cell shown above in the first code cell of each notebook.

---

## 5. The VS Code Dev Container (for development)

If you want to **edit and contribute to NeqSim** rather than just run it, use the VS
Code Dev Container. It provides Java 21, Maven 3.9, Python 3.12, JupyterLab, and the
recommended editor settings (formatter, checkstyle, Java tooling).

1. Install [Docker Desktop](https://www.docker.com/products/docker-desktop/) and the
   VS Code [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers).
2. Open the cloned `neqsim` folder in VS Code.
3. Run **Dev Containers: Reopen in Container** from the command palette.

VS Code builds (or pulls the cached) image from
[.devcontainer/devcontainer.json](https://github.com/equinor/neqsim/blob/master/.devcontainer/devcontainer.json),
then runs `./mvnw compile` and installs the Python dev tools automatically. After it
finishes you can run Java tests, Python scripts, and notebooks directly in the editor.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `mvnw: not found` or `exit 127` during build | Windows CRLF line endings on `mvnw` | The Dockerfile already strips CRLF; if building a fork, ensure `mvnw` keeps LF endings |
| Build is very slow the first time | Base image download + Maven cache warm-up | Subsequent builds reuse cached layers |
| `ModuleNotFoundError: neqsim_dev_setup` | `devtools/` not on `sys.path` | Add `sys.path.insert(0, "/workspaces/neqsim/devtools")` before importing |
| Transport properties return `0.0` | `initProperties()` not called after flash | Call `fluid.initProperties()` before reading viscosity/conductivity |
| Python loads a stale NeqSim | Installed PyPI `neqsim` shadowing the build | Use `neqsim_dev_setup` (loads `target/classes`), not `from neqsim import jneqsim` |
| JVM fails to start in Python | Java change not compiled | Pass `recompile=True` to `neqsim_init(...)` or run `./mvnw compile` first |

---

## Related Documentation

- [Getting Started with NeqSim in Java](java-getting-started.md)
- [Python Quickstart](quickstart/python-quickstart.md)
- [Developer Setup](development/DEVELOPER_SETUP.md)
- [Modules Overview](modules.md)
