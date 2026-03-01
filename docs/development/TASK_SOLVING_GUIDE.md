---
title: "Task Solving Guide"
description: "Step-by-step workflow for solving engineering tasks inside the NeqSim repo using AI (Copilot + Claude). Covers the develop-while-solving loop, task classification, AI agent coordination, and how to build persistent knowledge."
---

# Task Solving Guide

How to solve engineering tasks inside the NeqSim repo using AI, while
simultaneously developing the physics engine — and making every solution
findable for the next session.

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

### Option 3: Add a Doc Page (for recurring questions)

Add `docs/wiki/<topic>.md` or `docs/cookbook/<recipe>.md`.
Update `docs/REFERENCE_MANUAL_INDEX.md` with the link.

### Option 4: Log It (minimum bar — always do this)

Add an entry to `docs/development/TASK_LOG.md`. This is the searchable
memory that survives across AI sessions.

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
2. Open Copilot Chat
3. Type: `@solve.process [describe what you want to simulate]`
4. The agent creates a notebook, runs every cell, and hands back results
5. Open the notebook in `examples/notebooks/` to see the code and plots

You don't need to know Java, Maven, or git. The agent handles everything.

---

## Related Documentation

| Document | Purpose |
|----------|---------|
| `CONTEXT.md` | 60-second repo orientation |
| `docs/development/CODE_PATTERNS.md` | Copy-paste code starters |
| `docs/development/TASK_LOG.md` | Persistent task memory |
| `docs/development/extending_process_equipment.md` | Adding new equipment |
| `docs/development/extending_thermodynamic_models.md` | Adding new EOS |
| `docs/development/extending_physical_properties.md` | Adding property models |
| `docs/development/jupyter_development_workflow.md` | Jupyter + Java dev loop |
| `.github/copilot-instructions.md` | Full AI coding rules |
