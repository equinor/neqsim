package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Physical sign checks for the legacy implicit acoustic pressure correction.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class LegacyImexAcousticResponseTest {
  /**
   * Compression must generate a pressure force opposing the convergent velocity field.
   */
  @Test
  void pressureResponseOpposesClosedDomainCompression() {
    assertRestoringAcousticResponse(1.0);
  }

  /**
   * Expansion must generate a pressure force opposing the divergent velocity field.
   */
  @Test
  void pressureResponseOpposesClosedDomainExpansion() {
    assertRestoringAcousticResponse(-1.0);
  }

  /**
   * Check the restoring acoustic force and closed-domain mass for either velocity direction.
   *
   * @param direction positive for compression, negative for expansion
   */
  private static void assertRestoringAcousticResponse(double direction) {
    TimeIntegrator integrator = new TimeIntegrator(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    double[] sound = {10.0, 10.0, 10.0};
    double[] density = {100.0, 100.0, 100.0};
    double[] area = {1.0, 1.0, 1.0};
    double[] liquidDensity = {1000.0, 1000.0, 1000.0};
    integrator.setIMEXProperties(sound, density, area, density, liquidDensity, liquidDensity, 1.0, 1.0e5, false);
    double[][] initial = {{100.0, 0.0, 0.0, direction * 0.1, 0.0, 0.0, 0.0}, {100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
        {100.0, 0.0, 0.0, -direction * 0.1, 0.0, 0.0, 0.0}};
    // Linear acoustics about uniform pressure: face momenta are direction * {0, +0.05, -0.05, 0}.
    // Continuity changes middle-cell density; the resulting pressure force must oppose the initial motion.
    TimeIntegrator.RHSFunction rhs = (state, time) -> new double[][] {{-direction * 0.05, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
        {direction * 0.1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {-direction * 0.05, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}};
    double[][] result = integrator.step(initial, rhs, 0.1);

    assertEquals(300.0, result[0][0] + result[1][0] + result[2][0], 1.0e-12);
    assertTrue(direction * result[0][3] < direction * initial[0][3],
        "The acoustic force must oppose the initial left momentum, but it became " + result[0][3]);
    assertTrue(direction * result[2][3] > direction * initial[2][3],
        "The acoustic force must oppose the initial right momentum, but it became " + result[2][3]);
    assertTrue(kineticEnergy(result) < kineticEnergy(initial),
        "The restoring pressure force must reduce this mode's kinetic energy");
  }

  /**
   * Sum gas kinetic energy in the three unit-length cells.
   *
   * @param state conservative gas state
   * @return gas kinetic energy in joules
   */
  private static double kineticEnergy(double[][] state) {
    double energy = 0.0;
    for (double[] cell : state) {
      energy += 0.5 * cell[3] * cell[3] / cell[0];
    }
    return energy;
  }
}
