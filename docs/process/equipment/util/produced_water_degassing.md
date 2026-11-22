---
title: Produced Water Degassing System
description: Three-stage produced-water gas-release screening, supported defaults, and tuning limitations.
---

`neqsim.process.equipment.util.ProducedWaterDegassingSystem` builds an inlet,
three pressure-reduction heaters, and three separators (degasser, CFU, caisson).
It calculates gas release and component emission rates with `EmissionsCalculator`.
Use it for screening with an explicit calculation basis and independent validation.

## Thermodynamic model and limitations

The builder uses `SystemSrkCPAstatoil`, mixing rule 10, multiphase checking, and the
current NeqSim database interaction parameters. Water is initially 95 mol%; the
specified gas fractions distribute the remaining 5 mol%. This is an assumed
loading, not a fit to a measured gas-water ratio (GWR). Flow in m3/hr is converted
to mass using an assumed water density of 1000 kg/m3.

`setSalinity` currently stores reporting metadata only: it does not add NaCl,
change interaction parameters, or implement salting out. The gas composition,
water flow, temperature, and stage pressures remain separate inputs. This
convenience builder does not accept or modify a caller-owned fluid. For a
separately characterized fluid, assemble `Stream`, `Heater`, and
`ThreePhaseSeparator` equipment directly and validate that model independently.

### Optional tuning is unsupported

`setTunedInteractionParameters(true)` throws `UnsupportedOperationException`
before changing model state. Previously it silently retained default coefficients.
Omit the call, or explicitly use `false`, to select the unchanged database model.
The same rejection applies at `build()`, both `run()` entry points, and result access to protect
older serialized models that retained a true flag. A rejected setter call leaves
an existing process and its results intact. For an older model, explicitly select
`false` and rebuild before running.

There is no supported optional tuned model or claimed validity range in this
builder. Its legacy `KIJ_WATER_*` constants remain available for source
compatibility, are deprecated, and are not applied to any fluid.

The [Kristiansen et al. workshop paper (2023)](https://nfogm.no/wp-content/uploads/2025/08/05-Virtual-Measurement-of-Emissions-from-Produced-Water-Using-an-Online-Process-Simulator_-Equinor_Olav-Kristiansen.pdf)
is background for a separate field-calibrated model. Appendix A, Eq. (6), defines
$k_{ij}=k_{ij,0}+k_{ij,T}(T-288.15)$ with temperature $T$ in kelvin and
$k_{ij,T}$ in inverse kelvin. Its Table 2 was fitted to water containing about
10–11 wt% NaCl. These coefficients are not default neutral/electrolyte parameters.
Applying them requires reconciliation of the full EOS, association, mixing-rule,
temperature-reference, and salinity conventions, followed by validation against
independent public data. Combining a salt-specific fit with an explicit salt
correction can double-count salinity. See [issue #4483](https://github.com/equinor/neqsim/issues/4483)
and the broader provenance work in [#4234](https://github.com/equinor/neqsim/issues/4234).

The paper's field uncertainty and reporting qualifications do not establish the
accuracy or regulatory suitability of this screening builder. Results require a
case-specific uncertainty assessment and applicable reporting review.

## Executable Java example

This example uses the supported default model, synthetic conditions, and absolute
pressures. `ProducedWaterDegassingDocumentationTest` compiles and executes this
exact code fence against the current source.

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

The lab value above is synthetic and demonstrates comparison reporting; it is not
an independent physical validation. Inspect `getValidationResults()` and
`getMethodComparisonReport()` together with the model basis. Setting lab data
only computes deviations; it never calibrates coefficients or inlet loading.
The legacy `passesRegulatoryCriteria` map key tests a fixed 7.5% GWR threshold,
not regulatory compliance or a complete uncertainty budget.

## API reference

| Method | Supported behavior |
|--------|--------------------|
| `setWaterFlowRate(double, String)` | kg/hr, m3/hr, bbl/day; volume conversion assumes 1000 kg/m3 |
| `setWaterTemperature(double, String)` | C, K, F |
| `setInletPressure(double, String)` | bara, barg, psia, psig |
| `setDegasserPressure(double, String)` | First-stage pressure |
| `setCFUPressure(double, String)` | Second-stage pressure |
| `setCaissonPressure(double, String)` | Third-stage pressure |
| `setDissolvedGasComposition(String[], double[])` | Gas component names and mole fractions |
| `setDissolvedGasComposition(Map<String, Double>)` | Copies the supplied gas composition |
| `setSalinity(double, String)` | Stores metadata in wt%, ppm, or molal; no thermodynamic salt effect |
| `setTunedInteractionParameters(boolean)` | `false`: database model; `true`: throws |
| `setLabGWR(double)` | Comparison GWR in Sm3 gas per Sm3 water |
| `setLabGasComposition(Map<String, Double>)` | Copies lab gas mole fractions for comparison |
| `build()` | Builds or explicitly rebuilds the process from current configuration |
| `run()` / `run(UUID)` | Executes the process; builds it if absent |
| `getProcessSystem()` | Exposes the composed process |
| `getDegasserEmissions()` / `getCFUEmissions()` / `getCaissonEmissions()` | Stage calculators |
| `getTotalCO2EmissionRate(String)` / `getTotalMethaneEmissionRate(String)` / `getTotalNMVOCEmissionRate(String)` | Total component mass rates, e.g. kg/hr or tonnes/year |
| `getTotalCO2Equivalents(String)` | CO2 equivalents; built-in factors CH4=28 and nmVOC=2.2 |
| `getEmissionsReport()` / `getMethodComparisonReport()` | Text reports after running |
| `getValidationResults()` / `toMap()` | Comparison metrics / results suitable for JSON serialization |

After changing configuration on a previously built model, call `build()` again
before `run()`; the convenience runner only builds when the process is absent.
Stage temperatures follow the heater/flash calculations; inlet temperature is
not an independent temperature setpoint for every stage.

## Related documentation

- [Emissions calculator](../measurement_devices.md#emissionscalculator)
- [Offshore emission reporting background](../../../emissions/OFFSHORE_EMISSION_REPORTING.md)
- [Method comparison background](../../../examples/NorwegianEmissionMethods_Comparison.md)
- [H2S distribution guide](../../../thermo/H2S_distribution_guide.md)
