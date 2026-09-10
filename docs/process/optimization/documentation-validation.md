---
title: Running the Optimization Documentation
description: Executable coverage, test commands, notebook source setup and physical validation limits for production optimization.
---

# Running the optimization documentation

The production optimization examples are executable regression inputs. Their tests read
the code directly from Markdown and run notebooks from a fresh kernel in cell order.
The [production optimization documentation workflow](https://github.com/equinor/neqsim/actions/workflows/practical-optimization-examples.yml)
checks changes to the guides, notebooks, supporting equipment and optimizer code.

## What is checked

| Documentation | Executable coverage |
|---|---|
| Getting started, overview and plugin architecture | Every Java block, including connected process-model, hydraulic-action, allocation and bottleneck-relief fixtures; both overview Python blocks |
| Practical examples | All five Java classes and three Python scripts, including their entry points and generated CSV/PNG files |
| Production, compressor, constraint and bottleneck guides | Java API fragments, full process examples, and every Python block with the declared prerequisite equipment |
| Flow-rate, multi-objective, batch, SQP, researcher and reconciliation guides | Java examples and every Python block; independent analytical optima, objective direction, constraints, steady-state statistics and mass reconciliation |
| Capacity, design, pressure-boundary and process-module guides | Equipment, registry, constraint, boundary and supplied-table examples using explicit synthetic design ratings |
| External optimizer, multi-scenario, utility and pipeline guides | Java examples and all Python workflows, including SciPy, NLopt, hydraulic actions, allocation and paired debottleneck studies |
| Six documentation notebooks and eight linked field/network notebooks | Every code cell, retained rich outputs, HTML export, feasibility and physical consistency assertions |

Short API fragments use the equipment/model described on their page. They are compiled
inside an external client class or run with an explicit test fixture; they are not
presented as standalone Java files. Complete examples retain their own setup.

Checks include selected-point replay, hard-limit compliance, inlet/outlet mass balance,
positive compressor duty, power-unit consistency, signed network flow conservation,
standard-volume/GOR consistency and rejected or infeasible cases. Solver termination
alone is insufficient evidence of a physically valid optimum.

`ProductionOptimizer` never accepts cached search evidence as the final plant state. It
replays the selected decision vector through the full process. If that replay is
infeasible, it deterministically tries previously feasible search points in best-first
order and returns only the first point that passes a fresh full replay. If none passes,
the selected point is restored and reported as infeasible; an exception from the final
solve is propagated instead of returning stale evidence.

## Run the checks

Use Java 17 or another supported full JDK and the Python interpreter selected for your
workspace. Java snippets are compiled against the Java 8 API. In the commands below,
`python` means that selected interpreter.

```bash
./mvnw -B -ntp -DskipTests compile dependency:build-classpath \
  -Dmdep.outputFile=target/optimization-classpath.txt
python -m pip install neqsim==3.20.0 numpy scipy pandas matplotlib nlopt \
  nbformat nbclient nbconvert ipykernel
```

The workflow contains the complete affected Java regression selection. Its documentation
tests can also be run directly:

```bash
./mvnw -B -ntp \
  -Dtest=PracticalOptimizationDocumentationTest,OptimizationEntryDocumentationTest,ProductionOptimizationGuideExamplesTest,ProcessModelOptimizationOverviewDocumentationTest,OptimizationGuideExamplesTest,CapacityOptimizationDocumentationTest,OptimizationIntegrationDocumentationTest \
  test
python devtools/check_optimization_java_fragments.py
```

Select the compiled Java source for Python checks; otherwise the two standalone example
tests use the installed `neqsim` package's JAR:

```bash
export NEQSIM_TEST_CLASSPATH="$PWD/target/classes:$(cat target/optimization-classpath.txt)"
python -m unittest devtools.test_practical_optimization_examples -v
python -m unittest devtools.test_optimization_entry_examples -v
python src/test/python/test_production_optimization_guides.py
python -m unittest devtools.test_optimizer_guide_examples -v
python devtools/test_optimization_integration_examples.py
```

Prepare the notebook dependency classpath, then run all notebooks or name one from the
runner's help output:

```bash
./mvnw -B -ntp dependency:build-classpath \
  -Dmdep.outputFile=target/neqsim-dev-classpath.txt
python devtools/check_optimization_notebooks.py
python devtools/check_optimization_notebooks.py ProductionOptimizer_Tutorial
```

The default executor is `nbclient`, with a fresh kernel using the exact selected Python
interpreter. An explicit `--executor ipython` option supports environments that prohibit
local kernel sockets; it uses a fresh child process, sequential cell execution and rich
display capture. There is no automatic fallback. Execution records identify the executor,
input hash, cell count, result and runtime. Executed notebooks, figures, HTML and failure
logs are written under `target/optimization-notebooks/` without overwriting the source.

The notebooks prefer compiled workspace classes. Their Colab bootstrap obtains the
corrected documentation source from `refs/pull/3597/head`; set `NEQSIM_GIT_REF` to select
another revision deliberately. The released `neqsim==3.20.0` JAR alone does not contain
all fixes exercised by this suite. The standalone practical and overview Python examples
are also tested separately with that released JAR.

## Model scope

Examples use synthetic fluids, dimensions, operating conditions and installed ratings.
A sampled optimum or Pareto front is conditional on those assumptions and the enabled
constraints. An unrated compressor map, a disabled constraint or a surrogate objective
does not establish physical plant capacity. Full-field optimization is a staged choke
and arrival-pressure search, not proof of a global joint optimum.

VFP routines have separate contracts for supplied-BHP formatting, fixed-composition
capacity screening and independently qualified well calculations. The
[export contract](vfp-export-contract.md) implements #3600: complete indexed axes,
METRIC/FIELD conversion, strict infeasible-point rejection and failure of unsupported
process-to-BHP mappings. Independent slash-record parsing and authored fixtures cover the
deck representation; they do not constitute execution of a reservoir simulator or
validation of a physical well model. The simplified network choke's
critical-flow response is tracked in [#3601](https://github.com/equinor/neqsim/issues/3601).
Autosized compressor-map replay and broader nonlinear solver robustness remain part of
the [plant optimization roadmap](https://github.com/equinor/neqsim/issues/3154). These
limitations are stated in the relevant examples; none is hidden by accepting a failed
solve or relaxing its physical assertions.
