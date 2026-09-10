---
title: Mathematical Library Package
description: Executable polynomial, root finding, interpolation, differentiation, and linear algebra examples using NeqSim and its numerical dependencies.
---

NeqSim's mathematical package provides specialized utilities. General-purpose
solvers and interpolation are also available through its Apache Commons Math 3
dependency. Each Java block below is independent: place the imports at the top
of a file and the statements inside a method. The examples are compiled and
executed directly from this page by `MathAndExpanderDocumentationTest`.

## Table of Contents

- [Overview](#overview)
- [Package Structure](#package-structure)
- [Nonlinear Solvers](#nonlinear-solvers)
- [Numerical Derivatives](#numerical-derivatives)
- [General Math](#general-math)
- [Matrix Operations](#matrix-operations)
- [Usage Examples](#usage-examples)
- [Optimization](#optimization)

## Overview

**Location:** `neqsim.mathlib`

The package does not expose the previously documented `NewtonRaphson`,
`Brent`, `Bisection`, `SplineInterpolation`, or `GeneralMath` classes.
The existing polynomial solver's name is spelled `NewtonRhapson`.

## Package Structure

| Package | Implemented classes |
|---|---|
| `neqsim.mathlib.generalmath` | `TDMAsolve`, `BandedLinearSystemSolver` |
| `neqsim.mathlib.nonlinearsolver` | `NewtonRhapson`, `SysNewtonRhapson`, `NumericalDerivative`, `NumericalIntegration` |

## Nonlinear Solvers

### Newton-Raphson Method

Call `setOrder` before `setConstants`. Polynomial coefficients are in
**descending** powers. The initial guess is passed to `solve`; the class
does not accept a function object or expose `setTolerance`.

<!-- doc-test: math-newton -->
```java
import neqsim.mathlib.nonlinearsolver.NewtonRhapson;

NewtonRhapson solver = new NewtonRhapson();
solver.setOrder(2);
solver.setConstants(new double[] {1.0, 0.0, -2.0}); // x² - 2
solver.setMaxIterations(100);
double root = solver.solve(1.0); // approximately 1.41421356237
double residual = Math.abs(solver.funkValue(root));
```

Check the residual after solving; a returned number alone does not establish
convergence.

### Brent's Method

Use the Commons Math API for general bracketed root finding. Endpoints must
straddle a root.

<!-- doc-test: math-brent -->
```java
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;

UnivariateFunction function = x -> x * x * x - x - 2.0;
BrentSolver solver = new BrentSolver(1.0e-10);
double root = solver.solve(100, function, 1.0, 2.0); // approximately 1.5213797068
```

### Bisection Method

For `sin(x) - 0.5`, use `[0, π/2]` to find `π/6`. The formerly shown
`[0, π]` interval has the same sign at both endpoints and is not a valid
bracket, even though it contains two roots.

<!-- doc-test: math-bisection -->
```java
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BisectionSolver;

UnivariateFunction function = x -> Math.sin(x) - 0.5;
BisectionSolver solver = new BisectionSolver(1.0e-10);
double root = solver.solve(100, function, 0.0, Math.PI / 2.0);
```

## Numerical Derivatives

### Forward Difference

$$f'(x) \approx \frac{f(x+h)-f(x)}{h}$$

### Central Difference

$$f'(x) \approx \frac{f(x+h)-f(x-h)}{2h}$$

NeqSim's `NumericalDerivative` is a static thermodynamic helper. It does not
provide the previously shown general `setFunction` or `centralDifference`
API. For a scalar function, write the finite difference explicitly:

<!-- doc-test: math-derivative -->
```java
import java.util.function.DoubleUnaryOperator;

DoubleUnaryOperator function = Math::exp;
double x = 1.0;
double h = 1.0e-5;
double derivative = (function.applyAsDouble(x + h) - function.applyAsDouble(x - h))
    / (2.0 * h); // approximately e
```

Choose a step appropriate to the variable scale: small steps amplify roundoff,
while large steps increase truncation error.

## General Math

### TDMAsolve (Thomas Algorithm)

This solves `a[i] x[i-1] + b[i] x[i] + c[i] x[i+1] = d[i]`. Arrays must have
equal lengths, with unused endpoints `a[0] = c[n-1] = 0`. The algorithm does
not pivot; its elimination pivots must remain nonzero.

<!-- doc-test: math-tdma -->
```java
import neqsim.mathlib.generalmath.TDMAsolve;

double[] a = {0.0, 1.0, 1.0, 1.0};
double[] b = {4.0, 4.0, 4.0, 4.0};
double[] c = {1.0, 1.0, 1.0, 0.0};
double[] d = {5.0, 5.0, 5.0, 5.0};
double[] solution = TDMAsolve.solve(a, b, c, d);
```

### Spline Interpolation

Commons Math's natural cubic spline can interpolate inside the tabulated range.
Zero endpoint curvature means that it does not reproduce every quadratic
exactly between knots.

<!-- doc-test: math-spline -->
```java
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

double[] xData = {0, 1, 2, 3, 4, 5};
double[] yData = {0, 1, 4, 9, 16, 25};
PolynomialSplineFunction spline = new SplineInterpolator().interpolate(xData, yData);
double valueAtKnot = spline.value(2.0); // 4.0
double interpolated = spline.value(2.5); // approximately 6.26316
```

### Common Math Functions

Use Java's `Math` and Commons Math. Here polynomial coefficients are in
**ascending** powers, unlike `NewtonRhapson.setConstants`.

<!-- doc-test: math-functions -->
```java
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;

double logValue = Math.log(2.0);
PolynomialFunction polynomial = new PolynomialFunction(new double[] {1.0, 2.0, 3.0});
double polynomialValue = polynomial.value(2.0); // 17.0
double linearValue = new LinearInterpolator()
    .interpolate(new double[] {1.0, 3.0}, new double[] {2.0, 6.0})
    .value(2.0); // 4.0
```

Check logarithm domains explicitly. Clipping negative arguments would conceal
a modeling error.

## Matrix Operations

NeqSim uses EJML, Apache Commons Math, and JAMA. For example, solve `A x = b`
with EJML:

<!-- doc-test: math-matrix -->
```java
import org.ejml.simple.SimpleMatrix;

double[][] matrixData = {
    {1.0, 2.0},
    {3.0, 4.0}
};
double[][] rhsData = {
    {1.0},
    {2.0}
};
SimpleMatrix matrix = new SimpleMatrix(matrixData);
SimpleMatrix rhs = new SimpleMatrix(rhsData);
SimpleMatrix solution = matrix.solve(rhs); // [0.0, 0.5]
double residual = matrix.mult(solution).minus(rhs).normF();
```

## Usage Examples

### Usage in NeqSim

Flash and phase-envelope algorithms combine equilibrium models, stability
checks, and specialized solvers. Use the public `ThermodynamicOperations`
workflows in the [flash guide](../thermo/flash_calculations_guide).
Illustrative fugacity-update pseudocode is not a standalone flash solver.

## Optimization

### Minimization

Commons Math supports bounded scalar minimization:

<!-- doc-test: math-minimum -->
```java
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;

BrentOptimizer optimizer = new BrentOptimizer(1.0e-10, 1.0e-12);
UnivariatePointValuePair optimum = optimizer.optimize(
    new MaxEval(100),
    new UnivariateObjectiveFunction(x -> (x - 2.0) * (x - 2.0) + 1.0),
    GoalType.MINIMIZE,
    new SearchInterval(0.0, 5.0));
double minimumLocation = optimum.getPoint(); // 2.0
double minimumValue = optimum.getValue(); // 1.0
```

### Multidimensional Optimization

See [process optimization](../process/optimization/OPTIMIZATION_OVERVIEW) for
equipment constraints and process evaluators, and [statistics](../statistics/)
for parameter estimation.

## Convergence Criteria

Check residuals, bounds, finite values, and problem-scaled tolerances rather
than only the change between iterations. For linear systems check `||A x-b||`;
for physical models also check conservation and admissible states.

## Best Practices

1. Choose a solver whose assumptions match the problem.
2. Supply valid brackets or reasonable initial guesses.
3. Check residuals independently.
4. Handle singular systems and invalid function domains explicitly.

## Related Documentation

- [Flash Calculations](../thermo/flash_calculations_guide)
- [Physical Properties](../physical_properties/)
