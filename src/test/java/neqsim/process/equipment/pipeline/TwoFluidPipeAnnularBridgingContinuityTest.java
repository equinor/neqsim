package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Verifies that changing interfacial tension cannot expose a jump at the annular film-bridging limit.
 *
 * @author NeqSim development team
 * @version 1.0
 */
class TwoFluidPipeAnnularBridgingContinuityTest {
  /**
   * Evaluates the raw annular film at a specified gas velocity and interfacial tension.
   *
   * @param pipe configured pipe
   * @param gasVelocity superficial gas velocity in m/s
   * @param sigma interfacial tension in N/m
   * @return raw film and total liquid holdups
   * @throws Exception if the private closure cannot be invoked
   */
  private double[] annular(TwoFluidPipe pipe, double gasVelocity, double sigma) throws Exception {
    Method method = TwoFluidPipe.class.getDeclaredMethod("calculateAnnularHoldupOLGA", double.class, double.class,
        double.class, double.class, double.class, double.class, double.class, double.class, double.class);
    method.setAccessible(true);
    return (double[]) method.invoke(pipe, gasVelocity, 0.5, 25.0, 750.0, 1.0e-5, 5.0e-4, sigma, 0.15, 0.18);
  }

  /**
   * Evaluates the complete annular-to-intermittent holdup closure at the same physical state.
   *
   * @param pipe configured pipe
   * @param gasVelocity superficial gas velocity in m/s
   * @param sigma interfacial tension in N/m
   * @return complete liquid holdup
   * @throws Exception if the private closure cannot be invoked
   */
  private double holdup(TwoFluidPipe pipe, double gasVelocity, double sigma) throws Exception {
    Method method = TwoFluidPipe.class.getDeclaredMethod("holdupForRegime", FlowRegime.class, double.class,
        double.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class);
    method.setAccessible(true);
    return (double) method.invoke(pipe, FlowRegime.ANNULAR, gasVelocity, 0.5, 25.0, 750.0, 1.0e-5, 5.0e-4, sigma, 0.18,
        0.5 / (gasVelocity + 0.5));
  }

  /**
   * The unchanged 0.24 film limit must be approached continuously for multiple interfacial tensions.
   *
   * @param sigma interfacial tension in N/m
   * @throws Exception if the private closures cannot be invoked
   */
  @ParameterizedTest
  @ValueSource(doubles = {0.01, 0.013526544784957602, 0.02, 0.05})
  void bridgingLimitHasNoFiniteHoldupJump(double sigma) throws Exception {
    TwoFluidPipe pipe = new TwoFluidPipe("annular-bridging-continuity");
    pipe.setDiameter(0.15);
    pipe.setEnableAnnularFilmModel(true);
    double low = 0.1;
    double high = 100.0;
    assertTrue(annular(pipe, low, sigma)[1] > 0.24);
    assertTrue(annular(pipe, high, sigma)[1] < 0.24);
    for (int iteration = 0; iteration < 60; iteration++) {
      double middle = 0.5 * (low + high);
      if (annular(pipe, middle, sigma)[1] > 0.24) {
        low = middle;
      } else {
        high = middle;
      }
    }
    double criticalGasVelocity = 0.5 * (low + high);
    double below = holdup(pipe, criticalGasVelocity * (1.0 - 1.0e-6), sigma);
    double above = holdup(pipe, criticalGasVelocity * (1.0 + 1.0e-6), sigma);
    assertTrue(below > 0.0 && below < 1.0 && above > 0.0 && above < 1.0);
    assertEquals(below, above, 1.0e-4, "An infinitesimal film change must not jump between distinct holdups");
    assertEquals(annular(pipe, 100.0, sigma)[0], holdup(pipe, 100.0, sigma), 1.0e-12,
        "Thin films retain the original annular closure");
  }
}
