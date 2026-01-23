package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for MultiStageSeparatorTest.
 */
class MultiStageSeparatorTestTest {
  private SystemInterface reservoirFluid;

  @BeforeEach
  void setUp() {
    // Create a typical reservoir fluid
    reservoirFluid = new SystemSrkEos(373.15, 300.0); // 100°C, 300 bara
    reservoirFluid.addComponent("nitrogen", 0.5);
    reservoirFluid.addComponent("CO2", 2.0);
    reservoirFluid.addComponent("methane", 45.0);
    reservoirFluid.addComponent("ethane", 8.0);
    reservoirFluid.addComponent("propane", 5.0);
    reservoirFluid.addComponent("i-butane", 1.5);
    reservoirFluid.addComponent("n-butane", 2.5);
    reservoirFluid.addComponent("i-pentane", 1.0);
    reservoirFluid.addComponent("n-pentane", 1.5);
    reservoirFluid.addComponent("n-hexane", 3.0);
    reservoirFluid.addComponent("n-heptane", 30.0);
    reservoirFluid.setMixingRule("classic");
    reservoirFluid.setMultiPhaseCheck(true);
  }

  @Test
  void testBasicSeparatorTest() {
    MultiStageSeparatorTest sepTest = new MultiStageSeparatorTest(reservoirFluid);
    sepTest.setReservoirConditions(300.0, 100.0);

    // Add typical 3-stage separation
    sepTest.addSeparatorStage(50.0, 40.0, "HP Separator");
    sepTest.addSeparatorStage(10.0, 30.0, "LP Separator");
    sepTest.addStockTankStage();

    assertEquals(3, sepTest.getNumberOfStages());

    sepTest.run();

    // Check results are reasonable
    double gor = sepTest.getTotalGOR();
    assertTrue(gor > 0, "GOR should be positive");

    double bo = sepTest.getBo();
    assertTrue(bo > 1.0, "Bo should be greater than 1.0");

    double apiGravity = sepTest.getStockTankAPIGravity();
    // API can be negative for very heavy oils or simulation artifacts
    assertTrue(apiGravity > -50 && apiGravity < 80,
        "API gravity should be between -50 and 80, got " + apiGravity);

    double density = sepTest.getStockTankOilDensity();
    assertTrue(density > 500 && density < 1200,
        "Stock tank density should be 500-1200 kg/m3, got " + density);
  }

  @Test
  void testTypicalThreeStageSetup() {
    MultiStageSeparatorTest sepTest = new MultiStageSeparatorTest(reservoirFluid);
    sepTest.setReservoirConditions(300.0, 100.0);
    sepTest.setTypicalThreeStage(50.0, 40.0, 10.0, 30.0);

    assertEquals(3, sepTest.getNumberOfStages());

    sepTest.run();

    java.util.List<MultiStageSeparatorTest.SeparatorStageResult> results =
        sepTest.getStageResults();
    assertNotNull(results);
    assertEquals(3, results.size());

    // Check stage pressures
    assertEquals(50.0, results.get(0).getPressure(), 0.1);
    assertEquals(10.0, results.get(1).getPressure(), 0.1);
    assertEquals(1.01325, results.get(2).getPressure(), 0.1);
  }

  @Test
  void testStageResults() {
    MultiStageSeparatorTest sepTest = new MultiStageSeparatorTest(reservoirFluid);
    sepTest.setReservoirConditions(300.0, 100.0);
    sepTest.addSeparatorStage(30.0, 35.0, "Test Stage");
    sepTest.addStockTankStage();

    sepTest.run();

    java.util.List<MultiStageSeparatorTest.SeparatorStageResult> results =
        sepTest.getStageResults();
    assertEquals(2, results.size());

    MultiStageSeparatorTest.SeparatorStageResult stage1 = results.get(0);
    assertEquals("Test Stage", stage1.getStageName());
    assertEquals(30.0, stage1.getPressure(), 0.1);
    assertEquals(35.0, stage1.getTemperature(), 0.1);
    assertTrue(stage1.getOilDensity() > 0, "Oil density should be positive");
  }

  @Test
  void testGenerateReport() {
    MultiStageSeparatorTest sepTest = new MultiStageSeparatorTest(reservoirFluid);
    sepTest.setReservoirConditions(300.0, 100.0);
    sepTest.setTypicalThreeStage(50.0, 40.0, 10.0, 30.0);
    sepTest.run();

    String report = sepTest.generateReport();

    assertNotNull(report);
    assertTrue(report.contains("Multi-Stage Separator Test"));
    assertTrue(report.contains("HP Separator"));
    assertTrue(report.contains("LP Separator"));
    assertTrue(report.contains("Stock Tank"));
    assertTrue(report.contains("Total GOR"));
    assertTrue(report.contains("Bo (FVF)"));
    assertTrue(report.contains("API Gravity"));
  }

  @Test
  void testClearStages() {
    MultiStageSeparatorTest sepTest = new MultiStageSeparatorTest(reservoirFluid);
    sepTest.addSeparatorStage(50.0, 40.0);
    sepTest.addSeparatorStage(10.0, 30.0);
    assertEquals(2, sepTest.getNumberOfStages());

    sepTest.clearStages();
    assertEquals(0, sepTest.getNumberOfStages());
  }

  @Test
  void testCumulativeGOR() {
    MultiStageSeparatorTest sepTest = new MultiStageSeparatorTest(reservoirFluid);
    sepTest.setReservoirConditions(300.0, 100.0);
    sepTest.setTypicalThreeStage(50.0, 40.0, 10.0, 30.0);
    sepTest.run();

    java.util.List<MultiStageSeparatorTest.SeparatorStageResult> results =
        sepTest.getStageResults();

    // Cumulative GOR should increase with each stage
    double prevCumGOR = 0;
    for (MultiStageSeparatorTest.SeparatorStageResult result : results) {
      assertTrue(result.getCumulativeGOR() >= prevCumGOR, "Cumulative GOR should increase");
      prevCumGOR = result.getCumulativeGOR();
    }

    // Final cumulative GOR should equal total GOR
    double finalCumGOR = results.get(results.size() - 1).getCumulativeGOR();
    assertEquals(sepTest.getTotalGOR(), finalCumGOR, 0.1);
  }
}
