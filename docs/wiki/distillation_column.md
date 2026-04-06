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

Rather than solving these equations algebraically, NeqSim uses a **tray-by-tray flash approach**: each tray mixes its input streams and performs a pressure–enthalpy (PH) flash. This automatically satisfies M, E, S, and H for any equation of state available in NeqSim (SRK, CPA, GERG-2008, etc.).

## Available solver types

| Solver | Description | Best for |
|--------|-------------|----------|
| `DIRECT_SUBSTITUTION` | Classic tray-by-tray without damping (default) | General use |
| `DAMPED_SUBSTITUTION` | Adaptive relaxation controller | Difficult polar/CPA systems |
| `INSIDE_OUT` | Three-sweep IO with stripping factor correction and K-value tracking | Multi-feed, general-purpose, debugging |
| `WEGSTEIN` | Wegstein acceleration of successive substitution | Fast convergence on well-posed problems |
| `SUM_RATES` | Flow-corrected tearing method | Absorbers and strippers |
| `NEWTON` | Newton-Raphson simultaneous temperature correction | Difficult convergence, many-stage columns |

### Solver mathematics

**DAMPED_SUBSTITUTION** applies a relaxation factor $\alpha$ to stream and temperature updates:

$$T_j^{k+1} = T_j^k + \alpha (T_j^{flash} - T_j^k)$$

The factor adapts automatically: if the combined residual grows, $\alpha \leftarrow \max(\alpha_{min},\; 0.5\alpha)$; if it shrinks, $\alpha \leftarrow \min(\alpha_{max},\; 1.2\alpha)$.

**WEGSTEIN** accelerates successive substitution using the estimated slope of the fixed-point map:

$$T_j^{k+1} = (1 - q_j)\, g(T_j^k) + q_j\, T_j^k, \quad q_j = \frac{s_j}{s_j - 1}, \quad s_j = \frac{g(T_j^k) - g(T_j^{k-1})}{T_j^k - T_j^{k-1}}$$

with $q$ bounded to $[-2, 0]$ (more conservative than the classical $[-5, 0]$) to prevent divergence on multi-component systems. A warm-up phase of direct substitution iterations is run first to establish stable iterates.

**SUM_RATES** adjusts temperatures using flow correction factors:

$$\alpha_{eff} = \alpha \cdot \theta, \quad \theta = \frac{1}{\bar{r}}, \quad \bar{r} = \frac{1}{N}\sum_{j=1}^{N} \frac{L_j^{out} + V_j^{out}}{F_j^{in}}$$

**NEWTON** treats all $N$ tray temperatures as simultaneous variables. The residual is $f_i(\mathbf{T}) = T_i^{sweep} - T_i$ and the Jacobian is computed by finite-difference perturbation ($\epsilon = 0.1$ K):

$$J_{ij} \approx \frac{f_i(\mathbf{T} + \epsilon \mathbf{e}_j) - f_i(\mathbf{T})}{\epsilon}$$

A line search ($\lambda = 1, 0.5, 0.25, 0.125$) controls step size, and 2–3 warm-up direct substitution iterations establish the convergence basin.

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
column.getLastSolveTimeSeconds();      // wall-clock time
column.getConvergenceHistory();        // per-iteration [temp, mass, energy] (IO adds K-value residual as 4th element)
```

### Convergence criteria

All solvers use three residual metrics:

1. **Temperature**: $\varepsilon_T = \frac{1}{N}\sum_{j=1}^{N} |T_j^{new} - T_j^{old}|$ (K)
2. **Mass balance**: $\varepsilon_M = \max\!\Big(\max_j \frac{|M_j^{in} - M_j^{out}|}{M_j^{in}},\; \frac{\sum|M_j^{in}-M_j^{out}|}{\sum M_j^{in}}\Big)$
3. **Energy balance**: analogous per-tray and column-wide relative error

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

- Converges in the fewest iterations (7 for 5-tray, 18 for 10-tray deethanizer)
- Most reliable solver for difficult convergence cases
- Higher per-iteration cost: best suited for problems where sequential methods fail

```java
column.setSolverType(DistillationColumn.SolverType.NEWTON);
column.run();
```

## Suggestions for future improvement

- Thomas algorithm tridiagonal material balance in the IO inner loop
- Inner-loop enthalpy correlation for energy balance correction
- Non-linear pressure profiles or user specified pressure drops per tray
- Automatic solver selection based on problem characteristics
- Per-tray Murphree efficiency specification (current implementation is column-wide)
- Mass-balance-preserving Murphree via modified K-values in the flash loop
