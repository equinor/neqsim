---
title: "Solution Gas–Water Ratio (Rsw) Calculation"
description: "Source-bounded NeqSim Rsw screening with explicit units, method selection, salinity handling, executable Java, and validation limitations."
---

`SolutionGasWaterRatio` calculates a standard-volume ratio of gas dissolved in an aqueous
phase to water. The result is reported as Sm³ gas per Sm³ water. Use the class for reproducible
screening and sensitivity studies; qualify the selected thermodynamic method against laboratory
data before reservoir, emissions, or facility decisions.

The current public class offers McCain, Søreide–Whitson, and electrolyte-CPA paths. They do not
share the same physical model or failure behavior, so a method name alone is not a validation
statement.

## Public input and result contract

| Input or result | Public API | Unit and implementation boundary |
| --- | --- | --- |
| Temperature | `setTemperaturesAndPressures`, `calculateRsw` | K |
| Pressure | `setTemperaturesAndPressures`, `calculateRsw` | bara |
| Salinity | `setSalinity(double)` | mol NaCl per kg water |
| Salinity with unit | `setSalinity(double, String)` | `molal`, `mol/kg`, `wt%`, `weight%`, `ppm`, or `mg/l` |
| Calculation path | `setCalculationMethod` | enum or recognized method-name string |
| Vector result | `getRsw()` | Sm³ gas per Sm³ water |
| Indexed result | `getRsw(int)` | Sm³ gas per Sm³ water |

The temperature and pressure arrays must have equal lengths. The implementation stores the
provided arrays and calculates points by matching index; callers should not mutate them during a
calculation. It does not validate null arrays, empty arrays, negative salinity, or a method-specific
temperature/pressure range.

The `ppm` and `mg/l` conversions are approximate and assume water density near 1 kg/L.
`wt%` is converted to molality using 58.44 g/mol for NaCl. Treat salinity as NaCl equivalent
unless the selected model and input system have been independently qualified for the actual brine.

## Select a calculation path

| Method | Current implementation | Use boundary |
| --- | --- | --- |
| `MCCAIN` | Culberson–McKetta methane-in-water polynomial plus the implemented McCain salinity correction | The source gas composition is ignored. Use only as a methane/brine correlation screen inside its documented source range. |
| `SOREIDE_WHITSON` | Builds a new `SystemSoreideWhitson`, normalizes non-water source components, adds excess water and NaCl, applies mixing rule 11, and performs a multiphase TP flash | Requires a meaningful positive source-gas inventory. A caught flash exception returns zero. |
| `ELECTROLYTE_CPA` | Uses `SystemSrkCPAstatoil` at zero salinity or `SystemElectrolyteCPAstatoil` with explicit Na⁺/Cl⁻ at positive salinity, applies mixing rule 10, and performs a multiphase TP flash | Model availability is not evidence of accuracy for every gas, salt, temperature, or pressure. Flash exceptions propagate. |

The EoS-based paths identify an aqueous phase by phase type or, as a fallback, by water mole
fraction above 0.5. They return zero when no aqueous phase or no dissolved non-water gas is found.
For Søreide–Whitson, zero can also mean that the caught TP flash failed. A returned zero is therefore
not distinguishable from a physical zero without independent phase and convergence diagnostics.

The class defaults to `ELECTROLYTE_CPA`. Select the method explicitly in auditable workflows.

## Complete McCain screening example

This Java 8 program is extracted, compiled, and executed from this page by the repository test
suite. It checks implementation behavior for pure methane: finite positive results, increasing Rsw
over a bounded pressure series, and reduced Rsw after adding NaCl. Those checks are regression
evidence, not laboratory validation.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.pvtsimulation.simulation.SolutionGasWaterRatio;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

public final class SolutionGasWaterRatioReferenceExample {
  private static final Logger logger =
      LogManager.getLogger(SolutionGasWaterRatioReferenceExample.class);

  private SolutionGasWaterRatioReferenceExample() {}

  public static void main(String[] args) {
    SystemInterface methane = new SystemSrkCPAstatoil(350.0, 100.0);
    methane.addComponent("methane", 1.0);
    methane.setMixingRule(10);

    SolutionGasWaterRatio calculator = new SolutionGasWaterRatio(methane);
    calculator.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);
    calculator.setSalinity(0.0);

    double[] temperaturesK = {350.0, 350.0, 350.0};
    double[] pressuresBara = {50.0, 100.0, 150.0};
    calculator.setTemperaturesAndPressures(temperaturesK, pressuresBara);
    calculator.runCalc();

    double[] pressureSeries = calculator.getRsw();
    assert pressureSeries.length == pressuresBara.length;
    for (double value : pressureSeries) {
      assert Double.isFinite(value);
      assert value > 0.0;
    }
    assert pressureSeries[0] < pressureSeries[1];
    assert pressureSeries[1] < pressureSeries[2];

    calculator.setTemperaturesAndPressures(
        new double[] {350.0}, new double[] {100.0});
    calculator.setSalinity(0.0);
    calculator.runCalc();
    double pureWaterRsw = calculator.getRsw(0);

    calculator.setSalinity(3.5, "wt%");
    calculator.runCalc();
    double salineWaterRsw = calculator.getRsw(0);

    assert salineWaterRsw > 0.0;
    assert salineWaterRsw < pureWaterRsw;

    logger.info(
        "Rsw at 50/100/150 bara: {}/{}/{} Sm3/Sm3; at 100 bara pure/saline: {}/{}",
        pressureSeries[0],
        pressureSeries[1],
        pressureSeries[2],
        pureWaterRsw,
        salineWaterRsw);
  }
}
```

Run Java with assertions enabled (`-ea`) when using this example outside the test suite.

## McCain equation implemented by the class

The implementation converts kelvin to degrees Fahrenheit and bara to psia, then evaluates

$$R_{sw,mathrm{pure}}=A(T_F)+B(T_F)P_{mathrm{psia}}+C(T_F)P_{mathrm{psia}}^2$$

and applies

$$R_{sw,mathrm{brine}}=R_{sw,mathrm{pure}}10^{-C_s(T_F,P_{mathrm{psia}})S_{mathrm{wt%}}}$$

before multiplying the scf/STB result by 0.178108 to report Sm³/Sm³. The class source documents
the methane-correlation range as 60–350 °F and up to 10,000 psia. That source boundary is not a
substitute for checking the original correlation, pressure convention, fluid composition, and
project data.

## EoS result basis

For the EoS paths, the implementation sums non-water, non-ion mole fractions in the selected
aqueous phase and divides by the water mole fraction. It converts that molar ratio to a
standard-volume ratio using NeqSim's standard-state temperature and reference pressure, an ideal-gas
molar volume, and a fixed water density of 1000 kg/m³.

This result basis does not model gas liberation through a separator train, stock-tank shrinkage,
brine-density variation, mineral precipitation, chemical reaction, or uncertainty. Confirm phase
identity, material balance, convergence, composition, salt representation, and standard conditions
before comparing Rsw values across tools or datasets.

## Validation checklist

Before engineering use:

1. state gas composition, water analysis, NaCl-equivalent assumption, temperature in K, pressure in
   bara, and standard-volume basis;
2. preserve the selected calculation method with the result;
3. inspect phase identity and convergence for EoS calculations;
4. compare pressure, temperature, and salinity trends rather than accepting a single point;
5. benchmark against traceable laboratory data over the intended range;
6. treat zero, non-finite, or discontinuous values as diagnostics requiring investigation.

The enabled `SolutionGasWaterRatioTest` protects public method selection, units, array-length
handling, positive McCain results, pressure monotonicity, and salting-out behavior. Its broader
verification suite provides exploratory comparisons but does not establish universal method
accuracy.

## Related documentation

- [PVT simulation overview](README.md)
- [PVT workflow](pvt_workflow.md)
- [Flow-assurance overview](flow_assurance_overview.md)
- [Electrolyte CPA model](../thermo/ElectrolyteCPAModel.md)
- [Thermodynamic models](../thermo/thermodynamic_models.md)
- [Current NeqSim JavaDoc](https://equinor.github.io/neqsim/javadoc/index.html)
