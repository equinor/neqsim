---
title: "Process-Study Report: one command from a NeqSim model to a reviewable report"
description: "How to turn any NeqSim ProcessSystem or ProcessModel (Python file, JSON definition, serialized .neqsim file or extracted data) into a validated process-study report with the neqsim process-report command: engineering basis, all-stream tables for every operating case, balances, BFD and A3 PFD sheets, standards register and a fail-closed validation report."
---

# Process-Study Report

`neqsim process-report` turns a **solved NeqSim model** into a reviewable report package in one step: a Word
report (and PDF where Microsoft Word is available), a block flow diagram (BFD), A3 process flow diagram (PFD)
sheets with stream numbers, complete stream and component tables for every operating case, mass, component and
energy balances, a standards register and a machine-readable validation report. The same package is produced for
a ten-unit train and for a plant with more than a hundred streams.

The pipeline lives in `devtools/` (`process_report`, `process_study_report`, `psr_extract`, `psr_balances`,
`psr_diagrams`, `psr_report`, `psr_validate`, `psr_draft`). The skill
[process-study-report](../../.github/skills/process-study-report/SKILL.md) describes the method; the agent
[@process-study-report](../../.github/agents/process-study-report.agent.md) drives it for you. This page is the
how-to. Every command and code block below is executed by
[test_process_study_report_documentation.py](../test_process_study_report_documentation.py): blocks marked
`file:` are written to a folder, `run` blocks are run in document order, and the output table in section 5 is
checked against the folder that the first run produces.

## 1. What the report contains and when to use it

| Part | Content |
|------|---------|
| Front matter | Cover with the readiness label, document control, contents, executive summary |
| Basis | Objective, audience, maturity, boundaries, operating cases, acceptance criteria, sources inspected, model and EOS, conventions, data classification, assumptions, data gaps |
| Results | Stream tables (properties, phases, composition) for **every stream in every case**, unit results, balances, case comparison |
| Drawings | BFD and PFD sheets (SVG, PDF, PNG) from the simulated topology, with off-page connectors and a layout specification |
| Standards | Standards register with edition, status, source and what may be claimed |
| Validation | More than 35 fail-closed checks, also appended to the report |

Use it when a study needs a **process description, design basis, heat and material balance, stream tables or PFD/BFD
drawings** that match a NeqSim flowsheet. Do not use it for a single flash (use the `mcp_neqsim_*` tools) or for
the narrative task report of `neqsim report <task>`; the two complement each other, and the process-study report
writes a `process_study_report` block into the task `results.json` so that the task report can cite it.

## 2. Quick start

Interpreter: the NeqSim environment of your installation. Replace `neqsim` with
`python -m neqsim_cli` when the command is not on the path. Add `--no-word` on machines without Microsoft Word.

The four model sources are detected from the file:

| Source | Detected from | Cases without extra Python |
|--------|---------------|----------------------------|
| Python file defining `build(case)` or `model` | `.py` | `cases.yaml` overrides and `parameters` |
| NeqSim JSON process definition (`process` + `fluid`, or `areas`) | `.json` | `cases.yaml` overrides |
| Serialized process saved with `saveToNeqsim` | `.neqsim` | `cases.yaml` overrides |
| Data written earlier by the toolkit | `process_data.json` | none (cases are inside the file) |

Create the two files of the worked example of section 4 in an empty folder (`gas_plant_model.py` and `cases.yaml`)
and run:

<!-- run -->
```powershell
neqsim process-report gas_plant_model.py --cases cases.yaml --out report --no-word
```

The command solves the model once per case, extracts everything, drafts the engineering basis, builds the
drawings and the report and prints the validation status and the readiness label. Without a confirmed basis the
label is **SCREENING - basis not confirmed**; section 3 explains how to confirm it.

A JSON definition works the same way. The file below is a stabilisation train without compressors; the second file
holds its operating cases.

<!-- file: oil_stabilisation.json -->
```json
{
  "fluid": {
    "model": "SRK",
    "temperature": 343.15,
    "pressure": 70.0,
    "mixingRule": "classic",
    "multiPhaseCheck": true,
    "components": {
      "nitrogen": 0.004,
      "CO2": 0.015,
      "methane": 0.30,
      "ethane": 0.06,
      "propane": 0.05,
      "i-butane": 0.01,
      "n-butane": 0.03,
      "i-pentane": 0.015,
      "n-pentane": 0.02,
      "n-hexane": 0.07,
      "n-heptane": 0.09,
      "n-octane": 0.08,
      "n-nonane": 0.07,
      "nC10": 0.056,
      "water": 0.12
    }
  },
  "process": [
    {"type": "Stream", "name": "well fluid", "properties": {"flowRate": [90000.0, "kg/hr"]}},
    {"type": "ThreePhaseSeparator", "name": "inlet separator", "inlet": "well fluid"},
    {"type": "ThrottlingValve", "name": "oil valve 1", "inlet": "inlet separator.oilOut", "properties": {"outletPressure": 20.0}},
    {"type": "Heater", "name": "oil heater", "inlet": "oil valve 1.outlet", "properties": {"outTemperature": 358.15}},
    {"type": "ThreePhaseSeparator", "name": "second stage separator", "inlet": "oil heater.outlet"},
    {"type": "ThrottlingValve", "name": "oil valve 2", "inlet": "second stage separator.oilOut", "properties": {"outletPressure": 2.0}},
    {"type": "Separator", "name": "stabiliser flash drum", "inlet": "oil valve 2.outlet"},
    {"type": "Pump", "name": "oil export pump", "inlet": "stabiliser flash drum.liquidOut", "properties": {"outletPressure": 30.0}},
    {"type": "Pump", "name": "water pump", "inlet": "inlet separator.waterOut", "properties": {"outletPressure": 100.0}}
  ],
  "autoRun": true
}
```

<!-- file: oil_cases.yaml -->
```yaml
cases:
  - id: A
    label: Base case
    role: design
    description: Well fluid 90000 kg/h, heater outlet 85 C
    data_source: Synthetic example inputs
  - id: B
    label: Low rate, cooler heater
    role: scenario
    description: Well fluid 60000 kg/h, heater outlet 70 C
    data_source: Synthetic example inputs
    set:
      "well fluid.flowRate": [60000, kg/hr]
      "oil heater.outletTemperature": [70, C]
```

<!-- run -->
```powershell
neqsim process-report oil_stabilisation.json --cases oil_cases.yaml --out oil_report --no-word
```

The JSON builder drops unknown equipment silently; the command compares the entries of `process` with the units
that were built and stops with the list of names that were not created.

A serialized process is solved and reported in the same way. The first block saves one (the JSON process is
rebuilt with the JSON builder and written with `saveToNeqsim`):

<!-- file: save_process.py; run -->
```python
from jneqsim import neqsim

with open("oil_stabilisation.json", encoding="utf-8") as handle:
    result = neqsim.process.processmodel.ProcessSystem.fromJsonAndRun(handle.read())
process = result.getProcessSystem()
assert process.saveToNeqsim("oil_plant.neqsim")
```

<!-- run -->
```powershell
neqsim process-report oil_plant.neqsim --out serialized_report --no-word
```

Data that were extracted earlier (for example by a colleague, or by an earlier run of the command) are reported
without a JVM-side model: the case labels may be changed with `--cases`, the numbers are never recomputed.

<!-- run -->
```powershell
neqsim process-report report/data/process_data.json --out from_data --no-word
```

### Options

`neqsim process-report [SOURCE] [options]`; `SOURCE` is a model file or a folder that holds exactly one candidate
(`process_data.json`, `*.neqsim`, `model.py`, `build_model.py`, `process_model.py`, `process.json`; task folders are
searched in `step2_analysis/` too). The default is the current folder.

| Option | Meaning |
|--------|---------|
| `--source-type python\|json\|neqsim\|data` | Skip the auto-detection |
| `--cases FILE` | `cases.yaml` (default: only `<model>.cases.yaml`, or `cases.yaml` beside `model.py` / `process.json`) |
| `--basis FILE` | Confirmed engineering basis; without it a draft is written and used |
| `--standards FILE` | Standards register (default: the shipped template, marked verify before issue) |
| `--out DIR` | Output folder (default `<task>/deliverables/process_report`, else `./process_report`) |
| `--task DIR` | Task folder (default: detected from the model location) |
| `--title`, `--doc-no`, `--revision`, `--status`, `--author`, `--company`, `--project` | Document metadata; unset company and project stay unavailable in a draft (the task title is the study title, not the project) |
| `--init` | Only write the draft basis, cases template and standards copy, then stop |
| `--no-word` | Do not use Microsoft Word for the field update and the PDF |
| `--max-units-per-sheet N` | PFD packing limit (default 14) |
| `--no-rerun` | Do not call `run()` when no case override was applied |
| `--no-results-json` | Do not merge the summary into the task `results.json` |
| `--strict` | Exit code 1 when the validation status is FAIL |
| `--std-temperature`, `--std-pressure` | Standard conditions (default 15 degC and 1.01325 bara) |
| `--quiet` | Print only errors |

## 3. Step-by-step guide

### 3.1 Make and solve the model

Build the flowsheet in Python, with the JSON builder, or load a saved one. Follow the NeqSim rules: set a mixing
rule, give every unit a unique name, keep absolute pressures in bara, close recycles with a `Recycle` unit.
Multi-area plants are **one `ProcessSystem` per area, combined in a `ProcessModel`**. When no model exists,
ask `@solve-process`, `@process-model` or `@extract-process` to build it first.

### 3.2 Define the operating cases

Pass the file with `--cases`. It is picked up automatically only when it is named `<model>.cases.yaml` (or `<model>_cases.yaml`),
or `cases.yaml` beside a conventionally named model file (`model.py`, `build_model.py`, `process_model.py`, `process.json`);
a model file in a shared folder always needs `--cases`, so that cases of one model are never applied to another. A case
changes **values**, never the topology: each case starts from a fresh model, applies its `parameters` (to
`build(case)`) and its `set` overrides (through `getAutomation().setVariableValue`) and runs the model.

### 3.3 Draft and confirm the basis (`--init`)

Without `--basis` the command drafts the engineering basis itself. It fills what can be **derived** (cases, unit and
stream counts, EOS and mixing rule read from the model, component list, recycle units, conventions, the model file
hash, study title, author and the verbatim request from the task folder; the project is never guessed) and writes everything else as
`unavailable - TO BE CONFIRMED` with a `data_gaps` entry that states the effect on conclusions. Nothing is invented.

<!-- run -->
```powershell
neqsim process-report gas_plant_model.py --cases cases.yaml --init --out gas_inputs
```

`--init` solves the model, writes `gas_inputs/inputs/basis.yaml`, `cases.yaml` and `standards_register.yaml`,
prints the re-run command and stops. Edit the basis: replace every marker by a sourced statement, add measured data,
assumptions and requirement matrices, delete the `basis_status: draft` and `drafted_fields` keys, and set
`document.readiness`. Then run with the edited file:

<!-- run -->
```powershell
neqsim process-report gas_plant_model.py --cases gas_inputs/inputs/cases.yaml --basis gas_inputs/inputs/basis.yaml --out gas_report --no-word
```

(The draft is accepted as it is, with the **SCREENING - basis not confirmed** label and a `BASIS-STATUS` warning;
a basis that is not marked draft but still contains markers fails validation, and a draft basis cannot carry a
readiness above SCREENING.)

### 3.4 Standards register

The default register is the template shipped with the skill (editions verified 2026-10-20) and is marked
**verify before issue**: the check `STD-VERIFY` warns while the default is used or when a row is older than 90 days.
Re-verify editions with `neqsim-standards-lookup`, keep one row per standard actually applied, and pass the edited
file with `--standards`. See *What the register claims* in section 8.

### 3.5 Run, then look at the drawings

Run the command (section 2). Open `diagrams/pfd/previews/*.png` and `diagrams/bfd/bfd.png`: check overlapping
labels, tiny text, crossing lines and nearly empty sheets. The run summary prints the number of line crossings; the
manifest also lists `sparse_sheets`. Adjust with `--max-units-per-sheet`, explicit `pfd_sheets`, `bfd_blocks` or
`layout_overrides` in the basis (section 7) and re-run.

### 3.6 Read the validation report

`report/validation_report.md` lists every check as PASS, WARN or FAIL. A FAIL is a defect in the data, the basis or
the drawings and blocks issue; a WARN needs an explanation (typical: unconfirmed basis, zero-flow streams, residuals of
equipment whose energy bookkeeping is not a closure test, unverified standards). Use `--strict` in scripts: the exit
code is 1 when the status is FAIL (2 for usage or model errors, 0 otherwise).

### 3.7 Task folders and results.json

Inside a task folder (detected by `step1_scope_and_research/`, `study_config.yaml` or `user_input.md`) the output
defaults to `<task>/deliverables/process_report`, the title, author and request come from the task, and the summary
is merged into `<task>/results.json` as the `process_study_report` block (other keys are untouched). Use `--task` to
name the folder and `--no-results-json` to skip the merge.

## 4. Model-file contract and `cases.yaml` schema, with a worked example

A Python model file defines **`build(case)`** (or `build()`) returning a solved or runnable `ProcessSystem` or
multi-area `ProcessModel`, or a module-level `model` / `process` object. `case` is
`{"id": "A", "label": "...", "parameters": {...}}`. The file may import its folder neighbours; it must not start a
second JVM. After the case overrides are applied the tool calls `run()` once more (`--no-rerun` skips it when
there are no overrides).

**Repairing lost connections (`wiring`).** Every run audits the topology (`data/connectivity_audit.csv`, check
`TOPO-CONNECTIVITY`, report section 6.5). If the source model lost connections, for example an old JSON export where
recycles have no tear stream and columns no feed, fix the source or list the missing connections in `cases.yaml`;
they are applied to every fresh model before the overrides and the run, and are reported as a model repair:

```yaml
wiring:
  - {unit: "sture::gas resycle", inlet: "sture::compressor gas to gas column.outlet"}
  - {unit: "NGL column::NGL column", feed: "NGL column::ngl valve to column.outlet", tray: 5}
```

`unit` is `Area::name`; the stream is `Area::unit.port` with port `outlet`, `gasOut`, `liquidOut`, `oilOut`, `waterOut`,
`splitN` or `outN`. Choose the stream from the repair candidates of the audit and confirm it on the P&ID.

<!-- file: gas_plant_model.py -->
```python
"""Synthetic gas-processing example for ``neqsim process-report`` (public fixture, no real facility).

Three-stage separation (HP, MP, LP), LP and mid-pressure recompression with intercooling and scrubbers, export
compression and a scrubber-liquid recycle to the LP separator. Two areas in one ``ProcessModel``.

Contract used by the tool: ``build(case)`` returns a ``ProcessModel`` (or ``ProcessSystem``); ``case`` is
``{"id": ..., "label": ..., "parameters": {...}}``. Operating-case overrides given in cases.yaml are applied
afterwards through ``getAutomation()`` and the model is run.
"""
from jneqsim import neqsim


def _fluid(temperature_c):
    fluid = neqsim.thermo.system.SystemSrkEos(273.15 + temperature_c, 60.0)
    for name, amount in (("nitrogen", 0.004), ("CO2", 0.02), ("methane", 0.62), ("ethane", 0.07), ("propane", 0.05),
                         ("i-butane", 0.01), ("n-butane", 0.02), ("n-pentane", 0.012), ("n-hexane", 0.02),
                         ("water", 0.12)):
        fluid.addComponent(name, amount)
    fluid.addTBPfraction("C7", 0.04, 0.100, 0.760)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    return fluid


def build(case=None):
    """Build the flowsheet for one operating case and return the (run) ProcessModel."""
    params = (case or {}).get("parameters") or {}
    t_feed = float(params.get("feed_temperature_c", 45.0))
    fluid = _fluid(t_feed)
    equipment = neqsim.process.equipment
    feed = equipment.stream.Stream("feed", fluid)
    feed.setFlowRate(60000.0, "kg/hr")
    feed.setTemperature(t_feed, "C")
    feed.setPressure(60.0, "bara")

    hp_sep = equipment.separator.ThreePhaseSeparator("HP separator", feed)
    hp_valve = equipment.valve.ThrottlingValve("HP oil valve", hp_sep.getOilOutStream())
    hp_valve.setOutletPressure(18.0)
    mp_sep = equipment.separator.ThreePhaseSeparator("MP separator", hp_valve.getOutletStream())
    mp_valve = equipment.valve.ThrottlingValve("MP oil valve", mp_sep.getOilOutStream())
    mp_valve.setOutletPressure(4.0)

    seed = equipment.stream.Stream("scrubber liquid return", fluid.clone())
    seed.setFlowRate(1.0e-3, "kg/hr")
    lp_mixer = equipment.mixer.Mixer("LP inlet mixer")
    lp_mixer.addStream(mp_valve.getOutletStream())
    lp_mixer.addStream(seed)
    lp_sep = equipment.separator.Separator("LP separator", lp_mixer.getOutletStream())

    area_sep = neqsim.process.processmodel.ProcessSystem()
    for unit in (feed, hp_sep, hp_valve, mp_sep, mp_valve, seed, lp_mixer, lp_sep):
        area_sep.add(unit)

    lp_comp = equipment.compressor.Compressor("LP compressor", lp_sep.getGasOutStream())
    lp_comp.setOutletPressure(18.0)
    lp_comp.setIsentropicEfficiency(0.75)
    lp_cooler = equipment.heatexchanger.Cooler("LP cooler", lp_comp.getOutletStream())
    lp_cooler.setOutTemperature(273.15 + 35.0)
    lp_scrubber = equipment.separator.Separator("LP scrubber", lp_cooler.getOutletStream())
    scrub_valve = equipment.valve.ThrottlingValve("scrubber liquid valve", lp_scrubber.getLiquidOutStream())
    scrub_valve.setOutletPressure(4.0)
    recycle = equipment.util.Recycle("scrubber liquid recycle")
    recycle.addStream(scrub_valve.getOutletStream())
    recycle.setOutletStream(seed)

    mp_mixer = equipment.mixer.Mixer("MP gas mixer")
    mp_mixer.addStream(mp_sep.getGasOutStream())
    mp_mixer.addStream(lp_scrubber.getGasOutStream())
    mp_comp = equipment.compressor.Compressor("MP compressor", mp_mixer.getOutletStream())
    mp_comp.setOutletPressure(60.0)
    mp_comp.setIsentropicEfficiency(0.76)
    inter_cooler = equipment.heatexchanger.Cooler("intercooler", mp_comp.getOutletStream())
    inter_cooler.setOutTemperature(273.15 + 38.0)
    inter_scrubber = equipment.separator.Separator("intercooler scrubber", inter_cooler.getOutletStream())
    hp_mixer = equipment.mixer.Mixer("HP gas mixer")
    hp_mixer.addStream(hp_sep.getGasOutStream())
    hp_mixer.addStream(inter_scrubber.getGasOutStream())
    export_comp = equipment.compressor.Compressor("export compressor", hp_mixer.getOutletStream())
    export_comp.setOutletPressure(120.0)
    export_comp.setIsentropicEfficiency(0.77)
    after_cooler = equipment.heatexchanger.Cooler("export cooler", export_comp.getOutletStream())
    after_cooler.setOutTemperature(273.15 + 40.0)

    area_comp = neqsim.process.processmodel.ProcessSystem()
    for unit in (lp_comp, lp_cooler, lp_scrubber, scrub_valve, recycle, mp_mixer, mp_comp, inter_cooler, inter_scrubber,
                 hp_mixer, export_comp, after_cooler):
        area_comp.add(unit)

    plant = neqsim.process.processmodel.ProcessModel()
    plant.add("separation", area_sep)
    plant.add("compression", area_comp)
    plant.run()
    return plant
```

The `cases.yaml` of the example (two cases; case B changes the feed temperature through a **parameter** read by
`build(case)` and the feed rate and export pressure through **overrides**):

<!-- file: cases.yaml -->
```yaml
cases:
  - id: A
    label: Base case
    role: design
    description: Feed of 60000 kg/h wet gas at 45 C, export at 120 bara
    data_source: Synthetic example inputs
  - id: B
    label: High rate, warm feed
    role: scenario
    description: Feed raised to 75000 kg/h at 55 C, export lowered to 110 bara
    data_source: Synthetic example inputs
    parameters:
      feed_temperature_c: 55.0
    set:
      "separation::feed.flowRate": [75000, kg/hr]
      "compression::export compressor.outletPressure": [110, bara]
```

| Key | Meaning |
|-----|---------|
| `cases[].id` | 1-24 letters, digits, `_` or `-`; unique; must equal the ids of the basis operating cases |
| `label`, `role`, `description`, `data_source` | Text for the basis (`role`: design, calibration, hold-out or scenario); defaults are derived |
| `set` | `address: [value, unit]` (also `"value unit"` or `{value, unit}`); `unit.property` for a `ProcessSystem`, `Area::unit.property` for a `ProcessModel`; a wrong address stops the run and lists the units of the model |
| `parameters` | Free values handed to `build(case)` of a Python model (`case["parameters"]`) |
| `options` | Reserved top-level mapping for future switches |

Unit names that contain a dot cannot be addressed; rename the unit. Anything that adds or removes equipment between
cases is rejected (`the cases do not share one topology`).

## 5. Outputs

All paths are relative to `--out`.

| Path | What it is |
|------|------------|
| `report/*.docx` | The report (cover, control page, contents, summary, basis, methodology, drawings, results, appendices) |
| `report/validation_report.md` | Human-readable validation (also Appendix F of the report) |
| `report/validation_report.json` | The same checks, machine-readable |
| `report/report_facts.json` | Every `[[...]]` reference of the narrative with its resolved value |
| `data/process_data.json` | Complete extracted data (all streams, units, components, cases) |
| `data/process_data.xlsx` | Workbook with the same tables |
| `data/stream_properties.csv` | One row per stream and case |
| `data/stream_components.csv` | Component flows for every stream x component x case |
| `data/stream_phase_compositions.csv` | Phase compositions |
| `data/equipment.csv` | Unit results (duty, power, pressure drop, efficiencies) |
| `data/components.csv` | Component and pseudo-component properties |
| `data/balances.json` | Unit, area and overall balances; PASS, FAIL and NOTE items |
| `diagrams/bfd/bfd.svg` | Block flow diagram (editable source; also `.pdf`, `.png`) |
| `diagrams/pfd/sheets/*.svg` | PFD sheets, A3 landscape, editable |
| `diagrams/pfd/pfd_drawing_set.pdf` | All PFD sheets in one PDF |
| `diagrams/pfd/pfd_layout_spec.json` | Positions and routes of every symbol (source for regeneration) |
| `diagrams/pfd/previews/*.png` | Preview images for review |
| `inputs/basis.yaml` | The draft basis that was used (edit and pass with `--basis`) |
| `inputs/cases.yaml` | The cases that were run |
| `run_summary.json` | Paths, validation status, readiness label, timings, crossings |

## 6. The agent and the skill

In VS Code Chat use **`@process-study-report`**. The agent asks only for what
cannot be found (objective, audience, maturity, cases, acceptance criteria, sources), locates or builds the model,
runs the command, **views the rendered diagrams** and iterates on the layout, runs the validator, interprets the
warnings, writes the process description and interpretation with data tokens, re-verifies the standards and hands over a
short summary with the readiness label and the open gaps. It loads the skill
[process-study-report](../../.github/skills/process-study-report/SKILL.md),
`neqsim-professional-reporting` and `neqsim-standards-lookup`.

From Python the same pipeline is available as `process_study_report.run_pipeline(data, basis, standards, out_dir)`:

<!-- file: api_example.py; run -->
```python
import psr_basis
import process_study_report
import json

with open("report/data/process_data.json", encoding="utf-8") as handle:
    data = json.load(handle)
basis = psr_basis.load_structured("report/inputs/basis.yaml")
standards = psr_basis.load_structured("report/inputs/standards_register.yaml")["standards"]
summary = process_study_report.run_pipeline(data, basis, standards, "api_report", use_word=False)
print(summary["validation"])
```

## 7. Customising

| What | How |
|------|-----|
| Sheet size | `--max-units-per-sheet N`; sheets are split at the cut with the fewest crossing streams |
| Which areas on which sheet | `pfd_sheets: [{title, areas}]` in the basis |
| BFD blocks | `bfd_blocks: [{name, areas, description}]` in the basis |
| Symbol positions | `layout_overrides: {tag: {x: mm, y: mm}}` in the basis (positions are in the layout spec) |
| Equipment without a symbol | Drawn as a generic box with the NeqSim class under the tag, listed in the legend, flagged `EQ-GENERIC-SYMBOL` |
| Balance tolerances | `tolerances: {mass_rel: ..., energy_rel: ...}` in the basis (defaults in `psr_balances.DEFAULT_TOLERANCES`) |
| Known balance exceptions | `known_balance_exceptions: [{id, explanation}]` in the basis |
| Readiness label | `document.readiness` (SCREENING, CONCEPT, ISSUED FOR REVIEW, ...) and `readiness_note`; a draft basis is capped at SCREENING |
| Standards | `--standards my_register.yaml`; requirement matrices in `requirement_matrices` of the basis |
| Standard conditions | `--std-temperature`, `--std-pressure` (or `conventions.standard_conditions` in a given basis) |

Balance residuals are classified honestly. Separator and scrubber outlets are re-flashed isothermally and component
splitters re-flash at inlet conditions, so their energy residual is **not** a closure error: it is shown as `NOTE`
with its size expressed as an equivalent temperature shift of the inlet heat-capacity flow (limit 2 K). Recycle tear
residuals are `NOTE` below 0.1 % of throughput. Mass and component residuals of ordinary units, and larger residuals,
remain `FAIL`.

## 8. Troubleshooting and limitations

| Symptom | Cause and fix |
|---------|---------------|
| `ERROR: cannot tell the model source type` | Use `--source-type python\|json\|neqsim\|data` |
| `build() ... failed for case B` | The error of your function is printed; test `build({"id": "B", "label": "x", "parameters": {}})` alone |
| `cannot set 'x'` | Wrong address; the message lists the units of the model. Multi-area: `Area::unit.property` |
| `the cases do not share one topology` | `build(case)` must not add or remove equipment between cases |
| `NeqSim JSON ... did not build` | Names of the entries that were not created are listed (unknown type or inlet) |
| `Validation: FAIL` | Open `report/validation_report.md`; fix the FAIL checks (data, basis, drawings) and re-run |
| `BASIS-STATUS` warning | The draft basis is unconfirmed; follow section 3.3 |
| `TOPO-CONNECTIVITY` FAIL, arrows ending in blank space, units drawn "NOT CONNECTED" | The solved model lacks connections (recycles without tear stream, columns without feed, isolated streams), typically from an old JSON export or serialized file. Open `data/connectivity_audit.csv`, restore the wiring in the source model (re-export with a current NeqSim), or add a `wiring` list to `cases.yaml` (section 4); balances around open recycles are not meaningful |
| Mixed EOS or component warning | Areas use different equation-of-state classes or define a pseudo-component differently; state it in the basis |
| No PDF | PDF export and field update need Microsoft Word; without it the contents carry a static list |
| Nearly empty sheets | Long linear chains are wide and short; raise or lower `--max-units-per-sheet`, or group areas with `pfd_sheets` |

**What the standards register claims.** It lists the standards used for the conventions (ISO 10628-1/-2, ISO 14617,
NORSOK P-002, DEXPI, IEC 62424) with edition, status, source and date checked. Symbols and conventions follow the
standards **in spirit only**: the standard texts are paywalled and were not read, so **no conformance or compliance
is claimed** (the validator rejects an unqualified "complies" for a standard whose text was not available). NORSOK
P-002 is listed for applicability; compliance needs a line list, design pressures and safety-system data that
a NeqSim model does not contain.

**Limitations.** The topology is what the solved model contains; plant systems that are not modelled are listed on
every sheet. Utility media, drivers, sparing, vessel sizes and control loops appear only if the model has them.
Streams consumed by several units are counted once per area in the area balances. The layout is heuristic (layered
layout with crossing reduction); some crossings and sparse sheets can remain, so always look at the images. The
report is a description of a simulation, not an approved design document.

## Related

[Task Solving Guide](TASK_SOLVING_GUIDE.md), the skill
[process-study-report](../../.github/skills/process-study-report/SKILL.md), the agent
[process-study-report](../../.github/agents/process-study-report.agent.md), and the CLI overview in
[devtools/README.md](../../devtools/README.md).
