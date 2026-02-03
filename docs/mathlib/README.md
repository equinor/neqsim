---
title: Mathematical Library Package
description: The `mathlib` package provides mathematical utilities, nonlinear solvers, and numerical methods.
---

# Mathematical Library Package

The `mathlib` package provides mathematical utilities, nonlinear solvers, and numerical methods.

## Table of Contents
- [Overview](#overview)
- [Package Structure](#package-structure)
- [Nonlinear Solvers](#nonlinear-solvers)
- [General Math](#general-math)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.mathlib`

**Purpose:**
- Nonlinear equation solving
- Matrix operations
- Numerical differentiation
- Root finding algorithms
- Optimization routines

---

## Package Structure

```
mathlib/
├── generalmath/                  # General mathematical utilities
│   ├── GeneralMath.java          # Common math functions
│   ├── TDMAsolve.java            # Tridiagonal matrix solver
│   └── SplineInterpolation.java  # Spline interpolation
│
└── nonlinearsolver/              # Nonlinear equation solvers
    ├── NonLinearSolver.java      # Base solver
    ├── NewtonRaphson.java        # Newton-Raphson method
    ├── Brent.java                # Brent's method
    ├── Bisection.java            # Bisection method
    └── NumericalDerivative.java  # Numerical derivatives
```

---

## Nonlinear Solvers

### Newton-Raphson Method

Iterative method for finding roots of functions.

$$x_{n+1} = x_n - \frac{f(x_n)}{f'(x_n)}$$

```java
import neqsim.mathlib.nonlinearsolver.NewtonRaphson;

// Define function to solve: f(x) = x² - 2 (find √2)
Function<Double, Double> f = x -> x * x - 2.0;
Function<Double, Double> df = x -> 2.0 * x;

NewtonRaphson solver = new NewtonRaphson();
solver.setFunction(f);
solver.setDerivative(df);
solver.setInitialGuess(1.0);
solver.setTolerance(1e-10);
solver.setMaxIterations(100);

double root = solver.solve();
System.out.println("√2 = " + root);  // 1.4142135623...
```

### Brent's Method

Robust root-finding combining bisection, secant, and inverse quadratic interpolation.

```java
import neqsim.mathlib.nonlinearsolver.Brent;

Function<Double, Double> f = x -> x * x * x - x - 2.0;

Brent solver = new Brent();
solver.setFunction(f);
solver.setBracket(1.0, 2.0);  // Root is in [1, 2]
solver.setTolerance(1e-10);

double root = solver.solve();
System.out.println("Root: " + root);
```

### Bisection Method

Simple but robust root-finding.

```java
import neqsim.mathlib.nonlinearsolver.Bisection;

Function<Double, Double> f = x -> Math.sin(x) - 0.5;

Bisection solver = new Bisection();
solver.setFunction(f);
solver.setBracket(0.0, Math.PI);
solver.setTolerance(1e-8);

double root = solver.solve();
System.out.println("arcsin(0.5) = " + root);  // π/6 ≈ 0.5236
```

---

## Numerical Derivatives

### Forward Difference

$$f'(x) \approx \frac{f(x+h) - f(x)}{h}$$

### Central Difference

$$f'(x) \approx \frac{f(x+h) - f(x-h)}{2h}$$

```java
import neqsim.mathlib.nonlinearsolver.NumericalDerivative;

Function<Double, Double> f = x -> Math.exp(x);

NumericalDerivative deriv = new NumericalDerivative();
deriv.setFunction(f);
deriv.setStepSize(1e-6);

double df = deriv.centralDifference(1.0);
System.out.println("d/dx(e^x) at x=1: " + df);  // ≈ e ≈ 2.718
```

---

## General Math

### TDMAsolve (Thomas Algorithm)

Efficient solver for tridiagonal systems.

$$\begin{bmatrix} b_1 & c_1 \\ a_2 & b_2 & c_2 \\ & \ddots & \ddots & \ddots \\ & & a_{n-1} & b_{n-1} & c_{n-1} \\ & & & a_n & b_n \end{bmatrix} \begin{bmatrix} x_1 \\ x_2 \\ \vdots \\ x_{n-1} \\ x_n \end{bmatrix} = \begin{bmatrix} d_1 \\ d_2 \\ \vdots \\ d_{n-1} \\ d_n \end{bmatrix}$$

```java
import neqsim.mathlib.generalmath.TDMAsolve;

// Coefficients
double[] a = {0, 1, 1, 1};    // Lower diagonal
double[] b = {4, 4, 4, 4};    // Main diagonal
double[] c = {1, 1, 1, 0};    // Upper diagonal
double[] d = {5, 5, 5, 5};    // Right-hand side

double[] x = TDMAsolve.solve(a, b, c, d);
```

### Spline Interpolation

Cubic spline interpolation for smooth curves.

```java
import neqsim.mathlib.generalmath.SplineInterpolation;

double[] xData = {0, 1, 2, 3, 4, 5};
double[] yData = {0, 1, 4, 9, 16, 25};  // y = x²

SplineInterpolation spline = new SplineInterpolation(xData, yData);

// Interpolate at any point
double y = spline.interpolate(2.5);  // ≈ 6.25
```

### Common Math Functions

```java
import neqsim.mathlib.generalmath.GeneralMath;

// Safe logarithm (handles near-zero)
double logVal = GeneralMath.safeLog(x);

// Polynomial evaluation
double[] coeffs = {1, 2, 3};  // 1 + 2x + 3x²
double polyVal = GeneralMath.polynomial(x, coeffs);

// Linear interpolation
double y = GeneralMath.linearInterpolate(x, x1, y1, x2, y2);
```

---

## Matrix Operations

For matrix operations, NeqSim uses external libraries:
- **EJML** (Efficient Java Matrix Library)
- **Apache Commons Math**
- **JAMA**

```java
import org.ejml.simple.SimpleMatrix;

// Matrix multiplication
SimpleMatrix A = new SimpleMatrix(new double[][] {
    {1, 2}, {3, 4}
});
SimpleMatrix B = new SimpleMatrix(new double[][] {
    {5, 6}, {7, 8}
});
SimpleMatrix C = A.mult(B);

// Solve linear system Ax = b
double[][] bData = { {1}, {2} };
SimpleMatrix b = new SimpleMatrix(bData);
SimpleMatrix x = A.solve(b);

// Eigenvalue decomposition
SimpleEVD evd = A.eig();
```

---

## Usage in NeqSim

### Flash Calculations

Newton-Raphson used in flash convergence:

```java
// Simplified flash iteration
while (error > tolerance) {
    // Calculate fugacities
    double[] fugL = calculateLiquidFugacity();
    double[] fugV = calculateVaporFugacity();
    
    // Newton-Raphson update for K-values
    for (int i = 0; i < nc; i++) {
        K[i] = K[i] * fugL[i] / fugV[i];
    }
    
    // Rachford-Rice equation
    beta = solveRachfordRice(K, z);
    error = calculateError();
}
```

### Phase Envelope

Continuation methods for phase boundary tracking:

```java
// Predictor-corrector method
while (pressure < maxPressure) {
    // Predict next point
    double[] predicted = predictNextPoint(direction, stepSize);
    
    // Correct using Newton-Raphson
    double[] corrected = correctPoint(predicted);
    
    // Update direction for next step
    direction = updateDirection(corrected);
}
```

---

## Optimization

### Minimization

```java
// Golden section search for minimum
Function<Double, Double> f = x -> (x - 2) * (x - 2) + 1;

double a = 0, b = 5;
double tolerance = 1e-6;
double phi = (1 + Math.sqrt(5)) / 2;

while ((b - a) > tolerance) {
    double x1 = b - (b - a) / phi;
    double x2 = a + (b - a) / phi;
    
    if (f.apply(x1) < f.apply(x2)) {
        b = x2;
    } else {
        a = x1;
    }
}
double minimum = (a + b) / 2;  // ≈ 2.0
```

### Multidimensional Optimization

For parameter fitting, NeqSim uses:
- Levenberg-Marquardt
- Simplex (Nelder-Mead)
- BFGS

---

## Convergence Criteria

### Absolute Tolerance

$$|x_{n+1} - x_n| < \epsilon$$

### Relative Tolerance

$$\frac{|x_{n+1} - x_n|}{|x_n|} < \epsilon$$

### Function Value Tolerance

$$|f(x_n)| < \epsilon$$

---

## Best Practices

1. **Choose appropriate solver** - Newton for fast convergence, Brent for robustness
2. **Provide good initial guess** - Improves convergence
3. **Set reasonable tolerances** - Balance accuracy vs speed
4. **Check convergence** - Verify solver actually converged
5. **Handle edge cases** - Division by zero, negative values for log

---

## Related Documentation

- [Flash Calculations](../thermo/flash_calculations_guide) - Flash solver internals
- [Physical Properties](../physical_properties/README) - Property correlations
