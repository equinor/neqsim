# Recycle Convergence Acceleration Guide

This guide explains the recycle system in NeqSim, the available convergence acceleration methods, and best practices for optimizing process simulations.

## Table of Contents

1. [Overview](#overview)
2. [Understanding Recycles](#understanding-recycles)
3. [Acceleration Methods](#acceleration-methods)
4. [Usage Examples](#usage-examples)
5. [RecycleController for Multiple Recycles](#recyclecontroller-for-multiple-recycles)
6. [Performance Benchmarks](#performance-benchmarks)
7. [Troubleshooting](#troubleshooting)
8. [Best Practices](#best-practices)

---

## Overview

Process simulations often contain **recycle loops** where output streams from downstream equipment feed back into upstream units. These loops require iterative solving because the downstream conditions depend on upstream calculations, which in turn depend on the recycle stream values.

NeqSim provides three convergence acceleration methods to speed up recycle convergence:

| Method | Best For | Complexity |
|--------|----------|------------|
| **Direct Substitution** | Simple, well-behaved recycles | O(1) |
| **Wegstein** | Oscillating or slow-converging recycles | O(1) |
| **Broyden** | Tightly coupled multi-variable systems | O(n²) |

---

## Understanding Recycles

### What is a Recycle?

A `Recycle` unit in NeqSim connects an output stream to an input stream, creating a feedback loop. The recycle iterates until the difference between input and output falls below specified tolerances.

```
┌─────────────────────────────────────────────────────┐
│                                                     │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐         │
│  │  Feed   │───▶│  Unit A │───▶│  Unit B │───┐     │
│  └─────────┘    └─────────┘    └─────────┘   │     │
│       ▲                                       │     │
│       │         ┌─────────┐                   │     │
│       └─────────│ Recycle │◀──────────────────┘     │
│                 └─────────┘                         │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### Convergence Criteria

The recycle checks convergence on four properties:

1. **Temperature** - Default tolerance: 1.0 K
2. **Pressure** - Default tolerance: 0.01 bar
3. **Flow rate** - Default tolerance: 1.0 kg/hr
4. **Composition** - Default tolerance: 1e-3 (mole fraction)

### Basic Recycle Setup

```java
// Create inlet and outlet streams
Stream recycleInlet = new Stream("recycle inlet", fluid);
recycleInlet.setFlowRate(100.0, "kg/hr");

// ... add process equipment ...

// Create recycle connecting outlet back to inlet
Recycle recycle = new Recycle("main recycle");
recycle.addStream(downstreamOutput);  // Output from process
recycle.setOutletStream(recycleInlet); // Connects to inlet
recycle.setTolerance(1e-2);            // Overall tolerance

// Add to process system
process.add(recycle);
```

---

## Acceleration Methods

### 1. Direct Substitution (Default)

**Algorithm**: Simply uses the output values as the next input.

$$x_{n+1} = g(x_n)$$

**Characteristics**:
- Simplest method
- Always stable for contractive mappings
- May be slow for difficult problems
- No additional memory or computation

**When to Use**:
- Default choice for most problems
- When recycles converge quickly (< 10 iterations)
- When stability is more important than speed

```java
recycle.setAccelerationMethod(AccelerationMethod.DIRECT_SUBSTITUTION);
```

### 2. Wegstein Acceleration

**Algorithm**: Extrapolates based on the slope between consecutive iterations.

$$x_{n+1} = q \cdot g(x_n) + (1-q) \cdot x_n$$

where the q-factor is calculated from the slope:

$$q = \frac{s}{s-1}, \quad s = \frac{g(x_n) - g(x_{n-1})}{x_n - x_{n-1}}$$

**Bounded q-factor**: NeqSim bounds q ∈ [-5, 0] to prevent divergence:
- q = 0: Pure direct substitution
- q < 0: Damping for oscillatory behavior
- q = -5: Maximum damping

**Characteristics**:
- Low overhead (O(1) per variable)
- Excellent for single-variable problems
- Adaptive damping prevents oscillation
- Each variable accelerated independently

**When to Use**:
- Recycles that oscillate with direct substitution
- Single dominant variable controlling convergence
- When you need more stability than direct substitution

```java
recycle.setAccelerationMethod(AccelerationMethod.WEGSTEIN);

// Optional: Tune the q-factor bounds
recycle.setWegsteinQMin(-5.0);  // More damping
recycle.setWegsteinQMax(0.0);   // Maximum q (direct substitution)
```

### 3. Broyden's Method

**Algorithm**: Quasi-Newton method that builds up an approximation of the inverse Jacobian.

$$x_{n+1} = x_n - B_n^{-1} \cdot F(x_n)$$

where $F(x) = g(x) - x$ is the residual function and $B_n^{-1}$ is updated using the Sherman-Morrison formula:

$$B_{n+1}^{-1} = B_n^{-1} + \frac{(\Delta x - B_n^{-1} \Delta F) \Delta x^T B_n^{-1}}{\Delta x^T B_n^{-1} \Delta F}$$

**Characteristics**:
- Higher overhead (O(n²) for n variables)
- Excellent for coupled multi-variable systems
- Superlinear convergence near solution
- Requires storing previous iteration data

**When to Use**:
- Multiple tightly coupled recycles
- When Wegstein doesn't improve convergence
- Complex processes with many interacting variables

```java
recycle.setAccelerationMethod(AccelerationMethod.BROYDEN);
```

---

## Usage Examples

### Example 1: Simple Compression Loop

A gas compression system with intercooling and recycle:

```java
SystemInterface gas = new SystemSrkEos(298.15, 10.0);
gas.addComponent("methane", 0.9);
gas.addComponent("ethane", 0.1);
gas.setMixingRule("classic");

// Feed stream
Stream feed = new Stream("feed", gas);
feed.setFlowRate(1000.0, "kg/hr");

// Recycle inlet (estimate)
Stream recycleInlet = feed.clone("recycle inlet");
recycleInlet.setFlowRate(50.0, "kg/hr");

// Mix feed with recycle
Mixer mixer = new Mixer("inlet mixer");
mixer.addStream(feed);
mixer.addStream(recycleInlet);

// Compressor
Compressor comp = new Compressor("compressor", mixer.getOutletStream());
comp.setOutletPressure(50.0, "bara");

// Cooler
Cooler cooler = new Cooler("intercooler", comp.getOutletStream());
cooler.setOutTemperature(30.0, "C");

// Separator
Separator sep = new Separator("separator", cooler.getOutletStream());

// Recycle liquid back to inlet
Recycle recycle = new Recycle("liquid recycle");
recycle.addStream(sep.getLiquidOutStream());
recycle.setOutletStream(recycleInlet);
recycle.setTolerance(1e-3);
recycle.setAccelerationMethod(AccelerationMethod.WEGSTEIN);  // Use Wegstein

// Build process
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(recycleInlet);
process.add(mixer);
process.add(comp);
process.add(cooler);
process.add(sep);
process.add(recycle);

process.run();

System.out.println("Converged in " + recycle.getIterations() + " iterations");
```

### Example 2: Multi-Stage Separation with Multiple Recycles

```java
// Create process with multiple recycles
ProcessSystem process = new ProcessSystem();

// ... set up 3-stage separation train ...

// HP recycle with Broyden (coupled with MP recycle)
Recycle hpRecycle = new Recycle("HP recycle");
hpRecycle.addStream(hpSeparator.getLiquidOutStream());
hpRecycle.setOutletStream(hpRecycleInlet);
hpRecycle.setTolerance(1e-2);
hpRecycle.setAccelerationMethod(AccelerationMethod.BROYDEN);
hpRecycle.setPriority(100);  // Run first
process.add(hpRecycle);

// MP recycle with Broyden
Recycle mpRecycle = new Recycle("MP recycle");
mpRecycle.addStream(mpSeparator.getLiquidOutStream());
mpRecycle.setOutletStream(mpRecycleInlet);
mpRecycle.setTolerance(1e-2);
mpRecycle.setAccelerationMethod(AccelerationMethod.BROYDEN);
mpRecycle.setPriority(200);  // Run second
process.add(mpRecycle);

process.run();
```

### Example 3: Using RecycleController

For coordinated control of multiple recycles:

```java
// Create recycle controller
RecycleController controller = new RecycleController();

// Add recycles with priorities
controller.addRecycle(hpRecycle, 1);  // Priority 1 (highest)
controller.addRecycle(mpRecycle, 2);  // Priority 2
controller.addRecycle(lpRecycle, 3);  // Priority 3

// Set acceleration method for all recycles
controller.setAccelerationMethod(AccelerationMethod.BROYDEN);

// Configure controller
controller.setMaxIterations(50);
controller.setGlobalTolerance(1e-3);

// Run coordinated convergence
controller.converge();
```

---

## RecycleController for Multiple Recycles

The `RecycleController` class provides coordinated management of multiple recycle loops.

### Features

- **Priority-based sequencing**: Converge recycles in order of importance
- **Unified acceleration**: Apply the same method to all recycles
- **Global convergence tracking**: Monitor overall system convergence
- **Nested loop handling**: Properly handle recycles within recycles
- **Simultaneous modular solving**: Accelerate multiple recycles together using shared Broyden updates
- **Coordinated acceleration**: Treat tear streams at the same priority level as a coupled system

### Simultaneous Modular Solving

When multiple recycles operate at the same priority level, the controller can accelerate them *simultaneously* using a shared Broyden accelerator. This treats all tear stream variables as a single coupled system, which can dramatically improve convergence for tightly interacting recycles.

```java
RecycleController controller = new RecycleController();
controller.addRecycle(recycle1, 100);  // Same priority
controller.addRecycle(recycle2, 100);  // Same priority - will be accelerated together

// Enable coordinated acceleration (default: true)
controller.setUseCoordinatedAcceleration(true);

// Run simultaneous acceleration for all recycles at this priority
boolean converged = controller.runSimultaneousAcceleration(100, 1e-4, 50);
```

### Convergence Diagnostics

The controller provides detailed diagnostics for troubleshooting:

```java
// Get formatted diagnostic report
System.out.println(controller.getConvergenceDiagnostics());

// Output:
// RecycleController Diagnostics:
//   Total recycles: 2
//   Current priority level: 100
//   Using coordinated acceleration: true
//   Recycles at current priority: 2
//     - Recycle1 [iterations=4, solved=true, errComp=1.2e-05, errFlow=3.5e-06]
//     - Recycle2 [iterations=9, solved=true, errComp=0.0e+00, errFlow=4.1e-06]

// Query aggregate metrics
int totalIters = controller.getTotalIterations();
double maxError = controller.getMaxResidualError();
```

### API Reference

```java
RecycleController controller = new RecycleController();

// Add recycles
controller.addRecycle(recycle, priority);  // Lower priority = converged first
controller.removeRecycle(recycle);

// Configure
controller.setAccelerationMethod(AccelerationMethod.WEGSTEIN);
controller.setMaxIterations(100);
controller.setGlobalTolerance(1e-4);
controller.setUseCoordinatedAcceleration(true);  // Enable simultaneous solving

// Execute
controller.converge();

// Simultaneous acceleration for a specific priority level
boolean converged = controller.runSimultaneousAcceleration(priorityLevel, tolerance, maxIter);

// Query status
boolean converged = controller.isConverged();
int totalIterations = controller.getTotalIterations();
double maxError = controller.getMaxResidualError();
String diagnostics = controller.getConvergenceDiagnostics();
List<Recycle> unconverged = controller.getUnconvergedRecycles();

// Reset for re-running
controller.resetAll();
```

---

## Performance Benchmarks

Benchmarks on a 3-stage separation train with 2 liquid recycles (~20 process units):

| Method | Average Time | Iterations | Speedup |
|--------|-------------|------------|---------|
| Direct Substitution | 147 ms | 6 | 1.00x (baseline) |
| Wegstein | 125 ms | 6 | **1.18x** |
| Broyden | 112 ms | 6 | **1.31x** |

### Observations

1. **All methods converge in the same iterations** for well-conditioned problems
2. **Acceleration methods reduce error faster per iteration**
3. **Broyden performs best** for coupled multi-variable systems
4. **Wegstein is simpler** with lower overhead

### When Acceleration Helps Most

- Processes requiring many iterations (>10)
- Oscillating or slow-converging recycles
- Tightly coupled multi-recycle systems
- Large-scale simulations where time matters

---

## Troubleshooting

### Problem: Recycle doesn't converge

**Symptoms**: Maximum iterations reached, large residual errors

**Solutions**:
1. Increase `maxIterations`
2. Loosen tolerance with `setTolerance()`
3. Try `WEGSTEIN` for damping
4. Check initial estimates are reasonable
5. Verify process is physically feasible

```java
recycle.setMaxIterations(200);
recycle.setTolerance(1e-2);
recycle.setAccelerationMethod(AccelerationMethod.WEGSTEIN);
```

### Problem: Recycle oscillates

**Symptoms**: Error bounces between values, never settles

**Solutions**:
1. Use `WEGSTEIN` method (provides damping)
2. Reduce Wegstein qMax toward 0
3. Check for competing recycles

```java
recycle.setAccelerationMethod(AccelerationMethod.WEGSTEIN);
recycle.setWegsteinQMin(-10.0);  // Stronger damping
recycle.setWegsteinQMax(-0.5);   // Never use direct substitution
```

### Problem: Broyden diverges

**Symptoms**: Error grows exponentially with Broyden

**Solutions**:
1. Fall back to `WEGSTEIN` or `DIRECT_SUBSTITUTION`
2. Improve initial estimates
3. The problem may be ill-conditioned

### Problem: Slow convergence

**Symptoms**: Many iterations required

**Solutions**:
1. Try `BROYDEN` for coupled systems
2. Improve initial stream estimates
3. Consider process restructuring

---

## Best Practices

### 1. Start Simple

Begin with `DIRECT_SUBSTITUTION` (the default). Only switch to acceleration methods if:
- Convergence is too slow
- Recycle oscillates
- You have coupled multi-recycle systems

### 2. Set Appropriate Tolerances

```java
// Tight tolerance for final design
recycle.setTolerance(1e-4);
recycle.setFlowTolerance(0.01, "kg/hr");
recycle.setTemperatureTolerance(0.1);  // K

// Loose tolerance for initial exploration
recycle.setTolerance(1e-2);
```

### 3. Use Priorities for Multiple Recycles

```java
// Outer recycle converges first (lower number = higher priority)
outerRecycle.setPriority(100);

// Inner recycle converges second
innerRecycle.setPriority(200);
```

### 4. Provide Good Initial Estimates

The closer your initial recycle stream is to the solution, the faster convergence:

```java
// Estimate based on expected recycle ratio
Stream recycleEstimate = feed.clone("recycle estimate");
recycleEstimate.setFlowRate(feed.getFlowRate("kg/hr") * 0.1, "kg/hr");  // ~10% recycle
```

### 5. Monitor Convergence

```java
process.run();

for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
    if (unit instanceof Recycle) {
        Recycle r = (Recycle) unit;
        System.out.println(r.getName() + ": " + r.getIterations() + " iterations, " +
                          "converged=" + r.solved());
    }
}
```

### 6. Method Selection Guide

```
START
  │
  ▼
┌─────────────────────────────┐
│ Does direct substitution    │
│ converge in < 10 iterations?│
└─────────────────────────────┘
        │
   YES  │  NO
        │   │
        ▼   ▼
     DONE  ┌─────────────────────────────┐
           │ Is the recycle oscillating? │
           └─────────────────────────────┘
                   │
              YES  │  NO
                   │   │
                   ▼   ▼
            WEGSTEIN  ┌─────────────────────────────┐
                      │ Are there multiple coupled  │
                      │ recycles?                   │
                      └─────────────────────────────┘
                              │
                         YES  │  NO
                              │   │
                              ▼   ▼
                         BROYDEN  WEGSTEIN
```

---

## Free Sensitivity Analysis from Convergence

One powerful advantage of using the Broyden method is that the **inverse Jacobian computed during convergence can be reused for sensitivity analysis** at no additional computational cost.

### How It Works

During Broyden convergence, the accelerator builds an approximation of the inverse Jacobian matrix $B^{-1}$ where:

$$B \approx I - \frac{\partial g}{\partial x}$$

This matrix relates input perturbations to output changes for the tear stream variables. After convergence, you can extract this for free:

```java
// After running process with coordinated acceleration
RecycleController controller = process.getRecycleController();

if (controller.hasSensitivityData()) {
    // Get as SensitivityMatrix for named access
    SensitivityMatrix sensMatrix = controller.getTearStreamSensitivityMatrix();
    
    // Query individual sensitivities
    double dT_dP = sensMatrix.getSensitivity(
        "recycle1.temperature", 
        "recycle1.pressure"
    );
    
    // Or get raw Jacobian for matrix operations
    double[][] jacobian = controller.getConvergenceJacobian();
    
    // See variable names
    List<String> varNames = controller.getTearStreamVariableNames();
    // Returns: ["recycle1.temperature", "recycle1.pressure", "recycle1.flowRate", ...]
}
```

### Comparison with Finite Differences

| Method | Cost | Accuracy | Availability |
|--------|------|----------|--------------|
| **Broyden Jacobian** | Free (0 extra runs) | Approximate | After Broyden convergence |
| **Finite Differences** | 2n extra simulations | Central differences | Always |
| **Monte Carlo** | N samples × n runs | Statistical | Always |

For tear stream variables, the Broyden Jacobian provides **instant sensitivity estimates** without any additional simulations.

### Use Cases

1. **Uncertainty Propagation**: How inlet uncertainties affect recycle convergence
2. **Control Analysis**: Which variables most strongly affect others
3. **Design Sensitivity**: Impact of design parameters on recycle conditions
4. **Model Validation**: Compare against finite difference results

### General Sensitivity Analysis

For sensitivities beyond tear stream variables, use the `ProcessSensitivityAnalyzer`:

```java
ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(process);

SensitivityMatrix result = analyzer
    .withInput("feed", "temperature")
    .withInput("feed", "flowRate", "kg/hr")
    .withOutput("product", "temperature")
    .compute();  // Uses Broyden Jacobian when possible, else FD

String report = analyzer.generateReport(result);
```

See [Graph-Based Process Simulation - Process Sensitivity Analysis](graph_based_process_simulation.md#process-sensitivity-analysis) for full documentation.

---

## API Reference


### Recycle Class

```java
// Acceleration method
void setAccelerationMethod(AccelerationMethod method)
AccelerationMethod getAccelerationMethod()

// Wegstein parameters
void setWegsteinQMin(double qMin)  // Default: -5.0
void setWegsteinQMax(double qMax)  // Default: 0.0
double getWegsteinQMin()
double getWegsteinQMax()

// Tolerances
void setTolerance(double tolerance)
void setFlowTolerance(double tol, String unit)
void setTemperatureTolerance(double tol)
void setCompositionTolerance(double tol)

// Iteration control
void setMaxIterations(int max)
int getIterations()
boolean solved()

// Priority for multi-recycle coordination
void setPriority(int priority)
int getPriority()
```

### AccelerationMethod Enum

```java
public enum AccelerationMethod {
    DIRECT_SUBSTITUTION,  // Simple successive substitution
    WEGSTEIN,             // Wegstein acceleration with bounded q
    BROYDEN               // Broyden's quasi-Newton method
}
```

### RecycleController Class

```java
// Setup
void addRecycle(Recycle recycle)
void setUseCoordinatedAcceleration(boolean use)
void init()

// Running
void runCurrentPriorityLevel()
void runSimultaneousAcceleration()
void runAllPriorityLevels()

// Diagnostics
int getRecycleCount()
List<Recycle> getRecyclesAtCurrentPriority()
String getConvergenceDiagnostics()

// Sensitivity analysis (FREE from Broyden convergence)
boolean hasSensitivityData()
SensitivityMatrix getTearStreamSensitivityMatrix()
double[][] getConvergenceJacobian()
List<String> getTearStreamVariableNames()
```

---

## References

1. Wegstein, J.H. (1958). "Accelerating convergence of iterative processes". *Communications of the ACM*, 1(6), 9-13.

2. Broyden, C.G. (1965). "A class of methods for solving nonlinear simultaneous equations". *Mathematics of Computation*, 19(92), 577-593.

3. Seader, J.D., Henley, E.J., & Roper, D.K. (2011). *Separation Process Principles*. Wiley. Chapter on sequential modular simulation.

---

## See Also

- [Graph-Based Process Simulation](graph_based_process_simulation.md) - Detailed guide on graph algorithms and sensitivity analysis
- 📓 [GraphBasedProcessSimulation.ipynb](../examples/GraphBasedProcessSimulation.ipynb) - Interactive Jupyter notebook example

---

*Last updated: December 2025*
