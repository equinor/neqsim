---
title: "ASTM D6377 Vapor-Pressure Screening"
description: "Source-accurate NeqSim workflow for VPCR4, RVP-equivalent correlations, water-free variants, and EOS bubble-point pressure."
---

NeqSim's `Standard_ASTM_D6377` provides an equation-of-state screening calculation for
vapor-pressure quantities associated with crude oil and condensate. The class name and method
labels follow ASTM D6377 and historical ASTM D323 terminology, but the implementation is not the
prescribed laboratory apparatus or compliance evidence. Use a qualified laboratory result and the
applicable contract, regulation, and current controlled standard for custody transfer or product
acceptance.

## Quantities and Method Boundaries

The class calculates several distinct quantities at one configured reference temperature.

| NeqSim result | Current calculation | Interpretation |
|---|---|---|
| `TVP` | EOS bubble-point pressure | Thermodynamic screening value for the supplied fluid model |
| `VPCR4` | Pressure from `TVfractionFlash(0.8)` after the bubble-point solve | NeqSim vapor/liquid-volume-ratio screening result |
| `RVP_ASTM_D6377` | `0.834 * VPCR4` | NeqSim D6377-labelled RVP-equivalent correlation |
| `RVP_ASTM_D323_82` | `(0.752 * (100 * VPCR4) + 6.07) / 100` | NeqSim historical D323-labelled correlation |
| `VPCR4_no_water` | VPCR4 calculation on a clone with water removed | Water-free comparison, calculated lazily |
| `RVP_ASTM_D323_73_79` | Water-free VPCR4 result | NeqSim historical dry-method label |

These are model outputs, not interchangeable measurements. Report the selected method, reference
temperature, pressure unit, fluid characterization, equation of state, and mixing rule with every
result.

The default method is `VPCR4`. Select a method with the type-safe
`Standard_ASTM_D6377.RvpMethod` enum and read it through `getRvpResult()`. The structured result
contains the value in bara, method label, reference temperature in degrees Celsius, and a validity
flag. Use `getValue("RVP", unit)` only for the currently selected method and
`getValue("TVP", unit)` for the EOS bubble-point result. Do not call
`getValue("VPCR4", unit)`; that return-parameter name is not supported by the unit-aware legacy
getter.

## State Ownership and Model Selection

`calculate()` sets temperature and pressure and performs flashes on the `SystemInterface`
supplied to the constructor. Pass a clone when the caller must preserve the original fluid state.

The standard does not force SRK or any other equation of state. It uses the supplied system's
thermodynamic model, composition, characterization, and mixing rule. Vapor-pressure predictions
can be sensitive to light-end loss, heavy-end characterization, water handling, and binary
interaction parameters. Validate the chosen fluid model against representative laboratory data.

The default reference temperature is 37.8 degrees Celsius. Changing it is supported by the API,
but the result must then be reported at that configured temperature rather than presented as a
37.8 degrees Celsius standard result. The current `isOnSpec()` implementation always returns
`true`; apply project or contract limits explicitly instead of using it as a compliance check.

## Executable Java 8 Workflow

The program below preserves the source fluid, selects the D6377-labelled correlation, checks the
structured result, reads the EOS bubble-point pressure, compares VPCR4 and its water-free variant,
and converts the selected RVP value to kPa.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.standards.oilquality.Standard_ASTM_D6377;
import neqsim.standards.oilquality.Standard_ASTM_D6377.RvpMethod;
import neqsim.standards.oilquality.Standard_ASTM_D6377.RvpResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class AstmD6377Example {
  private static final Logger logger = LogManager.getLogger(AstmD6377Example.class);

  private AstmD6377Example() {}

  public static void main(String[] args) {
    SystemInterface sourceOil = new SystemSrkEos(275.15, 1.0);
    sourceOil.addComponent("methane", 0.0006538);
    sourceOil.addComponent("ethane", 0.006538);
    sourceOil.addComponent("propane", 0.065380);
    sourceOil.addComponent("n-pentane", 0.154500);
    sourceOil.addComponent("nC10", 0.545000);
    sourceOil.setMixingRule(2);
    sourceOil.init(0);

    SystemInterface workingFluid = sourceOil.clone();
    Standard_ASTM_D6377 vaporPressure = new Standard_ASTM_D6377(workingFluid);
    vaporPressure.setReferenceTemperature(37.8, "C");
    vaporPressure.setMethodRVP(RvpMethod.RVP_ASTM_D6377);
    vaporPressure.calculate();

    RvpResult selected = vaporPressure.getRvpResult();
    RvpResult vpcr4 = vaporPressure.getRvpResult(RvpMethod.VPCR4);
    RvpResult dryVpcr4 = vaporPressure.getRvpResult(RvpMethod.VPCR4_NO_WATER);
    double tvpBara = vaporPressure.getValue("TVP", "bara");
    double selectedRvpKPa = vaporPressure.getValue("RVP", "kPa");

    requireFinitePositive("selected RVP", selected.getValue());
    requireFinitePositive("VPCR4", vpcr4.getValue());
    requireFinitePositive("water-free VPCR4", dryVpcr4.getValue());
    requireFinitePositive("TVP", tvpBara);
    requireFinitePositive("selected RVP", selectedRvpKPa);

    logger.info("Selected result {}", selected.toJson());
    logger.info("TVP {} bara; VPCR4 {} bara; dry VPCR4 {} bara",
        tvpBara, vpcr4.getValue(), dryVpcr4.getValue());
    logger.info("Source state remains {} K and {} bara",
        sourceOil.getTemperature(), sourceOil.getPressure());
  }

  private static void requireFinitePositive(String name, double value) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalStateException(name + " calculation failed: " + value);
    }
  }
}
```

For a fluid without water, `VPCR4` and `VPCR4_NO_WATER` should agree within numerical
tolerance. For a water-bearing fluid, the difference is a model sensitivity; it is not permission
to discard measured water or emulsion effects.

## API Contract

| Operation | Supported use |
|---|---|
| `setReferenceTemperature(value, unit)` | Converts the supplied temperature to the internal Celsius reference |
| `setMethodRVP(RvpMethod)` | Selects the result returned as `RVP` |
| `calculate()` | Populates TVP, VPCR4, and the two direct correlations |
| `getRvpResult()` | Returns the selected structured result |
| `getRvpResult(method)` | Returns a named structured result; water-free variants are evaluated lazily |
| `getValue("RVP", pressureUnit)` | Converts the selected method result from bara |
| `getValue("TVP", pressureUnit)` | Converts the EOS bubble-point result from bara |
| `getMethodRVP()` | Returns the selected legacy method label |

The legacy string setter remains available, but the enum rejects unknown methods before a
calculation is interpreted. A structured result is valid only when its value is finite and
positive. It does not establish laboratory repeatability, regulatory acceptance, or fitness for a
specific product specification.

## Engineering Validation Checklist

Before using a calculated vapor pressure:

1. Preserve a characterized source fluid and run the standard on a clone.
2. Confirm light ends were not lost during sampling or fluid preparation.
3. Document heavy-end characterization, equation of state, mixing rule, and water treatment.
4. Record the exact method label, reference temperature, and absolute pressure unit.
5. Compare against representative laboratory vapor-pressure data over the operating envelope.
6. Apply the actual product or custody-transfer limit outside `isOnSpec()`.
7. Treat discrepancies as model or characterization evidence, not as a reason to tune silently.

## Related Documentation

- [Oil quality standards overview](oil_quality_standards.md)
- [Standards package overview](README.md)
- [TVP and RVP study](../examples/TVP_RVP_Study.md)
