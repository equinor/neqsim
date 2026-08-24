---
title: "Distillation column algorithm"
description: "Mathematical model, solver implementations, automatic solver selection, specification homotopy, adaptive matrix inside-out warm starts, MESH residual diagnostics, and convergence behavior for NeqSim distillation columns."
---

# Distillation column algorithm

This document describes the mathematical model and solver implementations that power the
`DistillationColumn` class in NeqSim. The implementation is centered on
`src/main/java/neqsim/process/equipment/distillation/DistillationColumn.java` and the
package-local solver and residual helper classes in the same package.

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

The matrix inside-out warm-start updates tray temperatures from the bubble-sum residual
$\sum_i K_{i,j} x_{i,j} - 1$ and cached derivatives of $\ln K$ with respect to temperature:

$$
\Delta T_j = -\frac{\sum_i K_{i,j} x_{i,j} - 1}
{\sum_i x_{i,j} K_{i,j} \frac{\partial \ln K_{i,j}}{\partial T}}
$$

The code limits $\Delta T_j$ to ±8 K and enforces bounds of 50–1000 K for numerical
stability during the matrix warm-start stage.

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
| `MATRIX_INSIDE_OUT` | `solveMatrixInsideOut()` | Adaptive matrix inside-out mode that bypasses matrix setup for small columns and otherwise solves component-balance tridiagonal systems as a warm start before rigorous inside-out polishing. | Avoids warm-start overhead on small columns while preserving a matrix path for larger hydrocarbon fractionators. |
| `WEGSTEIN` | `solveWegstein()` | Wegstein acceleration on the sequential temperature map after direct-substitution warm-up. | Speeds up well-conditioned fixed-point problems. |
| `SUM_RATES` | `solveSumRates()` | Flow-corrected tearing method that adjusts relaxation from tray sum-rate behavior. | Native for absorbers and reboiler-only strippers; condenser configurations remain guarded to damped substitution. |
| `NEWTON` | `solveNewton()` | Finite-difference Newton correction on tray temperatures with line search. | A tray-temperature accelerator, not a full simultaneous MESH Newton solver. |
| `NAPHTALI_SANDHOLM` | `solveNaphtaliSandholm()` | Inside-out warm start followed by guarded simultaneous Newton correction of liquid component flows, tray temperatures, and vapor flows. | Best for rigorous residual-driven MESH convergence on well-conditioned hydrocarbon fractionators. |
| `MESH_RESIDUAL` | `solveMeshResidual()` | Inside-out initialization followed by full MESH residual evaluation. | Best for auditing material, equilibrium, summation, energy, specification, and product-draw residuals. |
| `AUTO` | `ColumnSolverFactory.AutoSolver` | Runs a feasibility pre-screen and copy-based solver probes. A fixed-specification reboiler-only stripper tries native sum-rates before paying for the relaxed damped base; other configurations retain the robust base/fallback ladder. | Useful when an agent or workflow should request robust automatic solver selection while still reporting the concrete solver through `getLastSolverTypeUsed()`. |

### Accelerated full-sweep workspace

The finite-difference `NEWTON` route and the final `WEGSTEIN` stream synchronization use
undamped full-tray sweeps. Each internal vapor or liquid transfer now takes one owned clone of the
already-flashed tray outlet and installs that same snapshot as the target tray inlet. Because unit
relaxation never consumes a previous iterate, these sweeps do not allocate per-tray previous-stream
arrays or create a second cache clone. The one owned snapshot still follows the established
relaxation and reflash path, preserving downstream tear-state thermodynamic semantics.

Use `getLastAcceleratedFullTraySweepCount()` and
`getLastAcceleratedInternalStreamTransferCount()` to audit this work. The transfer count equals the
number of downward liquid plus upward vapor transfers across all reported sweeps. These values
describe accelerator work attempted by the latest route; they are preserved when `AUTO` adopts a
candidate and can remain nonzero when a later coordinated fallback completes the solve. They do
not replace mass, energy, specification, physical-state, or MESH convergence evidence.

### Sequential substitution details

- Upward sweep: for trays below the lowest feed, new liquid draws from the tray above.
- Downward sweep: for trays above the lowest feed, vapour comes from the tray below.
- Convergence metric: average absolute temperature change.
- Relaxation policy: decreases when combined residual (temperature, mass, energy) grows by
  more than 5 %, increases when it shrinks by more than 2 %.
- Adaptive default tolerances scale with column complexity. The base values are 9e-3 K for
  temperature and 1.6e-2 relative for mass and energy residuals unless the user overrides them.
- Native reboiler-only `SUM_RATES` solves use a tighter internal temperature target while keeping
  the public tolerance unchanged. Other sequential solvers retain their established convergence
  target; the margin ensures sum-rates terminal products agree with the damped reference near phase
  boundaries.
- A separated terminal product whose unintended minority phase is at most `1e-8` of the mole
  inventory is rebuilt as the intended outlet phase using the complete component-mole vector. This
  canonical phase definition preserves total and per-component flow while preventing a numerical
  parts-per-billion trace from creating solver-dependent product phase counts. Larger phase
  fractions remain untouched.

### Automatic solver pipeline

`AUTO` mode is intended for workflows where the caller wants a robust answer and diagnostics rather
than a specific numerical method. The pipeline first calls `screenSpecificationFeasibility()` and
adds a `FEASIBILITY_SCREEN` entry to the automatic solver summary. It then creates a candidate copy,
tries native `SUM_RATES` first for a fixed-specification reboiler-only stripper. If that candidate is
not applicable or is rejected, `AUTO` tries shortcut or thermodynamic-profile initialization, runs a
relaxed `DAMPED_SUBSTITUTION` base solve, and probes an ordered candidate list on copies of that
warmed base state.

The candidate order depends on column structure:

- reactive columns prioritize `NAPHTALI_SANDHOLM`, `MESH_RESIDUAL`, then damped substitution;
- adjustable product specifications use `MATRIX_INSIDE_OUT` first for larger columns and
  `INSIDE_OUT` first for smaller columns, followed by residual-oriented solvers and damped
  substitution;
- two-ended columns without adjustable product specs use matrix or regular inside-out candidates
  when tray count justifies them;
- fixed-specification absorber and reboiler-only stripper configurations without a condenser can
  accept native `SUM_RATES`; condenser-only configurations remain guarded to damped substitution.

Candidate probes do not run their own nested damped fallback solves. `AUTO` accepts the first solved
candidate that did not rely on guarded fallback products; otherwise it scores the available results
by residuals and falls back to a live damped-substitution solve when necessary. The public solver
request remains `AUTO`, while `getLastSolverTypeUsed()` reports the concrete accepted solver and
`getLastAutoSolverSummary()` records the candidate trace.

## Complete usage example

```java
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

// 1. Create feed fluid (natural gas condensate at 216 K, 30 bar)
SystemInterface feed = new SystemSrkEos(216.0, 30.0);
feed.addComponent("methane", 0.5);
feed.addComponent("ethane", 0.2);
feed.addComponent("propane", 0.15);
feed.addComponent("n-butane", 0.05);
feed.addComponent("n-pentane", 0.05);
feed.addComponent("n-hexane", 0.03);
feed.addComponent("n-heptane", 0.02);
feed.setMixingRule("classic");

// 2. Wrap in a stream and set flow rate
Stream feedStream = new Stream("feed", feed);
feedStream.setFlowRate(100.0, "kg/hr");
feedStream.run();

// 3. Build a 5-tray deethanizer (with reboiler, no condenser)
DistillationColumn column = new DistillationColumn("Deethanizer", 5, true, false);
column.addFeedStream(feedStream, 5);             // feed on tray 5 (top)
column.getReboiler().setOutTemperature(378.15);   // 105 °C
column.setTopPressure(30.0);                      // bar
column.setBottomPressure(32.0);

// 4. Select solver and run
column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
column.setMaxNumberOfIterations(50);
column.run();

// 5. Read results
System.out.println("Converged: " + column.solved());
System.out.println("Gas product:    " + column.getGasOutStream().getFlowRate("kg/hr") + " kg/hr");
System.out.println("Liquid product: " + column.getLiquidOutStream().getFlowRate("kg/hr") + " kg/hr");
System.out.println("Iterations:     " + column.getLastIterationCount());
System.out.println("Solve time:     " + column.getLastSolveTimeSeconds() + " s");
```

## Algorithm overview

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

## Outer tear variables

Some column features are coupled outside the inner tray solver because they change the traffic or
pressure profile that the tray sweeps depend on:

- side-draw flow specifications adjust tray gas or liquid draw fractions until the target product
  flow is reached;
- liquid pumparounds withdraw internal tray liquid, update a cooled or heated return stream, and
  re-solve the column with that return connected to the configured tray;
- optional hydraulic pressure-drop coupling rates internals, updates the top/bottom pressure
  profile from total pressure drop, and re-solves until the pressure-profile change is small.

`solveWithColumnTearVariables()` wraps the selected inner solver for these features. It records
`getLastColumnTearIterationCount()`, `getLastColumnTearResidual()`,
`isLastColumnTearConverged()`, `getLastPumparoundRelativeChange()`,
`getLastHydraulicPressureDropPa()`, and `getLastHydraulicPressureDropResidual()`. If a side-draw
target is physically impossible, the draw fraction is bounded by available tray traffic and the
latest tear diagnostics report non-convergence instead of allowing an impossible product draw.

A single independent side-draw flow specification is first evaluated on cold copied candidate
states. Only rigorous or reconciled inner-column solves can influence the controller or replace the
public column result. If a cold candidate is rejected after a rigorous state has been found, the
same fraction is retried once by continuation from the nearest accepted state. This makes the
controller robust to runtime-dependent cold-start basins without ever continuing from fallback
products. Failed and fallback-product candidates are rejected, the last accepted state is retained,
and subsequent interpolation or bounded exploration uses accepted flow observations only. Audit
the cold and continuation attempts with `getLastColumnTearRejectedCandidateCount()`,
`getLastColumnTearRollbackCount()`, `getLastColumnTearInnerIterationCount()`, and
`getLastColumnTearCandidateHistory()`. These transient diagnostics reset after copying or
deserialization.

### Specification homotopy

Purity, recovery, and product-flow specifications can be difficult if the outer loop jumps directly
from the current product split to a sharp target. `setSpecificationHomotopySteps(steps)` stages the
effective target over the requested number of continuation steps. Each stage solves with a temporary
target between the current product value and the final user target; the stored public
`ColumnSpecification` still contains the final target. `getLastSpecificationHomotopyStepCount()`
reports how many staged targets were completed by the latest run.

`AUTO` mode enables three specification-homotopy stages automatically for adjustable product
specifications when the user has not configured a larger or smaller value. Reflux-ratio and duty
specifications are not staged because they are direct operating specifications rather than product
targets manipulated through condenser or reboiler temperature.

### Matrix solver specifics

- `MATRIX_INSIDE_OUT` is adaptive. Below 12 trays it bypasses the matrix stage and runs rigorous
  `INSIDE_OUT` directly because benchmark columns showed matrix setup overhead dominating runtime.
- For larger columns, the matrix stage precomputes feed component, feed vapor, and feed liquid
  molar contributions by tray.
- It builds one tridiagonal component-balance system per component using stripping factors
  $S_j = K_{i,j} V_j / L_j$ based on current tray vapor and liquid traffic.
- Vapor component flows follow from cached stripping factors, while liquid component flows come
  from the Thomas-algorithm solution of each tridiagonal system.
- Tray compositions, cached outlet streams, K-values, and K-temperature derivatives are updated
  without a rigorous PH flash inside the matrix loop.
- Final products are still accepted only after the rigorous inside-out polish and the normal mass,
  product, internal-traffic, fallback, and optional MESH residual gates.

### Naphtali-Sandholm solver specifics

- Uses the inside-out solution as a warm start so legacy tray flash behavior provides a robust
  initial state.
- Solves a simultaneous block of MESH residual equations with liquid component flows, tray
  temperature, and vapor flow as tray variables.
- Builds a finite-difference block-tridiagonal Jacobian from neighboring tray couplings and uses a
  guarded Newton line search with flow and temperature trust limits. Before each build, up to one
  full-column thermodynamic pass per local finite-difference variable refreshes the base. This
  matches the restore-evaluation budget that the frozen base replaces. The latest finite state
  among those passes is retained so the base owns the most thermodynamically consistent K-value
  fixed point, while the incoming derived state is only a non-finite fallback. Refinement stops
  early when consecutive residual vectors agree within one tenth of the outer tolerance. Every
  perturbed column then starts from that same frozen K-value, vapor-flow, and enthalpy state.
  Exact restoration prevents finite-difference column order from silently refining the base
  residual.
- Starts every backtracking line-search trial from the same primary and derived thermodynamic base,
  so a rejected larger step cannot change the K-value seed of the next trial. The accepted trial
  remains applied and its evaluated MESH residual is reused; the solver does not repeat the same
  thermodynamic evaluation merely to apply a step that the line search has already accepted.
- Retries a rejected retained-state solve from the normal cold initializer before materializing the
  rejected tray profile. This keeps the live column and its product caches unchanged until a cold
  recovery attempt has either been accepted or exhausted.
- Restores the intended single gas or liquid phase after composition initialization when applying
  no-side-draw products. This prevents initialization from re-expanding both phase slots with the
  same accepted component inventory.
- Package-level solver diagnostics record Jacobian base-refinement passes and the residual-vector
  mutation measured after each completed build. The mutation is expected to be bitwise zero; these
  counters support deterministic regression and do not change the public column API.
- Work diagnostics classify every currently assembled derivative column as finite-difference;
  `getLastNaphtaliAnalyticJacobianColumns()` remains available for compatibility and reports zero
  until a mixed analytic/numerical assembly is implemented.
- When coordinated routing rejects a Naphtali-Sandholm result and adopts damped substitution,
  products, residuals, status, and `getLastIterationCount()` describe the accepted fallback.
  Naphtali-specific Jacobian, thermodynamic, cache, K-value, and linear-solve counters continue to
  describe the rejected simultaneous attempt. Use `getLastSolverTypeUsed()` and
  `getLastSolveStatusReason()` with those counters to distinguish accepted-state convergence from
  attempted-solver work. An identical subsequent invocation reuses the accepted damped tray and
  product state through its full sequential-state fingerprint without labeling that state as
  Naphtali-owned. Changed feed, tray, product, or configuration state invalidates that reuse.
- Tray thermodynamics perform up to two forced-root fugacity sweeps for a cold solve and up to three
  when refining a retained column state. Both paths stop early when
  `max(abs(log(Knew/Kold)))` is already at or below `1e-8`. The extra warm-start sweep keeps the
  finite-difference residual locally consistent after a nearby operating-point change without
  adding EOS work to cold solves. Diagnostics report the actual sweep
  count, the number of tray evaluations whose final
  `max(abs(log(Knew/Kold)))` exceeds `1e-8`, and the largest such final update through
  `getLastNaphtaliThermoKValueIterationCount()`,
  `getLastNaphtaliThermoKValueNonConvergedCount()`, and
  `getLastNaphtaliThermoMaxLogKValueUpdate()`. These metrics expose incomplete inner convergence;
  they do not loosen the MESH acceptance criteria or add extra EOS work.
- Allows at most three line-search steps that fail to reduce the MESH residual. After three such
  non-descent steps, the solver restores the best finite tray state and returns the actual iteration
  count so the column can proceed to its coordinated fallback instead of exhausting the Newton
  budget.
- Supports optional `setSeedTemperature(stageIndex, temperatureK)` warm-start guesses. Seeds are
  initial values only; they do not pin tray temperatures or replace energy-balance residuals.
- Accepts the Newton-refined state only when the scaled MESH residual improves; otherwise the
  inside-out warm-start state is retained.

## Result handling

Once any solver converges, the top gas outlet (`gasOutStream`) and bottom liquid outlet
(`liquidOutStream`) are cloned from the respective trays. Mass, energy, and iteration statistics
are exposed through getters such as `getLastIterationCount()`, `getLastMassResidual()`, and
`getLastEnergyResidual()`.

Every `run()` also records a scaled MESH residual vector for the final column state. The vector is
assembled by `ColumnMeshResidualEvaluator` from a `ColumnMeshState` snapshot and groups equations
as material, equilibrium, summation, energy, product-draw, and specification residuals. Public
diagnostics expose the full norm and group norms through `getLastMeshResidualNorm()`,
`getLastMeshMaterialResidualNorm()`, `getLastMeshEquilibriumResidualNorm()`,
`getLastMeshSummationResidualNorm()`, `getLastMeshEnergyResidualNorm()`,
`getLastMeshProductDrawResidualNorm()`, and `getLastMeshSpecificationResidualNorm()`.

For `MATRIX_INSIDE_OUT`, diagnostics also expose the adaptive warm-start decision:
`wasMatrixInsideOutWarmStartUsed()`, `wasMatrixInsideOutWarmStartBypassed()`,
`getLastMatrixInsideOutIterationCount()`, `getLastMatrixInsideOutTemperatureResidual()`, and
`getLastMatrixInsideOutSolveTimeSeconds()`. These values make it clear whether a run paid the
matrix setup cost or intentionally followed the rigorous inside-out path directly.

By default, the MESH residual vector is diagnostic for sequential solver modes. For
`NAPHTALI_SANDHOLM` and `MESH_RESIDUAL`, the residual gate is effective unless explicitly disabled.
When the gate is effective, `solved()` requires the latest residual norm to be finite and less than
`getMeshResidualTolerance()`, and also requires the product-draw residual to pass
`getMeshProductDrawResidualTolerance()`. For legacy solver modes, calling
`setEnforceMeshResidualTolerance(true)` opts into the same convergence contract; calling it with
`false` explicitly disables the gate even for the residual-oriented solvers.

## MESH equations

Each equilibrium stage $j$ in a column with $N$ stages and $n_c$ components satisfies:

**Material balance (M):**

$$M_{i,j} = L_{j-1} x_{i,j-1} + V_{j+1} y_{i,j+1} + F_j z_{i,j} - (L_j + S_j^L) x_{i,j} - (V_j + S_j^V) y_{i,j} = 0$$

**Equilibrium (E):**

$$E_{i,j} = K_{i,j}\, x_{i,j} - y_{i,j} = 0$$

**Summation (S):**

$$S_j = \sum_{i=1}^{n_c} y_{i,j} - \sum_{i=1}^{n_c} x_{i,j} = 0$$

**Enthalpy balance (H):**

$$H_j = L_{j-1} h_{j-1}^L + V_{j+1} h_{j+1}^V + F_j h_j^F - L_j h_j^L - V_j h_j^V - Q_j = 0$$

Most NeqSim column solvers use a **tray-by-tray flash approach**: each tray mixes its input
streams and performs a pressure-enthalpy (PH) flash. This provides a robust MESH-consistent update
for any equation of state available in NeqSim (SRK, CPA, GERG-2008, etc.). The
`NAPHTALI_SANDHOLM` solver adds a simultaneous residual-correction layer on top of that warm start.

## Available solver types

| Solver | Description | Best for |
|--------|-------------|----------|
| `DIRECT_SUBSTITUTION` | Classic tray-by-tray without damping (default) | General use |
| `DAMPED_SUBSTITUTION` | Adaptive relaxation controller | Difficult polar/CPA systems |
| `INSIDE_OUT` | Three-sweep IO with stripping factor correction and K-value tracking | Multi-feed, general-purpose, debugging |
| `MATRIX_INSIDE_OUT` | Adaptive matrix warm start plus rigorous inside-out polish; bypasses matrix setup for small columns | Larger hydrocarbon columns where the matrix warm start can help |
| `WEGSTEIN` | Wegstein acceleration of successive substitution | Fast convergence on well-posed problems |
| `SUM_RATES` | Flow-corrected tearing method, native without a condenser and guarded otherwise | Absorbers and reboiler-only strippers |
| `NEWTON` | Newton-Raphson tray-temperature correction accelerator | Difficult temperature convergence cases |
| `NAPHTALI_SANDHOLM` | Simultaneous MESH residual Newton correction with guarded acceptance | Rigorous residual convergence checks |
| `MESH_RESIDUAL` | Inside-out initialization with MESH residual diagnostics | Residual auditing and diagnostics |
| `AUTO` | Feasibility-screened automatic candidate selection with copy-based solver probes and damped fallback | Agent workflows and uncertain column setups |

### Solver mathematics

**DAMPED_SUBSTITUTION** applies a relaxation factor $\alpha$ to stream and temperature updates:

$$T_j^{k+1} = T_j^k + \alpha (T_j^{flash} - T_j^k)$$

The factor adapts automatically: if the combined residual grows, $\alpha \leftarrow \max(\alpha_{min},\; 0.5\alpha)$; if it shrinks, $\alpha \leftarrow \min(\alpha_{max},\; 1.2\alpha)$.

**WEGSTEIN** accelerates successive substitution using the estimated slope of the fixed-point map:

$$T_j^{k+1} = (1 - q_j)\, g(T_j^k) + q_j\, T_j^k, \quad q_j = \frac{s_j}{s_j - 1}, \quad s_j = \frac{g(T_j^k) - g(T_j^{k-1})}{T_j^k - T_j^{k-1}}$$

with $q$ bounded to $[-2, 0]$ (more conservative than the classical $[-5, 0]$) to prevent divergence on multi-component systems. A warm-up phase of direct substitution iterations is run first to establish stable iterates.

**SUM_RATES** adjusts temperatures using flow correction factors:

$$\alpha_{eff} = \alpha \cdot \theta, \quad \theta = \frac{1}{\bar{r}}, \quad \bar{r} = \frac{1}{N}\sum_{j=1}^{N} \frac{L_j^{out} + V_j^{out}}{F_j^{in}}$$

**NEWTON** treats all $N$ tray temperatures as simultaneous variables for a temperature-correction
accelerator. The residual is $f_i(\mathbf{T}) = T_i^{sweep} - T_i$ and the Jacobian is computed by finite-difference perturbation ($\epsilon = 0.1$ K):

$$J_{ij} \approx \frac{f_i(\mathbf{T} + \epsilon \mathbf{e}_j) - f_i(\mathbf{T})}{\epsilon}$$

A line search ($\lambda = 1, 0.5, 0.25, 0.125$) controls step size. It retains the evaluated trial with the lowest finite residual, including when every trial is non-descent, and the applied state, reported step, and reported residual refer to that same trial. Inspect `getLastNewtonLineSearchStepLength()`, `getLastNewtonLineSearchResidual()`, and `getLastNewtonLineSearchTrialCount()` after a `NEWTON` run. Two to three warm-up direct-substitution iterations establish the convergence basin.

**MESH_RESIDUAL** starts from `INSIDE_OUT` and evaluates the scaled MESH residual vector without
running an additional Newton-polishing solve. When the residual or product-draw gate is not
satisfied, the current implementation leaves the inside-out state in place and reports the failed
gate through `solved()`, the residual getters, and `getConvergenceDiagnostics()`. This keeps
`MESH_RESIDUAL` as a diagnostics-oriented entry point; use `NAPHTALI_SANDHOLM` when a guarded
simultaneous residual correction should be attempted.

### Example: selecting a solver

```java
column.setSolverType(DistillationColumn.SolverType.WEGSTEIN);
column.run();
```

## Convergence diagnostics

After solving, the following metrics are available:

```java
column.getLastIterationCount();        // number of iterations
column.getLastTemperatureResidual();   // avg temperature change (K)
column.getLastMassResidual();          // relative mass balance error
column.getLastEnergyResidual();        // relative enthalpy balance error
column.getLastMeshResidualNorm();      // full scaled MESH residual infinity norm
column.getLastSolveTimeSeconds();      // wall-clock time
column.getConvergenceHistory();        // per-iteration [temp, mass, energy] (IO adds K-value residual as 4th element)
```

### Convergence criteria

All solvers track three scalar residual metrics, and the residual-oriented modes also gate on the
MESH diagnostics:

1. **Temperature**: $\varepsilon_T = \frac{1}{N}\sum_{j=1}^{N} |T_j^{new} - T_j^{old}|$ (K)
2. **Mass balance**: $\varepsilon_M = \max\!\Big(\max_j \frac{|M_j^{in} - M_j^{out}|}{M_j^{in}},\; \frac{\sum|M_j^{in}-M_j^{out}|}{\sum M_j^{in}}\Big)$
3. **Energy balance**: analogous per-tray and column-wide relative error
4. **Optional MESH residual**: infinity norm of the scaled material, equilibrium, summation,
   energy, product-draw, and specification residual vector

Tolerances scale with column complexity:

$$\tau = \tau_{base} \cdot \min\!\Big(2.5,\; \max\big(1 + 0.06(N_{stages}-3),\; 1 + 0.25(N_{feeds}-1)\big)\Big)$$

### Plotting convergence history

```java
// After column.run()
java.util.List<double[]> history = column.getConvergenceHistory();
for (int k = 0; k < history.size(); k++) {
    double[] h = history.get(k);
    System.out.printf("iter %3d  tempErr=%.2e  massErr=%.2e  energyErr=%.2e%n",
        k + 1, h[0], h[1], h[2]);
}
```

### Inside-Out solver details

The `INSIDE_OUT` solver performs three sweep phases per iteration (liquid down, vapor up, polish liquid down) with:

- **Stripping factor correction**: adjusts temperature updates using the V/L flow ratio on each tray:

$$T_j^{k+1} = T_j^k + \alpha \cdot \beta_j \cdot (T_j^{flash} - T_j^k), \quad \beta_j = 1 + 0.05\,\text{clamp}\!\Big(\ln\frac{V_j}{L_j},\, -1,\, 1\Big)$$

- **K-value convergence tracking**: monitors max relative K-value change across all trays and components:

$$\varepsilon_K = \max_{i,j} \frac{|K_{i,j}^k - K_{i,j}^{k-1}|}{K_{i,j}^{k-1}}$$

- **Accelerated relaxation ramp**: increases relaxation 1.3× per improving iteration (vs 1.2× for other solvers)
- **Simplified inner-loop K-value model**: between rigorous flash iterations, cheap bubble-point temperature corrections using the fitted correlation $\ln K_{i,j} = a_{i,j} + b_{i,j}/T$ reduce flash count. The Newton correction for the bubble-point condition $\Sigma = \sum_i K_i x_i = 1$ is:

$$\Delta T = -\frac{\Sigma - 1}{\sum_i \frac{-b_{i,j}}{T^2} K_i x_i}$$

The convergence history for IO includes a 4th element: `[tempErr, massErr, energyErr, kValueResidual]`.

### Configuring the inner loop

```java
column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
column.setInnerLoopSteps(3);  // default: 3 cheap iterations between outer flash updates
column.setInnerLoopSteps(0);  // disable simplified model (all iterations use rigorous flash)
column.setInnerLoopSteps(5);  // more inner steps = fewer flashes but less correction per step
```

## Murphree tray efficiency

To model non-ideal trays, set the column-wide Murphree efficiency:

```java
column.setMurphreeEfficiency(0.75); // 75% efficiency
```

The correction is applied after each tray flash using the standard post-correction
formula:

$$y_{i,j}^{out} = y_{i,j-1} + E_{MV} \cdot (y_{i,j}^{eq} - y_{i,j-1})$$

where $y_{i,j}^{eq}$ is the equilibrium composition from the flash, $y_{i,j-1}$ is
the inlet vapor from the tray below, and $E_{MV}$ is the Murphree tray efficiency.
The reboiler and condenser (when present) are always treated as equilibrium stages.

- Supported solvers: `DIRECT_SUBSTITUTION`, `INSIDE_OUT`
- Typical values: 0.7–0.9 for sieve trays, 0.5–0.7 for bubble-cap trays

### Newton-Raphson solver details

The `NEWTON` solver treats all tray temperatures as simultaneous variables and computes a Jacobian by finite-difference perturbation. Each iteration requires $N+1$ column sweeps (1 base + $N$ perturbations). The resulting linear system is solved by Gaussian elimination with partial pivoting and a backtracking line search controls step size.

- Can reduce tray-temperature oscillations after the direct-substitution warm-up
- Preserves the public `NEWTON` contract as a temperature accelerator rather than a full MESH solver
- Higher per-iteration cost: best suited for cases where temperature convergence dominates

```java
column.setSolverType(DistillationColumn.SolverType.NEWTON);
column.run();
```

## Suggestions for future improvement

- Thomas algorithm tridiagonal material balance in the IO inner loop
- Inner-loop enthalpy correlation for energy balance correction
- Non-linear pressure profiles or user specified pressure drops per tray
- Mass-balance-preserving Murphree via modified K-values in the flash loop
- Public structured diagnostics for AUTO feasibility and initialization reports
