---
title: Manifold Mechanical-Design Screening
description: Source-backed NeqSim manifold wall, velocity, reinforcement, support, and weight screening with explicit units and qualification boundaries.
---

`ManifoldMechanicalDesignCalculator` provides deterministic screening calculations for topside,
onshore, and subsea manifold concepts. Use it to organize preliminary geometry, pressure,
temperature, flow, material, support, reinforcement, and weight evidence. It is not a detailed
piping design package, a code-compliance certificate, or a substitute for discipline review.

The calculator is the clearest public boundary when inputs and units must be explicit. The
equipment-owned `ManifoldMechanicalDesign` bridge is described separately because its current
pressure handoff requires additional verification.

## Input and result contract

The calculator does not attach unit strings to setters. Convert every input before calling it.

| Quantity | Setter or result | Required unit or basis |
| --- | --- | --- |
| Design pressure | `setDesignPressure(double)` | MPa |
| Design temperature | `setDesignTemperature(double)` | °C |
| Header and branch diameters | `setHeaderOuterDiameter`, `setBranchOuterDiameter` | m, outside diameter |
| Header and branch wall | `setHeaderWallThickness`, `setBranchWallThickness` | m |
| Corrosion allowance | `setCorrosionAllowance(double)` | m |
| Mass flow | `setMassFlowRate(double)` | kg/s, total manifold flow |
| Mixture density | `setMixtureDensity(double)` | kg/m³ |
| Liquid fraction | `setLiquidFraction(double)` | volume fraction from 0 to 1 |
| Header length and support spacing | setters and getters | m |
| Calculated weight | `getTotalDryWeight()` | kg |

`ManifoldLocation` accepts `TOPSIDE`, `ONSHORE`, or `SUBSEA`. `ManifoldType` accepts
`PRODUCTION`, `INJECTION`, `TEST`, `PIGGING`, or `DISTRIBUTION`.

## Complete screening example

This Java 8 program is extracted, compiled, and executed from this page during the repository
test suite. The values are illustrative screening inputs, not a project design basis.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator.ManifoldLocation;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator.ManifoldType;

public final class ManifoldMechanicalDesignScreeningExample {
  private static final Logger logger =
      LogManager.getLogger(ManifoldMechanicalDesignScreeningExample.class);

  private ManifoldMechanicalDesignScreeningExample() {}

  public static void main(String[] args) {
    ManifoldMechanicalDesignCalculator calculator =
        new ManifoldMechanicalDesignCalculator();
    calculator.setLocation(ManifoldLocation.TOPSIDE);
    calculator.setManifoldType(ManifoldType.PRODUCTION);
    calculator.setMaterialGrade("A106-B");
    calculator.setDesignPressure(5.5); // MPa
    calculator.setDesignTemperature(25.0); // °C
    calculator.setHeaderOuterDiameter(0.3048); // m
    calculator.setHeaderWallThickness(0.0200); // m
    calculator.setBranchOuterDiameter(0.1524); // m
    calculator.setBranchWallThickness(0.0150); // m
    calculator.setCorrosionAllowance(0.0030); // m
    calculator.setHeaderLength(5.0); // m
    calculator.setNumberOfInlets(1);
    calculator.setNumberOfOutlets(2);
    calculator.setNumberOfValves(4);
    calculator.setMassFlowRate(0.25); // kg/s
    calculator.setMixtureDensity(35.0); // kg/m3
    calculator.setLiquidFraction(0.0); // liquid volume fraction

    boolean screeningPass = calculator.performDesignVerification();
    String json = calculator.toJson();

    assert screeningPass;
    assert calculator.isWallThicknessCheckPassed();
    assert calculator.isVelocityCheckPassed();
    assert calculator.isReinforcementCheckPassed();
    assert calculator.getMinHeaderWallThickness() > 0.0;
    assert calculator.getMinHeaderWallThickness()
        < calculator.getHeaderWallThickness();
    assert calculator.getMinBranchWallThickness() > 0.0;
    assert calculator.getHeaderVelocity() > 0.0;
    assert calculator.getBranchVelocity() > 0.0;
    assert calculator.getErosionalVelocity() > calculator.getHeaderVelocity();
    assert calculator.getSupportSpacing() > 0.0;
    assert calculator.getNumberOfSupports() >= 2;
    assert calculator.getTotalDryWeight() > 0.0;
    assert json.contains("wallThicknessAnalysis");
    assert json.contains("velocityAnalysis");
    assert json.contains("supportAnalysis");

    logger.info(
        "Screening pass {}; minimum header wall {} mm; header velocity {} m/s; dry weight {} kg",
        screeningPass,
        calculator.getMinHeaderWallThickness() * 1000.0,
        calculator.getHeaderVelocity(),
        calculator.getTotalDryWeight());
  }
}
```

The example checks calculation integrity and internally consistent inputs. It does not establish
that the assumed material, corrosion allowance, geometry, or limits are suitable for a project.

## What the verification method checks

`performDesignVerification()` runs the current implementation in this order:

1. minimum header and branch wall calculations;
2. comparison of configured wall thicknesses with calculated minima;
3. header and branch velocity screening;
4. branch-reinforcement area screening;
5. support-spacing and dry-weight estimates;
6. submerged-weight estimation when the location is `SUBSEA`.

Read the individual flags with `isWallThicknessCheckPassed()`,
`isVelocityCheckPassed()`, and `isReinforcementCheckPassed()`. The returned Boolean is the
conjunction of those three checks. Support and weight are calculated but do not add independent
pass/fail criteria.

The JSON report exposes configuration, geometry, design conditions, material properties, wall,
velocity, reinforcement, weight, support, and the strings recorded in `appliedStandards`. Those
strings identify calculation branches; they are not evidence that a complete standards review
was performed.

## Implemented equations

For `TOPSIDE` and `ONSHORE`, the implemented minimum-wall expression is:

$$t_m=\frac{PD}{2(SE+PY)}$$

$$t_{\min}=\frac{t_m}{f_{\mathrm{tol}}}+c$$

Here, $P$ is in MPa, $D$ and $t$ are in metres, $S$ is in MPa, $E$ is joint efficiency,
$Y$ is the implementation coefficient, $f_{\mathrm{tol}}$ is the fabrication-tolerance factor,
and $c$ is corrosion allowance in metres.

For `SUBSEA`, the implemented pressure-containment expression is:

$$t_1=\frac{PD}{2f_y/(\gamma_M\gamma_{SC})}$$

The calculator also evaluates the screening erosional velocity as:

$$v_e=\frac{C}{\sqrt{\rho_m}}$$

These are descriptions of the current source calculations. They do not demonstrate compliance
with a particular edition of ASME B31.3, DNV-ST-F101, API RP 14E, NORSOK L-002, or another
project standard. The current subsea wall expression calculates seabed external pressure from
water depth but does not use that value in a collapse or combined-pressure check.

## Equipment bridge boundary

`Manifold.initMechanicalDesign()` creates a `ManifoldMechanicalDesign`. Its `calcDesign()` method
copies configured geometry and stream-derived density, mass flow, and liquid fraction into the
calculator before running the same screening verification.

Do not treat that bridge as a unit-safe design calculation on the current source. The inherited
`setMaxOperationPressure(double)` contract stores bara, while the bridge multiplies that value by
1.1 and passes it directly to the calculator field whose contract is MPa. Use an explicitly
converted calculator input, as in the executable example, until the bridge conversion is
corrected and independently validated. The temperature handoff converts kelvin to degrees
Celsius.

## Data and standards boundary

`ManifoldMechanicalDesignDataSource` attempts to load selected company and standards parameters
from `TechnicalRequirements_Process`, `asme_standards`, and `dnv_iso_en_standards`. Database
failures are logged and calculator defaults remain in use. The JSON output does not establish the
database row, document edition, material certificate, or project requirement that supplied a
value.

Before engineering use, preserve and independently verify at least:

- the governing code and edition, design cases, pressure/temperature basis, and unit conversions;
- material specification, temperature-dependent allowable stress, corrosion/erosion allowance,
  fabrication tolerance, weld efficiency, and sour-service requirements;
- branch geometry and reinforcement details, valve and piping loads, support reactions, thermal
  expansion, flexibility, fatigue, vibration, and nozzle loads;
- subsea collapse, propagation buckling, installation, accidental, thermal, fatigue, stability,
  free-span, and structural-frame cases;
- flange, connector, valve, pigging, inspection, testing, fabrication, and quality requirements;
- vendor data, project specifications, independent code calculations, and accountable discipline
  approval.

The dry-weight model uses simplified pipe, valve, reinforcement, and structure estimates. The
submerged-weight model uses a simplified solid-volume fraction. Neither result is a lifting,
transport, installation, or structural design.

## Related documentation

- [Manifold process simulation](manifolds)
- [Mechanical-design overview](../mechanical_design)
- [Pipeline mechanical design](../pipeline_mechanical_design)
- [Riser mechanical design](../riser_mechanical_design)
- [Engineering workflow hub](../../engineering/)
