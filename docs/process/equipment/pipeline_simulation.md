---
title: Pipeline Simulation Guide
description: Pipeline model selection, executable examples, units, profiles, and mechanical design in NeqSim.
keywords: "pipeline simulation, multiphase flow, Beggs and Brill, pressure drop, pipe flow, gas pipeline, oil pipeline, subsea, heat transfer, elevation, slug, holdup"
---

This guide uses the current Java pipeline API. The complete examples below define
both the fluid and flow rate; shorter configuration examples explicitly name the
model they apply to. Geometric setters use **metres** unless stated otherwise.

## Table of Contents

- [Overview](#overview)
- [Pipeline Interface](#pipeline-interface)
- [Pipeline Types](#pipeline-types)
- [Common Functionality](#common-functionality)
- [Flow Regime Detection](#flow-regime-detection)
- [Heat Transfer](#heat-transfer)
- [Pressure Drop Calculations](#pressure-drop-calculations)
- [Profile Methods](#profile-methods)
- [Geometry and Properties](#geometry-and-properties)
- [Mechanical Design Integration](#mechanical-design-integration)
- [Examples](#examples)

## Overview

NeqSim contains algebraic pressure-drop correlations, distributed flow solvers,
and transient pipeline models. Select the model according to the required
physics. A steady-state pressure-drop calculation does not predict line pack,
slug arrival times, or pressure-wave propagation.

Most process pipeline classes are in `neqsim.process.equipment.pipeline`.
`TransientPipe` is in its `twophasepipe` subpackage. Low-level flow systems are
in `neqsim.fluidmechanics.flowsystem`.

## Pipeline Interface

`PipeLineInterface` extends `SimulationInterface` and `TwoPortInterface`.
The following are selected **actual interface signatures**, not a complete
interface declaration. Implementations differ in supported physics and in which
result fields they populate; use the model-specific accessors demonstrated below.

| Method | Meaning / units |
|---|---|
| `setLength(double)` | Pipe length, m |
| `setDiameter(double)` | **Inner** flow diameter, m |
| `setPipeWallRoughness(double)` | Absolute roughness, m |
| `setElevation(double)` | Outlet minus inlet elevation, m |
| `setNumberOfIncrements(int)` | Discretization setting; interpretation depends on model |
| `getPressureDrop()` | Inlet minus outlet pressure, bar |
| `getOutletPressure(String)` | Outlet pressure in the requested unit, e.g. `"bara"` |
| `getOutletTemperature(String)` | Outlet temperature, e.g. `"C"` or `"K"` |
| `getFlowRegime()` | **String**, not an integer regime code |
| `setHeatTransferCoefficient(double)` | Heat-transfer coefficient, W/(m² K) |
| `setConstantSurfaceTemperature(double)` | Surface temperature, K |

Do not assume unit-string overloads for length, diameter, roughness, or heat
transfer setters. For example, pass 5000.0 to `setLength` for 5 km.

## Pipeline Types

### PipeBeggsAndBrills

`PipeBeggsAndBrills` uses the Beggs-Brill gas/liquid pressure-drop and holdup
correlation, with segmented equilibrium flashes and selectable thermal modes.
Use `getFlowRegime()` or `getFlowRegimeEnum()`, and use the segment holdup and
velocity profiles for computed hydraulic results. See Examples 2 and 3.

### AdiabaticPipe

`AdiabaticPipe` is a lightweight single-phase hydraulic model. Despite its name,
the current `run()` keeps inlet temperature unless an outlet temperature is
specified, then performs a TP flash at the calculated outlet pressure. It does
**not** solve an adiabatic energy balance. Constant temperature is an isothermal
assumption; adiabatic flow can change temperature. Example 1 demonstrates this
class's actual behavior.

For a model with an explicit thermal mode, use `PipeBeggsAndBrills` and
`PipeBeggsAndBrills.HeatTransferMode.ADIABATIC` or `.ISOTHERMAL` as appropriate.

### OnePhasePipe

The current process class is **`OnePhasePipeLine`**, not `OnePhasePipe`.
It wraps the low-level `PipeFlowSystem` for distributed single-phase flow.
See the [single-phase setup](../../fluidmechanics/#single-phase-pipe-flow)
for explicit geometry, boundary arrays, initialization, and solving.

### MultiphasePipe

`MultiphasePipe` wraps `TwoPhasePipeFlowSystem` for process-model integration.
It uses `setNumberOfNodesInLeg(int)`, `setHeatTransferCoefficient(double)`,
and `setAmbientTemperature(double)` (K). Its name does not imply that every
three-phase or phase-disappearance case is qualified. Read the
[two-phase model guide](../../fluidmechanics/TwoPhasePipeFlowModel) and assess
convergence, phase conservation, and applicability for the intended case.

### TransientPipe

The class is `neqsim.process.equipment.pipeline.twophasepipe.TransientPipe`.
Its API uses `setNumberOfSections(int)` and `setMaxSimulationTime(double)`
(seconds); it selects internal timesteps using its CFL setting. Boundary
conditions must also be specified. `run()` can stop when it reaches its
steady-state criterion before the maximum simulation time. See the
[transient pipeline guide](../../wiki/pipeline_transient_simulation).

## Common Functionality

### Setting Geometry

Use inner diameter rather than nominal pipe size or outside diameter. Convert
units before calling the geometric setters. For a straight incline, specify
length with either elevation change or angle; do not supply contradictory values.
`PipeBeggsAndBrills.setAngle(double)` takes degrees, positive uphill.

### Getting Flow Properties

For `AdiabaticPipe`, `getVelocity()`, `getReynoldsNumber()`, and
`getFrictionFactor()` expose hydraulic results. For `PipeBeggsAndBrills`, use
`getMixtureSuperficialVelocityProfile()` and `getMixtureReynoldsNumber()` for
computed segment data. Avoid assuming that every inherited scalar result field
is populated by every model.

### Two-Phase Properties

For `PipeBeggsAndBrills`, `getLiquidHoldupProfile()` contains liquid volume
fractions. `getGasSuperficialVelocityProfile()` and
`getLiquidSuperficialVelocityProfile()` give superficial velocities in m/s.
The phase velocity is the superficial velocity divided by its in-situ volume
fraction. This division is meaningful only while that phase is present.

## Flow Regime Detection

The Beggs-Brill model returns these enum names from `getFlowRegime()`:

| String | Meaning |
|---|---|
| `SEGREGATED` | Separated gas/liquid flow |
| `INTERMITTENT` | Slug/plug category |
| `DISTRIBUTED` | Dispersed-flow category |
| `TRANSITION` | Transition between categories |
| `SINGLE_PHASE` | A single phase is present |
| `UNKNOWN` | A regime has not been determined |

These are correlation categories, not predictions of slug timing or amplitude.
Example 2 reads the string and its corresponding `FlowRegime` enum.

### Flow Pattern Map

The Beggs-Brill flow pattern boundaries are defined by:

$$L_1 = 316 \cdot \lambda_L^{0.302}$$
$$L_2 = 0.0009252 \cdot \lambda_L^{-2.4684}$$
$$L_3 = 0.10 \cdot \lambda_L^{-1.4516}$$
$$L_4 = 0.5 \cdot \lambda_L^{-6.738}$$

Where $\lambda_L$ is the no-slip liquid holdup and $N_{Fr}$ is the Froude number.

---

## Heat Transfer

### Overall Heat Transfer Coefficient

For `PipeBeggsAndBrills`, set the surface temperature using
`setConstantSurfaceTemperature(4.0, "C")`, then set an effective U-value with
`setHeatTransferCoefficient(15.0)`. The latter selects `SPECIFIED_U` mode.
For a wall/insulation resistance model, use `setThickness(double)`,
`setPipeWallThermalConductivity(double)`, `setInsulation(thickness, conductivity)`,
`setOuterHeatTransferCoefficient(double)`, and select `HeatTransferMode.DETAILED_U`.
See the executable [heat-transfer examples](../../fluidmechanics/heat_transfer).

For the horizontal, unheated flowline in Example 2, inlet minus outlet enthalpy
flow estimates net heat removal. It is not a universal `getHeatLoss()` API and
requires accounting for elevation, kinetic energy, and other energy terms in
more general cases.

### Typical U-Values

U depends on the selected reference area, flow conditions, wall construction,
insulation, and environment. Treat a chosen value such as 15 W/(m² K) as an
example assumption; calculate or obtain a case-specific value for design.

## Pressure Drop Calculations

### Total Pressure Drop

The general decomposition is shown below. The current `PipeBeggsAndBrills`
pressure-drop calculation sums hydrostatic and friction terms; it does not
expose separate gravitational, frictional, and acceleration-drop getters.
Do not assume this model includes every term in the general expression.

$$\Delta P_{total} = \Delta P_{friction} + \Delta P_{gravity} + \Delta P_{acceleration}$$

### Frictional Pressure Drop (Beggs-Brill)

$$\Delta P_{friction} = \frac{f_{tp} \cdot \rho_{ns} \cdot v_m^2}{2 \cdot D} \cdot L$$

Where:
- $f_{tp}$ = two-phase friction factor
- $\rho_{ns}$ = no-slip mixture density
- $v_m$ = mixture velocity
- $D$ = pipe diameter
- $L$ = pipe length

### Gravitational Pressure Drop

$$\Delta P_{gravity} = \rho_s \cdot g \cdot \sin(\theta) \cdot L$$

Where:
- $\rho_s$ = slip mixture density = $\rho_L \cdot H_L + \rho_g \cdot (1-H_L)$
- $H_L$ = liquid holdup
- $\theta$ = pipe inclination angle

### Liquid Holdup Correlation

$$H_L(\theta) = H_L(0) \cdot \psi$$

Where $\psi$ is the inclination correction factor.

---

## Profile Methods

`PipeBeggsAndBrills` stores pressure in **bara**, temperature in **K**, and
length in **m**. For the non-isothermal examples below, profiles include the
inlet and outlet, giving `numberOfIncrements + 1` entries. Iterate over the
actual returned lengths; do not substitute a fabricated `getNumberOfNodes()`.
The isothermal path currently stores only the inlet temperature entry, so use
the inlet temperature for all positions if that mode is selected.

Example 2 reads the length, pressure, temperature, and holdup arrays together.
Other pipe models may expose a different profile layout or pressure unit;
check the specific model before combining profiles.

## Geometry and Properties

### Standard Pipe Sizes (API 5L)

| NPS (inch) | OD (mm) | OD (m) |
| ---------- | ------- | ------ |
| 2"         | 60.3    | 0.0603 |
| 4"         | 114.3   | 0.1143 |
| 6"         | 168.3   | 0.1683 |
| 8"         | 219.1   | 0.2191 |
| 10"        | 273.1   | 0.2731 |
| 12"        | 323.9   | 0.3239 |
| 16"        | 406.4   | 0.4064 |
| 20"        | 508.0   | 0.5080 |
| 24"        | 609.6   | 0.6096 |
| 30"        | 762.0   | 0.7620 |
| 36"        | 914.4   | 0.9144 |
| 42"        | 1066.8  | 1.0668 |
| 48"        | 1219.2  | 1.2192 |

---

The table lists **outside** diameters. Choose the wall thickness/schedule and
calculate the inner flow diameter before assigning `setDiameter`.

## Mechanical Design Integration

Pipeline hydraulic geometry and mechanical design use distinct inputs. The
convenience pipeline design API takes `setDesignTemperature` in °C and
`setDesignPressure` with an explicit unit. `calculateMinimumWallThickness()`
returns **metres**. This differs from the older `PipelineMechanicalDesign`
object's `setMaxOperationTemperature` (K) and `getWallThickness` (mm).

Example 4 demonstrates the convenience API. See
[Pipeline Mechanical Design](../pipeline_mechanical_design) for design
assumptions, standards, and the separate mechanical-design object.

## Examples

Each block is self-contained Java method-body code: put the imports at the top
of a class and the remaining statements inside `main` or a method. The
`PipelineGuideDocumentationTest` regression compiles these exact fenced blocks
and checks flow conservation and plausible results. Fluids and dimensions are
synthetic examples, using SRK with the classic mixing rule.

### Example 1: Gas Export Pipeline

<!-- pipeline-doc-test: gas-export -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.pipeline.AdiabaticPipe;

Logger logger = LogManager.getLogger("GasExportExample");
SystemSrkEos gas = new SystemSrkEos(303.15, 150.0); // K, bara
gas.addComponent("methane", 0.92);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.02);
gas.addComponent("CO2", 0.01);
gas.setMixingRule("classic");
Stream inlet = new Stream("Gas Inlet", gas);
inlet.setFlowRate(10.0, "MSm3/day"); // Standard volume, not actual pipe volume
inlet.run();

AdiabaticPipe pipeline = new AdiabaticPipe("Export Pipeline", inlet);
pipeline.setLength(100000.0); // m
pipeline.setDiameter(0.762); // m, assumed inner diameter
pipeline.setPipeWallRoughness(0.0001); // m
pipeline.run();
logger.info("Outlet: {} bara; pressure drop: {} bar",
    pipeline.getOutletPressure("bara"), pipeline.getPressureDrop());
logger.info("Velocity: {} m/s; Re: {}; Darcy f: {}", pipeline.getVelocity(),
    pipeline.getReynoldsNumber(), pipeline.getFrictionFactor());
logger.info("Temperature change: {} K",
    pipeline.getOutletTemperature("K") - inlet.getTemperature("K"));
```

Expect positive pressure drop and unchanged temperature for this class's
constant-temperature calculation. This does not establish an adiabatic heat
balance.

### Example 2: Subsea Multiphase Flowline

This example models a gas/condensate mixture with Beggs-Brill and a specified
U-value. It assumes a horizontal, constant-diameter pipe and equilibrium phase
partitioning; it does not resolve separate oil/water slip or slug transients.

<!-- pipeline-doc-test: subsea-flowline -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

Logger logger = LogManager.getLogger("SubseaFlowlineExample");
SystemSrkEos fluid = new SystemSrkEos(333.15, 100.0); // 60 C, 100 bara
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("n-decane", 0.10);
fluid.setMixingRule("classic");
Stream inlet = new Stream("Wellhead", fluid);
inlet.setFlowRate(10000.0, "kg/hr");
inlet.run();

PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("Subsea Flowline", inlet);
pipeline.setLength(5000.0);
pipeline.setDiameter(0.20);
pipeline.setElevation(0.0);
pipeline.setPipeWallRoughness(4.6e-5);
pipeline.setNumberOfIncrements(20);
pipeline.setConstantSurfaceTemperature(4.0, "C");
pipeline.setHeatTransferCoefficient(15.0); // W/(m2 K), selects SPECIFIED_U
pipeline.run();

String regime = pipeline.getFlowRegime();
PipeBeggsAndBrills.FlowRegime regimeEnum = pipeline.getFlowRegimeEnum();
double[] pressureBara = pipeline.getPressureProfile();
double[] temperatureK = pipeline.getTemperatureProfile();
double[] holdup = pipeline.getLiquidHoldupProfile();
for (int i = 0; i < pressureBara.length; i++) {
    logger.info("x={} m; P={} bara; T={} C; HL={}",
        pipeline.getLengthProfile().get(i), pressureBara[i], temperatureK[i] - 273.15,
        holdup[i]);
}
logger.info("Outlet regime: {} ({})", regime, regimeEnum);
logger.info("Outlet superficial velocities: gas={} m/s; liquid={} m/s",
    pipeline.getGasSuperficialVelocityProfile().get(holdup.length - 1),
    pipeline.getLiquidSuperficialVelocityProfile().get(holdup.length - 1));
double netEnthalpyRemovalKW = (inlet.getThermoSystem().getEnthalpy()
    - pipeline.getOutletStream().getThermoSystem().getEnthalpy()) / 1000.0;
logger.info("Net enthalpy-flow removal: {} kW", netEnthalpyRemovalKW);
```

Expect an outlet pressure below the inlet, cooling toward the 4 °C surroundings,
and holdup between zero and one. Check grid sensitivity and the correlation's
applicability before interpreting design results.

### Example 3: Vertical Riser

A synthetic gas/condensate stream rises 500 m over 550 m measured length.
The straight equivalent incline represents the net elevation, not catenary
geometry or dynamic slugging.

<!-- pipeline-doc-test: riser -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

Logger logger = LogManager.getLogger("RiserExample");
SystemSrkEos fluid = new SystemSrkEos(323.15, 100.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("n-decane", 0.15);
fluid.setMixingRule("classic");
Stream inlet = new Stream("Seabed Production", fluid);
inlet.setFlowRate(10000.0, "kg/hr");
inlet.run();

PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("Riser", inlet);
pipeline.setLength(550.0);
pipeline.setDiameter(0.20);
pipeline.setElevation(500.0);
pipeline.setNumberOfIncrements(20);
pipeline.setHeatTransferMode(PipeBeggsAndBrills.HeatTransferMode.ADIABATIC);
pipeline.run();
logger.info("Bottom: {} bara; top: {} bara; incline: {} degrees",
    inlet.getPressure("bara"), pipeline.getOutletPressure("bara"), pipeline.getAngle());
double[] holdup = pipeline.getLiquidHoldupProfile();
logger.info("Outlet regime: {}; liquid holdup: {}", pipeline.getFlowRegime(),
    holdup[holdup.length - 1]);
```

### Example 4: Pipeline with Mechanical Design

This demonstrates the mechanical calculator's input/output units. The required
thickness is a calculation result, not a selected commercial pipe schedule.

<!-- pipeline-doc-test: mechanical-design -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.AdiabaticPipe;

Logger logger = LogManager.getLogger("PipelineDesignExample");
AdiabaticPipe pipeline = new AdiabaticPipe("Gas Pipeline");
pipeline.setDiameter(0.508); // Inner diameter, m
pipeline.setLength(50000.0);
pipeline.setDesignPressure(150.0, "bar");
pipeline.setDesignTemperature(80.0); // C on the pipeline convenience API
pipeline.setMaterialGrade("X65");
pipeline.setDesignCode("ASME_B31_8");
pipeline.setLocationClass(2); // Integer, not "Class 2"
pipeline.setCorrosionAllowance(0.003); // m
double minimumThicknessM = pipeline.calculateMinimumWallThickness();
logger.info("Minimum wall thickness including corrosion allowance: {} mm",
    minimumThicknessM * 1000.0);
```

## Related Documentation

- [Pipeline Mechanical Design](../pipeline_mechanical_design) - Wall thickness, stress analysis, cost estimation
- [Mechanical Design Standards](../mechanical_design_standards) - ASME, DNV, API standards
- [Fluid Mechanics](../../fluidmechanics/) - Detailed flow modeling
- [Valves](valves) - Flow control devices
- [Equipment Index](index.md) - All equipment types
