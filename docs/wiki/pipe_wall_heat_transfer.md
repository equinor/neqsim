---
title: "Pipe Wall Construction and Heat Transfer Modeling"
description: "Build multilayer pipe walls and screen cylindrical heat transfer with explicit units, environmental assumptions, and NeqSim API boundaries."
---

NeqSim represents a pipe wall as concentric material layers and can combine their
cylindrical conduction resistance with a simplified surrounding-environment model.
Use this page for screening and for preparing heat-transfer inputs. It does not replace
a hydraulic flow model, a transient thermal model, or a qualified insulation design.

## Model basis

For a layer with inner radius $r_i$, outer radius $r_o$, thermal conductivity $k$, and
unit pipe length, NeqSim uses

$$
R'_{layer} = \frac{\ln(r_o/r_i)}{2\pi k}
$$

where $R'$ is in K m/W. `PipeWall.calcCylindricalThermalResistancePerLength()` sums
this resistance over all layers. `PipeWallBuilder.calcOverallUValue(double)` adds the
inner-film and outer-environment resistances and returns an overall coefficient in
W/(m² K), referenced to the inner pipe area.

The builder's inner-film argument is caller-owned. NeqSim does not infer it from a flow
regime in this geometry calculation. The air, seawater, and soil environment factories
also use simplified correlations; verify their applicability for the actual installation.

## Units and current API

| Quantity | Unit | Current API |
| --- | --- | --- |
| Inner diameter, radius, layer thickness | m | `PipeWallBuilder`, `MaterialLayer`, `PipeWall` |
| Material conductivity | W/(m K) | `PipeMaterial.getThermalConductivity()` |
| Density | kg/m³ | `PipeMaterial.getDensity()` |
| Specific heat capacity | J/(kg K) | `PipeMaterial.getSpecificHeatCapacity()` |
| Ambient temperature | K | `exposedToAir`, `subseaEnvironment`, `buriedInSoil` |
| Wind or current velocity | m/s | environment factory argument |
| Inner-film coefficient | W/(m² K) | `calcOverallUValue(double)` |
| Resistance per unit length | K m/W | `calcCylindricalThermalResistancePerLength()` |

`PipeWallBuilder` has a private constructor. Start with a factory such as
`carbonSteelPipe`, `barePipe`, `withInnerDiameter`, or `subseaPipe`. Add layers from the
inside out. The predefined `PipeMaterial` values are nominal properties near 20 °C, not
temperature-dependent material certificates.

## Executable subsea screening example

This complete Java 8 program builds carbon steel, FBE, and polyurethane layers. It adds
a simplified seawater environment, evaluates the overall coefficient, and calculates a
screening heat loss per metre from bulk-fluid to ambient temperature.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.PipeMaterial;
import neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.PipeWall;
import neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.PipeWallBuilder;
import neqsim.fluidmechanics.geometrydefinitions.surrounding.PipeSurroundingEnvironment;

public final class PipeWallHeatTransferGuideExample {
  private static final Logger LOGGER =
      LogManager.getLogger(PipeWallHeatTransferGuideExample.class);

  private PipeWallHeatTransferGuideExample() {}

  public static void main(String[] args) {
    double innerDiameterM = 0.254;
    double steelThicknessM = 0.0127;
    double fbeThicknessM = 0.0004;
    double insulationThicknessM = 0.050;
    double bulkFluidTemperatureK = 333.15;
    double seawaterTemperatureK = 277.15;
    double seawaterCurrentMPerS = 0.5;
    double innerFilmCoefficientWPerM2K = 800.0;

    PipeWallBuilder builder =
        PipeWallBuilder.carbonSteelPipe(innerDiameterM, steelThicknessM)
            .addFBECoating(fbeThicknessM)
            .addInsulation(PipeMaterial.POLYURETHANE_FOAM, insulationThicknessM)
            .subseaEnvironment(seawaterTemperatureK, seawaterCurrentMPerS);

    PipeWall wall = builder.build();
    PipeSurroundingEnvironment environment = builder.buildEnvironment();
    double resistanceKMW = wall.calcCylindricalThermalResistancePerLength();
    double overallUWPerM2K = builder.calcOverallUValue(innerFilmCoefficientWPerM2K);
    double heatLossWPerM =
        overallUWPerM2K
            * 2.0
            * Math.PI
            * wall.getInnerRadius()
            * (bulkFluidTemperatureK - seawaterTemperatureK);

    assert wall.getNumberOfLayers() == 3;
    assert environment.isSubsea();
    assert resistanceKMW > 0.0;
    assert overallUWPerM2K > 0.0;
    assert overallUWPerM2K < innerFilmCoefficientWPerM2K;
    assert heatLossWPerM > 0.0;

    LOGGER.info(
        "layers={}, outerDiameter={} m, resistance={} K m/W, U={} W/(m2 K), heatLoss={} W/m",
        wall.getNumberOfLayers(),
        2.0 * wall.getOuterRadius(),
        resistanceKMW,
        overallUWPerM2K,
        heatLossWPerM);
  }
}
```

Run the program with assertions enabled (`java -ea ...`). The assertions are executable
checks, not design acceptance criteria.

## Environment choices

- `exposedToAir(temperatureK, windVelocityMPerS)` uses a simple natural-plus-forced
  convection estimate.
- `subseaEnvironment(temperatureK, currentVelocityMPerS)` uses a simple seawater
  convection estimate.
- `buriedInSoil(temperatureK, depthToCentreM, soilMaterial)` uses the cylindrical
  shape-factor model and requires burial depth greater than the finished outer radius.

For a custom material, use `addCustomLayer(name, thicknessM, conductivityWPerMK,
densityKgPerM3, specificHeatJPerKgK)`. The thickness is the second argument; older
examples that put conductivity second are incorrect.

## Flow-solver integration boundary

`PipeWallBuilder` is a geometry screening API. It does not automatically configure a
`OnePhasePipeLine`. The `Pipeline` API uses the plural array setters
`setOuterTemperatures(double[])`, `setPipeOuterHeatTransferCoefficients(double[])`, and
`setPipeWallHeatTransferCoefficients(double[])`. Each array must contain
`numberOfLegs + 1` values, representing the boundary values along the legs. There is no
singular `setOuterTemperature(double)` or `setOverallHeatTransferCoefficient(double)` on
this pipeline type.

Do not insert the builder's overall coefficient into a wall-film or outer-film array
without reconciling the resistance basis. For a correlation-based pipeline model, follow
the model-specific heat-transfer contract in the
[pipeline simulation guide](../process/equipment/pipeline_simulation.md#heat-transfer).

## Engineering limits

- Material properties are nominal constants; account for temperature, aging, moisture,
  compression, manufacturing tolerances, and installation condition where relevant.
- The environment correlations omit detailed radiation, solar load, seabed contact,
  burial layering, fouling, wet insulation, thermal bridges, and transient heat storage.
- `calcOverallUValue` is a steady screening calculation. It does not predict fluid
  outlet temperature, multiphase behaviour, hydrate risk, cooldown, or restart response.
- Confirm geometry, coefficients, boundary conditions, uncertainty, and governing design
  standards with qualified discipline engineers before design or safety decisions.

For a local bare-pipe outdoor surface-temperature calculation, see the
[heat-transfer guide](../fluidmechanics/heat_transfer.md#wall-heat-transfer).
