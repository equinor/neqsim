package neqsim.pvtsimulation.regression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Method;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks the actual optimizer samples across observables with different units and magnitudes.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PVTRegressionWeightingTest {
  /**
   * Equal relative errors must contribute equally for Rs, Bo and density, including experiment weighting.
   *
   * @throws Exception if optimizer sample registration cannot be inspected
   */
  @Test
  void dleSamplesBalanceRelativeErrorsAndRetainExperimentWeight() throws Exception {
    PVTRegression regression = regression();
    regression.addDLEData(new double[] {200.0, 100.0}, new double[] {150.0, 75.0}, new double[] {1.4, 1.2},
        new double[] {700.0, 800.0}, 373.15);
    for (double weight : new double[] {1.0, 2.0}) {
      regression.setExperimentWeight(ExperimentType.DLE, weight);
      ArrayList<SampleValue> samples = samples(regression, "addDLESamples");
      assertEquals(6, samples.size());
      for (SampleValue sample : samples) {
        double onePercentError = 0.01 * sample.getSampleValue();
        assertEquals(weight * weight, Math.pow(onePercentError / sample.getStandardDeviation(), 2), 1e-12);
      }
    }
  }

  /**
   * Separator GOR and Bo use the same relative residual scale despite their different units.
   *
   * @throws Exception if optimizer sample registration cannot be inspected
   */
  @Test
  void separatorSamplesBalanceRelativeErrors() throws Exception {
    PVTRegression regression = regression();
    regression.addSeparatorData(125.0, 1.35, 35.0, 10.0, 288.15, 373.15);
    regression.setExperimentWeight(ExperimentType.SEPARATOR, 3.0);
    ArrayList<SampleValue> samples = samples(regression, "addSeparatorSamples");
    assertEquals(2, samples.size());
    for (SampleValue sample : samples) {
      double onePercentError = 0.01 * sample.getSampleValue();
      assertEquals(9.0, Math.pow(onePercentError / sample.getStandardDeviation(), 2), 1e-12);
    }
  }

  /**
   * Zero and near-zero gas-oil ratios keep a finite residual scale instead of dividing by zero.
   *
   * @throws Exception if optimizer sample registration cannot be inspected
   */
  @Test
  void zeroAndSmallObservationsUseTheAbsoluteFloorBeforeWeighting() throws Exception {
    PVTRegression regression = regression();
    regression.addDLEData(new double[] {100.0, 50.0}, new double[] {0.01, 0.0}, new double[] {1.2, 1.0},
        new double[] {750.0, 800.0}, 373.15);
    regression.addSeparatorData(0.0, 1.0, 35.0, 10.0, 288.15, 373.15);
    regression.setExperimentWeight(ExperimentType.DLE, 2.0);
    regression.setExperimentWeight(ExperimentType.SEPARATOR, 4.0);
    ArrayList<SampleValue> dle = samples(regression, "addDLESamples");
    assertEquals(0.0005, dle.get(0).getStandardDeviation(), 1e-15);
    assertEquals(0.0005, dle.get(3).getStandardDeviation(), 1e-15);
    assertEquals(0.00025, samples(regression, "addSeparatorSamples").get(0).getStandardDeviation(), 1e-15);
  }

  /**
   * Invoke the same sample builders used by runRegression without running an optimizer or a flash.
   *
   * @param regression configured regression
   * @param builder sample-registration method
   * @return actual optimizer samples
   * @throws Exception if sample registration cannot be inspected
   */
  private static ArrayList<SampleValue> samples(PVTRegression regression, String builder) throws Exception {
    Method create = PVTRegression.class.getDeclaredMethod("createRegressionFunction");
    create.setAccessible(true);
    PVTRegressionFunction function = (PVTRegressionFunction) create.invoke(regression);
    Method add = PVTRegression.class.getDeclaredMethod(builder, ArrayList.class, PVTRegressionFunction.class);
    add.setAccessible(true);
    ArrayList<SampleValue> samples = new ArrayList<>();
    add.invoke(regression, samples, function);
    return samples;
  }

  /**
   * Create a small synthetic fluid for sample registration; no thermodynamic calculation is required.
   *
   * @return fresh regression
   */
  private static PVTRegression regression() {
    SystemInterface fluid = new SystemSrkEos(373.15, 300.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("n-decane", 0.30);
    fluid.setMixingRule("classic");
    return new PVTRegression(fluid);
  }
}
