# Well and choke simulation in NeqSim

## Overview
NeqSim combines well inflow performance relationships with hydraulic flowline models and production chokes to represent surface networks. A `WellFlowlineNetwork` assembles wells, optional chokes, and pipelines into branches that are gathered in manifolds for steady-state or transient calculations.

## Well inflow models
`WellFlow` supports several inflow performance relationships that can either solve for outlet pressure from a specified flow or compute flow from a specified outlet pressure:

- **Production index (PI)** – Constant PI using squared-pressure drawdown.
- **Vogel** – Empirical oil well relationship using a reference test to derive the productivity curve.
- **Fetkovich** – Gas deliverability using C and n coefficients in squared-pressure space.
- **Backpressure with non-Darcy term** – Deliverability equation \(p_r^2 - p_{wf}^2 = a \cdot q + b \cdot q^2\) where the quadratic term captures turbulence/non-Darcy skin. The model is solved in either direction, with guards for insufficient drawdown.
- **Table-driven inflow** – User-supplied pairs of bottom-hole pressure and flow rate are sorted and linearly interpolated to compute flow or back-calculate the required pressure for a requested rate.

All models can switch between computing outlet pressure or flow via `solveFlowFromOutletPressure(boolean)`, enabling backpressure solves from downstream network pressure when desired.

## Choke representation
Production chokes are modeled as `ThrottlingValve` instances using IEC 60534 sizing. Chokes can be attached per branch and run in steady-state or transient mode. Valve travel and characterization are captured through the underlying valve model, and choking conditions can be toggled and tuned at the valve level.

## Network coupling
`WellFlowlineNetwork` wires wells and optional chokes into `PipeBeggsAndBrills` flowlines, collects them in manifolds, and optionally sends the combined stream downstream. The network offers steady-state and transient execution modes, supports target endpoint pressure solving, and can propagate arrival pressures back to well outlets for iterative backpressure calculations.
