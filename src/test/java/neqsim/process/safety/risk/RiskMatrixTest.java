package neqsim.process.safety.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for RiskMatrix.
 */
class RiskMatrixTest {

  private ProcessSystem processSystem;

  @BeforeEach
  void setUp() {
    // Create test process with various equipment
    SystemInterface fluid = new SystemSrkEos(280.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    processSystem = new ProcessSystem();

    Stream feed = new Stream("Well Feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    processSystem.add(feed);

    Separator separator = new Separator("Inlet Separator", feed);
    processSystem.add(separator);

    Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(100.0, "bara");
    processSystem.add(compressor);

    Cooler cooler = new Cooler("Export Cooler", compressor.getOutletStream());
    cooler.setOutTemperature(35.0, "C");
    processSystem.add(cooler);

    Stream exportStream = new Stream("Export Gas", cooler.getOutletStream());
    processSystem.add(exportStream);

    processSystem.run();
  }

  @Test
  void testBuildRiskMatrix() {
    RiskMatrix matrix = new RiskMatrix(processSystem);
    matrix.setFeedStreamName("Well Feed");
    matrix.setProductStreamName("Export Gas");
    matrix.setProductPrice(500.0, "USD/tonne");
    matrix.setDowntimeCostPerHour(5000.0);

    matrix.buildRiskMatrix();

    // Should have assessments for non-stream equipment
    assertTrue(matrix.getRiskAssessments().size() > 0);
  }

  @Test
  void testRiskAssessment() {
    RiskMatrix matrix = new RiskMatrix(processSystem);
    matrix.setFeedStreamName("Well Feed");
    matrix.setProductStreamName("Export Gas");
    matrix.buildRiskMatrix();

    RiskMatrix.RiskAssessment compressorRisk = matrix.getRiskAssessment("Export Compressor");

    assertNotNull(compressorRisk);
    assertTrue(compressorRisk.getRiskScore() > 0);
    assertNotNull(compressorRisk.getRiskLevel());
    assertTrue(compressorRisk.getAnnualRiskCost() >= 0);
  }

  @Test
  void testCustomEquipmentRisk() {
    RiskMatrix matrix = new RiskMatrix(processSystem);
    matrix.setFeedStreamName("Well Feed");
    matrix.setProductStreamName("Export Gas");

    // Add custom risk data (high failure rate)
    matrix.addEquipmentRisk("Export Compressor", 2.0, 72.0); // 2 failures/year, 72hr MTTR

    matrix.buildRiskMatrix();

    RiskMatrix.RiskAssessment assessment = matrix.getRiskAssessment("Export Compressor");
    assertEquals(2.0, assessment.getFailuresPerYear(), 0.01);
    assertEquals(72.0, assessment.getMttr(), 0.01);
  }

  @Test
  void testProbabilityCategories() {
    assertEquals(RiskMatrix.ProbabilityCategory.VERY_LOW,
        RiskMatrix.ProbabilityCategory.fromFrequency(0.05));
    assertEquals(RiskMatrix.ProbabilityCategory.LOW,
        RiskMatrix.ProbabilityCategory.fromFrequency(0.3));
    assertEquals(RiskMatrix.ProbabilityCategory.MEDIUM,
        RiskMatrix.ProbabilityCategory.fromFrequency(0.7));
    assertEquals(RiskMatrix.ProbabilityCategory.HIGH,
        RiskMatrix.ProbabilityCategory.fromFrequency(1.5));
    assertEquals(RiskMatrix.ProbabilityCategory.VERY_HIGH,
        RiskMatrix.ProbabilityCategory.fromFrequency(3.0));
  }

  @Test
  void testConsequenceCategories() {
    assertEquals(RiskMatrix.ConsequenceCategory.NEGLIGIBLE,
        RiskMatrix.ConsequenceCategory.fromProductionLoss(3.0));
    assertEquals(RiskMatrix.ConsequenceCategory.MINOR,
        RiskMatrix.ConsequenceCategory.fromProductionLoss(15.0));
    assertEquals(RiskMatrix.ConsequenceCategory.MODERATE,
        RiskMatrix.ConsequenceCategory.fromProductionLoss(35.0));
    assertEquals(RiskMatrix.ConsequenceCategory.MAJOR,
        RiskMatrix.ConsequenceCategory.fromProductionLoss(65.0));
    assertEquals(RiskMatrix.ConsequenceCategory.CATASTROPHIC,
        RiskMatrix.ConsequenceCategory.fromProductionLoss(90.0));
  }

  @Test
  void testRiskLevels() {
    assertEquals(RiskMatrix.RiskLevel.LOW, RiskMatrix.RiskLevel.fromScore(2));
    assertEquals(RiskMatrix.RiskLevel.MEDIUM, RiskMatrix.RiskLevel.fromScore(6));
    assertEquals(RiskMatrix.RiskLevel.HIGH, RiskMatrix.RiskLevel.fromScore(12));
    assertEquals(RiskMatrix.RiskLevel.CRITICAL, RiskMatrix.RiskLevel.fromScore(20));
  }

  @Test
  void testGetEquipmentByRiskLevel() {
    RiskMatrix matrix = new RiskMatrix(processSystem);
    matrix.setFeedStreamName("Well Feed");
    matrix.setProductStreamName("Export Gas");
    matrix.buildRiskMatrix();

    // Test that we can filter by risk level
    java.util.List<String> critical = matrix.getEquipmentByRiskLevel(RiskMatrix.RiskLevel.CRITICAL);
    assertNotNull(critical);
    // List may be empty if no critical risks
  }

  @Test
  void testSortedAssessments() {
    RiskMatrix matrix = new RiskMatrix(processSystem);
    matrix.setFeedStreamName("Well Feed");
    matrix.setProductStreamName("Export Gas");
    matrix.buildRiskMatrix();

    java.util.List<RiskMatrix.RiskAssessment> byRisk = matrix.getRiskAssessmentsSortedByRisk();
    java.util.List<RiskMatrix.RiskAssessment> byCost = matrix.getRiskAssessmentsSortedByCost();

    assertNotNull(byRisk);
    assertNotNull(byCost);

    // Verify sorted by risk (descending)
    if (byRisk.size() > 1) {
      assertTrue(byRisk.get(0).getRiskScore() >= byRisk.get(byRisk.size() - 1).getRiskScore());
    }
  }

  @Test
  void testMatrixData() {
    RiskMatrix matrix = new RiskMatrix(processSystem);
    matrix.setFeedStreamName("Well Feed");
    matrix.setProductStreamName("Export Gas");
    matrix.buildRiskMatrix();

    java.util.List<java.util.Map<String, Object>> matrixData = matrix.getMatrixData();

    assertNotNull(matrixData);
    for (java.util.Map<String, Object> point : matrixData) {
      assertNotNull(point.get("name"));
      assertNotNull(point.get("x"));
      assertNotNull(point.get("y"));
      assertNotNull(point.get("color"));
    }
  }

  @Test
  void testToJson() {
    RiskMatrix matrix = new RiskMatrix(processSystem);
    matrix.setFeedStreamName("Well Feed");
    matrix.setProductStreamName("Export Gas");
    matrix.setProductPrice(500.0, "USD/tonne");
    matrix.buildRiskMatrix();

    String json = matrix.toJson();

    assertNotNull(json);
    assertTrue(json.contains("summary"));
    assertTrue(json.contains("equipment"));
    assertTrue(json.contains("matrixData"));
    assertTrue(json.contains("categories"));
  }

  @Test
  void testTotalAnnualRiskCost() {
    RiskMatrix matrix = new RiskMatrix(processSystem);
    matrix.setFeedStreamName("Well Feed");
    matrix.setProductStreamName("Export Gas");
    matrix.setProductPrice(500.0, "USD/tonne");
    matrix.setDowntimeCostPerHour(10000.0);
    matrix.buildRiskMatrix();

    double totalCost = matrix.getTotalAnnualRiskCost();

    // Total cost should be sum of individual costs
    double sumCosts = 0;
    for (RiskMatrix.RiskAssessment a : matrix.getRiskAssessments().values()) {
      sumCosts += a.getAnnualRiskCost();
    }
    assertEquals(sumCosts, totalCost, 0.01);
  }
}
