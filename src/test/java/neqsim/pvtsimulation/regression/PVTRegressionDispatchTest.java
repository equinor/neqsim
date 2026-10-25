package neqsim.pvtsimulation.regression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Target-dispatch and optimizer rejection contracts for PVT regression.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PVTRegressionDispatchTest {
  /**
   * Covers every declared target and distinguishes implemented calculations from unsupported protocols.
   *
   * @param type target to evaluate
   */
  @ParameterizedTest
  @EnumSource(ExperimentType.class)
  void dispatchesEveryDeclaredExperimentType(ExperimentType type) {
    PVTRegressionFunction function = createFunction();
    int property = type == ExperimentType.CCE ? 0 : 1;
    double[] values = type == ExperimentType.SEPARATOR ? new double[] {20.0, 353.15, type.ordinal(), property, 373.15}
        : new double[] {20.0, 353.15, type.ordinal(), property};
    switch (type) {
    case SATURATION_PRESSURE:
    case SWELLING:
      UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
          () -> function.calcValue(values));
      assertTrue(failure.getMessage().contains(type.name()));
      break;
    case CCE:
    case CVD:
    case DLE:
    case SEPARATOR:
    case VISCOSITY:
      double prediction = function.calcValue(values);
      assertTrue(Double.isFinite(prediction) && prediction > 0.0,
          "implemented positive observable for " + type + ": " + prediction);
      break;
    default:
      throw new AssertionError("Declare the expected regression support for " + type);
    }
  }

  /**
   * Rejects ordinals that would otherwise be silently truncated or cast to a valid target.
   *
   * @param ordinal malformed target ordinal
   */
  @ParameterizedTest
  @ValueSource(doubles = {-1.0, 0.5, 7.0, Double.NaN, Double.POSITIVE_INFINITY})
  void rejectsMalformedExperimentOrdinals(double ordinal) {
    assertThrows(IllegalArgumentException.class,
        () -> createFunction().calcValue(new double[] {100.0, 353.15, ordinal, 0.0}));
  }

  /**
   * Rejects incomplete public dependent-variable arrays before evaluating a fluid.
   */
  @Test
  void rejectsIncompleteDependentValues() {
    assertThrows(IllegalArgumentException.class, () -> createFunction().calcValue(null));
    assertThrows(IllegalArgumentException.class, () -> createFunction().calcValue(new double[] {100.0, 353.15}));
  }

  /**
   * Prevents a zero-valued observation from making an unsupported prediction appear to fit perfectly.
   *
   * @param type unsupported regression target
   */
  @ParameterizedTest
  @EnumSource(value = ExperimentType.class, names = {"SATURATION_PRESSURE", "SWELLING"})
  void optimizerPropagatesUnsupportedTargetAndClearsPreviousSuccess(ExperimentType type) {
    LevenbergMarquardt optimizer = new LevenbergMarquardt();
    SampleValue supported = new SampleValue(1.0, 0.1, new double[] {0.0});
    supported.setFunction(new ConstantFunction());
    optimizer.setSampleSet(new SampleSet(new SampleValue[] {supported}));
    optimizer.solve();
    assertTrue(optimizer.isSolved());
    assertTrue(optimizer.getResult().isConverged());

    SampleValue unsupported = new SampleValue(0.0, 0.1, new double[] {100.0, 353.15, type.ordinal(), 0.0});
    unsupported.setFunction(createFunction());
    optimizer.setSampleSet(new SampleSet(new SampleValue[] {unsupported}));
    assertThrows(UnsupportedOperationException.class, optimizer::calcChiSquare);
    assertThrows(UnsupportedOperationException.class, optimizer::solve);
    assertFalse(optimizer.isSolved());
    assertFalse(optimizer.getResult().isConverged());
    assertTrue(Double.isNaN(optimizer.getResult().getFinalChiSquare()));
  }

  /**
   * Creates a valid, untuned SRK fluid for dispatch tests.
   *
   * @return regression function with no fitting parameters
   */
  private static PVTRegressionFunction createFunction() {
    SystemInterface fluid = new SystemSrkEos(353.15, 100.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-decane", 0.5);
    fluid.setMixingRule("classic");
    return new PVTRegressionFunction(fluid, Collections.<RegressionParameterConfig>emptyList(),
        new EnumMap<ExperimentType, Double>(ExperimentType.class));
  }

  /**
   * Exactly fitted synthetic function that establishes a prior successful optimizer result.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class ConstantFunction extends LevenbergMarquardtFunction {
    /** Creates a one-parameter constant function. */
    ConstantFunction() {
      params = new double[] {1.0};
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
      return params[0];
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int index, double value) {
      params[index] = value;
    }
  }
}
