# External Optimizer Integration Guide

This guide explains how to use NeqSim's `ProcessSimulationEvaluator` to integrate process simulation with external optimization frameworks like Python's SciPy, NLopt, or other optimization libraries.

## Overview

The `ProcessSimulationEvaluator` class provides a "black box" interface that:
- Accepts a vector of decision variables
- Runs the process simulation
- Returns objective values, constraint margins, and feasibility status
- Supports gradient estimation via finite differences
- Exports problem definitions in JSON format

## Key Concepts

### Decision Variables (Parameters)
Parameters are the values the optimizer will adjust. Each parameter has:
- **Equipment name**: The name of the unit operation
- **Property name**: The property to adjust (e.g., "flowRate", "pressure")
- **Bounds**: Lower and upper limits
- **Unit**: Engineering units (for clarity)

### Objectives
Functions to minimize or maximize. By default, objectives are minimized. For maximization, the evaluator automatically negates the value.

### Constraints
Process restrictions that must be satisfied:
- **Lower bound**: g(x) ≥ bound
- **Upper bound**: g(x) ≤ bound  
- **Range**: lower ≤ g(x) ≤ upper
- **Equality**: g(x) = target ± tolerance

## Java Setup

```java
import neqsim.process.util.optimizer.ProcessSimulationEvaluator;
import neqsim.process.equipment.stream.StreamInterface;

// Create evaluator with process system
ProcessSimulationEvaluator evaluator = new ProcessSimulationEvaluator(processSystem);

// Add decision variables
evaluator.addParameter("feed", "flowRate", 1000.0, 100000.0, "kg/hr");
evaluator.addParameter("valve", "pressure", 10.0, 50.0, "bara");

// Add objective (minimize compressor power)
evaluator.addObjective("power", 
    process -> process.getUnit("compressor").getEnergy("kW"));

// Add constraints
evaluator.addConstraintLowerBound("minPressure",
    process -> ((StreamInterface) process.getUnit("outlet")).getPressure("bara"),
    30.0);

evaluator.addConstraintUpperBound("maxTemperature",
    process -> ((StreamInterface) process.getUnit("outlet")).getTemperature("C"),
    80.0);
```

## Python Integration with JPype

### Installation

```bash
pip install jpype1 scipy numpy
```

### Basic Setup

```python
import jpype
import jpype.imports
import numpy as np
from scipy.optimize import minimize, differential_evolution

# Start JVM with NeqSim
jpype.startJVM(classpath=['neqsim.jar'])

from neqsim.process.util.optimizer import ProcessSimulationEvaluator
from neqsim.process.processmodel import ProcessSystem
from neqsim.process.equipment.stream import Stream
from neqsim.process.equipment.valve import ThrottlingValve
from neqsim.thermo.system import SystemSrkEos
```

### Creating the Process

```python
# Create a simple gas processing system
fluid = SystemSrkEos(273.15 + 25.0, 50.0)
fluid.addComponent("methane", 0.9)
fluid.addComponent("ethane", 0.1)
fluid.setMixingRule("classic")
fluid.setTotalFlowRate(10000.0, "kg/hr")

feed = Stream("feed", fluid)
feed.run()

valve = ThrottlingValve("valve", feed)
valve.setOutletPressure(30.0)
valve.run()

# Build process system
process = ProcessSystem()
process.add(feed)
process.add(valve)
```

### Setting Up the Evaluator

```python
# Create evaluator
evaluator = ProcessSimulationEvaluator(process)

# Add parameters (decision variables)
evaluator.addParameter("feed", "flowRate", 1000.0, 50000.0, "kg/hr")

# Add objective
evaluator.addObjective("outletPressure",
    lambda p: p.getUnit("valve").getOutletStream().getPressure("bara"))

# Add constraints
evaluator.addConstraintLowerBound("minFlow",
    lambda p: p.getUnit("feed").getFlowRate("kg/hr"),
    5000.0)
```

### Using SciPy Optimizers

#### Gradient-Based Optimization (L-BFGS-B)

```python
def objective(x):
    """Wrapper for SciPy"""
    result = evaluator.evaluate(x)
    return result.getObjective()

def objective_with_gradient(x):
    """Objective with gradient for L-BFGS-B"""
    obj = evaluator.evaluateObjective(x)
    grad = np.array(evaluator.estimateGradient(x))
    return obj, grad

# Get bounds from evaluator
bounds = [(b[0], b[1]) for b in evaluator.getBounds()]
x0 = np.array(evaluator.getInitialValues())

# Run L-BFGS-B optimization
result = minimize(
    objective_with_gradient,
    x0,
    method='L-BFGS-B',
    jac=True,
    bounds=bounds,
    options={'maxiter': 100, 'disp': True}
)

print(f"Optimal x: {result.x}")
print(f"Optimal objective: {result.fun}")
```

#### Constrained Optimization (SLSQP)

```python
def objective(x):
    return evaluator.evaluateObjective(x)

def constraints_func(x):
    """Returns constraint margins (positive = satisfied)"""
    return np.array(evaluator.getConstraintMargins(x))

# Define constraints for SLSQP
constraints = [{
    'type': 'ineq',
    'fun': lambda x: constraints_func(x)  # All margins must be ≥ 0
}]

result = minimize(
    objective,
    x0,
    method='SLSQP',
    bounds=bounds,
    constraints=constraints,
    options={'maxiter': 100, 'disp': True}
)
```

#### Global Optimization (Differential Evolution)

```python
def penalized_objective(x):
    """For global optimizers without explicit constraints"""
    result = evaluator.evaluate(x)
    return result.getPenalizedObjective()

result = differential_evolution(
    penalized_objective,
    bounds,
    maxiter=100,
    seed=42,
    disp=True
)
```

### Multi-Objective Optimization

```python
from scipy.optimize import minimize

# Setup with multiple objectives
evaluator.addObjective("power", lambda p: p.getUnit("compressor").getEnergy("kW"))
evaluator.addObjective("throughput", 
    lambda p: p.getUnit("product").getFlowRate("kg/hr"),
    ProcessSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE)

def weighted_objective(x, weights):
    result = evaluator.evaluate(x)
    return result.getWeightedObjective(weights)

# Pareto front approximation via weighted sum
pareto_points = []
for w1 in np.linspace(0.1, 0.9, 5):
    weights = np.array([w1, 1.0 - w1])
    result = minimize(
        lambda x: weighted_objective(x, weights),
        x0,
        method='L-BFGS-B',
        bounds=bounds
    )
    pareto_points.append({
        'weights': weights,
        'x': result.x,
        'objectives': evaluator.evaluate(result.x).getObjectivesRaw()
    })
```

## Using with NLopt (Python)

```python
import nlopt
import numpy as np

def nlopt_objective(x, grad):
    """NLopt objective function"""
    if grad.size > 0:
        gradient = evaluator.estimateGradient(x)
        for i, g in enumerate(gradient):
            grad[i] = g
    return evaluator.evaluateObjective(x)

def nlopt_constraint(x, grad, idx):
    """NLopt constraint function"""
    if grad.size > 0:
        jacobian = evaluator.estimateConstraintJacobian(x)
        for i, j in enumerate(jacobian[idx]):
            grad[i] = -j  # NLopt uses g(x) ≤ 0, we return -margin
    margins = evaluator.getConstraintMargins(x)
    return -margins[idx]  # Convert to ≤ 0 form

# Create optimizer
n = evaluator.getParameterCount()
opt = nlopt.opt(nlopt.LD_SLSQP, n)

# Set bounds
opt.set_lower_bounds(evaluator.getLowerBounds())
opt.set_upper_bounds(evaluator.getUpperBounds())

# Set objective
opt.set_min_objective(nlopt_objective)

# Add constraints
for i in range(evaluator.getConstraintCount()):
    opt.add_inequality_constraint(
        lambda x, g, idx=i: nlopt_constraint(x, g, idx),
        1e-6
    )

# Optimize
opt.set_maxeval(200)
x_opt = opt.optimize(evaluator.getInitialValues())
```

## Using with Pyomo

```python
from pyomo.environ import *

def create_pyomo_model():
    """Create a Pyomo model that calls NeqSim evaluator"""
    model = ConcreteModel()
    
    # Get bounds from evaluator
    n = evaluator.getParameterCount()
    bounds_array = evaluator.getBounds()
    
    # Decision variables
    model.x = Var(range(n), 
                  bounds=lambda m, i: (bounds_array[i][0], bounds_array[i][1]))
    
    # Initialize
    x0 = evaluator.getInitialValues()
    for i in range(n):
        model.x[i] = x0[i]
    
    # External function for objective
    def obj_rule(m):
        x = [m.x[i].value for i in range(n)]
        return evaluator.evaluateObjective(x)
    
    model.obj = Objective(rule=obj_rule, sense=minimize)
    
    # External constraints (simplified approach)
    def constraint_rule(m, j):
        x = [m.x[i].value for i in range(n)]
        margins = evaluator.getConstraintMargins(x)
        return margins[j] >= 0
    
    model.constraints = Constraint(range(evaluator.getConstraintCount()), 
                                    rule=constraint_rule)
    
    return model
```

## Advanced Features

### Custom Parameter Setters

For complex parameter mappings:

```python
# Java lambda for custom setter
evaluator.addParameterWithSetter(
    "customParam",
    lambda process, value: process.getUnit("valve").setOutletPressure(value * 1.1),
    10.0, 50.0, "bara"
)
```

### Caching for Expensive Evaluations

The evaluator tracks evaluation count and can be configured for caching:

```python
# Check evaluation statistics
print(f"Total evaluations: {evaluator.getEvaluationCount()}")

# Reset counter
evaluator.resetEvaluationCount()
```

### Gradient Configuration

```python
# Configure finite difference step
evaluator.setFiniteDifferenceStep(1e-6)

# Use relative step size
evaluator.setUseRelativeStep(True)  # step = h * |x_i| + h
```

### Export Problem Definition

```python
# Get problem definition as Python dict
import json

problem_json = evaluator.toJson()
problem = json.loads(problem_json)

print("Parameters:", problem['parameters'])
print("Objectives:", problem['objectives'])
print("Constraints:", problem['constraints'])
```

### Process Cloning for Thread Safety

For parallel evaluations (e.g., with Dask or multiprocessing):

```python
# Enable process cloning for thread safety
evaluator.setCloneForEvaluation(True)
```

## Complete Example: Gas Processing Optimization

```python
import jpype
import jpype.imports
import numpy as np
from scipy.optimize import minimize
import matplotlib.pyplot as plt

# Start JVM
jpype.startJVM(classpath=['neqsim.jar'])

from neqsim.process.util.optimizer import ProcessSimulationEvaluator
from neqsim.process.processmodel import ProcessSystem
from neqsim.process.equipment.stream import Stream
from neqsim.process.equipment.compressor import Compressor
from neqsim.process.equipment.cooler import Cooler
from neqsim.thermo.system import SystemSrkEos

# Create process
fluid = SystemSrkEos(273.15 + 30.0, 20.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")
fluid.setTotalFlowRate(50000.0, "kg/hr")

feed = Stream("feed", fluid)
compressor = Compressor("compressor", feed)
compressor.setOutletPressure(80.0)
cooler = Cooler("cooler", compressor.getOutletStream())
cooler.setOutletTemperature(273.15 + 40.0)

process = ProcessSystem()
process.add(feed)
process.add(compressor)
process.add(cooler)
process.run()

# Setup optimization
evaluator = ProcessSimulationEvaluator(process)

# Decision variables
evaluator.addParameter("feed", "flowRate", 10000.0, 100000.0, "kg/hr")
evaluator.addParameter("compressor", "outletPressure", 50.0, 120.0, "bara")

# Minimize compressor power
evaluator.addObjective("power", 
    lambda p: p.getUnit("compressor").getEnergy("kW"))

# Constraints
evaluator.addConstraintLowerBound("minOutletPressure",
    lambda p: p.getUnit("cooler").getOutletStream().getPressure("bara"),
    60.0)

evaluator.addConstraintUpperBound("maxOutletTemp",
    lambda p: p.getUnit("cooler").getOutletStream().getTemperature("C"),
    50.0)

# Optimize with SLSQP
def objective(x):
    return evaluator.evaluateObjective(x)

def constraint_margins(x):
    return evaluator.getConstraintMargins(x)

bounds = [(b[0], b[1]) for b in evaluator.getBounds()]
x0 = evaluator.getInitialValues()

result = minimize(
    objective,
    x0,
    method='SLSQP',
    bounds=bounds,
    constraints={'type': 'ineq', 'fun': constraint_margins},
    options={'maxiter': 100, 'disp': True}
)

# Display results
print("\n=== Optimization Results ===")
print(f"Optimal flow rate: {result.x[0]:.1f} kg/hr")
print(f"Optimal outlet pressure: {result.x[1]:.1f} bara")
print(f"Minimum power: {result.fun:.1f} kW")
print(f"Constraint margins: {constraint_margins(result.x)}")
print(f"Total evaluations: {evaluator.getEvaluationCount()}")

jpype.shutdownJVM()
```

## Troubleshooting

### Common Issues

1. **Simulation doesn't converge**: Check that parameter bounds are physically reasonable
2. **Gradient estimation fails**: Try larger finite difference step
3. **Slow evaluations**: Enable caching or reduce process complexity
4. **Thread safety errors**: Enable `setCloneForEvaluation(True)`

### Performance Tips

1. Start with fewer parameters and add more iteratively
2. Use warm starts from previous solutions
3. For global optimization, use differential evolution first, then polish with L-BFGS-B
4. Profile with `evaluator.getEvaluationCount()` to identify bottlenecks

## API Reference

### ProcessSimulationEvaluator

| Method | Description |
|--------|-------------|
| `evaluate(double[] x)` | Full evaluation returning EvaluationResult |
| `evaluateObjective(double[] x)` | Quick objective-only evaluation |
| `evaluatePenalizedObjective(double[] x)` | Objective + constraint penalties |
| `isFeasible(double[] x)` | Check constraint satisfaction |
| `getConstraintMargins(double[] x)` | Get constraint slack values |
| `estimateGradient(double[] x)` | Finite-difference gradient |
| `estimateConstraintJacobian(double[] x)` | Constraint Jacobian matrix |
| `getBounds()` | Get parameter bounds array |
| `getLowerBounds()` | Get lower bounds vector |
| `getUpperBounds()` | Get upper bounds vector |
| `getInitialValues()` | Get initial parameter values |
| `toJson()` | Export problem definition |

### EvaluationResult

| Method | Description |
|--------|-------------|
| `getObjective()` | Primary objective value |
| `getObjectives()` | All objective values (transformed) |
| `getObjectivesRaw()` | Raw objective values |
| `getPenalizedObjective()` | Objective + penalty |
| `getWeightedObjective(weights)` | Weighted sum of objectives |
| `getConstraintMargins()` | Constraint slack values |
| `isFeasible()` | All constraints satisfied? |
| `isSimulationConverged()` | Process simulation converged? |
| `getEvaluationNumber()` | Sequential evaluation number |
| `getAdditionalOutputs()` | Custom output values |

## See Also

- [OPTIMIZER_PLUGIN_ARCHITECTURE.md](../process/OPTIMIZER_PLUGIN_ARCHITECTURE.md) - Plugin architecture for equipment-specific optimization
- [ProcessOptimizationEngine](../process/optimization-engine.md) - Built-in optimization algorithms
- [FlowRateOptimizer](../process/flow-rate-optimizer.md) - Specialized pipeline optimizer
