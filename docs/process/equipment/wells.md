---
title: Well and Reservoir Equipment
description: Runnable well inflow, tubing, choke, nodal-analysis, and production-manifold examples using current NeqSim APIs.
---

Build a producing-well model from reservoir inflow, wellbore hydraulics, and surface equipment.
Keep bottom-hole pressure distinct from wellhead pressure, and specify whether a rate is gas
standard volume, stock-tank liquid volume, or actual volume at process conditions.

## Table of Contents

- [Overview](#overview)
- [Well Types](#well-types)
- [IPR Curves](#ipr-curves)
- [Wellbore Hydraulics](#wellbore-hydraulics)
- [Choke Modeling](#choke-modeling)
- [Nodal Analysis](#nodal-analysis)
- [Gas Lift](#gas-lift)
- [ESP](#esp)
- [Usage Examples](#usage-examples)
- [GOR and Water Cut](#gor-and-water-cut)

## Overview

| Class | Package | Role |
| --- | --- | --- |
| `WellFlow` | `neqsim.process.equipment.reservoir` | Inflow performance from reservoir to flowing bottom hole |
| `SimpleReservoir` | `neqsim.process.equipment.reservoir` | Lumped reservoir fluid inventory and producers/injectors |
| `WellSystem` | `neqsim.process.equipment.reservoir` | Integrated IPR/VLP operating-point workflow |
| `TubingPerformance` | `neqsim.process.equipment.reservoir` | Tubing correlations and temperature models |
| `PipeBeggsAndBrills` | `neqsim.process.equipment.pipeline` | Pressure drop along an inclined pipe or tubing |
| `ThrottlingValve` | `neqsim.process.equipment.valve` | Pressure reduction and valve/choke calculations |
| `ESPPump` | `neqsim.process.equipment.pump` | Electrical submersible pump equipment |

Each Java block is a complete Java 8 program. Save it using its public class name and run with
NeqSim and its dependencies on the classpath. Enable assertions with `java -ea` to verify the
stated results. `ControllersAndWellsDocumentationTest` compiles and runs these exact blocks.

## Well Types

Use `WellFlow` for an inflow relationship and add separate tubing and surface equipment as
needed. Its outlet represents the **flowing bottom hole**, not the wellhead. For measured
wellhead boundary conditions, a `Stream` is sufficient, as in the production-manifold example.

The formerly documented `SimpleWell`, `GasLiftWell`, `ESPWell`, and `ChokeValve` classes do not
exist. Choose the equipment above or an explicit well-network model instead.

## IPR Curves

For the single-layer gas production-index model:

$$q = PI\left(P_r^2-P_{wf}^2\right)$$

`WellFlow.setWellProductionIndex` uses **MSm3/day/bar²**, with M meaning one million.
Both pressures are absolute, in bara. `WellSystem.setProductionIndex` accepts a unit string
and commonly uses **Sm3/day/bar²**; the numerical PI values differ by a factor of one million.

```java
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

public class GasWellInflowExample {
  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(358.15, 250.0); // K, bara
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");
    Stream reservoir = new Stream("reservoir boundary", fluid);
    reservoir.setFlowRate(0.50, "MSm3/day");
    reservoir.run();

    WellFlow inflow = new WellFlow("gas-well IPR");
    inflow.setInletStream(reservoir);
    inflow.setWellProductionIndex(1.0e-4); // MSm3/day/bar2
    inflow.run(); // Default: calculate flowing bottom-hole pressure from inlet rate.

    double expectedBhp = Math.sqrt(250.0 * 250.0 - 0.50 / 1.0e-4);
    assert Math.abs(inflow.getOutletStream().getPressure("bara") - expectedBhp) < 1.0e-8;
    assert Math.abs(inflow.getOutletStream().getFlowRate("Sm3/day") - 500000.0) < 1.0e-4;

    // Alternative boundary condition: solve the produced rate at 230 bara BHP.
    inflow.setOutletPressure(230.0, "bara");
    inflow.solveFlowFromOutletPressure(true);
    inflow.run();
    double expectedRate = 1.0e-4 * (250.0 * 250.0 - 230.0 * 230.0);
    assert Math.abs(inflow.getOutletStream().getFlowRate("MSm3/day") - expectedRate) < 1.0e-10;
    assert Math.abs(inflow.getOutletStream().getPressure("bara") - 230.0) < 1.0e-8;
  }
}
```

Expected results are approximately 239.79 bara for the first calculation and 0.96 MSm3/day for
the second. In flow-solving mode, consume the calculated outlet rate; the inlet stream is a
reservoir boundary and its original rate is not automatically updated.

### Other Inflow Relationships

`WellFlow` also offers Vogel, Fetkovich, non-Darcy backpressure, table-driven, and liquid-inflow
relationships. Select them through the corresponding parameter methods rather than a string
`setIPRModel` call. The legacy Vogel, Fetkovich, backpressure, and table branches use MSm3/day
internally. Do not pass a stock-tank oil rate in Sm3/day to those branches without reconciling
the model's rate definition.

For oil wells, `InflowPerformance` with `WellFlow.setInflowPerformance` and `setLiquidRate`
provides a dedicated stock-tank liquid-rate basis in Sm3/day. A Vogel relation has the form

$$q = q_{max}\left[1-0.2\frac{P_{wf}}{P_r}-0.8\left(\frac{P_{wf}}{P_r}\right)^2\right].$$

Its rate basis and applicability must match the well test used to fit the curve. See the
[Well Simulation Guide](../../simulation/well_simulation_guide.md) and
[Integrated Production Modelling](../../fielddevelopment/INTEGRATED_PRODUCTION_MODELLING.md)
for the available inflow models and calibration workflows.

## Wellbore Hydraulics

`PipeBeggsAndBrills` uses length, elevation change, diameter, and wall roughness in metres.
Positive elevation represents upward flow. Its outlet is the wellhead when its inlet is the
bottom-hole stream. The next example explicitly includes 3000 m of vertical tubing.

`TubingPerformance` supplies additional correlations and temperature profiles. In an integrated
`WellSystem`, selecting `setPressureDropCorrelation` alone does not activate its full VLP solver;
select the matching `setVLPSolverMode` as described in the well guide. Check the operating-point
convergence flag and the physical pressure residual before accepting an integrated solution.

## Choke Modeling

Use `ThrottlingValve` for a specified downstream pressure. This performs an isenthalpic pressure
reduction unless configured otherwise. Specifying only outlet pressure does not determine a
unique choke bean size or establish whether the required flow is within a calibrated choke's
capacity. See [Valves](valves.md) for sizing, valve travel, and choking behavior.

## Nodal Analysis

An operating point must satisfy both the IPR and tubing hydraulics. This example varies the
production rate until the calculated wellhead pressure equals a 170 bara target. It brackets
the root, checks the final pressure residual, and only then runs the surface choke to 30 bara.
The bracket and target are specific to this example; recheck them for another fluid or well.

```java
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemSrkEos;

public class GasWellNodalExample {
  private static double wellheadPressure(double rate, Stream reservoir,
      WellFlow inflow, PipeBeggsAndBrills tubing) {
    reservoir.setFlowRate(rate, "MSm3/day");
    reservoir.run();
    inflow.run();
    tubing.run();
    return tubing.getOutletStream().getPressure("bara");
  }

  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(358.15, 250.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");
    Stream reservoir = new Stream("reservoir boundary", fluid);
    reservoir.setFlowRate(0.10, "MSm3/day");
    WellFlow inflow = new WellFlow("well IPR");
    inflow.setInletStream(reservoir);
    inflow.setWellProductionIndex(1.0e-5); // MSm3/day/bar2
    PipeBeggsAndBrills tubing = new PipeBeggsAndBrills("tubing", inflow.getOutletStream());
    tubing.setLength(3000.0);
    tubing.setElevation(3000.0);
    tubing.setDiameter(0.10);
    tubing.setPipeWallRoughness(5.0e-5);
    tubing.setNumberOfIncrements(20);

    double target = 170.0; // bara at the wellhead
    double low = 0.01; // MSm3/day
    double high = 0.40;
    double lowPressure = wellheadPressure(low, reservoir, inflow, tubing);
    double highPressure = wellheadPressure(high, reservoir, inflow, tubing);
    if (!(lowPressure > target && highPressure < target)) {
      throw new IllegalStateException("Rate bracket does not enclose the wellhead-pressure target");
    }
    for (int i = 0; i < 30; i++) {
      double rate = 0.5 * (low + high);
      double pressure = wellheadPressure(rate, reservoir, inflow, tubing);
      if (pressure > target) {
        low = rate;
      } else {
        high = rate;
      }
    }
    double operatingRate = 0.5 * (low + high);
    double wellhead = wellheadPressure(operatingRate, reservoir, inflow, tubing);
    double bhp = inflow.getOutletStream().getPressure("bara");
    assert operatingRate > 0.01 && operatingRate < 0.40;
    assert Math.abs(wellhead - target) < 1.0e-4;
    assert bhp > wellhead && bhp < 250.0;
    assert Math.abs(operatingRate - 1.0e-5 * (250.0 * 250.0 - bhp * bhp)) < 1.0e-9;

    ThrottlingValve choke = new ThrottlingValve("wellhead choke", tubing.getOutletStream());
    choke.setOutletPressure(30.0, "bara");
    choke.run();
    double inletMass = tubing.getOutletStream().getFlowRate("kg/hr");
    double outletMass = choke.getOutletStream().getFlowRate("kg/hr");
    assert Math.abs(choke.getOutletStream().getPressure("bara") - 30.0) < 1.0e-8;
    assert Math.abs(outletMass - inletMass) < inletMass * 1.0e-8;
    double inletEnthalpy = tubing.getOutletStream().getFluid().getEnthalpy();
    double outletEnthalpy = choke.getOutletStream().getFluid().getEnthalpy();
    assert Math.abs(outletEnthalpy - inletEnthalpy) < Math.max(Math.abs(inletEnthalpy), 1.0) * 1.0e-5;
  }
}
```

## Gas Lift

Include an injection-gas stream and its mass and energy balance when modeling gas lift through
a flowsheet. `GasLiftPerformanceCurve`, `GasLiftNetworkOptimizer`, and `ChokeableGasLiftWell`
provide performance-curve and allocation workflows in `neqsim.process.fielddevelopment.integrated`.
See [Integrated Production Modelling](../../fielddevelopment/INTEGRATED_PRODUCTION_MODELLING.md)
and [Production Well Networks](production_well_networks.md). A lift-gas rate by itself does not
define the injection depth, gas composition, or a complete wellbore model.

## ESP

Use `neqsim.process.equipment.pump.ESPPump` between explicit inlet and outlet wellbore sections.
Pump inlet pressure, free-gas fraction, pump performance, efficiency, and downstream hydraulics
determine the operating point. See the [ESP Pump Tutorial](../../examples/ESP_Pump_Tutorial.md)
for the equipment API and multiphase considerations.

## Usage Examples

### Example: Production System

When wellhead pressure and mass rate are given, represent each well as a boundary stream.
This example combines two such streams through individual pressure-reduction valves and a
manifold, then separates gas, oil, and water at 30 bara. It does not calculate reservoir
deliverability or depletion.

```java
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemPrEos;

public class ProductionManifoldExample {
  public static void main(String[] args) {
    SystemPrEos fluid = new SystemPrEos(323.15, 50.0);
    fluid.addComponent("methane", 0.40);
    fluid.addComponent("n-heptane", 0.40);
    fluid.addComponent("water", 0.20);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream well1 = new Stream("well 1 boundary", fluid);
    well1.setFlowRate(10000.0, "kg/hr");
    Stream well2 = new Stream("well 2 boundary", fluid.clone());
    well2.setPressure(45.0, "bara");
    well2.setFlowRate(8000.0, "kg/hr");

    ThrottlingValve choke1 = new ThrottlingValve("choke 1", well1);
    choke1.setOutletPressure(30.0, "bara");
    ThrottlingValve choke2 = new ThrottlingValve("choke 2", well2);
    choke2.setOutletPressure(30.0, "bara");
    Mixer manifold = new Mixer("production manifold");
    manifold.addStream(choke1.getOutletStream());
    manifold.addStream(choke2.getOutletStream());
    ThreePhaseSeparator separator = new ThreePhaseSeparator("production separator", manifold.getOutletStream());

    ProcessSystem process = new ProcessSystem("two-well production manifold");
    process.add(well1);
    process.add(well2);
    process.add(choke1);
    process.add(choke2);
    process.add(manifold);
    process.add(separator);
    process.run();

    double gasMass = separator.getGasOutStream().getFlowRate("kg/hr");
    double oilMass = separator.getOilOutStream().getFlowRate("kg/hr");
    double waterMass = separator.getWaterOutStream().getFlowRate("kg/hr");
    assert gasMass > 0.0 && oilMass > 0.0 && waterMass > 0.0;
    assert Math.abs(gasMass + oilMass + waterMass - 18000.0) < 18000.0 * 1.0e-7;
    assert Math.abs(separator.getGasOutStream().getPressure("bara") - 30.0) < 1.0e-8;

    double oilVolume = separator.getOilOutStream().getFlowRate("m3/hr");
    double waterVolume = separator.getWaterOutStream().getFlowRate("m3/hr");
    double waterCut = waterVolume / (oilVolume + waterVolume);
    assert waterCut > 0.0 && waterCut < 1.0;
  }
}
```

## GOR and Water Cut

The water-cut calculation above uses **actual liquid volumes at separator conditions**. Report
that basis with the result. A stock-tank water cut requires conditioning the liquids to the
specified stock-tank conditions first.

Stock-tank GOR is the gas standard volume divided by the stabilized oil liquid volume on its
specified reference basis. Calling `getFlowRate("Sm3/day")` on an oil stream does not perform
stock-tank separation or account for the gas liberated during stabilization. Model the relevant
separation stages and use the [PVT Laboratory Tests](../../pvtsimulation/pvt_lab_tests.md) guide
for separator-test and differential-liberation workflows.

## Related Documentation

- [Well Simulation Guide](../../simulation/well_simulation_guide.md)
- [Well and Choke Simulation](../../simulation/well_and_choke_simulation.md)
- [Production Well Networks](production_well_networks.md)
- [Valves](valves.md)
- [Separators](separators.md)
