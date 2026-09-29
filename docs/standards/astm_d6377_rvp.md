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
| `VPCR4` | Pressure from `TVfractionFlash(0.8)` after the bubble-point solve | Raw vapor/liquid volume ratio 4:1 result, not an 80% molar vapor fraction |
| `RVP_ASTM_D6377` | `0.834 * VPCR4` | NeqSim D6377-labelled RVP-equivalent correlation |
| `RVP_ASTM_D323_82` | `0.752 * VPCR4 + 0.0607` with pressures in bara | NeqSim historical D323-labelled correlation |
| `VPCR4_no_water` | VPCR4 calculation on a clone with water removed | Water-free comparison, calculated lazily |
| `RVP_ASTM_D323_73_79` | Water-free VPCR4 result | Implementation equivalence, not endorsement as a laboratory D323 procedure |

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
This does not guarantee independence of every model parameter: binary interaction arrays can be
shared across clones in some versions. Do not mutate BIPs through a clone. Use separately loaded
or constructed fluids for parameter studies and verify that the source remains unchanged.

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

When adding water or another component to an already configured fluid, reapply
`setMixingRule(...)` after the addition so the binary interaction matrices include
the new component, then call `init(0)`.

For a fluid without water, `VPCR4` and `VPCR4_NO_WATER` should agree within numerical
tolerance. For a water-bearing fluid, the difference is a model sensitivity; it is not permission
to discard measured water or emulsion effects.

## Water-Contact Storage: A Declared Engineering Basis

Oil stored in a water-contact cavern can be assessed on an **assumed water-saturated storage
basis**. This is a stated storage condition, not a universal property of oil or of every sample.
The entrained droplet amount captured in a laboratory sample can be incidental to sampling. A
reproducible engineering comparison can therefore separate the characterized dry hydrocarbon EOS
from an independently validated pure-water saturation pressure, rather than let arbitrary input
droplet water determine the reporting basis.

An E300 model tuned to hydrocarbon properties is not automatically validated for water vapor
pressure. Validate the particular model; do not infer that all water-bearing EOS calculations
are inaccurate. Computational water removal is **not** advice to dry a laboratory sample and
does not restore light ends already lost during sampling or handling.

### Preserve the Characterized Hydrocarbon Model

The dry basis must retain the relative hydrocarbon molar amounts, renormalized after water is
excluded. Preserve the EOS class, alpha model and parameters, heavy-component properties, mixing
rule, named-component BIPs and volume shifts in **every allocated phase**, not just phase 0.
Preserve non-water nonhydrocarbons as well unless their exclusion is separately justified.

Do not replace a characterized E300 fluid with an unrelated PR/SRK reconstruction. In particular,
`addTBPfraction(...)` followed by critical-property overrides only through `fluid.getComponent(...)`
does not update every phase and can invalidate the density/volume-shift characterization. Do not
reapply a database mixing rule to a tuned fluid simply to repair component indexing: that can
discard fitted BIPs.

**Component-removal limitation:** the current `removeComponent("water")` path compacts the
component list without remapping the EOS BIP matrix. If water is not the last component, a
hydrocarbon pair can inherit the wrong interaction. The lazy `VPCR4_NO_WATER` route uses this
removal path too; selecting that label is not a preservation guarantee. A synthetic regression
with water between pentane and methane detects a BIP change from 0.0123 to 0.5 in each allocated
phase. Reject such a dry basis. Supply a separately audited water-free version of the same
characterization instead; this guide intentionally does not offer a universal removal helper.
Removing last-listed water is covered by a per-phase preservation test for a synthetic SRK/TBP
fluid, not a guarantee for every E300 file or software version.

### Partial-Pressure Addition and Correlation Order

For the declared D6377-labelled engineering convention at 37.8 °C:

$$
\mathrm{RVPE}_{\mathrm{water\,sat}}
=0.834\left[\mathrm{VPCR4}_{\mathrm{dry}}+p^{\mathrm{sat}}_{\mathrm{water}}(37.8\,^{\circ}\mathrm{C})\right]
=\mathrm{RVPE}_{\mathrm{dry}}+0.834\,p^{\mathrm{sat}}_{\mathrm{water}}(37.8\,^{\circ}\mathrm{C}).
$$

The second equality requires $\mathrm{RVPE}_{\mathrm{dry}}=0.834\,\mathrm{VPCR4}_{\mathrm{dry}}$.
**Do not add the full water saturation pressure after conversion to RVPE.** Dry-basis selection
and output correlation are separate choices: `VPCR4_NO_WATER` returns raw dry VPCR4, not dry RVPE.
The historical D323-82 offset is 0.0607 bar (6.07 kPa); all terms in any correlation must use
consistent pressure units.

For an EOS bubble-point TVP at the specified temperature $T$, the analogous approximation is:

$$
\mathrm{TVP}_{\mathrm{water\,sat}}(T)
=\mathrm{TVP}_{\mathrm{dry}}(T)+p^{\mathrm{sat}}_{\mathrm{water}}(T).
$$

| Reference temperature | Illustrative rounded pure-water pressure | Added contribution |
|----------|----------|----------|
| 37.8 °C | About 6.54 kPa = 0.0654 bar | RVPE: about 5.45 kPa = 0.0545 bar |
| 30 °C | About 4.24 kPa = 0.0424 bar | TVP at 30 °C: about 4.24 kPa = 0.0424 bar |

These rounded numbers illustrate the arithmetic, not a new NeqSim water-property API. Obtain
$p^{\mathrm{sat}}$ from an approved independent source, for example an implementation of IAPWS-IF97
Region 4 within its saturation-temperature range (273.15–647.096 K), or an appropriate
[NIST water property source](https://webbook.nist.gov/cgi/cbook.cgi?ID=C7732185&Mask=4).
Record the source, version, validity range, temperature and units. The 0.0424 bar value is a
30 °C study convention, not a temperature-independent constant.

### Existing API Example on an Audited Dry Fluid

`auditedDryOil` below is a previously verified water-free version of the source characterization.
The earlier synthetic `sourceOil` is water-free and is also suitable for illustrating these calls.
The example performs no parameter mutation and applies the storage convention outside NeqSim.

```java
SystemInterface workingOil = auditedDryOil.clone();
Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(workingOil);
standard.setReferenceTemperature(37.8, "C");
standard.setMethodRVP(RvpMethod.VPCR4_NO_WATER);
standard.calculate();
RvpResult dry = standard.getRvpResult();
if (!dry.isValid()) {
  throw new IllegalStateException("No finite positive dry VPCR4 result");
}
// Rounded external inputs, in bar; replace with the approved property source.
double waterPsat378Bara = 0.0654;
double rvpeWaterSatBara = 0.834 * (dry.getValue() + waterPsat378Bara);

neqsim.process.equipment.stream.StreamInterface dryStream =
    new neqsim.process.equipment.stream.Stream("Dry oil", auditedDryOil.clone());
double tvp30DryBara = dryStream.getTVP(30.0, "C", "bara");
double tvp30WaterSatBara = tvp30DryBara + 0.0424;
```

`Stream.getRVP(T, temperatureUnit, pressureUnit)` defaults to **raw VPCR4**, not `0.834 * VPCR4`.
Select `"RVP_ASTM_D6377"` explicitly in its four-argument overload for that correlation on the
stream's supplied composition. `getTVP(...)` is a bubble-point calculation on the supplied fluid;
it does not remove water or add an independent saturation correction. TVP at 30 °C and RVPE at
37.8 °C are different quantities at different temperatures; there is no general ordering rule
between them.

### Numerical Checks and Reporting Limits

A finite positive `RvpResult` is not a convergence certificate. Check the physical volume ratio
on the calculation fluid and require coexisting gas and liquid phases. Native `TVfractionFlash`
uses density-initialized, volume-corrected fractions by default. A TP-flash bracketing/bisection
cross-check must use the **same corrected volume objective**, not raw `phase.getVolume()` ratios.
Use `initPhysicalProperties("density")` before `getCorrectedVolumeFraction(...)`; equivalently,
phase mass divided by initialized phase density gives the physical phase volume. For a two-phase
oil/gas result the target is a gas volume fraction of 0.8, or gas/liquid volume ratio 4.0.
Extra liquid phases require an explicit total-liquid-volume basis. A binary pentane/TBP screening
case can return a positive pressure while missing the 0.8 target; reject it rather than relaxing
the volume tolerance. A high-water synthetic oil can also return oil/aqueous phases without gas
while `isValid()` remains true. The regression tests distinguish that rejected case from three
trace-water cases that meet the gas/liquid corrected-volume target. These are numerical checks,
not validation of aqueous phase behavior. Repeat these checks for each model and operating case.

The additive convention assumes ideal partial-pressure addition, pure-water activity near one
and sufficient water/contact to establish saturation in storage. Salts and glycols change water
activity. Finite vapor/liquid ratio, water dissolution, nonideal coupling and emulsion effects
cannot be inferred from this addition. It is neither an ASTM apparatus simulation nor proof of
laboratory equivalence, legal compliance or universally superior accuracy. Compare with relevant
measurements before adopting it for a decision.

Report dry and water-saturated estimates separately, including the quantity (raw VPCR4, correlated
RVPE or bubble-point TVP), reference temperature, absolute pressure unit, water basis, complete
EOS provenance, water-property source, convergence evidence and assumptions. Compare a product
limit only on the same quantity, temperature and water basis. `isOnSpec()` does not perform this
assessment.

The synthetic API and preservation checks are in
[OilVapourPressureWaterBasisTest](../../src/test/java/neqsim/standards/oilquality/OilVapourPressureWaterBasisTest.java);
the original workflow is exercised by
[AstmD6377DocumentationTest](../../src/test/java/neqsim/standards/oilquality/AstmD6377DocumentationTest.java).
These are software regressions, not fabricated laboratory validation data.

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
