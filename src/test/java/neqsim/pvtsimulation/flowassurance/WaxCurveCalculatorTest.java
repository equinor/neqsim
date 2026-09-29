package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Failure provenance regressions for issue 4116.
 *
 * @author ESOL
 * @version 1.0
 */
class WaxCurveCalculatorTest {
  /** Fluid whose clone operation fails deterministically. */
  private static class FailingFluid extends SystemSrkEos {
    private static final long serialVersionUID = 1L;

    /** {@inheritDoc} */
    @Override
    public SystemSrkEos clone() {
      throw new IllegalStateException("Injected flash setup failure");
    }
  }

  /** Failed points must remain missing, including after optional smoothing. */
  @Test
  void failedPointsAreNotWaxFreeResults() {
    WaxCurveCalculator calculator = new WaxCurveCalculator(new FailingFluid());
    calculator.setTemperatureRange(10.0, 12.0, 1.0);
    calculator.calculate();
    assertEquals(0, calculator.getSuccessCount());
    assertEquals(3, calculator.getFailCount());
    for (double value : calculator.getRawWaxFractions()) {
      assertTrue(Double.isNaN(value));
    }
    for (double value : calculator.getWaxWeightFractions()) {
      assertTrue(Double.isNaN(value));
    }
    assertTrue(Double.isNaN(calculator.getWaxAppearanceTemperatureC()));
  }

  /** Invalid grids and pressures must fail before allocating or flashing. */
  @Test
  void rejectsInvalidInputs() {
    WaxCurveCalculator calculator = new WaxCurveCalculator(new SystemSrkEos());
    assertThrows(IllegalArgumentException.class, () -> calculator.setPressure(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> calculator.setPressure(0.0));
    assertThrows(IllegalArgumentException.class, () -> calculator.setTemperatureRange(10, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> calculator.setTemperatureRange(-274, 10, 1));
    assertThrows(IllegalArgumentException.class, () -> calculator.setTemperatureRange(0, 10, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> calculator.setTemperatureRange(0, 10, 0));
  }

  /** Deterministic flash sequence for testing curve postprocessing independently of thermodynamics. */
  private static class SequenceCalculator extends WaxCurveCalculator {
    private final double[] values;
    private int next;

    /**
     * Creates the sequence.
     *
     * @param values wax fractions, with NaN indicating a failed flash
     */
    SequenceCalculator(double... values) {
      super(new SystemSrkEos());
      this.values = values;
    }

    /** {@inheritDoc} */
    @Override
    double evaluateWaxFraction(double temperatureC, double pressure) {
      double result = values[next++ % values.length];
      if (Double.isNaN(result)) {
        throw new IllegalStateException("Injected point failure");
      }
      return result;
    }
  }

  /** Failure gaps cannot be filled or bridged by smoothing or WAT interpolation. */
  @Test
  void preservesGapsRawResultsAndDiagnostics() {
    WaxCurveCalculator calculator = new SequenceCalculator(0.0, Double.NaN, 0.2, 0.1);
    calculator.setTemperatureRange(0, 3, 1);
    calculator.calculate();
    assertArrayEquals(new double[] {0.0, Double.NaN, 0.2, 0.1}, calculator.getRawWaxFractions());
    assertArrayEquals(new double[] {0.0, Double.NaN, 0.2, 0.2}, calculator.getWaxWeightFractions());
    assertEquals(3, calculator.getSuccessCount());
    assertEquals(1, calculator.getFailCount());
    assertEquals(1, calculator.getMonotonicityCorrections());
    assertTrue(Double.isNaN(calculator.getWaxAppearanceTemperatureC()));
    assertNotNull(calculator.getFailureMessages()[1]);
    String originalMessage = calculator.getFailureMessages()[1];
    calculator.getFailureMessages()[1] = "changed";
    calculator.getRawWaxFractions()[0] = 0.9;
    assertEquals(originalMessage, calculator.getFailureMessages()[1]);
    assertEquals(0.0, calculator.getRawWaxFractions()[0]);
    calculator.setEnforceMonotonicity(false);
    calculator.calculate();
    assertEquals(0, calculator.getMonotonicityCorrections());
    assertEquals(0.1, calculator.getWaxWeightFractions()[3]);
  }

  /** An onset requires successful wax-free and wax-positive adjacent endpoints. */
  @Test
  void doesNotReportBoundaryAsWat() {
    WaxCurveCalculator positive = new SequenceCalculator(0.2, 0.3);
    positive.setTemperatureRange(0, 1, 1);
    positive.calculate();
    assertTrue(Double.isNaN(positive.getWaxAppearanceTemperatureC()));
    WaxCurveCalculator absent = new SequenceCalculator(0.0, 0.0);
    absent.setTemperatureRange(0, 1, 1);
    absent.calculate();
    assertTrue(Double.isNaN(absent.getWaxAppearanceTemperatureC()));
    WaxCurveCalculator bracket = new SequenceCalculator(0.0, 0.2);
    bracket.setTemperatureRange(0, 1, 1);
    bracket.calculate();
    assertEquals(1.0 - 1e-8 / 0.2, bracket.getWaxAppearanceTemperatureC(), 1e-12);
  }

  /** A failed gap resets the smoothing segment rather than carrying a warm maximum across it. */
  @Test
  void smoothingDoesNotCrossMissingPoint() {
    WaxCurveCalculator calculator = new SequenceCalculator(0.5, Double.NaN, 0.1);
    calculator.setTemperatureRange(0, 2, 1);
    calculator.calculate();
    assertEquals(0.1, calculator.getWaxWeightFractions()[2]);
    assertEquals(0, calculator.getMonotonicityCorrections());
  }

  /** A disabled wax calculation is not evidence of a wax-free fluid. */
  @Test
  void missingWaxConfigurationIsReported() {
    SystemInterface fluid = new SystemSrkEos(300, 10);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule(2);
    WaxCurveCalculator calculator = new WaxCurveCalculator(fluid);
    calculator.setTemperatureRange(0, 1, 1);
    calculator.calculate();
    assertEquals(2, calculator.getFailCount());
    assertEquals(0, calculator.getSuccessCount());
    assertTrue(calculator.getFailureMessages()[0].contains("setMultiphaseWaxCheck"));
  }

  /**
   * Creates a published repository regression fluid without fitting to the paper.
   *
   * @param model solid-solution model name
   * @return fully configured waxy fluid
   */
  private SystemInterface createFluid(String model) {
    NeqSimDataBase.setCreateTemporaryTables(true);
    try {
      SystemInterface fluid = new SystemSrkEos(298.0, 10.0);
      fluid.addComponent("methane", 6.78);
      fluid.addTBPfraction("C19", 10.13, 0.170, 0.7814);
      fluid.addPlusFraction("C20", 10.62, 0.381, 0.850871882888);
      fluid.getCharacterization().characterisePlusFraction();
      fluid.getWaxModel().addTBPWax();
      fluid.createDatabase(true);
      fluid.setMixingRule(2);
      fluid.setWaxModelType(model);
      fluid.addSolidComplexPhase("wax");
      fluid.setMultiphaseWaxCheck(true);
      fluid.setMultiPhaseCheck(true);
      fluid.init(0);
      fluid.init(1);
      return fluid;
    } finally {
      NeqSimDataBase.setCreateTemporaryTables(false);
    }
  }

  /**
   * All four real models agree with direct flashes while preserving the input fluid.
   *
   * @param model solid-solution model
   */
  @ParameterizedTest
  @ValueSource(strings = {"Pedersen", "Won", "Wilson", "Coutinho"})
  void rawCurvesMatchDirectFlashes(String model) {
    SystemInterface fluid = createFluid(model);
    WaxCurveCalculator calculator = new WaxCurveCalculator(fluid);
    calculator.setPressure(5);
    calculator.setTemperatureRange(-12.15, 1.85, 7);
    calculator.setEnforceMonotonicity(false);
    calculator.calculate();
    assertEquals(3, calculator.getSuccessCount());
    assertEquals(0, calculator.getFailCount());
    double[] temperatures = calculator.getTemperaturesC();
    double[] fractions = calculator.getRawWaxFractions();
    for (int i = 0; i < temperatures.length; i++) {
      SystemInterface trial = fluid.clone();
      trial.setTemperature(temperatures[i] + 273.15);
      trial.setPressure(5.0);
      trial.init(0);
      new ThermodynamicOperations(trial).TPflash();
      double expected = trial.getWtFraction(trial.getPhaseNumberOfPhase("wax"));
      // The direct API divides by feed mass; the curve sums flashed phase masses.
      // Their difference is bounded by the TP component-balance residual.
      assertEquals(expected, fractions[i], 1e-8);
      assertTrue(fractions[i] > 0.0 && fractions[i] < 1.0);
    }
    assertEquals(298.0, fluid.getTemperature());
    assertEquals(10.0, fluid.getPressure());
    assertEquals(fractions[1], calculator.calculateAtMultiplePressures(new double[] {5}, temperatures[1]).get(5.0),
        1e-10);
  }
}
