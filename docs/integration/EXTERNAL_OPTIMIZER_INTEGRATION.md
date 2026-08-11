---
title: External Optimizer Integration Guide
description: This guide explains how to use NeqSim's simulation evaluators to integrate process simulations and multi-area process models with external optimization frameworks like Python's SciPy, NLopt, or other optimization libraries.
---

> **New to process optimization?** Start with the [Optimization Overview](../process/optimization/OPTIMIZATION_OVERVIEW.md) to understand when to use which optimizer.

This guide explains how to use NeqSim's simulation evaluators to integrate process simulation with external optimization frameworks like Python's SciPy, NLopt, or other optimization libraries.

## Related Documentation

| Document | Description |
|----------|-------------|
| [Optimization Overview](../process/optimization/OPTIMIZATION_OVERVIEW.md) | When to use which optimizer |
| [Production Optimization Guide](../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) | ProductionOptimizer examples |
| [Practical Examples](../process/optimization/PRACTICAL_EXAMPLES.md) | Code samples |
| [Capacity Constraint Framework](../process/CAPACITY_CONSTRAINT_FRAMEWORK.md) | Installed equipment limits and bottleneck detection |

## Overview

NeqSim provides two black-box evaluator classes for external optimizers and one convenience helper for the common full-facility producer-ramp workflow:

| Class | Model boundary | Decision variable addressing | Best use |
|-------|----------------|------------------------------|----------|
| `ProcessSimulationEvaluator` | One `ProcessSystem` | Unit name plus property name | Compact flowsheets, equipment tuning, single-process optimization |
| `ProcessModelSimulationEvaluator` | Full `ProcessModel` with named areas | Area-qualified `ProcessAutomation` addresses such as `wells::feed.flowRate` | Large facilities, multi-producer throughput studies, fixed-equipment bottleneck workflows |
| `ProcessModelThroughputOptimizer` | Full `ProcessModel` with named areas | Producer mappings plus installed capacity CSV tables | Increase producers until the full facility reaches its first fixed-equipment bottleneck |

The evaluator classes provide a black-box interface that:
- Accepts a vector of decision variables
- Runs the process simulation
- Returns objective values, constraint margins, and feasibility status
- Supports gradient estimation via finite differences
- Exports problem definitions in JSON format

Use `ProcessSimulationEvaluator` when all manipulated variables and outputs belong to one process system. Use `ProcessModelSimulationEvaluator` when the optimization must run the complete plant model and evaluate constraints across several process areas. Use `ProcessModelThroughputOptimizer` when the practical question is a scalar producer ramp: find the maximum feasible throughput and report the active bottleneck case table.

### Full ProcessModel Evaluations

`ProcessModelSimulationEvaluator` is intended for optimization studies where the process has already been split into named `ProcessSystem` areas and composed into a `ProcessModel`. This matches large offshore or gas-plant models where wells, separation, compression, export, utility, and recycle areas are solved together.

The practical workflow is:

1. Build and validate the full `ProcessModel` at the base case.
2. Attach installed `CapacityConstraint` objects to equipment with fixed design sizes.
3. Register producer/feed rates, pressure setpoints, or scenario multipliers as decision variables.
4. Add model-level objective functions, for example export gas flow or total compressor power.
5. Call `addEquipmentCapacityConstraints()` to include enabled equipment limits as hard constraints.
6. Pass `getBounds()`, `getInitialValues()`, `evaluate(...)`, `evaluatePenalizedObjective(...)`, or `estimateGradient(...)` to the external optimizer.
7. Inspect `EvaluationResult.getConstraintMargins()` and `EvaluationResult.getActiveBottleneck()` for engineering interpretation.

For full-model studies, area-qualified addresses keep optimizer scripts independent of Java object wiring. Examples include `wells::feed.flowRate`, `separation::separator.gasOutStream.flowRate`, and `compression::export compressor.outletPressure`.

Capacity constraints remain explicit engineering data. Strategy-generated defaults can identify candidate bottlenecks, while installed equipment limits should be attached directly to equipment when the study assumes equipment sizes are fixed.

### Full-Facility Throughput-to-Bottleneck Helper

`ProcessModelThroughputOptimizer` wraps the evaluator for Chapter-15-style facility studies. It maps producers, optionally loads installed equipment limits from a CSV table, performs a robust scalar throughput search, and returns a `ProcessModelThroughputResult` containing the best feasible case, first infeasible case, and all evaluated case rows.

```java
ProcessModelThroughputOptimizer optimizer = new ProcessModelThroughputOptimizer(model);
optimizer.addProducer("feed", "wells::feed.flowRate", 1.0, 2.0, "kg/hr");
optimizer.setObjective("exportGas", new ToDoubleFunction<ProcessModel>() {
    @Override
    public double applyAsDouble(ProcessModel processModel) {
        return processModel.getVariableValue("separation::separator.gasOutStream.flowRate", "kg/hr");
    }
}, "kg/hr");
optimizer.loadInstalledCapacities("installed_capacity.csv");

ProcessModelThroughputResult result = optimizer.findMaximumThroughput(1.0, 2.0, 0.01);
ThroughputCaseRow best = result.getBestFeasibleCase();
ThroughputCaseRow firstLimit = result.getFirstInfeasibleCase();
result.exportToCSV("throughput_trace.csv");
```

The installed-capacity CSV format is intentionally small and auditable:

```text
area,equipment,constraint,currentValueAddress,designValue,maxValue,unit,severity,enabled
separation,separator,installedGasCapacity,wells::feed.flowRate,15000,16500,kg/hr,HARD,true
```

By default, the helper uses explicit installed capacity limits attached directly to equipment. Enable strategy-generated constraints with `setIncludeStrategyCapacityConstraints(true)` when you want generic screening limits to participate in addition to installed design data.

Each throughput row records `minimumConstraint` so engineering-unit margins remain unambiguous.
For a maximum-directed limit, `capacityMargin = limit - current`; for a minimum-directed limit,
`capacityMargin = current - limit`. A non-negative margin is therefore feasible in both cases, and
the reported `designValue` is the applicable finite limit even when the equipment constraint was
constructed with `setMinValue(...)` only. Each row also carries `dataSource`, copied from the
underlying `CapacityConstraint`, so Python and external optimizers can distinguish an installed
data-sheet limit from mechanical-design output, an operating envelope, or an untagged `not_set`
limit. Preserve this provenance in ranked recommendations and result archives.

Evidence metadata is also snapshotted from the active `CapacityConstraint`. Java rows expose
`hasConfidence()`, `getConfidence()`, `hasValidityRange()`, `getValidityMinimum()`,
`getValidityMaximum()`, and `isCurrentValueWithinValidityRange()`. JSON represents unset
numeric and applicability values as `null`; CSV includes the presence flags and leaves unset
value cells blank. External and AI optimizers should preserve these diagnostics, require
engineering review for missing or out-of-range evidence, and must not treat confidence as a
probability of safety. The fields do not change feasibility or ranking automatically.

`utilizationMargin` remains `1 - utilization` for both directions.

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

For both `ProcessSimulationEvaluator` and `ProcessModelSimulationEvaluator`, one completed
`evaluate(...)` call samples each objective and constraint callback exactly once after the
simulation. Raw and sign-adjusted objectives, plus constraint values, margins, feasibility, and
penalties, are derived from those same scalar samples. This matters when result extraction includes
costly reporting or serialization and prevents inconsistent fields when a callback reads mutable
diagnostics. Callbacks should nevertheless remain side-effect free.

## Converting Constraints to the Internal Optimizer

Use the plural conversion when a `ConstraintDefinition` is passed to `ProductionOptimizer`:

```java
List<ProductionOptimizer.OptimizationConstraint> internalConstraints =
    externalConstraint.toOptimizationConstraints();
```

A lower or upper bound produces one immutable-list element. A range produces `name_lower` and
`name_upper`; an equality target produces the equivalent `target - tolerance` lower side and
`target + tolerance` upper side. Each generated side keeps the source evaluator, hard/soft
severity, and penalty weight.

The singular `toOptimizationConstraint()` method remains available for compatibility. It is
lossless only for one-sided constraints; for range and equality types it retains the historical
upper-side-only behavior. Do not use that method for operating envelopes, product-quality bands,
equipment turndown ranges, or market-nomination tolerances. Treat lower- and upper-side
sensitivities separately because only one side can normally be active at a given operating point.

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

## Python integration through neqsim-python

### Installation

```bash
pip install neqsim scipy numpy
```

### Basic setup

The public `neqsim` package starts and configures the Java gateway. Do not start a second JVM or
assume that a local NeqSim JAR file exists.

```python
import numpy as np
from scipy.optimize import differential_evolution, minimize
from neqsim import jneqsim

ProcessSimulationEvaluator = (
    jneqsim.process.util.optimizer.ProcessSimulationEvaluator
)
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
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

An ordinary Pyomo `Objective(rule=...)` or `Constraint(rule=...)` must build a symbolic
expression. Reading `.value` from Pyomo variables inside those rules and immediately calling
NeqSim evaluates only the construction-time values; it does not create a live connection that
Pyomo can differentiate or re-evaluate while solving.

NeqSim does not currently provide a maintained Pyomo `ExternalFunction` or PyNumero callback
adapter. Use the SciPy or NLopt black-box patterns above, or implement and validate a dedicated
Pyomo external-function bridge with explicit value, gradient, lifecycle, and failure handling.
Do not present a construction-time numeric callback as a Pyomo optimization model.

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

### Evaluation accounting

`ProcessSimulationEvaluator` counts evaluation attempts:

```python
print(f"Total evaluations: {evaluator.getEvaluationCount()}")
evaluator.resetEvaluationCount()
```

These methods measure work; they do not enable result caching. Add caching in the external
optimizer only when the complete parameter vector, model identity, operating case, and mutable
process state are part of a safe cache key.

### Gradient Configuration

```python
# Configure finite difference step
evaluator.setFiniteDifferenceStep(1e-6)

# Use relative step size
evaluator.setUseRelativeStep(True)  # step = h * max(|x_i|, 1)

# Optional second-order stencil for smooth interior operating points
FiniteDifferenceMethod = jneqsim.process.util.optimizer.ProcessModelSimulationEvaluator.FiniteDifferenceMethod
evaluator.setFiniteDifferenceMethod(FiniteDifferenceMethod.CENTRAL)
```

`ProcessModelSimulationEvaluator` keeps `FORWARD` as the default because it requires only one
perturbed simulation per parameter. Both objective gradients and constraint Jacobians use the
actual perturbation remaining inside each parameter's bounds; they no longer divide by a requested
step that was partly removed by bound clamping. `CENTRAL` uses symmetric in-bounds points when both
directions are available and falls back to a one-sided difference at an active bound. A fixed
parameter has zero derivative because it has no feasible perturbation direction. Treat either
finite-difference result as a local sensitivity, not as an optimizer-independent shadow price, and
check step-size stability before using it to rank debottlenecking value.

For a reusable quality record, run the coarse step and one halved step through the combined
objective/constraint API:

```python
quality_result = evaluator.estimateSensitivitiesWithQuality(x)
gradient = quality_result.getObjectiveGradient()
jacobian = quality_result.getConstraintJacobian()

objective = quality_result.getObjectiveSnapshot()
print(
    objective.getName(),
    objective.getDirection(),
    objective.getUnit(),
    objective.getBaseRawValue(),
    objective.getGradient(),
)

for parameter in quality_result.getParameterSnapshots():
    print(
        parameter.getIndex(),
        parameter.getName(),
        parameter.getAddress(),
        parameter.getUnit(),
        parameter.getBaseValue(),
    )

for constraint in quality_result.getConstraintSnapshots():
    print(
        constraint.getIndex(),
        constraint.getName(),
        constraint.getType(),
        constraint.getUnit(),
        constraint.getBaseMargin(),
        constraint.getMarginGradient(),
        constraint.getAreaName(),
        constraint.getEquipmentName(),
    )

for parameter_quality in quality_result.getParameterQuality():
    print(
        parameter_quality.getParameterName(),
        parameter_quality.getStencil(),
        parameter_quality.getCoarseStep(),
        parameter_quality.getFineStep(),
        parameter_quality.getMaximumRelativeDisagreement(),
        parameter_quality.isAllEvaluationsConverged(),
        parameter_quality.isAllEvaluationsFeasible(),
    )
```

`estimateSensitivitiesWithQuality(...)` returns the fine-step derivatives and preserves an
immutable record for every perturbation: signed applied step, actual parameter value, process
convergence, hard-constraint feasibility, and evaluation error. Its relative disagreement is
`abs(D_h - D_h/2) / max(abs(D_h), abs(D_h/2))`, or zero when both derivatives are zero. Use
`isNumericallyStable(tolerance)` with a tolerance justified for the engineering decision. The
method needs four perturbed simulations per interior central parameter and two per one-sided
parameter, while a fixed parameter needs none; objective and constraint derivatives reuse those
same simulations. Existing `estimateGradient(...)` and `estimateConstraintJacobian(...)` remain
the lower-cost APIs.

The same result snapshots the derivative identities at the base point. Parameter snapshots retain
registration index, name, automation address, unit, bounds, and the bounded value actually
evaluated. The objective snapshot retains direction, unit, weight, raw and minimizer-convention
base values, and the gradient. Each constraint snapshot retains type, unit, hard/soft flag,
penalty, bounds or tolerance, capacity area/equipment origin, sampled base value, margin, and its
Jacobian row. These records are immutable and serializable, so later evaluator mutations or
process runs cannot silently relabel archived sensitivities. Raw margins and derivatives keep
their declared units; do not compare or rank unlike constraints without explicit engineering
scaling.

Convergence and numerical agreement are necessary but not sufficient. Inspect perturbation
feasibility separately, test nearby operating points, and reject or qualify sensitivities that
cross equipment/control regimes. An infeasible perturbation is retained as evidence rather than
silently invalidating a constraint-margin derivative.

Use an explicit qualification policy before passing local derivatives into a bottleneck or
operating-action workflow:

```python
Policy = (
    jneqsim.process.util.optimizer.ProcessModelSimulationEvaluator
    .SensitivityQualificationPolicy
)

# Requires a feasible base and feasible perturbations. One-sided bounded stencils are allowed.
strict_policy = Policy.strict(0.05)
assessments = quality_result.assessConstraintSensitivities(strict_policy)

for assessment in assessments:
    print(
        assessment.getConstraint().getName(),
        assessment.getParameter().getName(),
        assessment.getRawObjectiveDerivative(),
        assessment.getRawObjectiveDerivativeUnit(),
        assessment.getMarginDerivative(),
        assessment.getMarginDerivativeUnit(),
        assessment.isAccepted(),
        list(assessment.getEvidenceFlags()),
        list(assessment.getRejectionReasons()),
        list(assessment.getDiagnostics()),
    )

accepted = quality_result.getAcceptedConstraintSensitivities(strict_policy)
```

Qualification performs no additional process evaluations. Every assessment binds one constraint
row and parameter column to the immutable snapshots, reports both raw and minimizer-convention
objective derivatives, and retains the exact stencil plus objective and constraint coarse/fine
disagreements. Convergence failures, evaluation errors, non-finite derivatives, unstable
refinement, and fixed parameters always reject a pair. The policy explicitly controls whether
base or perturbation infeasibility and one-sided stencils reject it. Even when allowed, these
conditions remain visible in `getEvidenceFlags()` and the diagnostics.

`Policy.numericalOnly(tolerance)` is useful for diagnosing a violated or boundary-crossing case,
but acceptance under that policy is numerical evidence only. It is not engineering approval.
Results remain in declared units and are intentionally not ranked across constraints. Explicit
scaling, regime validation, active-set logic, and optimizer-specific KKT evidence are separate
requirements.

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

### Process cloning and parallel evaluation

Only `ProcessSimulationEvaluator` exposes `setCloneForEvaluation(true)`. It clones the
`ProcessSystem` used for an evaluation so the registered base process is not mutated by that
call:

```python
evaluator.setCloneForEvaluation(True)
```

This switch does not make one evaluator instance safe for concurrent calls: counters, last-result
state, and registered definitions remain mutable. Use one evaluator and one JVM-owned process
model per worker, and validate deterministic equivalence before parallel production studies.

## Complete Example: Gas Processing Optimization

```python
import matplotlib.pyplot as plt
import numpy as np
from scipy.optimize import minimize
from neqsim import jneqsim

ProcessSimulationEvaluator = (
    jneqsim.process.util.optimizer.ProcessSimulationEvaluator
)
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.cooler.Cooler
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos

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

### ProcessModelSimulationEvaluator

| Method | Description |
|--------|-------------|
| `estimateSensitivitiesWithQuality(double[] x)` | Primary-objective fine-step gradient and constraint-margin Jacobian, plus immutable parameter/objective/constraint identity, base values, capacity origin, perturbation convergence, feasibility, and step-halving evidence |
| `estimateSensitivitiesWithQuality(double[] x, int objectiveIndex)` | The same evidence for the selected registered objective |
| `SensitivityQualityResult.assessConstraintSensitivities(policy)` | Immutable evidence and acceptance/rejection diagnostics for every constraint/parameter pair; performs no process evaluations |
| `SensitivityQualityResult.getAcceptedConstraintSensitivities(policy)` | Accepted local pairs only; inspect the full assessment list to retain rejected evidence |

The sensitivity-quality methods belong to `ProcessModelSimulationEvaluator`; they are not methods
on `ProcessSimulationEvaluator`.

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

- [OPTIMIZER_PLUGIN_ARCHITECTURE.md](../process/optimization/OPTIMIZER_PLUGIN_ARCHITECTURE.md) - Plugin architecture for equipment-specific optimization
- [flow-rate-optimization.md](../process/optimization/flow-rate-optimization.md) - FlowRateOptimizer for lift curve generation
- [pressure_boundary_optimization.md](../process/pressure_boundary_optimization.md) - Simplified pressure boundary optimizer
- [PRODUCTION_OPTIMIZATION_GUIDE.md](../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) - Complete production optimization examples
