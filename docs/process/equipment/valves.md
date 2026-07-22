---
title: Valve Equipment
description: >-
  Verified NeqSim examples for throttling valves, valve flow coefficients,
  characteristics, mechanical design, and dynamic travel.
keywords: "valve, throttling valve, control valve, Joule-Thomson, Cv, Kv, pressure drop, choke, mechanical design"
---

NeqSim represents pressure letdown and control-valve calculations with classes in
`neqsim.process.equipment.valve`. This page focuses on the current
`ThrottlingValve` API. For pressure-safety valves and production-choke
correlations, use the dedicated guides linked below.

## Available valve classes

| Class | Purpose |
|---|---|
| `ThrottlingValve` | Isenthalpic pressure letdown, specified outlet pressure, or Cv/Kv-based calculation |
| `ControlValve` | Named specialization of `ThrottlingValve` for control applications |
| `SafetyValve` | Scenario-based relieving valve with transient opening and blowdown behavior |
| `SafetyReliefValve` | Dynamic PSV model with configurable opening law and rated Cv |
| `BlowdownValve` | Timed emergency blowdown-valve opening |
| `ESDValve` | Emergency-shutdown valve with stroke-time behavior |

There is no `ChokeValve` class. Represent a production choke with
`ThrottlingValve` and select a multiphase choke model through its
`ValveMechanicalDesign` object.

## Complete pressure-letdown example

The following program creates a gas stream, flashes it through a valve, and
checks the defining isenthalpic relationship. Pressure units are absolute.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class ValveLetdownExample {
  private static final Logger logger = LogManager.getLogger(ValveLetdownExample.class);

  private ValveLetdownExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 80.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("feed", fluid);
    inlet.setFlowRate(10_000.0, "kg/hr");
    inlet.setTemperature(30.0, "C");
    inlet.setPressure(80.0, "bara");
    inlet.run();

    double inletEnthalpy = inlet.getFluid().getEnthalpy("J/mol");
    double inletTemperature = inlet.getTemperature("C");

    ThrottlingValve valve = new ThrottlingValve("PV-100", inlet);
    valve.setOutletPressure(30.0, "bara");
    valve.run();

    double outletEnthalpy = valve.getOutletStream().getFluid().getEnthalpy("J/mol");
    double outletTemperature = valve.getOutletStream().getTemperature("C");
    double enthalpyResidual = outletEnthalpy - inletEnthalpy;

    logger.info("Outlet temperature: {} C", outletTemperature);
    logger.info("Temperature change: {} K", outletTemperature - inletTemperature);
    logger.info("Molar-enthalpy residual: {} J/mol", enthalpyResidual);
  }
}
```

The outlet temperature is calculated by an isenthalpic flash. The sign and
magnitude of the Joule–Thomson temperature change depend on the fluid,
temperature, pressure, and thermodynamic model.

## Cv, Kv, and valve opening

`Cv` uses the US convention and `Kv` the SI convention. NeqSim stores the
coefficient internally as Kv and converts with $C_v = 1.156K_v$.

```java
ThrottlingValve valve = new ThrottlingValve("FV-100", inlet);
valve.setCv(150.0, "US");
valve.setPercentValveOpening(50.0);

double cvUS = valve.getCv("US");
double kvSI = valve.getCv("SI");
double opening = valve.getPercentValveOpening();
```

Setting an outlet pressure and calling `run()` performs a specified-pressure
letdown. To solve outlet pressure from the inlet flow, coefficient, and opening,
set the Cv/Kv and call `setIsCalcOutPressure(true)` before running the valve.
The result depends on the selected gas/liquid sizing behavior and valid inlet
physical properties.

## Valve characteristic and mechanical design

The inherent characteristic belongs to `ValveMechanicalDesign`, not directly to
`ThrottlingValve`. Supported strings include `linear`, `equal percentage`, and
`quick opening`.

```java
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;

ThrottlingValve valve = new ThrottlingValve("PCV-101", inlet);
valve.setOutletPressure(60.0, "bara");
valve.run();

ValveMechanicalDesign design = valve.getMechanicalDesign();
design.setValveCharacterization("equal percentage");
design.setValveSizingStandard("IEC 60534");
design.calcDesign();

String characteristic = design.getValveCharacterization();
int ansiClass = design.getAnsiPressureClass();
double nominalSize = design.getNominalSizeInches();
double actuatorThrust = design.getRequiredActuatorThrust();
double totalWeight = design.getWeightTotal();
```

Mechanical-design results are preliminary sizing estimates. Review the selected
standard, service, correction factors, material requirements, vendor data, and
project design basis before engineering use.

## Production chokes

Use `ThrottlingValve`; configure the choke correlation through the existing
mechanical-design object. Do not import or instantiate `ChokeValve`.

```java
ThrottlingValve choke = new ThrottlingValve("Production choke", wellStream);
choke.setOutletPressure(30.0, "bara");

ValveMechanicalDesign design = choke.getMechanicalDesign();
design.setValveSizingStandard("Sachdeva");
design.setChokeDiameter(32.0, "64ths");
design.setChokeDischargeCoefficient(0.84);
```

The available multiphase methods and their input assumptions are documented in
[Multiphase Choke Flow Models](../MultiphaseChokeFlow.md).

## Dynamic valve travel

`ThrottlingValve` supports linear travel or a first-order lag. The current and
requested openings are separate during a transient.

```java
import java.util.UUID;
import neqsim.process.equipment.valve.ValveTravelModel;

valve.setCalculateSteadyState(false);
valve.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
valve.setTravelTime(10.0);
valve.setPercentValveOpening(20.0);
valve.setTargetPercentValveOpening(80.0);

valve.runTransient(1.0, UUID.randomUUID());

double currentOpening = valve.getPercentValveOpening();
double targetOpening = valve.getTargetPercentValveOpening();
```

For an on/off emergency function with a prescribed stroke time, use `ESDValve`.
For blowdown activation logic, use `BlowdownValve`.

## Physical and numerical checks

For every valve calculation, verify:

- inlet pressure exceeds outlet pressure unless reverse flow is intentionally allowed;
- mass flow is conserved;
- a normal throttling calculation preserves specific enthalpy within numerical tolerance;
- temperature and phase changes are physically plausible for the selected fluid model;
- Cv/Kv units and pressure basis are explicit;
- choked-flow and laminar-flow assumptions match the service;
- valve opening remains within configured minimum and maximum limits.

## Related documentation

- [Control Valves](control_valves.md)
- [Valve Mechanical Design](../ValveMechanicalDesign.md)
- [Multiphase Choke Flow Models](../MultiphaseChokeFlow.md)
- [HIPPS Implementation](../../safety/hipps_implementation.md)
- [Dynamic PSV sizing](../../safety/psv_dynamic_sizing_example.md)
- [Process equipment overview](README.md)
