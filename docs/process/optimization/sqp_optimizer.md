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
| Multi-objective Pareto | `MultiObjectiveOptimizer` |

SQP is the right choice when you have a **constrained optimization** problem:
minimise cost, maximise throughput, or optimise operating conditions subject to
equipment limits, product specs, or safety constraints.

## Algorithm

The solver implements a reduced SQP method for smooth local optimization:

1. **Quadratic sub-problem (QP):** At each iteration, build a quadratic objective
   model and linearise constraints. The reduced active-set solve releases
   inequalities with negative multipliers; variable bounds use projection.

2. **BFGS Hessian update:** Objective-gradient differences update the Hessian
   approximation with damped BFGS (Powell's modification). Nonlinear constraint
   curvature is not included in this update.

3. **L1 merit function:** An exact penalty merit function combines the objective
   and constraint violations to determine step acceptance:

   $$\phi(x) = f(x) + \sum_i \mu_i |c_i(x)| + \sum_j \mu_j \max(0, -h_j(x))$$

4. **Armijo backtracking:** The step length is reduced until sufficient decrease
   in the merit function is achieved.

5. **KKT convergence:** The solver checks Karush-Kuhn-Tucker optimality conditions
   and stops when the KKT error falls below the tolerance.

Check `isConverged()` and replay the selected process state before using the result.
The reported KKT error checks stationarity and primal feasibility; independently
check constraint values and solution quality for the intended application.

## Basic Usage

Each example is a Java method body: put imports above your class and executable
statements inside `public static void main(String[] args)`. Run the algebraic and
process examples separately because they reuse local names.


Java output uses Log4j2. Declare this field inside your example class:
`private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger("OptimizationExample");`.

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

// Start from a point satisfying the equality, inequality, and bounds.
sqp.setInitialPoint(new double[] {1.0, 3.0});
OptimizationResult result = sqp.solve();
logger.info("Converged={}, KKT error={}", result.isConverged(), result.getKktError());

if (result.isConverged()) {
    double[] xOpt = result.getOptimalPoint();
    logger.info(String.format("x* = [%.4f, %.4f]%n", xOpt[0], xOpt[1]));
    logger.info(String.format("f* = %.6f%n", result.getOptimalValue()));
    logger.info(String.format("Iterations: %d%n", result.getIterations()));
    logger.info(String.format("KKT error: %.2e%n", result.getKktError()));
}
```

## Process Optimization Example

Optimise compressor interstage pressures to minimise total power:

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.SQPoptimizer;
import neqsim.process.util.optimizer.SQPoptimizer.OptimizationResult;

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
comp1.setIsentropicEfficiency(0.75);
Cooler cooler1 = new Cooler("Intercooler", comp1.getOutletStream());
cooler1.setOutTemperature(303.15);
Compressor comp2 = new Compressor("HP Comp", cooler1.getOutletStream());
comp2.setOutletPressure(80.0);
comp2.setIsentropicEfficiency(0.75);

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
sqp.setFiniteDifferenceStep(1e-4);  // Relative step, multiplied by max(1, |x[i]|)

sqp.setInitialPoint(new double[] {20.0});
OptimizationResult result = sqp.solve();
logger.info("Converged={}, KKT error={}", result.isConverged(), result.getKktError());

if (result.isConverged()) {
    double pOpt = result.getOptimalPoint()[0];
    comp1.setOutletPressure(pOpt);
    process.run(); // Restore the optimum after finite-difference trial evaluations
    logger.info(String.format("Optimal interstage P: %.1f bara%n", pOpt));
    logger.info(String.format("Min total power: %.0f kW%n", result.getOptimalValue()));
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
| `setTolerance(double)` | 1e-6 | KKT error convergence tolerance |
| `setFiniteDifferenceStep(double)` | 1e-6 | Relative step for central-difference gradients |

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
