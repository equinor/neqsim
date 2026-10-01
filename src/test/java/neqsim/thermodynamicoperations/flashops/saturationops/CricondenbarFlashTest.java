package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.mathlib.linearalgebra.JamaLinearAlgebra;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for {@link CricondenbarFlash}.
 *
 * <p>
 * The expected values were captured from the implementation that used {@code Jama.Matrix} directly, before it was
 * migrated to {@code LinearAlgebraOperations}. Both paths run the same JAMA LU solve, so the values must match exactly.
 * </p>
 *
 * @author asmf
 * @version 1.0
 */
class CricondenbarFlashTest {
  /**
   * Builds the methane-propane SRK fluid used by the cricondenbar example test.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  private static SystemInterface createFluid() {
    SystemInterface system = new SystemSrkEos(300.0, 80.01325);
    system.addComponent("methane", 0.1);
    system.addComponent("propane", 0.1);
    system.createDatabase(true);
    system.setMixingRule(2);
    system.init(0);
    return system;
  }

  /**
   * Verifies the temperature and pressure reached by the cricondenbar search.
   */
  @Test
  void runReachesUnchangedState() {
    SystemInterface system = createFluid();

    new CricondenbarFlash(system).run();

    assertEquals(345.83263333311504, system.getTemperature(), 0.0);
    assertEquals(125.01325, system.getPressure(), 0.0);
  }

  /**
   * Verifies one Newton step built from the residual vector and Jacobian.
   */
  @Test
  void newtonStepIsUnchanged() {
    SystemInterface system = createFluid();
    CricondenbarFlash flash = new CricondenbarFlash(system);
    flash.run();
    system.init(3);

    flash.setfvec();
    flash.setJac();
    double[] step = new JamaLinearAlgebra().solve(flash.Jac, flash.fvec);

    assertArrayEquals(new double[] {0.10371631234839603, -0.10371631234839604, -0.5241645555543996}, step, 0.0);
  }
}
