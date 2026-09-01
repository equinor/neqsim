---
title: Gas-Oil Separation Plant Screening Tutorial
description: Executable NeqSim tutorial for three-stage gas-oil-water separation, mass-balance checks, VPCR4 screening, and engineering model boundaries.
---

This tutorial builds a three-stage gas-oil separation screening model. It is intended for
process-model development and teaching: the calculation predicts equilibrium phase splits at the
selected pressures, but it does not qualify separator internals, produced-water treatment,
compression, export specifications, or mechanical design.

## Engineering Question

For a synthetic water-bearing well fluid, calculate the gas, oil, and aqueous product rates after
separation at 50, 10, and 2 bara. Check the overall mass balance and calculate a model-based VPCR4
value for the final oil at 37.8 degrees Celsius.

The example uses SRK-CPA because water is present. The supplied TBP molar masses are in kg/mol and
the densities are specific gravities, matching the current `addTBPfraction` API.

## Process Configuration

```text
well stream
    |
    v
HP three-phase separator (50 bara) ----> HP gas
    |
    +----> HP water
    |
    v
MP valve -> MP separator (10 bara) ----> MP gas + MP water
    |
    v
LP valve -> LP separator (2 bara) -----> LP gas + LP water
    |
    v
export-oil screening stream
```

The three gas streams are separate battery-limit products. A real facility would normally route
the MP and LP gas through scrubbers, compression, cooling, and recycle before export. Those units
are deliberately excluded so the phase-split and material-balance contract remains clear.

## Complete Java 8 Example

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

public final class GospScreeningExample {
  private static final Logger logger = LogManager.getLogger(GospScreeningExample.class);

  private GospScreeningExample() {}

  public static void main(String[] args) {
    SystemInterface wellFluid = new SystemSrkCPAstatoil(353.15, 50.0);
    wellFluid.addComponent("nitrogen", 0.005);
    wellFluid.addComponent("CO2", 0.020);
    wellFluid.addComponent("methane", 0.350);
    wellFluid.addComponent("ethane", 0.080);
    wellFluid.addComponent("propane", 0.060);
    wellFluid.addComponent("i-butane", 0.020);
    wellFluid.addComponent("n-butane", 0.030);
    wellFluid.addComponent("i-pentane", 0.015);
    wellFluid.addComponent("n-pentane", 0.020);
    wellFluid.addComponent("n-hexane", 0.025);
    wellFluid.addComponent("n-heptane", 0.040);
    wellFluid.addComponent("n-octane", 0.050);
    wellFluid.addComponent("n-nonane", 0.040);
    wellFluid.addComponent("nC10", 0.030);
    wellFluid.addTBPfraction("C11", 0.050, 0.150, 0.78);
    wellFluid.addTBPfraction("C15", 0.040, 0.210, 0.82);
    wellFluid.addTBPfraction("C20", 0.060, 0.350, 0.88);
    wellFluid.addComponent("water", 0.050);
    wellFluid.setMixingRule(10);
    wellFluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("well stream", wellFluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(80.0, "C");
    feed.setPressure(50.0, "bara");

    ThreePhaseSeparator hpSeparator = new ThreePhaseSeparator("HP separator", feed);
    StreamInterface hpOil = hpSeparator.getOilOutStream();

    ThrottlingValve mpValve = new ThrottlingValve("MP valve", hpOil);
    mpValve.setOutletPressure(10.0, "bara");
    ThreePhaseSeparator mpSeparator =
        new ThreePhaseSeparator("MP separator", mpValve.getOutletStream());
    StreamInterface mpOil = mpSeparator.getOilOutStream();

    ThrottlingValve lpValve = new ThrottlingValve("LP valve", mpOil);
    lpValve.setOutletPressure(2.0, "bara");
    ThreePhaseSeparator lpSeparator =
        new ThreePhaseSeparator("LP separator", lpValve.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(hpSeparator);
    process.add(mpValve);
    process.add(mpSeparator);
    process.add(lpValve);
    process.add(lpSeparator);
    process.run();

    double gasMassFlow = hpSeparator.getGasOutStream().getFlowRate("kg/hr")
        + mpSeparator.getGasOutStream().getFlowRate("kg/hr")
        + lpSeparator.getGasOutStream().getFlowRate("kg/hr");
    double waterMassFlow = hpSeparator.getWaterOutStream().getFlowRate("kg/hr")
        + mpSeparator.getWaterOutStream().getFlowRate("kg/hr")
        + lpSeparator.getWaterOutStream().getFlowRate("kg/hr");
    StreamInterface exportOil = lpSeparator.getOilOutStream();
    double oilMassFlow = exportOil.getFlowRate("kg/hr");
    double feedMassFlow = feed.getFlowRate("kg/hr");
    double recoveredMassFlow = gasMassFlow + waterMassFlow + oilMassFlow;
    double relativeMassBalanceError =
        Math.abs(recoveredMassFlow - feedMassFlow) / feedMassFlow;
    double vpcr4Bara = exportOil.getRVP(37.8, "C", "bara");

    requireFinitePositive("gas mass flow", gasMassFlow);
    requireFinitePositive("water mass flow", waterMassFlow);
    requireFinitePositive("oil mass flow", oilMassFlow);
    requireFinitePositive("VPCR4", vpcr4Bara);
    if (relativeMassBalanceError > 1.0e-3) {
      throw new IllegalStateException(
          "Relative material-balance error exceeds 0.1%: " + relativeMassBalanceError);
    }

    logger.info("Gas products: {} kg/hr", gasMassFlow);
    logger.info("Water products: {} kg/hr", waterMassFlow);
    logger.info("Export-oil screening stream: {} kg/hr", oilMassFlow);
    logger.info("Relative material-balance error: {}", relativeMassBalanceError);
    logger.info("Model VPCR4 at 37.8 C: {} bara", vpcr4Bara);
  }

  private static void requireFinitePositive(String name, double value) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalStateException(name + " is not finite and positive: " + value);
    }
  }
}
```

## Interpret the Results

The recovered product mass should match the feed within the stated numerical tolerance. Each gas,
oil, and water rate is an equilibrium phase-split result for this synthetic fluid and selected
model; it is not a separator-efficiency guarantee.

`getRVP(37.8, "C", "bara")` returns NeqSim's VPCR4 model result on a cloned fluid. It is not the
LP separator pressure and does not replace a qualified laboratory result. See the
[ASTM D6377 vapor-pressure screening guide](../standards/astm_d6377_rvp.md) for method semantics,
state ownership, and compliance boundaries.

## Pressure Selection

Stage pressure changes affect liquid recovery, flash-gas production, vapor pressure, compression
power, and downstream water handling. Compare candidate pressure sets with freshly constructed
process cases and record, at minimum:

1. export-oil and gas mass rates;
2. VPCR4 or the contract-selected vapor-pressure quantity;
3. MP/LP gas compression duties and discharge temperatures;
4. hydrocarbon losses to produced water;
5. material and energy closure; and
6. equipment operating envelopes.

Maximizing oil mass alone is not a complete optimization objective. Pressure limits, compressor
maps, heating/cooling duties, product specifications, emissions, and operability must also be
represented.

## Model Boundaries

| Topic | What this tutorial proves | Additional evidence required |
|---|---|---|
| Phase separation | Equilibrium gas/oil/aqueous splits | Internals, residence time, entrainment, foaming, and vessel sizing |
| Vapor pressure | NeqSim VPCR4 screening at 37.8 degrees Celsius | Qualified laboratory method and applicable product contract |
| Produced water | Aqueous phase rate leaving each stage | Hydrocyclone/deoiling model, oil-in-water measurement, chemistry, and discharge basis |
| Gas export | Gas available at three pressure levels | Compression, cooling, scrubbers, recycle, dew-point treatment, and metering |
| Oil export | Final equilibrium oil stream | BS&W/salt/H2S analysis, export pumping, custody-transfer basis, and specification checks |
| Floating facility | Thermodynamic screening remains usable | Motion-specific separation performance and accountable mechanical design |

Do not apply generic RVP, BS&W, salt, H2S, dew-point, heating-value, or discharge limits. These
limits depend on the product, jurisdiction, receiving system, measurement method, and controlled
contract or regulation.

## Related Documentation

- [Separator equipment](../process/equipment/separators.md)
- [Compressor equipment](../process/equipment/compressors.md)
- [Process cookbook](../cookbook/process-recipes.md)
- [Flow-assurance overview](../pvtsimulation/flowassurance/index.md)
- [ASTM D6377 vapor-pressure screening](../standards/astm_d6377_rvp.md)
