---
title: TEG Gas Dehydration Tutorial
description: Build and validate a mass-conserving NeqSim screening model for water-saturated natural gas contacting lean triethylene glycol.
---

This tutorial builds a conservative equilibrium-contact model for screening
triethylene glycol (TEG) dehydration. It shows how to saturate a gas with water,
contact it with lean TEG, split the equilibrium phases, and verify total and
water-component balances.

The example is a thermodynamic screening calculation, not a rated absorber or a
complete regeneration plant. It does not predict packing height, mass-transfer
rates, tray efficiency, glycol losses, foaming, corrosion, or regenerator
performance.

## Learning objectives

After completing the tutorial, you can:

- construct a CPA fluid for natural gas, water, and TEG;
- create a reproducible water-saturated gas with `StreamSaturatorUtil`;
- model one equilibrium contact with `Mixer` and `Separator`;
- distinguish molar gas water content from glycol mass purity;
- close total and water-component balances; and
- identify what must be added for equipment design.

## Model boundary

The calculation represents one ideal equilibrium contact:

```text
Dry gas basis -> water saturator --\
                                    mixer -> equilibrium separator -> gas product
Lean TEG --------------------------/                         \-----> rich TEG
```

The gas basis is 1.0 MSm³/day at 30 °C and 70 bara. The solvent flow is
3,000 kg/h and its composition is entered as 99.5 wt% TEG and 0.5 wt% water.
`addComponent(..., "kg/hr")` establishes the mass basis before the stream is
scaled to its operating flow.

`StreamSaturatorUtil` calls the NeqSim water-saturation operation at the feed
state. Do not label an arbitrary fixed water mole fraction as saturated.

## Complete Java example

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

public final class TegEquilibriumScreening {
  private static final Logger logger =
      LogManager.getLogger(TegEquilibriumScreening.class);

  private TegEquilibriumScreening() {}

  private static double componentFlow(
      StreamInterface stream, String componentName) {
    double flow = 0.0;
    for (int phaseNumber = 0;
        phaseNumber < stream.getFluid().getNumberOfPhases();
        phaseNumber++) {
      ComponentInterface component = stream.getFluid()
          .getPhase(phaseNumber).getComponent(componentName);
      if (component != null) {
        flow += component.getFlowRate("kg/hr");
      }
    }
    return flow;
  }

  public static void main(String[] args) {
    SystemSrkCPAstatoil gasFluid =
        new SystemSrkCPAstatoil(273.15 + 30.0, 70.0);
    gasFluid.addComponent("methane", 0.90);
    gasFluid.addComponent("ethane", 0.05);
    gasFluid.addComponent("propane", 0.02);
    gasFluid.addComponent("CO2", 0.02);
    gasFluid.addComponent("nitrogen", 0.01);
    gasFluid.setMixingRule(10);

    Stream gasFeed = new Stream("dry gas basis", gasFluid);
    gasFeed.setFlowRate(1.0, "MSm3/day");
    gasFeed.setTemperature(30.0, "C");
    gasFeed.setPressure(70.0, "bara");

    StreamSaturatorUtil saturator =
        new StreamSaturatorUtil("water saturator", gasFeed);

    SystemSrkCPAstatoil tegFluid =
        new SystemSrkCPAstatoil(273.15 + 30.0, 70.0);
    tegFluid.addComponent("TEG", 99.5, "kg/hr");
    tegFluid.addComponent("water", 0.5, "kg/hr");
    tegFluid.setMixingRule(10);

    Stream leanTeg = new Stream("lean TEG", tegFluid);
    leanTeg.setFlowRate(3000.0, "kg/hr");
    leanTeg.setTemperature(30.0, "C");
    leanTeg.setPressure(70.0, "bara");

    Mixer equilibriumContact = new Mixer("equilibrium contact");
    equilibriumContact.addStream(saturator.getOutletStream());
    equilibriumContact.addStream(leanTeg);

    Separator phaseSplitter = new Separator(
        "gas and rich TEG separator",
        equilibriumContact.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(gasFeed);
    process.add(saturator);
    process.add(leanTeg);
    process.add(equilibriumContact);
    process.add(phaseSplitter);
    process.run();

    StreamInterface wetGas = saturator.getOutletStream();
    StreamInterface productGas = phaseSplitter.getGasOutStream();
    StreamInterface richTeg = phaseSplitter.getLiquidOutStream();

    double wetWater = wetGas.getFluid().getPhase("gas")
        .getComponent("water").getx();
    double productWater = productGas.getFluid().getPhase("gas")
        .getComponent("water").getx();

    double wetWaterFlow = componentFlow(wetGas, "water");
    double leanWaterFlow = componentFlow(leanTeg, "water");
    double productWaterFlow = componentFlow(productGas, "water");
    double richWaterFlow = componentFlow(richTeg, "water");

    double waterResidual = wetWaterFlow + leanWaterFlow
        - productWaterFlow - richWaterFlow;
    double totalMassResidual =
        wetGas.getFlowRate("kg/hr") + leanTeg.getFlowRate("kg/hr")
        - productGas.getFlowRate("kg/hr")
        - richTeg.getFlowRate("kg/hr");

    logger.info("Saturated gas water: {} mol-ppm", wetWater * 1.0e6);
    logger.info("Equilibrium gas water: {} mol-ppm", productWater * 1.0e6);
    logger.info("Water transferred: {} kg/h",
        wetWaterFlow - productWaterFlow);
    logger.info("Water balance residual: {} kg/h", waterResidual);
    logger.info("Total mass residual: {} kg/h", totalMassResidual);
  }
}
```

## Equivalent Python example

Run this in a clean environment after `pip install neqsim`:

```python
from neqsim import jneqsim


SystemSrkCPAstatoil = jneqsim.thermo.system.SystemSrkCPAstatoil
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
StreamSaturatorUtil = jneqsim.process.equipment.util.StreamSaturatorUtil
Mixer = jneqsim.process.equipment.mixer.Mixer
Separator = jneqsim.process.equipment.separator.Separator

gas_fluid = SystemSrkCPAstatoil(273.15 + 30.0, 70.0)
gas_fluid.addComponent("methane", 0.90)
gas_fluid.addComponent("ethane", 0.05)
gas_fluid.addComponent("propane", 0.02)
gas_fluid.addComponent("CO2", 0.02)
gas_fluid.addComponent("nitrogen", 0.01)
gas_fluid.setMixingRule(10)

gas_feed = Stream("dry gas basis", gas_fluid)
gas_feed.setFlowRate(1.0, "MSm3/day")
gas_feed.setTemperature(30.0, "C")
gas_feed.setPressure(70.0, "bara")
saturator = StreamSaturatorUtil("water saturator", gas_feed)

teg_fluid = SystemSrkCPAstatoil(273.15 + 30.0, 70.0)
teg_fluid.addComponent("TEG", 99.5, "kg/hr")
teg_fluid.addComponent("water", 0.5, "kg/hr")
teg_fluid.setMixingRule(10)

lean_teg = Stream("lean TEG", teg_fluid)
lean_teg.setFlowRate(3000.0, "kg/hr")
lean_teg.setTemperature(30.0, "C")
lean_teg.setPressure(70.0, "bara")

equilibrium_contact = Mixer("equilibrium contact")
equilibrium_contact.addStream(saturator.getOutletStream())
equilibrium_contact.addStream(lean_teg)
phase_splitter = Separator(
    "gas and rich TEG separator",
    equilibrium_contact.getOutletStream(),
)

process = ProcessSystem()
process.add(gas_feed)
process.add(saturator)
process.add(lean_teg)
process.add(equilibrium_contact)
process.add(phase_splitter)
process.run()

wet_gas = saturator.getOutletStream()
product_gas = phase_splitter.getGasOutStream()
rich_teg = phase_splitter.getLiquidOutStream()


def component_flow(stream, component_name):
    fluid = stream.getFluid()
    flow = 0.0
    for phase_number in range(fluid.getNumberOfPhases()):
        component = (
            fluid.getPhase(phase_number).getComponent(component_name)
        )
        if component is not None:
            flow += component.getFlowRate("kg/hr")
    return flow


wet_water = wet_gas.getFluid().getPhase("gas").getComponent("water").getx()
product_water = (
    product_gas.getFluid().getPhase("gas").getComponent("water").getx()
)
wet_water_flow = component_flow(wet_gas, "water")
lean_water_flow = component_flow(lean_teg, "water")
product_water_flow = component_flow(product_gas, "water")
rich_water_flow = component_flow(rich_teg, "water")

water_residual = (
    wet_water_flow
    + lean_water_flow
    - product_water_flow
    - rich_water_flow
)
total_mass_residual = (
    wet_gas.getFlowRate("kg/hr")
    + lean_teg.getFlowRate("kg/hr")
    - product_gas.getFlowRate("kg/hr")
    - rich_teg.getFlowRate("kg/hr")
)

print(f"Saturated gas water: {wet_water * 1.0e6:.3f} mol-ppm")
print(
    "Equilibrium gas water: "
    f"{product_water * 1.0e6:.3f} mol-ppm"
)
print(
    "Water transferred: "
    f"{wet_water_flow - product_water_flow:.6f} kg/h"
)
print(f"Water balance residual: {water_residual:.3e} kg/h")
print(f"Total mass residual: {total_mass_residual:.3e} kg/h")
```

## Expected screening results

The clean public-release execution used NeqSim 3.16.0, Python 3.12.13,
and OpenJDK 17.0.19. The focused Java regression also runs against the current
repository implementation. Use the narrow engineering envelopes below rather
than a sub-ppm golden tolerance because compatible solver and runtime changes
can slightly shift the equilibrium result while preserving the model behavior
and balances.

| Result | Clean 3.16.0 result | Regression envelope |
| --- | ---: | ---: |
| Saturated-gas water content | 778.927 mol-ppm | 700--900 mol-ppm |
| Equilibrium product-gas water content | 46.034 mol-ppm | 40--55 mol-ppm |
| Water transferred to the liquid phase | 23.286223 kg/h | 22--25 kg/h |
| Rich-liquid flow | 3,045.903 kg/h | 3,040--3,050 kg/h |
| Water-component residual | $5.54\times10^{-13}$ kg/h | absolute value below $10^{-8}$ kg/h |
| Total mass residual | $3.37\times10^{-11}$ kg/h | absolute value below $10^{-8}$ kg/h |

For example, the Java 21 full-suite run on the current repository head produced
47.685 mol-ppm product-gas water. Both results represent more than 90% removal
of gas-phase water and satisfy the conservation criteria. The rich-liquid
increase is larger than the water transfer because the equilibrium liquid also
absorbs some hydrocarbon and acid gas. Inspect all component balances before
using the result to size downstream regeneration equipment.

## Balance equations

The water transferred from gas to liquid is

$$\dot m_{\mathrm{H_2O,transfer}}=\dot m_{\mathrm{H_2O,wet}}-\dot m_{\mathrm{H_2O,product}}$$

For a conservative contact, the same transfer appears in the solvent:

$$\dot m_{\mathrm{H_2O,transfer}}=\dot m_{\mathrm{H_2O,rich}}-\dot m_{\mathrm{H_2O,lean}}$$

The water residual used by the example is

$$r_{\mathrm{H_2O}}=\dot m_{\mathrm{H_2O,wet}}+\dot m_{\mathrm{H_2O,lean}}-\dot m_{\mathrm{H_2O,product}}-\dot m_{\mathrm{H_2O,rich}}$$

Require the residual to be negligible relative to the inlet water flow. Also
close the total mass balance because gas components can dissolve in TEG.

## Interpretation and limitations

The product result is the equilibrium outcome of one ideal contact. It is useful
for checking model setup, solvent purity sensitivity, temperature sensitivity,
and the thermodynamic lower bound for a specified contact state.

It is not a guaranteed outlet specification. A real contactor requires
rate-based or validated stage-efficiency modeling, packing hydraulics, column
diameter and height, liquid distribution, mist elimination, glycol entrainment,
foaming allowance, and an operating envelope. A full regeneration loop also
requires pressure letdown, flash-gas handling, lean/rich heat exchange,
reboiling and stripping, cooling, pumping, makeup, and recycle convergence.

NeqSim does not currently provide the `GlycolDehydrationModule` API previously
shown on this page. Do not copy that obsolete example. `SimpleTEGAbsorber` is
available for stage-efficiency screening and now conserves the complete gas and
solvent inventories even when the two feeds start with different component
lists. Its gas and rich-TEG outlets close both the water-component and total
mass balances; this behavior is protected by the regression for
[issue #2659](https://github.com/equinor/neqsim/issues/2659).

Outlet construction follows the thermodynamic system's logical gas and aqueous
phase mapping rather than its internal backing-array order. This preserves the
four-port component inventory if a flash has reordered its internal phase
slots or a downstream `Stream` wrapper reflashes an outlet. The absorber checks
the unchanged feed inventory after its TP and PH flashes and after extracting
both outlets; it reports an invalid state instead of rescaling a converged
flash result.

The conservative outlet behavior does not turn `SimpleTEGAbsorber` into a
rate-based equipment model. Continue to apply the packing, hydraulics,
mass-transfer, regeneration, and operating-envelope limitations described
above, and verify component and total balances for every engineering case.

## Sensitivity studies

Change one input at a time and rerun the complete process:

1. Lean-TEG purity on a mass basis.
2. TEG circulation rate in kg/h.
3. Contactor temperature.
4. Gas pressure.
5. Feed composition and acid-gas content.

For each case, record gas water content, transferred water, hydrocarbon
co-absorption, total mass residual, and water residual. A lower equilibrium gas
water content is not automatically a better plant design if glycol circulation,
hydrocarbon loss, regeneration duty, or emissions increase.

## Related documentation

- [Absorbers and strippers](../process/equipment/absorbers.md)
- [Thermodynamics recipes](../cookbook/thermodynamics-recipes.md)
- [Process recipes](../cookbook/process-recipes.md)
- [Component reference list](../thermo/component_list.md)
- [Troubleshooting guide](../troubleshooting/index.md)
