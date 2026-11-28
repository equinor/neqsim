---
title: "Process Simulation Documentation"
description: "Documentation for NeqSim's process simulation capabilities including equipment, pipelines, and system modeling."
---

{% include_relative README.md %}

## Flowline operation models

- [Safety Shutdown acoustic valve travel](../wiki/water_hammer_implementation.md#safety-shutdown-with-prescribed-actual-valve-travel): absolute liquid capacity, prescribed actual travel, conservative cell inventory and analytical evidence limits.

- [Two-fluid pipeline model](TWOFLUIDPIPE_MODEL.md): steady closures, transient qualification and configuration.
- [Compositional line-pack pipe](compositional-linepack-pipe.md): retrograde condensation, depressurisation and dew-point-guarded restart of long gas/condensate lines.
- [Non-equilibrium two-phase pipe flow](non-equilibrium-two-phase-pipe-flow.md): film-model heat and mass transfer rates in the `TwoFluidPipe` transient instead of a relaxation time.
- [Experimental pressure-driven pig mechanics](pig-motion): prescribed-pressure mechanics with explicit coupling limits.

## Residual film after pigging

- [Non-equilibrium MEG/water film transfer](TwoFluidPipeFilmDrying.md): experimental isothermal two-fluid component transfer and initial low-point inventories.
