---
title: SQP Optimizer
description: "Sequential Quadratic Programming (SQP) nonlinear optimizer in NeqSim. Solves constrained process optimization problems with equality/inequality constraints, variable bounds, BFGS Hessian updates, and KKT convergence checking."
---

# SQP Optimizer

`SQPoptimizer` is a general-purpose nonlinear programming (NLP) solver that uses
Sequential Quadratic Programming to minimize an objective function subject to
equality constraints, inequality constraints, and variable bounds.

## When to Use SQP

| Need | Recommended Optimizer |
|------|----------------------|
| Single variable adjustment | `Adjuster` |
| Multi-variable simultaneous specs | `MultiVariableAdjuster` |
| Minimize/maximize an objective (no constraints) | `ProductionOptimizer` or SQP |
| Minimize/maximize with equality & inequality constraints | **SQP** |
| External optimizer integration | `ProcessSimulationEvaluator` + SciPy |
| Multi-objective Pareto | `ProductionOptimizer.optimizePareto()` |

SQP is the right choice when you have a **constrained optimization** problem:
minimise cost, maximise throughput, or optimise operating conditions subject to
equipment limits, product specs, or safety constraints.

## Algorithm

The solver implements a textbook SQP method:

1. **Quadratic sub-problem (QP):** At each iteration, approximate the Lagrangian
   with a quadratic model and linearise constraints, then solve the resulting QP
   with an active-set method.

2. **BFGS Hessian update:** The Hessian of the Lagrangian is approximated using
   a damped BFGS update (Powell's modification) for guaranteed positive definiteness.

3. **L1 merit function:** An exact penalty merit function combines the objective
   and constraint violations to determine step acceptance:

   $$\phi(x) = f(x) + \sum_i \mu_i |c_i(x)| + \sum_j \mu_j \max(0, -h_j(x))$$

4. **Armijo backtracking:** The step length is reduced until sufficient decrease
   in the merit function is achieved.

5. **KKT convergence:** The solver checks Karush-Kuhn-Tucker optimality conditions
   and stops when the KKT error falls below the tolerance.

## Basic Usage

```java
import neqsim.process.util.optimizer.SQPoptimizer;
import neqsim.process.util.optimizer.SQPoptimizer.OptimizationResult;

SQPoptimizer sqp = new SQPoptimizer();

// Objective: minimise f(x) = (x[0]-2)^2 + (x[1]-3)^2
sqp.setObjectiveFunction(x -> (x[0] - 2.0) * (x[0] - 2.0)
                             + (x[1] - 3.0) * (x[1] - 3.0));

// Equality constraint: x[0] + x[1] = 4
sqp.addEqualityConstraint(x -> x[0] + x[1] - 4.0);

// Inequality constraint (h(x) >= 0): x[0] >= 1
sqp.addInequalityConstraint(x -> x[0] - 1.0);

// Variable bounds
sqp.setVariableBounds(
    new double[] {0.0, 0.0},   // lower bounds
    new double[] {10.0, 10.0}  // upper bounds
);

// Solver settings
sqp.setMaxIterations(100);
sqp.setTolerance(1e-8);

// Solve from initial point
sqp.setInitialPoint(new double[] {0.0, 0.0});
OptimizationResult result = sqp.solve();

if (result.isConverged()) {
    double[] xOpt = result.getOptimalPoint();
    System.out.printf("x* = [%.4f, %.4f]%n", xOpt[0], xOpt[1]);
    System.out.printf("f* = %.6f%n", result.getOptimalValue());
    System.out.printf("Iterations: %d%n", result.getIterations());
    System.out.printf("KKT error: %.2e%n", result.getKktError());
}
```

## Process Optimization Example

Optimise compressor interstage pressures to minimise total power:

```java
// Build process: 2-stage compression with intercooling
SystemSrkEos gas = new SystemSrkEos(288.15, 5.0);
gas.addComponent("methane", 0.9);
gas.addComponent("ethane", 0.07);
gas.addComponent("propane", 0.03);
gas.setMixingRule("classic");

Stream feed = new Stream("feed", gas);
feed.setFlowRate(50000.0, "kg/hr");

Compressor comp1 = new Compressor("LP Comp", feed);
comp1.setOutletPressure(20.0);
Cooler cooler1 = new Cooler("Intercooler", comp1.getOutletStream());
cooler1.setOutTemperature(303.15);
Compressor comp2 = new Compressor("HP Comp", cooler1.getOutletStream());
comp2.setOutletPressure(80.0);

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(comp1);
process.add(cooler1);
process.add(comp2);
process.run();

// Optimise: find interstage pressure that minimises total power
SQPoptimizer sqp = new SQPoptimizer();

sqp.setObjectiveFunction(x -> {
    comp1.setOutletPressure(x[0]);
    process.run();
    return comp1.getPower("kW") + comp2.getPower("kW");
});

// Interstage pressure between feed and discharge
sqp.setVariableBounds(
    new double[] {8.0},    // above feed pressure
    new double[] {60.0}    // below final discharge
);

sqp.setMaxIterations(30);
sqp.setTolerance(1e-4);
sqp.setFiniteDifferenceStep(0.5);  // pressure step for gradients

sqp.setInitialPoint(new double[] {20.0});
OptimizationResult result = sqp.solve();

if (result.isConverged()) {
    double pOpt = result.getOptimalPoint()[0];
    System.out.printf("Optimal interstage P: %.1f bara%n", pOpt);
    System.out.printf("Min total power: %.0f kW%n", result.getOptimalValue());
}
```

## API Reference

### Constructor

```java
SQPoptimizer sqp = new SQPoptimizer();
```

### Problem Definition

| Method | Description |
|--------|-------------|
| `setObjectiveFunction(ObjectiveFunc)` | Function to minimise: `f(double[] x) -> double` |
| `addEqualityConstraint(ConstraintFunc)` | Constraint `c(x) = 0` |
| `addInequalityConstraint(ConstraintFunc)` | Constraint `h(x) >= 0` |
| `setVariableBounds(double[], double[])` | Lower and upper bounds on all variables |

### Solver Settings

| Method | Default | Description |
|--------|---------|-------------|
| `setMaxIterations(int)` | 100 | Maximum iterations |
| `setTolerance(double)` | 1e-8 | KKT error convergence tolerance |
| `setFiniteDifferenceStep(double)` | 1e-6 | Step for central-difference gradients |

### Result

`OptimizationResult` is returned from `solve()`:

| Method | Returns | Description |
|--------|---------|-------------|
| `isConverged()` | `boolean` | Whether KKT conditions are satisfied |
| `getOptimalPoint()` | `double[]` | Optimal variable values |
| `getOptimalValue()` | `double` | Objective value at optimum |
| `getIterations()` | `int` | Number of SQP iterations |
| `getKktError()` | `double` | Final KKT error measure |

### Functional Interfaces

```java
// Objective function: x -> scalar
SQPoptimizer.ObjectiveFunc f = x -> x[0]*x[0] + x[1]*x[1];

// Constraint function: x -> scalar (equality: =0, inequality: >=0)
SQPoptimizer.ConstraintFunc c = x -> x[0] + x[1] - 1.0;
```

## Integration with ProcessOptimizationEngine

The `SEQUENTIAL_QUADRATIC_PROGRAMMING` algorithm is also registered as a
`SearchAlgorithm` in `ProcessOptimizationEngine`:

```java
import neqsim.process.util.optimizer.ProcessOptimizationEngine;

ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
engine.setSearchAlgorithm(
    ProcessOptimizationEngine.SearchAlgorithm.SEQUENTIAL_QUADRATIC_PROGRAMMING);
// ... configure and run
```

## Related Documentation

- [Optimization Overview](OPTIMIZATION_OVERVIEW) - All optimization capabilities
- [Adjusters](../equipment/util/adjusters) - Single and multi-variable adjusters
- [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION) - SciPy/Python integration
- [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) - ProductionOptimizer examples
