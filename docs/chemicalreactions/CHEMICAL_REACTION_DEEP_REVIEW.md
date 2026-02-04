---
title: "Deep Review: Chemical Reaction Initialization and Solving in NeqSim"
description: "This document provides a comprehensive deep-dive into how NeqSim initializes, sets up, and solves chemical reactions during thermodynamic calculations, with particular focus on integration with TP flash operations."
---

# Deep Review: Chemical Reaction Initialization and Solving in NeqSim

This document provides a comprehensive deep-dive into how NeqSim initializes, sets up, and solves chemical reactions during thermodynamic calculations, with particular focus on integration with TP flash operations.

## Table of Contents

1. [Overview](#1-overview)
2. [Chemical Reaction Initialization](#2-chemical-reaction-initialization)
3. [Reaction Matrix Setup](#3-reaction-matrix-setup)
4. [Stoichiometry Matrix (A-matrix) Construction](#4-stoichiometry-matrix-a-matrix-construction)
5. [Reference Potential Calculation](#5-reference-potential-calculation)
6. [Initial Estimate Generation (Linear Programming)](#6-initial-estimate-generation-linear-programming)
7. [Newton Solver for Chemical Equilibrium](#7-newton-solver-for-chemical-equilibrium)
8. [Integration with TP Flash](#8-integration-with-tp-flash)
9. [Mathematical Formulation](#9-mathematical-formulation)
10. [Class Diagram and Flow](#10-class-diagram-and-flow)
11. [Key Methods Summary](#11-key-methods-summary)
12. [Numerical Stability Considerations](#12-numerical-stability-considerations)

---

## 1. Overview

NeqSim's chemical reaction system solves for **chemical equilibrium** by minimizing Gibbs free energy subject to element balance constraints. The system uses a **two-stage approach**:

1. **Linear Programming (LP) Initial Estimate**: Generates a feasible starting point
2. **Newton-Raphson Iteration**: Refines the solution to convergence

The chemical equilibrium is solved **only in the aqueous/reactive phase**, not in gas or oil phases.

### Key Classes

| Class | Location | Purpose |
|-------|----------|---------|
| `ChemicalReactionOperations` | `chemicalreactions/` | Main orchestrator for reaction solving |
| `ChemicalReactionList` | `chemicalreactions/chemicalreaction/` | Manages reaction collection and matrix creation |
| `ChemicalReaction` | `chemicalreactions/chemicalreaction/` | Single reaction definition |
| `LinearProgrammingChemicalEquilibrium` | `chemicalreactions/chemicalequilibrium/` | LP-based initial estimate |
| `ChemicalEquilibrium` | `chemicalreactions/chemicalequilibrium/` | Newton solver |
| `ChemEq` | `chemicalreactions/chemicalequilibrium/` | Alternative solver (standalone) |

---

## 2. Chemical Reaction Initialization

### Entry Point: `SystemThermo.chemicalReactionInit()`

```java
public void chemicalReactionInit() {
    chemicalReactionOperations = new ChemicalReactionOperations(this);
    chemicalSystem = chemicalReactionOperations.hasReactions();
}
```

This is called when a user enables chemical reactions on a fluid system. It creates the `ChemicalReactionOperations` object which performs all initialization.

### ChemicalReactionOperations Constructor Flow

```
ChemicalReactionOperations(system)
    │
    ├── 1. Read reactions from database
    │       └── reactionList.readReactions(system)
    │
    ├── 2. Filter applicable reactions
    │       ├── removeJunkReactions(componentNames)
    │       └── removeDependentReactions()
    │
    ├── 3. Add missing product components
    │       └── addNewComponents()
    │
    ├── 4. Set up reactive components array
    │       └── setReactiveComponents()
    │
    ├── 5. Initialize reactions for phase
    │       └── reactionList.checkReactions(phase)
    │
    ├── 6. Create reaction matrix
    │       └── reactionList.createReactionMatrix(phase, components)
    │
    ├── 7. Calculate reference potentials
    │       └── calcChemRefPot(phase)
    │
    ├── 8. Get all elements in system
    │       └── getAllElements()
    │
    ├── 9. Create LP solver
    │       └── new LinearProgrammingChemicalEquilibrium(...)
    │
    ├── 10. Calculate A-matrix (stoichiometry)
    │        └── calcAmatrix()
    │
    ├── 11. Calculate mole vector
    │        └── calcNVector()
    │
    └── 12. Calculate element balance vector
             └── calcBVector()
```

### Database Reaction Loading

Reactions are loaded from the database table `reactiondata` (or `reactiondatakenteisenberg` for Kent-Eisenberg models):

```java
// ChemicalReactionList.readReactions()
dataSet = database.getResultSet("SELECT * FROM reactiondata");

// For each reaction:
// - Load K coefficients: K[0], K[1], K[2], K[3]
// - Load reference temperature: Tref
// - Load rate factor: r
// - Load activation energy: actH
// - Load stoichiometric coefficients from stoccoefdata table
```

The equilibrium constant is calculated as:

$$\ln K = K_0 + \frac{K_1}{T} + K_2 \ln(T) + K_3 T$$

### Filtering Reactions

Two filtering steps ensure only relevant, independent reactions are kept:

1. **`removeJunkReactions()`**: Removes reactions where not all reactants are present in the system
2. **`removeDependentReactions()`**: Removes linearly dependent reactions using matrix rank analysis

```java
// removeDependentReactions() builds the stoichiometry matrix and checks rank
Matrix mat = new Matrix(matrixData);
int rank = mat.rank();
if (rank < independentReactions.size()) {
    independentReactions.remove(independentReactions.size() - 1);
}
```

---

## 3. Reaction Matrix Setup

### createReactionMatrix()

The reaction matrix relates reactions to components through stoichiometric coefficients:

```java
public double[][] createReactionMatrix(PhaseInterface phase, ComponentInterface[] components) {
    // Store components for reference potential calculations
    this.refPotComponents = components;
    
    // Create matrices:
    // reacMatrix[reactions][components] - stoichiometric coefficients
    // reacGMatrix[reactions][components+1] - includes RT*ln(K) in last column
    
    reacMatrix = new double[chemicalReactionList.size()][components.length];
    reacGMatrix = new double[chemicalReactionList.size()][components.length + 1];
    
    for each reaction:
        for each component:
            if component is in reaction:
                reacMatrix[reaction][component] = stoichiometric_coefficient
                reacGMatrix[reaction][component] = stoichiometric_coefficient
        
        // Last column: -RT*ln(K) term for the reaction
        // Sign is NEGATIVE to match equilibrium: Σ(ν_i * μ_i) = -RT*ln(K)
        reacGMatrix[reaction][last] = -R * T * ln(K)
}
```

**Example**: For CO₂ + H₂O ⇌ H₂CO₃

| Component | CO₂ | H₂O | H₂CO₃ | -RT·ln(K) |
|-----------|-----|-----|-------|-----------|
| Reaction  | -1  | -1  | +1    | value     |

---

## 4. Stoichiometry Matrix (A-matrix) Construction

### calcAmatrix()

The A-matrix relates **components to elements** (plus ionic charge for electroneutrality):

```java
public double[][] calcAmatrix() {
    // Dimensions: (elements + 1) × components
    // Extra row for ionic charge balance
    double[][] A = new double[elements.length + 1][components.length];
    
    for each component j:
        for each element i:
            A[i][j] = number of atoms of element i in component j
        
        // Last row: ionic charge for electroneutrality
        A[elements.length][j] = components[j].getIonicCharge();
    
    return A;
}
```

**Example**: For a system with H₂O, H₃O⁺, OH⁻

| Component → | H₂O | H₃O⁺ | OH⁻ |
|-------------|-----|------|-----|
| H atoms     | 2   | 3    | 1   |
| O atoms     | 1   | 1    | 1   |
| Charge      | 0   | +1   | -1  |

The **element balance constraint** is:

$$\mathbf{A} \cdot \mathbf{n} = \mathbf{b}$$

Where:
- $\mathbf{n}$ = mole vector of components
- $\mathbf{b}$ = total moles of each element (conserved)

---

## 5. Reference Potential Calculation

### calcReferencePotentials()

Reference potentials ($\mu_i^{ref}$) are the standard-state chemical potentials. They are calculated from the reaction equilibrium relationships:

$$\sum_i \nu_i \mu_i^{ref} = -RT \ln K$$

The algorithm:

1. **Build G-matrix**: Stoichiometric coefficients + RT·ln(K) column
2. **Find independent columns**: Using matrix rank analysis
3. **Solve for independent components**: Direct matrix solve
4. **Propagate to dependent components**: Using reaction relationships

```java
public double[] calcReferencePotentials() {
    // Find linearly independent columns (components)
    ArrayList<Integer> independentColumns = new ArrayList<>();
    ArrayList<Integer> dependentColumns = new ArrayList<>();
    
    for each column j:
        // Try adding this column to the independent set
        if (nextMat.rank() > currentRank):
            independentColumns.add(j)
        else:
            dependentColumns.add(j)
    
    // Solve: A_indep * μ_indep = -B (where B = RT*ln(K))
    Matrix solv = currentMat.solve(Bmatrix.times(-1.0));
    
    // Propagate to dependent components using reaction stoichiometry
    for each dependent component:
        // Find reaction where all other components are known
        // Calculate: μ_dep = (-RT*ln(K) - Σ(ν_i*μ_i)) / ν_dep
}
```

---

## 6. Initial Estimate Generation (Linear Programming)

### LinearProgrammingChemicalEquilibrium

The LP solver generates an initial feasible estimate by minimizing the linear approximation of Gibbs energy:

**Objective Function**:
$$\min \sum_i \frac{\mu_i^{ref}}{RT} n_i$$

**Constraints**:
$$\mathbf{A} \cdot \mathbf{n} = \mathbf{b} \quad \text{(element balance)}$$
$$n_i \geq 0 \quad \text{(non-negative moles)}$$

### Implementation

```java
public double[] generateInitialEstimates(SystemInterface system, double[] bVector,
        double inertMoles, int phaseNum) {
    
    // Objective: minimize sum(μ_i/RT * n_i)
    double[] v = new double[components.length + 1];
    for (i = 0; i < components.length; i++) {
        v[i + 1] = chemRefPot[i] / (R * T);
    }
    LinearObjectiveFunction f = new LinearObjectiveFunction(v, 0.0);
    
    // Constraints: A*n = b (element balance)
    List<LinearConstraint> cons = new ArrayList<>();
    for each element j:
        cons.add(new LinearConstraint(A_row_j, Relationship.EQ, bVector[j]));
    
    // Solve using Apache Commons Math SimplexSolver
    SimplexSolver solver = new SimplexSolver();
    PointValuePair optimal = solver.optimize(
        new MaxIter(1000), f, consSet, GoalType.MINIMIZE, 
        new NonNegativeConstraint(true)
    );
    
    return optimal.getPoint();
}
```

---

## 7. Newton Solver for Chemical Equilibrium

### ChemicalEquilibrium Class

The Newton solver refines the LP estimate to satisfy the full Gibbs minimization with activity coefficients.

### Mathematical Formulation (Smith-Missen Method)

The Lagrangian for Gibbs minimization with element constraints:

$$\mathcal{L} = G - \sum_j \lambda_j \left( \sum_i a_{ji} n_i - b_j \right)$$

At equilibrium:
$$\frac{\partial \mathcal{L}}{\partial n_i} = \mu_i - \sum_j \lambda_j a_{ji} = 0$$

### Newton Update Equations

The Newton step is derived from the linearized equilibrium conditions:

$$\begin{bmatrix} \mathbf{AMA} & \mathbf{b}^T \\ \mathbf{b} & 0 \end{bmatrix} \begin{bmatrix} \boldsymbol{\lambda} \\ \tau \end{bmatrix} = \begin{bmatrix} \mathbf{AMμ} \\ n^T \boldsymbol{\mu} \end{bmatrix}$$

Where:
- $\mathbf{M} = \text{diag}(1/n_i)$ - diagonal matrix of inverse moles
- $\mathbf{AMA} = \mathbf{A} \mathbf{M}^{-1} \mathbf{A}^T$ - projected Hessian
- $\mathbf{AMμ} = \mathbf{A} \mathbf{M}^{-1} \boldsymbol{\mu}$

The mole updates are (Equation 3.115 in Smith & Missen):

$$\Delta n_i = \frac{1}{n_i} \left( \sum_j a_{ji} \lambda_j - \mu_i \right) + n_i \tau$$

### chemSolve() Implementation

```java
public void chemSolve() {
    // Protect against n_t = 0
    n_t = Math.max(MIN_MOLES, system.getPhase(phasenumb).getNumberOfMolesInPhase());
    
    // Build M-matrix: M_ij = δ_ij/n_i
    for (i = 0; i < NSPEC; i++) {
        n_mol[i] = component[i].getNumberOfMolesInPhase();
        for (k = 0; k < NSPEC; k++) {
            M_matrix[i][k] = (i == k) ? (1.0 / n_mol[i]) : 0.0;
            
            // Optional: add fugacity coefficient derivatives for non-ideal mixtures
            if (useFugacityDerivatives) {
                M_matrix[i][k] += dlnφ_i/dn_k;  // Non-ideal contribution
            }
        }
    }
    
    // Calculate chemical potentials: μ_i/RT = μ_ref + ln(n_i/n_t) + ln(γ_i)
    for (i = 0; i < NSPEC; i++) {
        chem_pot[i] = chem_ref[i] + Math.log(n_mol[i]/n_t) + logactivity[i];
    }
    
    // Build AMA matrix: A * M^-1 * A^T
    M_inv_AT = M_Jama_matrix.solve(A_Jama_matrix.transpose());
    AMA_matrix = A_Jama_matrix.times(M_inv_AT);
    
    // Build AMU vector: A * M^-1 * μ
    M_inv_mu = M_Jama_matrix.solve(chem_pot_Jama_Matrix.transpose());
    AMU_matrix = A_Jama_matrix.times(M_inv_mu);
    
    // Assemble and solve the Newton system
    // [AMA  b^T] [λ]   [AMμ]
    // [b    0  ] [τ] = [n·μ]
    A_solve.setMatrix(0, NELE-1, 0, NELE-1, AMA_matrix);
    A_solve.setMatrix(0, NELE-1, NELE, NELE, b_matrix.transpose());
    A_solve.setMatrix(NELE, NELE, 0, NELE-1, b_matrix);
    A_solve.set(NELE, NELE, 0.0);
    
    b_solve.setMatrix(0, NELE-1, 0, 0, AMU_matrix);
    b_solve.setMatrix(NELE, NELE, 0, 0, n·μ);
    
    x_solve = A_solve.solve(b_solve);  // [λ; τ]
    
    // Calculate mole updates: Δn = M^-1(A^T·λ - μ) + n·τ
    dn_matrix = M^-1 * (A^T * λ - μ) + n * τ;
}
```

### solve() - Main Iteration Loop

```java
public boolean solve() {
    double error = 1e10;
    int p = 0;
    
    do {
        p++;
        chemSolve();  // Calculate Newton step
        
        double step = step();  // Calculate damping factor
        
        // Update moles and calculate error
        for (i = 0; i < NSPEC; i++) {
            error += |Δn_i / n_i|;
            n_mol[i] = Δn_i * step + current_moles;
        }
        
        if (error <= errOld) {
            updateMoles();  // Apply to phase
            system.init(1, phasenumb);  // Reinitialize thermodynamics
            calcRefPot();  // Update activity coefficients
        }
        
    } while (error > tolerance && p < MAX_ITERATIONS);
    
    return converged;
}
```

---

## 8. Integration with TP Flash

### TPflash.run() Flow

Chemical equilibrium is solved **within** the TP flash iteration:

```
TPflash.run()
    │
    ├── system.init(0)
    ├── system.init(1)
    │
    ├── Check single-phase case
    │   └── if (isChemicalSystem()):
    │       └── solveChemEq(0, 0)  ← Initial LP estimate
    │       └── solveChemEq(0, 1)  ← Newton refinement
    │
    ├── Initialize K-values (Wilson correlation)
    │
    ├── if (isChemicalSystem()):
    │   └── solveChemEq(1, 0)  ← Pre-flash chemical equilibrium
    │   └── solveChemEq(1, 1)
    │
    ├── Rachford-Rice for phase split
    │
    ├── Main flash iteration loop:
    │   │
    │   ├── Update K-values
    │   │
    │   ├── if (isChemicalSystem()):
    │   │   └── solveChemEq(phase, 1)  ← Chemical eq. each iteration
    │   │
    │   ├── Rachford-Rice / successive substitution
    │   │
    │   └── Check convergence
    │
    └── Stability analysis (if needed)
```

### solveChemEq() Method

```java
public boolean solveChemEq(int phaseNum, int type) {
    // Find reactive phase (aqueous/liquid only)
    int reactivePhase = getReactivePhaseIndex();
    if (reactivePhase < 0) return false;  // Skip if no aqueous phase
    
    // Reinitialize if phase changed
    if (this.phase != phaseNum) {
        reinitializeForReactivePhase(phaseNum);
    }
    
    // Update element balance based on current composition
    nVector = calcNVector();
    bVector = calcBVector();
    
    // Type 0: LP initial estimate (firsttime or forced)
    if (firsttime || type == 0) {
        newMoles = initCalc.generateInitialEstimates(system, bVector, inertMoles, phaseNum);
        if (newMoles != null) {
            updateMoles(phaseNum);
            firsttime = false;
        }
    }
    
    // Newton solver refinement
    solver = new ChemicalEquilibrium(Amatrix, bVector, system, components, phaseNum);
    return solver.solve();
}
```

### Phase Detection

Chemical reactions are **only solved in the aqueous phase**:

```java
private int getReactivePhaseIndex() {
    for (int i = 0; i < nPhases; i++) {
        if ("aqueous".equalsIgnoreCase(phaseTypeName)) return i;
    }
    // Fallback to liquid phase during initialization
    for (int i = 0; i < nPhases; i++) {
        if ("liquid".equalsIgnoreCase(phaseTypeName)) return i;
    }
    return -1;  // No reactive phase
}
```

---

## 9. Mathematical Formulation

### Gibbs Free Energy Minimization

The objective is to find the composition $\{n_i\}$ that minimizes:

$$G = \sum_i n_i \mu_i = \sum_i n_i \left( \mu_i^{ref} + RT \ln \frac{n_i}{n_t} + RT \ln \gamma_i \right)$$

### Constraints

1. **Element balance** (mass conservation):
$$\sum_i a_{ji} n_i = b_j \quad \forall j \in \text{elements}$$

2. **Electroneutrality** (charge balance):
$$\sum_i z_i n_i = 0$$

3. **Non-negativity**:
$$n_i \geq 0 \quad \forall i$$

### Lagrangian and KKT Conditions

The Lagrangian:
$$\mathcal{L} = G - \sum_j \lambda_j (A_j \cdot n - b_j)$$

First-order optimality (KKT conditions):
$$\mu_i = \sum_j \lambda_j a_{ji} \quad \forall i$$

This states that at equilibrium, the chemical potential of each component equals the weighted sum of Lagrange multipliers (element potentials).

### Newton System Derivation

Linearizing the KKT conditions around current point $(n^k, \lambda^k)$:

$$\mathbf{M} \Delta n + \mathbf{A}^T \Delta \lambda = -(\mu - \mathbf{A}^T \lambda)$$
$$\mathbf{A} \Delta n = 0$$

Eliminating $\Delta n$:

$$(\mathbf{A} \mathbf{M}^{-1} \mathbf{A}^T) \Delta \lambda = \mathbf{A} \mathbf{M}^{-1} (\mathbf{A}^T \lambda - \mu)$$

With an additional normalization constraint for numerical stability.

---

## 10. Class Diagram and Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        SystemThermo                             │
│  chemicalReactionInit() ─────────────────────────┐              │
│  isChemicalSystem() ◄──────────────────────┐     │              │
│  getChemicalReactionOperations() ──────────┼─────┼──────┐       │
└─────────────────────────────────────────────┼─────┼──────┼───────┘
                                              │     │      │
                                              ▼     │      │
┌─────────────────────────────────────────────────────────────────┐
│              ChemicalReactionOperations                         │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Fields:                                                   │   │
│  │  - reactionList: ChemicalReactionList                     │   │
│  │  - components: ComponentInterface[]                       │   │
│  │  - Amatrix: double[][]                                    │   │
│  │  - bVector: double[]                                      │   │
│  │  - chemRefPot: double[]                                   │   │
│  │  - initCalc: LinearProgrammingChemicalEquilibrium         │   │
│  │  - solver: ChemicalEquilibrium                            │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  solveChemEq(phaseNum, type) ───────────────────────────────────┼───┐
│  calcAmatrix() ─────────────────────────────────────────────────┼─┐ │
│  calcBVector() ─────────────────────────────────────────────────┼─┤ │
│  calcChemRefPot() ──────────────────────────────────────────────┼─┤ │
└─────────────────────────────────────────────────────────────────┘ │ │
        │                                                           │ │
        ▼                                                           │ │
┌───────────────────────────────┐   ┌───────────────────────────────┐
│    ChemicalReactionList       │   │ LinearProgrammingChemical-    │
│                               │   │ Equilibrium                   │
│  readReactions()              │   │                               │
│  createReactionMatrix()       │   │  generateInitialEstimates()   │
│  calcReferencePotentials()    │   │  calcA()                      │
│  removeDependentReactions()   │   │                               │
└───────────────────────────────┘   └───────────────────────────────┘
        │                                       │
        ▼                                       ▼
┌───────────────────────────────┐   ┌───────────────────────────────┐
│    ChemicalReaction           │   │    ChemicalEquilibrium        │
│                               │   │                               │
│  - names: String[]            │   │  chemSolve()  ◄───────────────┼── Newton step
│  - stocCoefs: double[]        │   │  solve()      ◄───────────────┼── Main iteration
│  - K: double[]                │   │  step()       ◄───────────────┼── Line search
│                               │   │  updateMoles()                │
│  getK(phase)                  │   │                               │
│  init(phase)                  │   │  M_matrix, A_matrix           │
│  initMoleNumbers()            │   │  AMA_matrix, AMU_matrix       │
└───────────────────────────────┘   └───────────────────────────────┘
```

---

## 11. Key Methods Summary

| Method | Class | Purpose |
|--------|-------|---------|
| `chemicalReactionInit()` | SystemThermo | Entry point for reaction setup |
| `readReactions()` | ChemicalReactionList | Load reactions from database |
| `removeJunkReactions()` | ChemicalReactionList | Filter inapplicable reactions |
| `removeDependentReactions()` | ChemicalReactionList | Remove linearly dependent reactions |
| `createReactionMatrix()` | ChemicalReactionList | Build stoichiometry matrix |
| `calcReferencePotentials()` | ChemicalReactionList | Calculate standard chemical potentials |
| `calcAmatrix()` | ChemicalReactionOperations | Build element-component matrix |
| `calcBVector()` | ChemicalReactionOperations | Calculate element balance |
| `generateInitialEstimates()` | LinearProgrammingChemicalEquilibrium | LP-based starting point |
| `solveChemEq()` | ChemicalReactionOperations | Orchestrate equilibrium solving |
| `chemSolve()` | ChemicalEquilibrium | Single Newton iteration |
| `solve()` | ChemicalEquilibrium | Main Newton iteration loop |
| `step()` | ChemicalEquilibrium | Calculate damping factor |
| `updateMoles()` | ChemicalEquilibrium | Apply mole updates to phase |

---

## 12. Numerical Stability Considerations

### Minimum Moles Protection

All solvers protect against log(0) and division by zero using a unified constant:

```java
// Unified across ChemicalEquilibrium, ChemEq, and LinearProgrammingChemicalEquilibrium
private static final double MIN_MOLES = 1e-60;

// Usage:
double safeMoles = Math.max(MIN_MOLES, n_mol[i]);
chem_pot[i] = chem_ref[i] + Math.log(safeMoles / n_t);
```

### Configurable Solver Parameters

The solver supports configurable iteration limits and tolerances:

```java
ChemicalEquilibrium solver = new ChemicalEquilibrium(...);

// Configure iteration limits (default: 100)
solver.setMaxIterations(200);

// Configure convergence tolerance (default: 1e-8)
solver.setConvergenceTolerance(1e-10);

// Enable full Smith-Missen M-matrix with -1/n_t coupling term
solver.setUseFullMMatrix(true);
```

### Solver Metrics

After solving, diagnostic metrics are available:

```java
boolean converged = solver.solve();

// Query solver performance
int iterations = solver.getLastIterationCount();
double finalError = solver.getLastError();
boolean success = solver.isLastConverged();
```

### Singular Matrix Handling

The Newton system can become singular. Fallbacks are implemented:

```java
try {
    x_solve = A_solve.solve(b_solve);
} catch (Exception ex) {
    // Try pseudo-inverse (SVD-based least squares)
    x_solve = solveLeastSquares(A_solve, b_solve);
}
```

### Step Size Damping

The `step()` method ensures moles don't go negative:

```java
if (n_omega[i] < 0) {
    // Reduce step to keep n positive
    step = min(step, -n_mol[i] / d_n[i] * 0.99);
}
```

### Stagnation Detection

The solver detects when it's not making progress:

```java
private static final int STAGNATION_LIMIT = 10;

if (error >= bestError) {
    stagnationCount++;
}
if (stagnationCount >= STAGNATION_LIMIT) {
    break;  // Exit to prevent infinite loop
}
```

### Ion Concentration Enforcement

For aqueous systems, water equilibrium is enforced:

```java
// Enforce Kw = [H3O+][OH-] ≈ 10^-14
if (h3oTooLow && ohTooLow) {
    // Both ions unrealistically low - solver failed
    // Set to neutral pH as reasonable default
    targetH3OMoles = neutralH3OMoleFraction * totalMoles;
}
```

---

## References

1. Smith, W.R. and Missen, R.W., "Chemical Reaction Equilibrium Analysis: Theory and Algorithms", Wiley (1982)
2. Michelsen, M.L. and Mollerup, J.M., "Thermodynamic Models: Fundamentals & Computational Aspects", Tie-Line Publications (2007)
3. NeqSim Source Code: [ChemicalReactionOperations.java](../src/main/java/neqsim/chemicalreactions/ChemicalReactionOperations.java)
4. NeqSim Source Code: [ChemicalEquilibrium.java](../src/main/java/neqsim/chemicalreactions/chemicalequilibrium/ChemicalEquilibrium.java)

---

*Document generated: December 2024 (Updated: December 27, 2024)*
*NeqSim Chemical Reactions Package Deep Review*

---

## Changelog

| Date | Changes |
|------|--------|
| Dec 27, 2024 | Fixed sign convention in `createReactionMatrix()` - now correctly stores `-RT*ln(K)` |
| Dec 27, 2024 | Unified `MIN_MOLES` constant to `1e-60` across all solvers |
| Dec 27, 2024 | Added configurable `maxIterations` and `convergenceTolerance` parameters |
| Dec 27, 2024 | Added solver metrics: `getLastIterationCount()`, `getLastError()`, `isLastConverged()` |
| Dec 27, 2024 | Fixed recursive stack overflow in `ChemEq.solve()` - now iterative |
| Dec 27, 2024 | Added optional `useFullMMatrix` flag for Smith-Missen -1/n_t term |
| Dec 27, 2024 | Added LP result validation for NaN/Inf values |
