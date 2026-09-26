---
title: Storage tanks and LNG boil-off
description: Current Tank and LNGTank APIs, units, execution boundaries, and an executable LNG boil-off example.
---

NeqSim provides two related process-equipment models in
`neqsim.process.equipment.tank`:

| Class | Maintained purpose |
| --- | --- |
| `Tank` | A thermodynamic vessel with gas and liquid outlet streams, steady-state execution, optional lumped transient execution, auto-sizing, capacity constraints, and mechanical-design access. |
| `LNGTank` | A `Tank` extension that adds a steady heat-ingress and boil-off-gas (BOG) calculation for an LNG inventory. |

These models do not have the same contract. Use `Tank` for the generic vessel
state and outlet APIs. Use `LNGTank` when the required result is the simplified
steady LNG heat-leak/BOG screen described below.

## API and unit map

`Tank` accepts an inlet `StreamInterface` in its constructor or through
`setInletStream`/`addStream`. Its maintained outlets are
`getGasOutStream()` and `getLiquidOutStream()`.

- `setVolume(double)` takes a volume in m³. There is no unit-string overload.
- The public API does not provide `setLiquidLevel` or `setPressure` methods.
  Pressure and temperature enter through the connected stream state.
- `setDesignResidenceTime(double)` uses seconds, and
  `setDesignLiquidLevel(double)` uses a fraction from 0 to 1.
- `setCalculateSteadyState(false)` selects the lumped transient route.
  A dynamic study must provide consistent inlet/outlet streams, a time step,
  initialization, and controls; it is not created by setting a level and calling
  a no-argument transient loop.

`LNGTank` adds the following explicit inputs and outputs:

| Method | Quantity and unit |
| --- | --- |
| `setAmbientTemperature(value, "C" or "K")` | Ambient temperature |
| `setTankSurfaceArea(value)` | External area in m² |
| `setOverallHeatTransferCoefficient(value)` | Overall coefficient in W/m²/K |
| `setLNGInventory(value)` | LNG inventory in kg |
| `setStoragePressure(value)` | Storage pressure in bara |
| `getHeatIngress()` | Heat ingress in W |
| `getBOGMassFlowRate()` | BOG mass flow in kg/hr |
| `getBoilOffRatePctPerDay()` | Percent of inventory per day |
| `getBOGStream()` | BOG outlet stream |
| `getLNGProductStream()` | LNG liquid outlet stream when created |

`getBoilOffRatePctPerDay()` already returns percent/day; do not multiply it by
100 again.

## Model equations

The steady screen calculates heat ingress as

$$
\dot{Q}_{\mathrm{in}} = U A (T_{\mathrm{ambient}} - T_{\mathrm{LNG}})
$$

and BOG mass flow as

$$
\dot{m}_{\mathrm{BOG}} =
\frac{\dot{Q}_{\mathrm{in}}}{\Delta h_{\mathrm{vap}}}.
$$

Here, `U` is in W/m²/K, `A` is in m², both temperatures use the same absolute
scale, and the latent heat is in J/kg. NeqSim attempts to obtain the latent heat
from the flashed inlet fluid at the storage pressure. If that calculation cannot
supply a usable two-phase value, the implementation retains its documented
default latent heat. The resulting mass rate is converted from kg/s to kg/hr.

## Executable LNG boil-off example

The following is one complete Java 8 program. It uses Log4j2, runs the actual
`LNGTank` implementation, and checks heat-ingress closure plus bounded,
positive BOG results.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.LNGTank;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class LNGTankGuideExample {
  private static final Logger logger = LogManager.getLogger(LNGTankGuideExample.class);

  private LNGTankGuideExample() {}

  public static void main(String[] args) {
    double lngTemperatureC = -162.0;
    double ambientTemperatureC = 25.0;
    double storagePressureBara = 1.1;
    double overallHeatTransferCoefficientWm2K = 0.04;
    double surfaceAreaM2 = 12000.0;
    double inventoryKg = 60000000.0;

    SystemInterface lng = new SystemSrkEos(273.15 + lngTemperatureC, storagePressureBara);
    lng.addComponent("methane", 0.92);
    lng.addComponent("ethane", 0.05);
    lng.addComponent("propane", 0.02);
    lng.addComponent("nitrogen", 0.01);
    lng.setMixingRule("classic");

    Stream feed = new Stream("LNG feed", lng);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(lngTemperatureC, "C");
    feed.setPressure(storagePressureBara, "bara");

    LNGTank tank = new LNGTank("LNG storage", feed);
    tank.setInsulationType(LNGTank.InsulationType.MEMBRANE);
    tank.setAmbientTemperature(ambientTemperatureC, "C");
    tank.setTankSurfaceArea(surfaceAreaM2);
    tank.setOverallHeatTransferCoefficient(overallHeatTransferCoefficientWm2K);
    tank.setLNGInventory(inventoryKg);
    tank.setStoragePressure(storagePressureBara);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(tank);
    process.run();

    double expectedHeatIngressW = overallHeatTransferCoefficientWm2K * surfaceAreaM2
        * (ambientTemperatureC - lngTemperatureC);
    double bogMassFlowKgPerHr = tank.getBOGMassFlowRate();
    double boilOffRatePctPerDay = tank.getBoilOffRatePctPerDay();

    assert Math.abs(tank.getHeatIngress() - expectedHeatIngressW) < 1.0e-6;
    assert bogMassFlowKgPerHr > 0.0;
    assert boilOffRatePctPerDay > 0.0 && boilOffRatePctPerDay < 1.0;
    assert tank.getBOGStream() != null;
    assert Math.abs(tank.getBOGStream().getFlowRate("kg/hr") - bogMassFlowKgPerHr) < 1.0e-6;

    logger.info("Heat ingress: {} W", tank.getHeatIngress());
    logger.info("BOG rate: {} kg/hr ({} %/day)", bogMassFlowKgPerHr, boilOffRatePctPerDay);
  }
}
```

Run documentation examples with assertions enabled (`java -ea`) so the
engineering checks are not skipped.

## Engineering boundaries

This model is an engineering screen, not a detailed storage-tank design or
operations simulator. It does not resolve stratification, rollover, weather
transients, filling and withdrawal schedules, pressure-relief sizing, vent
hydraulics, sloshing, structural loads, or a dynamic vapor-space pressure
balance. It also does not replace a vendor thermal design, an API/NFPA/EN code
assessment, or an independent process-safety review.

Before using the result, confirm:

1. the inlet composition and storage state represent the design case;
2. `U`, area, ambient temperature, inventory, and storage pressure use the
   units listed above;
3. the calculated latent heat is appropriate for the fluid and pressure;
4. BOG handling, pressure control, relief, and disposal capacity are assessed
   in their dedicated models.

For API 650/620 mechanical screening, see
[Mechanical design](../mechanical_design). For API 2000 vent-demand and
rated-capacity screening, see
[API 2000 tank venting](../mechanical_design/api_2000_tank_venting).

## Related documentation

- [Equipment index](index.md)
- [Dynamic simulation](../../simulation/dynamic_simulation_guide)
- [Process safety and release models](../safety/release-flow-models)
