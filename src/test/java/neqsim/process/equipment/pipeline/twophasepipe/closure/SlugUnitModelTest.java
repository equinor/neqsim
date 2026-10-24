package neqsim.process.equipment.pipeline.twophasepipe.closure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
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
  /** Serialized pre-weight model: UID 1000L and its sole boolean field, frontMixingLossIncluded=false. */
  private static final String LEGACY_DISABLED_MODEL = "rO0ABXNyAERuZXFzaW0ucHJvY2Vzcy5lcXVpcG1lbnQucGlwZWxpbmUudHdvcGhhc2VwaXBlLmNsb3N1cmUu"
      + "U2x1Z1VuaXRNb2RlbAAAAAAAAAPoAgABWgAXZnJvbnRNaXhpbmdMb3NzSW5jbHVkZWR4cAA=";

  /**
   * Legacy saves preserve both their front-mixing choice and their original unscaled velocities.
   *
   * @throws IOException if the serialized fixture cannot be read
   * @throws ClassNotFoundException if the serialized model class is unavailable
   */
  @Test
  void legacySerializedMixingFlagsPreservePressureLossChoice() throws IOException, ClassNotFoundException {
    for (boolean included : new boolean[] {false, true}) {
      byte[] legacy = Base64.getDecoder().decode(LEGACY_DISABLED_MODEL);
      legacy[legacy.length - 1] = (byte) (included ? 1 : 0);
      SlugUnitModel restored = deserialize(legacy);
      assertEquals(included, restored.isFrontMixingLossIncluded());
      assertFalse(restored.isHighFroudeSlipReductionEnabled());
      SlugUnitModel.Result result = restored.solve(4.96, 0.60, 22.8, 664.0, 1.24e-5, 3.5e-4, 0.015, 0.25, ROUGHNESS,
          0.0);
      assertTrue(result.valid);
      assertTrue(result.accelerationGradient > 0.0);
      assertEquals(result.wallFrictionGradient + (included ? result.accelerationGradient : 0.0),
          result.frictionGradient, 1.0e-12);
    }
  }

  /**
   * Current saves preserve explicit, fractional and automatic weights at more than one operating point.
   *
   * @throws IOException if the model cannot be serialized
   * @throws ClassNotFoundException if the serialized model class is unavailable
   */
  @Test
  void weightedAndAutomaticConfigurationsSurviveSerialization() throws IOException, ClassNotFoundException {
    for (double weight : new double[] {0.0, 1.0, 0.35, Double.NaN}) {
      for (boolean slipReduction : new boolean[] {false, true}) {
        SlugUnitModel original = new SlugUnitModel();
        original.setFrontMixingLossWeight(weight);
        original.setHighFroudeSlipReductionEnabled(slipReduction);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
          output.writeObject(original);
        }
        SlugUnitModel restored = deserialize(bytes.toByteArray());
        assertEquals(weight == 1.0, restored.isFrontMixingLossIncluded());
        assertEquals(slipReduction, restored.isHighFroudeSlipReductionEnabled());
        for (double[] velocity : new double[][] {{4.71, 0.60}, {7.89, 1.22}}) {
          SlugUnitModel.Result expected = original.solve(velocity[0], velocity[1], 23.2, 669.6, 1.24e-5, 3.5e-4, 0.0154,
              0.25, ROUGHNESS, 0.0);
          SlugUnitModel.Result actual = restored.solve(velocity[0], velocity[1], 23.2, 669.6, 1.24e-5, 3.5e-4, 0.0154,
              0.25, ROUGHNESS, 0.0);
          assertTrue(expected.valid && actual.valid);
          assertEquals(expected.frictionGradient, actual.frictionGradient, 1.0e-12);
          assertEquals(expected.unitHoldup, actual.unitHoldup, 1.0e-12);
          assertEquals(expected.translationalVelocity, actual.translationalVelocity, 1.0e-12);
        }
      }
    }
  }

  /**
   * Deserialize a slug-unit model from a complete Java serialization stream.
   *
   * @param bytes serialized model bytes
   * @return restored model
   * @throws IOException if the model cannot be read
   * @throws ClassNotFoundException if the serialized model class is unavailable
   */
  private static SlugUnitModel deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return (SlugUnitModel) input.readObject();
    }
  }

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
  void frontMixingLossCanBeSwitchedOffOrCountedInFull() {
    double vsG = 4.96;
    double vsL = 0.60;
    SlugUnitModel model = new SlugUnitModel();
    assertFalse(model.isFrontMixingLossIncluded());
    model.setFrontMixingLossIncluded(false);
    SlugUnitModel.Result wallOnly = model.solve(vsG, vsL, 22.8, 664.0, 1.24e-5, 3.5e-4, 0.015, 0.25, ROUGHNESS, 0.0);
    model.setFrontMixingLossIncluded(true);
    assertTrue(model.isFrontMixingLossIncluded());
    SlugUnitModel.Result counted = model.solve(vsG, vsL, 22.8, 664.0, 1.24e-5, 3.5e-4, 0.015, 0.25, ROUGHNESS, 0.0);
    assertTrue(counted.valid && wallOnly.valid);
    assertTrue(counted.accelerationGradient > 0.0);
    assertEquals(wallOnly.wallFrictionGradient, counted.wallFrictionGradient, 1.0e-12);
    assertEquals(wallOnly.wallFrictionGradient, wallOnly.frictionGradient, 1.0e-12);
    assertEquals(counted.wallFrictionGradient + counted.accelerationGradient, counted.frictionGradient, 1.0e-12);
  }

  @Test
  void automaticFrontMixingWeightIsHorizontalAndNeedsSlugFroudeNumber() {
    assertEquals(0.0, SlugUnitModel.automaticFrontMixingWeight(1.5, 0.0), 1.0e-12);
    assertEquals(0.6, SlugUnitModel.automaticFrontMixingWeight(3.4, 0.0), 1.0e-12);
    assertEquals(0.2, SlugUnitModel.automaticFrontMixingWeight(7.0, 0.0), 1.0e-12);
    assertEquals(0.0, SlugUnitModel.automaticFrontMixingWeight(3.4, Math.sin(Math.toRadians(4.0))), 1.0e-12);

    SlugUnitModel automatic = new SlugUnitModel();
    SlugUnitModel wallOnly = new SlugUnitModel();
    wallOnly.setFrontMixingLossWeight(0.0);
    SlugUnitModel.Result auto = automatic.solve(4.71, 0.60, 23.2, 669.6, 1.24e-5, 3.5e-4, 0.0154, 0.25, ROUGHNESS, 0.0);
    SlugUnitModel.Result wall = wallOnly.solve(4.71, 0.60, 23.2, 669.6, 1.24e-5, 3.5e-4, 0.0154, 0.25, ROUGHNESS, 0.0);
    assertTrue(auto.valid && wall.valid);
    assertEquals(wall.frictionGradient + 0.6 * auto.accelerationGradient, auto.frictionGradient, 1.0e-9);
    assertThrows(IllegalArgumentException.class, () -> automatic.setFrontMixingLossWeight(1.2));
  }

  @Test
  void highFroudeSlipReductionLowersHorizontalHoldupTowardsNoSlip() {
    // 10 in gas-oil line at 37 bara, 7.9 and 1.2 m/s superficial: OLGA 2025.1 holds 0.205 of liquid, Bendiksen 0.278.
    SlugUnitModel plain = new SlugUnitModel();
    plain.setHighFroudeSlipReductionEnabled(false);
    SlugUnitModel reduced = new SlugUnitModel();
    assertTrue(reduced.isHighFroudeSlipReductionEnabled());
    SlugUnitModel.Result bendiksen = plain.solve(7.89, 1.22, 26.7, 663.0, 1.3e-5, 3.5e-4, 0.0145, 0.25, ROUGHNESS, 0.0);
    SlugUnitModel.Result scaled = reduced.solve(7.89, 1.22, 26.7, 663.0, 1.3e-5, 3.5e-4, 0.0145, 0.25, ROUGHNESS, 0.0);
    assertTrue(bendiksen.valid && scaled.valid);
    assertEquals(0.278, bendiksen.unitHoldup, 0.01);
    assertEquals(0.205, scaled.unitHoldup, 0.02);
    assertTrue(scaled.filmHoldup < bendiksen.filmHoldup);
    assertTrue(scaled.translationalVelocity < bendiksen.translationalVelocity);
  }

  @Test
  void highFroudeSlipReductionLeavesLowFroudeAndVerticalUnitsUnchanged() {
    SlugUnitModel plain = new SlugUnitModel();
    plain.setHighFroudeSlipReductionEnabled(false);
    SlugUnitModel reduced = new SlugUnitModel();
    SlugUnitModel.Result lowA = plain.solve(1.95, 0.30, 22.8, 664.0, MU_G, MU_L, SIGMA, DIAMETER, ROUGHNESS, 0.0);
    SlugUnitModel.Result lowB = reduced.solve(1.95, 0.30, 22.8, 664.0, MU_G, MU_L, SIGMA, DIAMETER, ROUGHNESS, 0.0);
    assertEquals(lowA.translationalVelocity, lowB.translationalVelocity, 1.0e-12);
    SlugUnitModel.Result riserA = plain.solve(6.9, 0.74, 21.5, 670.0, MU_G, MU_L, SIGMA, 0.2, ROUGHNESS, Math.PI / 2.0);
    SlugUnitModel.Result riserB = reduced.solve(6.9, 0.74, 21.5, 670.0, MU_G, MU_L, SIGMA, 0.2, ROUGHNESS,
        Math.PI / 2.0);
    assertEquals(riserA.unitHoldup, riserB.unitHoldup, 1.0e-9);
  }

  @Test
  void rejectsDegenerateInput() {
    SlugUnitModel model = new SlugUnitModel();
    assertFalse(model.solve(0.0, 1.0, RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, ROUGHNESS, 0.0).valid);
    assertFalse(model.solve(1.0, 0.0, RHO_G, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, ROUGHNESS, 0.0).valid);
    assertFalse(model.solve(1.0, 1.0, RHO_L, RHO_L, MU_G, MU_L, SIGMA, DIAMETER, ROUGHNESS, 0.0).valid);
  }
}
