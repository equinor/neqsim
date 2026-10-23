---
title: "Erosion Prediction Calculator"
description: "Source-backed screening workflow for API RP 14E velocity limits and simplified sand-erosion estimates in production piping."
---

`ErosionPredictionCalculator` is a screening calculator for production-piping velocity and sand-erosion assessments. It reports an API RP 14E erosional-velocity check, a simplified DNV RP O501-inspired erosion estimate, remaining wall thickness, and a combined risk category.

The implementation is [`neqsim.pvtsimulation.flowassurance.ErosionPredictionCalculator`](../../../src/main/java/neqsim/pvtsimulation/flowassurance/ErosionPredictionCalculator.java). Its focused executable regression is [`ErosionPredictionCalculatorTest`](../../../src/test/java/neqsim/pvtsimulation/flowassurance/ErosionPredictionCalculatorTest.java).

## Qualification boundary

This calculator is a screening implementation, not a standards-conformance certificate or a replacement for project erosion, corrosion, materials, mechanical-integrity, or piping-design reviews. Confirm the applicable editions of API RP 14E and DNV RP O501, the project design basis, measured sand production, particle distribution, geometry, material response, corrosion allowance, and uncertainty before using results in an engineering decision.

The implementation accepts unknown material and geometry strings without throwing an exception and applies fallback constants. Validate every identifier explicitly; a numerical result does not prove that an intended model was selected.

## API RP 14E velocity check

The implementation evaluates the traditional imperial-unit relation

$$V_e=\frac{C}{\sqrt{\rho_m}}$$

with mixture density converted from kg/m³ to lb/ft³ and velocity converted from ft/s to m/s. `setApiCFactor` therefore expects the imperial C-factor. The default is `100.0`; choose another value only from the governing project basis.

After `calculate()`:

- `getErosionalVelocity()` returns the limit in m/s.
- `getVelocityRatio()` returns actual mixture velocity divided by the erosional velocity.
- `isWithinApiLimits()` is true when the ratio is at most `1.0`.

## Sand-erosion screen

When `sandRate > 0`, the calculator estimates particle velocity, material response, impact angle, and geometry response, then converts material loss to mm/year. Inputs use these units:

| Input | Unit |
|---|---|
| Mixture density | kg/m³ |
| Mixture velocity | m/s |
| Internal pipe diameter | m |
| Wall thickness and corrosion allowance | mm |
| Sand rate | kg/day |
| Sand particle diameter | mm |
| Design life | years |

Supported geometry identifiers are `straight`, `elbow`, `bend`, `tee`, `blind_tee`, `reducer`, `contraction`, `choke`, `valve`, and `weld`. Use the exact identifier `straight` for a straight section.

Supported material identifiers are `carbon_steel`, `duplex_steel`, `22cr`, `super_duplex`, `25cr`, `13cr`, `inconel`, `inconel_625`, `titanium`, and `tungsten_carbide`.

## Complete Java example

The example uses Log4j2 rather than direct console output. Its API calls are exercised by the focused JUnit regression linked above.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.pvtsimulation.flowassurance.ErosionPredictionCalculator;
import neqsim.pvtsimulation.flowassurance.ErosionPredictionCalculator.SandLoadDefaults;

public final class ErosionScreeningExample {
  private static final Logger logger =
      LogManager.getLogger(ErosionScreeningExample.class);

  private ErosionScreeningExample() {}

  public static void main(String[] args) {
    ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
    calc.setMixtureDensity(150.0); // kg/m3
    calc.setMixtureVelocity(15.0); // m/s
    calc.setPipeDiameter(0.1524); // m
    calc.setWallThickness(10.0); // mm
    calc.setPipeMaterial("carbon_steel");
    calc.setGeometry("elbow");
    calc.setDesignLife(25.0); // years
    calc.setCorrosionAllowance(3.0); // mm

    // Convert completion defaults to kg/day using the supplied production basis.
    calc.applySandLoadDefaults("ohgp_complete", 1200.0, 0.0, 850.0);
    calc.calculate();

    logger.info(
        "API limit={} m/s, ratio={}, withinLimit={}",
        calc.getErosionalVelocity(),
        calc.getVelocityRatio(),
        calc.isWithinApiLimits());
    logger.info(
        "Erosion={} mm/yr, cumulative={} mm, remainingWall={} mm, risk={}",
        calc.getErosionRate(),
        calc.getCumulativeErosion(),
        calc.getRemainingWallThickness(),
        calc.getRiskLevel());

    SandLoadDefaults defaults =
        ErosionPredictionCalculator.getSandLoadDefaults("ohgp_complete");
    logger.info(
        "Completion={}, liquid={} ppm-wt, gas={} ppm-wt, particle={} micrometres",
        defaults.getCompletionType(),
        defaults.getLiquidPpmWt(),
        defaults.getGasPpmWt(),
        defaults.getParticleSizeMicrons());

    double protectedVelocity =
        calc.calcMaxVelocityForCorrosionProtection(true, true);
    logger.info("Corrosion-protection velocity screen={} m/s", protectedVelocity);
    logger.debug("Calculation record: {}", calc.toJson());
  }
}
```

Run the focused regression from the repository root:

```bash
./mvnw -Dtest=ErosionPredictionCalculatorTest test
```

## Completion defaults

`getSandLoadDefaults(String)` returns assumptions in ppm by weight for liquid and dry-gas flow, plus particle size in micrometres. `applySandLoadDefaults(String, double, double, double)` converts those assumptions to kg/day using liquid rate in m³/day, gas rate in kg/day, and liquid density in kg/m³. It calculates liquid- and gas-phase sand rates separately and applies the larger value; it does not add them.

| Identifier | Liquid ppm-wt | Dry-gas ppm-wt | Particle size (µm) |
|---|---:|---:|---:|
| `natural` | 1.0 | 0.05 | 250 |
| `natural_failure` | 10.0 | 0.5 | 250 |
| `sas` | 3.0 | 0.3 | 100 |
| `ohgp_incomplete` | 3.0 | 0.3 | 100 |
| `ohgp_complete` | 1.0 | 0.05 | 50 |

`getAvailableCompletionTypes()` returns those five identifiers. The lookup also accepts source-defined aliases, but using the advertised identifiers keeps input records unambiguous. An unrecognized identifier returns `null`; `applySandLoadDefaults` then leaves the calculator unchanged.

## Corrosion-protection velocity screen

`calcMaxVelocityForCorrosionProtection(boolean corrosionInhibited, boolean sandPresent)` reads the calculator's mixture density. It uses an internal C-factor of `150.0` for inhibited service or `100.0` otherwise, then reduces that factor by 25% when sand is present. The method is another screening result and does not verify inhibitor-film stability or corrosion control.

## Risk classification

Risk combines erosion rate and velocity ratio in this order:

| Result | Source logic |
|---|---|
| `critical` | erosion rate > 1.0 mm/year **or** velocity ratio > 1.5 |
| `high` | otherwise, erosion rate > 0.5 mm/year **or** velocity ratio > 1.0 |
| `medium` | otherwise, erosion rate > 0.1 mm/year **or** velocity ratio > 0.8 |
| `low` | otherwise |

Because the comparisons are strict `>` checks, a value exactly on a listed threshold falls through to the next category unless the other metric triggers a higher category.

## Structured output

`toJson()` serializes `toMap()` and preserves the engineering basis used by the calculation:

- `inputs` contains density, velocity, dimensions, sand inputs, identifiers, C-factor, corrosion allowance, and design life.
- `API_RP_14E` contains `erosionalVelocity_ms`, `velocityRatio`, and `withinApiLimits`.
- `DNV_RP_O501` contains `erosionRate_mmyr`, `cumulativeErosion_mm`, `remainingWallThickness_mm`, and `withinErosionLimits`.
- `riskLevel` contains the combined category.

Store inputs, NeqSim version, source revision, project assumptions, and independent review evidence alongside any exported result.

## Related documentation

- [Flow-assurance screening tools](flow_assurance_screening_tools.md)
- [Flow-assurance overview](../flow_assurance_overview.md)
- [Emulsion viscosity calculator](emulsion_viscosity_calculator.md)
