# NeqSim Base Modules

This document provides an overview of the seven foundational modules that make up NeqSim. Each module resides under `src/main/java/neqsim` and works together to support fluid characterization and process design.

## Thermodynamic routines
- **Location:** `thermo` and `thermodynamicoperations`
- **Features:** fluid phase equilibria, equation-of-state implementations, flash and phase envelope calculations.

## Physical properties routines
- **Location:** `physicalproperties`
- **Features:** density, viscosity, heat capacity and other transport properties calculated from thermodynamic state.

## Fluid mechanic routines
- **Location:** `fluidmechanics`
- **Features:** models for steady-state and transient flow, pipeline hydraulics, and flow node/leg modelling.

## Unit operations
- **Location:** `process/equipment`
- **Features:** library of process equipment including separators, heat exchangers, valves and other common unit operations.

## Chemical reactions routines
- **Location:** `chemicalreactions`
- **Features:** equilibrium and kinetic reaction models with support for reaction mechanisms and kinetic rate expressions.

## Parameter fitting routines
- **Location:** `statistics/parameterfitting`
- **Features:** tools for regression and parameter estimation using experimental data, including Monte Carlo simulations.

## Process simulation routines
- **Location:** `process`
- **Features:** framework for assembling flowsheets and running dynamic or steady-state process simulations that couple unit operations and property packages.

