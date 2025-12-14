package neqsim.pvtsimulation.regression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the PVT Regression Framework.
 */
public class PVTRegressionTest {
  private SystemInterface testFluid;

  @BeforeEach
  public void setUp() {
    // Create a simple test fluid
    testFluid = new SystemSrkEos(373.15, 200.0);
    testFluid.addComponent("methane", 0.70);
    testFluid.addComponent("ethane", 0.10);
    testFluid.addComponent("propane", 0.05);
    testFluid.addComponent("n-pentane", 0.05);
    testFluid.addPlusFraction("C7+", 0.10, 0.150, 0.82);
    testFluid.setMixingRule(2);
    testFluid.init(0);
  }

  @Test
  public void testPVTRegressionCreation() {
    PVTRegression regression = new PVTRegression(testFluid);
    assertNotNull(regression);
    assertNotNull(regression.getBaseFluid());
  }

  @Test
  public void testAddCCEData() {
    PVTRegression regression = new PVTRegression(testFluid);

    double[] pressures = {300.0, 250.0, 200.0, 150.0, 100.0};
    double[] relativeVolumes = {0.985, 1.000, 1.050, 1.150, 1.350};
    double temperature = 373.15;

    regression.addCCEData(pressures, relativeVolumes, temperature);

    assertEquals(5, regression.getCCEData().size());
    assertEquals(300.0, regression.getCCEData().get(0).getPressure(), 1e-6);
    assertEquals(0.985, regression.getCCEData().get(0).getRelativeVolume(), 1e-6);
  }

  @Test
  public void testAddCVDData() {
    PVTRegression regression = new PVTRegression(testFluid);

    double[] pressures = {250.0, 200.0, 150.0, 100.0};
    double[] liquidDropout = {0.0, 5.0, 12.0, 8.0};
    double[] zFactors = {0.85, 0.88, 0.91, 0.94};
    double temperature = 373.15;

    regression.addCVDData(pressures, liquidDropout, zFactors, temperature);

    assertEquals(4, regression.getCVDData().size());
    assertEquals(5.0, regression.getCVDData().get(1).getLiquidDropout(), 1e-6);
  }

  @Test
  public void testAddDLEData() {
    PVTRegression regression = new PVTRegression(testFluid);

    double[] pressures = {250.0, 200.0, 150.0, 100.0};
    double[] rs = {150.0, 120.0, 85.0, 50.0};
    double[] bo = {1.45, 1.38, 1.30, 1.20};
    double[] oilDensity = {720.0, 740.0, 760.0, 780.0};
    double temperature = 373.15;

    regression.addDLEData(pressures, rs, bo, oilDensity, temperature);

    assertEquals(4, regression.getDLEData().size());
    assertEquals(150.0, regression.getDLEData().get(0).getRs(), 1e-6);
  }

  @Test
  public void testAddSeparatorData() {
    PVTRegression regression = new PVTRegression(testFluid);

    regression.addSeparatorData(100.0, 1.35, 35.0, 10.0, 288.15, 373.15);

    assertEquals(1, regression.getSeparatorData().size());
    assertEquals(100.0, regression.getSeparatorData().get(0).getGor(), 1e-6);
  }

  @Test
  public void testAddRegressionParameter() {
    PVTRegression regression = new PVTRegression(testFluid);

    regression.addRegressionParameter(RegressionParameter.BIP_METHANE_C7PLUS, 0.0, 0.10, 0.03);

    // Add some data to avoid exception
    regression.addCCEData(new double[] {200.0}, new double[] {1.0}, 373.15);

    // Should not throw
    assertNotNull(regression);
  }

  @Test
  public void testAddRegressionParameterWithDefaults() {
    PVTRegression regression = new PVTRegression(testFluid);

    regression.addRegressionParameter(RegressionParameter.VOLUME_SHIFT_C7PLUS);

    // Add some data
    regression.addCCEData(new double[] {200.0}, new double[] {1.0}, 373.15);

    assertNotNull(regression);
  }

  @Test
  public void testSetExperimentWeight() {
    PVTRegression regression = new PVTRegression(testFluid);
    regression.setExperimentWeight(ExperimentType.DLE, 2.0);
    // No assertion needed - just verify no exception
  }

  @Test
  public void testRegressionWithNoData() {
    PVTRegression regression = new PVTRegression(testFluid);
    regression.addRegressionParameter(RegressionParameter.BIP_METHANE_C7PLUS);

    assertThrows(IllegalStateException.class, () -> {
      regression.runRegression();
    });
  }

  @Test
  public void testRegressionWithNoParameters() {
    PVTRegression regression = new PVTRegression(testFluid);
    regression.addCCEData(new double[] {200.0}, new double[] {1.0}, 373.15);

    assertThrows(IllegalStateException.class, () -> {
      regression.runRegression();
    });
  }

  @Test
  public void testRegressionParameterDefaultBounds() {
    double[] bounds = RegressionParameter.BIP_METHANE_C7PLUS.getDefaultBounds();

    assertEquals(3, bounds.length);
    assertEquals(0.0, bounds[0], 1e-6); // lower bound
    assertEquals(0.10, bounds[1], 1e-6); // upper bound
    assertEquals(0.03, bounds[2], 1e-6); // initial guess
  }

  @Test
  public void testCCEDataPoint() {
    CCEDataPoint point = new CCEDataPoint(200.0, 1.05, 373.15);

    assertEquals(200.0, point.getPressure(), 1e-6);
    assertEquals(1.05, point.getRelativeVolume(), 1e-6);
    assertEquals(373.15, point.getTemperature(), 1e-6);
    assertTrue(Double.isNaN(point.getYFactor()));

    point.setYFactor(1.02);
    assertEquals(1.02, point.getYFactor(), 1e-6);
  }

  @Test
  public void testDLEDataPoint() {
    DLEDataPoint point = new DLEDataPoint(200.0, 120.0, 1.38, 740.0, 373.15);

    assertEquals(200.0, point.getPressure(), 1e-6);
    assertEquals(120.0, point.getRs(), 1e-6);
    assertEquals(1.38, point.getBo(), 1e-6);
    assertEquals(740.0, point.getOilDensity(), 1e-6);
    assertEquals(373.15, point.getTemperature(), 1e-6);

    point.setGasGravity(0.85);
    assertEquals(0.85, point.getGasGravity(), 1e-6);
  }

  @Test
  public void testCVDDataPoint() {
    CVDDataPoint point = new CVDDataPoint(200.0, 10.0, 0.88, 373.15);

    assertEquals(200.0, point.getPressure(), 1e-6);
    assertEquals(10.0, point.getLiquidDropout(), 1e-6);
    assertEquals(0.88, point.getZFactor(), 1e-6);
    assertEquals(373.15, point.getTemperature(), 1e-6);
  }

  @Test
  public void testSeparatorDataPoint() {
    SeparatorDataPoint point = new SeparatorDataPoint(100.0, 1.35, 35.0, 10.0, 288.15, 373.15);

    assertEquals(100.0, point.getGor(), 1e-6);
    assertEquals(1.35, point.getBo(), 1e-6);
    assertEquals(35.0, point.getApiGravity(), 1e-6);
    assertEquals(10.0, point.getSeparatorPressure(), 1e-6);
    assertEquals(288.15, point.getSeparatorTemperature(), 1e-6);
    assertEquals(373.15, point.getReservoirTemperature(), 1e-6);
  }

  @Test
  public void testRegressionParameterConfig() {
    RegressionParameterConfig config =
        new RegressionParameterConfig(RegressionParameter.BIP_METHANE_C7PLUS, 0.0, 0.10, 0.03);

    assertEquals(RegressionParameter.BIP_METHANE_C7PLUS, config.getParameter());
    assertEquals(0.0, config.getLowerBound(), 1e-6);
    assertEquals(0.10, config.getUpperBound(), 1e-6);
    assertEquals(0.03, config.getInitialGuess(), 1e-6);
    assertTrue(Double.isNaN(config.getOptimizedValue()));

    config.setOptimizedValue(0.045);
    assertEquals(0.045, config.getOptimizedValue(), 1e-6);
  }

  @Test
  public void testUncertaintyAnalysis() {
    double[] paramValues = {0.045, 1.02};
    double[] stdErrors = {0.005, 0.01};
    double[][] corrMatrix = {{1.0, 0.2}, {0.2, 1.0}};
    double[] ci95 = {0.01, 0.02};

    UncertaintyAnalysis uncertainty =
        new UncertaintyAnalysis(paramValues, stdErrors, corrMatrix, ci95, 10, 0.001);

    assertEquals(0.045, uncertainty.getParameterValue(0), 1e-6);
    assertEquals(0.005, uncertainty.getStandardError(0), 1e-6);
    assertEquals(0.2, uncertainty.getCorrelation(0, 1), 1e-6);
    assertEquals(0.01, uncertainty.getConfidenceInterval95(0), 1e-6);
    assertEquals(10, uncertainty.getDegreesOfFreedom());
    assertEquals(0.001, uncertainty.getResidualVariance(), 1e-6);

    double[] bounds = uncertainty.getConfidenceIntervalBounds(0);
    assertEquals(0.035, bounds[0], 1e-6);
    assertEquals(0.055, bounds[1], 1e-6);

    // Low correlation matrix - should return false
    assertTrue(!uncertainty.hasHighCorrelations());

    String summary = uncertainty.generateSummary();
    assertTrue(summary.contains("Uncertainty Analysis"));
  }

  @Test
  public void testClearData() {
    PVTRegression regression = new PVTRegression(testFluid);
    regression.addCCEData(new double[] {200.0}, new double[] {1.0}, 373.15);
    regression.addDLEData(new double[] {200.0}, new double[] {100.0}, new double[] {1.3},
        new double[] {750.0}, 373.15);

    assertEquals(1, regression.getCCEData().size());
    assertEquals(1, regression.getDLEData().size());

    regression.clearData();

    assertEquals(0, regression.getCCEData().size());
    assertEquals(0, regression.getDLEData().size());
  }

  @Test
  public void testExperimentTypeEnum() {
    assertEquals(7, ExperimentType.values().length);
    assertEquals(ExperimentType.CCE, ExperimentType.valueOf("CCE"));
    assertEquals(ExperimentType.CVD, ExperimentType.valueOf("CVD"));
    assertEquals(ExperimentType.DLE, ExperimentType.valueOf("DLE"));
    assertEquals(ExperimentType.SEPARATOR, ExperimentType.valueOf("SEPARATOR"));
  }

  @Test
  public void testRegressionResultMethods() {
    // Test RegressionResult methods without running full regression
    // Create a simple fluid for testing
    SystemInterface fluid = new SystemSrkEos(373.15, 200.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule(2);
    fluid.init(0);

    java.util.Map<ExperimentType, Double> objectiveValues =
        new java.util.EnumMap<>(ExperimentType.class);
    objectiveValues.put(ExperimentType.CCE, 0.001);

    java.util.List<RegressionParameterConfig> paramConfigs = new java.util.ArrayList<>();
    RegressionParameterConfig config =
        new RegressionParameterConfig(RegressionParameter.BIP_METHANE_C7PLUS, 0.0, 0.10, 0.03);
    config.setOptimizedValue(0.045);
    paramConfigs.add(config);

    double[] paramValues = {0.045};
    double[] stdErrors = {0.005};
    double[][] corrMatrix = {{1.0}};
    double[] ci95 = {0.01};

    UncertaintyAnalysis uncertainty =
        new UncertaintyAnalysis(paramValues, stdErrors, corrMatrix, ci95, 10, 0.001);

    RegressionResult result =
        new RegressionResult(fluid, objectiveValues, paramConfigs, uncertainty, paramValues, 0.001);

    // Test result methods
    assertNotNull(result.getTunedFluid());
    assertEquals(0.001, result.getObjectiveValue(ExperimentType.CCE), 1e-6);
    assertEquals(0.045, result.getOptimizedValue(RegressionParameter.BIP_METHANE_C7PLUS), 1e-6);
    assertNotNull(result.getUncertainty());
    assertEquals(0.001, result.getFinalChiSquare(), 1e-6);

    String summary = result.generateSummary();
    assertTrue(summary.contains("PVT Regression Results"));
    assertTrue(summary.contains("CCE"));
    assertTrue(summary.contains("BIP_METHANE_C7PLUS"));
  }
}
