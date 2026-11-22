package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * Frozen equation checks using the original Chung coefficient table and an independent Python evaluation.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ChungDenseCorrelationTest {
  /** Checks dilute, dense nonpolar, polar and associating states against frozen reference values. */
  @Test
  void originalCoefficientReferenceMatrix() {
    // T [K], molar density [mol/m3], M [kg/mol], Tc [K], Vc [cm3/mol], omega, Cv [J/mol/K], dipole [D], kappa, k.
    double[][] states = {{300, 0, 0.0280134, 126.2, 89.4, 0.0372, 20.8, 0, 0, 0.026436687902351361},
        {300, 4000, 0.0280134, 126.2, 89.4, 0.0372, 20.8, 0, 0, 0.031034910394252442},
        {473, 5810.575246949447, 0.042081, 364.9, 184.6, 0.142, 82.67, 0.4, 0, 0.059100960749808175},
        {400, 12000, 0.04607, 516.25, 168, 0.6371, 90, 1.69, 0.175, 0.11497183673220499},
        {200, 24000, 0.016043, 190.564, 98.6, 0.01142, 25, 0, 0, 0.14523150764316997}};
    for (double[] state : states) {
      assertEquals(state[9], ChungDenseConductivityMethod.calculateConductivity(state[0], state[1], state[2], state[3],
          state[4], state[5], state[6], state[7], state[8]), state[9] * 1e-12);
    }
  }

  /** Verifies the analytic zero-density limit is approached smoothly without exponential cancellation. */
  @Test
  void diluteLimitIsContinuous() {
    double dilute = ChungDenseConductivityMethod.calculateConductivity(300, 0, 0.0280134, 126.2, 89.4, 0.0372, 20.8, 0,
        0);
    for (double density : new double[] {1e-20, 1e-12, 1e-6}) {
      assertEquals(dilute,
          ChungDenseConductivityMethod.calculateConductivity(300, density, 0.0280134, 126.2, 89.4, 0.0372, 20.8, 0, 0),
          dilute * 1e-9);
    }
  }

  /** Rejects NaN, negative density, singular packing and invalid collision-integral temperatures. */
  @Test
  void invalidInputsAreExplicit() {
    for (double density : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, 6e6 / 89.4}) {
      assertThrows(IllegalArgumentException.class, () -> ChungDenseConductivityMethod.calculateConductivity(300,
          density, 0.0280134, 126.2, 89.4, 0.0372, 20.8, 0, 0));
    }
    for (double temperature : new double[] {0, Double.NaN, 1, 1e6}) {
      assertThrows(IllegalArgumentException.class, () -> ChungDenseConductivityMethod.calculateConductivity(temperature,
          1, 0.0280134, 126.2, 89.4, 0.0372, 20.8, 0, 0));
    }
  }
}
