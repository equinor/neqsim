package neqsim.process.equipment.pipeline.twophasepipe.closure;

import static org.junit.jupiter.api.Assertions.*;
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
    assertTrue(result.liquidWallShear > 0,
        "Liquid wall shear should be positive for positive velocity");
  }

  @Test
  void testFrictionIncreasesWithVelocity() {
    WallFrictionResult resultLow = wallFriction.calculate(FlowRegime.STRATIFIED_SMOOTH, 5.0, 0.5, // lower
                                                                                                  // velocities
        50.0, 800.0, 1.5e-5, 1e-3, 0.3, 0.1, 0.00005);

    WallFrictionResult resultHigh = wallFriction.calculate(FlowRegime.STRATIFIED_SMOOTH, 10.0, 1.0, // higher
                                                                                                    // velocities
        50.0, 800.0, 1.5e-5, 1e-3, 0.3, 0.1, 0.00005);

    assertTrue(resultHigh.gasWallShear > resultLow.gasWallShear,
        "Higher velocity should give higher gas friction");
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

  @Test
  void testSlugFlowRegime() {
    WallFrictionResult result = wallFriction.calculate(FlowRegime.SLUG, 5.0, 2.0, 50.0, 800.0,
        1.5e-5, 1e-3, 0.5, 0.1, 0.00005);

    assertTrue(result.gasWallShear >= 0, "Slug gas wall shear should be non-negative");
    assertTrue(result.liquidWallShear >= 0, "Slug liquid wall shear should be non-negative");
  }

  @Test
  void testReynoldsNumbersCalculated() {
    WallFrictionResult result = wallFriction.calculate(FlowRegime.STRATIFIED_SMOOTH, 10.0, 1.0,
        50.0, 800.0, 1.5e-5, 1e-3, 0.3, 0.1, 0.00005);

    assertTrue(result.gasReynolds > 0, "Gas Reynolds should be positive");
    assertTrue(result.liquidReynolds > 0, "Liquid Reynolds should be positive");
  }

  @Test
  void testFrictionFactorsReasonable() {
    WallFrictionResult result = wallFriction.calculate(FlowRegime.STRATIFIED_SMOOTH, 10.0, 1.0,
        50.0, 800.0, 1.5e-5, 1e-3, 0.3, 0.1, 0.00005);

    // Friction factors should be in physical range (0.001 to 0.1)
    assertTrue(result.gasFrictionFactor > 0.001 && result.gasFrictionFactor < 0.2,
        "Gas friction factor should be in physical range");
    assertTrue(result.liquidFrictionFactor > 0.001 && result.liquidFrictionFactor < 0.2,
        "Liquid friction factor should be in physical range");
  }
}
