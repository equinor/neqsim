# Numerical Implementation

<!-- Chapter metadata -->
<!-- Notebooks: 01_solver_comparison.ipynb, 02_convergence_analysis.ipynb, 03_performance_benchmarks.ipynb -->
<!-- Estimated pages: 22 -->

## Learning Objectives

After reading this chapter, the reader will be able to:

1. Implement the successive substitution method for solving the CPA site balance
2. Describe the fully implicit Newton approach and its advantages
3. Apply Broyden's quasi-Newton method for CPA calculations
4. Use Anderson acceleration to speed up convergence
5. Analyze convergence properties and diagnose numerical difficulties
6. Select the appropriate solver variant in NeqSim for a given application

## 8.1 Overview of the Numerical Challenge

### 8.1.1 The Nested Iteration Problem

CPA introduces a fundamental computational challenge that does not exist for classical cubic EoS: the site fractions $X_A$ are implicit functions of temperature, density, and composition. In a flash calculation, the overall algorithm has a nested structure:

**Outer loop (Flash)**: Update phase compositions ($x_i$, $y_i$) and phase fraction ($\beta$)
  **Inner loop (Fugacity)**: For each phase, solve the site balance equations to get $X_A$, then compute fugacity coefficients

At each outer iteration, the inner loop must converge to provide consistent fugacity coefficients. This nested iteration can be expensive: if the flash requires 10 outer iterations and each fugacity evaluation requires 5–10 inner iterations to solve the site balance, the total number of EoS evaluations is 50–100, compared to 10 for a non-associating system.

### 8.1.2 Strategies for Reducing Computational Cost

Three broad strategies exist to reduce the computational cost of CPA calculations:

1. **Efficient inner loop**: Minimize the number of iterations needed to solve the site balance equations (successive substitution, Newton, analytical solutions)
2. **Fully implicit formulation**: Eliminate the inner loop by solving the site balance equations simultaneously with the flash equations
3. **Acceleration techniques**: Apply convergence accelerators (Anderson mixing, DIIS) to either the inner or outer loop

NeqSim implements all three strategies through different system classes, allowing the user to choose the best approach for their application.

## 8.2 Successive Substitution for the Site Balance

### 8.2.1 The Basic Algorithm

The simplest approach to solve the site balance equations is successive substitution (SS). Starting from an initial guess $X_A^{(0)} = 1$ (no association), the equations are iterated:

$$X_{A_i}^{(k+1)} = \frac{1}{1 + \rho \sum_j x_j \sum_{B_j} X_{B_j}^{(k)} \Delta^{A_i B_j}}$$

This is guaranteed to converge because the mapping is a contraction — it can be shown that:

$$\left|\frac{\partial X_{A_i}^{(k+1)}}{\partial X_{B_j}^{(k)}}\right| < 1$$

for all physical conditions. However, convergence can be slow, particularly at:

- **Low temperatures**: where association is strong ($\Delta$ is large) and $X_A$ values are small
- **High densities**: where the product $\rho\Delta$ is large
- **Near critical points**: where multiple competing phases create sensitivity

### 8.2.2 Convergence Rate

The successive substitution method converges linearly, with the rate determined by the spectral radius of the Jacobian:

$$\|X^{(k+1)} - X^*\| \leq \sigma \|X^{(k)} - X^*\|$$

where $\sigma < 1$ is the spectral radius. For typical conditions, $\sigma \approx 0.3$–$0.7$, meaning convergence in 5–15 iterations to a tolerance of $10^{-10}$. Near the critical point or at very low temperatures, $\sigma$ can approach 1, requiring 50 or more iterations.

### 8.2.3 Damping and Acceleration

Simple modifications can improve the convergence of successive substitution:

**Under-relaxation** (damping):

$$X_{A_i}^{(k+1)} = \omega \cdot X_{A_i}^{\text{new}} + (1 - \omega) \cdot X_{A_i}^{(k)}$$

where $\omega \in (0, 1]$ is the damping factor. This can prevent oscillations but slows convergence.

**Wegstein acceleration**: Estimates the contraction factor from two successive iterates and applies a Wegstein-type update:

$$X^{(k+1)} = X^{(k)} + \frac{g^{(k)} - X^{(k)}}{1 - q^{(k)}}$$

where $q^{(k)}$ estimates the slope of the iteration map.

## 8.3 Newton's Method for the Site Balance

### 8.3.1 Formulation

Newton's method solves the site balance equations by linearizing the residual:

$$R_{A_i}(\mathbf{X}) = X_{A_i} - \frac{1}{1 + \rho \sum_j x_j \sum_{B_j} X_{B_j} \Delta^{A_i B_j}} = 0$$

The Newton update is:

$$\mathbf{X}^{(k+1)} = \mathbf{X}^{(k)} - \mathbf{J}^{-1} \mathbf{R}(\mathbf{X}^{(k)})$$

where the Jacobian matrix elements are:

$$J_{A_i, B_j} = \frac{\partial R_{A_i}}{\partial X_{B_j}} = \delta_{A_i B_j} - \frac{\rho x_j \Delta^{A_i B_j}}{\left(1 + \rho \sum_k x_k \sum_{C_k} X_{C_k} \Delta^{A_i C_k}\right)^2}$$

### 8.3.2 Convergence Properties

Newton's method converges quadratically near the solution:

$$\|X^{(k+1)} - X^*\| \leq C \|X^{(k)} - X^*\|^2$$

This means that once the iterates are close to the solution, convergence is extremely rapid — typically 2–4 iterations suffice.

### 8.3.3 Cost Analysis

For a mixture with $N_s$ total association sites, each Newton step requires:

- Forming the $N_s \times N_s$ Jacobian: $O(N_s^2)$ operations
- Solving the linear system: $O(N_s^3)$ operations

For typical mixtures (2–5 associating components, 4–20 total sites), $N_s$ is small and the linear algebra cost is negligible compared to the EoS evaluation.

## 8.4 The Fully Implicit Approach

### 8.4.1 Motivation

The standard approach treats the site fractions as an inner loop within the fugacity evaluation. The fully implicit approach eliminates this nested structure by treating $X_A$ as additional unknowns in the flash problem.

Consider a TP flash for a two-phase system with $c$ components and $N_s$ association sites per phase. The standard approach solves:

- **Flash equations**: $c$ equations for compositions and vapor fraction → $c$ unknowns
- **Site balance** (per phase): $N_s$ equations → solved as inner loop

The fully implicit approach solves all equations simultaneously:

- **Flash equations**: $c$ equations for compositions and vapor fraction
- **Site balance (vapor)**: $N_s$ equations for $X_A^V$
- **Site balance (liquid)**: $N_s$ equations for $X_A^L$

Total: $c + 2N_s$ equations and unknowns, solved by Newton's method as a single system.

### 8.4.2 The Augmented Jacobian

The Jacobian of the fully implicit system has a block structure:

$$\mathbf{J} = \begin{pmatrix} \frac{\partial \mathbf{F}^{\text{flash}}}{\partial \mathbf{x}} & \frac{\partial \mathbf{F}^{\text{flash}}}{\partial \mathbf{X}^V} & \frac{\partial \mathbf{F}^{\text{flash}}}{\partial \mathbf{X}^L} \\ \frac{\partial \mathbf{R}^V}{\partial \mathbf{x}} & \frac{\partial \mathbf{R}^V}{\partial \mathbf{X}^V} & 0 \\ \frac{\partial \mathbf{R}^L}{\partial \mathbf{x}} & 0 & \frac{\partial \mathbf{R}^L}{\partial \mathbf{X}^L} \end{pmatrix}$$

The zero blocks arise because the site balance in one phase does not directly depend on the site fractions in the other phase (they are coupled only through the composition variables).

### 8.4.3 Advantages of the Fully Implicit Approach

1. **No inner loop**: eliminates the nested iteration, reducing the risk of convergence failure
2. **Quadratic convergence** for the entire system, not just the site balance
3. **Better behavior near critical points**: the coupling between association and phase equilibrium is resolved simultaneously
4. **Consistent derivatives**: the Jacobian includes all cross-coupling terms

### 8.4.4 NeqSim Implementation

NeqSim implements the fully implicit approach in `SystemSrkCPAstatoilFullyImplicit`:

```python
from neqsim import jneqsim

# Standard CPA (nested iteration)
fluid_std = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, 50.0)
fluid_std.addComponent("water", 0.1)
fluid_std.addComponent("methane", 0.9)
fluid_std.setMixingRule(10)

# Fully implicit CPA (simultaneous solution)
fluid_impl = jneqsim.thermo.system.SystemSrkCPAstatoilFullyImplicit(298.15, 50.0)
fluid_impl.addComponent("water", 0.1)
fluid_impl.addComponent("methane", 0.9)
fluid_impl.setMixingRule(10)

# Both should give the same answer
for fluid, name in [(fluid_std, "Standard"), (fluid_impl, "Fully Implicit")]:
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()
    rho = fluid.getDensity("kg/m3")
    print(f"{name}: density = {rho:.2f} kg/m3")
```

## 8.5 Broyden's Quasi-Newton Method

### 8.5.1 Motivation

Newton's method requires computing and factoring the Jacobian at each step, which involves evaluating many partial derivatives. Broyden's method approximates the Jacobian using information from previous iterations, avoiding the need for explicit derivative computation.

### 8.5.2 The Broyden Update

Starting from an initial Jacobian estimate $\mathbf{B}^{(0)}$ (typically the identity matrix or an approximate Jacobian), the Broyden update is:

$$\mathbf{B}^{(k+1)} = \mathbf{B}^{(k)} + \frac{(\Delta \mathbf{R}^{(k)} - \mathbf{B}^{(k)} \Delta \mathbf{X}^{(k)}) (\Delta \mathbf{X}^{(k)})^T}{(\Delta \mathbf{X}^{(k)})^T \Delta \mathbf{X}^{(k)}}$$

where:

$$\Delta \mathbf{X}^{(k)} = \mathbf{X}^{(k+1)} - \mathbf{X}^{(k)}, \quad \Delta \mathbf{R}^{(k)} = \mathbf{R}^{(k+1)} - \mathbf{R}^{(k)}$$

This rank-1 update preserves the secant condition: $\mathbf{B}^{(k+1)} \Delta \mathbf{X}^{(k)} = \Delta \mathbf{R}^{(k)}$.

### 8.5.3 Convergence Properties

Broyden's method converges superlinearly:

$$\|X^{(k+1)} - X^*\| \leq C_k \|X^{(k)} - X^*\|$$

where $C_k \to 0$ as $k \to \infty$. This is faster than linear (successive substitution) but slower than quadratic (Newton). In practice, Broyden's method typically converges in 4–8 iterations, comparable to Newton but without the cost of explicit Jacobian computation.

### 8.5.4 NeqSim Implementation

```python
from neqsim import jneqsim

# Broyden solver variant
fluid = jneqsim.thermo.system.SystemSrkCPAstatoilBroydenImplicit(298.15, 100.0)
fluid.addComponent("water", 0.05)
fluid.addComponent("CO2", 0.3)
fluid.addComponent("methane", 0.65)
fluid.setMixingRule(10)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Density: {fluid.getDensity('kg/m3'):.2f} kg/m3")
```

## 8.6 Anderson Acceleration

### 8.6.1 The Anderson Mixing Algorithm

Anderson acceleration (also called Anderson mixing or DIIS — Direct Inversion in the Iterative Subspace) is a convergence accelerator that can dramatically speed up fixed-point iterations. Given a sequence of iterates from successive substitution, Anderson acceleration constructs improved estimates by mixing previous iterates.

The algorithm maintains a history of $m$ previous iterates and residuals:

$$\mathbf{X}^{(k+1)} = \sum_{j=0}^{m_k} \alpha_j^{(k)} \tilde{\mathbf{X}}^{(k-m_k+j)}$$

where $\tilde{\mathbf{X}}$ are the SS-updated values and the mixing coefficients $\alpha_j$ are chosen to minimize the residual in a least-squares sense:

$$\min_{\alpha} \left\| \sum_{j=0}^{m_k} \alpha_j \mathbf{R}^{(k-m_k+j)} \right\|^2 \quad \text{subject to} \quad \sum_j \alpha_j = 1$$

### 8.6.2 History Depth and Regularization

The parameter $m$ controls how many previous iterates are used:

- $m = 0$: reduces to standard successive substitution
- $m = 1$: equivalent to Wegstein's method
- $m = 3$–$5$: good balance of acceleration and stability
- $m > 10$: can become unstable due to near-linear dependence of residuals

For numerical stability, regularization is recommended:

$$\min_{\alpha} \left\| \sum_j \alpha_j \mathbf{R}^{(j)} \right\|^2 + \lambda \|\alpha\|^2$$

### 8.6.3 Performance Comparison

A typical benchmark for CPA solvers involves computing the phase equilibrium of a water–methane–CO$_2$ system at conditions near a phase boundary. Representative iteration counts are:

| Method | Iterations (inner) | Iterations (outer) | Total EoS evaluations |
|--------|-------------------|--------------------|-----------------------|
| Successive Substitution | 8–12 | 8–15 | 80–180 |
| Newton (inner) | 2–3 | 8–15 | 20–50 |
| Fully Implicit Newton | — | 4–8 | 4–8 |
| Broyden Implicit | — | 5–10 | 5–10 |
| Anderson Acceleration ($m=3$) | 3–5 | 5–10 | 15–50 |

*Table 8.1: Typical iteration counts for different CPA solver strategies.*

### 8.6.4 NeqSim Implementation

```python
from neqsim import jneqsim

# Anderson acceleration solver
fluid = jneqsim.thermo.system.SystemSrkCPAstatoilAndersonMixing(298.15, 100.0)
fluid.addComponent("water", 0.1)
fluid.addComponent("methane", 0.9)
fluid.setMixingRule(10)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Density: {fluid.getDensity('kg/m3'):.2f} kg/m3")
```

## 8.7 Reduced-Variable Formulation

### 8.7.1 Motivation

Working with volume ($V$) as the primary variable (rather than compressibility factor $Z$) can improve numerical stability for CPA. The "reduced-variable" formulation expresses the Helmholtz energy in terms of dimensionless quantities:

$$\delta = \frac{\rho}{\rho_c} \quad \text{(reduced density)}, \quad \tau = \frac{T_c}{T} \quad \text{(inverse reduced temperature)}$$

### 8.7.2 Advantages

The reduced-variable formulation:

1. Improves scaling of the Jacobian matrix (all variables are order unity)
2. Separates temperature and density dependence more clearly
3. Simplifies the computation of second derivatives for caloric properties
4. Reduces round-off errors in the association term at extreme conditions

NeqSim's advanced CPA solvers use a variant of this approach internally.

## 8.8 Convergence Diagnostics and Troubleshooting

### 8.8.1 Common Convergence Issues

CPA calculations can fail to converge for several reasons:

1. **Near-critical conditions**: The flash objective function becomes very flat, making it hard to determine the correct number of phases
2. **Very dilute solutions**: When $x_i < 10^{-8}$, numerical precision becomes an issue
3. **Strong association at low temperature**: The site fractions approach zero, creating stiffness
4. **Incompatible initial estimates**: Starting the flash from an inappropriate K-factor estimate

### 8.8.2 Diagnostic Indicators

Useful diagnostics include:

- **Residual history**: Plot $\|\mathbf{R}\|$ vs. iteration count — monotonic decrease indicates healthy convergence
- **Spectral radius**: Estimate $\sigma$ from successive iterates — values near 1 indicate slow convergence
- **Site fraction bounds**: $X_A$ should always be in $(0, 1]$ — values outside this range indicate a bug
- **Material balance**: Total composition should be conserved — errors > $10^{-8}$ indicate a problem

### 8.8.3 Recovery Strategies

When convergence fails, NeqSim employs automatic recovery strategies:

1. **Reduce step size**: Apply a line search to the Newton step
2. **Restart with SS**: Fall back to successive substitution to get a better initial estimate
3. **Try different initialization**: Use Wilson K-factors or stability analysis results
4. **Switch solver**: Try Broyden or Anderson if Newton fails

```python
from neqsim import jneqsim

# For difficult cases, the fully implicit solver often converges
# when the standard solver fails
fluid = jneqsim.thermo.system.SystemSrkCPAstatoilFullyImplicit(280.0, 200.0)
fluid.addComponent("water", 0.001)
fluid.addComponent("methane", 0.95)
fluid.addComponent("CO2", 0.049)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Number of phases: {fluid.getNumberOfPhases()}")
print(f"Gas density: {fluid.getPhase('gas').getDensity('kg/m3'):.2f} kg/m3")
```

## 8.9 Performance Optimization

### 8.9.1 Analytical vs. Numerical Derivatives

A critical performance factor is the use of analytical derivatives rather than finite differences. For a system with $c$ components and $N_s$ sites:

- **Analytical derivatives**: one evaluation of $\partial \varphi_i / \partial n_j$ costs $O(cN_s)$
- **Numerical derivatives**: requires $2c$ additional function evaluations, each costing $O(N_s)$ for the inner loop

The speedup from analytical derivatives is typically 3–10x for mixtures with 5–10 components.

### 8.9.2 Exploiting Symmetry

Many association schemes have symmetric sites (e.g., the two proton sites on water are identical). Exploiting this symmetry:

- Reduces the number of independent $X_A$ values: instead of 4 equations for water, solve 2
- Reduces the Jacobian size: halves the number of rows and columns
- Provides analytical solutions for pure components (see Chapter 4)

### 8.9.3 Caching and Reuse

In flash calculations, the fugacity coefficients are evaluated many times at similar conditions. Performance can be improved by:

- **Caching association strengths** $\Delta^{AB}$: these depend only on $T$, $\rho$, and the pure-component parameters, not on composition
- **Reusing site fractions** from the previous iteration as initial guesses
- **Storing intermediate quantities**: $g(\rho)$, $\exp(\varepsilon/RT)$, etc.

## 8.10 Comparison of Solver Variants in NeqSim

### 8.10.1 When to Use Each Solver

| Solver | NeqSim Class | Best For | Limitations |
|--------|-------------|----------|-------------|
| Standard SS | `SystemSrkCPAstatoil` | Simple systems, robust | Slow for complex mixtures |
| Fully Implicit | `SystemSrkCPAstatoilFullyImplicit` | Near-critical, difficult VLE | Highest per-iteration cost |
| Broyden | `SystemSrkCPAstatoilBroydenImplicit` | General purpose, good balance | May need restart if poorly initialized |
| Anderson | `SystemSrkCPAstatoilAndersonMixing` | Weakly convergent systems | Requires tuning of history depth |

*Table 8.2: CPA solver variants in NeqSim and their recommended use cases.*

### 8.10.2 Practical Recommendations

For most industrial applications:

1. **Start with `SystemSrkCPAstatoil`** — it is the most robust and well-tested
2. **Switch to `SystemSrkCPAstatoilFullyImplicit`** if convergence is slow or fails near critical points
3. **Use `SystemSrkCPAstatoilBroydenImplicit`** for large-scale process simulations where speed matters
4. **Anderson mixing** is experimental and recommended only for specialized applications

## Summary

Key points from this chapter:

- CPA introduces a nested iteration structure: site balance inside flash
- Successive substitution is simple and robust but can be slow
- Newton's method provides quadratic convergence for the site balance
- The fully implicit approach eliminates the inner loop, solving everything simultaneously
- Broyden's method avoids explicit Jacobian computation while maintaining superlinear convergence
- Anderson acceleration can dramatically speed up fixed-point iterations
- NeqSim provides four solver variants to match different application requirements
- Analytical derivatives are essential for performance
- Recovery strategies handle convergence failures automatically

## Exercises

1. **Exercise 8.1:** Implement successive substitution for the site balance of a pure 4C water system at 25°C and 1 bar. Plot the residual $\|R\|$ vs. iteration count and determine the convergence rate.

2. **Exercise 8.2:** Using NeqSim, compare the computation time for a 10-component CPA flash using all four solver variants. Use a mixture of water, methanol, CO$_2$, and seven alkanes (C1–C7) at various pressures.

3. **Exercise 8.3:** Investigate the convergence behavior near the upper critical end point of the water–methane system (approximately 360°C, 700 bar). Which solver variant is most robust?

4. **Exercise 8.4:** Implement a simple Anderson acceleration ($m = 2$) wrapper around the successive substitution iterations for the site balance. Compare convergence with plain SS.

## References

<!-- Chapter-level references are merged into master refs.bib -->


## Figures

![Figure 8.1: 01 Convergence Xa](figures/fig_ch08_01_convergence_XA.png)

*Figure 8.1: 01 Convergence Xa*

![Figure 8.2: 02 Benchmark Implicit Cpa](figures/fig_ch08_02_benchmark_implicit_cpa.png)

*Figure 8.2: 02 Benchmark Implicit Cpa*

![Figure 8.3: 03 Speedup Heatmap](figures/fig_ch08_03_speedup_heatmap.png)

*Figure 8.3: 03 Speedup Heatmap*

![Figure 8.4: 04 Multi System Benchmark](figures/fig_ch08_04_multi_system_benchmark.png)

*Figure 8.4: 04 Multi System Benchmark*
