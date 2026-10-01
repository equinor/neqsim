package neqsim.physicalproperties.interfaceproperties.surfacetension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for {@link GTSurfaceTensionSimple}.
 *
 * <p>
 * The solve direction is checked directly: in the full calculation the solution only seeds an iteration that converges
 * to almost the same profile, so solving the untransposed system moves the surface tension by about 1e-15 relative,
 * well below any tolerance that survives differences in floating-point libraries between CPU architectures.
 * </p>
 *
 * @author asmf
 * @version 1.0
 */
class GTSurfaceTensionSimpleTest {
  /** Iteratively converged values differ by more than 1e-8 between x86_64 and arm64 (see PhasePCSAFTRahmatTest). */
  private static final double RELATIVE_TOLERANCE = 1e-6;

  /**
   * Flashes a mixture and returns its gas-oil interfacial tension from simple gradient theory.
   *
   * @param names component names
   * @param moles number of moles of each component
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return interfacial tension in N/m
   */
  private static double simpleGradientTheoryTension(String[] names, double[] moles, double temperature,
      double pressure) {
    SystemInterface system = new SystemSrkEos(temperature, pressure);
    for (int i = 0; i < names.length; i++) {
      system.addComponent(names[i], moles[i]);
    }
    system.setMixingRule("classic");
    new ThermodynamicOperations(system).TPflash();
    system.initProperties();
    assertEquals(2, system.getNumberOfPhases());
    system.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "Simple Gradient Theory");
    return system.getInterphaseProperties().getSurfaceTension(0, 1);
  }

  /**
   * Verifies that the system F<sup>T</sup> x = b is solved rather than F x = b.
   *
   * <p>
   * With F = [[1, 2], [0, 1]] and b = [1, 4] the transposed system gives x = [1, 2], while the untransposed one gives
   * [-7, 4]. Every intermediate value is a small integer, so the comparison is exact on any platform.
   * </p>
   */
  @Test
  void solvesTheTransposedSystem() {
    double[] solution = GTSurfaceTensionSimple.solveTransposed(new double[][] {{1.0, 2.0}, {0.0, 1.0}},
        new double[] {1.0, 4.0});

    assertArrayEquals(new double[] {1.0, 2.0}, solution, 0.0);
  }

  /**
   * Verifies a binary mixture, where the linear system is one by one.
   */
  @Test
  void methaneDecaneTensionIsUnchanged() {
    double expected = 0.015989522215911635;
    assertEquals(expected,
        simpleGradientTheoryTension(new String[] {"methane", "n-decane"}, new double[] {0.6, 0.4}, 310.0, 50.0),
        expected * RELATIVE_TOLERANCE);
  }

  /**
   * Verifies a ternary mixture.
   */
  @Test
  void methanePropaneHeptaneTensionIsUnchanged() {
    double expected = 0.007770644492084066;
    assertEquals(expected, simpleGradientTheoryTension(new String[] {"methane", "propane", "n-heptane"},
        new double[] {0.5, 0.2, 0.3}, 320.0, 60.0), expected * RELATIVE_TOLERANCE);
  }

  /**
   * Verifies a four-component mixture.
   */
  @Test
  void fourComponentTensionIsUnchanged() {
    double expected = 0.008762064537835006;
    assertEquals(expected, simpleGradientTheoryTension(new String[] {"methane", "ethane", "propane", "n-decane"},
        new double[] {0.5, 0.1, 0.1, 0.3}, 330.0, 80.0), expected * RELATIVE_TOLERANCE);
  }
}
