# Capstone A: Blue H2 Plant Python Study

<!-- Estimated pages: 18-26 -->

## Learning objectives

After this chapter you should be able to:

1. Explain how to combine reforming, WGS, PSA, tail-gas, CO2 capture, compression, and reporting.
2. Translate the topic into a reproducible NeqSim Python workflow.
3. Identify the dominant assumptions and sanity checks for an integrated blue H2 screening case.
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

Combine reforming, WGS, PSA, tail-gas, CO2 capture, compression, and reporting. The practical setting is an integrated blue H2 screening case. In a desktop simulator this
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
notebook closes? \cite{iea2023hydrogen,irena2022greenhydrogen,buttler2018water,iso14687,iso22734}

## Conceptual model

The chapter model can be read as a five-step engineering calculation:

1. Define the material boundary and choose the thermodynamic basis.
2. Run the smallest equilibrium or unit-operation calculation that answers the
   question.
3. Compare the output against a physical lower or upper bound.
4. Convert the output to engineering KPIs: plant H2 rate, CO2 intensity, fuel balance, CAPEX, and acceptance criteria.
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

Specific NeqSim/Python surfaces and engineering references emphasized here: **BlueHydrogenPlantBuilder, ReformerFurnace, WaterGasShiftReactor, ComponentCaptureUnit, PSACascade, ProcessModel, multi-area ProcessSystem, PSA, CCS screening, cost, uncertainty, report data**.



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
builder = J.process.hydrogen.BlueHydrogenPlantBuilder()
builder.setName("Capstone blue H2")
builder.setMethaneFeedMolePerSec(120.0)
builder.setSteamToCarbonRatio(3.0)
builder.setCo2CaptureFraction(0.92)
builder.setCo2ExportPressure(110.0)
builder.setH2ExportPressure(100.0)
builder.setIncludePsa(True)

process = builder.build()
process.run()

furnace = builder.getReformerFurnace()
ht_shift = builder.getHighTemperatureShiftReactor()
lt_shift = builder.getLowTemperatureShiftReactor()
psa = builder.getPsaCascade()
results = {
   "capture_target_fraction": builder.getCo2CaptureFraction(),
   "tube_duty_kW": furnace.getTubeHeatDemandKW(),
   "heat_balance_ratio": furnace.getHeatBalanceRatio(),
   "methane_conversion": furnace.getTubeReformer().getMethaneConversion(),
   "ht_shift_co_conversion": ht_shift.getCarbonMonoxideConversion(),
   "lt_shift_co_conversion": lt_shift.getCarbonMonoxideConversion(),
   "captured_co2_kg_per_hr": builder.getCapturedCo2MassFlowKgPerHour(),
   "h2_product_kg_per_hr": builder.getHydrogenProductMassFlowKgPerHour(),
   "carbon_intensity_kgCO2_per_kgH2": builder.getCarbonIntensityKgCO2PerKgH2(),
   "gross_carbon_intensity_kgCO2_per_kgH2": builder.getGrossCarbonIntensityKgCO2PerKgH2(),
   "psa_h2_purity": psa.getH2Purity(),
   "psa_h2_recovery": psa.getH2Recovery(),
}
print(results)
```

## Parameter study script, graph, and discussion

The chapter includes a runnable parameter-study notebook at `notebooks/parameter_study.ipynb`.
It takes the compact script above one step further: the notebook defines a sweep,
builds a results table, saves a CSV/JSON evidence bundle, and writes the graph
shown below. The values are either direct engineering calculations or normalized
study indices from cited public references and standards. Re-run the notebook on
the active NeqSim branch when project values or branch-specific APIs change.

**Calculation basis.** Stoichiometric SMR/ATR/POX/WGS balances, equilibrium or route-builder sweeps, PSA recovery accounting, and CO2-capture mass-balance indices.

**Study basis.** IEA hydrogen route context plus process-design and gas-purification references set the screening ranges and interpretation limits. \cite{iea2023hydrogen,towler2013chemical,kohl1997,gpsa2012}

| Case | H2 product index | Residual CI index | Total energy index |
|---|---|---|---|
| Base 90% | 1.0 | 0.18 | 1.0 |
| High capture | 0.98 | 0.1 | 1.08 |
| Low S/C | 1.04 | 0.22 | 0.94 |
| High pressure export | 0.99 | 0.18 | 1.12 |

![Figure 24.2: Parameter study for blue-H2 capstone acceptance criteria.](figures/parameter_blue_capstone_acceptance.png)

The blue capstone study compares capture target cases against residual carbon intensity, hydrogen product index, and total energy index.

**Discussion.** The capstone result makes acceptance criteria explicit. The best carbon case is not automatically the best energy case, and a process change that increases hydrogen production may worsen residual carbon intensity. This is why capstone notebooks need pass/warn/fail criteria, not only KPIs.






## Notebook coverage: carbon intensity of hydrogen

A modern hydrogen study is judged as much on carbon intensity as on cost. In
this book the modelling surface for the emission side is the captured and
uncaptured CO2 from `ComponentCaptureUnit` and `BlueHydrogenPlantBuilder`, plus
the electricity demand that the electrolysis and compression chapters already
compute.

Carbon intensity is the well-to-product CO2-equivalent emission divided by the
hydrogen delivered:

$$
CI_{H2} = \frac{\dot m_{CO_2,\,direct}\,(1-\eta_{cap})
               + \dot m_{CO_2,\,upstream}
               + e_{H2}\, f_{grid}}{\dot m_{H2}}
\quad\left[\text{kg CO}_2\text{e/kg H}_2\right]
$$

The first term is the process CO2 that escapes capture, where $\eta_{cap}$ is
the capture efficiency. The second term is upstream and fuel-related emissions,
including methane slip for reforming routes. The third term is the power-related
emission, the product of the specific energy $e_{H2}$ and the grid emission
factor $f_{grid}$ in kg CO2e per kWh; it dominates the carbon intensity of
electrolytic hydrogen.

The reference bands make the result easy to sanity-check. Unabated steam
methane reforming (grey hydrogen) is usually around 9-10 kg CO2e per kg H2.
Blue hydrogen with 90-95 percent capture typically falls to roughly 1-4 kg CO2e
per kg H2, with the exact value sensitive to capture rate and upstream methane
emissions. Green hydrogen approaches zero on a fully renewable grid, but with a
fossil-heavy grid the power term alone can exceed the grey-hydrogen value, which
is why $f_{grid}$ must always be stated, not assumed.

The physical-limit checks are that capture efficiency stays between zero and
one, that the uncaptured plus captured CO2 closes against the process carbon
balance, and that a low-carbon claim is always reported together with the grid
emission factor and the capture rate that produced it.




The preferred workflow is deliberately repetitive. Define the fluid, set the units, run
the flash or process, initialize properties, extract a small number of engineering key
performance indicators, and save the evidence. Repetition is not a lack of
sophistication; it is the mechanism that makes complex models reviewable and reusable.

## Worked simulation study

![Figure 24.1: Evidence-backed calculation and study basis for Capstone A: Blue H2 Plant Python Study.](figures/fig_ch24_blue_capstone.png)

**Calculation basis.** Stoichiometric SMR/ATR/POX/WGS balances, equilibrium or route-builder sweeps, PSA recovery accounting, and CO2-capture mass-balance indices.

**Public study or standard basis.** IEA hydrogen route context plus process-design and gas-purification references set the screening ranges and interpretation limits. \cite{iea2023hydrogen,towler2013chemical,kohl1997,gpsa2012}

**Discussion.** The figure turns an integrated blue H2 screening case into a data-backed study with traceable assumptions. The plotted values are generated from the chapter's calculation table, while the basis panel records the independent study or standard used to interpret plant H2 rate, CO2 intensity, fuel balance, CAPEX, and acceptance criteria. The physical mechanism behind the figure is
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
calculation, validate against a physical limit, extract plant H2 rate, CO2 intensity, fuel balance, CAPEX, and acceptance criteria, and save the
evidence. The full notebook structure, the three-step validation routine
(balance, physical-limit, and reference-case checks), the patterns for extending
a single converged case into a sensitivity or technology study, and the
peer-review prompts are collected once in *Appendix: Notebook structure,
validation, and review methodology* so they are not repeated in every chapter.
Chapter 3 covers the setup and reproducibility habits those steps rely on. Apply
that routine to the model in this chapter (an integrated blue H2 screening case) before treating any number
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

- Set up a NeqSim Python notebook that addresses an integrated blue H2 screening case with an explicit
  fluid, equipment block, run, and validation step.
- Identify the KPIs (plant H2 rate, CO2 intensity, fuel balance, CAPEX, and acceptance criteria) and report them with units and a screening band.
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

This chapter positioned an integrated blue H2 screening case inside a reproducible NeqSim workflow. The main
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
