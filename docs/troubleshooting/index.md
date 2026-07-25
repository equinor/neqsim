---
title: "NeqSim Troubleshooting Guide"
description: "Diagnose NeqSim flash, density, Python, process, recycle, and phase-envelope problems."
---

Use this page to reduce a failing case to a small, reproducible calculation before
changing models or numerical settings. Record the NeqSim version, fluid
composition, equation of state, mixing rule, temperature in K or °C, absolute
pressure in bara, and the complete exception.

## Start from a known-good diagnostic case

This complete Python example verifies the gateway, composition, flash, and
physical-property initialization:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ThermodynamicOperations = (
    jneqsim.thermodynamicoperations.ThermodynamicOperations
)

fluid = SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.90)
fluid.addComponent("ethane", 0.06)
fluid.addComponent("propane", 0.03)
fluid.addComponent("CO2", 0.01)
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

operations = ThermodynamicOperations(fluid)
operations.TPflash()
fluid.initProperties()

overall_total = sum(
    fluid.getPhase(0).getComponent(i).getz()
    for i in range(fluid.getPhase(0).getNumberOfComponents())
)
bulk_density = fluid.getDensity("kg/m3")

assert abs(overall_total - 1.0) < 1.0e-12
assert bulk_density > 0.0
print(f"Composition total: {overall_total:.12f}")
print(f"Bulk density: {bulk_density:.3f} kg/m³")
```

With NeqSim 3.16.0, this fixture reports a composition total of `1.000000000000`
and a bulk density of approximately `40.636 kg/m³`.

## Flash convergence and unexpected phases

Check these causes in order:

1. Confirm constructor temperature is in kelvin and pressure is absolute in
   bara. For example, `SystemSrkEos(298.15, 50.0)` is 25°C and 50 bara.
2. Confirm every component name exists and the overall composition is positive
   and normalized. Inspect overall mole fractions with
   `fluid.getPhase(0).getComponent(i).getz()`.
3. Set the mixing rule before the flash. Use `"classic"` for a basic SRK or PR
   hydrocarbon case.
4. Enable `setMultiPhaseCheck(True)` when an additional stable phase is
   physically possible. This changes the phase-stability search; it is not a
   generic convergence switch.
5. Reproduce the problem on a clone or freshly constructed fluid. A failed
   operation may leave an object unsuitable for a diagnostic retry.
6. Check whether the selected model represents the fluid chemistry. CPA or an
   electrolyte model may be appropriate for associating or ionic systems, but
   changing the equation of state is a physical-model decision rather than a
   numerical workaround.

Do not silently perturb composition, add an inert component, or change operating
conditions merely to make a flash converge. Such changes define a different
engineering case.

## Density and physical properties

NeqSim exposes two distinct density paths:

- `fluid.getDensity()` derives an equation-of-state density from molar volume.
- `fluid.getDensity("kg/m3")` returns the initialized physical-property density
  in the requested supported unit.

After a flash, call `fluid.initProperties()` before reading density, viscosity,
thermal conductivity, or other physical properties. Then use
`fluid.getDensity("kg/m3")` for the bulk value and, after checking
`fluid.hasPhaseType("gas")`, use
`fluid.getPhase("gas").getDensity("kg/m3")` for the gas phase.

The unit-aware getter uses the initialized physical-property path; it does not
select a volume-translation model. Any volume translation is determined by the
configured thermodynamic system and its component parameters. Report the model
and property path with density results.

Reflash and reinitialize properties after changing temperature, pressure, or
composition. For two-phase systems, also inspect phase-specific densities and
phase fractions; a bulk value can be correct while being misinterpreted as a
single-phase property.

## Python gateway and overload errors

For `TypeError: No matching overloads found`, compare the call with the current
Java signature. Use the supported `from neqsim import jneqsim` gateway, preserve
floating-point values and unit strings when the method expects them, and convert
returned Java strings with `str(...)` before applying Python formatting.

A JVM cannot be restarted in the same Python process. Restart the kernel or use
a new process after stopping it. Do not suppress the original Java exception;
retain the full traceback and the minimal input case.

## Process equipment produces zero or implausible results

Create all connections, add every unit to one `ProcessSystem`, run the system,
and only then read results:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor

feed_fluid = SystemSrkEos(298.15, 50.0)
feed_fluid.addComponent("methane", 0.90)
feed_fluid.addComponent("ethane", 0.06)
feed_fluid.addComponent("propane", 0.03)
feed_fluid.addComponent("CO2", 0.01)
feed_fluid.setMixingRule("classic")

feed = Stream("feed", feed_fluid)
feed.setFlowRate(10_000.0, "kg/hr")
separator = Separator("separator", feed)
compressor = Compressor("compressor", separator.getGasOutStream())
compressor.setOutletPressure(80.0, "bara")
compressor.setIsentropicEfficiency(0.75)

process = ProcessSystem()
process.add(feed)
process.add(separator)
process.add(compressor)
process.run()

gas_flow = separator.getGasOutStream().getFlowRate("kg/hr")
compressor_power = compressor.getPower("kW")

assert gas_flow > 0.0
assert compressor_power > 0.0
```

NeqSim 3.16.0 gives `10,000 kg/h` gas and approximately `228.434 kW` for this
single-phase fixture. If a result differs, inspect the stream states directly
before changing equipment parameters.

Compressor efficiency is a fraction, not percent. Very high pressure ratios may
require staging and intercooling, but no universal pressure-ratio limit proves
that a compressor is feasible. Use a valid compressor chart, operating envelope,
and mechanical-design basis for equipment conclusions.

## Recycle convergence

Use the current `Recycle` API. `setMaximumIterations(...)` and
`setDampingFactor(...)` are not `Recycle` methods:

```python
from neqsim import jneqsim

Recycle = jneqsim.process.equipment.util.Recycle
AccelerationMethod = jneqsim.process.equipment.util.AccelerationMethod

recycle = Recycle("recycle")
recycle.setTolerance(1.0e-4)
recycle.setMaxIterations(50)
recycle.setAccelerationMethod(AccelerationMethod.WEGSTEIN)

assert recycle.getMaxIterations() == 50
assert recycle.getAccelerationMethod() == AccelerationMethod.WEGSTEIN
```

`setTolerance(...)` applies the same threshold to flow, temperature,
composition, and pressure. Use `setFlowTolerance(...)`,
`setTemperatureTolerance(...)`, `setCompositionTolerance(...)`, and
`setPressureTolerance(...)` when the quantities need different thresholds.
These internal convergence errors are not all expressed in the same physical
unit, so record each threshold rather than describing one as a universal
temperature or pressure tolerance.

Before using acceleration, first verify that the loop is correctly connected and
that the tear-stream initial estimate is physically plausible. See
[Recycle acceleration](../simulation/recycle_acceleration_guide.md) for the
supported direct-substitution, Wegstein, and Broyden options.

## Phase-envelope failures

Start with a multicomponent hydrocarbon fluid, a cubic equation of state, and a
positive composition:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ThermodynamicOperations = (
    jneqsim.thermodynamicoperations.ThermodynamicOperations
)

fluid = SystemSrkEos(283.15, 10.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.08)
fluid.addComponent("propane", 0.04)
fluid.addComponent("n-butane", 0.03)
fluid.setMixingRule("classic")

operations = ThermodynamicOperations(fluid)
operations.calcPTphaseEnvelope()
dew_temperatures = operations.get("dewT")
bubble_temperatures = operations.get("bubT")

assert len(dew_temperatures) > 2
assert len(bubble_temperatures) > 2
```

If the default trace fails, rebuild the same case on a fresh fluid and use a
supported overload to control the starting branch and low-pressure point, such
as `calcPTphaseEnvelope(True, 1.0)`. The old
`calcPTphaseEnvelopeSpecificPoint(...)` remedy does not exist. Do not add a
component only to force numerical completion; that changes the phase envelope.

## Performance without stale properties

- Avoid physical-property initialization when only equilibrium phase amounts or
  compositions are needed.
- When transport properties are needed at every state in a sweep, call
  `initProperties()` after every flash. Calling it only after the loop leaves
  earlier results unavailable or stale.
- Reuse a fluid only when state mutation is intentional. Use `clone()` to isolate
  cases and improve reproducibility, not as a claimed universal speed-up.
- Reduce pseudo-components or pipeline increments only after confirming that the
  reduced resolution does not change the engineering conclusion.
- Profile the minimal reproducible case before changing tolerances or models.

## Common exception triage

| Symptom | First check |
| --- | --- |
| `NullPointerException` | Missing object, connection, component, or initialization |
| `IndexOutOfBoundsException` | Phase/component existence before indexed access |
| `No matching overloads` | Exact Java parameter types and unit-bearing overload |
| `JVM cannot be restarted` | Use a fresh Python process or kernel |
| Flash or equipment exception | Full nested cause, state, model, composition, and units |

## Related documentation

- [Thermodynamics cookbook](../cookbook/thermodynamics-recipes.md)
- [Process cookbook](../cookbook/process-recipes.md)
- [Pipeline cookbook](../cookbook/pipeline-recipes.md)
- [Recycle acceleration](../simulation/recycle_acceleration_guide.md)
- [Reference Manual Index](../REFERENCE_MANUAL_INDEX.md)

When opening a GitHub issue, include the NeqSim, Java, and Python versions; a
minimal executable example; exact inputs and units; expected and actual
behavior; and the complete exception with nested causes.
