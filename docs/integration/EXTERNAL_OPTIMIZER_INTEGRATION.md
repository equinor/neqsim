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

Use explicit engineering references before comparing dimensionless margins or exposing a
candidate-active set:

```python
import jpype

Analyzer = jneqsim.process.util.optimizer.ConstraintActivityAnalyzer
ArrayList = jpype.JClass("java.util.ArrayList")

reference_by_name = {
    # Values are positive and use each constraint's declared unit.
    "export compressor power": (12_000.0, "installed motor rating"),
    "gas export nomination": (1_000_000.0, "daily nomination basis"),
}

scales = ArrayList()
for constraint in quality_result.getConstraintSnapshots():
    reference, provenance = reference_by_name[str(constraint.getName())]
    scales.add(
        Analyzer.ConstraintScale.fromSnapshot(
            constraint,
            reference,
            provenance,
        )
    )

activity_policy = Analyzer.ActivityPolicy.hardConstraints(
    0.05,  # candidate active when 0 <= normalized margin <= 5% of its reference
    strict_policy,
)
activity = Analyzer.assess(
    quality_result,
    scales,
    activity_policy,
)

for item in activity:
    print(
        item.getConstraint().getName(),
        item.getScale().getReferenceValue(),
        item.getScale().getUnit(),
        item.getScale().getProvenance(),
        item.getNormalizedMargin(),
        item.getStatus(),
        list(item.getDiagnostics()),
    )
    for derivative in item.getSensitivities():
        print(
            derivative.getSensitivityAssessment().getParameter().getName(),
            derivative.getNormalizedMarginDerivative(),
            derivative.getNormalizedMarginDerivativeUnit(),
            derivative.isUsable(),
            list(derivative.getRejectionReasons()),
        )

candidate_active = Analyzer.getCandidateActiveConstraints(activity)
violated = Analyzer.getViolatedConstraints(activity)
```

Scales may be supplied in any order, but exactly one identity-matched scale is required for every
constraint and results remain in registration order. A stale scale fails if type, bounds,
hardness, penalty or capacity origin changed. Soft constraints are excluded unless the policy
explicitly includes them. A normalized derivative remains auditable even when rejected; require
`isUsable()` before consuming it. Keep violated constraints separate from feasible
`CANDIDATE_ACTIVE` constraints, and never interpret the candidate set as optimizer KKT evidence or
rank economic value without an optimizer-specific solution and objective scaling.

### Declare reversible continuous and discrete operating actions

Use `ProcessModelOperatingAction` when an optimizer candidate must retain stable engineering
identity, provenance, exact value semantics, and an explicit restoration token instead of being an
anonymous numeric setter:

```python
Action = jneqsim.process.util.optimizer.ProcessModelOperatingAction
JDoubleArray = jpype.JArray(jpype.JDouble)

feed_target = Action.continuous(
    "field-feed",
    "Field feed target",
    "wells::feed.flowRate",
    5000.0,
    20000.0,
    "kg/hr",
    "approved operating envelope revision A",
)
pressure_mode = Action.discrete(
    "compressor-lineup",
    "Compressor line-up",
    "compression::lineup-selector.value",
    JDoubleArray([1.0, 2.0, 3.0]),
    "count",
    "installed train line-up table revision B",
)

capability = feed_target.inspectCapability(process_model)
if not capability.isAvailable():
    raise RuntimeError(list(capability.getDiagnostics()))

baseline = feed_target.capture(process_model)
application = feed_target.apply(process_model, 12000.0)
if not application.isApplied():
    raise RuntimeError(application.getDiagnostic())

# Running and validating the candidate remains explicit.
process_model.run()
# Inspect convergence, constraints, conservation, and product specifications here.

restoration = feed_target.restore(process_model, baseline)
if not restoration.isApplied():
    raise RuntimeError(restoration.getDiagnostic())
```

Continuous candidates must lie inside the inclusive declared bounds. Discrete candidates must
equal one enumerated value exactly; the API never interpolates a line-up. A readable brownfield
baseline may lie outside the candidate domain and is still capturable for restoration.
`apply(...)` writes through the existing area-qualified `ProcessAutomation` address and verifies
the read-back without running the process.

Register an action with the established model evaluator when an external optimizer should own the
candidate loop:

```python
binding = feed_target.registerWith(evaluator)
print(binding.getParameterIndex(), binding.getInitialValue())

lineup_binding = compressor_lineup.registerWith(evaluator)
allowed_lineups = list(lineup_binding.getAllowedValues())
```

Registration requires the current model value to belong to the declared candidate domain; an
off-domain brownfield baseline may be captured and restored but is not silently promoted to an
optimizer starting point. For a discrete binding, the evaluator vector exposes only the numerical
envelope; enumerate
`getAllowedValues()`. Any intermediate proposal fails the evaluation explicitly and leaves the
previous verified value unchanged. Evaluator callback setters are transient, consistent with
objective and constraint callbacks, so re-register actions after deserializing an evaluator.
Captured actions, bindings, capability/application results, and state tokens are immutable and
Java-serializable for JPype workflows.

Capability inspection proves only that the exact address is readable in the declared unit.
Application proves only write/read-back consistency. Neither runs NeqSim, changes topology,
establishes process feasibility, selects a line-up, or constitutes operating or safety approval.

### Evaluate and restore one hydraulic operating candidate

Use `ProcessModelOperatingActionEvaluator` when an optimizer needs a complete candidate result
against exact enabled reservoir, well, gathering, or pipeline capacity constraints and the shared
model must be returned to its baseline state before the callback returns. Configure objectives and
other process constraints on a zero-parameter `ProcessModelSimulationEvaluator`; the transactional
wrapper owns the action write and automatically registers enabled equipment capacities.

```python
SimulationEvaluator = (
    jneqsim.process.util.optimizer.ProcessModelSimulationEvaluator
)
ActionEvaluator = (
    jneqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator
)
HydraulicRole = (
    ActionEvaluator.HydraulicLimitRole
)

simulation = SimulationEvaluator(process_model)
simulation.setIncludeStrategyCapacityConstraints(False)

well_rate = Action.continuous(
    "producer-rate",
    "Producer gas rate",
    "Subsurface::producer.flowRate",
    0.5,
    1.5,
    "MSm3/day",
    "approved well operating envelope revision A",
).withReadBackTolerance(
    1.0e-5,
    0.0,
    "producer flow-control tag resolution in MSm3/day",
)
hydraulic = ActionEvaluator(simulation, well_rate)
hydraulic.requireHydraulicConstraint(
    HydraulicRole.WELL_INFLOW_OUTFLOW,
    "Subsurface",
    "well",
    "well drawdown",
    "installed maximum drawdown basis",
)

candidate = hydraulic.evaluate(1.2)
if not candidate.isBaselineRestored():
    raise RuntimeError(list(candidate.getDiagnostics()))
if not candidate.isBaselineSimulationConverged():
    raise RuntimeError("Restored baseline did not reconverge")

print(candidate.getOutcome())
for constraint in candidate.getHydraulicConstraints():
    print(
        constraint.getBinding().getQualifiedConstraintName(),
        constraint.getCurrentValue(),
        constraint.getUnit(),
        constraint.getUtilization(),
        constraint.getMargin(),
        constraint.getDataSource(),
        constraint.getEvidenceApplicability(),
    )
```

Exact names are intentional: a missing or disabled bound constraint fails closed instead of
silently accepting a different limit. Finite utilization at or below one is insufficient when an
explicit validity range exists and the candidate lies outside it. Missing validity metadata is
reported as `NOT_ASSESSED`; confidence remains evidence-quality metadata, not a safety
probability. Where automation conversion or control-tag resolution is coarser than the default
floating-point comparison, configure `withReadBackTolerance(absolute, relative, provenance)`.
The action unit applies to the absolute tolerance. Application diagnostics retain requested,
read-back, residual, allowed-tolerance, and tolerance-provenance evidence; this qualifies only
write/read-back consistency, not process feasibility. The immutable result is Java-serializable
and exposes defensive arrays and immutable lists for JPype/Python consumers.

The wrapper evaluates one steady-state action at a time and is synchronized because it mutates the
supplied model. It does not coordinate routing/topology transactions, solve multiple simultaneous
actions, replace `WellFlow` or pipeline correlations, or infer mechanical, safety, environmental,
product-quality, or market approval. Register all other applicable constraints on `simulation`
before constructing the wrapper, and use separate model instances for parallel candidate calls.

### Evaluate a coupled well-allocation candidate atomically

Use `ProcessModelOperatingActionSetEvaluator` for a candidate vector that must be applied as one
transaction, such as two well-rate targets competing for one gathering limit. All actions are
captured before the first write. Duplicate IDs or automation addresses fail at construction. If a
later value is rejected, the partial candidate is not simulated and every captured action is
restored in reverse declaration order.

```python
ActionSetEvaluator = (
    jneqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator
)

allocation = ActionSetEvaluator(
    "field-allocation",
    "Field production allocation",
    "approved well envelopes and gathering basis revision A",
    simulation,
    [well_a_action, well_b_action],
)
allocation.requireHydraulicConstraint(
    HydraulicRole.WELL_INFLOW_OUTFLOW,
    "Subsurface",
    "well A",
    "well drawdown",
    "well A installed maximum drawdown",
)
allocation.requireHydraulicConstraint(
    HydraulicRole.WELL_INFLOW_OUTFLOW,
    "Subsurface",
    "well B",
    "well drawdown",
    "well B installed maximum drawdown",
)
allocation.requireHydraulicConstraint(
    HydraulicRole.GATHERING_HYDRAULICS,
    "Gathering",
    "inlet separator",
    "installed gathering rate",
    "shared installed gathering capacity",
)

candidate = allocation.evaluate([well_a_rate, well_b_rate])
for action_result in candidate.getActionEvidence():
    print(
        action_result.getAction().getId(),
        action_result.getBaselineValue(),
        action_result.getRequestedValue(),
        action_result.getReadBackValue(),
        action_result.getReadBackResidual(),
        action_result.getReadBackTolerance(),
        action_result.getReadBackToleranceProvenance(),
        action_result.isRestored(),
    )

if not candidate.isBaselineRestored():
    raise RuntimeError(list(candidate.getDiagnostics()))
if not candidate.isBaselineSimulationConverged():
    raise RuntimeError("Restored coupled baseline did not reconverge")
```

The candidate array follows action declaration order and each value uses its action's declared unit.
Wrong-length vectors, invalid continuous or discrete values, missing addresses, failed read-back,
missing/non-finite/out-of-range exact constraints, hard violations, non-convergence, or incomplete
restoration fail closed with distinct outcomes. Results retain defensive arrays and fresh immutable
Java lists for JPype consumers and are Java-serializable.

The evaluator does not optimize the vector, interpolate discrete line-ups, change routing or
hydraulic correlations, or establish operating approval. Validate conservation, constraint
residuals, product specifications and nearby operating points with the underlying NeqSim model.

### Search a fixed-total continuous allocation

`ProcessModelAllocationOptimizer` composes the atomic evaluator when all allocation actions are
continuous, use one exact unit, and must preserve a declared shared total. Its deterministic
transfer search retains every candidate result instead of reducing simulator evidence to one score.

```python
AllocationOptimizer = (
    jneqsim.process.util.optimizer.ProcessModelAllocationOptimizer
)

optimizer = AllocationOptimizer(
    "field-rate-allocation",
    "Field rate allocation",
    "approved well envelopes and host capacity basis revision A",
    allocation,
    total_rate,
    "kg/hr",
)
optimizer.setInitialAllocation([initial_well_a_rate, initial_well_b_rate])
optimizer.setObjectiveIndex(0)
optimizer.setInitialStepFraction(0.10)
optimizer.setRelativeStepTolerance(1.0e-3)
optimizer.setObjectiveImprovementTolerance(
    1.0e-6,
    "validated export-rate calculation resolution",
)
optimizer.setMaximumEvaluations(100)

search = optimizer.optimize()
if not search.isModelRecovered():
    raise RuntimeError(list(search.getDiagnostics()))

best = search.getBestFeasibleCandidate()
if best is not None:
    best_rates = list(best.getCandidateValues())
    bottlenecks = list(search.getRankedHydraulicConstraintsAtBestFeasible())
    for constraint in bottlenecks:
        print(
            constraint.getBinding().getQualifiedConstraintName(),
            constraint.getUtilization(),
            constraint.getMargin(),
            constraint.getDataSource(),
        )

    for capacity in search.getInstalledCapacityEvidenceAtBestFeasible():
        print(
            capacity.getQualifiedConstraintName(),
            capacity.getNormalizedUtilization(),
            capacity.getNormalizedUnit(),
            capacity.getCurrentValue(),
            capacity.getApplicableLimit(),
            capacity.getRequiredRelief(),
            capacity.getPhysicalUnit(),
            capacity.getConstraintOrigin(),
            capacity.getEvidenceStatus(),
        )

sampled_gap = search.getSampledObjectiveOpportunityGap()
```

The result is Java-serializable and exposes defensive arrays and fresh immutable lists through
JPype. It freezes objective direction, unit and weight together with allocation identity,
provenance, bounds, seed, budget, tolerances, terminal outcome, complete candidate results, ranked
hydraulic evidence, and every discovered installed-capacity row. Normalized utilization/margin use
unit `"1"`; physical current, applicable limit, margin, and required relief use
`getPhysicalUnit()`. `getBestSampledObjectiveCandidate()` may be infeasible; its gap to the
best feasible point is only an opportunity among evaluated points.

A converged transfer step is local numerical evidence, not a global optimum or shadow price. A
budget-exhausted result retains the best feasible point but remains unconverged. Any incomplete
baseline recovery stops the search and sets `isModelRecovered()` false. This API does not support
discrete line-ups, routing changes, mixed-unit allocation, dynamic qualification, economics or
operating approval.

Use independent models for parallel candidates.

### Analyze sampled bottleneck relief without another simulation

After a fixed-total search, use the trace-only analyzer to retain exact hard-constraint identity,
required in-unit relief, and action movement from the best feasible allocation. The call performs
no model evaluation or mutation.

```python
BottleneckAnalyzer = (
    jneqsim.process.util.optimizer.ProcessModelAllocationBottleneckAnalyzer
)

analyzer = BottleneckAnalyzer(
    "field-allocation-relief",
    "Field allocation bottleneck relief",
    "approved allocation study revision A",
)
analysis = analyzer.analyze(search)

for opportunity in analysis.getOpportunities():
    print(
        opportunity.getCandidateSequenceIndex(),
        list(opportunity.getCandidateValues()),
        list(opportunity.getActionDeltasFromBestFeasible()),
        opportunity.getObjectiveGain(),
        opportunity.getObjective().getUnit(),
        opportunity.getEvidenceClass(),
    )
    for relief in opportunity.getConstraintRelief():
        constraint = relief.getConstraint()
        print(
            constraint.getName(),
            constraint.getAreaName(),
            constraint.getEquipmentName(),
            relief.getRequiredMarginRelief(),
            relief.getUnit(),
        )
```

`CandidateSetEvaluationResult.getObjectiveEvidence()` and
`getConstraintEvidence()` return fresh immutable lists in evaluator registration order. Each row
freezes the definition metadata together with the sampled raw/minimizer objective or
constraint value/signed margin. This prevents a serialized trace from being joined back to mutable
evaluator definitions.

An opportunity requires a converged candidate, verified complete restoration and baseline
reconvergence, a finite direction-aware improvement above the search tolerance, and at least one
finite violated hard constraint. Installed-capacity relief is read from the same immutable current/applicable-limit snapshot that
supplied normalized candidate feasibility, including installed constraints not selected as required
hydraulic bindings; a general constraint uses `max(0, -margin)` in its declared unit. Soft violations are not reported, raw relief is never compared across unlike units, and
missing/non-finite/out-of-validity hydraulic evidence produces `EVIDENCE_LIMITED`.

The ranking is over the common selected-objective unit and finite sampled points only. It is
non-causal evidence, not a capacity-sizing answer, global optimum, KKT multiplier, shadow price,
production-loss estimate, economic value, or operating approval.

### Paired debottleneck studies from Python

`ProcessModelDebottleneckStudy` evaluates one documented direct installed-capacity alternative
with the same `ScenarioSearch` in both cases. The built-in `CandidateListSearch` is useful when
Python has already generated a bounded candidate design and NeqSim must remain the physical
feasibility authority.

JPype callers should pass an ordered Java `List<double[]>`. Result arrays are defensive copies,
and result lists are unmodifiable Java lists. Convert them explicitly when native Python containers
are needed:

```python
import jpype
from jpype.types import JArray, JDouble

ArrayList = jpype.JClass("java.util.ArrayList")
Study = jneqsim.process.util.optimizer.ProcessModelDebottleneckStudy

candidates = ArrayList()
candidates.add(JArray(JDouble)([800.0]))
candidates.add(JArray(JDouble)([999.0]))
candidates.add(JArray(JDouble)([1199.0]))

search = Study.CandidateListSearch(
    "throughput-grid",
    "Ordered throughput grid",
    "screening candidate set rev A",
    candidates,
    0,
    0.0,
)

# Configure CapacityAlternative, ProcessModelDebottleneckStudy, and metric
# definitions with explicit units/provenance as shown in the optimization guide.
result = study.evaluate()

baseline_parameters = list(result.getBaseline().getSelectedParameters())
alternative_parameters = list(result.getAlternative().getSelectedParameters())
metric_deltas = {
    row.getBaseline().getId(): row.getDelta()
    for row in result.getMetricComparisons()
    if row.isCalculable()
}
diagnostics = list(result.getDiagnostics())
```

Here the 999 and 1199 kg/hr candidates retain 1 kg/hr of declared headroom below nominal
1000 and 1200 kg/hr installed limits. Do not rely on a unit conversion or floating-point process
reconstruction to land exactly on a hard constraint boundary.

The study independently re-evaluates the selected point after the search callback returns. A
custom external search therefore supplies a candidate vector, not trusted convergence or
feasibility flags. Do not mutate the same evaluator or process model concurrently. The study
restores and reconverges the pre-study parameter vector before returning.

The immutable `StudyResult` retains the alternative identity and provenance, original and applied
capacity state, paired objectives and constraint residuals, installed-capacity and process-boundary
evidence, physical/screening metrics, evaluation counts, recovery flags, and diagnostics. It
retains no process model, equipment, live capacity constraint, or Python callback and can be
Java-serialized for restartable records. Screening economics and emissions remain caller-supplied
metrics with explicit basis and provenance; NeqSim does not certify them.

### Rank compatible paired studies from Python

`ProcessModelDebottleneckRanking` consumes completed immutable study results. It ranks one exact
metric definition and rejects changed units, bases, provenance, periods, confidence, searches, or
baseline evidence instead of constructing a normalized score.

```python
Ranking = jneqsim.process.util.optimizer.ProcessModelDebottleneckRanking

policy = Ranking.RankingPolicy(
    "production-delta",
    "Production delta ranking",
    "screening portfolio rev A",
    "production",
    "Feed production",
    Study.MetricKind.PRODUCTION,
    "kg/hr",
    "wet feed mass rate",
    "NeqSim stream result",
    "single steady state",
    Ranking.RankingDirection.MAXIMIZE,
    0.0,
    0.5,
    0.9,
)

ranking = Ranking(
    "separator-portfolio",
    "Separator alternatives portfolio",
    "brownfield screening alternatives rev A",
    policy,
)

study_results = ArrayList()
study_results.add(result_1100)
study_results.add(result_1150)
study_results.add(result_1200)
portfolio = ranking.rank(study_results)

best = portfolio.getBestCandidate()
best_alternative_id = best.getAlternativeDefinition().getId()
best_delta = best.getDelta()
best_unit = portfolio.getPolicy().getUnit()

ranked_rows = [
    {
        "rank": row.getRank(),
        "alternative_id": row.getAlternativeDefinition().getId(),
        "delta": row.getDelta(),
    }
    for row in portfolio.getRankedCandidates()
]
rejected_rows = [
    {
        "alternative_id": row.getAlternativeDefinition().getId(),
        "status": str(row.getStatus()),
        "diagnostics": list(row.getDiagnostics()),
    }
    for row in portfolio.getRejectedCandidates()
]
```

The same deterministic baseline must be reproduced by every rankable study. A different candidate
grid that selects another installed-case point is rejected even when its alternative delta is
finite. The returned Java lists are unmodifiable and every `CandidateEvidence` retains its complete
serializable `StudyResult`. Run separate policies for production, power, emissions, and screening
economics; never sum their raw deltas or compare unlike units.

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
| `ConstraintActivityAnalyzer.ConstraintScale.fromSnapshot(...)` | Positive identity-bound constraint reference with declared unit and provenance |
| `ConstraintActivityAnalyzer.assess(result, scales, policy)` | Dimensionless margins, normalized local margin sensitivities and conservative activity diagnostics without process evaluations |
| `getCandidateActiveConstraints(...)` / `getViolatedConstraints(...)` | Registration-ordered feasible-near-boundary and violated subsets; neither is optimizer KKT evidence |
| `EvaluationResult.getInstalledEquipmentCapacityEvidence()` | Fresh immutable, utilization-ranked installed-capacity rows with stable identity/provenance, dimensionless feasibility, and separate physical residuals |
| `snapshotInstalledEquipmentCapacityEvidence(model)` | Read a live model once per enabled capacity supplier outside an evaluator run; no process simulation is performed |

The sensitivity-quality methods belong to `ProcessModelSimulationEvaluator`; they are not methods
on `ProcessSimulationEvaluator`.

### ProcessModelAllocationOptimizer

| Method | Description |
|--------|-------------|
| `optimize()` | Run fixed-total continuous pair-transfer search through the atomic action-set evaluator |
| `AllocationSearchResult.getCandidates()` | Complete immutable candidate and restoration trace |
| `getBestFeasibleCandidate()` | Best feasible finite-objective candidate under the frozen direction |
| `getBestSampledObjectiveCandidate()` | Best sampled objective candidate, whether feasible or not |
| `getSampledObjectiveOpportunityGap()` | Non-negative sampled diagnostic gap; not global production loss or shadow value |
| `getRankedHydraulicConstraintsAtBestFeasible()` | Stable descending-utilization evidence at the feasible incumbent |
| `getRankedHydraulicConstraintsAtBestSampledObjective()` | Stable descending-utilization evidence at the best sampled objective |
| `getInstalledCapacityEvidenceAtBestFeasible()` | Complete immutable installed-capacity evidence at the feasible incumbent |
| `getInstalledCapacityEvidenceAtBestSampledObjective()` | Complete immutable installed-capacity evidence at the best sampled objective |

### ProcessModelDebottleneckStudy

| Method | Description |
|--------|-------------|
| `evaluate()` | Run the common search for baseline and alternative, independently verify each selected point, sample metrics, and recover state |
| `CandidateListSearch(...)` | Deterministic direction-aware search over an ordered bounded candidate list; equal objective values retain the first candidate |
| `CapacityAlternative(...)` | Stable direct-constraint identity plus proposed applicable limit, unit, direction, source, confidence, and validity range |
| `addMetric(MetricDefinition)` | Register one production, power, energy, emissions, screening-economic, or other metric with unit, basis, provenance, period, confidence, and required status |
| `StudyResult.getBaseline()` / `getAlternative()` | Immutable scenario evidence with selected parameters, objectives, margins, installed/boundary evidence, metrics, and diagnostics |
| `StudyResult.getMetricComparisons()` | Identically defined metric rows with `alternative - baseline` deltas |
| `StudyResult.isCapacityRestored()` / `isProcessStateRestored()` | Explicit transaction-recovery evidence |

### ProcessModelDebottleneckRanking

| Method | Description |
|--------|-------------|
| `RankingPolicy(...)` | Exact single-metric identity, unit, basis, provenance, period, direction, tie tolerance, and optional confidence floors |
| `rank(List<StudyResult>)` | Fail-closed qualification, exact-baseline comparison, deterministic ordering, and competition ranks |
| `RankingResult.getRankedCandidates()` | Compatible candidates in deterministic rank order with their complete paired-study evidence |
| `RankingResult.getRejectedCandidates()` | Incompatible studies with status and diagnostics; no synthetic normalized score |
| `RankingResult.getCandidatesInInputOrder()` | Complete audit trail in caller submission order |
| `RankingResult.getBestCandidate()` | Highest-ranked compatible candidate, or `null` when none qualifies |

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

