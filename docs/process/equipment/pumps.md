---
title: Pumps
description: Simulate liquid pumps, vendor curves, NPSH, and API 610 mechanical-design screening.
---

NeqSim's `Pump` models a liquid pump from a process inlet stream. Use either a specified
discharge pressure and isentropic efficiency, a vendor head/efficiency curve, or a specified
discharge temperature. `PumpMechanicalDesign` adds preliminary mechanical-design calculations
and an auditable API 610 screening result.

## Packages

| Class | Package | Purpose |
| --- | --- | --- |
| `Pump` | `neqsim.process.equipment.pump` | Steady-state and transient process calculation |
| `PumpChartInterface` | `neqsim.process.equipment.pump` | Vendor head, efficiency, and NPSHr curves |
| `PumpMechanicalDesign` | `neqsim.process.mechanicaldesign.pump` | Preliminary sizing and design outputs |
| `PumpApi610DesignCalculator` | `neqsim.process.mechanicaldesign.pump` | Structured API 610 screening checks |

## Calculation modes

### Specified discharge pressure

Set the discharge pressure and isentropic efficiency before calling `run()`:

```java
pump.setOutletPressure(15.0, "bara");
pump.setIsentropicEfficiency(0.78);
pump.run();
```

The absorbed shaft power is based on the isentropic enthalpy rise divided by the specified
efficiency:

$$
P = \frac{\dot m\left(h_{2s}-h_1\right)}{\eta_s}
$$

Here $P$ is shaft power, $\dot m$ is mass flow, $h_1$ is inlet specific enthalpy,
$h_{2s}$ is the isentropic discharge enthalpy, and $\eta_s$ is isentropic efficiency.

### Vendor performance curves

Supply one row per speed to `PumpChartInterface.setCurves(...)`. Flow is in m³/h,
efficiency is supplied in percent, and the head unit is set separately. The NPSHr array must
have the same speed/flow layout.

```java
double[] speed = new double[] { 1000.0 };
double[][] flow = new double[][] { { 50.0, 75.0, 100.0, 125.0, 150.0 } };
double[][] head = new double[][] { { 120.0, 115.0, 105.0, 90.0, 70.0 } };
double[][] efficiency = new double[][] { { 65.0, 75.0, 82.0, 78.0, 68.0 } };
double[][] npshRequired = new double[][] { { 2.0, 2.4, 3.0, 4.0, 5.5 } };

pump.getPumpChart().setCurves(new double[] {}, speed, flow, head, efficiency);
pump.getPumpChart().setHeadUnit("meter");
pump.getPumpChart().setNPSHCurve(npshRequired);
pump.setSpeed(1000.0);
pump.run();
```

Do not use `setHeadCurve`, `setEfficiencyCurve`, or `setNPSHRequiredCurve`; those convenience
methods are not part of the current `Pump` API.

### Specified discharge temperature

Calling `setOutletTemperature(...)` selects the fixed-temperature mode. NeqSim performs a TP
flash at the specified discharge temperature and pressure and back-calculates power from the
inlet/outlet enthalpy difference. This mode represents a measured or externally specified
discharge state; it does not evaluate a vendor performance curve.

```java
pump.setOutletPressure(15.0, "bara");
pump.setOutletTemperature(35.0, "C");
pump.run();
```

`setOutTemperature(double)` is deprecated. Use `setOutletTemperature(double)` for kelvin or
`setOutletTemperature(double, String)` for an explicit unit.

## NPSH screening

`getNPSHAvailable()` returns metres of liquid head. It clones the inlet fluid, calculates its
bubble-point pressure at the inlet temperature, and evaluates the pressure head. The current
implementation assumes zero velocity-head contribution and expects static elevation effects to
already be represented in the suction system:

$$
NPSH_A = \frac{P_s-P_v}{\rho g}
$$

where $P_s$ is absolute suction pressure, $P_v$ is bubble-point pressure, $\rho$ is inlet-fluid
density, and $g$ is gravitational acceleration. A failed bubble-point calculation returns
`Double.NaN`; it is not interpreted as a large safe margin.

`getNPSHRequired()` returns the interpolated vendor NPSHr when a chart is present. Without a
vendor NPSHr curve it returns a coarse flow-based screening estimate. Treat that fallback as a
data gap, not as vendor qualification.

```java
double npshAvailableM = pump.getNPSHAvailable();
double npshRequiredM = pump.getNPSHRequired();
pump.setCheckNPSH(true);
pump.setNPSHMargin(1.15);
boolean cavitationRisk = pump.isCavitating();
```

The getter methods do not accept unit strings; both values are returned in metres.

## API 610 mechanical-design screening

`PumpMechanicalDesign` combines the simulated duty with purchaser inputs and vendor curve data.
Its `PumpApi610DesignCalculator` reports each check as `PASS`, `WARNING`, `FAIL`, or
`NOT_EVALUATED`, and combines them into `PASS`, `PASS_WITH_WARNINGS`, `FAIL`, or
`NOT_EVALUATED`.

The screen covers:

- rated flow relative to vendor BEP, rated-point region, POR, and AOR;
- NPSHa/NPSHr head and ratio margins;
- maximum suction pressure, shutoff head, furnished casing MAWP, and preliminary hydrotest pressure;
- absorbed power, driver margin, and selection from configured driver ratings;
- head rise to shutoff when vendor or purchaser evidence is available;
- ISO 281 basic rating life when rolling-element bearing loads are supplied; and
- optional shaft-deflection, critical-speed, nozzle-load, and vibration evidence.

Missing purchaser or vendor values remain `NOT_EVALUATED`. NeqSim does not certify casing,
rotor, bearing, nozzle, seal, baseplate, material, inspection, or test compliance. Use the
purchased standard edition, project specification, and vendor documentation for final design.

## Complete Java example

This Java 8 example runs a water pump with a vendor curve, checks NPSH, performs the API 610
screen, and exports the structured mechanical-design response.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator;
import neqsim.process.mechanicaldesign.pump.PumpMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class PumpDesignExample {
  private static final Logger logger = LogManager.getLogger(PumpDesignExample.class);

  private PumpDesignExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 5.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("pump feed", fluid);
    feed.setFlowRate(100.0, "m3/hr");
    feed.run();

    Pump pump = new Pump("P-100", feed);
    double[] speed = new double[] { 1000.0 };
    double[][] flow = new double[][] { { 50.0, 75.0, 100.0, 125.0, 150.0 } };
    double[][] head = new double[][] { { 120.0, 115.0, 105.0, 90.0, 70.0 } };
    double[][] efficiency = new double[][] { { 65.0, 75.0, 82.0, 78.0, 68.0 } };
    double[][] npshRequired = new double[][] { { 2.0, 2.4, 3.0, 4.0, 5.5 } };

    pump.getPumpChart().setCurves(new double[] {}, speed, flow, head, efficiency);
    pump.getPumpChart().setHeadUnit("meter");
    pump.getPumpChart().setNPSHCurve(npshRequired);
    pump.setSpeed(1000.0);
    pump.setCheckNPSH(true);
    pump.setNPSHMargin(1.15);
    pump.run();

    double powerKw = pump.getPower("kW");
    double vendorHeadM =
        pump.getPumpChart().getHead(feed.getFlowRate("m3/hr"), pump.getSpeed());
    double npshAvailableM = pump.getNPSHAvailable();
    double npshRequiredM = pump.getNPSHRequired();

    PumpMechanicalDesign design = pump.getMechanicalDesign();
    design.setApi610PumpType(PumpApi610DesignCalculator.Api610PumpType.OH2);
    design.setMaximumSuctionPressure(8.0);
    design.setFurnishedCasingMawp(25.0);
    design.calcDesign();

    PumpApi610DesignCalculator assessment = design.getApi610Assessment();
    PumpApi610DesignCalculator.AssessmentStatus status = assessment.getAssessmentStatus();
    String responseJson = design.getResponse().toJson();

    logger.info("Power: {} kW; vendor head: {} m", powerKw, vendorHeadM);
    logger.info("NPSHa: {} m; NPSHr: {} m", npshAvailableM, npshRequiredM);
    logger.info("API 610 status: {}; response: {}", status, responseJson);
  }
}
```

## Interpreting results

| Result | Interpretation |
| --- | --- |
| `pump.getPower("kW")` | Absorbed shaft power calculated by the process model |
| `pump.getPumpChart().getHead(flow, speed)` | Vendor-curve head in the configured head unit |
| `pump.getNPSHAvailable()` | Process-side NPSHa in metres, or `NaN` if unavailable |
| `pump.getNPSHRequired()` | Vendor curve value or coarse fallback estimate in metres |
| `assessment.getOperatingRegion()` | Rated-point classification relative to vendor BEP |
| `assessment.getSelectedDriverPowerKw()` | First configured rating meeting the required driver power |
| `assessment.getRequiredCasingPressureBara()` | Screening casing-pressure requirement in bara |
| `assessment.getChecks()` | Immutable list of individual checks and missing-evidence findings |

## Limitations

- The process model is a one-dimensional steady-state equipment calculation; it does not resolve
  internal impeller or volute CFD.
- Vendor curves govern realistic head, efficiency, NPSHr, runout, and shutoff behaviour.
- The fallback NPSHr correlation is only a preliminary screen.
- The mechanical-design calculation provides screening dimensions and evidence status, not a
  certificate of conformity or accountable design approval.
- Positive-displacement pumps require equipment and standards models appropriate to their type;
  this page describes the centrifugal `Pump` implementation.

## Related documentation

- [Equipment index](index.md)
- [Mechanical design](../mechanical_design.md)
- [Compressors](compressors.md)
- [Separators](separators.md)
