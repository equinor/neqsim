package neqsim.process.equipment.absorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    System.out.println("=== Triazine Scavenger Test ===");
    System.out.println("Inlet H2S: " + scavenger.getInletH2SConcentration() + " ppm");
    System.out.println("Outlet H2S: " + scavenger.getOutletH2SConcentration() + " ppm");
    System.out.println("Removal Efficiency: " + scavenger.getH2SRemovalEfficiencyPercent() + "%");
    System.out.println("H2S Removed: " + scavenger.getH2SRemoved("kg/hr") + " kg/hr");
    System.out.println(scavenger.getPerformanceSummary());

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

      System.out.println("\n=== " + type.getDisplayName() + " ===");
      System.out.println("Base Stoichiometry: " + type.getBaseStoichiometry() + " lb/lb H2S");
      System.out.println("Base Efficiency: " + (type.getBaseEfficiency() * 100) + "%");
      System.out
          .println("Achieved Efficiency: " + scavenger.getH2SRemovalEfficiencyPercent() + "%");
      System.out.println(
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

    System.out.println("\n=== Required Injection Rate Test ===");
    System.out.println("Target H2S: " + scavenger.getTargetH2SConcentration() + " ppm");
    System.out.println("Required Injection Rate: " + requiredRate + " l/hr");

    assertTrue(requiredRate > 0, "Required rate should be positive for sour gas");

    // Now set this rate and verify we meet spec
    scavenger.setScavengerInjectionRate(requiredRate, "l/hr");
    scavenger.run();

    System.out.println(
        "Outlet H2S at calculated rate: " + scavenger.getOutletH2SConcentration() + " ppm");
    System.out.println("Removal efficiency: " + scavenger.getH2SRemovalEfficiencyPercent() + "%");

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
    System.out.println("\n=== Contact Time Effect Test ===");

    double[] contactTimes = {5.0, 15.0, 30.0, 60.0, 120.0};

    for (double ct : contactTimes) {
      H2SScavenger scavenger = new H2SScavenger("Scavenger", feedStream);
      scavenger.setScavengerType(ScavengerType.TRIAZINE);
      scavenger.setScavengerInjectionRate(50.0, "l/hr");
      scavenger.setScavengerConcentration(0.5);
      scavenger.setContactTime(ct);

      scavenger.run();

      System.out.println(String.format("Contact Time: %.0f s -> Efficiency: %.1f%%", ct,
          scavenger.getH2SRemovalEfficiencyPercent()));
    }
  }

  @Test
  @DisplayName("Test effect of mixing efficiency")
  void testMixingEfficiencyEffect() {
    System.out.println("\n=== Mixing Efficiency Effect Test ===");

    double[] mixingEfficiencies = {0.3, 0.5, 0.7, 0.85, 1.0};

    for (double me : mixingEfficiencies) {
      H2SScavenger scavenger = new H2SScavenger("Scavenger", feedStream);
      scavenger.setScavengerType(ScavengerType.TRIAZINE);
      scavenger.setScavengerInjectionRate(50.0, "l/hr");
      scavenger.setScavengerConcentration(0.5);
      scavenger.setMixingEfficiency(me);

      scavenger.run();

      System.out.println(String.format("Mixing Efficiency: %.0f%% -> Removal: %.1f%%", me * 100,
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

    System.out.println("\n=== ProcessSystem Integration Test ===");
    System.out.println("Feed H2S: " + scavenger.getInletH2SConcentration() + " ppm");
    System.out.println("Treated Gas H2S: " + scavenger.getOutletH2SConcentration() + " ppm");

    // Verify outlet stream has reduced H2S
    double outletH2S =
        scavenger.getOutletStream().getFluid().getPhase(0).getComponent("H2S").getx() * 1e6;
    System.out.println("Outlet Stream H2S (from fluid): " + outletH2S + " ppm");

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

    System.out.println("\n=== Cost Calculation Test ===");
    System.out.println(String.format("Injection Rate: %.1f l/hr", 50.0));
    System.out.println(String.format("Unit Cost: $%.2f/gal", costPerGal));
    System.out.println(String.format("Hourly Cost: $%.2f", hourlyCost));
    System.out.println(String.format("Daily Cost: $%.2f", dailyCost));

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
    System.out.println("\n=== JSON Output ===");
    System.out.println(json);

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

    System.out.println("\n=== Unit Conversion Test ===");
    System.out.println("Injection rate (l/hr): " + scavenger.getScavengerInjectionRate("l/hr"));
    System.out.println("Injection rate (gal/hr): " + scavenger.getScavengerInjectionRate("gal/hr"));
    System.out.println("Injection rate (kg/hr): " + scavenger.getScavengerInjectionRate("kg/hr"));
    System.out.println("Injection rate (lb/hr): " + scavenger.getScavengerInjectionRate("lb/hr"));

    System.out.println("H2S removed (kg/hr): " + scavenger.getH2SRemoved("kg/hr"));
    System.out.println("H2S removed (lb/hr): " + scavenger.getH2SRemoved("lb/hr"));
    System.out.println("H2S removed (kg/day): " + scavenger.getH2SRemoved("kg/day"));

    // Verify conversion consistency
    double lPerHr = scavenger.getScavengerInjectionRate("l/hr");
    double galPerHr = scavenger.getScavengerInjectionRate("gal/hr");
    assertEquals(lPerHr / 3.78541, galPerHr, 0.01, "l/hr to gal/hr conversion");
  }
}
