---
title: "Distillation column algorithm"
description: "This document describes the mathematical model and solver implementations that power the"
---

# Distillation column algorithm

This document describes the mathematical model and solver implementations that power the
`DistillationColumn` class in NeqSim. The class maps directly to the files
`src/main/java/neqsim/process/equipment/distillation/DistillationColumn.java` and
`DistillationColumnMatrixSolver.java`.

## Governing equations

Each ideal-equilibrium tray satisfies the familiar MESH relationships:

1. **Total mass balance (tray j)**

   $
   V_{j-1} + L_{j+1} + F_j = V_j + L_j
   $

2. **Component balances**

   $
   V_{j-1} y_{i,j-1} + L_{j+1} x_{i,j+1} + F_j z_{i,j}
   = V_j y_{i,j} + L_j x_{i,j}
   $

3. **Phase equilibrium (K-values)**

   $
   y_{i,j} = K_{i,j} x_{i,j}, \qquad K_{i,j} = \frac{\hat f_{i,j}^{\text{vap}}}{\hat f_{i,j}^{\text{liq}}}
   $

4. **Energy balance**

   $
   V_{j-1} h_{j-1}^{V} + L_{j+1} h_{j+1}^{L} + F_j h_j^{F} + Q_j
   = V_j h_j^{V} + L_j h_j^{L}
   $

NeqSim evaluates fugacity-based K-values and molar enthalpies through the active
`SystemInterface`. The matrix solver also uses linearized component balances in
tridiagonal form:

$
A_j l_{i,j-1} + B_j l_{i,j} + C_j l_{i,j+1} = D_{i,j}
$

with stripping factors $S_j = K_{i,j} V_j / L_j$ embedded in the diagonal terms.

Temperature updates rely on the log-Newton step derived from $\sum_i y_{i,j}=1$:

$
\Delta T_j = -\frac{\ln(\sum_i K_{i,j} x_{i,j}) R T_j^2}{h_j^{V} - h_j^{L}}
$

The code limits $\Delta T_j$ to ±5 K and enforces bounds of 50–1000 K for numerical
stability.

## Column preparation

1. **Feed assignment** – feeds are attached with `addFeedStream`; unassigned feeds are
   auto-placed near matching tray temperatures.
2. **Temperature seeding** – `init()` runs the lowest feed tray, extrapolates temperatures
   towards condenser and reboiler, and links neighbouring trays with vapour/liquid streams.
3. **Pressure profile** – `prepareColumnForSolve()` imposes a linear pressure drop between the
   configured bottom and top pressures (or inferred tray values when unspecified).

## Solver implementations

| Solver | Class/Method | Strategy | Notes |
| --- | --- | --- | --- |
| `DIRECT_SUBSTITUTION` | `solveSequential()` | Classic two-sweep sequential substitution (liquids down, vapours up) with adaptive relaxation on temperatures and streams. | Converges robustly for well-behaved systems; default choice. |
| `DAMPED_SUBSTITUTION` | `runDamped()` | Same equations as direct substitution but starts with a user-defined fixed relaxation factor before enabling adaptation. | Useful for stiff columns where the default step overshoots. |
| `INSIDE_OUT` | `solveInsideOut()` | Quadrat-structure inside-out method: streams are relaxed against previous iterates while tray properties update using enthalpy-driven temperature corrections. | Balances mass/energy less frequently to reduce cost and supports a polishing phase for tight tolerances. |
| `BROYDEN` (experimental) | `runBroyden()` | Applies a secant correction on tray temperatures, effectively mixing current and previous deltas. | Handy for rapid feasibility studies but less stable than inside-out. |
| `MATRIX_SOLVER` | `DistillationColumnMatrixSolver.solve()` | Builds component flow equations into a TDMA system, blends constant molar overflow (CMO) estimates with sum-rate flows, then updates temperatures via the log-Newton scheme above. | Eliminates explicit stream tearing by solving component balances directly; still refines temperatures iteratively. |

### Sequential substitution details

- Upward sweep: for trays below the lowest feed, new liquid draws from the tray above.
- Downward sweep: for trays above the lowest feed, vapour comes from the tray below.
- Convergence metric: average absolute temperature change.
- Relaxation policy: decreases when combined residual (temperature, mass, energy) grows by
  more than 5 %, increases when it shrinks by more than 2 %.
- Default tolerances were tightened in recent iterations (temperature to 0.01 K, mass/energy to
  1e-4 relative) to prevent premature termination when using highly non-ideal feeds.

### Inside-out specifics

- Maintains cached vapour/liquid streams (`previousGasStreams`, `previousLiquidStreams`).
- `applyRelaxation()` mixes flow, temperature, pressure, and composition prior to cloning.
- Mass and energy balances are evaluated every few iterations (stride depends on tray count) and
  more often near convergence.
- Optional polishing stage tightens tolerances to 1e-5 K / relative mass 1e-4 when base tolerances
  are met but the user requires stricter balances.
- Tracks per-iteration mass and energy residuals alongside relaxed stream norms so operators can
  audit the inside-out trajectory when debugging column stability.
- Records the peak relaxation factors applied to trays, providing a quick signal when the column
  required aggressive damping to converge.

### Matrix solver specifics

- Precomputes feed molar contributions (`feedFlows`, vapor/liquid split) per tray.
- Builds stripping factors $S_j$ to couple component molar flows between neighbouring trays.
- Uses constant molar overflow anchors (bottom vapour, top liquid) blended with instantaneous
  sum-rate flows: $L_j = w L_{\text{CMO},j} + (1-w) L_{\text{SR},j}$ with default
  $w = 0.95$.
- Applies damping on both component flows and total holdups to keep the linear update stable.
- Temperature correction follows the same log-Newton formula, requiring `system.init(2)` for
  enthalpy data and `system.init(1)` afterwards to refresh K-values.

## Result handling

Once any solver converges, the top gas outlet (`gasOutStream`) and bottom liquid outlet
(`liquidOutStream`) are cloned from the respective trays. Mass, energy, and iteration statistics
are exposed through getters such as `getLastIterationCount()`, `getLastMassResidual()`, and
`getLastEnergyResidual()`.

## Further improvements

- Support user-defined pressure profiles or tray-by-tray pressure drops.
- Provide Jacobian-based temperature updates for faster convergence with highly non-ideal feeds.
- Persist diagnostic data (residual history, damping schedule) for easier profiling across
  solver types.
