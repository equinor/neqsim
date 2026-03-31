---
title: "GibbsReactor and GibbsReactorCO2 — Reference Documentation"
description: "Comprehensive reference for NeqSim's Gibbs free energy minimisation reactors. Covers mathematical foundation, algorithm details, configuration parameters, usage examples, and literature references for chemical equilibrium calculations."
---

# GibbsReactor and GibbsReactorCO2 — Reference Documentation

## 1. Overview

NeqSim provides two reactor classes for computing **chemical equilibrium** via Gibbs free energy minimisation:

| Class | Package | Purpose |
|-------|---------|---------|
| `GibbsReactor` | `neqsim.process.equipment.reactor` | Core solver — Newton-Raphson with Lagrange multipliers |
| `GibbsReactorCO2` | `neqsim.process.equipment.reactor` | Wrapper for acid gas (CO₂/H₂S/SO₂/NOₓ) systems |

Both are `TwoPortEquipment` (one inlet stream, one outlet stream) and integrate seamlessly with `ProcessSystem` flowsheets.

---

## 2. Mathematical Foundation

### 2.1 The Gibbs Minimisation Problem

At fixed temperature $T$ and pressure $P$, a closed system reaches chemical equilibrium when its total Gibbs free energy is minimised subject to conservation of each chemical element.

**Objective:**

$$
\min_{\mathbf{n}} \; G(\mathbf{n}) = \sum_{i=1}^{N_c} n_i \mu_i
$$

where $n_i$ is the molar amount of species $i$ and $\mu_i$ is its chemical potential:

$$
\mu_i = G_{f,i}^{\circ}(T) + RT\ln\varphi_i + RT\ln y_i + RT\ln\left(\frac{P}{P^{\circ}}\right)
$$

- $G_{f,i}^{\circ}(T)$: standard Gibbs energy of formation at temperature $T$ (from Gibbs database, polynomial in $T$)
- $\varphi_i$: fugacity coefficient from the equation of state (SRK, PR, CPA, etc.)
- $y_i = n_i / N$: mole fraction, $N = \sum_j n_j$
- $P^{\circ} = 1$ bar: reference pressure

**Constraints:** Element balance for each element $k$:

$$
\sum_{i=1}^{N_c} a_{ki} n_i = b_k, \quad k = 1, \ldots, N_e
$$

where $a_{ki}$ is the number of atoms of element $k$ in species $i$, and $b_k$ is the total moles of element $k$ in the feed.

**References:**
- White, W. B., Johnson, S. M., and Dantzig, G. B. (1958). *Chemical Equilibrium in Complex Mixtures.* J. Chem. Phys. **28**(5), 751–755.
- Smith, W. R. and Missen, R. W. (1982). *Chemical Reaction Equilibrium Analysis: Theory and Algorithms.* Wiley.

### 2.2 Lagrangian Formulation

Introducing Lagrange multipliers $\lambda_k$ for the element constraints:

$$
\mathcal{L}(\mathbf{n}, \boldsymbol{\lambda}) = G(\mathbf{n}) - \sum_{k=1}^{N_e} \lambda_k \left(\sum_i a_{ki} n_i - b_k\right)
$$

The KKT (stationarity) conditions give the equilibrium equations $F_i = 0$:

$$
F_i \equiv \frac{\partial \mathcal{L}}{\partial n_i} = G_{f,i}^{\circ}(T) + RT\ln\varphi_i + RT\ln y_i + RT\ln P - \sum_{k} \lambda_k a_{ki} = 0
$$

Combined with the element balance constraints, this forms a system of $N_c + N_e$ nonlinear equations in $N_c + N_e$ unknowns ($\mathbf{n}$ and $\boldsymbol{\lambda}$).

### 2.3 Newton-Raphson Solver

The system is solved by Newton-Raphson iteration. At iteration $(m)$, solve:

$$
\begin{pmatrix} \mathbf{J} & -\mathbf{A}^T \\ \mathbf{A} & \mathbf{0} \end{pmatrix}
\begin{pmatrix} \Delta\mathbf{n} \\ \Delta\boldsymbol{\lambda} \end{pmatrix}
= -\begin{pmatrix} \mathbf{F} \\ \mathbf{b} - \mathbf{A}\mathbf{n}^{(m)} \end{pmatrix}
$$

where the Jacobian entries are:

$$
J_{ii} = \frac{\partial F_i}{\partial n_i} = RT\left(\frac{1}{n_i} - \frac{1}{N} + \frac{\partial \ln\varphi_i}{\partial n_i}\right)
$$

$$
J_{ij} = \frac{\partial F_i}{\partial n_j} = RT\left(-\frac{1}{N} + \frac{\partial \ln\varphi_i}{\partial n_j}\right), \quad i \neq j
$$

The fugacity coefficient derivatives $\partial\ln\varphi_i / \partial n_j$ are **dimensionless** quantities computed by the equation of state (stored in `logfugcoefdN` in the phase model).

**Update with damping:**

$$
n_i^{(m+1)} = n_i^{(m)} + \alpha \cdot \Delta n_i
$$

$$
\lambda_k^{(m+1)} = \lambda_k^{(m)} + \alpha_\lambda \cdot \Delta\lambda_k
$$

where $\alpha \in (0, 1]$ is the composition damping factor (typically 0.01--0.05).

### 2.3.1 LU Decomposition for the Linear System

The Newton step $\Delta\mathbf{x} = -J^{-1}\mathbf{F}$ is computed by solving $J \cdot \Delta\mathbf{x} = -\mathbf{F}$ via LU decomposition rather than explicitly inverting $J$. This is the standard approach recommended by Nocedal & Wright (2000, Ch. 3):

- **Cost:** $O(n^3/3)$ vs $O(n^3)$ for explicit inversion — roughly 3× faster
- **Stability:** avoids amplifying round-off errors through matrix inversion
- **Fallback:** if LU decomposition fails (singular matrix), the solver falls back to Moore-Penrose pseudo-inverse

### 2.3.2 Adaptive Step Sizing (NASA CEA-Style)

When enabled (`setUseAdaptiveStepSize(true)`), the fixed damping factor is replaced with an adaptive step size computed each iteration, following the NASA CEA approach (Gordon & McBride, 1994):

$$
\alpha = \min\left(1,\; \min_{i \in \text{major}} \frac{\ln 5 \cdot n_i}{|\Delta n_i|}\right)
$$

where "major" components are those with $n_i > 0.001 \cdot N$. Minor species (< 0.1% of total) are excluded from the limiting so they can grow rapidly from near-zero. An additional negativity guard ensures $n_i + \alpha \cdot \Delta n_i > 0$.

**References:**
- Nocedal, J. and Wright, S. J. (2006). *Numerical Optimization.* 2nd ed., Springer. — Chapter 3 (LU solve), Chapters 18-19 (equality-constrained optimisation).
- Gordon, S. and McBride, B. J. (1994). *Computer Program for Calculation of Complex Chemical Equilibrium Compositions and Applications.* NASA RP-1311. — Step limiting algorithm.

### 2.4 Log-Mole Column Scaling (Gordon-McBride)

For systems with trace species (e.g., S₈ at ppb alongside CO₂ at 99.9%), the standard Jacobian is ill-conditioned because the diagonal term $RT/n_i$ becomes very large for small $n_i$.

The **log-mole scaling** transforms the Newton variable from $\Delta n_j$ to $\Delta\ln n_j$ by multiplying each Jacobian column $j$ by $n_j$:

$$
J'_{ij} = J_{ij} \cdot n_j
$$

After solving for $\Delta\ln n_j$, compositions are updated as:

$$
n_j^{(m+1)} = n_j^{(m)} + \alpha \cdot n_j^{(m)} \cdot \Delta\ln n_j
$$

This removes the $1/n_i$ singularity from the diagonal, dramatically improving convergence for systems spanning many orders of magnitude in concentration.

**References:**
- Gordon, S. and McBride, B. J. (1994). *Computer Program for Calculation of Complex Chemical Equilibrium Compositions and Applications.* NASA Reference Publication 1311.
- Reynolds, W. C. (1986). *The Element Potential Method for Chemical Equilibrium Analysis.* Stanford University, Dept. Mechanical Engineering.

### 2.5 Adiabatic Mode

In adiabatic mode, the temperature is updated at each iteration to satisfy the enthalpy balance:

$$
H_\text{outlet}(T^{(m+1)}) = H_\text{inlet}
$$

The temperature update is computed from:

$$
\Delta T = \frac{H_\text{inlet} - H_\text{outlet}(T^{(m)})}{\overline{C}_p}
$$

where $\overline{C}_p$ is the mixture heat capacity. The inlet enthalpy is calculated from the Gibbs database polynomial correlations for $H_i(T)$.

---

## 3. GibbsReactor — Detailed API

### 3.1 Construction

```java
// With inlet stream (standard usage)
GibbsReactor reactor = new GibbsReactor("name", inletStream);

// Name only (stream set later)
GibbsReactor reactor = new GibbsReactor("name");
```

### 3.2 Configuration Methods

#### Energy Mode

```java
// Enum-based (preferred)
reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
reactor.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);

// String-based
reactor.setEnergyMode("isothermal");
reactor.setEnergyMode("adiabatic");
```

| Mode | Behaviour |
|------|-----------|
| `ISOTHERMAL` | Temperature fixed at inlet value; reactor reports heat duty |
| `ADIABATIC` | Temperature adjusts to satisfy $H_\text{out} = H_\text{in}$; no external heat |

#### Solver Parameters

| Method | Default | Description |
|--------|---------|-------------|
| `setMaxIterations(int n)` | 5000 | Maximum Newton-Raphson iterations |
| `setConvergenceTolerance(double tol)` | 1e-3 | Convergence criterion: $\lVert\Delta\mathbf{x}\rVert < \text{tol}$ |
| `setDampingComposition(double alpha)` | 0.05 | Step size damping factor $\alpha$ for composition updates |
| `setMinIterations(int n)` | 100 | Minimum iterations before convergence is checked |
| `setUseAdaptiveStepSize(boolean)` | false | Enable NASA CEA-style adaptive step sizing |

**Guidance for choosing $\alpha$:**
- `0.001`–`0.01`: Very conservative, for stiff acid gas systems or near-pure compositions
- `0.01`–`0.05`: Standard range for most applications
- `0.05`–`0.1`: Aggressive, for well-conditioned simple systems (e.g., combustion)

#### Adaptive Step Sizing

When `setUseAdaptiveStepSize(true)` is enabled, the solver automatically computes the step size each iteration using NASA CEA-style step limiting (Gordon & McBride, 1994). Instead of a fixed damping factor, the algorithm:

1. Starts with $\alpha = 1.0$ (full Newton step)
2. Limits $\alpha$ so that no major component (> 0.1% of total moles) changes by more than a factor of $e^{\ln 5} \approx 5$ in a single step
3. Prevents any component from going negative
4. Floors $\alpha$ at $10^{-4}$ to avoid vanishingly small steps

This allows larger steps when safe and smaller steps near steep gradients, typically reducing iteration count significantly for isothermal systems.

#### Minimum Iterations

The solver requires at least `minIterations` iterations before declaring convergence, even if $\lVert\Delta\mathbf{x}\rVert < \text{tol}$. This prevents premature termination in adiabatic mode where the temperature update lags behind composition convergence. The default of 100 is conservative; for simple isothermal cases, `setMinIterations(3)` combined with adaptive step sizing can reduce total iterations from hundreds to tens.

#### Species Management

```java
// Mark a component as non-reactive (excluded from equilibrium)
reactor.setComponentAsInert("nitrogen");
reactor.setComponentAsInert("CO2");

// Include all species from the GibbsReactDatabase (not just those in the inlet)
reactor.setUseAllDatabaseSpecies(true);  // default: false
```

#### Consistent Jacobian (Opt-In)

```java
// Enable the RT-consistent off-diagonal + log-mole scaling + lambda damping
reactor.setUseConsistentOffDiagonal(true);  // default: false

// Query state
boolean isConsistent = reactor.isUseConsistentOffDiagonal();
```

**When to enable:** Use for multi-stage acid gas systems, or any system where trace species (< 1 ppm) coexist with major components. Not needed for simple combustion or ammonia synthesis.

### 3.3 Running the Reactor

```java
reactor.run();  // standard execution

// Or with UUID for tracking
reactor.run(UUID.randomUUID());
```

Internally, `run()` calls `solveGibbsEquilibrium(dampingComposition)` which executes the full Newton-Raphson iteration loop. The linear system $J \cdot \Delta\mathbf{x} = -\mathbf{F}$ is solved using LU decomposition (EJML `solve()`), which is ~3× faster and more numerically stable than explicit matrix inversion.

### 3.4 Results — Convergence Diagnostics

```java
boolean ok = reactor.hasConverged();              // Did Newton-Raphson converge?
int niter = reactor.getActualIterations();         // Iterations performed
double err = reactor.getFinalConvergenceError();   // Final ||Δx|| norm
double mbErr = reactor.getMassBalanceError();       // Mass balance error (%)
boolean mbOk = reactor.getMassBalanceConverged();   // Mass balance < 0.001%?
```

### 3.5 Results — Thermodynamic Quantities

```java
double dH = reactor.getEnthalpyOfReactions();     // Cumulative ΔH (kJ)
double dT = reactor.getTemperatureChange();        // Cumulative ΔT (K, adiabatic mode)
double power = reactor.getPower();                 // Power in W
double powerKW = reactor.getPower("kW");           // Power in kW
double powerMW = reactor.getPower("MW");           // Power in MW
```

### 3.6 Results — Outlet Composition

```java
// Access the outlet stream's thermo system
SystemInterface outlet = reactor.getOutletStream().getThermoSystem();

// Component mole fractions
double z_i = outlet.getComponent("H2S").getz();

// Convert to ppm
double ppm_i = z_i * 1e6;

// Full fluid properties
outlet.initProperties();
double rho = outlet.getDensity("kg/m3");
```

### 3.7 Advanced — Jacobian Inspection

For diagnostic purposes, the Jacobian and its inverse can be inspected:

```java
double[][] J = reactor.getJacobianMatrix();          // Deep copy
double[][] Jinv = reactor.getJacobianInverse();       // Deep copy
List<String> rowLabels = reactor.getJacobianRowLabels();  // e.g., ["F_H2S", "F_SO2", ..., "Balance_S"]
List<String> colLabels = reactor.getJacobianColLabels();  // e.g., ["n_H2S", "n_SO2", ..., "lambda_S"]
boolean valid = reactor.verifyJacobianInverse();       // J * J^-1 ≈ I?
```

### 3.8 Advanced — Objective Function Inspection

```java
// Get the residual vector F (should be ≈0 at equilibrium)
Map<String, Double> fValues = reactor.getObjectiveFunctionValues();

// Get the full minimisation vector [F; b-An] and its labels
double[] minVec = reactor.getObjectiveMinimizationVector();
List<String> minLabels = reactor.getObjectiveMinimizationVectorLabels();
```

### 3.9 Mixture Thermodynamic Properties

```java
double H = reactor.getMixtureEnthalpy();           // H(T) in kJ using Gibbs database
double G = reactor.getMixtureGibbsEnergy();         // G(T) in kJ using Gibbs database
double Hstd = reactor.calculateMixtureEnthalpyStandard(names, moles, componentMap);  // H(298.15K)
```

---

## 4. GibbsReactorCO2 — Acid Gas Wrapper

### 4.1 Purpose

`GibbsReactorCO2` is a convenience wrapper that automatically selects the appropriate reaction pathway for acid gas systems. It is designed for CO₂ transport and storage applications where trace contaminants (H₂S, SO₂, NOₓ, O₂) undergo reactions in the dense CO₂ phase.

### 4.2 Construction

```java
GibbsReactorCO2 reactor = new GibbsReactorCO2("acid gas reactor", inletStream);
reactor.run();
Stream outlet = reactor.getOutletStream();
```

No configuration is needed — the reactor auto-configures based on inlet composition.

### 4.3 Automatic Routing Logic

The reactor inspects inlet composition (in ppm) and selects one of three pathways:

| Condition | Pathway | Description |
|-----------|---------|-------------|
| NO₂ > 0.01 ppm AND H₂S > 0.01 ppm | Single reactor | Both species react together |
| O₂ > 0.01 ppm | Two-stage oxidation | Stage 1: H₂S oxidation; Stage 2: SO₂ processing |
| Otherwise | Single reactor with SO₂ inert | SO₂ is marked non-reactive |

### 4.4 CO₂ Density Guard

Reactions are **skipped entirely** when the CO₂ phase density is below 300 kg/m³. At low densities, the CO₂ is in a gas-like state where bulk-phase reaction kinetics are negligible. The inlet composition is passed through unchanged.

### 4.5 Default Configuration

Each internal `GibbsReactor` is created with:

| Parameter | Value |
|-----------|-------|
| Damping ($\alpha$) | 0.03 |
| Max iterations | 15000 |
| Convergence tolerance | 1e-3 |
| Energy mode | Isothermal |
| Database species | false (inlet species only) |

**Default inert components:** CO, COS, CO₂, ammonia, hydrogen, N₂O₃, nitrogen, N₂H₄, N₂O

The two-stage pathway additionally enables `useConsistentOffDiagonal(true)` on both the H₂S and SO₂ reactors.

### 4.6 Limitations

- Only **bulk (homogeneous) phase reactions** are modelled. Surface reactions, heterogeneous catalysis, and interfacial phenomena are not included.
- The model assumes thermodynamic equilibrium is reached. No kinetic rate expressions or residence time effects.
- The CO₂ density threshold (300 kg/m³) is a heuristic — real reaction rates depend on temperature, catalysis, and contact time.

---

## 5. Gibbs Thermodynamic Database

### 5.1 Data Source

The reactor reads species thermodynamic data from `GibbsReactDatabase.csv` in `src/main/resources/data/`. Each entry provides:

- Gibbs energy of formation $G_f^{\circ}(T)$ via polynomial coefficients
- Enthalpy of formation $H_f^{\circ}(T)$ via polynomial coefficients
- Elemental composition ($a_{ki}$): number of O, N, C, H, S, Ar atoms per molecule

### 5.2 Available Elements

The solver tracks 7 elements: **O, N, C, H, S, Ar, Z** (where Z is a charge balance placeholder for ionic species).

### 5.3 Species Matching

Components in the inlet stream are matched (case-insensitive) to the Gibbs database. Unmatched components are treated as pass-through (moles unchanged). When `setUseAllDatabaseSpecies(true)` is called, all database species are added to the system at trace concentrations (1e-20 mol).

---

## 6. Usage Examples

### 6.1 Methane Combustion (Adiabatic)

```java
SystemInterface system = new SystemSrkEos(298.15, 100.0);
system.addComponent("methane", 0.25);
system.addComponent("oxygen", 1.0);
system.addComponent("nitrogen", 1.0);
system.addComponent("CO2", 0.0);
system.addComponent("water", 0.0);
system.addComponent("CO", 0.0);
system.setMixingRule(2);

Stream inlet = new Stream("Feed", system);
inlet.run();

GibbsReactor reactor = new GibbsReactor("Combustion", inlet);
reactor.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);
reactor.setDampingComposition(0.01);
reactor.setMaxIterations(5000);
reactor.setConvergenceTolerance(1e-3);
reactor.run();

// Adiabatic flame temperature
double T_flame = reactor.getOutletStream().getThermoSystem().getTemperature(); // ~2200 K
```

### 6.2 Ammonia Synthesis (Isothermal, High Pressure)

```java
SystemInterface system = new SystemSrkEos(298.15, 100.0);
system.addComponent("hydrogen", 1.5);
system.addComponent("nitrogen", 0.5);
system.addComponent("ammonia", 0.0);
system.setMixingRule(2);

Stream inlet = new Stream("Feed", system);
inlet.setPressure(300, "bara");
inlet.setTemperature(450, "K");
inlet.run();

GibbsReactor reactor = new GibbsReactor("Haber-Bosch", inlet);
reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
reactor.setDampingComposition(0.05);
reactor.setMaxIterations(2500);
reactor.setConvergenceTolerance(1e-6);
reactor.run();

// At 450 K, 300 bar: ~93% ammonia conversion
double z_NH3 = reactor.getOutletStream().getThermoSystem().getComponent("ammonia").getz();
```

### 6.3 Sulfur Deposition from Sour Gas (Isothermal)

```java
SystemInterface system = new SystemSrkCPAstatoil(298.15, 1.0);
system.addComponent("methane", 1e6);
system.addComponent("H2S", 10.0);
system.addComponent("oxygen", 2.0);
system.addComponent("SO2", 0.0);
system.addComponent("SO3", 0.0);
system.addComponent("sulfuric acid", 0.0);
system.addComponent("water", 0.0);
system.addComponent("S8", 0.0);
system.setMixingRule(2);

Stream inlet = new Stream("Sour Gas", system);
inlet.setPressure(10, "bara");
inlet.setTemperature(100, "C");
inlet.run();

GibbsReactor reactor = new GibbsReactor("Sulfur Formation", inlet);
reactor.setUseAllDatabaseSpecies(false);
reactor.setDampingComposition(0.001);
reactor.setMaxIterations(10000);
reactor.setConvergenceTolerance(1e-3);
reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
reactor.run();

// Check S8 formation
double ppm_S8 = reactor.getOutletStream().getThermoSystem().getComponent("S8").getz() * 1e6;
```

### 6.4 Acid Gas Equilibrium in CO₂ Transport (GibbsReactorCO2)

```java
SystemSrkEos system = new SystemSrkEos(275.15, 100.0);
system.addComponent("CO2", 1e6, "mole/sec");
system.addComponent("water", 50.0, "mole/sec");
system.addComponent("H2S", 10.0, "mole/sec");
system.addComponent("oxygen", 30.0, "mole/sec");
system.addComponent("NO2", 0.0, "mole/sec");
system.addComponent("SO2", 0.0, "mole/sec");
system.addComponent("S8", 0.0, "mole/sec");
// ... add other potential products at 0.0
system.setMixingRule(2);

Stream inlet = new Stream("CO2 Pipeline", system);
inlet.run();

GibbsReactorCO2 reactor = new GibbsReactorCO2("Acid Gas Equilibrium", inlet);
reactor.run();

// Outlet composition — H2S consumed, SO2 and water formed
SystemInterface outlet = reactor.getOutletStream().getThermoSystem();
double h2s_ppm = outlet.getComponent("H2S").getz() * 1e6;
double so2_ppm = outlet.getComponent("SO2").getz() * 1e6;
```

---

## 7. Troubleshooting

### Non-Convergence

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Oscillating residuals, never converges | Damping too large | Reduce `setDampingComposition()` (try 0.001) |
| Very slow convergence (> 10000 iter) | Damping too small or stiff system | Enable `setUseAdaptiveStepSize(true)` with `setMinIterations(3)` for isothermal, or increase damping and enable `setUseConsistentOffDiagonal(true)` |
| `null` outlet from `GibbsReactorCO2` | Two-stage pathway diverged | Already fixed with consistent Jacobian; check that inlet has expected components |
| Mass balance error > 0.1% | Premature convergence | Decrease `setConvergenceTolerance()` (try 1e-6) |
| Converges but takes 100+ iterations for simple system | `minIterations` too high | Set `setMinIterations(3)` + `setUseAdaptiveStepSize(true)` |

### Zero or Unexpected Moles

- Ensure product species are added to the fluid at **0.0 moles** before running — the solver can only produce species that exist in the system.
- Check that species are in the Gibbs database (`GibbsReactDatabase.csv`). Unrecognised species pass through unchanged.

### Phase Issues

- The Gibbs reactor operates on a single gas phase. For systems that might form liquids or solids, run a flash calculation on the outlet stream.
- For solid sulfur (S₈) precipitation, use `ThermodynamicOperations.TPSolidflash()` on the outlet.

---

## 8. Literature References

1. White, W. B., Johnson, S. M., and Dantzig, G. B. (1958). *Chemical Equilibrium in Complex Mixtures.* J. Chem. Phys. **28**(5), 751–755.
2. Smith, W. R. and Missen, R. W. (1982). *Chemical Reaction Equilibrium Analysis: Theory and Algorithms.* Wiley.
3. Gordon, S. and McBride, B. J. (1994). *Computer Program for Calculation of Complex Chemical Equilibrium Compositions and Applications.* NASA Reference Publication 1311.
4. Reynolds, W. C. (1986). *The Element Potential Method for Chemical Equilibrium Analysis.* Stanford University.
5. Nocedal, J. and Wright, S. J. (2006). *Numerical Optimization.* 2nd ed., Springer.
6. Eriksson, G. (1971). *Thermodynamic Studies of High Temperature Equilibria. III. SOLGAS, a computer program for calculating the composition and heat condition of an equilibrium mixture.* Acta Chem. Scand. **25**, 2651–2658.
7. Koukkari, P. and Pajarre, R. (2006). *Calculation of constrained equilibria by Gibbs energy minimization.* Calphad **30**(1), 18–26.
