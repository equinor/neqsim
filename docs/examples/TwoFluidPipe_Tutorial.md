# TwoFluidPipe Model Tutorial

## Multiphase Pipeline Flow Simulation with NeqSim

This tutorial demonstrates the use of NeqSim's `TwoFluidPipe` model for simulating multiphase (gas-liquid) flow in pipelines.

## Overview

The two-fluid model solves separate conservation equations for gas and liquid phases, providing detailed predictions of:

- **Pressure profiles** along the pipeline
- **Liquid holdup** (volume fraction of liquid)
- **Flow regimes** (stratified, slug, annular, etc.)
- **Temperature profiles** with heat transfer
- **Slug tracking** using Lagrangian methods

## Topics Covered

1. Basic two-phase pipe flow simulation
2. Transient simulation with Lagrangian slug tracking
3. Terrain-induced slugging analysis
4. Heat transfer modeling
5. Comparison of model types
6. Visualization of results

## View the Notebook

| Format | Link |
|--------|------|
| **nbviewer** | [View on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/TwoFluidPipe_Tutorial.ipynb) |
| **Colab** | [Open in Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/TwoFluidPipe_Tutorial.ipynb) |
| **GitHub** | [View on GitHub](https://github.com/equinor/neqsim/blob/master/docs/examples/TwoFluidPipe_Tutorial.ipynb) |

## References

- Bendiksen et al. (1991) "The Dynamic Two-Fluid Model OLGA"
- Taitel & Dukler (1976) "Flow Regime Transitions"
- Zabaras (2000) "Prediction of Slug Frequency"

## Related Documentation

- [TwoFluidPipe Model](../process/TWOFLUIDPIPE_MODEL.md)
- [Two-Fluid Model OLGA Comparison](../wiki/two_fluid_model_olga_comparison.md)
- [Pipeline Simulation Guide](../process/equipment/pipeline_simulation.md)
- [Fluid Mechanics Module](../fluidmechanics/README.md)
