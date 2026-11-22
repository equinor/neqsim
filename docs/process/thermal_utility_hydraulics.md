---
title: Thermal utility header hydraulics
description: Convert utility duty to circulation flow and screen an equivalent header for velocity, pressure loss, pump power, and capacity.
---

`ThermalUtilityHydraulicModel` combines a physical utility mass flow with a single-header Darcy-Weisbach calculation. Use it for early sizing, bottleneck checks, and transparent debottlenecking screens.

## Calculation chain

1. Define supply and return states in SI units.
2. Convert a thermal duty to circulation mass flow with `UtilityEnergyBus`.
3. Configure equivalent header geometry and representative fluid properties.
4. Check velocity, Reynolds number, pressure drop, pump power, and capacity.

For pipe diameter $D$, density $\rho$, mass flow $\dot m$, and flow area $A$, the mean velocity is

$$v=\frac{\dot m}{\rho A},\qquad A=\frac{\pi D^2}{4}$$

The model uses the Darcy friction factor $f$ and an aggregate local-loss coefficient $K$: 

$$\Delta p=\left(f\frac{L}{D}+K\right)\frac{\rho v^2}{2}$$

Below Reynolds number 2300, $f=64/\mathrm{Re}$. Above that boundary, the model uses the Haaland approximation. Hydraulic power is $\Delta p\,\dot m/\rho$; shaft power divides that result by pump efficiency.

## Executable cooling-water screen

The example represents a 2 MW cooling duty. The stated specific enthalpies give an 84 kJ/kg utility temperature rise; density and viscosity are representative inputs for screening and are not calculated by the hydraulic model.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.energy.ThermalUtilityHydraulicModel;
import neqsim.process.equipment.energy.ThermalUtilityState;
import neqsim.process.equipment.energy.UtilityEnergyBus;
import neqsim.process.equipment.stream.UtilityLevel;

public final class ThermalUtilityHydraulicsExample {
  private static final Logger logger =
      LogManager.getLogger(ThermalUtilityHydraulicsExample.class);

  private ThermalUtilityHydraulicsExample() {}

  public static void main(String[] args) {
    ThermalUtilityState supply =
        new ThermalUtilityState(293.15, 3.0e5, 84.0e3);
    ThermalUtilityState returns =
        new ThermalUtilityState(313.15, 2.5e5, 168.0e3);
    UtilityEnergyBus coolingWater = new UtilityEnergyBus(
        "cooling water", UtilityLevel.COOLING_WATER, supply, returns);

    double thermalDutyW = 2.0e6;
    double massFlowKgPerSecond = coolingWater.getMassFlowForDuty(thermalDutyW);

    ThermalUtilityHydraulicModel header = new ThermalUtilityHydraulicModel();
    header.setGeometry(1000.0, 0.30, 4.5e-5);
    header.setFluidProperties(998.0, 1.0e-3);
    header.setLocalLossCoefficient(8.0);
    header.setPumpEfficiency(0.80);
    header.setCapacityLimits(2.5, 3.0e5);

    double velocity = header.getVelocity(massFlowKgPerSecond);
    double reynoldsNumber = header.getReynoldsNumber(massFlowKgPerSecond);
    double pressureDropPa = header.getPressureDrop(massFlowKgPerSecond);
    double pumpPowerW = header.getPumpPower(massFlowKgPerSecond);
    double maximumMassFlow = header.getMaximumMassFlow();

    assert Math.abs(massFlowKgPerSecond - 23.8095238095) < 1.0e-9;
    assert velocity > 0.33 && velocity < 0.35;
    assert reynoldsNumber > 1.0e5 && reynoldsNumber < 1.1e5;
    assert pressureDropPa > 3.5e3 && pressureDropPa < 4.5e3;
    assert pumpPowerW > 100.0 && pumpPowerW < 140.0;
    assert header.isWithinCapacity(massFlowKgPerSecond);
    assert maximumMassFlow > massFlowKgPerSecond;

    logger.info(
        "Cooling-water screen: flow={} kg/s, velocity={} m/s, Re={}, dp={} Pa, pump={} W, capacity={} kg/s",
        massFlowKgPerSecond, velocity, reynoldsNumber, pressureDropPa,
        pumpPowerW, maximumMassFlow);
  }
}
```

With assertions enabled, the representative case gives approximately 23.81 kg/s, 0.34 m/s, Reynolds number $1.0\times10^5$, 4.0 kPa pressure loss, and 0.12 kW pump power. Exact values remain tied to the stated screening inputs.

## Input and interpretation boundaries

| Input | Unit | Interpretation |
| --- | --- | --- |
| Length, diameter, roughness | m | One equivalent circular header |
| Density | kg/m³ | Representative value at the screened condition |
| Dynamic viscosity | Pa·s | Representative single-phase value |
| Local-loss coefficient | dimensionless | Sum of valves, bends, strainers, and other minor losses |
| Pump efficiency | fraction | Must be in `(0, 1]` |
| Velocity limit | m/s | User-selected screening limit |
| Pressure-drop limit | Pa | User-selected end-to-end limit |

`getMaximumMassFlow()` returns the limiting mass flow for the configured velocity and pressure-drop limits. An infinite result means neither limit was configured; it is not evidence of unlimited physical capacity.

## Scope

This is a steady, incompressible, single-equivalent-header screen. It does not replace branched-network balancing, compressible Fanno flow, flashing or condensation, two-phase pressure drop, control-valve sizing, surge or water-hammer analysis, or qualified pump and compressor curves. Confirm properties, materials, allowable velocities, pressure design, operability, and vendor limits independently before design use.
