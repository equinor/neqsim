package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.fielddevelopment.SensitivityAnalysis.DistributionType;
import neqsim.process.util.fielddevelopment.SensitivityAnalysis.MonteCarloResult;
import neqsim.process.util.fielddevelopment.SensitivityAnalysis.SensitivityConfig;
import neqsim.process.util.fielddevelopment.SensitivityAnalysis.TrialResult;
import neqsim.process.util.fielddevelopment.SensitivityAnalysis.UncertainParameter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

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

  private ProcessSystem process;
  private Stream feedStream;
  private SensitivityAnalysis sensitivityAnalysis;

  @BeforeEach
  void setUp() {
    // Create a simple process system
    process = new ProcessSystem();

    SystemInterface gasSystem = new SystemSrkEos(298.15, 50.0);
    gasSystem.addComponent("methane", 0.85);
    gasSystem.addComponent("ethane", 0.10);
    gasSystem.addComponent("propane", 0.05);
    gasSystem.setMixingRule("classic");

    feedStream = new Stream("Feed", gasSystem);
    feedStream.setFlowRate(10000.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(50.0, "bara");

    Separator separator = new Separator("Separator", feedStream);

    process.add(feedStream);
    process.add(separator);

    sensitivityAnalysis = new SensitivityAnalysis(process);
  }

  @Test
  @DisplayName("SensitivityAnalysis creation with valid process")
  void testCreation() {
    assertNotNull(sensitivityAnalysis, "SensitivityAnalysis should be created");
  }

  @Test
  @DisplayName("Add uncertain parameter with triangular distribution")
  void testAddTriangularParameter() {
    UncertainParameter param = UncertainParameter.triangular("Temperature", 20.0, 25.0, 30.0,
        (proc, val) -> feedStream.setTemperature(val, "C"));

    sensitivityAnalysis.addParameter(param);

    List<UncertainParameter> params = sensitivityAnalysis.getParameters();
    assertEquals(1, params.size(), "Should have 1 parameter");
    assertEquals("Temperature", params.get(0).getName(), "Parameter name should match");
    assertEquals(DistributionType.TRIANGULAR, params.get(0).getDistribution(),
        "Distribution should be TRIANGULAR");
  }

  @Test
  @DisplayName("Add uncertain parameter with normal distribution")
  void testAddNormalParameter() {
    UncertainParameter param = UncertainParameter.normal("Pressure", 45.0, 50.0, 55.0,
        (proc, val) -> feedStream.setPressure(val, "bara"));

    sensitivityAnalysis.addParameter(param);

    UncertainParameter retrieved = sensitivityAnalysis.getParameters().get(0);
    assertNotNull(retrieved, "Parameter should exist");
    assertEquals(DistributionType.NORMAL, retrieved.getDistribution());
  }

  @Test
  @DisplayName("Add uncertain parameter with uniform distribution")
  void testAddUniformParameter() {
    UncertainParameter param = UncertainParameter.uniform("FlowRate", 8000.0, 12000.0,
        (proc, val) -> feedStream.setFlowRate(val, "kg/hr"));

    sensitivityAnalysis.addParameter(param);

    UncertainParameter retrieved = sensitivityAnalysis.getParameters().get(0);
    assertNotNull(retrieved, "Parameter should exist");
    assertEquals(DistributionType.UNIFORM, retrieved.getDistribution());
  }

  @Test
  @DisplayName("Add uncertain parameter with lognormal distribution")
  void testAddLognormalParameter() {
    UncertainParameter param =
        UncertainParameter.lognormal("Permeability", 50.0, 100.0, 200.0, (proc, val) -> {
          /* dummy setter */
        });

    sensitivityAnalysis.addParameter(param);

    UncertainParameter retrieved = sensitivityAnalysis.getParameters().get(0);
    assertNotNull(retrieved, "Parameter should exist");
    assertEquals(DistributionType.LOGNORMAL, retrieved.getDistribution());
  }

  @Test
  @DisplayName("Triangular distribution sampling within bounds")
  void testTriangularSamplingBounds() {
    double min = 10.0;
    double mode = 15.0;
    double max = 20.0;
    UncertainParameter param =
        UncertainParameter.triangular("TestParam", min, mode, max, (proc, val) -> {
        });

    Random rng = new Random(42L);

    // Sample 100 values and check bounds
    for (int i = 0; i < 100; i++) {
      double sample = param.sample(rng);
      assertTrue(sample >= min, "Sample should be >= min");
      assertTrue(sample <= max, "Sample should be <= max");
    }
  }

  @Test
  @DisplayName("Uniform distribution sampling within bounds")
  void testUniformSamplingBounds() {
    double min = 10.0;
    double max = 20.0;
    UncertainParameter param = UncertainParameter.uniform("TestParam", min, max, (proc, val) -> {
    });

    Random rng = new Random(42L);

    // Sample 100 values and check bounds
    for (int i = 0; i < 100; i++) {
      double sample = param.sample(rng);
      assertTrue(sample >= min, "Sample should be >= min");
      assertTrue(sample <= max, "Sample should be <= max");
    }
  }

  @Test
  @DisplayName("Normal distribution sampling produces expected range")
  void testNormalSampling() {
    double p10 = 80.0;
    double p50 = 100.0;
    double p90 = 120.0;
    UncertainParameter param =
        UncertainParameter.normal("TestParam", p10, p50, p90, (proc, val) -> {
        });

    Random rng = new Random(42L);

    // Sample 1000 values
    double sum = 0;
    int nSamples = 1000;
    for (int i = 0; i < nSamples; i++) {
      double sample = param.sample(rng);
      sum += sample;
    }
    double sampledMean = sum / nSamples;

    // Check that sampled mean is reasonably close to P50
    assertEquals(p50, sampledMean, 5.0, "Sampled mean should be close to P50");
  }

  @Test
  @DisplayName("Lognormal distribution sampling produces positive values")
  void testLognormalSamplingPositive() {
    UncertainParameter param =
        UncertainParameter.lognormal("TestParam", 50.0, 100.0, 200.0, (proc, val) -> {
        });

    Random rng = new Random(42L);

    // All samples should be positive
    for (int i = 0; i < 100; i++) {
      double sample = param.sample(rng);
      assertTrue(sample > 0, "Lognormal sample should be positive");
    }
  }

  @Test
  @DisplayName("Parameter range calculation")
  void testParameterRange() {
    UncertainParameter param =
        UncertainParameter.triangular("TestParam", 10.0, 15.0, 30.0, (proc, val) -> {
        });

    assertEquals(20.0, param.getRange(), 0.001, "Range should be P90 - P10");
  }

  @Test
  @DisplayName("Parameter percentiles accessible")
  void testParameterPercentiles() {
    UncertainParameter param =
        UncertainParameter.triangular("TestParam", 10.0, 15.0, 20.0, (proc, val) -> {
        });

    assertEquals(10.0, param.getP10(), 0.001);
    assertEquals(15.0, param.getP50(), 0.001);
    assertEquals(20.0, param.getP90(), 0.001);
  }

  @Test
  @DisplayName("Clear parameters removes all")
  void testClearParameters() {
    sensitivityAnalysis
        .addParameter(UncertainParameter.triangular("Param1", 10.0, 15.0, 20.0, (proc, val) -> {
        }));
    sensitivityAnalysis
        .addParameter(UncertainParameter.triangular("Param2", 20.0, 25.0, 30.0, (proc, val) -> {
        }));

    assertEquals(2, sensitivityAnalysis.getParameters().size());

    sensitivityAnalysis.clearParameters();

    assertEquals(0, sensitivityAnalysis.getParameters().size(), "Parameters should be cleared");
  }

  @Test
  @DisplayName("Parameter toString provides readable output")
  void testParameterToString() {
    UncertainParameter param = UncertainParameter.triangular("Temperature", 20.0, 25.0, 30.0,
        (proc, val) -> feedStream.setTemperature(val, "C"));

    String str = param.toString();

    assertNotNull(str);
    assertTrue(str.contains("Temperature"), "Should contain parameter name");
    assertTrue(str.contains("TRIANGULAR"), "Should contain distribution type");
  }

  @Test
  @DisplayName("Invalid percentile order throws exception")
  void testInvalidPercentileOrder() {
    assertThrows(IllegalArgumentException.class, () -> {
      // P10 > P50 is invalid
      new UncertainParameter("Invalid", 30.0, 20.0, 40.0, DistributionType.TRIANGULAR, null,
          (proc, val) -> {
          });
    });
  }

  @Test
  @DisplayName("SensitivityConfig builder pattern works")
  void testSensitivityConfigBuilder() {
    SensitivityConfig config = new SensitivityConfig().numberOfTrials(500).parallel(true);

    assertEquals(500, config.getNumberOfTrials());
    assertTrue(config.isParallel());
  }

  @Test
  @DisplayName("SensitivityConfig default values")
  void testSensitivityConfigDefaults() {
    SensitivityConfig config = new SensitivityConfig();

    // Should have reasonable defaults
    assertTrue(config.getNumberOfTrials() > 0, "Should have positive number of trials");
  }

  @Test
  @DisplayName("MonteCarloResult statistics calculation")
  void testMonteCarloResultStatistics() {
    // Create mock trial results
    java.util.List<TrialResult> trials = new java.util.ArrayList<>();

    // Add some trial results with known values
    for (int i = 0; i < 100; i++) {
      Map<String, Double> sampledValues = new java.util.HashMap<>();
      sampledValues.put("TestParam", 50.0 + i);
      trials.add(new TrialResult(sampledValues, 100.0 + i * 2, true, true));
    }

    Map<String, Double> tornadoSensitivities = new java.util.HashMap<>();
    tornadoSensitivities.put("TestParam", 0.5);

    MonteCarloResult result = new MonteCarloResult(trials, tornadoSensitivities, "Output", "units");

    // Check statistics
    assertTrue(result.getP10() < result.getP50(), "P10 should be less than P50");
    assertTrue(result.getP50() < result.getP90(), "P50 should be less than P90");
    assertTrue(result.getMin() <= result.getP10(), "Min should be <= P10");
    assertTrue(result.getMax() >= result.getP90(), "Max should be >= P90");
    assertTrue(result.getMean() > 0, "Mean should be positive");
    assertTrue(result.getStdDev() > 0, "StdDev should be positive");
  }

  @Test
  @DisplayName("MonteCarloResult tornado sensitivities accessible")
  void testMonteCarloResultTornado() {
    java.util.List<TrialResult> trials = new java.util.ArrayList<>();
    Map<String, Double> sampledValues = new java.util.HashMap<>();
    sampledValues.put("Param1", 100.0);
    trials.add(new TrialResult(sampledValues, 200.0, true, true));

    Map<String, Double> tornadoSensitivities = new java.util.LinkedHashMap<>();
    tornadoSensitivities.put("Param1", 0.8);
    tornadoSensitivities.put("Param2", 0.3);

    MonteCarloResult result = new MonteCarloResult(trials, tornadoSensitivities, "Output", "units");

    Map<String, Double> sensitivities = result.getTornadoSensitivities();
    assertNotNull(sensitivities);
    assertEquals(0.8, sensitivities.get("Param1"), 0.001);
    assertEquals(0.3, sensitivities.get("Param2"), 0.001);
  }

  @Test
  @DisplayName("MonteCarloResult toTornadoMarkdown generates valid output")
  void testTornadoMarkdown() {
    java.util.List<TrialResult> trials = new java.util.ArrayList<>();
    Map<String, Double> sampledValues = new java.util.HashMap<>();
    sampledValues.put("Temperature", 25.0);
    trials.add(new TrialResult(sampledValues, 100.0, true, true));

    Map<String, Double> tornadoSensitivities = new java.util.LinkedHashMap<>();
    tornadoSensitivities.put("Temperature", 0.6);

    MonteCarloResult result = new MonteCarloResult(trials, tornadoSensitivities, "Flow", "kg/hr");

    String markdown = result.toTornadoMarkdown();

    assertNotNull(markdown);
    assertTrue(markdown.contains("Tornado"), "Should contain Tornado header");
    assertTrue(markdown.contains("Temperature"), "Should contain parameter name");
  }

  @Test
  @DisplayName("MonteCarloResult getPercentile works for custom percentiles")
  void testCustomPercentile() {
    java.util.List<TrialResult> trials = new java.util.ArrayList<>();
    for (int i = 0; i < 100; i++) {
      Map<String, Double> sampledValues = new java.util.HashMap<>();
      sampledValues.put("TestParam", (double) i);
      trials.add(new TrialResult(sampledValues, (double) i, true, true));
    }

    MonteCarloResult result = new MonteCarloResult(trials, new java.util.HashMap<>(), "Out", "u");

    double p25 = result.getPercentile(0.25);
    double p75 = result.getPercentile(0.75);

    assertTrue(p25 < p75, "P25 should be less than P75");
  }

  @Test
  @DisplayName("TrialResult stores sampled values correctly")
  void testTrialResultSampledValues() {
    Map<String, Double> sampledValues = new java.util.HashMap<>();
    sampledValues.put("Param1", 100.0);
    sampledValues.put("Param2", 200.0);

    TrialResult trial = new TrialResult(sampledValues, 500.0, true, true);

    assertEquals(100.0, trial.getSampledValues().get("Param1"), 0.001);
    assertEquals(200.0, trial.getSampledValues().get("Param2"), 0.001);
    assertEquals(500.0, trial.getOutputValue(), 0.001);
    assertTrue(trial.isFeasible());
    assertTrue(trial.isConverged());
  }

  @Test
  @DisplayName("TrialResult comparison based on output value")
  void testTrialResultComparison() {
    TrialResult low = new TrialResult(new java.util.HashMap<>(), 100.0, true, true);
    TrialResult high = new TrialResult(new java.util.HashMap<>(), 200.0, true, true);

    assertTrue(low.compareTo(high) < 0, "Lower output should compare less");
    assertTrue(high.compareTo(low) > 0, "Higher output should compare greater");
  }

  @Test
  @DisplayName("MonteCarloResult tracks feasibility count")
  void testFeasibilityTracking() {
    java.util.List<TrialResult> trials = new java.util.ArrayList<>();

    // 8 feasible, 2 infeasible
    for (int i = 0; i < 8; i++) {
      trials.add(new TrialResult(new java.util.HashMap<>(), 100.0, true, true));
    }
    for (int i = 0; i < 2; i++) {
      trials.add(new TrialResult(new java.util.HashMap<>(), 50.0, false, true));
    }

    MonteCarloResult result = new MonteCarloResult(trials, new java.util.HashMap<>(), "Out", "u");

    // The result should track these counts
    List<TrialResult> allTrials = result.getTrials();
    assertEquals(10, allTrials.size());

    long feasibleCount = allTrials.stream().filter(TrialResult::isFeasible).count();
    assertEquals(8, feasibleCount);
  }
}
