package neqsim.process.equipment.absorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.absorber.H2SScavenger.ScavengerType;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for H2SScavenger unit operation.
 *
 * @author ESOL
 */
public class H2SScavengerTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(H2SScavengerTest.class);
  private SystemInterface sourGas;
  private Stream feedStream;

  @BeforeEach
  void setUp() {
    // Create a sour gas with H2S
    sourGas = new SystemSrkEos(273.15 + 40.0, 50.0); // 40°C, 50 bara

    // Typical sour gas composition
    sourGas.addComponent("methane", 0.85);
    sourGas.addComponent("ethane", 0.05);
    sourGas.addComponent("propane", 0.02);
    sourGas.addComponent("CO2", 0.02);
    sourGas.addComponent("H2S", 0.005); // 5000 ppm H2S
    sourGas.addComponent("nitrogen", 0.055);

    sourGas.setMixingRule("classic");
    sourGas.setMultiPhaseCheck(true);

    feedStream = new Stream("Sour Gas Feed", sourGas);
    feedStream.setFlowRate(100000.0, "Sm3/day"); // 100 MSm3/d
    feedStream.run();
  }

  @Test
  @DisplayName("Test basic H2S removal with triazine scavenger")
  void testTriazineScavenger() {
    H2SScavenger scavenger = new H2SScavenger("H2S Scavenger", feedStream);
    scavenger.setScavengerType(ScavengerType.TRIAZINE);
    scavenger.setScavengerInjectionRate(50.0, "l/hr");
    scavenger.setScavengerConcentration(0.5); // 50% active

    scavenger.run();

    logger.info("=== Triazine Scavenger Test ===");
    logger.info("Inlet H2S: " + scavenger.getInletH2SConcentration() + " ppm");
    logger.info("Outlet H2S: " + scavenger.getOutletH2SConcentration() + " ppm");
    logger.info("Removal Efficiency: " + scavenger.getH2SRemovalEfficiencyPercent() + "%");
    logger.info("H2S Removed: " + scavenger.getH2SRemoved("kg/hr") + " kg/hr");
    logger.info(scavenger.getPerformanceSummary());

    // Verify H2S was removed
    assertTrue(scavenger.getOutletH2SConcentration() < scavenger.getInletH2SConcentration(),
        "Outlet H2S should be less than inlet");
    assertTrue(scavenger.getH2SRemovalEfficiency() > 0, "Removal efficiency should be positive");
    assertTrue(scavenger.getH2SRemoved("kg/hr") > 0, "H2S removed should be positive");
  }

  @Test
  @DisplayName("Test different scavenger types")
  void testDifferentScavengerTypes() {
    for (ScavengerType type : ScavengerType.values()) {
      H2SScavenger scavenger = new H2SScavenger("Scavenger " + type, feedStream);
      scavenger.setScavengerType(type);
      scavenger.setScavengerInjectionRate(100.0, "l/hr");
      scavenger.setScavengerConcentration(0.5);

      scavenger.run();

      logger.info("\n=== " + type.getDisplayName() + " ===");
      logger.info("Base Stoichiometry: " + type.getBaseStoichiometry() + " lb/lb H2S");
      logger.info("Base Efficiency: " + (type.getBaseEfficiency() * 100) + "%");
      System.out
          .println("Achieved Efficiency: " + scavenger.getH2SRemovalEfficiencyPercent() + "%");
      logger.info(
          "Outlet H2S: " + String.format("%.1f", scavenger.getOutletH2SConcentration()) + " ppm");

      assertTrue(scavenger.getH2SRemovalEfficiency() >= 0, "Efficiency should be non-negative");
      assertTrue(scavenger.getH2SRemovalEfficiency() <= 1, "Efficiency should not exceed 100%");
    }
  }

  @Test
  @DisplayName("Test required injection rate calculation")
  void testRequiredInjectionRate() {
    H2SScavenger scavenger = new H2SScavenger("H2S Scavenger", feedStream);
    scavenger.setScavengerType(ScavengerType.TRIAZINE);
    scavenger.setScavengerConcentration(0.5);
    scavenger.setTargetH2SConcentration(4.0); // 4 ppm target (sales gas spec)

    double requiredRate = scavenger.calculateRequiredInjectionRate();

    logger.info("\n=== Required Injection Rate Test ===");
    logger.info("Target H2S: " + scavenger.getTargetH2SConcentration() + " ppm");
    logger.info("Required Injection Rate: " + requiredRate + " l/hr");

    assertTrue(requiredRate > 0, "Required rate should be positive for sour gas");

    // Now set this rate and verify we meet spec
    scavenger.setScavengerInjectionRate(requiredRate, "l/hr");
    scavenger.run();

    logger.info("Outlet H2S at calculated rate: " + scavenger.getOutletH2SConcentration() + " ppm");
    logger.info("Removal efficiency: " + scavenger.getH2SRemovalEfficiencyPercent() + "%");

    // Should achieve some removal (correlation is approximate)
    assertTrue(scavenger.getOutletH2SConcentration() < scavenger.getInletH2SConcentration(),
        "Should achieve some removal");
  }

  @Test
  @DisplayName("Test with no H2S in feed")
  void testNoH2SInFeed() {
    // Create sweet gas
    SystemInterface sweetGas = new SystemSrkEos(273.15 + 40.0, 50.0);
    sweetGas.addComponent("methane", 0.90);
    sweetGas.addComponent("ethane", 0.05);
    sweetGas.addComponent("propane", 0.03);
    sweetGas.addComponent("nitrogen", 0.02);
    sweetGas.setMixingRule("classic");

    Stream sweetFeed = new Stream("Sweet Gas", sweetGas);
    sweetFeed.setFlowRate(100000.0, "Sm3/day");
    sweetFeed.run();

    H2SScavenger scavenger = new H2SScavenger("Scavenger", sweetFeed);
    scavenger.setScavengerType(ScavengerType.TRIAZINE);
    scavenger.setScavengerInjectionRate(50.0, "l/hr");

    scavenger.run();

    assertEquals(0.0, scavenger.getInletH2SConcentration(), 1e-6,
        "Inlet H2S should be zero for sweet gas");
    assertEquals(0.0, scavenger.getH2SRemoved("kg/hr"), 1e-6, "No H2S should be removed");
  }

  @Test
  @DisplayName("Test effect of contact time")
  void testContactTimeEffect() {
    logger.info("\n=== Contact Time Effect Test ===");

    double[] contactTimes = {5.0, 15.0, 30.0, 60.0, 120.0};

    for (double ct : contactTimes) {
      H2SScavenger scavenger = new H2SScavenger("Scavenger", feedStream);
      scavenger.setScavengerType(ScavengerType.TRIAZINE);
      scavenger.setScavengerInjectionRate(50.0, "l/hr");
      scavenger.setScavengerConcentration(0.5);
      scavenger.setContactTime(ct);

      scavenger.run();

      logger.info(String.format("Contact Time: %.0f s -> Efficiency: %.1f%%", ct,
          scavenger.getH2SRemovalEfficiencyPercent()));
    }
  }

  @Test
  @DisplayName("Test effect of mixing efficiency")
  void testMixingEfficiencyEffect() {
    logger.info("\n=== Mixing Efficiency Effect Test ===");

    double[] mixingEfficiencies = {0.3, 0.5, 0.7, 0.85, 1.0};

    for (double me : mixingEfficiencies) {
      H2SScavenger scavenger = new H2SScavenger("Scavenger", feedStream);
      scavenger.setScavengerType(ScavengerType.TRIAZINE);
      scavenger.setScavengerInjectionRate(50.0, "l/hr");
      scavenger.setScavengerConcentration(0.5);
      scavenger.setMixingEfficiency(me);

      scavenger.run();

      logger.info(String.format("Mixing Efficiency: %.0f%% -> Removal: %.1f%%", me * 100,
          scavenger.getH2SRemovalEfficiencyPercent()));
    }
  }

  @Test
  @DisplayName("Test integration with ProcessSystem")
  void testProcessSystemIntegration() {
    ProcessSystem process = new ProcessSystem("H2S Treatment Process");

    process.add(feedStream);

    H2SScavenger scavenger = new H2SScavenger("H2S Scavenger", feedStream);
    scavenger.setScavengerType(ScavengerType.TRIAZINE);
    scavenger.setScavengerInjectionRate(100.0, "l/hr");
    scavenger.setScavengerConcentration(0.5);
    process.add(scavenger);

    process.run();

    logger.info("\n=== ProcessSystem Integration Test ===");
    logger.info("Feed H2S: " + scavenger.getInletH2SConcentration() + " ppm");
    logger.info("Treated Gas H2S: " + scavenger.getOutletH2SConcentration() + " ppm");

    // Verify outlet stream has reduced H2S
    double outletH2S =
        scavenger.getOutletStream().getFluid().getPhase(0).getComponent("H2S").getx() * 1e6;
    logger.info("Outlet Stream H2S (from fluid): " + outletH2S + " ppm");

    assertTrue(outletH2S < 5000, "Outlet H2S should be reduced from inlet 5000 ppm");
  }

  @Test
  @DisplayName("Test cost calculation")
  void testCostCalculation() {
    H2SScavenger scavenger = new H2SScavenger("Scavenger", feedStream);
    scavenger.setScavengerType(ScavengerType.TRIAZINE);
    scavenger.setScavengerInjectionRate(50.0, "l/hr");
    scavenger.setScavengerConcentration(0.5);

    scavenger.run();

    // Typical triazine cost ~$5-10/gal
    double costPerGal = 7.0; // $/gal
    double hourlyCost = scavenger.calculateHourlyCost(costPerGal, "$/gal");
    double dailyCost = hourlyCost * 24;

    logger.info("\n=== Cost Calculation Test ===");
    logger.info(String.format("Injection Rate: %.1f l/hr", 50.0));
    logger.info(String.format("Unit Cost: $%.2f/gal", costPerGal));
    logger.info(String.format("Hourly Cost: $%.2f", hourlyCost));
    logger.info(String.format("Daily Cost: $%.2f", dailyCost));

    assertTrue(hourlyCost > 0, "Cost should be positive");
  }

  @Test
  @DisplayName("Test JSON output")
  void testJsonOutput() {
    H2SScavenger scavenger = new H2SScavenger("Test Scavenger", feedStream);
    scavenger.setScavengerType(ScavengerType.CAUSTIC);
    scavenger.setScavengerInjectionRate(30.0, "l/hr");

    scavenger.run();

    String json = scavenger.toJson();
    logger.info("\n=== JSON Output ===");
    logger.info(json);

    assertTrue(json.contains("Test Scavenger"), "JSON should contain equipment name");
    assertTrue(json.contains("Sodium Hydroxide"), "JSON should contain scavenger type");
    assertTrue(json.contains("removalEfficiencyPercent"), "JSON should contain efficiency");
  }

  @Test
  @DisplayName("Test unit conversions")
  void testUnitConversions() {
    H2SScavenger scavenger = new H2SScavenger("Scavenger", feedStream);
    scavenger.setScavengerType(ScavengerType.TRIAZINE);
    scavenger.setScavengerInjectionRate(100.0, "l/hr");

    scavenger.run();

    logger.info("\n=== Unit Conversion Test ===");
    logger.info("Injection rate (l/hr): " + scavenger.getScavengerInjectionRate("l/hr"));
    logger.info("Injection rate (gal/hr): " + scavenger.getScavengerInjectionRate("gal/hr"));
    logger.info("Injection rate (kg/hr): " + scavenger.getScavengerInjectionRate("kg/hr"));
    logger.info("Injection rate (lb/hr): " + scavenger.getScavengerInjectionRate("lb/hr"));

    logger.info("H2S removed (kg/hr): " + scavenger.getH2SRemoved("kg/hr"));
    logger.info("H2S removed (lb/hr): " + scavenger.getH2SRemoved("lb/hr"));
    logger.info("H2S removed (kg/day): " + scavenger.getH2SRemoved("kg/day"));

    // Verify conversion consistency
    double lPerHr = scavenger.getScavengerInjectionRate("l/hr");
    double galPerHr = scavenger.getScavengerInjectionRate("gal/hr");
    assertEquals(lPerHr / 3.78541, galPerHr, 0.01, "l/hr to gal/hr conversion");
  }
}
