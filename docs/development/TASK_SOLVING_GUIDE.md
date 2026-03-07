---
title: "Task Solving Guide"
description: "Step-by-step workflow for solving engineering tasks inside the NeqSim repo using AI (Copilot + Claude). Covers the develop-while-solving loop, task classification, AI agent coordination, and how to build persistent knowledge."
---

# Task Solving Guide

How to solve engineering tasks inside the NeqSim repo using AI, while
simultaneously developing the physics engine — and making every solution
findable for the next session.

---

## AI-Supported Task Solving While Developing

Every engineering task follows a 4-step workflow that combines AI research
tools with NeqSim's physics-based API. Each task gets its own folder in
`task_solve/` (gitignored — local working area only).

```
 STEP 1              STEP 2              STEP 3              STEP 4
 Research            Technical Analysis  Iterative Result    Final Technical
                     via NeqSim &        Evaluation          Writing
 Google NotebookLM   Copilot
 + open sources                          GitHub Copilot      Claude Opus 4.6
                     NeqSim API +        + Claude Opus 4.6   + GitHub Copilot
 Build knowledge     GitHub Copilot
 base                Deep analysis       Refine results      Synthesize into
                                                             assessment
```

### How to Start a New Task

1. **Run the setup script** (auto-creates `task_solve/` on first use):
   ```powershell
   python devtools/new_task.py "JT cooling for rich gas"
   python devtools/new_task.py "TEG dehydration sizing" --type B --author "Your Name"
   ```
2. **Open the generated README** — it has AI prompts ready to paste for each step
3. **Work through Steps 1–4**, saving artifacts in the corresponding subfolder
4. **When done**, promote reusable outputs back into the repo:
   - Tests → `src/test/java/neqsim/`
   - Notebooks → `examples/notebooks/`
   - API extensions → `src/main/java/neqsim/`
   - Task log entry → `docs/development/TASK_LOG.md`

> **New user?** The script is in `devtools/` (tracked in git), so it's available
> immediately after `git clone`. It creates `task_solve/` automatically on first run.

### Task Folder Structure

```
task_solve/
├── README.md                              ← workflow overview
├── TASK_TEMPLATE/                         ← copy this to start
│   ├── README.md                          ← task checklist
│   ├── step1_research/notes.md            ← literature, sources
│   ├── step2_analysis/                    ← simulations, notebooks
│   ├── step3_evaluation/notes.md          ← validation, iterations
│   ├── step4_report/                      ← reports, deliverables
│   └── figures/                           ← all saved plots
└── 2026-03-07_jt_cooling_rich_gas/        ← example real task
```

See `task_solve/README.md` for the full workflow description and details.

---

## Why Solve Tasks Inside the Repo?

NeqSim is both the *tool* you use and the *codebase* you develop. When you
solve a task here, the boundary between "using the library" and "extending it"
disappears:

```
                  ┌──────────────────────────┐
                  │     Engineering Task      │
                  │ "What's the JT cooling    │
                  │  for this gas at 200 bar?"│
                  └────────────┬─────────────┘
                               │
                  ┌────────────▼─────────────┐
                  │   Can NeqSim solve this   │
                  │      as-is?               │
                  └──────┬──────────┬─────────┘
                    YES  │          │  NO
                         │          │
              ┌──────────▼───┐  ┌───▼──────────────┐
              │ Write test / │  │ Extend the API:  │
              │ notebook     │  │ add method, model│
              │ using API    │  │ or equipment     │
              └──────────┬───┘  └───┬──────────────┘
                         │          │
                  ┌──────▼──────────▼─────────┐
                  │   Verify, commit, log      │
                  │   (knowledge accumulates)  │
                  └───────────────────────────┘
```

Every task you solve becomes a test, notebook, or feature — so the *next*
task starts from a higher baseline. The AI agent re-reads `TASK_LOG.md`
each session and picks up where the last one left off.

---

## The AI-Assisted Development Loop

This is the core workflow. Each step maps to a concrete action that either
you or the AI agent performs.

### Phase 1: Orient (60 seconds)

```
AI reads:  CONTEXT.md           → repo map, build commands, constraints
           TASK_LOG.md          → similar past solutions
           copilot-instructions → API rules, Java 8, patterns
```

**What you do:** Describe the task in natural language. The AI handles the rest.

**What the AI does:**
1. Reads `CONTEXT.md` to understand where things live
2. Searches `TASK_LOG.md` for keywords matching your task
3. Identifies the task type (A–F) to pick the right workflow

### Phase 2: Investigate (2–5 minutes)

```
AI searches:  src/test/java/neqsim/     → existing tests doing similar things
              src/main/java/neqsim/     → actual API methods available
              examples/notebooks/        → Python examples
              docs/wiki/                 → explanations of the physics
```

**Key principle:** The AI reads actual source code to verify method signatures
before using them. It does not hallucinate API calls because it can `grep_search`
the real Java files.

### Phase 3: Solve (the core loop)

```
                ┌─────────────────────────────────┐
                │ Write code (test or notebook)    │
                └─────────────┬───────────────────┘
                              │
                ┌─────────────▼───────────────────┐
          ┌─NO──┤ Does the API support what I need?│
          │     └─────────────┬───────────────────┘
          │                   │ YES
          │     ┌─────────────▼───────────────────┐
          │     │ Run it: mvnw test / run cell     │
          │     └─────────────┬───────────────────┘
          │                   │
          │     ┌─────────────▼───────────────────┐
          │     │ Check physics: are numbers sane? │
          │     └──┬──────────────────────────┬───┘
          │        │ YES                      │ NO
          │        ▼                          │
          │     DONE ← log it           debug/fix ──┘
          │
          ▼
  ┌───────────────────────────────────────────┐
  │ EXTEND THE API:                           │
  │ 1. Find the right class in src/main/      │
  │ 2. Add the method / model / equipment     │
  │ 3. Write a test in src/test/              │
  │ 4. mvnw compile → mvnw test              │
  │ 5. Return to the original task            │
  └───────────────────────────────────────────┘
```

This is the unique power of working inside the repo: when the physics engine
is missing something, you add it — right there, mid-task — then keep going.

### Phase 4: Verify

Run the checklist:
- Does it compile? (`.\mvnw.cmd compile`)
- Do tests pass? (`.\mvnw.cmd test -Dtest=YourTest`)
- Are the numbers physically reasonable?
- Does checkstyle pass? (`.\mvnw.cmd checkstyle:check`)
- Java 8 compatible? (no `var`, `List.of()`, etc.)

### Phase 5: Log

Add an entry to `docs/development/TASK_LOG.md`:

```markdown
### 2026-03-01 — Joule-Thomson cooling for rich gas at 200 bar
**Type:** A (Property)
**Keywords:** JT, Joule-Thomson, cooling, isenthalpic, rich gas, high pressure
**Solution:** src/test/java/neqsim/thermo/JouleThomsonTest.java
**Notes:** Used SystemPrEos with PR78 alpha function for better accuracy
at high pressure. JT coefficient = 0.35 K/bar at 200 bar, 40°C.
CPA not needed since no water in this case.
```

---

## Task Classification

Every NeqSim task falls into one of six types. Each has a different starting
point, verification strategy, and AI agent.

### Type A: Property Calculation

**"What is the density / viscosity / JT coefficient of this fluid?"**

| Aspect | Detail |
|--------|--------|
| Start from | Fluid creation pattern in `CONTEXT.md` |
| EOS choice | See EOS Selection table in `CONTEXT.md` |
| Code goes in | Test (`src/test/`) or notebook (`examples/notebooks/`) |
| Verify by | Compare against NIST, experiment, or published correlations |
| AI agent | `@thermo.fluid` |

```java
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
fluid.addComponent("methane", 1.0);
fluid.setMixingRule("classic");
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.init(3);
System.out.println("Density: " + fluid.getDensity("kg/m3"));
```

**If the property method doesn't exist:** See [Extending the Physics Engine](#extending-the-physics-engine-mid-task) below.

---

### Type B: Process Simulation

**"Size a 3-stage compression train" / "Model a TEG dehydration unit"**

| Aspect | Detail |
|--------|--------|
| Start from | Process pattern in `CONTEXT.md` |
| Look at | `src/test/java/neqsim/process/` for similar flowsheets |
| Code goes in | Notebook (best for presentation) or test (best for regression) |
| Verify by | Mass/energy balance, physical reasonableness |
| AI agent | `@solve.process` or `@process.model` |

Key patterns:
- Equipment connects via streams: `new Compressor("comp", sep.getGasOutStream())`
- `ProcessSystem.run()` handles all sequencing
- Use `Recycle` for recirculation loops, `Adjuster` for spec-matching
- Get results: `equipment.getOutletStream().getFlowRate("kg/hr")`

**If the equipment doesn't exist:** See the extending-equipment guide at
`docs/development/extending_process_equipment.md`.

---

### Type C: PVT Study

**"Run CME / CVD / differential liberation on this oil"**

| Aspect | Detail |
|--------|--------|
| Start from | `src/main/java/neqsim/pvtsimulation/simulation/` |
| Experiments | CME, CVD, DL, SaturationPressure, GOR, SwellingTest, MMP |
| Verify by | Compare against lab data |
| AI agent | `@pvt.simulation` |

---

### Type D: Standards Calculation

**"Calculate Wobbe index, GCV, hydrocarbon dew point per ISO 6976"**

| Aspect | Detail |
|--------|--------|
| Start from | `src/main/java/neqsim/standards/gasquality/` |
| Verify by | Standard reference values, round-robin test results |
| AI agent | `@gas.quality` |

---

### Type E: New Feature / Bug Fix

**"Add anti-surge to the compressor" / "Fix the CPA solver for CO2-water"**

| Aspect | Detail |
|--------|--------|
| Find the class | `src/main/java/neqsim/process/equipment/<package>/` |
| Read the interface | `*Interface.java` for method contracts |
| Write tests first | Mirror location in `src/test/java/neqsim/` |
| Verify by | `.\mvnw.cmd test -Dtest=YourTest` then `checkstyle:check` |
| AI agent | `@neqsim.test` for test writing |

---

### Type F: Mechanical Design

**"Wall thickness per DNV-OS-F101 for a 20-inch subsea pipeline"**

| Aspect | Detail |
|--------|--------|
| Pattern | Mechanical Design section in `.github/copilot-instructions.md` |
| Design data | `src/main/resources/designdata/` |
| AI agent | `@mechanical.design` |

---

## Extending the Physics Engine Mid-Task

This is the key advantage of working inside the repo. When you hit a gap —
a missing property, a missing equipment type, a missing correlation — you
don't stop and file an issue. You add it.

### Decision: Use vs. Extend

```
Can I solve this with existing methods?
  YES → Write a test/notebook using current API
  NO  → What's missing?
    ├── A property method    → extend physicalproperties/ or thermo/
    ├── A process equipment  → extend process/equipment/
    ├── A thermo model/EOS   → extend thermo/system/ + phase/ + component/
    ├── A PVT experiment     → extend pvtsimulation/
    └── A standard calc      → extend standards/
```

### Pattern: Adding a Property Method

1. **Find the right layer** — Properties live at three levels:
   - System level: `SystemThermo` → `getDensity()`, `getEnthalpy()`
   - Phase level: `Phase` → `getViscosity()`, `getConductivity()`
   - Component level: `Component` → `getFugacityCoefficient()`

2. **Read the existing method** closest to what you need

3. **Add your method**, following the naming convention:
   ```java
   // In the Phase class or PhysicalProperties class
   public double getMyNewProperty(String unit) {
       // calculation...
   }
   ```

4. **Write a test** validating against known values

5. **Use it** in the original task

See `docs/development/extending_physical_properties.md` for the full guide.

### Pattern: Adding Process Equipment

1. Choose base class: `TwoPortEquipment` (single in/out) or `ProcessEquipmentBaseClass`
2. Implement `run(UUID id)` with the physics
3. Wire streams in the constructor
4. Add to `ProcessSystem` and test
5. See `docs/development/extending_process_equipment.md` for details

### Pattern: Adding a Thermodynamic Model

1. Create three classes: `SystemYourEos`, `PhaseYourEos`, `ComponentYourEos`
2. Override EOS parameters (a, b, alpha function)
3. Register characterization models
4. See `docs/development/extending_thermodynamic_models.md` for details

---

## How AI Agents Coordinate

The 12 specialist agents in `.github/agents/` are designed for specific task
types. They share the same codebase context but differ in what they optimize for.

### Agent Selection Guide

| Agent | When to Use | Output |
|-------|-------------|--------|
| `@thermo.fluid` | Fluid setup, EOS selection, flash, properties | Java code or notebook cell |
| `@solve.process` | Complete simulation task → working notebook | Full Jupyter notebook |
| `@process.model` | Process flowsheet design, equipment sizing | Process code |
| `@pvt.simulation` | PVT lab experiments | PVT results + plots |
| `@gas.quality` | Gas quality per ISO/GPA standards | Standards results |
| `@mechanical.design` | Wall thickness, structural design | Design report JSON |
| `@neqsim.test` | Writing regression/unit tests | JUnit 5 test class |
| `@notebook.example` | Creating example notebooks | Jupyter notebook |
| `@flow.assurance` | Hydrates, wax, corrosion, slugging | Analysis + mitigation |
| `@safety.depressuring` | Depressurization, PSV, fire cases | Safety analysis |
| `@documentation` | Wiki pages, guides, cookbooks | Markdown files |

### Chaining Agents

Complex tasks often need multiple agent capabilities. The general Copilot Chat
(without an `@agent` prefix) can do everything — read, write, test, extend.
Specialist agents are shortcuts for well-defined task shapes.

Example of a multi-step task:
```
1. @thermo.fluid  → "Create a CPA fluid for gas with 5% MEG and water"
2. @solve.process → "Build a TEG dehydration unit using that fluid"
3. @neqsim.test   → "Write regression tests for the dehydration results"
```

### What the AI Can See

The AI reads actual Java source files, so it:
- Verifies method signatures before calling them (no hallucinated APIs)
- Reads existing tests to match coding style
- Checks `TASK_LOG.md` for prior solutions
- Follows `copilot-instructions.md` rules (Java 8, JavaDoc, checkstyle)
- Can run `mvnw.cmd test` and read the output

### What the AI Cannot Do (Limitations)

- **No persistent memory** across sessions — `TASK_LOG.md` is the workaround
- **Cannot run the JVM** — it writes code and runs Maven, but doesn't inspect
  runtime state interactively (use Jupyter notebooks for interactive exploration)
- **May get physics wrong** — always verify numbers against known references
- **Cannot access external data** — if you need lab data or NIST values, paste them in

---

## The Develop-While-Solving Workflow in Detail

This section walks through a realistic example of solving an engineering task
while simultaneously improving the NeqSim API.

### Example: "Calculate JT cooling across a valve for a rich gas"

**Step 1 — Orient**

AI reads `CONTEXT.md`, searches `TASK_LOG.md` for "Joule-Thomson" or "JT" or
"valve" or "isenthalpic". Finds no prior solution.

**Step 2 — Classify**

This is a **Type A** (Property) + **Type B** (Process) hybrid.
The JT effect is a thermodynamic property; the valve is process equipment.

**Step 3 — Investigate**

AI searches for existing valve tests:
```
grep_search: "ThrottlingValve" in src/test/java/neqsim/
→ finds ValveTest.java with basic valve operations
```

AI reads the `ThrottlingValve` class to check the API:
```
ThrottlingValve valve = new ThrottlingValve("JT Valve", stream);
valve.setOutletPressure(pressureOut);
// run() does isenthalpic flash internally
```

**Step 4 — Solve**

AI writes a test:
```java
@Test
void testJTCoolingRichGas() {
    SystemInterface fluid = new SystemPrEos(273.15 + 40.0, 200.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.08);
    fluid.addComponent("n-butane", 0.05);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("CO2", 0.04);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    process.add(feed);

    ThrottlingValve valve = new ThrottlingValve("JT", feed);
    valve.setOutletPressure(50.0, "bara");
    process.add(valve);

    process.run();

    double inletT = feed.getTemperature("C");
    double outletT = valve.getOutletStream().getTemperature("C");
    double cooling = inletT - outletT;

    assertTrue(cooling > 0, "JT cooling should be positive for this gas");
    assertTrue(cooling < 50, "JT cooling should be < 50°C");
    // JT coefficient ~ 0.3–0.5 K/bar for this gas
    double jtCoeff = cooling / (200.0 - 50.0);
    assertTrue(jtCoeff > 0.1 && jtCoeff < 1.0);
}
```

**Step 5 — Run**

```powershell
.\mvnw.cmd test -Dtest=ValveJTTest
```

Tests pass. Results: inlet 40°C, outlet -5°C, JT cooling = 45°C,
coefficient = 0.30 K/bar. Physically reasonable for a rich gas.

**Step 6 — Extend (if needed)**

Suppose you also want the JT coefficient as a direct API call and it doesn't
exist on the fluid. You would:

1. Add `getJouleThomsonCoefficient()` to `SystemThermo.java`
2. Implement using $\mu_{JT} = \frac{1}{C_p}\left(T\frac{\partial V}{\partial T}\bigg|_P - V\right)$
3. Write a test comparing against the numerical result from the valve
4. Now the next person who needs $\mu_{JT}$ can just call the method

**Step 7 — Log**

Add to `TASK_LOG.md`:
```markdown
### 2026-03-01 — JT cooling for rich gas across valve
**Type:** A+B (Property + Process)
**Keywords:** Joule-Thomson, JT, valve, isenthalpic, cooling, rich gas
**Solution:** src/test/java/neqsim/process/equipment/valve/ValveJTTest.java
**Notes:** Used SystemPrEos. JT coefficient ≈ 0.30 K/bar at 200 bar.
Rich gas (C1-C5 + CO2) cools from 40°C to -5°C expanding to 50 bar.
```

---

## Finding Similar Solved Problems

Before writing anything, search for prior solutions:

```
1. TASK_LOG.md     → keyword search for the topic
2. Tests           → grep in src/test/java/neqsim/
3. Notebooks       → ls examples/notebooks/
4. Wiki            → docs/wiki/ (60+ topic files)
5. Reference index → docs/REFERENCE_MANUAL_INDEX.md
```

### Search Commands

```powershell
# Search task log
Select-String -Path "docs\development\TASK_LOG.md" -Pattern "compressor|compression"

# Search tests
Select-String -Path "src\test\java\neqsim\**\*.java" -Pattern "separator" -Recurse |
  Select-Object -First 10

# List example notebooks
Get-ChildItem examples\notebooks\*.ipynb | Select-Object Name
```

---

## Verification Checklist

Before considering a task done:

- [ ] **Compiles**: `.\mvnw.cmd compile` passes
- [ ] **Tests pass**: `.\mvnw.cmd test -Dtest=YourTest` green
- [ ] **Physics check**: Are the numbers physically reasonable?
  - Temperatures between -273°C and ~1000°C
  - Pressures positive
  - Mass balance closes (in = out ± 0.1%)
  - Energy balance closes
  - Densities in expected range (gas: 1–200 kg/m3, liquid: 500–1500 kg/m3)
  - Compressor outlet hotter than inlet
  - JT cooling positive for most gases
- [ ] **Java 8**: No `var`, `List.of()`, `String.repeat()`, text blocks
- [ ] **Style**: `.\mvnw.cmd checkstyle:check` passes
- [ ] **JavaDoc**: All methods documented with `@param`, `@return`, `@throws`
- [ ] **Logged**: Entry added to `docs/development/TASK_LOG.md`
- [ ] **Figures saved**: All plots saved to `figures/` directory (not just displayed inline)
- [ ] **Report generated** (if deliverable): Word document builds end-to-end via `python generate_report.py`
- [ ] **Task folder complete**: All 4 steps documented in `task_solve/YYYY-MM-DD_description/`
- [ ] **Reusable outputs promoted**: Tests, notebooks, or API extensions moved back into the repo

---

## Making Solutions Reusable

Every solved task should be findable by the next person (or AI session).
Choose one or more of these output formats:

### Option 1: Write a Test (always do this)

Put a test in `src/test/java/neqsim/<matching_package>/`. Tests are:
- Searchable (grep finds them)
- Runnable (proof the solution works)
- Regression-proof (CI catches breakage)
- Self-documenting (the code is the example)

### Option 2: Write a Notebook (best for process engineers)

Put a notebook in `examples/notebooks/` with:
- Clear title and description
- Colab badge for one-click running from a browser
- Dual-boot setup cell (works with both `devtools` and `pip install neqsim`)
- Markdown cells explaining the engineering reasoning
- **Save all figures to disk** — every plot should be saved as a PNG/SVG file
  alongside the notebook so results survive kernel restarts and can be reused
  in reports (see Option 5 below)

**Figure-saving pattern** (do this for every plot cell):

```python
import matplotlib
matplotlib.use('Agg')          # non-interactive backend, no GUI needed
import matplotlib.pyplot as plt
import os

FIG_DIR = os.path.join(os.path.dirname(os.path.abspath('__file__')), 'figures')
os.makedirs(FIG_DIR, exist_ok=True)

fig, ax = plt.subplots(figsize=(10, 6))
ax.plot(time_hrs, temperature_C, 'b-', linewidth=2)
ax.set_xlabel('Time (hours)')
ax.set_ylabel('Temperature (°C)')
ax.set_title('Gas Temperature During Filling')
ax.grid(True, alpha=0.3)
fig.tight_layout()
fig.savefig(os.path.join(FIG_DIR, 'filling_temperature.png'), dpi=150)
plt.close(fig)                 # free memory, avoid display issues
print(f"Saved: {os.path.join(FIG_DIR, 'filling_temperature.png')}")
```

**Key rules for figure saving:**
- Use `matplotlib.use('Agg')` at the top of the notebook to avoid GUI backend
  issues when running headless or via scripts
- Always call `plt.close(fig)` after saving to prevent memory leaks in long
  simulation loops
- Save to a `figures/` subdirectory next to the notebook
- Use descriptive filenames: `filling_temperature.png`, not `fig1.png`
- Print the saved path so the report generator can find them

### Option 3: Add a Doc Page (for recurring questions)

Add `docs/wiki/<topic>.md` or `docs/cookbook/<recipe>.md`.
Update `docs/REFERENCE_MANUAL_INDEX.md` with the link.

### Option 4: Log It (minimum bar — always do this)

Add an entry to `docs/development/TASK_LOG.md`. This is the searchable
memory that survives across AI sessions.

### Option 5: Generate a Word Report (best for deliverables)

For engineering studies and client deliverables, generate a Word document
using `python-docx`. This was proven on the CNG Tank Modelling project
(`examples/CNGtankmodelling/generate_report.py`).

**Architecture: a standalone report-generator script:**

```
your_project/
├── simulation_notebook.ipynb   # Interactive exploration
├── generate_report.py          # Headless: runs sims + writes .docx
├── figures/                    # PNG files saved by both notebook and script
└── YourReport.docx             # Output
```

**Why a separate script instead of the notebook?**
- Reproducible: runs end-to-end without Jupyter kernel state
- Headless: works on CI/CD or via `python generate_report.py`
- Figures and text stay in sync because both are generated in one pass
- Version-controllable (`.py` diffs cleanly; `.ipynb` JSON does not)

**Report generator pattern:**

```python
import matplotlib
matplotlib.use('Agg')  # MUST be before any pyplot import
import matplotlib.pyplot as plt
from docx import Document
from docx.shared import Inches, Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH
import os

# ── Constants ────────────────────────────────────────────
FIG_DIR = os.path.join(os.path.dirname(__file__), 'figures')
os.makedirs(FIG_DIR, exist_ok=True)

# ── 1. Run simulations ──────────────────────────────────
def run_baseline():
    """Run the baseline simulation and return results dict."""
    # ... NeqSim simulation code ...
    return {'time': time_list, 'temperature': temp_list, 'pressure': pres_list}

# ── 2. Generate figures ─────────────────────────────────
def make_temperature_figure(results):
    """Create and save temperature plot, return path."""
    fig, ax = plt.subplots(figsize=(10, 6))
    ax.plot(results['time'], results['temperature'], 'b-', lw=2)
    ax.set_xlabel('Time (hours)')
    ax.set_ylabel('Temperature (°C)')
    ax.set_title('Gas Temperature')
    ax.grid(True, alpha=0.3)
    path = os.path.join(FIG_DIR, 'temperature.png')
    fig.savefig(path, dpi=150, bbox_inches='tight')
    plt.close(fig)
    return path

# ── 3. Build Word document ──────────────────────────────
def build_report(results, fig_paths):
    """Assemble the Word document."""
    doc = Document()
    doc.add_heading('Simulation Report', level=0)

    doc.add_heading('1. Introduction', level=1)
    doc.add_paragraph('This report presents the simulation results for ...')

    doc.add_heading('2. Results', level=1)
    doc.add_paragraph(
        f"Peak temperature: {max(results['temperature']):.1f} °C"
    )
    doc.add_picture(fig_paths['temperature'], width=Inches(6.0))
    last = doc.paragraphs[-1]
    last.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.save('MyReport.docx')
    print('Report saved: MyReport.docx')

# ── 4. Main ─────────────────────────────────────────────
if __name__ == '__main__':
    results = run_baseline()
    fig_paths = {
        'temperature': make_temperature_figure(results),
    }
    build_report(results, fig_paths)
```

**Key lessons from the CNG report project:**

| Lesson | Detail |
|--------|--------|
| `matplotlib.use('Agg')` first | Must come before `import matplotlib.pyplot`. Otherwise the script hangs or crashes without a display. |
| Separate sim from plotting | Run all simulations first, collect results dicts, then generate all figures. Keeps the script restartable. |
| Use f-strings for dynamic text | Report text should reference computed values (`f"{max_temp:.1f} °C"`) so the document stays in sync with simulation. |
| `plt.close(fig)` always | Without this, memory grows with each figure in a loop (sensitivity studies generate many). |
| `bbox_inches='tight'` | Prevents axis labels from being clipped in the saved PNG. |
| Handle simulation failures | Wrap each simulation batch in try/except so one failure doesn't kill the whole report. Log the error and skip. |
| `dpi=150` is enough | Higher DPI makes the .docx file huge with no visible quality gain in print. |
| Save figures AND embed them | Save to `figures/` for reuse, and embed in the .docx. Both are needed. |

**Rendering equations natively in Word (OMML):**

Word supports native Office Math Markup Language (OMML) equations that render
beautifully — the same typeset quality as the Word equation editor. Use the
`latex2mathml` + XSLT approach to convert LaTeX strings into OMML XML and
insert them directly into the document.

```python
import latex2mathml.converter
from lxml import etree
from docx.oxml.ns import qn

# ── XSLT stylesheet (ships with Word / Office) ──────────
# Download MML2OMML.XSL from your Office install or from:
# C:\Program Files\Microsoft Office\root\Office16\MML2OMML.XSL
# or bundle it with the project.
XSLT_PATH = os.path.join(os.path.dirname(__file__), 'MML2OMML.XSL')
xslt = etree.parse(XSLT_PATH)
transform = etree.XSLT(xslt)

def add_latex_equation(paragraph, latex_str):
    """Insert a LaTeX equation as a native Word OMML equation.

    Args:
        paragraph: A python-docx Paragraph object to append the equation to.
        latex_str: LaTeX math string WITHOUT delimiters (no $ or $$).
                   Example: r'P = \\frac{RT}{V - b}'
    """
    # 1. LaTeX → MathML
    mathml_str = latex2mathml.converter.convert(latex_str)

    # 2. MathML → OMML via Microsoft's XSLT
    mathml_tree = etree.fromstring(mathml_str.encode('utf-8'))
    omml_tree = transform(mathml_tree)

    # 3. Insert into paragraph's XML
    for omml_element in omml_tree.getroot():
        paragraph._element.append(omml_element)

# ── Usage ────────────────────────────────────────────────
doc = Document()
doc.add_heading('Energy Balance', level=1)

p = doc.add_paragraph('The first law for a constant-volume system:')
p = doc.add_paragraph()  # new paragraph for equation
add_latex_equation(p, r'm \\, c_v \\, \\frac{dT}{dt} = \\dot{Q} - \\dot{m} \\, h')

p = doc.add_paragraph('The SRK equation of state:')
p = doc.add_paragraph()
add_latex_equation(p, r'P = \\frac{RT}{V-b} - \\frac{a(T)}{V(V+b)}')

doc.save('report_with_equations.docx')
```

The equations above render in Word exactly like the built-in equation editor —
fully scalable, editable, and print-quality. No images, no blurry screenshots.

**Setup for OMML equations:**

1. Install `latex2mathml` and `lxml`:
   ```
   pip install latex2mathml lxml
   ```
2. Copy `MML2OMML.XSL` from your Office installation:
   ```
   C:\Program Files\Microsoft Office\root\Office16\MML2OMML.XSL
   ```
   Bundle it next to `generate_report.py` so the script is self-contained.

3. Common LaTeX expressions for NeqSim reports:

   | What | LaTeX |
   |------|-------|
   | SRK EOS | `r'P = \frac{RT}{V-b} - \frac{a(T)}{V(V+b)}'` |
   | Energy balance | `r'm c_v \frac{dT}{dt} = \dot{Q} - \dot{m} h'` |
   | Churchill-Chu | `r'Nu = \left[0.825 + \frac{0.387 Ra^{1/6}}{(1+(0.492/Pr)^{9/16})^{8/27}}\right]^2'` |
   | Heat transfer | `r'Q = h A (T_w - T_f)'` |
   | Compressibility | `r'Z = \frac{PV}{nRT}'` |

**Dependencies:**
```
pip install python-docx matplotlib latex2mathml lxml neqsim
```

---

## Common Pitfalls

| Mistake | Symptom | Fix |
|---------|---------|-----|
| Forgot mixing rule | Wrong/no results, NaN | `fluid.setMixingRule("classic")` |
| Temperature in °C to constructor | Wildly wrong properties | Use Kelvin: `273.15 + T_celsius` |
| Shared fluid state | Equipment B sees A's changes | `fluid.clone()` before branching |
| Duplicate equipment names | Exception from ProcessSystem | Every name must be unique |
| Java 9+ syntax | CI build fails | See forbidden features in `CONTEXT.md` |
| Missing `fluid.init(3)` | Properties return 0 or NaN | Always call after flash |
| Using `getTemperature()` as °C | Off by 273 | Returns Kelvin — subtract 273.15 |
| Wrong mixing rule number for CPA | Convergence failure | Use `10` (numeric) for CPA EOS |
| Not setting `setMultiPhaseCheck(true)` | Missing liquid phase | Required for water/heavy systems |
| Forgot `setFlowRate` on stream | Zero flow everywhere | Set before running |
| `matplotlib.use('Agg')` after pyplot | Script hangs or crashes | Put `matplotlib.use('Agg')` before any `import matplotlib.pyplot` |
| Not closing figures in loops | Memory grows, OOM | Always call `plt.close(fig)` after `savefig()` |
| Hardcoded results in report text | Report drifts from simulation | Use f-strings: `f"{value:.1f}"` so text updates automatically |
| Saving figures without `bbox_inches='tight'` | Axis labels clipped | Add `bbox_inches='tight'` to `savefig()` |

---

## Copilot Chat Agents

12 specialist agents in `.github/agents/`:

| Agent | Best For |
|-------|----------|
| `@thermo.fluid` | EOS selection, fluid creation, flash, properties |
| `@solve.process` | Complete process simulation → working notebook |
| `@process.model` | Process flowsheet design |
| `@pvt.simulation` | PVT experiments (CME, CVD, etc.) |
| `@gas.quality` | Gas quality standards (GCV, Wobbe) |
| `@mechanical.design` | Wall thickness, ASME/DNV design |
| `@neqsim.test` | Writing JUnit 5 tests |
| `@notebook.example` | Creating example notebooks |
| `@flow.assurance` | Hydrates, wax, corrosion, slugging |
| `@safety.depressuring` | Depressurization, PSV, fire cases |
| `@documentation` | Writing docs and wiki pages |

---

## Session Start Template

When starting a new Copilot Chat session, paste this to establish context:

```
I'm working in the NeqSim repo (Java thermodynamics + process simulation).
Read CONTEXT.md for orientation.
Task: [describe your task here]
Search docs/development/TASK_LOG.md for similar past tasks.
```

---

## Process Engineer Quick Start

If you're a process engineer (not a developer):

1. Open VS Code with the NeqSim repo
2. Run: `python devtools/new_task.py "your engineering question" --type B`
3. Open Copilot Chat and paste the prompt from the generated README
4. The agent creates a notebook, runs every cell, and hands back results
5. Run `python step4_report/generate_report.py` to create a Word report

You don't need to know Java, Maven, or git. The script and agent handle everything.

---

## Related Documentation

| Document | Purpose |
|----------|---------|
| `devtools/new_task.py` | Script to create task folders (auto-bootstraps `task_solve/`) |
| `task_solve/README.md` | AI-supported task-solving workflow (4-step process) |
| `task_solve/TASK_TEMPLATE/` | Template folder with prompts and report generator |
| `CONTEXT.md` | 60-second repo orientation |
| `docs/development/CODE_PATTERNS.md` | Copy-paste code starters |
| `docs/development/TASK_LOG.md` | Persistent task memory |
| `docs/development/extending_process_equipment.md` | Adding new equipment |
| `docs/development/extending_thermodynamic_models.md` | Adding new EOS |
| `docs/development/extending_physical_properties.md` | Adding property models |
| `docs/development/jupyter_development_workflow.md` | Jupyter + Java dev loop |
| `.github/copilot-instructions.md` | Full AI coding rules |
