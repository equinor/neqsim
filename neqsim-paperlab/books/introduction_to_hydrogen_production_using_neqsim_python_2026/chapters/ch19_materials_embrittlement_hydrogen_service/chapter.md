# Materials, Leakage, and Hydrogen Embrittlement

<!-- Estimated pages: 18-26 -->

## Learning objectives

After this chapter you should be able to:

1. Explain how to connect hydrogen simulations to material limits, leakage risk, embrittlement screening, and standards review.
2. Translate the topic into a reproducible NeqSim Python workflow.
3. Identify the dominant assumptions and sanity checks for hydrogen service design decisions for piping, vessels, compressors, welds, valves, and storage interfaces.
4. Save a chapter-level artifact that can be reused in a hydrogen study.

## Prerequisites

Before reading this chapter the reader should be comfortable with:

- A working Python 3.10+ environment with NeqSim installed (see Chapter 0 and
  Chapter 3 for setup), and the ability to start a JVM from Python with
  `from neqsim import jneqsim as J`.
- The thermodynamic and fluid concepts introduced in Part II (Chapters 4-8):
  EOS choice, mixing rules, flash calculations, and the use of
  `initProperties()` after a flash.
- The general NeqSim object model from Chapter 2: `ThermodynamicSystem`,
  `Stream`, equipment classes, `ProcessSystem`, and the difference between
  a steady-state `run()` and a transient `runTransient()`.
- The reproducibility habits from Chapter 3: workspace-vs-installed mode,
  `results.json` outputs, and the fact that every code block in this book is
  marked `<!-- noexec -->` because it must be run by the reader in a
  controlled environment.

If any of the above feels unfamiliar, return to the indicated chapter; the
rest of this chapter assumes those habits are in place.

## Why this chapter matters

Connect hydrogen simulations to material limits, leakage risk, embrittlement screening,
and standards review. The practical setting is hydrogen service design decisions for piping, vessels, compressors, welds, valves, and storage interfaces. In a desktop simulator this
kind of model often disappears into a case file. In this book it becomes a
Python-controlled object graph: fluids, streams, unit operations, calculations,
figures, and result summaries are all visible and versionable.

A hydrogen model is useful only when its boundary is explicit. The inlet composition,
water specification, utility assumptions, pressure levels, product specification, and
disposal route for by-products decide which equations are meaningful. NeqSim makes those
boundaries inspectable because every stream and unit operation is an object that can be
read, copied, serialized, and validated from Python.

The same calculation should be able to serve several audiences. A process engineer wants
mass and energy balances, a rotating-equipment engineer wants power and discharge
temperature, a safety engineer wants inventories and relief cases, and a project
engineer wants a cost range. The book therefore treats Python code as the common
workbench where these views are generated from one model rather than retyped into
separate spreadsheets.

Hydrogen adds its own modelling pressure. Molecules are light, diffusivity is
high, compression work is significant, embrittlement and leakage matter, and
small composition errors can move a product stream outside fuel-cell or pipeline
specifications. That is why the chapter keeps returning to three questions:
what is conserved, what is assumed, and what evidence should survive after the
notebook closes? \cite{asmeb3112,api941,api521,ccps2008,iso19880}

## Conceptual model

The chapter model can be read as a five-step engineering calculation:

1. Define the material boundary and choose the thermodynamic basis.
2. Run the smallest equilibrium or unit-operation calculation that answers the
   question.
3. Compare the output against a physical lower or upper bound.
4. Convert the output to engineering KPIs: design pressure, temperature, H2 partial pressure, cyclic pressure range, leak consequence input, and material-screening status.
5. Save the model state, the figure, and the assumptions.

A generic material balance for a hydrogen unit is

$$
\dot n_{H2,out} = \dot n_{H2,in} + \nu_{H2} \xi - \dot n_{H2,loss}
$$

where $\xi$ is the reaction extent or electrochemical extent, and the loss term
captures tail gas, purge, venting, slip, or measurement closure. The important
habit is not the equation alone. The important habit is to identify where each
term appears in the NeqSim object model and to check it after every run.

For hydrogen systems the most dangerous errors are often quiet errors: a missing mixing
rule, transport properties read before initialization, water handled with an unsuitable
equation of state, a specific energy below the thermodynamic minimum, or a purification
recovery that hides hydrogen in the tail gas. Each chapter includes a short set of
sanity checks so the model teaches discipline as well as syntax.

## NeqSim capabilities used

This chapter uses or prepares for these capabilities:

| Capability | How it is used in the chapter |
|---|---|
| Thermodynamic system | Defines hydrogen-rich fluids, water, steam, CO2, and impurities. |
| Process equipment | Turns stream properties into material and energy balances. |
| Python orchestration | Runs parameter cases, figures, and evidence export. |
| Validation checks | Guards against non-physical specific energy, missing phases, or mass imbalance. |
| Reporting artifact | Captures a reusable output for the capstone studies. |

Specific NeqSim/Python surfaces and engineering references emphasized here: **ASME B31.12, API 941, ISO 14687, API 521, process safety checks, material screening from NeqSim operating envelopes**.



## Python workflow pattern

The code block below is intentionally compact. In a production notebook you
would split it into setup, input definition, run, checks, plotting, and
results.json cells. It is marked as a readable pattern: the named Java classes
were checked against the local NeqSim source tree when this book was generated,
but readers should still run the snippet against the exact branch they use.

<!-- noexec -->
```python
from neqsim import jneqsim as J

# Direct Java access through neqsim-python. Use explicit units and call
# setMixingRule before running flashes or process equipment.
# NeqSim calculates the operating envelope; material acceptance is a standards review.
service = {
    "standard": ["ASME B31.12", "API 941", "ISO 14687", "API 521"],
    "fluid": "hydrogen",
    "design_pressure_bara": 150.0,
    "min_temperature_C": 5.0,
    "max_temperature_C": 95.0,
    "cyclic_pressure_delta_bar": 60.0,
    "screening_checks": [
        "H2 partial pressure and temperature inside material limits",
        "fracture toughness and weld procedure qualified for hydrogen service",
        "leak scenario exported to relief/dispersion/consequence study",
        "compressor discharge temperature kept below materials and seal limits",
    ],
}
print(service)
```

## Parameter study script, graph, and discussion

The chapter includes a runnable parameter-study notebook at `notebooks/parameter_study.ipynb`.
It takes the compact script above one step further: the notebook defines a sweep,
builds a results table, saves a CSV/JSON evidence bundle, and writes the graph
shown below. The values are either direct engineering calculations or normalized
study indices from cited public references and standards. Re-run the notebook on
the active NeqSim branch when project values or branch-specific APIs change.

**Calculation basis.** Operating-pressure, hydrogen partial-pressure, leak-source, materials-attention, and operability-margin screening calculations.

**Study basis.** ASME B31.12, API 941, API 521, and ISO hydrogen quality standards define the review basis; the plotted values are screening inputs, not acceptance decisions. \cite{asmeb3112,api941,api521,iso14687}

| Screening input | Expected output | Review use |
|---|---|---|
| H2 partial pressure | 150 bara | ASME B31.12/API 941 screen |
| Max discharge T | 67 degC | Seal and material limit |
| Min pipeline T | 5.5 degC | Toughness and MDMT prompt |
| Pressure cycle | 60 bar | Fatigue prompt |
| Leak source term | available | Dispersion/QRA handoff |

![Figure 19.2: Expected materials and embrittlement screening inputs from process outputs.](figures/notebook_materials_screening_output.png)

The materials chapter notebook should not claim to predict embrittlement failure. It should convert process outputs into review inputs: partial pressure, temperature range, pressure cycling, water/impurity flags, and leak/source-term basis.

**Discussion.** The parameter study shows how hydrogen service design decisions for piping, vessels, compressors, welds, valves, and storage interfaces responds when the controlling parameter changes. The important result is not a single base-case value; it is the shape of the response and the point where design pressure, temperature, H2 partial pressure, cyclic pressure range, leak consequence input, and material-screening status begin to trade against margin or cost.






## Notebook coverage: materials and hydrogen embrittlement

The transport notebook contains the practical warnings that belong beside any
pipeline calculation: hydrogen can leak through small defects, has a wide
flammability range, can embrittle susceptible steels, and makes welded joints,
fittings, valves, seals, compressor parts, and pressure cycles part of the
simulation boundary. This chapter puts those warnings into a workflow.

NeqSim does not replace a material engineer. Its role is to deliver the service
envelope: H2 partial pressure, total pressure, temperature range, cyclic pressure
range, composition, water content, flow regime, inventories, and release-source
terms. The material review then applies ASME B31.12, API 941, project material
specifications, fracture-mechanics evidence, weld procedure qualifications,
hardness limits, and inspection requirements.

The minimum screening table should contain:

| Input from model | Why it matters for material review |
|---|---|
| H2 partial pressure | Embrittlement and high-temperature hydrogen attack screening. |
| Maximum operating temperature | API 941 and seal/material temperature limits. |
| Minimum operating temperature | Toughness, MDMT, and depressurization cold-end checks. |
| Pressure cycles | Fatigue and crack-growth screening. |
| Water and impurities | Corrosion, hydrate, ice, and contaminant compatibility. |
| Inventory and leak source terms | Ventilation, dispersion, fire/explosion consequence input. |

The chapter therefore avoids a false one-click material answer. It shows how a
simulation creates the evidence package that a material and safety review needs.


## Materials-screening deep dive

Hydrogen embrittlement cannot be solved from a process simulator alone. It
depends on material grade, welds, heat treatment, stress state, defects,
pressure cycling, impurities, temperature, and inspection strategy. The role of
the NeqSim model is to supply the exposure evidence: H2 partial pressure,
temperature envelope, pressure cycles, impurity and water content, and credible
leak/source-term conditions.

The chapter notebook should therefore create a materials screening table. Each
row should map an equipment item or pipeline segment to pressure, temperature,
H2 mole fraction, water/impurity flags, pressure cycling, applicable standards,
and required follow-up. The status column should say "materials review needed"
or "screening basis acceptable", not "embrittlement predicted".

This distinction matters. A good process model does not replace ASME B31.12,
API 941, ISO 14687, project material specifications, or fracture-mechanics
assessment. It makes those reviews faster and better because the exposure
conditions are explicit, reproducible, and tied to the process design.


The preferred workflow is deliberately repetitive. Define the fluid, set the units, run
the flash or process, initialize properties, extract a small number of engineering key
performance indicators, and save the evidence. Repetition is not a lack of
sophistication; it is the mechanism that makes complex models reviewable and reusable.

## Worked simulation study

![Figure 19.1: Evidence-backed calculation and study basis for Materials, Leakage, and Hydrogen Embrittlement.](figures/fig_ch19_materials_embrittlement.png)

**Calculation basis.** Operating-pressure, hydrogen partial-pressure, leak-source, materials-attention, and operability-margin screening calculations.

**Public study or standard basis.** ASME B31.12, API 941, API 521, and ISO hydrogen quality standards define the review basis; the plotted values are screening inputs, not acceptance decisions. \cite{asmeb3112,api941,api521,iso14687}

**Discussion.** The figure turns hydrogen service design decisions for piping, vessels, compressors, welds, valves, and storage interfaces into a data-backed study with traceable assumptions. The plotted values are generated from the chapter's calculation table, while the basis panel records the independent study or standard used to interpret design pressure, temperature, H2 partial pressure, cyclic pressure range, leak consequence input, and material-screening status. The physical mechanism behind the figure is
the coupling between equilibrium, transport, equipment performance, and
specification constraints. Hydrogen production is rarely a single calculation:
route chemistry sets purification load, purification losses affect heat balance,
electrolyzer voltage sets compression and cooling demand, and operating pressure
sets both linepack value and materials/safety attention. The engineering
implication is that the first chapter figure should already carry numbers,
units, and sources. It is not a sketch to decorate the chapter; it is the first
screening result that the later notebook can rerun and refine.

## Interpretation checklist

| Check | Expected behaviour | What to do if it fails |
|---|---|---|
| Material closure | Total mass error below 0.01 percent for steady-state examples. | Inspect disconnected streams, recycle convergence, and unit basis. |
| Energy sanity | Specific energy is above the thermodynamic minimum and within technology bands. | Recheck current, voltage, efficiency, pressure, and heat-duty sign. |
| Phase sanity | Phase count and water split match the process temperature and pressure. | Revisit EOS, mixing rule, water model, and property initialization. |
| Product quality | H2 purity and impurity limits match the intended market. | Add purification, drying, purge, or tighter recovery assumptions. |
| Evidence | KPIs, assumptions, and figures are saved with units. | Create a results.json entry before drawing conclusions. |

The examples use Python to orchestrate Java classes directly. This avoids the false
comfort of a narrow wrapper and exposes the same objects used by the NeqSim engine
itself. When a new class appears in NeqSim, a Python notebook can usually call it
immediately through jneqsim or ns.JClass, which is exactly what a fast-moving hydrogen
technology program needs.

## Applying the standard workflow

The model in this chapter is built and reviewed with the same workbench routine
used everywhere in the book: define the boundary, run the smallest meaningful
calculation, validate against a physical limit, extract design pressure, temperature, H2 partial pressure, cyclic pressure range, leak consequence input, and material-screening status, and save the
evidence. The full notebook structure, the three-step validation routine
(balance, physical-limit, and reference-case checks), the patterns for extending
a single converged case into a sensitivity or technology study, and the
peer-review prompts are collected once in *Appendix: Notebook structure,
validation, and review methodology* so they are not repeated in every chapter.
Chapter 3 covers the setup and reproducibility habits those steps rely on. Apply
that routine to the model in this chapter (hydrogen service design decisions for piping, vessels, compressors, welds, valves, and storage interfaces) before treating any number
here as a design result.

## Common modelling pitfalls

- Treating hydrogen as a generic light gas when the question depends on density,
  compression work, leakage, or cryogenic behaviour.
- Reading viscosity, thermal conductivity, or density after a flash without
  calling `initProperties()`.
- Comparing green and blue hydrogen only on stack or reactor efficiency while
  ignoring compression, purification, cooling, water, CO2, and capacity factor.
- Reporting product flow without checking where hydrogen leaves in tail gas,
  purge, vent, dissolved water, or inventory changes.
- Forgetting that early cost estimates are screening estimates until vendor
  data, installation factors, local execution strategy, and utilities are added.

## Exercises

1. Change the main pressure level and explain which KPI changes first.
2. Add one impurity or by-product and decide whether the selected EOS is still
   appropriate.
3. Create a small sensitivity table for one design variable and one operational
   variable.
4. Write a `results.json` object with at least five key results and two
   validation checks.
5. State what additional data would be needed before using this model for a
   design decision.

## Self-check questions

Use these short questions to test understanding before moving on. They are
designed to be answered from the chapter narrative without rerunning the
notebook.

1. Which physical quantity is conserved in the chapter's worked example, and
   where in the NeqSim object model is that conservation enforced?
2. Which two assumptions, if relaxed, would most change the chapter KPIs?
3. Which of the listed NeqSim capabilities is the most sensitive to EOS choice
   or mixing rule, and why?
4. What single sanity check would a reviewer run first on the worked figure?
5. If the reader had to defend the chapter result to a project gate, which
   piece of evidence (figure, table, results.json field, citation) would they
   point at first?

## What you should now be able to do

A reader who has worked through this chapter should be able to:

- Set up a NeqSim Python notebook that addresses hydrogen service design decisions for piping, vessels, compressors, welds, valves, and storage interfaces with an explicit
  fluid, equipment block, run, and validation step.
- Identify the KPIs (design pressure, temperature, H2 partial pressure, cyclic pressure range, leak consequence input, and material-screening status) and report them with units and a screening band.
- Apply the interpretation checklist above to spot the most likely modelling
  errors before they propagate into a study report.
- Save a `results.json` artifact and a figure that another engineer can read
  without opening the notebook.

## Where to next

- For deeper thermodynamic foundations, return to Part II (Chapters 4-8).
- For an end-to-end view of how this chapter's result feeds the value chain,
  see Part VII Chapter 26 (capstone value-chain integration).
- For safety, materials, and operability implications of the modelling
  decisions made here, see Part V (Chapters 19-22).
- For automation, scenario sweeps, and digital-twin patterns that turn the
  chapter notebook into reusable infrastructure, see Chapter 23.

## Chapter summary

This chapter positioned hydrogen service design decisions for piping, vessels, compressors, welds, valves, and storage interfaces inside a reproducible NeqSim workflow. The main
lesson is that hydrogen production simulation is not just chemistry or just
process equipment. It is the coupling of thermodynamics, reaction or
electrochemical extent, purification, compression, heat management, cost,
standards, and evidence. The next chapter keeps the same workflow and changes
the modelling lens.

## Portfolio artifact

Create a folder for this chapter with a notebook, the generated figure, and a
small `results.json` file. The artifact should be understandable without the
book open: inputs, method, units, key results, validation, and one engineering
recommendation.
