package neqsim.process.equipment.pipeline.twophasepipe.closure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SlugUnitModel}.
 *
 * @author ESOL
 * @version 1.0
 */
class SlugUnitModelTest {
  private static final double RHO_G = 29.3;
  private static final double RHO_L = 660.0;
  private static final double MU_G = 1.3e-5;
  private static final double MU_L = 4.0e-4;
  private static final double SIGMA = 0.02;
  private static final double DIAMETER = 0.25;
  private static final double ROUGHNESS = 4.6e-5;

  @Test
  void unitConservesBothPhases() {
    double vsG = 7.3;
    double vsL = 1.22;
    SlugUnitModel.Result unit = new SlugUnitModel().solve(vsG, vsL, RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER,
        ROUGHNESS, 0.0);
    assertTrue(unit.valid);
    double beta = unit.slugFraction;
    double liquidFlux = beta * unit.slugLiquidVelocity * unit.slugHoldup
        + (1.0 - beta) * unit.filmLiquidVelocity * unit.filmHoldup;
    assertEquals(vsL, liquidFlux, 1.0e-9);
    double bubbleVelocity = (vsG + vsL - unit.slugLiquidVelocity * unit.slugHoldup) / (1.0 - unit.slugHoldup);
    double gasFlux = beta * bubbleVelocity * (1.0 - unit.slugHoldup)
        + (1.0 - beta) * unit.filmGasVelocity * (1.0 - unit.filmHoldup);
    assertEquals(vsG, gasFlux, 1.0e-9);
    assertTrue(unit.filmHoldup < unit.unitHoldup && unit.unitHoldup < unit.slugHoldup);
  }

  @Test
  void unitFrictionIsBelowMixtureFrictionOverWholeUnit() {
    double vsG = 7.3;
    double vsL = 1.22;
    SlugUnitModel.Result unit = new SlugUnitModel().solve(vsG, vsL, RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER,
        ROUGHNESS, 0.0);
    double vm = vsG + vsL;
    double slugDensity = unit.slugHoldup * RHO_L + (1.0 - unit.slugHoldup) * RHO_G;
    double mixtureGradient = 0.02 * slugDensity * vm * vm / (2.0 * DIAMETER);
    assertTrue(unit.frictionGradient > 0.0);
    assertTrue(unit.frictionGradient < mixtureGradient);
    assertEquals(0.0, unit.gravityGradient, 1.0e-12);
    assertEquals(unit.frictionGradient, unit.totalGradient(), 1.0e-12);
  }

  @Test
  void thinLiquidLoadingCannotSustainSlugs() {
    SlugUnitModel.Result unit = new SlugUnitModel().solve(4.74, 0.17, 80.1, 574.4, MU_G, 1.7e-4, SIGMA, 0.4, ROUGHNESS,
        0.0);
    assertFalse(unit.valid);
    assertTrue(unit.stratified);
  }

  @Test
  void steepRiserUsesAnnularFilmAndGravityDominates() {
    SlugUnitModel.Result unit = new SlugUnitModel().solve(6.9, 0.74, 21.5, 670.0, MU_G, MU_L, SIGMA, 0.2, ROUGHNESS,
        Math.PI / 2.0);
    assertTrue(unit.valid);
    assertTrue(unit.unitHoldup > 0.0 && unit.unitHoldup < 0.5);
    assertTrue(unit.gravityGradient > unit.frictionGradient);
  }

  @Test
  void rejectsDegenerateInput() {
    SlugUnitModel model = new SlugUnitModel();
    assertFalse(model.solve(0.0, 1.0, RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, ROUGHNESS, 0.0).valid);
    assertFalse(model.solve(1.0, 0.0, RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, ROUGHNESS, 0.0).valid);
    assertFalse(model.solve(1.0, 1.0, RHO_L, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, ROUGHNESS, 0.0).valid);
  }
}
