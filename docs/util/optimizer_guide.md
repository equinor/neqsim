# Process Optimization Module

The `neqsim.util.optimizer` package provides a comprehensive optimization framework for process simulation, including gradient-based optimizers, multi-objective optimization with Pareto front generation, and sensitivity analysis tools.

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Optimization Algorithms](#optimization-algorithms)
- [Multi-Objective Optimization](#multi-objective-optimization)
- [Sensitivity Analysis](#sensitivity-analysis)
- [Flow Rate Optimization](#flow-rate-optimization)
- [Production Optimization](#production-optimization)
- [Advanced Features](#advanced-features)
- [Best Practices](#best-practices)

---

## Overview

**Location:** `neqsim.util.optimizer`

**Purpose:**

- Unified optimization API for process systems
- Multiple algorithm support (BFGS, Gradient Descent, Line Search)
- Multi-objective optimization with Pareto front generation
- Auto-sensitivity analysis for decision support
- Equipment-specific optimization strategies
- Plugin architecture for custom equipment

---

## Quick Start

### Basic Optimization

```java
import neqsim.util.optimizer.*;

// Create process system
ProcessSystem process = new ProcessSystem();
// ... add equipment ...

// Create optimizer
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);

// Define objective: Maximize production
engine.setObjectiveFunction(sys -> {
    Stream product = (Stream) sys.getMeasurementDevice("ProductStream");
    return -product.getFlowRate("kg/hr"); // Negative for maximization
});

// Add variables
engine.addVariable("FeedFlow", 1000.0, 5000.0, 2000.0);
engine.addVariable("Pressure", 10.0, 80.0, 50.0);

// Add constraints
engine.addConstraint("MaxTemp", sys -> {
    Heater heater = (Heater) sys.getUnit("Heater1");
    return heater.getOutletStream().getTemperature() - 473.15; // < 200°C
});

// Run optimization
OptimizationResult result = engine.optimize();
System.out.println("Optimal production: " + (-result.getObjectiveValue()));
System.out.println("Optimal variables: " + result.getOptimalVariables());
```

### Using OptimizationBuilder (Fluent API)

```java
OptimizationResult result = OptimizationBuilder.forSystem(process)
    .minimizing(sys -> calculateOperatingCost(sys))
    .withVariable("feedRate", 500.0, 2000.0, 1000.0)
    .withVariable("pressure", 20.0, 100.0, 60.0)
    .subjectTo("maxPower", sys -> getPower(sys) - 5000.0)
    .usingBFGS()
    .withTolerance(1e-6)
    .withMaxIterations(200)
    .optimize();
```

---

## Architecture

### Class Hierarchy

```
neqsim.util.optimizer/
├── ProcessOptimizationEngine     # Main unified optimizer
├── OptimizationBuilder           # Fluent API builder
├── OptimizationResult            # Result with sensitivity analysis
│
├── algorithms/                   # Optimization algorithms
│   ├── BFGSOptimizer             # Quasi-Newton BFGS
│   ├── ArmijoWolfeLineSearch     # Line search with Wolfe conditions
│   ├── GradientDescentOptimizer  # Simple gradient descent
│   └── MultiObjectiveOptimizer   # Pareto optimization
│
├── pareto/                       # Multi-objective support
│   ├── ParetoFront               # Non-dominated solution set
│   ├── ParetoSolution            # Single Pareto point
│   └── ObjectiveWeight           # Objective weighting
│
├── strategies/                   # Equipment strategies
│   ├── EquipmentOptimizationStrategy
│   ├── CompressorCapacityStrategy
│   ├── SeparatorCapacityStrategy
│   ├── PumpCapacityStrategy
│   ├── ExpanderCapacityStrategy
│   └── EjectorCapacityStrategy
│
└── production/                   # Production optimization
    ├── FlowRateOptimizer         # Flow rate calculations
    ├── ProductionOptimizer       # Production optimization
    └── LiftCurveGenerator        # Well lift curves
```

### Plugin System

Register custom equipment strategies:

```java
// Create custom strategy
public class CustomEquipmentStrategy implements EquipmentOptimizationStrategy {
    @Override
    public String getEquipmentType() {
        return "CustomEquipment";
    }
    
    @Override
    public double evaluateCapacity(ProcessEquipmentInterface equipment,
                                   ProcessSystem system) {
        // Custom capacity evaluation
        return calculateCapacity(equipment);
    }
    
    @Override
    public void applyConstraints(ProcessOptimizationEngine engine,
                                 ProcessEquipmentInterface equipment) {
        // Add equipment-specific constraints
        engine.addConstraint("customLimit", sys -> ...);
    }
}

// Register with engine
engine.registerStrategy(new CustomEquipmentStrategy());
```

---

## Optimization Algorithms

### BFGS Optimizer

Quasi-Newton method with strong convergence properties:

```java
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
engine.setAlgorithm(OptimizationAlgorithm.BFGS);

// BFGS-specific settings
engine.setGradientTolerance(1e-8);
engine.setLineSearchMethod(LineSearchMethod.ARMIJO_WOLFE);
engine.setMaxIterations(500);

OptimizationResult result = engine.optimize();
```

### Armijo-Wolfe Line Search

Robust line search satisfying both Armijo and Wolfe conditions:

```java
ArmijoWolfeLineSearch lineSearch = new ArmijoWolfeLineSearch();
lineSearch.setC1(1e-4);  // Armijo constant
lineSearch.setC2(0.9);   // Wolfe constant
lineSearch.setMaxIterations(50);

// Use with BFGS
BFGSOptimizer bfgs = new BFGSOptimizer();
bfgs.setLineSearch(lineSearch);
```

### Gradient Descent

Simple but robust for convex problems:

```java
engine.setAlgorithm(OptimizationAlgorithm.GRADIENT_DESCENT);
engine.setLearningRate(0.01);
engine.setMomentum(0.9);
```

---

## Multi-Objective Optimization

### Weighted Sum Method

```java
MultiObjectiveOptimizer moOptimizer = new MultiObjectiveOptimizer(process);

// Define multiple objectives
moOptimizer.addObjective("Production", sys -> 
    -getProduction(sys), 0.6);  // Weight 0.6, maximize
moOptimizer.addObjective("Cost", sys -> 
    getOperatingCost(sys), 0.3);  // Weight 0.3, minimize
moOptimizer.addObjective("Emissions", sys -> 
    getCO2Emissions(sys), 0.1);  // Weight 0.1, minimize

// Generate Pareto front
ParetoFront pareto = moOptimizer.optimizeWeightedSum(20); // 20 weight combinations

// Get solutions
for (ParetoSolution solution : pareto.getSolutions()) {
    System.out.println("Production: " + solution.getObjective("Production"));
    System.out.println("Cost: " + solution.getObjective("Cost"));
    System.out.println("Variables: " + solution.getVariables());
}
```

### Epsilon-Constraint Method

```java
// Fix one objective, optimize others
ParetoFront pareto = moOptimizer.optimizeEpsilonConstraint(
    "Cost",           // Primary objective to optimize
    "Production",     // Constrained objective
    minProduction,    // Minimum production constraint
    maxProduction,    // Maximum production constraint
    10                // Number of epsilon values
);
```

### Pareto Front Analysis

```java
ParetoFront pareto = moOptimizer.optimize();

// Get extreme points
ParetoSolution minCost = pareto.getExtremePoint("Cost", false);
ParetoSolution maxProduction = pareto.getExtremePoint("Production", true);

// Get knee point (balanced solution)
ParetoSolution knee = pareto.getKneePoint();

// Export for visualization
String json = pareto.toJson();
```

---

## Sensitivity Analysis

### Automatic Sensitivity Analysis

OptimizationResult includes automatic sensitivity analysis:

```java
OptimizationResult result = engine.optimize();

// Get sensitivity report
SensitivityReport sensitivity = result.getSensitivityAnalysis();

// Most influential variables
List<VariableSensitivity> ranked = sensitivity.getRankedByInfluence();
for (VariableSensitivity vs : ranked) {
    System.out.println(vs.getName() + ": " + vs.getInfluenceScore());
}

// Constraint activity
for (ConstraintSensitivity cs : sensitivity.getConstraints()) {
    if (cs.isActive()) {
        System.out.println(cs.getName() + " is binding");
        System.out.println("Shadow price: " + cs.getShadowPrice());
    }
}
```

### Custom Sensitivity Analysis

```java
// One-at-a-time sensitivity
Map<String, double[]> oatSensitivity = engine.computeOATSensitivity(
    result.getOptimalVariables(),
    0.1  // 10% perturbation
);

// Full factorial analysis
SensitivityMatrix matrix = engine.computeFactorialSensitivity(
    new int[]{5, 5, 5}  // 5 levels per variable
);
```

---

## Flow Rate Optimization

### Eclipse Lift Curve Integration

```java
FlowRateOptimizer flowOptimizer = new FlowRateOptimizer(process);

// Set well/pipeline conditions
flowOptimizer.setInletPressure(150.0, "bara");
flowOptimizer.setOutletPressure(30.0, "bara");
flowOptimizer.setFluid(reservoirFluid);

// Calculate operating point
FlowRateResult result = flowOptimizer.calculateOperatingPoint();
System.out.println("Flow rate: " + result.getFlowRate("Sm3/day"));
System.out.println("GOR: " + result.getGOR());
System.out.println("Water cut: " + result.getWaterCut());

// Generate lift curve for Eclipse
LiftCurve curve = flowOptimizer.generateLiftCurve(
    10.0,   // Min pressure
    200.0,  // Max pressure
    20      // Number of points
);
curve.exportToEclipse("WELL_A_LIFT.DATA");
```

### Well Performance Curves

```java
// IPR curve
IPRCurve ipr = flowOptimizer.generateIPR(
    reservoirPressure,
    productivityIndex,
    IPRModel.VOGEL
);

// VLP curve  
VLPCurve vlp = flowOptimizer.generateVLP(
    tubingSize,
    wellDepth,
    VLPCorrelation.BEGGS_BRILL
);

// Find intersection (operating point)
OperatingPoint op = flowOptimizer.findOperatingPoint(ipr, vlp);
```

---

## Production Optimization

### Real-Time Optimization

```java
ProductionOptimizer prodOptimizer = new ProductionOptimizer(process);

// Configure for real-time
prodOptimizer.setMode(OptimizationMode.REAL_TIME);
prodOptimizer.setUpdateInterval(60, TimeUnit.SECONDS);

// Set production targets
prodOptimizer.setOilTarget(10000.0, "Sm3/day");
prodOptimizer.setGasConstraint(50.0, "MSm3/day");
prodOptimizer.setWaterHandlingLimit(5000.0, "Sm3/day");

// Optimize well allocation
AllocationResult allocation = prodOptimizer.optimizeWellAllocation();

for (WellSetpoint setpoint : allocation.getSetpoints()) {
    System.out.println(setpoint.getWellName() + ": " + 
        setpoint.getChoke() + "% choke, " +
        setpoint.getGasLift() + " MSm3/day GL");
}
```

### Gas Lift Optimization

```java
prodOptimizer.enableGasLiftOptimization(true);
prodOptimizer.setTotalGasLiftAvailable(2.0, "MSm3/day");

// Marginal rate allocation
GasLiftAllocation glResult = prodOptimizer.optimizeGasLift(
    GasLiftMethod.MARGINAL_RATE
);
```

---

## Advanced Features

### Constraint Handling

```java
// Equality constraint
engine.addEqualityConstraint("MassBalance", sys -> {
    double inflow = getInflow(sys);
    double outflow = getOutflow(sys);
    return inflow - outflow; // Must equal 0
}, 1e-6); // Tolerance

// Inequality constraint
engine.addConstraint("PressureLimit", sys -> {
    return getPressure(sys) - 100.0; // Must be ≤ 0
});

// Penalty method for soft constraints
engine.addSoftConstraint("Preference", sys -> ..., 
    1000.0); // Penalty weight
```

### Convergence Control

```java
engine.setConvergenceCriteria(
    ConvergenceCriteria.builder()
        .absoluteTolerance(1e-6)
        .relativeTolerance(1e-8)
        .gradientTolerance(1e-10)
        .maxIterations(1000)
        .maxFunctionEvaluations(5000)
        .build()
);

// Callback for monitoring
engine.setIterationCallback((iter, obj, vars) -> {
    System.out.println("Iteration " + iter + ": " + obj);
    return true; // Continue
});
```

### Parallel Evaluation

```java
// Enable parallel gradient evaluation
engine.setParallelEvaluation(true);
engine.setThreadCount(4);

// Parallel Pareto front generation
MultiObjectiveOptimizer mo = new MultiObjectiveOptimizer(process);
mo.setParallelFrontGeneration(true);
```

---

## Best Practices

### 1. Variable Scaling

Always scale variables to similar ranges:

```java
// Instead of:
engine.addVariable("flowRate", 100, 10000, 5000);  // Large range
engine.addVariable("pressure", 1, 5, 3);           // Small range

// Use scaling:
engine.addScaledVariable("flowRate", 100, 10000, 5000, 
    ScalingMethod.LOGARITHMIC);
engine.addScaledVariable("pressure", 1, 5, 3, 
    ScalingMethod.LINEAR);
```

### 2. Gradient Verification

Verify numerical gradients in development:

```java
engine.verifyGradients(true);  // Enable gradient checking
engine.setGradientCheckTolerance(1e-4);
```

### 3. Constraint Qualification

Ensure constraints are well-behaved:

```java
// Good: Smooth constraint
engine.addConstraint("pressure", sys -> 
    getPressure(sys) - 100.0);

// Avoid: Non-smooth constraint
engine.addConstraint("binary", sys -> 
    isActive(sys) ? 0.0 : 1.0);  // May cause convergence issues
```

### 4. Initial Point Selection

Provide good initial points:

```java
// Run process first to get feasible point
process.run();

// Extract current values as initial point
double[] initialPoint = engine.extractCurrentValues();
engine.setInitialPoint(initialPoint);
```

### 5. Multi-Start for Non-Convex Problems

```java
OptimizationResult bestResult = null;
for (int i = 0; i < 10; i++) {
    engine.setRandomInitialPoint();
    OptimizationResult result = engine.optimize();
    if (bestResult == null || 
        result.getObjectiveValue() < bestResult.getObjectiveValue()) {
        bestResult = result;
    }
}
```

---

## Integration with Adjuster

The optimizer integrates with NeqSim's Adjuster class:

```java
// Create adjuster for pressure control
Adjuster pressureControl = new Adjuster("PC-101");
pressureControl.setTargetVariable(separator, "pressure", 50.0, "bara");
pressureControl.setAdjustedVariable(feedValve, "opening");

// Add adjuster to system
process.add(pressureControl);

// Optimizer respects adjuster during optimization
engine.setRespectAdjusters(true);
```

---

## API Reference

### ProcessOptimizationEngine

| Method | Description |
|--------|-------------|
| `setObjectiveFunction(Function)` | Set objective to minimize |
| `addVariable(name, min, max, initial)` | Add optimization variable |
| `addConstraint(name, Function)` | Add inequality constraint |
| `setAlgorithm(Algorithm)` | Set optimization algorithm |
| `optimize()` | Run optimization |

### OptimizationResult

| Method | Description |
|--------|-------------|
| `getObjectiveValue()` | Final objective value |
| `getOptimalVariables()` | Optimal variable values |
| `isConverged()` | Whether optimization converged |
| `getIterationCount()` | Number of iterations |
| `getSensitivityAnalysis()` | Auto-generated sensitivity |

### MultiObjectiveOptimizer

| Method | Description |
|--------|-------------|
| `addObjective(name, Function, weight)` | Add objective |
| `optimizeWeightedSum(n)` | Generate Pareto with n points |
| `optimizeEpsilonConstraint(...)` | Epsilon-constraint method |
| `getParetoFront()` | Get resulting Pareto front |

---

## See Also

- [Process Design Framework](../process/DESIGN_FRAMEWORK.md)
- [Optimization Improvement Proposal](../process/OPTIMIZATION_IMPROVEMENT_PROPOSAL.md)
- [Production Optimization Guide](../examples/PRODUCTION_OPTIMIZATION_GUIDE.md)
- [Field Development](../fielddevelopment/API_GUIDE.md)
