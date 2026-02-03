---
title: "Pipeline Flow Simulation: Governing Equations and Numerical Methods"
description: "This document provides a comprehensive reference for single-phase pipeline flow simulation in NeqSim, including the governing equations, discretization schemes, and numerical solution methods."
---

# Pipeline Flow Simulation: Governing Equations and Numerical Methods

This document provides a comprehensive reference for single-phase pipeline flow simulation in NeqSim, including the governing equations, discretization schemes, and numerical solution methods.

## Table of Contents

1. [Governing Equations](#governing-equations)
2. [Steady-State Solution](#steady-state-solution)
3. [Transient Solution](#transient-solution)
4. [Compositional Tracking](#compositional-tracking)
5. [Advection Schemes and Numerical Dispersion](#advection-schemes-and-numerical-dispersion)
6. [Boundary Conditions](#boundary-conditions)
7. [Solution Algorithm](#solution-algorithm)

---

## Governing Equations

### Conservation of Mass (Continuity)

The one-dimensional continuity equation for compressible flow in a pipe:

$$
\frac{\partial \rho}{\partial t} + \frac{\partial (\rho v)}{\partial x} = 0
$$

Where:
- $\rho$ = fluid density (kg/m³)
- $v$ = flow velocity (m/s)
- $t$ = time (s)
- $x$ = axial position along pipe (m)

### Conservation of Momentum

The momentum equation including friction and gravity:

$$
\frac{\partial (\rho v)}{\partial t} + \frac{\partial (\rho v^2)}{\partial x} = -\frac{\partial P}{\partial x} - \frac{f \rho v |v|}{2D} - \rho g \sin\theta
$$

Where:
- $P$ = pressure (Pa)
- $f$ = Darcy friction factor (dimensionless)
- $D$ = pipe inner diameter (m)
- $g$ = gravitational acceleration (9.81 m/s²)
- $\theta$ = pipe inclination angle from horizontal

### Conservation of Energy

The energy equation with heat transfer:

$$
\frac{\partial (\rho e)}{\partial t} + \frac{\partial (\rho v h)}{\partial x} = \frac{q_{wall}}{\pi D^2 / 4}
$$

Where:
- $e$ = specific internal energy (J/kg)
- $h$ = specific enthalpy (J/kg)
- $q_{wall}$ = heat transfer rate per unit length (W/m)

### Species Conservation (Compositional Tracking)

For each component $i$:

$$
\frac{\partial (\rho w_i)}{\partial t} + \frac{\partial (\rho v w_i)}{\partial x} = 0
$$

Where:
- $w_i$ = mass fraction of component $i$

---

## Steady-State Solution

### Pressure Drop Calculation

For steady-state flow, the momentum equation simplifies to:

$$
\frac{dP}{dx} = -\frac{f \rho v^2}{2D} - \rho g \sin\theta
$$

The Darcy friction factor $f$ is calculated using the Colebrook-White equation:

$$
\frac{1}{\sqrt{f}} = -2 \log_{10}\left(\frac{\varepsilon/D}{3.7} + \frac{2.51}{Re \sqrt{f}}\right)
$$

Where:
- $\varepsilon$ = pipe wall roughness (m)
- $Re$ = Reynolds number = $\rho v D / \mu$

### Discretization

The pipe is divided into $N$ nodes. For node $i$:

$$
P_{i+1} = P_i - \Delta x \left(\frac{f_i \rho_i v_i^2}{2D} + \rho_i g \sin\theta_i\right)
$$

---

## Transient Solution

### Finite Volume Method

NeqSim uses a staggered grid finite volume method. The pipe is divided into control volumes with:
- Scalar quantities (P, T, ρ, composition) at cell centers
- Velocities at cell faces

### Time Discretization

Implicit (backward) Euler scheme for stability:

$$
\frac{\phi^{n+1} - \phi^n}{\Delta t} + \frac{\partial F}{\partial x}\bigg|^{n+1} = S^{n+1}
$$

Where:
- $\phi$ = conserved variable
- $F$ = flux
- $S$ = source term
- $n$ = time step index

### CFL Condition

For numerical stability, the Courant-Friedrichs-Lewy (CFL) number should satisfy:

$$
CFL = \frac{(v + c) \Delta t}{\Delta x} \leq 1
$$

Where $c$ is the speed of sound in the fluid.

### TDMA Solver

The discretized equations form a tri-diagonal matrix system:

$$
a_i \phi_{i-1} + b_i \phi_i + c_i \phi_{i+1} = r_i
$$

Solved using the Thomas algorithm (TDMA).

---

## Compositional Tracking

### Conservation Equation

The mass fraction transport equation in conservative form:

$$
\frac{\partial (\rho A w)}{\partial t} + \frac{\partial (\dot{m} w)}{\partial x} = 0
$$

Where:
- $A$ = pipe cross-sectional area (m²)
- $\dot{m} = \rho v A$ = mass flow rate (kg/s)

### Discretized Form

For control volume $i$:

$$
\frac{(\rho A w)_i^{n+1} - (\rho A w)_i^{n}}{\Delta t} + \frac{F_e - F_w}{\Delta x} = 0
$$

Where $F_e$ and $F_w$ are the convective fluxes at east and west faces.

### First-Order Upwind Scheme

The convective flux at a face is:

$$
F_e = \max(\dot{m}_e, 0) w_i + \min(\dot{m}_e, 0) w_{i+1}
$$

This leads to the coefficient matrix:
- $a_i = \max(F_w, 0)$
- $c_i = \max(-F_e, 0)$
- $b_i = a_i + c_i + (F_e - F_w) + \frac{\rho_i A_i \Delta x}{\Delta t}$

---

## Advection Schemes and Numerical Dispersion

### The Numerical Dispersion Problem

First-order upwind introduces artificial (numerical) diffusion:

$$
D_{num} = \frac{v \Delta x}{2} (1 - CFL)
$$

This causes composition fronts to spread over distance:

$$
\sigma = \sqrt{2 D_{num} t} = \sqrt{\Delta x \cdot L \cdot (1 - CFL)}
$$

Where $L$ is the transport distance.

### Available Advection Schemes

| Scheme | Order | Numerical Dispersion | Stability |
|--------|-------|---------------------|-----------|
| First-Order Upwind | 1 | High | Unconditional |
| Second-Order Upwind | 2 | Low | CFL ≤ 0.5 |
| QUICK | 3 | Very Low | CFL ≤ 0.5 |
| TVD Van Leer | 2 | Low | CFL ≤ 1.0 |
| TVD Minmod | 2 | Medium | CFL ≤ 1.0 |
| TVD Superbee | 2 | Very Low | CFL ≤ 1.0 |
| TVD Van Albada | 2 | Low | CFL ≤ 1.0 |
| MUSCL Van Leer | 2 | Low | CFL ≤ 1.0 |

### TVD (Total Variation Diminishing) Schemes

TVD schemes use flux limiters to achieve high accuracy in smooth regions while preventing oscillations near discontinuities.

The flux limiter $\psi(r)$ depends on the gradient ratio:

$$
r = \frac{\phi_i - \phi_{i-1}}{\phi_{i+1} - \phi_i}
$$

#### Limiter Functions

**Minmod** (most diffusive):
$$
\psi(r) = \max(0, \min(r, 1))
$$

**Van Leer** (recommended):
$$
\psi(r) = \frac{r + |r|}{1 + |r|}
$$

**Superbee** (least diffusive):
$$
\psi(r) = \max(0, \min(2r, 1), \min(r, 2))
$$

**Van Albada** (smooth):
$$
\psi(r) = \frac{r^2 + r}{r^2 + 1}
$$

### TVD Flux Correction

The higher-order flux correction is:

$$
F_{HO} = F_{LO} + \frac{1}{2} \psi(r) |F| (1 - |CFL|) (\phi_{downstream} - \phi_{upstream})
$$

Where $F_{LO}$ is the first-order upwind flux.

### Dispersion Reduction

The effective numerical diffusion with TVD schemes:

$$
D_{eff} = D_{num} \times \text{ReductionFactor}
$$

Typical reduction factors:
- Van Leer: 0.15 (7× reduction)
- Superbee: 0.08 (12× reduction)
- Minmod: 0.30 (3× reduction)

---

## Boundary Conditions

### Inlet Boundary

Fixed conditions from upstream:
- Pressure: $P_{inlet}$
- Temperature: $T_{inlet}$
- Composition: $w_{i,inlet}$
- Flow rate: $\dot{m}_{inlet}$

### Outlet Boundary

Typically one of:
- Fixed pressure: $P_{outlet}$
- Fixed flow rate: $\dot{m}_{outlet}$
- Reservoir (pressure-flow relationship)

### Implementation

```java
// Inlet: Dirichlet condition
a[0] = 0;
b[0] = 1;
c[0] = 0;
r[0] = w_inlet;

// Interior nodes: discretized conservation equation
// ... (TDMA coefficients)

// Outlet: extrapolation or fixed value
```

---

## Solution Algorithm

### Steady-State Algorithm

1. Initialize with linear pressure profile
2. Calculate fluid properties at each node
3. Calculate friction factors
4. Solve momentum equation for pressure
5. Update velocities
6. Repeat until convergence

### Transient Algorithm

```
for each time step:
    1. Apply inlet boundary conditions
    2. Calculate time step (CFL condition)
    3. Assemble coefficient matrices
    4. Solve TDMA for each conservation equation:
       - Momentum (pressure/velocity)
       - Energy (temperature)
       - Species (composition)
    5. Update fluid properties (EOS flash)
    6. Update outlet stream
    7. Store results
```

### Code Example

```java
// Create pipeline
OnePhasePipeLine pipe = new OnePhasePipeLine("GasPipe", inletStream);
pipe.setNumberOfLegs(1);
pipe.setNumberOfNodesInLeg(100);
pipe.setPipeDiameters(new double[] {0.3, 0.3});
pipe.setLegPositions(new double[] {0.0, 5000.0});

// Enable compositional tracking with TVD scheme
pipe.setCompositionalTracking(true);
pipe.setAdvectionScheme(AdvectionScheme.TVD_VAN_LEER);

// Run steady-state initialization
pipe.run();

// Run transient simulation
UUID id = UUID.randomUUID();
for (int step = 0; step < 100; step++) {
    pipe.runTransient(1.0, id);  // 1 second time step
}
```

---

## References

1. Patankar, S.V. (1980). *Numerical Heat Transfer and Fluid Flow*. Taylor & Francis.
2. Versteeg, H.K. & Malalasekera, W. (2007). *An Introduction to Computational Fluid Dynamics*. Pearson.
3. LeVeque, R.J. (2002). *Finite Volume Methods for Hyperbolic Problems*. Cambridge University Press.
4. Sweby, P.K. (1984). "High Resolution Schemes Using Flux Limiters for Hyperbolic Conservation Laws". *SIAM J. Numer. Anal.* 21(5): 995-1011.
