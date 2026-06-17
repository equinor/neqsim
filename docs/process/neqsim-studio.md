---
title: "NeqSim Studio: Python-First Process Builder"
description: "Newcomer-friendly Python layer for building NeqSim process models — natural language, template recipes, a guided wizard, edit-by-chat automation, and a recipe gallery. Lowers the barrier from idea to a running flowsheet."
---

# NeqSim Studio — Python-First Process Builder

Setting up a process model is one of the hardest first steps for newcomers to
NeqSim: you have to know which Java classes to instantiate, how to wire streams,
and how to configure equipment. **NeqSim Studio** removes that barrier with a
small Python layer that lets you go from an idea to a running, validated
flowsheet in one or two lines.

Studio never replaces the engine — every builder produces the JSON understood by
NeqSim's `JsonProcessBuilder` and runs through `ProcessSystem.fromJsonAndRun`, so
all the thermodynamics and unit operations stay in the trusted Java core. The
Python layer only handles the *authoring* experience.

The package lives at `devtools/neqsim_studio/`.

## Five ways to build a process

| # | Method | Best for | Entry point |
|---|--------|----------|-------------|
| 1 | Natural language | Describing a process in plain English | `studio.from_text(...)` |
| 2 | Template recipes | Standard unit trains with a few knobs | `studio.gas_compression(...)` |
| 3 | Guided wizard | Newcomers who want to be asked | `studio.wizard(...)` |
| 4 | Edit by chat / automation | Tweaking a built model conversationally | `result.editor().apply_command(...)` |
| 5 | Recipe gallery | Copy-paste ready cookbook examples | `studio.gallery()` |

---

## Setup

Studio reuses the standard NeqSim dev setup so your **workspace** Java classes
(from `target/classes`) are loaded — no packaged JAR copy required.

```python
import sys
sys.path.insert(0, "devtools")            # make neqsim_studio importable

from neqsim_studio import connect
studio = connect()                         # starts (or reuses) the NeqSim JVM
```

In a notebook that already initialised NeqSim with
`neqsim_dev_setup.neqsim_init`, pass the namespace so the JVM is not restarted:

```python
from neqsim_dev_setup import neqsim_init, neqsim_classes
ns = neqsim_init(project_root=PROJECT_ROOT, recompile=False, verbose=True)
ns = neqsim_classes(ns)

from neqsim_studio import connect
studio = connect(ns=ns)                    # reuse the running JVM
```

---

## 1. Natural language

Build a flowsheet from a plain-language description. Works offline with a
deterministic rule-based parser, or with any LLM callable you supply.

```python
result = studio.from_text(
    "Take natural gas at 25 C and 40 bara, cool it to 20 C, "
    "then compress to 150 bara"
)
result.summary()
print(result.units())
# ['feed', 'Cooler 1', 'Compressor 1']
```

Recognised phrases include `cool to 30 C`, `heat to 60 C`, `compress to
120 bara`, `pump to 200 bara`, `expand to 20 bara`, and `separator` /
`knock out`. Feed conditions (`at 25 C and 40 bara`, flow rate, and fluid type
such as *lean gas* / *rich gas* / *co2*) are auto-detected.

For LLM-backed parsing, pass a callable `str -> str` that turns a grounding
prompt into a JSON flowsheet:

```python
result = studio.from_text("two-stage export compression for rich gas", llm=my_llm)
```

---

## 2. Template recipes

Parameterised standard trains. Pass only the knobs you care about; the rest have
sensible defaults, and a preset feed fluid is created when you don't supply one.

```python
# Multi-stage gas compression with intercooling
result = studio.gas_compression(
    suction_pressure_bara=20,
    discharge_pressure_bara=120,
    stages=2,
    interstage_temperature_c=30,
    polytropic_efficiency=0.78,
)
print(result.key_results())
# {'total_compressor_power_MW': 11.64, 'total_cooling_duty_MW': 8.64}

sep  = studio.three_stage_separation(hp_pressure_bara=70, mp_pressure_bara=20, lp_pressure_bara=3)
dehy = studio.dehydration(feed_pressure_bara=70, contactor_temperature_c=25)
co2  = studio.co2_capture(feed_pressure_bara=40, export_pressure_bara=120)
```

List or build templates generically:

```python
studio.list_templates()
# {'gas_compression': ..., 'three_stage_separation': ...,
#  'dehydration': ..., 'co2_capture': ...}

studio.from_template("gas_compression", stages=3, discharge_pressure_bara=200)
```

---

## 3. Guided wizard

Lets a newcomer be *asked* what they want. Programmatic (testable) and
interactive modes are both supported.

```python
result = studio.wizard(answers={
    "objective": "compress",            # compress / separate / dehydrate / co2
    "fluid": "lean_gas",
    "feed_temperature_c": "35",
    "feed_pressure_bara": "30",
    "target_pressure_bara": "100",
})

# Or prompt the user interactively:
result = studio.wizard(interactive=True)
```

---

## 4. Edit by chat / automation

Once a flowsheet is built, tweak it conversationally. The editor wraps the
NeqSim `ProcessAutomation` facade (string-addressable variables, self-healing
fuzzy matching, closed-loop `evaluate`).

```python
result = studio.gas_compression(stages=2)
editor = result.editor()

editor.units()
# ['feed', 'Stage 1 Compressor', 'Stage 1 Intercooler', 'Stage 2 Compressor']

editor.apply_command("set Stage 1 Compressor outlet pressure to 55 bara")
editor.apply_command("change Stage 1 Intercooler outlet temperature to 25 C")

editor.get("Stage 2 Compressor.outletPressure", "bara")
editor.set_safe("Stage 2 Compressor.outletPressure", 130, "bara")   # JSON result
editor.describe()                          # full unit/variable manifest
editor.topology()                          # equipment + connections
```

Closed-loop evaluation applies a batch of setpoints, runs to convergence, and
reads back objectives in one non-throwing call (ideal for optimisation):

```python
out = editor.evaluate(
    setpoints={"Stage 2 Compressor.outletPressure": 150.0},
    readbacks=["Stage 2 Compressor.power"],
    unit="bara",
    readback_unit="kW",
)
# out["feasible"], out["readbacks"], out["converged"], ...
```

---

## 5. Recipe gallery

A cookbook of self-contained, ready-to-run recipes (each carries its own fluid
and JSON):

```python
studio.gallery()                           # prints the catalog

studio.recipes()
# {'two_stage_export_compression': ..., 'three_stage_separation': ...,
#  'water_knockout': ..., 'co2_conditioning': ...}

result = studio.build_recipe("two_stage_export_compression")
result.summary()
```

---

## Working with a result

Every builder returns a `FlowsheetResult`:

```python
result.ok                    # build succeeded (no hard errors)
result.units()               # equipment names
result.summary()             # printed text overview + key results
result.key_results()         # dict: power / duty headline numbers
result.describe()            # full automation manifest (dict)
result.topology()            # equipment + connections (dict)
result.get(addr, unit)       # read a variable
result.set(addr, value, unit)# write a variable and re-run
result.editor()              # the FlowsheetEditor (method 4)
result.automation()          # the raw ProcessAutomation facade
result.to_json()             # the JSON definition that was built
result.show()                # matplotlib block diagram
process = result.process     # the underlying NeqSim ProcessSystem
```

---

## Building from a spec or raw JSON

To assemble the JSON yourself, use `FlowsheetSpec` (pure Python, no JVM needed to
build the dict) and hand it to the studio:

```python
from neqsim_studio import FlowsheetSpec

spec = FlowsheetSpec("my process")
spec.fluid({"methane": 0.9, "ethane": 0.07, "propane": 0.03},
           temperature_c=25, pressure_bara=40)
spec.cooler("Cooler 1", outlet_temperature_c=15)
spec.compressor("Compressor 1", outlet_pressure_bara=120)

result = studio.from_json(spec.to_dict())
```

---

## Design & testing

The authoring logic is split so it can be unit-tested without starting the JVM.
The `*_spec` template builders, the natural-language parser, the
`parse_edit_command` function, the wizard router, and the gallery catalog are all
pure Python. Run the suite (no Java required):

```bash
python devtools/test_neqsim_studio.py
# 27 passed, 0 failed
```

---

## Related documentation

- `devtools/neqsim_studio/README.md` — the package README.
- [examples/notebooks/neqsim_studio_quickstart.ipynb](../examples/index) — runnable walkthrough.
- [Process Design Guide](process_design_guide) — the underlying design workflow.
- [ProcessSystem and flowsheet management](processmodel/) — the JSON builder and automation facade.
