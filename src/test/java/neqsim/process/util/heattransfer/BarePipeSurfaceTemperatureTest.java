package neqsim.process.util.heattransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

class BarePipeSurfaceTemperatureTest {
  private static BarePipeSurfaceTemperature.Result solve(double fluidK, double wind, double emissivity,
      double innerFilm) {
    // DN40 nominal: explicit 48.3 mm OD with a 3.2 mm wall, giving 41.9 mm ID.
    return BarePipeSurfaceTemperature.calculate(fluidK, 293.15, 0.0419, 0.0032, 50.0, innerFilm, wind, emissivity);
  }

  @Test
  void hotPipeBalancesFilmConductionConvectionAndRadiation() {
    double fluidK = 401.55;
    BarePipeSurfaceTemperature.Result result = solve(fluidK, 2.0, 0.8, 1500.0);
    double radiusInner = 0.0419 / 2.0;
    double radiusOuter = 0.0483 / 2.0;
    assertTrue(result.getOuterTemperatureK() > 293.15);
    assertTrue(result.getOuterTemperatureK() < fluidK);
    assertEquals(125.75, result.getOuterTemperatureK() - 273.15, 0.1);
    assertEquals(result.getHeatLossWPerM(), result.getConvectionWPerM() + result.getRadiationWPerM(), 1e-8);
    assertEquals(result.getHeatLossWPerM(),
        (fluidK - result.getInnerTemperatureK()) * 2.0 * Math.PI * radiusInner * 1500.0, 1e-7);
    assertEquals(result.getHeatLossWPerM(), (result.getInnerTemperatureK() - result.getOuterTemperatureK()) * 2.0
        * Math.PI * 50.0 / Math.log(radiusOuter / radiusInner), 1e-7);
  }

  @Test
  void hotPipeRespondsToWindRadiationAndInnerFilm() {
    double reference = solve(401.55, 2.0, 0.8, 1500.0).getOuterTemperatureK();
    assertTrue(solve(401.55, 0.0, 0.8, 1500.0).getOuterTemperatureK() > reference);
    assertTrue(solve(401.55, 2.0, 0.0, 1500.0).getOuterTemperatureK() > reference);
    assertTrue(solve(401.55, 2.0, 0.8, 150.0).getOuterTemperatureK() < reference);
    assertTrue(solve(401.55, 2.0, 0.8, 1e9).getOuterTemperatureK() > reference);
    assertEquals(128.08, solve(401.55, 2.0, 0.8, 40000.0).getOuterTemperatureK() - 273.15, 0.1);
  }

  @Test
  void coldPipeAbsorbsHeatAndEqualTemperatureHasZeroFlux() {
    BarePipeSurfaceTemperature.Result cold = solve(278.15, 2.0, 0.8, 500.0);
    assertTrue(cold.getOuterTemperatureK() > 278.15);
    assertTrue(cold.getOuterTemperatureK() < 293.15);
    assertTrue(cold.getHeatLossWPerM() < 0.0);
    BarePipeSurfaceTemperature.Result equal = solve(293.15, 0.0, 0.8, 500.0);
    assertEquals(293.15, equal.getOuterTemperatureK(), 1e-12);
    assertEquals(0.0, equal.getHeatLossWPerM(), 1e-8);
  }

  @Test
  void validatesInputAndUsesEquipmentGeometry() {
    assertThrows(IllegalArgumentException.class, () -> solve(401.55, -1.0, 0.8, 500.0));
    assertThrows(IllegalArgumentException.class, () -> solve(401.55, 2.0, 1.1, 500.0));
    assertThrows(IllegalArgumentException.class, () -> solve(401.55, 2.0, 0.8, 0.0));
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("FW-52-0106");
    pipe.setDiameter(0.0419);
    pipe.setThickness(0.0032);
    pipe.setPipeWallThermalConductivity(50.0);
    BarePipeSurfaceTemperature.Result equipment = pipe.calculateBarePipeSurfaceTemperature(401.55, 293.15, 1500.0, 2.0,
        0.8);
    assertEquals(solve(401.55, 2.0, 0.8, 1500.0).getOuterTemperatureK(), equipment.getOuterTemperatureK(), 1e-10);
    pipe.setInsulation(0.01, 0.04);
    assertThrows(IllegalStateException.class,
        () -> pipe.calculateBarePipeSurfaceTemperature(401.55, 293.15, 1500.0, 2.0, 0.8));
  }
}
