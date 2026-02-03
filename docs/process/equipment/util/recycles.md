---
title: Recycles
description: Documentation for recycle handling in NeqSim process simulation.
---

# Recycles

Documentation for recycle handling in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Recycle Class](#recycle-class)
- [Convergence](#convergence)
- [Acceleration Methods](#acceleration-methods)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.util`

**Classes:**
| Class | Description |
|-------|-------------|
| `Recycle` | Main recycle handler |
| `RecycleController` | Advanced recycle control |
| `AccelerationMethod` | Convergence acceleration |
| `BroydenAccelerator` | Broyden's method acceleration |

Recycles handle iterative loops in process flowsheets where a downstream stream feeds back into an upstream unit. Common examples:
- Solvent recycle in absorption
- Reactor recycle for conversion
- Distillation reflux
- Heat integration loops

---

## Recycle Class

### Basic Usage

```java
import neqsim.process.equipment.util.Recycle;

// Create recycle
Recycle recycle = new Recycle("Solvent Recycle");

// Add the stream coming from downstream
recycle.addStream(returnStream);

// Set where the recycle feeds into
recycle.setOutletStream(feedMixer);

// Add to process
process.add(recycle);
process.run();
```

### How Recycles Work

1. **Initial Estimate**: Process runs with assumed recycle composition
2. **Calculate Downstream**: Equipment processes the estimate
3. **Update Recycle**: New recycle stream values are calculated
4. **Iterate**: Repeat until convergence

---

## Configuration

### Tolerance

```java
// Set convergence tolerance
recycle.setTolerance(1e-6);

// Separate tolerances for flow and composition
recycle.setFlowTolerance(1e-4);
recycle.setCompositionTolerance(1e-6);
recycle.setTemperatureTolerance(0.1);  // K
recycle.setPressureTolerance(0.01);    // bar
```

### Maximum Iterations

```java
// Limit iterations
recycle.setMaximumIterations(50);
```

### Damping

Damping helps prevent oscillation:

```java
// Set damping factor (0-1, lower = more damping)
recycle.setDampingFactor(0.5);  // 50% of new value, 50% of old
```

---

## Acceleration Methods

For faster convergence, acceleration methods can be used:

### Wegstein Acceleration

```java
import neqsim.process.equipment.util.AccelerationMethod;

recycle.setAccelerationMethod(AccelerationMethod.WEGSTEIN);
```

### Broyden Acceleration

```java
import neqsim.process.equipment.util.BroydenAccelerator;

BroydenAccelerator accelerator = new BroydenAccelerator();
recycle.setAccelerationMethod(accelerator);
```

### Direct Substitution

Simple successive substitution (default):

```java
recycle.setAccelerationMethod("direct");
```

---

## Usage Examples

### Simple Solvent Recycle

```java
ProcessSystem process = new ProcessSystem();

// Feed stream
Stream feed = new Stream("Feed", feedFluid);
process.add(feed);

// Mixer for feed and recycle
Mixer mixer = new Mixer("Feed Mixer");
mixer.addStream(feed);
process.add(mixer);

// Process unit (e.g., absorber)
Absorber absorber = new Absorber("TEG Contactor", mixer.getOutletStream());
process.add(absorber);

// Regeneration
Heater regenerator = new Heater("TEG Regenerator", absorber.getLiquidOutStream());
regenerator.setOutTemperature(200.0, "C");
process.add(regenerator);

// Cooler
Cooler cooler = new Cooler("TEG Cooler", regenerator.getOutletStream());
cooler.setOutTemperature(40.0, "C");
process.add(cooler);

// Recycle lean solvent back to mixer
Recycle solventRecycle = new Recycle("TEG Recycle");
solventRecycle.addStream(cooler.getOutletStream());
solventRecycle.setOutletStream(mixer);
solventRecycle.setTolerance(1e-5);
process.add(solventRecycle);

// Connect mixer to absorber with recycle
mixer.addStream(solventRecycle.getOutletStream());

// Run
process.run();

// Check convergence
if (solventRecycle.isConverged()) {
    System.out.println("Recycle converged in " + 
        solventRecycle.getIterations() + " iterations");
}
```

### Reactor Recycle

```java
// Fresh feed
Stream freshFeed = new Stream("Fresh Feed", freshFeedFluid);
process.add(freshFeed);

// Mix fresh feed with recycle
Mixer reactorFeed = new Mixer("Reactor Feed");
reactorFeed.addStream(freshFeed);
process.add(reactorFeed);

// Reactor
GibbsReactor reactor = new GibbsReactor("Synthesis Reactor");
reactor.setInletStream(reactorFeed.getOutletStream());
process.add(reactor);

// Separator
Separator productSep = new Separator("Product Separator", reactor.getOutletStream());
process.add(productSep);

// Recycle unreacted gas
Recycle gasRecycle = new Recycle("Unreacted Gas Recycle");
gasRecycle.addStream(productSep.getGasOutStream());
gasRecycle.setOutletStream(reactorFeed);
gasRecycle.setTolerance(1e-5);
gasRecycle.setDampingFactor(0.7);
process.add(gasRecycle);

reactorFeed.addStream(gasRecycle.getOutletStream());

process.run();
```

### Nested Recycles

For processes with multiple recycle loops:

```java
// Outer recycle (converges first)
Recycle outerRecycle = new Recycle("Outer Recycle");
outerRecycle.addStream(outerStream);
outerRecycle.setOutletStream(outerMixer);
outerRecycle.setPriority(1);  // Lower priority converges first
process.add(outerRecycle);

// Inner recycle (converges second)
Recycle innerRecycle = new Recycle("Inner Recycle");
innerRecycle.addStream(innerStream);
innerRecycle.setOutletStream(innerMixer);
innerRecycle.setPriority(2);  // Higher priority
process.add(innerRecycle);
```

---

## Convergence Monitoring

### Check Status

```java
// Check if converged
boolean converged = recycle.isConverged();

// Get number of iterations
int iterations = recycle.getIterations();

// Get current error
double error = recycle.getError();

System.out.println("Recycle status:");
System.out.println("  Converged: " + converged);
System.out.println("  Iterations: " + iterations);
System.out.println("  Error: " + error);
```

### Convergence History

```java
// Get convergence history for debugging
double[] errorHistory = recycle.getErrorHistory();
for (int i = 0; i < errorHistory.length; i++) {
    System.out.println("Iteration " + i + ": error = " + errorHistory[i]);
}
```

---

## Troubleshooting

### Slow Convergence

1. Reduce damping factor
2. Use acceleration method
3. Check for conflicting specifications
4. Improve initial estimate

```java
// Try Wegstein acceleration
recycle.setAccelerationMethod(AccelerationMethod.WEGSTEIN);
recycle.setDampingFactor(0.8);
```

### Oscillation

1. Increase damping
2. Reduce step size
3. Check for multiple solutions

```java
// Heavy damping for oscillating systems
recycle.setDampingFactor(0.3);
recycle.setMaximumIterations(100);
```

### Non-Convergence

1. Check physical feasibility
2. Verify mass balance closure
3. Start with simpler configuration
4. Check stream specifications

```java
// Debug mode
recycle.setVerbose(true);
process.run();
```

---

## Best Practices

1. **Tear Stream Selection**: Choose streams with least impact on downstream
2. **Good Initial Estimate**: Provide reasonable starting values
3. **Appropriate Tolerance**: Balance accuracy vs. computation time
4. **Monitor Convergence**: Check iteration count and error trends
5. **Sequential Solution**: For nested loops, converge inner loops first

---

## Related Documentation

- [Adjusters](adjusters) - Variable adjustment
- [Calculators](calculators) - Custom calculations
- [Process System](../../processmodel/README) - Process execution
