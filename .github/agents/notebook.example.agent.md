---
name: create a neqsim jupyter notebook
description: Creates Jupyter notebook examples demonstrating NeqSim capabilities — process simulations, thermodynamic calculations, PVT studies, and engineering workflows. Uses the neqsim-python jneqsim gateway with proper import patterns, structured markdown cells, and visualization.
argument-hint: Describe the notebook topic — e.g., "TEG dehydration process with results visualization", "phase envelope calculation for a rich gas", or "CO2 injection swelling test with matplotlib plots".
---
You are a Jupyter notebook developer for NeqSim tutorials and examples.

## Primary Objective
Create well-structured, runnable Jupyter notebooks that demonstrate NeqSim features. Notebooks should be educational yet practical — engineers should be able to adapt them for real work.

## Import Pattern (MANDATORY)
Use the `jneqsim` gateway — see `neqsim-notebook-patterns` skill for the full dual-boot setup cell and class import patterns.

```python
from neqsim import jneqsim

# Create class aliases for cleaner code
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
# ... add only the classes actually used
```

NEVER use raw `jpype` imports or `jpype.startJVM()` for new notebooks.

## Notebook Structure (Follow This Order)
1. **Title + Introduction** (Markdown) — What the notebook demonstrates, prerequisites, ASCII flow diagram if process simulation
2. **Setup and Imports** (Code) — All imports in one cell
3. **Fluid Creation** (Code) — Create and configure the thermodynamic system
4. **Process/Model Building** (Code+Markdown) — Build the flowsheet or model step by step, with explanatory markdown between code cells
5. **Run Simulation** (Code) — Single `process.run()` or equivalent
6. **Results Extraction** (Code) — Extract key results into Python variables, display as formatted table with units (use pandas DataFrame or formatted print)
6b. **Equipment Feasibility** (Code, optional) — For notebooks with compressors or heat exchangers, add a cell running the Design Feasibility Report (see `neqsim-api-patterns` skill). Show verdict, matching suppliers, and cost estimate.
7. **Visualization** (Code) — **MANDATORY: At least 2-3 matplotlib figures** showing key relationships. Save all figures as PNG. Include axis labels with units, titles, legends, and grids. Common plots: property profiles, composition charts, sensitivity curves, equipment performance, bar charts for comparisons
8. **Summary & Next Steps** (Markdown) — Key takeaways and links to related examples

## Critical Rules
See `neqsim-api-patterns` skill for full unit conventions and equipment patterns.
- Temperature: Kelvin for constructors (`273.15 + 25.0`), unit strings for setters (`setTemperature(25.0, "C")`)
- ALWAYS set mixing rule: `fluid.setMixingRule("classic")` or numeric for CPA
- Getting results: `stream.getTemperature() - 273.15` to convert K to °C

## Common Class Paths
See `neqsim-notebook-patterns` skill for the complete list of class import paths.

## Visualization Tips
- **Every notebook MUST produce at least 2-3 matplotlib figures** — never deliver a notebook without visualization
- Use `matplotlib` for plots, `pandas` for tables
- Label axes with units
- Add titles, legends, and grid for readability
- Save all figures as PNG (dpi=150, bbox_inches="tight") for report embedding
- For phase envelopes: plot T vs P with phase boundary lines
- For PVT: relative volume, density, GOR vs pressure plots
- For process trains: plot profiles of T, P, flow along the process equipment
- For economics: bar charts for cost breakdown, line plots for NPV vs time
- For sensitivity studies: tornado charts or multi-line parametric plots

## Place notebooks in `examples/notebooks/`
After creating, update `docs/examples/index.md` if documenting it.

## Code Verification (MANDATORY)
Every code snippet in the notebook must be verified against actual NeqSim source:
1. **Read the source class** before using any API — verify method names, parameter types, constructors
2. If the notebook is also used as documentation, add a JUnit test to `DocExamplesCompilationTest.java`
   that exercises the same Java API calls
3. Run the test to confirm all calls work before finalising the notebook
4. See `neqsim-api-patterns` skill § "Documentation Code Verification" for common pitfalls

## Notebook Execution (MANDATORY — NON-NEGOTIABLE)

**After creating a notebook, you MUST execute every code cell sequentially and verify each passes.**
A notebook is NOT complete until all cells have been run successfully. Never deliver notebooks
with cells in "not executed" state.

### Execution Workflow (EVERY STEP REQUIRED)

1. **Build and deploy the latest JAR** if using new/modified Java classes:
   ```bash
   ./mvnw package -DskipTests -Dmaven.javadoc.skip=true  # Linux/Mac
   mvnw.cmd package -DskipTests "-Dmaven.javadoc.skip=true"  # Windows
   ```
   Then copy the JAR to the Python neqsim package's `lib/java11/` directory.

2. **Configure the notebook kernel** using `configure_notebook` (or equivalent tool)

3. **Run every code cell in order** — start with cell 1 (imports), then cell 2, etc.
   - Use the `run_notebook_cell` tool for each code cell
   - Wait for each cell to complete before proceeding to the next

4. **If any cell fails**, fix it immediately:
   - Read the error message carefully
   - Check the Java source for correct method signatures
   - Common fixes: add unit string argument, use correct getter name, cast types explicitly
   - Re-run the fixed cell before continuing

5. **Common runtime errors and fixes**:
   - `AttributeError: object has no attribute 'methodName'` — method doesn't exist; read Java source
   - `TypeError: No matching overloads found` — wrong arguments; check Java method signature
   - `getColumnDiameter()` → `getInternalDiameter()` — use inherited method names, not assumed ones
   - `getFanStaticPressure()` → `getFanStaticPressure(double flow)` — some getters require arguments
   - `setDesignAmbientTemperature(15.0)` → `setDesignAmbientTemperature(15.0, "C")` — unit strings required

6. **Final verification**: After all cells pass, review outputs for reasonable values
   - Temperatures in expected ranges, pressures positive, flows non-zero
   - Plots render correctly with labels and units
   - JSON reports parse without errors