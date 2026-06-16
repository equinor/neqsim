# Python Environment and Reproducibility

<!-- Estimated pages: 18-26 -->

## Learning objectives

After this chapter you should be able to:

1. Explain how to set up notebooks, environments, units, versioning, and result files for industrial studies.
2. Translate the topic into a reproducible NeqSim Python workflow.
3. Identify the dominant assumptions and sanity checks for the working environment around each hydrogen study.
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

Set up notebooks, environments, units, versioning, and result files for industrial
studies. The practical setting is the working environment around each hydrogen study. In a desktop simulator this
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
4. Convert the output to engineering KPIs: environment checks, unit conventions, and traceable results.
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

Specific NeqSim/Python surfaces and engineering references emphasized here: **neqsim-python, jpype, notebooks, matplotlib, JSON, Git**.



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
fluid = J.thermo.system.SystemSrkEos(298.15, 30.0)
fluid.addComponent("hydrogen", 0.80)
fluid.addComponent("methane", 0.05)
fluid.addComponent("CO2", 0.10)
fluid.addComponent("water", 0.05)
fluid.setMixingRule("classic")
ops = J.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()
print(fluid.getNumberOfPhases(), fluid.getDensity("kg/m3"), fluid.getEnthalpy("J/kg"))
```

## Parameter study script, graph, and discussion

The chapter includes a runnable parameter-study notebook at `notebooks/parameter_study.ipynb`.
It takes the compact script above one step further: the notebook defines a sweep,
builds a results table, saves a CSV/JSON evidence bundle, and writes the graph
shown below. The values are either direct engineering calculations or normalized
study indices from cited public references and standards. Re-run the notebook on
the active NeqSim branch when project values or branch-specific APIs change.

**Calculation basis.** A reproducibility score computed from visible artifacts: executable notebooks, result JSON files, graph files, traceable source code, and rerun path length.

**Study basis.** The public-study context is IEA's need for auditable low-emission hydrogen deployment evidence; the numerical scores are generated from the book workflow rubric. \cite{iea2023hydrogen}

| Execution mode | Setup effort | Fresh-branch fidelity | Repeatability |
|---|---|---|---|
| Installed package | 0.25 | 0.45 | 0.7 |
| Workspace devtools | 0.55 | 0.95 | 0.78 |
| Runner notebook | 0.72 | 0.95 | 0.94 |
| Report refresh | 0.65 | 0.8 | 0.9 |

![Figure 3.2: Parameter study for reproducibility modes in hydrogen notebooks.](figures/parameter_reproducibility_modes.png)

The reproducibility study compares local installed packages, workspace devtools loading, isolated runner execution, and cached report regeneration. The goal is to make environment choice a visible modelling assumption.

**Discussion.** Workspace devtools and runner execution score highest when the notebook must reflect the active NeqSim branch. Installed packages remain useful for published examples, but project studies need branch fidelity and repeatable execution. This is why generated notebooks include explicit setup and JSON outputs.








## Extended notebook laboratory

The short code pattern above is the smallest working fragment. A serious
chapter notebook should be longer and more explicit. For the working environment around each hydrogen study, build the
notebook around the base case, one sensitivity case, and one validation case. Keep the cells small enough that a reviewer can run
one section, inspect the state, and decide whether the next section is credible.
The objective is not to make the notebook decorative; it is to make the model
auditable.

| Notebook section | Purpose | Evidence to save |
|---|---|---|
| Input cell | Define composition, pressure, temperature, flow, technology, and standards basis. | A printed assumptions table with units. |
| Model cell | Create the NeqSim system, stream, unit operation, or process model. | The class names and setter values used. |
| Run cell | Execute the flash, unit operation, or `ProcessSystem`. | Convergence status and warning messages. |
| KPI cell | Extract environment checks, unit conventions, and traceable results. | A table with units and engineering labels. |
| Plot cell | Show the operating range, not just the base point. | PNG figure with axis labels and caption. |
| Check cell | Compare against bounds, standards, or reference equations. | Pass/warn/fail status with comments. |
| Export cell | Write the result into a dictionary or `results.json`. | A machine-readable artifact for reports. |

When expanding the notebook, start by turning the example into a function. The
function should take a small set of physical inputs and return a dictionary of
outputs. That one change makes the chapter useful for sensitivity studies,
optimization, uncertainty analysis, and automated reports. A good signature is
usually something like `run_case(feed, pressure, temperature, option)` rather
than a function with twenty unlabelled arguments. Put units in argument names or
docstrings, and return units in the result keys.

The first validation step is a balance check. For reacting and electrochemical
systems, the notebook should show where atoms or moles move. For compression
and pipeline systems, it should show mass-flow consistency. For purification,
it should show where hydrogen leaves the product boundary. For property-model
chapters, it should compare the calculated density or enthalpy to a reference
model or published data point. A model that has a plausible final number but no
traceable balance is not ready for reuse.

The second validation step is a physical-limit check. Examples include a
specific energy above the thermodynamic minimum for electrolysis, a compressor
discharge temperature below the material or seal limit, a PSA recovery inside
industrial ranges, a pipeline pressure above delivery pressure plus margin, or
a reaction result that moves in the correct direction when temperature,
pressure, or steam-to-carbon changes. This check is often more valuable than an
extra decimal place because it catches the wrong model before the final report
does.

The third validation step is a reference-case check. Use a simple published
benchmark, a notebook from a previous study, a known industrial range, or a
reference equation where available. In the hydrogen thermodynamics chapter this
means GERG-2008 and Leachman-style checks. In the reaction chapter it means
reaction extents, equilibrium direction, and heat-of-reaction signs. In the
transport chapter it means pressure drop and temperature profiles that respond
sensibly to diameter, roughness, ambient temperature, and flow rate. In the
materials chapter it means that the simulation outputs match the data fields a
material engineer actually needs.

A useful notebook also preserves negative results. If a flash fails, a reactor
does not converge, a compressor discharge temperature exceeds a limit, or a
pipeline case misses delivery pressure, write that down in the result table.
Failed cases are often more informative than successful ones because they mark
the edge of the operating envelope. The correct response is not to hide them;
the correct response is to make the failure reproducible and then decide
whether the model, input data, or process concept must change.

For Python Environment and Reproducibility, a strong final cell should contain a compact executive table. It
should have one row for the base case, one row for the most conservative
technical case, and one row for the most favourable case. The table should
include environment checks, unit conventions, and traceable results, plus a note about the thermodynamic model, the process
configuration, and the quality of the input data. That table is the bridge from
the teaching notebook to an engineering decision.




The preferred workflow is deliberately repetitive. Define the fluid, set the units, run
the flash or process, initialize properties, extract a small number of engineering key
performance indicators, and save the evidence. Repetition is not a lack of
sophistication; it is the mechanism that makes complex models reviewable and reusable.

## Worked simulation study

![Figure 3.1: Evidence-backed calculation and study basis for Python Environment and Reproducibility.](figures/fig_ch03_reproducibility_loop.png)

**Calculation basis.** A reproducibility score computed from visible artifacts: executable notebooks, result JSON files, graph files, traceable source code, and rerun path length.

**Public study or standard basis.** The public-study context is IEA's need for auditable low-emission hydrogen deployment evidence; the numerical scores are generated from the book workflow rubric. \cite{iea2023hydrogen}

**Discussion.** The figure turns the working environment around each hydrogen study into a data-backed study with traceable assumptions. The plotted values are generated from the chapter's calculation table, while the basis panel records the independent study or standard used to interpret environment checks, unit conventions, and traceable results. The physical mechanism behind the figure is
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

## Advanced modelling notes

The next level of detail is to decide which simplifications are allowed to
remain in the model. For the working environment around each hydrogen study, a simple equilibrium or equipment block is
often enough to rank alternatives, but it is rarely enough to freeze a design.
The modelling lead should write down which variables are design variables,
which variables are operating variables, and which variables are uncertain
parameters. A pressure level, for example, may be a design variable in a concept
study, an operating variable in a dispatch model, and an uncertain parameter in
an early vendor comparison. The same NeqSim object can support all three views,
but the notebook should label the view clearly.

A good hydrogen model also separates thermodynamic uncertainty from process
configuration uncertainty. Thermodynamic uncertainty lives in EOS selection,
binary interaction parameters, impurity treatment, water handling, and transport
property correlations. Process configuration uncertainty lives in bed count,
compressor staging, heat recovery, reactor approach to equilibrium, current
density, plant availability, and control philosophy. Mixing these two categories
in one sensitivity table makes the result hard to interpret. Keep the first
screening table small, then add separate thermodynamic and process tables when
the decision becomes sensitive.

The most useful extension point in NeqSim is not a single class. It is the
combination of a transparent `ProcessSystem`, addressable variables through
`ProcessAutomation`, and Python loops that can run families of cases. Once the base
case for the working environment around each hydrogen study is converged, the next study can sweep feed composition,
pressure, recovery, cell voltage, catalyst activity, or ambient temperature
without copying the flowsheet by hand. This is how a teaching example becomes a
concept-selection engine.

When the model supports a project decision, every result should carry a quality
label. A classroom calculation may be labelled educational. A screening study
may be AACE Class 5 or Class 4. A pre-FEED study needs vendor data, design code
checks, process guarantees, and a documented uncertainty range. This chapter's
workflow is deliberately compatible with that escalation: the first notebook is
small, but it already contains the habits needed for a defensible larger study.

## Extending the simulation

| Extension | Why it matters | NeqSim/Python pattern |
|---|---|---|
| Composition sweep | Tests feedstock or product-spec sensitivity. | Clone the fluid, change mole fractions, run the same process. |
| Pressure-level sweep | Reveals compression, purification, and storage trade-offs. | Update stream or equipment pressure through setters or `ProcessAutomation`. |
| Technology comparison | Compares PEM, alkaline, SOEC, ATR, SMR, or PSA configurations. | Wrap each case in a function returning KPIs and assumptions. |
| Uncertainty case | Gives P10/P50/P90 style decision support. | Use Python sampling with a NeqSim run inside each iteration where practical. |
| Report package | Makes the calculation reviewable. | Save figures, results.json, assumptions, and validation checks together. |

The extension step should be conservative. Change one idea at a time until the
model response is understood. If two parameters must be changed together, write
that coupling down explicitly. For example, increasing electrolyzer pressure may
reduce downstream compression but can also change stack assumptions and cooling.
Increasing PSA recovery may raise hydrogen product flow but can lower tail-gas
heating value. Increasing reformer temperature may improve methane conversion
but can worsen tube duty, materials limits, and catalyst deactivation. The
simulation is the place where these trade-offs become visible.

For the working environment around each hydrogen study, a useful design-review plot has three properties. First, the axes
carry units and show the operating range rather than only the base point.
Second, at least one line or marker is tied to a physical limit: equilibrium,
specification, material temperature, maximum pressure, or a cost threshold.
Third, the caption explains what decision the plot supports. A beautiful plot
that does not support a decision belongs in a notebook scratchpad, not in the
engineering report.

The final extension is to make the model callable. A function that takes inputs
and returns a dictionary of KPIs can be used by a notebook, a command-line
script, a FastAPI endpoint, an optimizer, or a digital-twin loop. That is the
practical reason this book uses Python rather than screenshots: the model can
become infrastructure.

## Technical review prompts

Use these prompts when reviewing the chapter model with another engineer:

- What decision would change if environment checks, unit conventions, and traceable results moved by 10 percent?
- Which assumption is most likely to be wrong in the current data set?
- Which result is governed by thermodynamics, and which is governed by equipment
    configuration?
- Which unit operation should receive vendor data first?
- Which safety or standards check must be completed before the result leaves
    screening status?
- Can the notebook be rerun by another engineer without editing hidden paths or
    undocumented environment variables?

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

- Set up a NeqSim Python notebook that addresses the working environment around each hydrogen study with an explicit
  fluid, equipment block, run, and validation step.
- Identify the KPIs (environment checks, unit conventions, and traceable results) and report them with units and a screening band.
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

This chapter positioned the working environment around each hydrogen study inside a reproducible NeqSim workflow. The main
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
