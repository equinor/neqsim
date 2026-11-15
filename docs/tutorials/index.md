---
title: Tutorials Index
description: Step-by-step tutorials and learning paths for mastering NeqSim thermodynamic and process simulation.
---


Structured learning resources to help you master NeqSim.

## Learning Paths

Curated sequences of tutorials based on your role:

| Path | Description | Time |
|------|-------------|------|
| **[Learning Paths Overview](learning-paths.md)** | Choose your learning track | - |
| [PVT Engineer Track](learning-paths.md#pvt-engineer-path) | Fluids, flash, characterization | 4-6 hours |
| [Process Engineer Track](learning-paths.md#process-engineer-path) | Equipment, flowsheets, optimization | 6-8 hours |
| [Developer Track](learning-paths.md#developer-path) | API, architecture, extensions | 8-10 hours |

## Quick Start Guides

Get up and running fast:

| Guide | Time | Platform |
|-------|------|----------|
| **[Solve an Engineering Task](solve-engineering-task.md)** | **15-60 min** | **Any (VS Code, Codex, manual)** |
| **[Introduction to Agentic Engineering](../integration/ai_agentic_programming_intro.md)** | **20 min** | **VS Code + Copilot** |
| [Java Quickstart](../quickstart/java-quickstart.md) | 10 min | Java/Maven |
| [Python Quickstart](../quickstart/python-quickstart.md) | 5 min | Python/pip |
| [Colab Quickstart](../quickstart/colab-quickstart.md) | 1 min | Browser |

## Process Tutorials

Complete walkthroughs for common applications:

| Tutorial | Description | Level |
|----------|-------------|-------|
| **[TEG Gas Dehydration](teg_dehydration_tutorial.md)** | Equilibrium-contact screening with component and total mass balances | Intermediate |
| **[Gas-Oil Separation Plant (GOSP)](gosp_tutorial.md)** | Three-stage separation, material balance, and VPCR4 screening | Intermediate |
| **[PVT Lab Test Simulations](../pvtsimulation/pvt_lab_tests.md)** | CCE, CVD, DL, separator tests | Intermediate |
| **[Flow Assurance Overview](../pvtsimulation/flow_assurance_overview.md)** | Hydrates, wax, asphaltenes screening | Intermediate |
| **[UniSim/HYSYS to NeqSim Conversion](../process/unisim-to-neqsim-conversion.md)** | Convert UniSim `.usc` models to NeqSim | Intermediate |

## CO2 Impurity Kinetics Tutorials

These committed notebooks contain stored executed outputs, but they are experimental screening
models with illustrative, uncalibrated reaction parameters and fallback correlations. Their stored
status is not a current-`master` rerun or engineering qualification.

| Tutorial | Scope | Sources |
|----------|-------|---------|
| **100-hour CSTR experiment** | Three-phase CSTR setup, geometry, residence time, tabular results, and visualization | [Notebook](CO2_CSTR_Experiment_Tutorial.ipynb) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/tutorials/CO2_CSTR_Experiment_Tutorial.ipynb) |
| **Interactive kinetics and thermodynamics guide** | SRK-backed impurity screening, explicit fallback boundary, dynamic cases, and sensitivities | [Notebook](CO2_Impurity_Kinetics_Tutorial.ipynb) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/tutorials/CO2_Impurity_Kinetics_Tutorial.ipynb) |

Both notebooks use the shared [CO2 kinetics support module](neqsim_co2_kinetics.py).
Reproducible experiment drivers are available for the
[960-hour master run](run_master_long_experiment_960h.py),
[10 ppm stepwise run](run_stepwise_10ppm_every100h.py), and
[20 bara, -24 °C user case](run_user_20bar_minus24C_experiment.py).

## Video Tutorials

Coming soon! For now, see the [NeqSim Colab notebooks](https://github.com/EvenSol/NeqSim-Colab) for interactive examples.

## Next Steps

- **[Cookbook](../cookbook/index.md)** - Quick recipes for specific tasks
- **[Examples](../examples/index.md)** - Jupyter notebooks and code samples
- **[Troubleshooting](../troubleshooting/index.md)** - Common issues and solutions
- **[JavaDoc API](https://equinor.github.io/neqsim/javadoc/index.html)** - Complete API reference
