package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import Jama.Matrix;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraException;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

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
   * Verifies independent state and numerical behavior after serialization of the converted legacy solvers.
   *
   * @throws IOException if the solver state cannot be serialized
   * @throws ClassNotFoundException if a serialized class cannot be resolved
   */
  @Test
  void legacyTemperatureStateSurvivesSerialization() throws IOException, ClassNotFoundException {
    SystemInterface system = createFluid();
    new ThermodynamicOperations(system).TPflash();
    CricondenBarTemp original = new CricondenBarTemp(system, 2, system.getNumberOfComponents());
    CricondenBarTemp restored = (CricondenBarTemp) roundTrip(original);
    assertArrayEquals(original.u, restored.u, 0.0);
    assertArrayEquals(original.uold, restored.uold, 0.0);
    assertNotSame(original.u, restored.u);
    assertNotSame(restored.u, restored.uold);
    assertEquals(original.solve(), restored.solve(), 1.0e-14);

    CricondenBarTemp1 incomplete = new CricondenBarTemp1(system);
    CricondenBarTemp1 restoredIncomplete = (CricondenBarTemp1) roundTrip(incomplete);
    assertArrayEquals(incomplete.u, restoredIncomplete.u, 0.0);
    assertNotSame(restoredIncomplete.u, restoredIncomplete.uold);
    assertThrows(LinearAlgebraException.class, restoredIncomplete::solve);
  }

  /**
   * Serializes and deserializes a solver without sharing its mutable state.
   *
   * @param value solver to round-trip
   * @return independently deserialized solver
   * @throws IOException if serialization fails
   * @throws ClassNotFoundException if a serialized class cannot be resolved
   */
  private static Object roundTrip(Object value) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(value);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return input.readObject();
    }
  }

  /**
   * Compares the legacy cricondenbar update and convergence norm against the original Jama operations.
   */
  @Test
  void legacyTemperatureStepMatchesJama() {
    SystemInterface system = createFluid();
    new ThermodynamicOperations(system).TPflash();
    assertEquals(2, system.getNumberOfPhases());
    CricondenBarTemp solver = new CricondenBarTemp(system, 2, system.getNumberOfComponents());
    solver.init();
    solver.setfvec();
    solver.setJac();
    Matrix expectedStep = new Matrix(solver.Jac).solve(new Matrix(solver.fvec, solver.fvec.length));
    Matrix expectedState = new Matrix(solver.u, solver.u.length).minus(expectedStep);
    double[] oldState = solver.uold.clone();

    double convergence = solver.solve();

    assertArrayEquals(expectedStep.getColumnPackedCopy(), solver.dx, 1.0e-14);
    assertArrayEquals(expectedState.getColumnPackedCopy(), solver.u, 1.0e-14);
    assertEquals(expectedStep.norm2() / expectedState.norm2(), convergence, 1.0e-14);
    assertArrayEquals(oldState, solver.uold, 0.0);
    assertNotSame(solver.u, solver.uold);
  }

  /**
   * Preserves the incomplete legacy variant's singular-system failure without changing its equations.
   */
  @Test
  void incompleteTemperatureVariantStillRejectsSingularSystem() {
    SystemInterface system = createFluid();
    new ThermodynamicOperations(system).TPflash();
    assertEquals(2, system.getNumberOfPhases());
    CricondenBarTemp1 solver = new CricondenBarTemp1(system);
    solver.init();
    solver.setfvec();
    solver.setJac();
    Matrix jacobian = new Matrix(solver.Jac);
    Matrix residual = new Matrix(solver.fvec, solver.fvec.length);
    assertThrows(RuntimeException.class, () -> jacobian.solve(residual));
    double[] oldState = solver.u.clone();

    assertThrows(LinearAlgebraException.class, solver::solve);

    assertArrayEquals(oldState, solver.u, 0.0);
    assertArrayEquals(oldState, solver.uold, 0.0);
    assertEquals(1, solver.iter);
  }

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
  @EnabledIfSystemProperty(named = "os.arch", matches = ".*aarch64")
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
  @EnabledIfSystemProperty(named = "os.arch", matches = ".*aarch64")
  @Test
  void newtonStepIsUnchanged() {
    SystemInterface system = createFluid();
    CricondenbarFlash flash = new CricondenbarFlash(system);
    flash.run();
    system.init(3);

    flash.setfvec();
    flash.setJac();
    double[] step = new EjmlLinearAlgebra().solve(flash.Jac, flash.fvec);

    assertArrayEquals(new double[] {0.10371631234839603, -0.10371631234839604, -0.5241645555543996}, step, 0.0);
  }
}
