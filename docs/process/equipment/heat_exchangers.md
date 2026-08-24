---
title: Heat Exchanger Equipment
description: Source-anchored guide to heater, cooler, two-stream heat-exchanger, sizing, and dynamic heat-transfer APIs in NeqSim.
keywords: "heat exchanger, heater, cooler, UA, duty, effectiveness, LMTD, dynamic heat exchanger, auto sizing"
---

# Heat Exchanger Equipment

NeqSim provides single-stream temperature or duty equipment and a two-stream heat exchanger.
This guide separates their specifications and result APIs so that a model does not accidentally
mix heater, exchanger, column-condenser, or mechanical-design semantics.

## API ownership

| Equipment | Package | Primary use |
|---|---|---|
| `Heater` | `neqsim.process.equipment.heatexchanger` | Add heat, specify an outlet temperature, or connect a heat-duty stream |
| `Cooler` | `neqsim.process.equipment.heatexchanger` | The `Heater` calculation with cooler naming and typically a lower outlet temperature |
| `HeatExchanger` | `neqsim.process.equipment.heatexchanger` | Exchange heat between exactly two process streams |
| `MultiStreamHeatExchanger2` | `neqsim.process.equipment.heatexchanger` | Exchange heat among more than two streams; see the [multi-stream guide](multistream_heat_exchanger) |
| `ReBoiler` | `neqsim.process.equipment.heatexchanger` | Apply a specified reboiler duty to one stream |
| `Condenser` | `neqsim.process.equipment.distillation` | Model a distillation-column condenser and reflux split; it is not a two-stream exchanger |

`WaterCooler`, air-cooler, steam-heater, and detailed shell-and-tube calculations have dedicated
guides under [water cooler and reboiler](water_cooler_reboiler),
[air cooler](../../wiki/air_cooler), [steam heater](../../wiki/steam_heater), and
[thermal-hydraulic design](../mechanical_design/thermal_hydraulic_design).

## Runnable two-stream quick start

The following complete Java program creates independent hot and cold inlet streams, sets a UA in
W/K, solves the exchanger, and checks the energy-transfer direction. `getDuty()` is reported in W;
use `Math.abs(...)` when the engineering question is the transferred-duty magnitude because the
sign follows the internally selected calculation side.

```java
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class HeatExchangerGuideExample {
  private HeatExchangerGuideExample() {}

  public static void main(String[] args) {
    SystemInterface gas = new SystemSrkEos(303.15, 30.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    Stream hot = new Stream("hot feed", gas);
    hot.setTemperature(100.0, "C");
    hot.setFlowRate(10000.0, "kg/hr");
    hot.run();

    Stream cold = new Stream("cold feed", gas.clone());
    cold.setTemperature(20.0, "C");
    cold.setFlowRate(8000.0, "kg/hr");
    cold.run();

    HeatExchanger exchanger = new HeatExchanger("E-100", hot, cold);
    exchanger.setUAvalue(5000.0);
    exchanger.setGuessOutTemperature(70.0, "C");
    exchanger.run();

    double hotOutletC = exchanger.getOutStream(0).getTemperature("C");
    double coldOutletC = exchanger.getOutStream(1).getTemperature("C");
    double dutyKW = Math.abs(exchanger.getDuty()) / 1000.0;
    double effectiveness = exchanger.getThermalEffectiveness();
    double minimumApproachK = exchanger.getApproachTemperature();

    if (!(hotOutletC < 100.0 && coldOutletC > 20.0 && dutyKW > 0.0)) {
      throw new IllegalStateException("Unexpected heat-exchanger result");
    }

    System.out.printf(
        "hot out %.2f C, cold out %.2f C, duty %.2f kW, effectiveness %.3f, approach %.2f K%n",
        hotOutletC, coldOutletC, dutyKW, effectiveness, minimumApproachK);
  }
}
```

`setGuessOutTemperature(...)` supplies an initial estimate; it is not an outlet specification.
The two outlets retain side indices 0 and 1 from the constructor. `getOutletStream()` returns only
side 0, so use `getOutStream(int)` or `getOutletStreams()` when both sides matter.

## Single-stream heater and cooler specifications

Use `setOutletTemperature(value, unit)` for a unit-bearing heater or cooler temperature
specification. The legacy `setOutTemperature(double)` accepts kelvin only and is deprecated;
there is no `setOutTemperature(double, String)` overload on `Heater` or `Cooler`.

Other supported modes are:

- `setdT(double)` for a temperature difference in kelvin; there is no unit-bearing overload.
- `setEnergyInput(double)` or `setDuty(double)` for a duty in W. Positive duty adds enthalpy and
  negative duty removes it.
- `setEnergyStream(EnergyStream)` to use a connected energy stream as the specification.

The most recently selected temperature, duty, or energy-stream mode controls the calculation.
After `run()`, read `getDuty()` in W or `getDuty(unit)` in a supported power unit such as `"kW"`.

## Two-stream exchanger specifications

### UA mode

`setUAvalue(double)` stores UA in W/K. In this mode, give a reasonable initial outlet estimate
with `setGuessOutTemperature(value, unit)` and solve the exchanger. Read the retained setting with
`getUAvalue()`.

For a counter-current exchanger,

$$Q=UA\Delta T_{\mathrm{lm}}$$

with

$$\Delta T_{\mathrm{lm}}=\frac{\Delta T_1-\Delta T_2}{\ln(\Delta T_1/\Delta T_2)}$$

where $\Delta T_1=T_{h,in}-T_{c,out}$ and $\Delta T_2=T_{h,out}-T_{c,in}$. The public exchanger
API does not expose `getLMTD()` or `getNTU()`. `getSizingReport()` includes the calculated LMTD;
`getThermalEffectiveness()` returns the solved effectiveness. The array-valued `getEffectiveness()`
and `getNtu()` methods belong to `FoulingScreeningResult`, not to `HeatExchanger` itself.

### Fixed outlet temperature

To pin one exchanger side, first call `setOutStreamSpecificationNumber(0)` or
`setOutStreamSpecificationNumber(1)`, then call `setOutTemperature(value, unit)`. The selected
side is flashed to that temperature at its inlet pressure and the other side is energy-balanced.
This unit-bearing overload exists on `HeatExchanger`; do not confuse it with the heater API.

### Results and checks

After a successful run, inspect:

- `getOutStream(0)` and `getOutStream(1)` for outlet states;
- `getDuty()` for transferred duty in W;
- `getThermalEffectiveness()` for the dimensionless solved effectiveness;
- `getApproachTemperature()` for the current minimum approach in K;
- `getHotColdDutyBalance()` as the exchanger's internal balance diagnostic; and
- `getSizingReport()` or `toJson()` for reporting.

Always confirm hot- and cold-side energy changes independently when using results for design or
optimization. A converged process calculation is not a mechanical guarantee.

## Dynamic model

The dynamic wall model is opt-in. Configure `setDynamicModelEnabled(true)`, a positive wall mass
with `setWallMass(...)`, wall heat capacity with `setWallCp(...)`, heat-transfer area with
`setHeatTransferArea(...)`, and shell/tube heat-transfer coefficients with
`setShellSideHtc(...)` and `setTubeSideHtc(...)`. Advance it with
`runTransient(double dt, UUID id)`, where `dt` is seconds. The no-argument `runTransient()` call
shown in older examples is not a `HeatExchanger` API.

Use a `ProcessSystem` transient workflow when the exchanger is coupled to upstream equipment,
controllers, or recycles; see [dynamic simulation](../dynamic-simulation).

## Auto-sizing and mechanical design

Call `autoSize(safetyFactor)` only after the exchanger has two connected streams and a solved
operating point. The safety factor multiplies the absolute calculated duty. Then inspect
`isAutoSized()` and `getSizingReport()`.

Detailed candidate geometry belongs to `HeatExchangerMechanicalDesign`:

1. Obtain it with `exchanger.getMechanicalDesign()`.
2. Call `calcDesign()` after the process-side duty and temperatures are available.
3. Iterate `getSizingResults()`.
4. Read `HeatExchangerSizingResult.getRequiredArea()`, `getRequiredUA()`,
   `getEstimatedPressureDrop()`, and the other typed result getters.

There is no `HeatExchangerSizingResult.getArea()` method. The calculations are screening and
sizing support; accountable TEMA, materials, vibration, relief, fabrication, and code compliance
remain engineering-review tasks. See the [mechanical-design guide](../../wiki/heat_exchanger_mechanical_design),
[two-phase heat-transfer guide](../mechanical_design/two_phase_heat_transfer), and
[design framework](../DESIGN_FRAMEWORK).

## Condenser and reboiler boundary

`neqsim.process.equipment.distillation.Condenser` is a column-tray component constructed with a
name and configured through condenser/reflux APIs such as `setTotalCondenser(...)` and
`setRefluxRatio(...)`. It does not accept a vapor stream in its constructor and does not provide
heater-style `setOutTemperature(...)`, `setDewPointTemperature(...)`, or `setSubCooling(...)`
methods. Use the owning distillation-column workflow rather than treating it as a stand-alone
cooler.

`ReBoiler` is a simpler two-port unit. `setReboilerDuty(double)` accepts W and adds that enthalpy
to its inlet during `run()`; it does not perform a full column-equilibrium reboiler design.

## Related documentation

- [Multi-stream heat exchanger](multistream_heat_exchanger)
- [Water cooler and reboiler](water_cooler_reboiler)
- [Thermal-hydraulic design](../mechanical_design/thermal_hydraulic_design)
- [Two-phase heat transfer](../mechanical_design/two_phase_heat_transfer)
- [Heat-exchanger mechanical design](../../wiki/heat_exchanger_mechanical_design)
- [Process package](../)
