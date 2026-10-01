package neqsim.physicalproperties.interfaceproperties.surfacetension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.mathlib.linearalgebra.LinearAlgebraException;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for {@link GTSurfaceTensionSimple}.
 *
 * <p>
 * The expected values were captured from the implementation that called {@code Jama.Matrix.solveTranspose}, before it
 * was rewritten as a solve against the transposed matrix. The integrated results allow 1e-12 N/m of floating-point
 * variation across JVMs. A separate nonsymmetric system checks the transpose directly, since integration masks that
 * error by converging to almost the same density profile.
 * </p>
 *
 * @author asmf
 * @version 1.0
 */
class GTSurfaceTensionSimpleTest {
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
   * Verifies a binary mixture, where the linear system is one by one.
   */
  @Test
  void methaneDecaneTensionIsUnchanged() {
    assertEquals(0.015989522215911635,
        simpleGradientTheoryTension(new String[] {"methane", "n-decane"}, new double[] {0.6, 0.4}, 310.0, 50.0), 1e-12);
  }

  /**
   * Verifies a ternary mixture, where the transposed two by two system differs from the untransposed one.
   */
  @Test
  void methanePropaneHeptaneTensionIsUnchanged() {
    assertEquals(0.007770644492084066, simpleGradientTheoryTension(new String[] {"methane", "propane", "n-heptane"},
        new double[] {0.5, 0.2, 0.3}, 320.0, 60.0), 1e-12);
  }

  /**
   * Verifies a four-component mixture, where the transposed three by three system differs from the untransposed one.
   */
  @Test
  void fourComponentTensionIsUnchanged() {
    assertEquals(0.008762064537835006,
        simpleGradientTheoryTension(new String[] {"methane", "ethane", "propane", "n-decane"},
            new double[] {0.5, 0.1, 0.1, 0.3}, 330.0, 80.0),
        1e-12);
  }

  /** Verifies the transpose against an analytical solution that differs strongly from an untransposed solve. */
  @Test
  void densityGradientUsesTransposedMatrix() {
    // A^T * [1, 2] = [8, 9], whereas A * x = [8, 9] gives [1.4, 1.8].
    assertArrayEquals(new double[] {1.0, 2.0},
        GTSurfaceTensionSimple.solveDensityGradient(new double[][] {{2.0, 1.0}, {3.0, 4.0}}, new double[] {8.0, 9.0}),
        1e-12);
  }

  /** Verifies that a singular density-gradient system reports failure rather than continuing with stale values. */
  @Test
  void singularDensityGradientReportsFailure() {
    assertThrows(LinearAlgebraException.class, () -> GTSurfaceTensionSimple
        .solveDensityGradient(new double[][] {{1.0, 2.0}, {2.0, 4.0}}, new double[] {1.0, 2.0}));
  }
}
