---
title: "Norwegian Emission Methods Comparison"
description: "Source-backed comparison of NeqSim's conventional factors and produced-water degassing screening model."
---

This page explains the two comparison paths implemented in NeqSim. It is a
screening and software-integration guide, not a regulatory calculation procedure.
This model does not establish field accuracy, regulatory compliance, or an uncertainty budget.
Verify the applicable handbook revision, facility
basis, measurement plan, and reporting requirements with accountable specialists.

## Choose the calculation path

| Path | Current NeqSim behavior | Use it for | Do not infer |
|---|---|---|---|
| Conventional factors | Multiplies annual produced-water volume and pressure drop by the two constants implemented in `EmissionsCalculator` | Reproducing the implemented factor calculation and creating a transparent comparison basis | That the constants are current or applicable for a particular report |
| Thermodynamic screening | Runs the `ProducedWaterDegassingSystem` SRK-CPA convenience model and reports released CO2, methane, and C2+ hydrocarbons by stage | Exploring sensitivity to stated flow, temperature, pressure, and assumed gas composition | Field calibration, salinity correction, certified uncertainty, or regulatory acceptance |

The convenience model assumes 95 mol% water and distributes the remaining 5 mol%
using the supplied gas fractions. It uses mixing rule 10 and database interaction
parameters. `setSalinity` stores metadata only; it does not add salt or alter the
thermodynamic calculation. See the
[supported-model guide](../process/equipment/util/produced_water_degassing.md) for
the complete basis and limitations.

## Conventional factor calculation

The current Java source defines `HANDBOOK_F_CH4 = 14.0` and
`HANDBOOK_F_NMVOC = 3.5`, both in g/(m3 water bar). Its static methods return
tonnes for the supplied produced-water volume in m3 and pressure drop in bar:

$$U_{\mathrm{CH_4}}=f_{\mathrm{CH_4}}V_{\mathrm{pw}}\Delta P\times10^{-6}$$

$$U_{\mathrm{nmVOC}}=f_{\mathrm{nmVOC}}V_{\mathrm{pw}}\Delta P\times10^{-6}$$

`calculateConventionalEmissions` also returns `CO2_tonnes = 0.0` and computes
`CO2eq_tonnes` with the source constants `GWP_METHANE = 28.0` and
`GWP_NMVOC = 2.2`. Those are implementation choices, not a promise that the
factors or GWP basis match the reporting basis for a new case.

The source-backed static entry points are:

- `calculateConventionalCH4(double producedWaterVolume_m3, double pressureDrop_bar)`
- `calculateConventionalNMVOC(double producedWaterVolume_m3, double pressureDrop_bar)`
- `calculateConventionalEmissions(double producedWaterVolume_m3, double pressureDrop_bar)`

## Thermodynamic screening calculation

`ProducedWaterDegassingSystem` builds three pressure-reduction stages: degasser,
compact-flotation-unit (CFU), and caisson. After `run()`, use
`getTotalCO2EmissionRate`, `getTotalMethaneEmissionRate`,
`getTotalNMVOCEmissionRate`, and `getTotalCO2Equivalents` for totals. Use
`getMethodComparisonReport()` or `toMap()` to carry the comparison into an
application logger, report, or data store instead of printing directly to the
console.

### Executable Java example

The example is intentionally identical to the one in the supported-model guide.
`ProducedWaterDegassingDocumentationTest#supportedGuideExampleCompilesAndRuns`
extracts that guide's Java fence, compiles it with Java 8 settings, loads it, and
executes `runExample()` against the current source. The synthetic lab GWR only
demonstrates comparison reporting; it does not calibrate the model.

```java
import java.util.Map;
import neqsim.process.equipment.util.ProducedWaterDegassingSystem;

public class ProducedWaterDegassingExample {
  /**
   * Runs a synthetic screening case with the default database model.
   *
   * @return the executed model for inspection or integration
   */
  public static ProducedWaterDegassingSystem runExample() {
    ProducedWaterDegassingSystem system = new ProducedWaterDegassingSystem("Screening PW");
    system.setWaterFlowRate(100.0, "m3/hr");
    system.setWaterTemperature(80.0, "C");
    system.setInletPressure(30.0, "bara");
    system.setDegasserPressure(4.0, "bara");
    system.setCFUPressure(1.2, "bara");
    system.setCaissonPressure(1.01325, "bara");
    system.setDissolvedGasComposition(
        new String[] {"CO2", "methane", "ethane", "propane"},
        new double[] {0.51, 0.44, 0.04, 0.01});
    system.setTunedInteractionParameters(false);
    system.setLabGWR(0.85);
    system.run();
    Map<String, Object> comparison = system.getValidationResults();
    if (comparison == null || !comparison.containsKey("GWR")) {
      throw new IllegalStateException("Missing lab comparison");
    }
    return system;
  }
}
```

For a real case, replace the synthetic inputs with traceable facility data and
retain the input basis alongside the result. Changing configuration after a model
has already been built requires an explicit `build()` before the next `run()`.

## Compare and review

1. Record the produced-water volume, pressure basis (absolute versus gauge),
   temperature, assumed or measured gas composition, operating duration, and
   source revision.
2. Run the conventional factor calculation with the applicable, independently
   verified factor basis.
3. Run the thermodynamic screening case and preserve `toMap()` output, including
   `processConditions`, stage emissions, totals, validation, and the conventional
   comparison.
4. Reconcile differences component by component. The conventional implementation
   does not calculate CO2; the thermodynamic path depends on its fluid and process
   assumptions.
5. Validate against representative pressurized-water sampling, evolved-gas volume
   and composition, and a case-specific uncertainty assessment before operational
   or reporting use.

The legacy `passesRegulatoryCriteria` result only applies a fixed 7.5% deviation
threshold to the supplied lab GWR. It is not a regulatory decision and is not a
complete uncertainty budget.

## Evidence and provenance

The 2023 workshop paper
[Virtual Measurement of Emissions from Produced Water Using an Online Process Simulator](https://nfogm.no/wp-content/uploads/2025/08/05-Virtual-Measurement-of-Emissions-from-Produced-Water-Using-an-Online-Process-Simulator_-Equinor_Olav-Kristiansen.pdf)
describes a separate field-calibrated model. Its reported field results and
uncertainties must not be transferred to this convenience builder. In particular,
the paper's saline-water interaction fit is not enabled by
`setTunedInteractionParameters(false)` and is not implemented by `setSalinity`.

For current implementation evidence, inspect:

- [`EmissionsCalculator`](../../src/main/java/neqsim/process/equipment/util/EmissionsCalculator.java)
- [`ProducedWaterDegassingSystem`](../../src/main/java/neqsim/process/equipment/util/ProducedWaterDegassingSystem.java)
- [`EmissionsCalculatorTest`](../../src/test/java/neqsim/process/equipment/util/EmissionsCalculatorTest.java)
- [`ProducedWaterDegassingDocumentationTest`](../../src/test/java/neqsim/process/equipment/util/ProducedWaterDegassingDocumentationTest.java)

## Related documentation

- [Produced-water emissions tutorial](ProducedWaterEmissions_Tutorial.md)
- [Examples index](index.md)
- [Reference manual: sustainability and emissions](../REFERENCE_MANUAL_INDEX.md#chapter-43-sustainability--emissions)
