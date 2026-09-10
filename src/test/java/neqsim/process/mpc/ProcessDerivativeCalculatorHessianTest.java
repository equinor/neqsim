package neqsim.process.mpc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Regression and analytical checks for scalar process Hessians (issue #3616). */
class ProcessDerivativeCalculatorHessianTest extends neqsim.NeqSimTest {
  private static final double[][] FIRST_HESSIAN = { { 6.0, 2.0, 7.0 }, { 2.0, 10.0, -4.0 }, { 7.0, -4.0, 22.0 } };
  private static final double[][] SECOND_HESSIAN = { { -4.0, 6.0, -3.0 }, { 6.0, 8.0, 8.0 }, { -3.0, 8.0, 1.0 } };

  @Test
  void linearStreamWithThreeInputsAndTwoOutputsHasZeroHessian() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(500.0, "kg/hr");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();
    ProcessDerivativeCalculator calc = new ProcessDerivativeCalculator(process);
    calc.addInputVariable("Feed.flowRate", "kg/hr");
    calc.addInputVariable("Feed.pressure", "bara");
    calc.addInputVariable("Feed.temperature", "K");
    calc.addOutputVariable("Feed.flowRate", "kg/hr");
    calc.addOutputVariable("Feed.pressure", "bara");
    double[] inputs = calc.getBaseInputValues();
    double[] outputs = calc.getBaseOutputValues();

    assertMatrixEquals(new double[3][3], calc.calculateHessian("Feed.flowRate"), 1e-6);
    assertArrayEquals(inputs, calc.getBaseInputValues(), 0.0);
    assertArrayEquals(outputs, calc.getBaseOutputValues(), 0.0);
    assertEquals(inputs[0], feed.getFlowRate("kg/hr"), 1e-10);
    assertEquals(inputs[1], feed.getPressure("bara"), 1e-10);
    assertEquals(inputs[2], feed.getTemperature("K"), 1e-10);
    calc.setMethod(ProcessDerivativeCalculator.DerivativeMethod.FORWARD_DIFFERENCE);
    assertArrayEquals(new double[] { 1.0, 0.0, 0.0 }, calc.getGradient("Feed.flowRate"), 1e-8);
  }

  @ParameterizedTest
  @EnumSource(ProcessDerivativeCalculator.DerivativeMethod.class)
  void selectedQuadraticOutputHasAnalyticalHessian(ProcessDerivativeCalculator.DerivativeMethod method) {
    QuadraticProcess process = new QuadraticProcess();
    ProcessDerivativeCalculator calc = calculator(process, 3);
    calc.addOutputVariable("Polynomial.first", "");
    calc.addOutputVariable("Polynomial.second", "");
    calc.setMethod(method);
    double[] inputs = calc.getBaseInputValues();
    double[] outputs = calc.getBaseOutputValues();

    assertMatrixEquals(FIRST_HESSIAN, calc.calculateHessian("Polynomial.first"), 1e-8);
    assertRestored(process, calc, inputs, outputs);
    assertMatrixEquals(SECOND_HESSIAN, calc.calculateHessian("Polynomial.second"), 1e-8);
    assertRestored(process, calc, inputs, outputs);
    // A subsequent forward Jacobian uses the original cached output, not a perturbation.
    calc.setMethod(ProcessDerivativeCalculator.DerivativeMethod.FORWARD_DIFFERENCE);
    assertArrayEquals(new double[] { 20.0 + 3.0 * 0.02, -13.0 + 5.0 * 0.03, 55.75 + 11.0 * 0.04 },
        calc.getGradient("Polynomial.first"), 1e-9);
  }

  @Test
  void moreOutputsThanInputsUsesSelectedOutputBeyondInputCount() {
    QuadraticProcess process = new QuadraticProcess();
    ProcessDerivativeCalculator calc = calculator(process, 2);
    calc.addOutputVariable("Polynomial.x", "");
    calc.addOutputVariable("Polynomial.first", "");
    calc.addOutputVariable("Polynomial.second", "");

    assertMatrixEquals(new double[][] { { -4.0, 6.0 }, { 6.0, 8.0 } }, calc.calculateHessian("Polynomial.second"),
        1e-8);
    assertMatrixEquals(new double[][] { { 6.0, 2.0 }, { 2.0, 10.0 } }, calc.calculateHessian("Polynomial.first"), 1e-8);
    assertMatrixEquals(new double[2][2], calc.calculateHessian("Polynomial.x"), 1e-8);
  }

  @ParameterizedTest
  @EnumSource(FailureMode.class)
  void restoresInputsOutputsAndCacheAfterMixedPerturbationFailure(FailureMode failureMode) {
    QuadraticProcess process = new QuadraticProcess();
    ProcessDerivativeCalculator calc = calculator(process, 3);
    calc.addOutputVariable("Polynomial.first", "");
    calc.addOutputVariable("Polynomial.second", "");
    double[] inputs = calc.getBaseInputValues();
    double[] outputs = calc.getBaseOutputValues();
    process.unit.failureMode = failureMode;

    RuntimeException failure = assertThrows(RuntimeException.class, () -> calc.calculateHessian("Polynomial.first"));
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    assertSame(process.unit.perturbationFailure, cause);
    assertRestored(process, calc, inputs, outputs);
    assertMatrixEquals(SECOND_HESSIAN, calc.calculateHessian("Polynomial.second"), 1e-8);
  }

  @Test
  void restorationFailurePreservesOriginalExceptionAndInvalidatesCache() {
    QuadraticProcess process = new QuadraticProcess();
    ProcessDerivativeCalculator calc = calculator(process, 3);
    calc.addOutputVariable("Polynomial.first", "");
    double[] inputs = calc.getBaseInputValues();
    double[] outputs = calc.getBaseOutputValues();
    process.unit.failureMode = FailureMode.RUN;
    process.failRestoration = true;

    RuntimeException failure = assertThrows(RuntimeException.class, () -> calc.calculateHessian("Polynomial.first"));
    assertSame(process.unit.perturbationFailure, failure);
    assertEquals(1, failure.getSuppressed().length);
    assertSame(process.restorationFailure, failure.getSuppressed()[0]);
    int runsAfterFailure = process.runs;
    assertArrayEquals(inputs, calc.getBaseInputValues(), 0.0);
    assertTrue(process.runs > runsAfterFailure, "A failed restoration must force base-case recalculation");
    assertArrayEquals(outputs, calc.getBaseOutputValues(), 0.0);
    assertMatrixEquals(FIRST_HESSIAN, calc.calculateHessian("Polynomial.first"), 1e-8);
  }

  @Test
  void unknownOutputDoesNotChangeProcess() {
    QuadraticProcess process = new QuadraticProcess();
    ProcessDerivativeCalculator calc = calculator(process, 3);
    calc.addOutputVariable("Polynomial.first", "");
    assertThrows(IllegalArgumentException.class, () -> calc.calculateHessian("Polynomial.missing"));
    assertEquals(0, process.runs);
  }

  private ProcessDerivativeCalculator calculator(QuadraticProcess process, int inputs) {
    ProcessDerivativeCalculator calc = new ProcessDerivativeCalculator(process);
    calc.addInputVariable("Polynomial.x", "", 0.02);
    calc.addInputVariable("Polynomial.y", "", 0.03);
    if (inputs == 3) {
      calc.addInputVariable("Polynomial.z", "", 0.04);
    }
    return calc;
  }

  private void assertRestored(QuadraticProcess process, ProcessDerivativeCalculator calc, double[] inputs,
      double[] outputs) {
    assertArrayEquals(inputs, new double[] { process.unit.x, process.unit.y, process.unit.z }, 0.0);
    assertArrayEquals(outputs, new double[] { process.unit.first, process.unit.second }, 0.0);
    int runs = process.runs;
    assertArrayEquals(inputs, calc.getBaseInputValues(), 0.0);
    assertArrayEquals(outputs, calc.getBaseOutputValues(), 0.0);
    assertEquals(runs, process.runs, "Successful restoration preserves a valid base-state cache");
  }

  private void assertMatrixEquals(double[][] expected, double[][] actual, double tolerance) {
    assertEquals(expected.length, actual.length);
    for (int i = 0; i < expected.length; i++) {
      assertArrayEquals(expected[i], actual[i], tolerance, "Hessian row " + i);
      for (int j = 0; j < actual[i].length; j++) {
        assertTrue(Double.isFinite(actual[i][j]));
        assertEquals(actual[i][j], actual[j][i], 0.0, "Hessian symmetry");
      }
    }
  }

  /** Points at which a process perturbation can fail. */
  enum FailureMode {
    RUN, SETTER, OUTPUT
  }

  /** Deterministic process with two scalar polynomial responses. */
  static class QuadraticProcess extends ProcessSystem {
    private static final long serialVersionUID = 1L;
    private final QuadraticUnit unit = new QuadraticUnit();
    private final RuntimeException restorationFailure = new IllegalStateException("Restoration run failed");
    private boolean failRestoration;
    private int runs;

    QuadraticProcess() {
      add(unit);
    }

    @Override
    public void run() {
      runs++;
      unit.failIfRequested(FailureMode.RUN);
      if (failRestoration && unit.failureTriggered && unit.x == 1.25 && unit.y == -0.75 && unit.z == 2.0) {
        failRestoration = false;
        throw restorationFailure;
      }
      unit.first = 3.0 * unit.x * unit.x + 2.0 * unit.x * unit.y + 5.0 * unit.y * unit.y + 7.0 * unit.x * unit.z
          - 4.0 * unit.y * unit.z + 11.0 * unit.z * unit.z;
      unit.second = -2.0 * unit.x * unit.x + 6.0 * unit.x * unit.y + 4.0 * unit.y * unit.y - 3.0 * unit.x * unit.z
          + 8.0 * unit.y * unit.z + 0.5 * unit.z * unit.z + unit.y;
    }
  }

  /** Public accessors allow the production path resolver to read/write the analytical fixture. */
  public static class QuadraticUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1L;
    private double x = 1.25;
    private double y = -0.75;
    private double z = 2.0;
    private double first;
    private double second;
    private FailureMode failureMode;
    private boolean failureTriggered;
    private final RuntimeException perturbationFailure = new IllegalStateException("Mixed perturbation failed");

    QuadraticUnit() {
      super("Polynomial");
    }

    @Override
    public void run(java.util.UUID id) {
      // The enclosing analytical process evaluates both responses together.
      setCalculationIdentifier(id);
    }

    private void failIfRequested(FailureMode mode) {
      if (failureMode == mode && x != 1.25 && y != -0.75) {
        failureMode = null;
        failureTriggered = true;
        // Include a third changed input to detect incomplete rollback.
        z = 9.0;
        throw perturbationFailure;
      }
    }

    public double getX() {
      return x;
    }

    public void setX(double value) {
      x = value;
    }

    public double getY() {
      return y;
    }

    public void setY(double value) {
      y = value;
      failIfRequested(FailureMode.SETTER);
    }

    public double getZ() {
      return z;
    }

    public void setZ(double value) {
      z = value;
    }

    public double getFirst() {
      failIfRequested(FailureMode.OUTPUT);
      return first;
    }

    public double getSecond() {
      return second;
    }
  }
}
