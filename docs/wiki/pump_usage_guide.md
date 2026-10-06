---
title: "Pump Usage Guide - Quick Reference"
description: "Executable quick reference for liquid-pump curves, power, NPSH screening, units, and engineering boundaries in NeqSim."
---

Use this guide for a compact, copy-paste pump calculation. The example uses a vendor-style
head, efficiency, and required-net-positive-suction-head (NPSHr) map. For API 610 screening,
mechanical design, response JSON, and detailed model ownership, continue with the
[comprehensive pump guide](../process/equipment/pumps).

## Calculation basis

| Item | Basis in the example |
| --- | --- |
| Thermodynamic state | 298.15 K and 5.0 bara |
| Liquid | Pure n-hexane with the classic mixing rule |
| Actual inlet flow | 100.0 m3/hr |
| Vendor-map speed | 1000 rpm |
| Head curve | metres of pumped liquid |
| Efficiency curve | percent |
| NPSHr curve | metres of pumped liquid |
| Reported shaft power | kW |

The program is a screening example, not a vendor selection. Run it with assertions enabled
(`-ea`); the assertions are part of the published engineering evidence.

## Executable Java example

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class PumpUsageGuideExample {
  private static final Logger logger = LogManager.getLogger(PumpUsageGuideExample.class);

  private PumpUsageGuideExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 5.0);
    fluid.addComponent("n-hexane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("pump feed", fluid);
    feed.setFlowRate(100.0, "m3/hr");
    feed.run();

    Pump pump = new Pump("P-100", feed);
    double[] speedRpm = new double[] {1000.0};
    double[][] flowM3PerHour = new double[][] {{50.0, 75.0, 100.0, 125.0, 150.0}};
    double[][] headM = new double[][] {{120.0, 115.0, 105.0, 90.0, 70.0}};
    double[][] efficiencyPercent = new double[][] {{65.0, 75.0, 82.0, 78.0, 68.0}};
    double[][] npshRequiredM = new double[][] {{2.0, 2.4, 3.0, 4.0, 5.5}};

    pump.getPumpChart().setCurves(
        new double[] {}, speedRpm, flowM3PerHour, headM, efficiencyPercent);
    pump.getPumpChart().setHeadUnit("meter");
    pump.getPumpChart().setNPSHCurve(npshRequiredM);
    pump.setSpeed(1000.0);
    pump.setCheckNPSH(true);
    pump.setNPSHMargin(1.15);
    pump.run();

    double actualFlowM3PerHour = feed.getFlowRate("m3/hr");
    double vendorHeadM =
        pump.getPumpChart().getHead(actualFlowM3PerHour, pump.getSpeed());
    double powerKw = pump.getPower("kW");
    double npshAvailableM = pump.getNPSHAvailable();
    double requiredNpshM = pump.getNPSHRequired();

    assert Math.abs(actualFlowM3PerHour - 100.0) < 1.0e-9 : "Unexpected inlet flow";
    assert Math.abs(vendorHeadM - 105.0) < 1.0e-9 : "Unexpected map head";
    assert Double.isFinite(powerKw) && powerKw > 0.0 : "Pump power must be positive";
    assert Double.isFinite(npshAvailableM) : "NPSHa calculation failed";
    assert Math.abs(requiredNpshM - 3.0) < 0.02 : "Unexpected map NPSHr";
    assert npshAvailableM > requiredNpshM : "NPSHa must exceed NPSHr";
    assert !pump.isCavitating() : "Configured NPSH margin is not satisfied";

    logger.info(
        "Pump result: flow={} m3/hr, head={} m, power={} kW, NPSHa={} m, NPSHr={} m",
        actualFlowM3PerHour,
        vendorHeadM,
        powerKw,
        npshAvailableM,
        requiredNpshM);
  }
}
```

## What the example verifies

- `setCurves(...)` activates the pump map at the supplied speed, flow, head, and efficiency
  points. All curve arrays must use the same point ordering.
- `setHeadUnit("meter")` declares the head-curve basis. Do not pass a pressure value into this
  curve.
- `setNPSHCurve(...)` supplies NPSHr from vendor data. At 100 m3/hr and 1000 rpm, the example
  interpolates exactly 3.0 m.
- `getNPSHAvailable()` estimates NPSHa from absolute suction pressure, calculated vapor
  pressure, liquid density, and zero velocity-head contribution. Elevation is not added inside
  this method; represent upstream pressure losses and static head in the suction system before
  the pump inlet.
- `setNPSHMargin(1.15)` makes `isCavitating()` compare NPSHa with 1.15 times NPSHr. A failed or
  non-finite NPSH calculation is treated as cavitation risk when checking is enabled.

## Common operating modes

| Objective | Configuration | Boundary |
| --- | --- | --- |
| Specify discharge pressure | `setOutletPressure(value, "bara")` | Uses absolute bara; specify efficiency separately when required |
| Use vendor curves | `getPumpChart().setCurves(...)` and `setSpeed(rpm)` | Curve range, interpolation, fluid correction, and speed range require review |
| Specify outlet temperature | `setOutletTemperature(value, "C")` | A calculation mode, not a vendor-performance guarantee |
| Monitor cavitation screening | `setCheckNPSH(true)` and `setNPSHMargin(factor)` | Requires credible suction state and NPSHr evidence |

Do not combine an outlet-pressure target with a vendor curve without first deciding which
quantity owns the operating point. Retain typed `Pump` and `Stream` references so the result can
be embedded in a larger `ProcessSystem`.

## Troubleshooting

| Observation | Check first |
| --- | --- |
| Non-finite NPSHa | Inlet phase, bubble-point calculation, absolute pressure, and initialized density |
| Unexpected head | Head unit, actual-flow unit, speed, curve ordering, and extrapolation outside vendor data |
| Unexpected power | Liquid density, efficiency curve, head basis, and whether the pump is using a map or a specified target |
| Cavitation warning | Suction pressure and temperature, upstream losses, static head, NPSHr data, and the selected margin |
| Result changes after composition update | Re-run the inlet stream and pump after changing composition, temperature, pressure, or flow |

## Engineering limits

This example checks software execution and basic physical consistency. It does not qualify a
pump, suction vessel, piping system, driver, seal system, minimum-flow recycle, transient
startup, or protection function. Use approved vendor curves over their documented speed and
flow ranges, model the full suction system, evaluate maximum and minimum operating cases, and
obtain accountable mechanical, process, electrical, and safety review before design use.
