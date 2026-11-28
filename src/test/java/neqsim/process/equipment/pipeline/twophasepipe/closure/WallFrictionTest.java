package neqsim.process.equipment.pipeline.twophasepipe.closure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction.WallFrictionResult;

/**
 * Unit tests for WallFriction.
 *
 * Tests wall shear stress calculations for various flow regimes.
 */
public class WallFrictionTest {
  private WallFriction wallFriction;

  @BeforeEach
  void setUp() {
    wallFriction = new WallFriction();
  }

  @Test
  void testZeroVelocityGivesZeroFriction() {
    WallFrictionResult result = wallFriction.calculate(FlowRegime.STRATIFIED_SMOOTH, 0.0, 0.0, // velocities
        50.0, 800.0, // densities
        1.5e-5, 1e-3, // viscosities
        0.3, 0.1, 0.00005 // holdup, diameter, roughness
    );

    assertEquals(0.0, result.gasWallShear, 1e-6);
    assertEquals(0.0, result.liquidWallShear, 1e-6);
  }

  @Test
  void testPositiveVelocityGivesPositiveFriction() {
    WallFrictionResult result = wallFriction.calculate(FlowRegime.STRATIFIED_SMOOTH, 10.0, 1.0, // velocities
        50.0, 800.0, // densities
        1.5e-5, 1e-3, // viscosities
        0.3, 0.1, 0.00005);

    assertTrue(result.gasWallShear > 0, "Gas wall shear should be positive for positive velocity");
    assertTrue(result.liquidWallShear > 0, "Liquid wall shear should be positive for positive velocity");
  }

  @Test
  void testFrictionIncreasesWithVelocity() {
    WallFrictionResult resultLow = wallFriction.calculate(FlowRegime.STRATIFIED_SMOOTH, 5.0, 0.5, // lower
        // velocities
        50.0, 800.0, 1.5e-5, 1e-3, 0.3, 0.1, 0.00005);

    WallFrictionResult resultHigh = wallFriction.calculate(FlowRegime.STRATIFIED_SMOOTH, 10.0, 1.0, // higher
        // velocities
        50.0, 800.0, 1.5e-5, 1e-3, 0.3, 0.1, 0.00005);

    assertTrue(resultHigh.gasWallShear > resultLow.gasWallShear, "Higher velocity should give higher gas friction");
    assertTrue(resultHigh.liquidWallShear > resultLow.liquidWallShear,
        "Higher velocity should give higher liquid friction");
  }

  @Test
  void testAnnularFlowRegime() {
    WallFrictionResult result = wallFriction.calculate(FlowRegime.ANNULAR, 25.0, 0.5, // high gas
        // velocity
        // typical for
        // annular
        50.0, 800.0, 1.5e-5, 1e-3, 0.1, 0.1, 0.00005 // low holdup typical for annular
    );

    assertTrue(result.gasWallShear >= 0, "Annular gas wall shear should be non-negative");
    assertTrue(result.liquidWallShear >= 0, "Annular liquid wall shear should be non-negative");
  }

  /**
   * An annular film thinner than a molecular diameter cannot shield the wall: the gas must carry the single-phase wall
   * force. Before this limit a vanishing film left both phases without any wall friction.
   */
  @Test
  void annularFilmBelowMolecularThicknessUsesDryWallGasFriction() {
    double gasVelocity = 40.0;
    double gasDensity = 180.0;
    double gasViscosity = 2.0e-5;
    double diameter = 1.0;
    double roughness = 4.5e-5;
    WallFrictionResult dry = wallFriction.calculate(FlowRegime.SINGLE_PHASE_GAS, gasVelocity, 0.0, gasDensity, 600.0,
        gasViscosity, 2.0e-4, 0.0, diameter, roughness);
    assertTrue(dry.gasWallForcePerLength > 0.0, "Single-phase gas must feel wall friction");

    for (double holdup : new double[] {0.0, 1.0e-300, 1.0e-30, 1.0e-12}) {
      WallFrictionResult trace = wallFriction.calculate(FlowRegime.ANNULAR, gasVelocity, gasVelocity, gasDensity, 600.0,
          gasViscosity, 2.0e-4, holdup, diameter, roughness);
      assertEquals(dry.gasWallForcePerLength, trace.gasWallForcePerLength, 0.0, "holdup " + holdup);
      assertEquals(dry.gasWallResistance, trace.gasWallResistance, 0.0, "holdup " + holdup);
      assertEquals(0.0, trace.liquidWallForcePerLength, 0.0, "holdup " + holdup);
    }

    WallFrictionResult film = wallFriction.calculate(FlowRegime.ANNULAR, gasVelocity, 0.5, gasDensity, 600.0,
        gasViscosity, 2.0e-4, 1.0e-3, diameter, roughness);
    assertEquals(0.0, film.gasWallShear, 0.0, "A film thicker than the roughness keeps the gas core off the wall");
    assertTrue(film.liquidWallShear > 0.0, "A resolved film carries the wall shear");
  }

  /**
   * A film thinner than the wall roughness leaves roughness elements in the gas core. The gas wall force must fall
   * continuously from the dry-wall value to zero as the film grows to the roughness height.
   */
  @Test
  void annularFilmThinnerThanRoughnessLeavesTheGasInContactWithTheWall() {
    double gasVelocity = 40.0;
    double gasDensity = 180.0;
    double gasViscosity = 2.0e-5;
    double diameter = 1.0;
    double roughness = 4.5e-5;
    double dryForce = wallFriction.calculate(FlowRegime.SINGLE_PHASE_GAS, gasVelocity, 0.0, gasDensity, 600.0,
        gasViscosity, 2.0e-4, 0.0, diameter, roughness).gasWallForcePerLength;
    GeometryCalculator geometry = new GeometryCalculator();

    double previousForce = dryForce;
    for (double holdup : new double[] {1.0e-9, 1.0e-7, 1.0e-5, 5.0e-5, 1.0e-4, 1.5e-4}) {
      double filmThickness = geometry.calcAnnularFilmThickness(holdup, diameter);
      assertTrue(filmThickness < roughness, "holdup " + holdup);
      WallFrictionResult thin = wallFriction.calculate(FlowRegime.ANNULAR, gasVelocity, 0.1, gasDensity, 600.0,
          gasViscosity, 2.0e-4, holdup, diameter, roughness);
      assertEquals((1.0 - filmThickness / roughness) * dryForce, thin.gasWallForcePerLength, 1.0e-12 * dryForce,
          "holdup " + holdup);
      assertTrue(thin.gasWallForcePerLength < previousForce, "holdup " + holdup);
      assertTrue(thin.liquidWallShear > 0.0, "holdup " + holdup);
      previousForce = thin.gasWallForcePerLength;
    }
    assertTrue(previousForce < 0.2 * dryForce, "The gas wall force must vanish as the film reaches the roughness");
  }

  @Test
  void testSlugFlowRegime() {
    WallFrictionResult result = wallFriction.calculate(FlowRegime.SLUG, 5.0, 2.0, 50.0, 800.0, 1.5e-5, 1e-3, 0.5, 0.1,
        0.00005);

    assertTrue(result.gasWallShear >= 0, "Slug gas wall shear should be non-negative");
    assertTrue(result.liquidWallShear >= 0, "Slug liquid wall shear should be non-negative");
  }

  @Test
  void testReynoldsNumbersCalculated() {
    WallFrictionResult result = wallFriction.calculate(FlowRegime.STRATIFIED_SMOOTH, 10.0, 1.0, 50.0, 800.0, 1.5e-5,
        1e-3, 0.3, 0.1, 0.00005);

    assertTrue(result.gasReynolds > 0, "Gas Reynolds should be positive");
    assertTrue(result.liquidReynolds > 0, "Liquid Reynolds should be positive");
  }

  @Test
  void testFrictionFactorsReasonable() {
    WallFrictionResult result = wallFriction.calculate(FlowRegime.STRATIFIED_SMOOTH, 10.0, 1.0, 50.0, 800.0, 1.5e-5,
        1e-3, 0.3, 0.1, 0.00005);

    // Friction factors should be in physical range (0.001 to 0.1)
    assertTrue(result.gasFrictionFactor > 0.001 && result.gasFrictionFactor < 0.2,
        "Gas friction factor should be in physical range");
    assertTrue(result.liquidFrictionFactor > 0.001 && result.liquidFrictionFactor < 0.2,
        "Liquid friction factor should be in physical range");
  }
}
