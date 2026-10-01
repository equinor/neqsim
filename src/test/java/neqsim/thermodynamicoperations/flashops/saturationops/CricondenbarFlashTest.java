package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import Jama.Matrix;
import neqsim.mathlib.linearalgebra.JamaLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraException;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for the migrated cricondenbar Newton system.
 *
 * <p>
 * Tests use a fixed two-phase state and an independent direct JAMA solve. The experimental search trajectory is
 * sensitive to floating-point arithmetic, so architecture-specific end-state snapshots do not establish solver
 * equivalence. These tests check the residual, Jacobian, linear solve and failed-solve path on every JVM.
 * </p>
 *
 * @author asmf
 * @version 1.0
 */
class CricondenbarFlashTest {
  /**
   * Verifies legacy serialized field descriptors and a complete Newton-state round trip.
   *
   * @throws IOException if serialization fails
   * @throws ClassNotFoundException if a serialized class is unavailable
   */
  @Test
  void serializationRetainsLegacyMatrixSchema() throws IOException, ClassNotFoundException {
    ObjectStreamClass descriptor = ObjectStreamClass.lookup(CricondenbarFlash.class);
    assertEquals(Matrix.class, descriptor.getField("Jac").getType());
    assertEquals(Matrix.class, descriptor.getField("fvec").getType());
    CricondenbarFlash flash = new CricondenbarFlash(createFluid());
    flash.setfvec();
    flash.setJac();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(flash);
    }
    CricondenbarFlash copy;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      copy = (CricondenbarFlash) input.readObject();
    }
    assertArrayEquals(flash.fvec, copy.fvec, 0.0);
    for (int i = 0; i < flash.Jac.length; i++) {
      assertArrayEquals(flash.Jac[i], copy.Jac[i], 0.0);
    }
    assertArrayEquals(flash.solveNewtonStep(), copy.solveNewtonStep(), 1e-12);
  }

  /**
   * Builds a fixed methane-propane SRK state with distinct gas and liquid compositions.
   *
   * @return fluid used for Newton-system regression
   */
  private static SystemInterface createFluid() {
    SystemInterface system = new SystemSrkEos(300.0, 80.01325);
    system.addComponent("methane", 0.1);
    system.addComponent("propane", 0.1);
    system.createDatabase(true);
    system.setMixingRule(2);
    system.init(0);
    system.setNumberOfPhases(2);
    system.setBeta(0.5);
    system.getPhase(0).getComponent(0).setx(0.8);
    system.getPhase(0).getComponent(1).setx(0.2);
    system.getPhase(1).getComponent(0).setx(0.2);
    system.getPhase(1).getComponent(1).setx(0.8);
    system.init(3);
    return system;
  }

  /** Verifies the residual and Jacobian against their defining equations, including repeated initialization. */
  @Test
  void residualAndJacobianPreserveDefinitions() {
    SystemInterface system = createFluid();
    CricondenbarFlash flash = new CricondenbarFlash(system);
    flash.Jac[2][2] = 99.0;
    flash.setfvec();
    flash.setJac();
    for (int i = 0; i < 2; i++) {
      double expectedResidual = Math
          .log(system.getPhase(0).getComponent(i).getFugacityCoefficient() * system.getPhase(0).getComponent(i).getz()
              * system.getPressure())
          - Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient()
              * system.getPhase(1).getComponent(i).getx() * system.getPressure());
      assertEquals(expectedResidual, flash.fvec[i], 1e-12);
      for (int j = 0; j < 2; j++) {
        double expectedEntry = -(i == j ? 1.0 : 0.0) / system.getPhase(1).getComponent(i).getx()
            - system.getPhase(1).getComponent(i).getdfugdx(j);
        assertEquals(expectedEntry, flash.Jac[i][j], 1e-12);
      }
      assertEquals(-1.0, flash.Jac[2][i], 0.0);
      assertEquals(system.getPhase(0).getComponent(i).getdfugdp() - system.getPhase(1).getComponent(i).getdfugdp(),
          flash.Jac[i][2], 1e-12);
    }
    assertEquals(0.0, flash.fvec[2], 1e-12);
    assertEquals(0.0, flash.Jac[2][2], 0.0);
  }

  /** Verifies a Newton step against direct JAMA arithmetic and the linear-system residual. */
  @Test
  void newtonStepMatchesDirectJamaSolve() {
    CricondenbarFlash flash = new CricondenbarFlash(createFluid());
    flash.setfvec();
    flash.setJac();
    JamaLinearAlgebra algebra = new JamaLinearAlgebra();
    double[] step = flash.solveNewtonStep();
    assertArrayEquals(new Matrix(flash.Jac).solve(new Matrix(flash.fvec, flash.fvec.length)).getColumnPackedCopy(),
        step, 1e-12);
    assertArrayEquals(flash.fvec, algebra.multiply(flash.Jac, step), 1e-10);
  }

  /** Verifies that a failed Newton solve terminates rather than spinning with a null or stale step. */
  @Test
  void singularNewtonSystemFailsPromptly() {
    CricondenbarFlash flash = new CricondenbarFlash(createFluid());
    flash.setfvec();
    for (double[] row : flash.Jac) {
      Arrays.fill(row, 0.0);
    }
    LinearAlgebraException failure = assertThrows(LinearAlgebraException.class, flash::solveNewtonStep);
    assertTrue(failure.getCause() instanceof LinearAlgebraException);
  }
}
