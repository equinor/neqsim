---
title: "Reference water and seawater cooling"
description: "IAPWS water and fixed-salinity seawater utilities, exchanger energy balances, transport properties and cooling-water system sizing."
---

## Select the utility model

| Task | API | Property basis |
| --- | --- | --- |
| Explicit fresh-water side of a two-stream exchanger | `SystemWaterIF97(T_K, P_bara)` and `HeatExchanger` | IAPWS-IF97 enthalpy, heat capacity and density |
| Explicit seawater side | `SystemSeawater(T_K, P_bara, salinity_gPerKg)` and `HeatExchanger` | IF97 water plus IAPWS-08 saline Gibbs energy, following IAPWS Advisory Note 5 |
| Utility demand for a specified process outlet | `WaterCooler` | Reference utility enthalpy rise; `setCoolingWaterSalinity(35.0)` selects seawater |
| Plant cooling duties, supply flow and pumping | `CoolingWaterSystem` | Reference solution enthalpy difference and supply density |

The reference-system constructors already add one nominal mole of water. Set the
flow with `setTotalFlowRate(...)`; do not add another component or specify an EOS
mixing rule. Each heat-exchanger side keeps its own fluid model. Selecting the
`WATER` physical-property package on an SRK, PR or CPA fluid does **not** replace
its EOS enthalpy or heat capacity with IF97. `WaterCooler` preserves the process
fluid's property package.

For liquid reference cooling water, initialized transport properties use IAPWS
R12-08 viscosity and R15-11 thermal conductivity. Seawater conductivity adds the
pressure-dependent increment of IAPWS G10-15. Seawater viscosity uses the
Sharqawy et al. (2010) salinity multiplier applied to IAPWS water at the actual
pressure. The multiplier's high-pressure use is an engineering extension of
near-atmospheric data. Critical enhancements are omitted in this low-temperature
cooling range. Call `initProperties()` after a flash before reading transport
properties or using them in a rating calculation.

## Reproducible gas cooler

This public synthetic case cools a methane-rich gas with a separate 35 g/kg
seawater stream, then sizes a specified-temperature cooler and its cooling-water
system. The exact Java example is compiled and executed by
`ReferenceCoolingWaterDocumentationTest`.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.heatexchanger.CoolingWaterSystem;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.WaterCooler;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSeawater;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Reference seawater cooling of a synthetic natural gas.
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class CoolingWaterGuideExample {
  private static final Logger logger = LogManager.getLogger(CoolingWaterGuideExample.class);

  /** Prevents instantiation. */
  private CoolingWaterGuideExample() {
  }

  /**
   * Runs explicit-stream and utility-demand cooling examples.
   * @param args unused arguments
   */
  public static void main(String[] args) {
    SystemSrkEos gas = new SystemSrkEos(393.15, 20.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");
    gas.setTotalFlowRate(1.0, "kg/sec");
    Stream hot = new Stream("hot gas", gas);

    SystemSeawater seawater = new SystemSeawater(288.15, 3.0, 35.0);
    seawater.setTotalFlowRate(2.0, "kg/sec");
    Stream cooling = new Stream("seawater supply", seawater);
    HeatExchanger exchanger = new HeatExchanger("E-100", hot, cooling);
    exchanger.setUAvalue(500.0); // W/K, supplied design assumption
    exchanger.setGuessOutTemperature(65.0, "C");

    ProcessSystem process = new ProcessSystem();
    process.add(hot);
    process.add(cooling);
    process.add(exchanger);
    process.run();
    double hotChange = exchanger.getOutStream(0).getFluid().getEnthalpy()
        - hot.getFluid().getEnthalpy();
    double coldChange = exchanger.getOutStream(1).getFluid().getEnthalpy()
        - cooling.getFluid().getEnthalpy();
    if (Math.abs(hotChange + coldChange) > 0.01) {
      throw new IllegalStateException("Exchanger energy balance exceeds 0.01 W");
    }
    exchanger.getOutStream(1).getFluid().initProperties();
    logger.info("Hot outlet {} C; seawater return {} C; rejected heat {} kW",
        exchanger.getOutStream(0).getTemperature("C"),
        exchanger.getOutStream(1).getTemperature("C"), Math.abs(hotChange) / 1000.0);

    WaterCooler demand = new WaterCooler("E-101", hot);
    demand.setOutletTemperature(40.0, "C");
    demand.setWaterInletTemperature(15.0, "C");
    demand.setWaterOutletTemperature(25.0, "C");
    demand.setWaterPressure(3.0, "bara");
    demand.setCoolingWaterSalinity(35.0); // g salt/kg solution
    demand.run();

    CoolingWaterSystem system = new CoolingWaterSystem();
    system.addCoolingRequirement("E-101", -demand.getDuty() / 1000.0, 40.0, 10.0);
    system.setCoolingWaterSupplyTemperature(15.0); // C
    system.setCoolingWaterReturnTemperature(25.0); // C
    system.setCoolingWaterPressure(3.0); // bara, independent of hydraulic pressure drop
    system.setCoolingWaterSalinity(35.0); // g/kg solution
    system.setTowerFanPowerFraction(0.0); // once-through seawater; no cooling tower
    system.calculate();
    if (Math.abs(system.getTotalCWFlowRate() - demand.getCoolingWaterFlowRate("m3/hr")) > 1e-6) {
      throw new IllegalStateException("Cooling utility flow estimates disagree");
    }
    logger.info("Seawater demand {} kg/s or {} actual m3/hr at supply conditions",
        demand.getCoolingWaterFlowRate("kg/sec"), system.getTotalCWFlowRate());
  }
}
```

The same public Java constructors and methods are available through JPype in
Python. Use the installed NeqSim version's documented Java bridge. This API may
be available in source before the next released Python package includes it.

## Balances and interpretation

Utility demand uses temperature-dependent solution enthalpy rather than an
assumed constant heat capacity. This demand does not establish that a particular
UA or installed exchanger can achieve the process target; evaluate that
separately with the two-stream model:

$$\dot m_{cw}=\frac{-Q_{process}}{h(T_{return},P,S)-h(T_{supply},P,S)}$$

Here $Q_{process}$ is negative for cooling and measured in W; $h$ is in J/kg
solution; mass flow is in kg/s. `WaterCooler` accepts mass-flow units and actual
volume units evaluated at the utility inlet. Standard gas-volume units are
rejected. `CoolingWaterSystem.getTotalCWFlowRate()` returns actual m³/h at the
supply state; pump power uses that volume and the separately specified hydraulic
pressure drop. Tower-fan power remains a screening assumption: set its fraction
to zero for a once-through seawater system without a tower.

`HeatExchanger` retains its effectiveness/NTU method with secant heat-capacity
rates. A saline utility capacity probe is bounded by its property envelope,
while maximum heat transfer uses the actual inlet temperature difference. This
allows a 120 °C process inlet with a seawater outlet below 79.85 °C without
evaluating seawater at a fictitious 120 °C endpoint. Fixed-UA operation, specified
process outlet temperature, thermal/hydraulic rating and fouling screening have
balance tests. An actual seawater outlet outside the model envelope is rejected.
This does not qualify internal pinch profiles, boiling, transient hydraulic
response, material selection or exchanger mechanical design.

## Applicability and composition

- The implemented saline utility envelope is 273.15–353 K (0–79.85 °C),
  1–120 bara and 0–120 g/kg solution. Fresh-water utility demand uses the same
  envelope. `SystemWaterIF97` retains its existing compact liquid/steam model
  outside that cooling envelope.
- The saline pressure derivatives are reference-based in the oceanographic
  subset up to 313 K and 42 g/kg. High-temperature or concentrated seawater
  pressure derivatives are extrapolations even at atmospheric pressure. Inspect
  `SeawaterProperties.isWithinPressureReferenceRange(T_K, S_gPerKg)` and report
  this limitation; the broader numerical envelope does not establish equal
  accuracy for every property. Atmospheric caloric properties extend to 353 K
  and 120 g/kg. The industrial model uses IF97 water, rather than IAPWS-95.
- Reference-Composition Salinity is g dissolved sea salt/kg solution. Practical
  salinity 35 corresponds to 35.16504 g/kg for seawater of Reference Composition;
  do not relabel practical salinity as an absolute mass fraction.
- `SystemSeawater` is a **bulk, fixed-salinity utility model**. Its one nominal
  water component carries the mass of the entire solution, with water molar mass
  defining nominal moles. Use mass units for engineering results. It does not
  track salt species, dilution, mixing different salinities, evaporation,
  precipitation, dissolved gas, pH or ion activity. Use an electrolyte model for
  those cases. Glycol, MEG, methanol and arbitrary brines also need their own
  mixture models.
- Salinity survives cloning and serialization. After changing it, rerun the
  fluid and dependent equipment. Change the live stream's fluid after execution;
  `Stream.run()` clones its input, so the original constructor object may no
  longer be the current stream state. `HeatExchanger.needRecalculation()` includes
  the salinity parameter on both sides. Other equipment caching and salt mixing
  have not been qualified for this bulk utility model.

## Validation and sources

`CoolingWaterReferenceTest` checks R12-08/R15-11 Table 4, every G10-15 Table 2
saline conductivity increment, AN5-13(2016) Table A1 at normal salinity/0 °C and
100 g/kg/353 K, and finite-difference equality of heat capacity and the enthalpy
temperature derivative. `ReferenceCoolingWaterTest` checks density/volume
closure, initialized transport properties, zero salinity, bounded PH inversion,
repeat runs, clone ownership and serialization. `ReferenceWaterHeatExchangerTest`
checks process-side model preservation, utility-flow units, both capacity
orderings, changed salinity and exchanger balances. These are published property
and synthetic integration checks, not a field-performance qualification.

The model-specification gate also runs the `SEAWATER_35` fixture: 27 density,
enthalpy and heat-capacity cases at nine independent `iapws` 1.5.5
`SeaWater(IF97=True)` reference states from 278.15 to 308.15 K and 1.01325 to
50 bara. This catalog coverage is restricted to 35 g/kg solution. The IF97 state
control verifies `H = U + PV` and density/volume closure with SI volume conversion
in both the liquid and steam regions.

1. [IAPWS R7-97(2012), Industrial Formulation 1997](https://iapws.org/technical-guidance/release/IF97).
2. [IAPWS R13-08, Thermodynamic Properties of Seawater](https://iapws.org/technical-guidance/release/Seawater).
3. [IAPWS AN5-13(2016), Industrial Calculation of Seawater Properties](https://iapws.org/technical-guidance/release/Advise5).
4. [IAPWS R12-08, Viscosity](https://iapws.org/technical-guidance/release/viscosity)
   and [R15-11, Thermal Conductivity](https://iapws.org/technical-guidance/release/ThCond).
5. [IAPWS G10-15, Thermal Conductivity of Seawater](https://iapws.org/technical-guidance/release/Seawater-ThCond).
6. Sharqawy, Lienhard and Zubair (2010),
   [Thermophysical properties of seawater](https://web.mit.edu/lienhard/www/Thermophysical_properties_of_seawater-DWT-16-354-2010.pdf),
   Eqs. 22–23. Equations are implemented from the publications; no MIT MATLAB
   library code is redistributed.
