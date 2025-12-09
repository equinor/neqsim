package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.util.fielddevelopment.SensitivityAnalysis.DistributionType;
import neqsim.process.util.fielddevelopment.SensitivityAnalysis.MonteCarloResult;
import neqsim.process.util.fielddevelopment.SensitivityAnalysis.TornadoEntry;
import neqsim.process.util.fielddevelopment.SensitivityAnalysis.TrialResult;
import neqsim.process.util.fielddevelopment.SensitivityAnalysis.UncertainParameter;

/**
 * Unit tests for {@link SensitivityAnalysis}.
 *
 * <p>
 * Tests Monte Carlo simulation for uncertainty quantification, probability distributions, and
 * tornado diagram generation.
 * </p>
 *
 * @author NeqSim Development Team
 */
public class SensitivityAnalysisTest {

  private SensitivityAnalysis sensitivityAnalysis;

  @BeforeEach
  void setUp() {
    sensitivityAnalysis = new SensitivityAnalysis("TestAnalysis");
  }

  @Test
  @DisplayName("SensitivityAnalysis creation with valid name")
  void testCreation() {
    assertNotNull(sensitivityAnalysis, "SensitivityAnalysis should be created");
    assertEquals("TestAnalysis", sensitivityAnalysis.getName(), "Name should match");
  }

  @Test
  @DisplayName("Add uncertain parameter with normal distribution")
  void testAddNormalParameter() {
    UncertainParameter param =
        new UncertainParameter("OilPrice", DistributionType.NORMAL, 75.0, 10.0); // mean=75, std=10

    sensitivityAnalysis.addParameter(param);

    List<UncertainParameter> params = sensitivityAnalysis.getParameters();
    assertEquals(1, params.size(), "Should have 1 parameter");
    assertEquals("OilPrice", params.get(0).getName(), "Parameter name should match");
    assertEquals(DistributionType.NORMAL, params.get(0).getDistribution(),
        "Distribution should be NORMAL");
  }

  @Test
  @DisplayName("Add uncertain parameter with triangular distribution")
  void testAddTriangularParameter() {
    UncertainParameter param =
        new UncertainParameter("RecoveryFactor", DistributionType.TRIANGULAR, 0.25, 0.35, 0.45); // min,
                                                                                                 // mode,
                                                                                                 // max

    sensitivityAnalysis.addParameter(param);

    UncertainParameter retrieved = sensitivityAnalysis.getParameter("RecoveryFactor");
    assertNotNull(retrieved, "Parameter should exist");
    assertEquals(DistributionType.TRIANGULAR, retrieved.getDistribution());
  }

  @Test
  @DisplayName("Add uncertain parameter with uniform distribution")
  void testAddUniformParameter() {
    UncertainParameter param =
        new UncertainParameter("Capex", DistributionType.UNIFORM, 500_000_000.0, 800_000_000.0); // min,
                                                                                                 // max

    sensitivityAnalysis.addParameter(param);

    UncertainParameter retrieved = sensitivityAnalysis.getParameter("Capex");
    assertNotNull(retrieved, "Parameter should exist");
    assertEquals(DistributionType.UNIFORM, retrieved.getDistribution());
  }

  @Test
  @DisplayName("Add uncertain parameter with lognormal distribution")
  void testAddLognormalParameter() {
    UncertainParameter param = new UncertainParameter("Reserves", DistributionType.LOGNORMAL,
        Math.log(100_000_000.0), 0.3); // mu (log of mean), sigma

    sensitivityAnalysis.addParameter(param);

    UncertainParameter retrieved = sensitivityAnalysis.getParameter("Reserves");
    assertNotNull(retrieved, "Parameter should exist");
    assertEquals(DistributionType.LOGNORMAL, retrieved.getDistribution());
  }

  @Test
  @DisplayName("Normal distribution sampling within expected range")
  void testNormalSampling() {
    double mean = 100.0;
    double std = 10.0;
    UncertainParameter param =
        new UncertainParameter("TestParam", DistributionType.NORMAL, mean, std);

    sensitivityAnalysis.addParameter(param);
    sensitivityAnalysis.setSeed(42L); // For reproducibility

    // Sample 1000 values
    double sum = 0;
    int nSamples = 1000;
    for (int i = 0; i < nSamples; i++) {
      double sample = param.sample();
      sum += sample;
    }
    double sampledMean = sum / nSamples;

    // Check that sampled mean is within reasonable range of true mean
    assertEquals(mean, sampledMean, std, "Sampled mean should be close to true mean");
  }

  @Test
  @DisplayName("Uniform distribution sampling within bounds")
  void testUniformSamplingBounds() {
    double min = 10.0;
    double max = 20.0;
    UncertainParameter param =
        new UncertainParameter("TestParam", DistributionType.UNIFORM, min, max);

    sensitivityAnalysis.addParameter(param);

    // Sample 100 values and check bounds
    for (int i = 0; i < 100; i++) {
      double sample = param.sample();
      assertTrue(sample >= min, "Sample should be >= min");
      assertTrue(sample <= max, "Sample should be <= max");
    }
  }

  @Test
  @DisplayName("Triangular distribution sampling within bounds")
  void testTriangularSamplingBounds() {
    double min = 0.0;
    double mode = 0.5;
    double max = 1.0;
    UncertainParameter param =
        new UncertainParameter("TestParam", DistributionType.TRIANGULAR, min, mode, max);

    sensitivityAnalysis.addParameter(param);

    // Sample 100 values and check bounds
    for (int i = 0; i < 100; i++) {
      double sample = param.sample();
      assertTrue(sample >= min, "Sample should be >= min");
      assertTrue(sample <= max, "Sample should be <= max");
    }
  }

  @Test
  @DisplayName("Run Monte Carlo simulation")
  void testRunMonteCarlo() {
    // Add parameters
    sensitivityAnalysis
        .addParameter(new UncertainParameter("OilPrice", DistributionType.NORMAL, 75.0, 15.0));
    sensitivityAnalysis.addParameter(new UncertainParameter("Reserves", DistributionType.TRIANGULAR,
        50_000_000.0, 100_000_000.0, 150_000_000.0));

    int numTrials = 100;
    sensitivityAnalysis.setNumberOfTrials(numTrials);
    sensitivityAnalysis.setSeed(12345L);

    MonteCarloResult result = sensitivityAnalysis.runMonteCarlo();

    assertNotNull(result, "Result should not be null");
    assertEquals(numTrials, result.getTrialCount(), "Should have correct number of trials");
  }

  @Test
  @DisplayName("Monte Carlo result statistics")
  void testMonteCarloStatistics() {
    sensitivityAnalysis.addParameter(
        new UncertainParameter("Revenue", DistributionType.NORMAL, 1_000_000.0, 200_000.0));

    sensitivityAnalysis.setNumberOfTrials(500);
    sensitivityAnalysis.setSeed(42L);

    MonteCarloResult result = sensitivityAnalysis.runMonteCarlo();

    double p10 = result.getP10();
    double p50 = result.getP50();
    double p90 = result.getP90();

    assertTrue(p10 < p50, "P10 should be less than P50");
    assertTrue(p50 < p90, "P50 should be less than P90");
  }

  @Test
  @DisplayName("Trial results contain sampled values")
  void testTrialResults() {
    sensitivityAnalysis
        .addParameter(new UncertainParameter("Param1", DistributionType.UNIFORM, 0.0, 100.0));
    sensitivityAnalysis
        .addParameter(new UncertainParameter("Param2", DistributionType.UNIFORM, 0.0, 50.0));

    sensitivityAnalysis.setNumberOfTrials(10);
    sensitivityAnalysis.setSeed(123L);

    MonteCarloResult result = sensitivityAnalysis.runMonteCarlo();

    List<TrialResult> trials = result.getTrials();
    assertEquals(10, trials.size(), "Should have 10 trials");

    TrialResult firstTrial = trials.get(0);
    Map<String, Double> sampledValues = firstTrial.getSampledValues();

    assertTrue(sampledValues.containsKey("Param1"), "Should have Param1");
    assertTrue(sampledValues.containsKey("Param2"), "Should have Param2");
  }

  @Test
  @DisplayName("Generate tornado diagram data")
  void testTornadoDiagram() {
    sensitivityAnalysis
        .addParameter(new UncertainParameter("HighImpact", DistributionType.UNIFORM, 0.0, 100.0));
    sensitivityAnalysis
        .addParameter(new UncertainParameter("LowImpact", DistributionType.UNIFORM, 40.0, 60.0));

    sensitivityAnalysis.setNumberOfTrials(100);
    sensitivityAnalysis.setSeed(42L);

    MonteCarloResult result = sensitivityAnalysis.runMonteCarlo();
    List<TornadoEntry> tornado = result.generateTornadoDiagram();

    assertNotNull(tornado, "Tornado data should not be null");
    assertEquals(2, tornado.size(), "Should have 2 entries");

    // Higher impact parameter should be first
    TornadoEntry first = tornado.get(0);
    TornadoEntry second = tornado.get(1);
    assertTrue(first.getImpact() >= second.getImpact(),
        "Tornado should be sorted by impact (high to low)");
  }

  @Test
  @DisplayName("Convergence check for Monte Carlo")
  void testConvergenceCheck() {
    sensitivityAnalysis
        .addParameter(new UncertainParameter("TestParam", DistributionType.NORMAL, 50.0, 5.0));

    sensitivityAnalysis.setNumberOfTrials(1000);
    sensitivityAnalysis.setSeed(42L);
    sensitivityAnalysis.setConvergenceThreshold(0.01);

    MonteCarloResult result = sensitivityAnalysis.runMonteCarlo();

    assertTrue(result.isConverged(), "Should converge with 1000 trials");
  }

  @Test
  @DisplayName("Export results to CSV")
  void testExportToCsv() {
    sensitivityAnalysis
        .addParameter(new UncertainParameter("Param1", DistributionType.NORMAL, 100.0, 10.0));

    sensitivityAnalysis.setNumberOfTrials(50);
    sensitivityAnalysis.setSeed(42L);

    MonteCarloResult result = sensitivityAnalysis.runMonteCarlo();
    String csv = result.exportToCsv();

    assertNotNull(csv, "CSV should not be null");
    assertTrue(csv.contains(","), "CSV should contain commas");
    assertTrue(csv.contains("Param1"), "CSV should contain parameter name");
  }

  @Test
  @DisplayName("Correlation coefficient calculation")
  void testCorrelationCalculation() {
    sensitivityAnalysis
        .addParameter(new UncertainParameter("X", DistributionType.UNIFORM, 0.0, 100.0));
    sensitivityAnalysis
        .addParameter(new UncertainParameter("Y", DistributionType.UNIFORM, 0.0, 100.0));

    sensitivityAnalysis.setNumberOfTrials(100);
    sensitivityAnalysis.setSeed(42L);

    MonteCarloResult result = sensitivityAnalysis.runMonteCarlo();
    double correlation = result.getCorrelation("X", "Y");

    // Uniform independent distributions should have low correlation
    assertTrue(Math.abs(correlation) <= 1.0, "Correlation should be between -1 and 1");
  }

  @Test
  @DisplayName("Set correlated parameters")
  void testCorrelatedParameters() {
    UncertainParameter param1 =
        new UncertainParameter("Reserves", DistributionType.NORMAL, 100_000_000.0, 20_000_000.0);
    UncertainParameter param2 =
        new UncertainParameter("ProductionRate", DistributionType.NORMAL, 50_000.0, 5_000.0);

    sensitivityAnalysis.addParameter(param1);
    sensitivityAnalysis.addParameter(param2);

    // Set correlation between reserves and production rate
    sensitivityAnalysis.setCorrelation("Reserves", "ProductionRate", 0.7);

    double correlation = sensitivityAnalysis.getCorrelation("Reserves", "ProductionRate");
    assertEquals(0.7, correlation, 0.001, "Correlation should match set value");
  }

  @Test
  @DisplayName("Mean and standard deviation from Monte Carlo")
  void testMeanAndStdDev() {
    sensitivityAnalysis
        .addParameter(new UncertainParameter("TestParam", DistributionType.NORMAL, 100.0, 10.0));

    sensitivityAnalysis.setNumberOfTrials(5000);
    sensitivityAnalysis.setSeed(42L);

    MonteCarloResult result = sensitivityAnalysis.runMonteCarlo();

    double mean = result.getMean();
    double stdDev = result.getStandardDeviation();

    // With 5000 samples, mean should be close to 100
    assertEquals(100.0, mean, 2.0, "Mean should be close to expected");
    // Standard deviation should be close to 10
    assertEquals(10.0, stdDev, 2.0, "StdDev should be close to expected");
  }

  @Test
  @DisplayName("Histogram data generation")
  void testHistogramGeneration() {
    sensitivityAnalysis
        .addParameter(new UncertainParameter("TestParam", DistributionType.UNIFORM, 0.0, 100.0));

    sensitivityAnalysis.setNumberOfTrials(1000);
    sensitivityAnalysis.setSeed(42L);

    MonteCarloResult result = sensitivityAnalysis.runMonteCarlo();

    int numBins = 10;
    int[] histogram = result.generateHistogram(numBins);

    assertNotNull(histogram, "Histogram should not be null");
    assertEquals(numBins, histogram.length, "Should have correct number of bins");

    // Sum of all bins should equal number of trials
    int sum = 0;
    for (int count : histogram) {
      sum += count;
      assertTrue(count >= 0, "Bin count should be non-negative");
    }
    assertEquals(1000, sum, "Total histogram count should equal number of trials");
  }

  @Test
  @DisplayName("Sensitivity index calculation")
  void testSensitivityIndex() {
    sensitivityAnalysis
        .addParameter(new UncertainParameter("HighVar", DistributionType.UNIFORM, 0.0, 100.0));
    sensitivityAnalysis
        .addParameter(new UncertainParameter("LowVar", DistributionType.UNIFORM, 45.0, 55.0));

    sensitivityAnalysis.setNumberOfTrials(500);
    sensitivityAnalysis.setSeed(42L);

    MonteCarloResult result = sensitivityAnalysis.runMonteCarlo();

    double highVarSensitivity = result.getSensitivityIndex("HighVar");
    double lowVarSensitivity = result.getSensitivityIndex("LowVar");

    assertTrue(highVarSensitivity > lowVarSensitivity,
        "High variance parameter should have higher sensitivity index");
  }

  @Test
  @DisplayName("Clear parameters")
  void testClearParameters() {
    sensitivityAnalysis
        .addParameter(new UncertainParameter("Param1", DistributionType.NORMAL, 0.0, 1.0));
    sensitivityAnalysis
        .addParameter(new UncertainParameter("Param2", DistributionType.NORMAL, 0.0, 1.0));

    assertEquals(2, sensitivityAnalysis.getParameters().size());

    sensitivityAnalysis.clearParameters();

    assertEquals(0, sensitivityAnalysis.getParameters().size(), "Parameters should be cleared");
  }

  @Test
  @DisplayName("Remove single parameter")
  void testRemoveParameter() {
    sensitivityAnalysis
        .addParameter(new UncertainParameter("Keep", DistributionType.NORMAL, 0.0, 1.0));
    sensitivityAnalysis
        .addParameter(new UncertainParameter("Remove", DistributionType.NORMAL, 0.0, 1.0));

    sensitivityAnalysis.removeParameter("Remove");

    assertEquals(1, sensitivityAnalysis.getParameters().size());
    assertNull(sensitivityAnalysis.getParameter("Remove"), "Removed parameter should not exist");
    assertNotNull(sensitivityAnalysis.getParameter("Keep"), "Kept parameter should exist");
  }
}
