<h1>
  <img src="https://github.com/equinor/neqsim/blob/master/docs/wiki/neqsimlogocircleflatsmall.png" alt="NeqSim Logo" width="120" valign="middle">&nbsp;NeqSim
</h1>

<p align="center">
  <strong>Industrial Agentic Engineering with NeqSim<br>AI Agents for Engineering Task Solving in Industry</strong>
</p>

<p align="center">
  <a href="https://github.com/equinor/neqsim/actions/workflows/verify_build.yml?query=branch%3Amaster"><img src="https://img.shields.io/github/actions/workflow/status/equinor/neqsim/verify_build.yml?branch=master&label=CI%20Build&logo=github" alt="CI Build"></a>
  <a href="https://search.maven.org/search?q=g:%22com.equinor.neqsim%22%20AND%20a:%22neqsim%22"><img src="https://img.shields.io/maven-central/v/com.equinor.neqsim/neqsim.svg?label=Maven%20Central" alt="Maven Central"></a>
  <a href="https://codecov.io/gh/equinor/neqsim"><img src="https://codecov.io/gh/equinor/neqsim/branch/master/graph/badge.svg" alt="Coverage"></a>
  <a href="https://github.com/equinor/neqsim/security/code-scanning"><img src="https://img.shields.io/github/actions/workflow/status/equinor/neqsim/codeql.yml?branch=master&label=CodeQL&logo=github" alt="CodeQL"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue.svg" alt="License"></a>
</p>

<p align="center">
  <a href="https://github.com/codespaces/new?hide_repo_select=true&ref=master&repo=equinor/neqsim"><img src="https://img.shields.io/badge/Open_in-Codespaces-blue?logo=github" alt="Open in Codespaces"></a>
  <a href="https://colab.research.google.com/drive/1XkQ_CrVj2gLTtJvXhFQMWALzXii522CL"><img src="https://img.shields.io/badge/Open_in-Colab-F9AB00?logo=googlecolab" alt="Open in Colab"></a>
</p>

<p align="center">
  <a href="#quick-start">Quick Start</a> | <a href="#what-can-you-do-with-neqsim">Use Cases</a> | <a href="#agentic-engineering--mcp-server">AI / MCP</a> | <a href="#use-neqsim-in-java">Java</a> | <a href="#use-neqsim-in-python">Python</a> | <a href="#develop--contribute">Contribute</a> | <a href="https://equinor.github.io/neqsim/">Docs</a>
</p>

---

## What is NeqSim?

**NeqSim** (Non-Equilibrium Simulator) is a comprehensive Java library for fluid property estimation, process simulation, and engineering design. It covers the full process engineering workflow, from thermodynamic modeling and PVT analysis through equipment sizing, pipeline flow, safety studies, and field development economics.

Developed at [NTNU](https://www.ntnu.edu/employees/even.solbraa) and maintained by [Equinor](https://www.equinor.com/), NeqSim is used for real-world oil & gas, carbon capture, hydrogen, and energy applications.

Use it from **Java**, **Python**, **Jupyter notebooks**, **.NET**, **MATLAB**, or let an **AI agent** drive it via natural language.

### Key capabilities

| Domain | What NeqSim provides |
|--------|---------------------|
| **Thermodynamics** | 60+ equation-of-state models (SRK, PR, CPA, GERG-2008, and more), flash calculations (TP, PH, PS, dew, bubble), phase envelopes |
| **Physical properties** | Density, viscosity, thermal conductivity, surface tension, diffusion coefficients |
| **Process simulation** | 33+ equipment types: separators, compressors, heat exchangers, valves, distillation columns, pumps, reactors |
| **Pipeline & flow** | Steady-state and transient multiphase pipe flow (Beggs & Brill, two-fluid model), pipe networks |
| **PVT simulation** | CME, CVD, differential liberation, separator tests, swelling tests, saturation pressure |
| **Safety** | Depressurization/blowdown, PSV sizing (API 520/521), source term generation, safety envelopes |
| **Standards** | ISO 6976 (gas quality), NORSOK, DNV, API, ASME compliance checks |
| **Mechanical design** | Wall thickness, weight estimation, cost analysis for pipelines, vessels, wells (SURF) |
| **Field development** | Production forecasting, concept screening, NPV/IRR economics, Monte Carlo uncertainty |

See the [full documentation](https://equinor.github.io/neqsim/), [Java Wiki](https://github.com/equinor/neqsim/wiki), or ask questions in [Discussions](https://github.com/equinor/neqsim/discussions).

## Quick Start

### Python - try it in 30 seconds

```bash
pip install neqsim
```

```python
from neqsim import jneqsim

# Create a natural gas fluid
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 60.0)  # 25 C, 60 bara
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

# Run a flash calculation
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Gas density:    {fluid.getPhase('gas').getDensity('kg/m3'):.2f} kg/m3")
print(f"Gas viscosity:  {fluid.getPhase('gas').getViscosity('kg/msec'):.6f} kg/(m*s)")
print(f"Z-factor:       {fluid.getPhase('gas').getZ():.4f}")
```

### Java - add to your project

**Maven Central** (simplest - no authentication needed):

```xml
<dependency>
  <groupId>com.equinor.neqsim</groupId>
  <artifactId>neqsim</artifactId>
  <version>3.10.0</version>
</dependency>
```

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();

System.out.println("Density: " + fluid.getDensity("kg/m3") + " kg/m3");
```

### AI agent - describe your problem in plain English

```
@solve.task hydrate formation temperature for wet gas at 100 bara
```

The agent scopes the task, builds a NeqSim simulation, validates results, and generates a Word + HTML report with no coding required.

---

## What can you do with NeqSim?

<details>
<summary><strong>Calculate fluid properties</strong></summary>

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 15.0, 100.0)
fluid.addComponent("methane", 0.90)
fluid.addComponent("CO2", 0.05)
fluid.addComponent("nitrogen", 0.05)
fluid.setMixingRule("classic")

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Density:      {fluid.getDensity('kg/m3'):.2f} kg/m3")
print(f"Molar mass:   {fluid.getMolarMass('kg/mol'):.4f} kg/mol")
print(f"Phases:       {fluid.getNumberOfPhases()}")
```
</details>

<details>
<summary><strong>Simulate a process flowsheet</strong></summary>

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 30.0, 80.0)
fluid.addComponent("methane", 0.80)
fluid.addComponent("ethane", 0.12)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-butane", 0.03)
fluid.setMixingRule("classic")

Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

feed = Stream("Feed", fluid)
feed.setFlowRate(50000.0, "kg/hr")

separator = Separator("HP Separator", feed)
compressor = Compressor("Export Compressor", separator.getGasOutStream())
compressor.setOutletPressure(150.0, "bara")

process = ProcessSystem()
process.add(feed)
process.add(separator)
process.add(compressor)
process.run()

print(f"Compressor power: {compressor.getPower('kW'):.0f} kW")
print(f"Gas out temp:     {compressor.getOutletStream().getTemperature() - 273.15:.1f} C")
```
</details>

<details>
<summary><strong>Predict hydrate formation temperature</strong></summary>

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 5.0, 80.0)
fluid.addComponent("methane", 0.90)
fluid.addComponent("ethane", 0.06)
fluid.addComponent("propane", 0.03)
fluid.addComponent("water", 0.01)
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.hydrateFormationTemperature()

print(f"Hydrate T: {fluid.getTemperature() - 273.15:.2f} C")
```
</details>

<details>
<summary><strong>Run pipeline pressure-drop calculations</strong></summary>

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 40.0, 120.0)
fluid.addComponent("methane", 0.95)
fluid.addComponent("ethane", 0.05)
fluid.setMixingRule("classic")

Stream = jneqsim.process.equipment.stream.Stream
PipeBeggsAndBrills = jneqsim.process.equipment.pipeline.PipeBeggsAndBrills

feed = Stream("Inlet", fluid)
feed.setFlowRate(200000.0, "kg/hr")

pipe = PipeBeggsAndBrills("Export Pipeline", feed)
pipe.setPipeWallRoughness(5e-5)
pipe.setLength(50000.0)       # 50 km
pipe.setDiameter(0.508)        # 20 inch
pipe.setNumberOfIncrements(20)
pipe.run()

outlet = pipe.getOutletStream()
print(f"Outlet pressure: {outlet.getPressure():.1f} bara")
print(f"Outlet temp:     {outlet.getTemperature() - 273.15:.1f} C")
```
</details>

<details>
<summary><strong>More examples</strong></summary>

Explore **30+ Jupyter notebooks** in [`examples/notebooks/`](examples/notebooks/):

- Phase envelope calculation
- TEG dehydration process
- Vessel depressurization / blowdown
- Heat exchanger thermal-hydraulic design
- Production bottleneck analysis
- Risk simulation and visualization
- Data reconciliation and parameter estimation
- Reservoir-to-export integrated workflows
- Multiphase transient pipe flow

</details>

---

## Agentic Engineering & MCP Server

LLMs reason well but hallucinate physics. NeqSim is exact on thermodynamics but needs context. **Together, they form a complete engineering system.** The LLM reasons. NeqSim computes. Provenance proves it.

### MCP Server - give any LLM access to rigorous thermodynamics

The [NeqSim MCP Server](neqsim-mcp-server/) lets any MCP-compatible client (VS Code Copilot, Claude Desktop, Cursor, etc.) run real calculations. Install in seconds:

```bash
# Docker (no Java needed)
docker pull ghcr.io/equinor/neqsim-mcp-server:latest
```

| Ask the LLM | MCP Tool |
|---|---|
| *"Dew point of 85% methane, 10% ethane, 5% propane at 50 bara?"* | `runFlash` |
| *"How does density change from 0 to 50 C at 80 bara?"* | `runBatch` |
| *"Phase envelope for this natural gas"* | `getPhaseEnvelope` |
| *"Simulate gas through a separator then compressor to 120 bara"* | `runProcess` |

Every response includes provenance metadata (EOS model, convergence, assumptions, limitations). See the [MCP Server docs](neqsim-mcp-server/) and [setup guide](neqsim-mcp-server/#install-from-github-release-3-steps).

### AI task-solving workflow

```
@solve.task TEG dehydration sizing for 50 MMSCFD wet gas
```

The agent creates a task folder, runs NeqSim simulations, validates results, and generates a Word + HTML report with no coding required. See the [tutorial](docs/tutorials/solve-engineering-task.md) or [workflow reference](docs/development/TASK_SOLVING_GUIDE.md).

---

## Use NeqSim in Java

```xml
<dependency>
  <groupId>com.equinor.neqsim</groupId>
  <artifactId>neqsim</artifactId>
  <version>3.10.0</version>
</dependency>
```

The Quick Start above shows the core pattern (create a fluid, run a flash, and read properties). For process simulation, add equipment to a `ProcessSystem` and call `run()`; see the [Java Getting Started Guide](docs/java-getting-started.md) for full examples.

<details>
<summary>GitHub Packages setup (latest snapshots)</summary>

1. Configure authentication in your Maven `settings.xml`:

```xml
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>${env.GITHUB_TOKEN}</password>
  </server>
</servers>
```

2. Add to your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/equinor/neqsim</url>
  </repository>
</repositories>
```
</details>

**Learn more:** [Java Getting Started Guide](docs/java-getting-started.md) | [JavaDoc](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html) | [Wiki](https://github.com/equinor/neqsim/wiki) | [Colab demo](https://colab.research.google.com/drive/1XkQ_CrVj2gLTtJvXhFQMWALzXii522CL)

---

## Use NeqSim in Python

```bash
pip install neqsim
```

NeqSim Python gives you direct access to the full Java API via the `jneqsim` gateway. All Java classes are available, including thermodynamics, process equipment, PVT, standards, and more.

```python
from neqsim import jneqsim

# All Java classes accessible through jneqsim
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
# ... 200+ classes available
```

Explore **30+ ready-to-run Jupyter notebooks** in [`examples/notebooks/`](examples/notebooks/).

### Other language bindings

| Language | Repository |
|----------|-----------|
| Python | [`pip install neqsim`](https://github.com/equinor/neqsimpython) |
| MATLAB | [equinor/neqsimmatlab](https://github.com/equinor/neqsimmatlab) |
| .NET (C#) | [equinor/neqsimcapeopen](https://github.com/equinor/neqsimcapeopen) |

---

## Develop & Contribute

### Clone and build

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
./mvnw install        # Linux/macOS
mvnw.cmd install      # Windows
```

### Run tests

```bash
./mvnw test                                    # all tests
./mvnw test -Dtest=SeparatorTest               # single class
./mvnw test -Dtest=SeparatorTest#testTwoPhase  # single method
./mvnw checkstyle:check spotbugs:check pmd:check  # static analysis
```

### Open in VS Code

The repository includes a ready-to-use [dev container](.devcontainer/); just open the repo in VS Code with container support:

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
code .
```

### Architecture

```mermaid
graph TB
    subgraph core["NeqSim Core (Java 8+)"]
        THERMO["Thermodynamics<br/>60+ EOS models"]
        PROCESS["Process Simulation<br/>33+ equipment types"]
        PVT["PVT Simulation"]
        MECH["Mechanical Design<br/>& Standards"]
    end

    subgraph access["Access Layers"]
        PYTHON["Python / Jupyter<br/>pip install neqsim"]
        JAVA["Java / Maven<br/>Direct API"]
        MCP["MCP Server (Java 17+)<br/>LLM integration"]
        AGENTS["AI Agents<br/>VS Code Copilot"]
    end

    PYTHON --> THERMO
    PYTHON --> PROCESS
    JAVA --> THERMO
    JAVA --> PROCESS
    MCP --> THERMO
    MCP --> PROCESS
    AGENTS --> MCP
    AGENTS --> PYTHON
```

#### Which entry point should I use?

| I want to... | Use | Requires |
|---|---|---|
| Quick property lookup via LLM | [MCP Server](neqsim-mcp-server/) + any LLM client | Java 17+ (or Docker) |
| Python scripting / Jupyter notebooks | `pip install neqsim` | Python 3.8+, JVM |
| Embed in a Java application | Maven dependency | Java 8+ |
| Full engineering study with reports | `@solve.task` agent in VS Code | VS Code + GitHub Copilot |
| .NET / MATLAB integration | [Language bindings](#other-language-bindings) | See linked repos |

#### Java version matrix

| Component | Java Version | Notes |
|---|---|---|
| **NeqSim core library** | 8+ | All thermodynamics, process equipment, PVT |
| **MCP server** | 17+ | Quarkus-based; thin wrapper around core |
| **Python users** | No Java coding | JVM bundled via jpype |
| **Running prebuilt MCP jar** | 17+ | Download from [releases](https://github.com/equinor/neqsim/releases) |

#### Core modules

| Module | Package | Purpose |
|--------|---------|---------|
| **Thermodynamics** | `thermo/` | 60+ EOS implementations, flash calculations, phase equilibria |
| **Physical properties** | `physicalproperties/` | Density, viscosity, thermal conductivity, surface tension |
| **Fluid mechanics** | `fluidmechanics/` | Single- and multiphase pipe flow, pipeline networks |
| **Process equipment** | `process/equipment/` | 33+ unit operations (separators, compressors, HX, valves, ...) |
| **Chemical reactions** | `chemicalreactions/` | Equilibrium and kinetic reaction models |
| **Parameter fitting** | `statistics/` | Regression, parameter estimation, Monte Carlo |
| **Process simulation** | `process/` | Flowsheet assembly, dynamic simulation, recycle/adjuster coordination |

For details see [docs/modules.md](docs/modules.md).

### Contributing

We welcome contributions of all kinds: bug fixes, new models, examples, documentation, and notebook recipes.
**AI-assisted PRs are first-class contributions**; see [CONTRIBUTING.md](CONTRIBUTING.md).

**New here?** Three commands to get started:

```bash
git clone https://github.com/equinor/neqsim.git && cd neqsim
pip install -e devtools/    # one-time: registers the `neqsim` command
neqsim onboard             # interactive setup (Java, Maven, build, Python, agents)
```

> **Tip:** Using a virtual environment (`python -m venv .venv` then activate it) avoids
> PATH issues on all platforms. See [devtools/README.md](devtools/README.md#troubleshooting-neqsim-not-found)
> if `neqsim` is not found, or use `python -m neqsim_cli` as a fallback.

Or skip local setup entirely: **[Open in GitHub Codespaces](https://github.com/codespaces/new?hide_repo_select=true&ref=master&repo=equinor/neqsim)**, with everything pre-installed in the browser.

Then explore and contribute:

```bash
neqsim try                 # interactive playground - experiment with NeqSim instantly
neqsim contribute          # guided wizard - picks the right path for you
neqsim doctor              # quick diagnostic if something isn't working
```

- [CONTRIBUTING.md](CONTRIBUTING.md) - Code of conduct, PR process, AI-assisted contributions
- [VISION_AGENTS.md](VISION_AGENTS.md) - What belongs in the agentic system (core vs. community)
- [Developer setup guide](docs/DEVELOPER_SETUP.md) - Build, test, and project structure
- [Contributing structure](docs/contributing-structure.md) - Where to place code, tests, and resources

#### Where to start

**Skills** are markdown files containing engineering knowledge (code patterns, design rules, troubleshooting tips) that AI agents load automatically when solving related tasks. Contributing a skill is the easiest way to make the agentic system smarter, with no Java required.

| # | First Contribution | Difficulty | What to do |
|---|---|---|---|
| 1 | **Contribute a skill** | Easy | Write a SKILL.md with domain knowledge - `neqsim new-skill "name"` ([guide](.github/skills/README.md#how-to-contribute-a-skill), [example skill](.github/skills/neqsim-input-validation/SKILL.md)) |
| 2 | Add a NIST validation benchmark | Easy | Compare NeqSim flash results to NIST data in `docs/benchmarks/` |
| 3 | Create a Jupyter notebook example | Medium | Add a worked example to `examples/notebooks/` |
| 4 | Add an MCP example to the catalog | Easy | Add a new entry in `ExampleCatalog.java` |
| 5 | Fix a broken doc link | Easy | Search `docs/**/*.md` for dead links and fix them |
| 6 | Add a unit test for existing equipment | Medium | Add tests under `src/test/java/neqsim/` |

#### Community Skill Catalog

Browse and install community-contributed skills, or publish your own:

```bash
neqsim skill list                    # browse the catalog
neqsim skill install <name>          # install a skill
neqsim skill publish user/repo-name  # publish yours (creates a draft PR)
```

See the [Skills Guide](docs/integration/skills_guide.md) for the full walkthrough,
[community-skills.yaml](community-skills.yaml) for the catalog, and
[.github/skills/README.md](.github/skills/README.md) for the quick contribution guide.

All tests and `./mvnw checkstyle:check` must pass before a PR is merged.

---

## Documentation & Resources

| Resource | Link |
|----------|------|
| **User documentation** | [equinor.github.io/neqsim](https://equinor.github.io/neqsim/) |
| **Benchmark gallery** | [docs/benchmarks/](docs/benchmarks/index.md) - validation against NIST, published data |
| **Reference manual index** | [REFERENCE_MANUAL_INDEX.md](docs/REFERENCE_MANUAL_INDEX.md) (350+ pages) |
| **MCP tool contract** | [MCP_CONTRACT.md](neqsim-mcp-server/MCP_CONTRACT.md) - stable API for agent builders |
| **JavaDoc API** | [JavaDoc](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html) |
| **Jupyter notebooks** | [examples/notebooks/](examples/notebooks/) (30+ examples) |
| **Discussion forum** | [GitHub Discussions](https://github.com/equinor/neqsim/discussions) |
| **Releases** | [GitHub Releases](https://github.com/equinor/neqsim/releases) |
| **NeqSim homepage** | [equinor.github.io/neqsimhome](https://equinor.github.io/neqsimhome/) |

---

## Authors

Even Solbraa (esolbraa@gmail.com), Marlene Louise Lund

NeqSim development was initiated at [NTNU](https://www.ntnu.edu/employees/even.solbraa). A number of master and PhD students have contributed to its development, and we greatly acknowledge their contributions.

## License

[Apache-2.0](LICENSE)
