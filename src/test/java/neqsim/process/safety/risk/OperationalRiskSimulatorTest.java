package neqsim.process.safety.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for OperationalRiskSimulator.
 */
class OperationalRiskSimulatorTest {

  private ProcessSystem processSystem;
  private Stream exportStream;

  @BeforeEach
  void setUp() {
    // Create test process
    SystemInterface fluid = new SystemSrkEos(280.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    processSystem = new ProcessSystem();

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
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

    exportStream = new Stream("Export Gas", cooler.getOutletStream());
    processSystem.add(exportStream);

    processSystem.run();
  }

  @Test
  void testBasicSimulation() {
    OperationalRiskSimulator simulator = new OperationalRiskSimulator(processSystem);
    simulator.setFeedStreamName("Feed");
    simulator.setProductStreamName("Export Gas");
    simulator.setRandomSeed(12345); // For reproducibility

    // Add equipment with failure rates
    simulator.addEquipmentReliability("Export Compressor", 0.05, 24.0); // 5% failures/yr, 24hr MTTR

    OperationalRiskResult result = simulator.runSimulation(100, 365.0);

    assertNotNull(result);
    assertEquals(100, result.getIterations());
    assertEquals(365.0, result.getTimeHorizonDays(), 0.01);
    assertTrue(result.getMeanAvailability() > 0);
    assertTrue(result.getMeanAvailability() <= 100);
  }

  @Test
  void testMultipleEquipmentSimulation() {
    OperationalRiskSimulator simulator = new OperationalRiskSimulator(processSystem);
    simulator.setFeedStreamName("Feed");
    simulator.setProductStreamName("Export Gas");
    simulator.setRandomSeed(12345);

    simulator.addEquipmentReliability("Export Compressor", 0.05, 24.0);
    simulator.addEquipmentReliability("Export Cooler", 0.02, 8.0);
    simulator.addEquipmentReliability("Inlet Separator", 0.01, 48.0);

    OperationalRiskResult result = simulator.runSimulation(100, 365.0);

    assertNotNull(result);
    // With multiple equipment, availability should be somewhat lower
    assertTrue(result.getMeanAvailability() < 100);
  }

  @Test
  void testAddEquipmentMtbf() {
    OperationalRiskSimulator simulator = new OperationalRiskSimulator(processSystem);

    // 25000 hours MTBF = ~2.85 failures per year
    simulator.addEquipmentMtbf("Export Compressor", 25000, 72);

    java.util.Map<String, OperationalRiskSimulator.EquipmentReliability> reliability =
        simulator.getEquipmentReliability();

    assertNotNull(reliability.get("Export Compressor"));
    assertEquals(25000, reliability.get("Export Compressor").getMtbf(), 0.01);
    assertEquals(72, reliability.get("Export Compressor").getMttr(), 0.01);
  }

  @Test
  void testEquipmentAvailabilityCalculation() {
    OperationalRiskSimulator.EquipmentReliability rel =
        new OperationalRiskSimulator.EquipmentReliability("Test", 1.0, 24.0);

    // MTBF = 8760 hours (1 failure/year), MTTR = 24 hours
    // Availability = 8760 / (8760 + 24) = 0.9973
    double expectedAvailability = 8760.0 / (8760.0 + 24.0);
    assertEquals(expectedAvailability, rel.getAvailability(), 0.001);
  }

  @Test
  void testProductionStatistics() {
    OperationalRiskSimulator simulator = new OperationalRiskSimulator(processSystem);
    simulator.setFeedStreamName("Feed");
    simulator.setProductStreamName("Export Gas");
    simulator.setRandomSeed(42);

    simulator.addEquipmentReliability("Export Compressor", 0.10, 48.0); // Higher failure rate

    OperationalRiskResult result = simulator.runSimulation(500, 30.0);

    assertNotNull(result);
    // Check production statistics are reasonable
    assertTrue(result.getP10Production() <= result.getP50Production());
    assertTrue(result.getP50Production() <= result.getP90Production());
    assertTrue(result.getMinProduction() <= result.getMeanProduction());
    assertTrue(result.getMeanProduction() <= result.getMaxProduction());
  }

  @Test
  void testResultToJson() {
    OperationalRiskSimulator simulator = new OperationalRiskSimulator(processSystem);
    simulator.setFeedStreamName("Feed");
    simulator.setProductStreamName("Export Gas");
    simulator.setRandomSeed(12345);
    simulator.addEquipmentReliability("Export Compressor", 0.05, 24.0);

    OperationalRiskResult result = simulator.runSimulation(50, 30.0);

    String json = result.toJson();
    assertNotNull(json);
    assertTrue(json.contains("productionStatistics"));
    assertTrue(json.contains("availabilityStatistics"));
    assertTrue(json.contains("failureStatistics"));
  }

  @Test
  void testProductionForecast() {
    OperationalRiskSimulator simulator = new OperationalRiskSimulator(processSystem);
    simulator.setFeedStreamName("Feed");
    simulator.setProductStreamName("Export Gas");
    simulator.setRandomSeed(42);
    simulator.addEquipmentReliability("Export Compressor", 0.05, 24.0);

    // Generate 7-day forecast
    OperationalRiskSimulator.ProductionForecast forecast = simulator.generateForecast(7, 50);

    assertNotNull(forecast);
    assertEquals(7, forecast.getDays());
    assertEquals(7, forecast.getPoints().size());

    // Check that cumulative production increases over time
    OperationalRiskSimulator.ForecastPoint day1 = forecast.getPoint(1);
    OperationalRiskSimulator.ForecastPoint day7 = forecast.getPoint(7);
    assertTrue(day7.mean > day1.mean);
  }
}
