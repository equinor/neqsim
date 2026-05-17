package neqsim.process.costestimation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.SystemMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for the unified ProcessSystem → MechanicalDesign → CostEstimation workflow.
 *
 * @author AGAS
 * @version 1.0
 */
public class ProcessCostEstimateTest {

  private ProcessSystem process;

  @BeforeEach
  void setUp() {
    // Create a simple gas processing system
    SystemInterface gas = new SystemSrkEos(288.15, 50.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    // Create process
    process = new ProcessSystem();
    process.setName("Test Gas Process");

    // Feed stream
    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    // Inlet separator
    Separator inletSep = new Separator("InletSeparator", feed);
    process.add(inletSep);

    // Compressor
    Compressor compressor = new Compressor("MainCompressor", inletSep.getGasOutStream());
    compressor.setOutletPressure(100.0);
    process.add(compressor);

    // Aftercooler
    Cooler cooler = new Cooler("AfterCooler", compressor.getOutletStream());
    cooler.setOutTemperature(35.0, "C");
    process.add(cooler);

    // Throttling valve
    ThrottlingValve valve = new ThrottlingValve("JTValve", cooler.getOutletStream());
    valve.setOutletPressure(30.0);
    process.add(valve);

    // Run the process
    process.run();
  }

  @Test
  void testProcessSystemCostEstimateWorkflow() {
    // Get cost estimate directly from ProcessSystem
    ProcessCostEstimate costEst = process.getCostEstimate();
    assertNotNull(costEst, "Cost estimate should not be null");

    // Calculate costs
    costEst.calculateAllCosts();

    // Verify costs are calculated
    assertTrue(costEst.getTotalPurchasedEquipmentCost() > 0, "PEC should be positive");
    assertTrue(costEst.getTotalBareModuleCost() > 0, "BMC should be positive");
    assertTrue(costEst.getTotalModuleCost() > 0, "TMC should be positive");
    assertTrue(costEst.getTotalGrassRootsCost() > 0, "Grass roots cost should be positive");

    // Print summary
    System.out.println("=== Cost Estimate via ProcessSystem ===");
    System.out.println("PEC: $" + String.format("%,.0f", costEst.getTotalPurchasedEquipmentCost()));
    System.out.println("BMC: $" + String.format("%,.0f", costEst.getTotalBareModuleCost()));
    System.out.println("TMC: $" + String.format("%,.0f", costEst.getTotalModuleCost()));
    System.out.println("Grass Roots: $" + String.format("%,.0f", costEst.getTotalGrassRootsCost()));
  }

  @Test
  void testSystemMechanicalDesignWorkflow() {
    // Get system mechanical design
    SystemMechanicalDesign mecDesign = process.getSystemMechanicalDesign();
    assertNotNull(mecDesign, "System mechanical design should not be null");

    // Run design calculation
    mecDesign.runDesignCalculation();

    // Verify weights are calculated
    assertTrue(mecDesign.getTotalWeight() > 0, "Total weight should be positive");
    assertTrue(mecDesign.getEquipmentList().size() > 0, "Should have equipment");

    // Print summary
    System.out.println("\n=== Mechanical Design via ProcessSystem ===");
    System.out
        .println("Total Weight: " + String.format("%,.0f", mecDesign.getTotalWeight()) + " kg");
    System.out.println("Equipment Count: " + mecDesign.getEquipmentList().size());
    System.out.println(
        "Total Power: " + String.format("%,.1f", mecDesign.getTotalPowerRequired()) + " kW");
  }

  @Test
  void testCombinedMechanicalDesignAndCostEstimation() {
    // Run combined workflow
    process.runMechanicalDesignAndCostEstimation();

    // Get results
    SystemMechanicalDesign mecDesign = process.getSystemMechanicalDesign();
    ProcessCostEstimate costEst = process.getCostEstimate();

    // Verify both are populated
    assertTrue(mecDesign.getTotalWeight() > 0, "Total weight should be positive");
    assertTrue(costEst.getTotalGrassRootsCost() > 0, "Grass roots cost should be positive");

    // Print summary
    System.out.println("\n=== Combined Workflow ===");
    System.out.println("Weight: " + String.format("%,.0f", mecDesign.getTotalWeight()) + " kg");
    System.out.println("Grass Roots: $" + String.format("%,.0f", costEst.getTotalGrassRootsCost()));
  }

  @Test
  void testJsonExport() {
    // Run workflow
    process.runMechanicalDesignAndCostEstimation();

    // Get combined JSON
    String json = process.getMechanicalDesignAndCostEstimateJson();
    assertNotNull(json, "JSON should not be null");
    assertTrue(json.contains("processName"), "JSON should contain process name");
    assertTrue(json.contains("mechanicalDesignSummary"),
        "JSON should contain mechanical design summary");
    assertTrue(json.contains("costEstimateSummary"), "JSON should contain cost estimate summary");
    assertTrue(json.contains("purchasedEquipmentCost_USD"), "JSON should contain PEC");
    assertTrue(json.contains("grassRootsCost_USD"), "JSON should contain grass roots cost");

    // Print first 2000 chars
    System.out.println("\n=== Combined JSON (first 2000 chars) ===");
    System.out.println(json.substring(0, Math.min(json.length(), 2000)));
  }

  @Test
  void testEquipmentSpecificCostEstimate() {
    // Get cost estimate for specific equipment
    neqsim.process.costestimation.UnitCostEstimateBaseClass compressorCost =
        process.getEquipmentCostEstimate("MainCompressor");

    assertNotNull(compressorCost, "Compressor cost estimate should not be null");
    assertTrue(compressorCost.getPurchasedEquipmentCost() > 0, "Compressor PEC should be positive");

    System.out.println("\n=== Compressor Cost Estimate ===");
    System.out.println(
        "Compressor PEC: $" + String.format("%,.0f", compressorCost.getPurchasedEquipmentCost()));
    System.out
        .println("Compressor BMC: $" + String.format("%,.0f", compressorCost.getBareModuleCost()));
  }

  @Test
  void testEquipmentSpecificMechanicalDesign() {
    // Get mechanical design for specific equipment
    neqsim.process.mechanicaldesign.MechanicalDesign sepDesign =
        process.getEquipmentMechanicalDesign("InletSeparator");

    assertNotNull(sepDesign, "Separator mechanical design should not be null");
    assertTrue(sepDesign.getWeightTotal() > 0, "Separator weight should be positive");

    System.out.println("\n=== Separator Mechanical Design ===");
    System.out.println("Weight: " + String.format("%,.0f", sepDesign.getWeightTotal()) + " kg");
    System.out.println(
        "Design Pressure: " + String.format("%,.1f", sepDesign.getMaxDesignPressure()) + " bara");
  }

  @Test
  void testCostEstimateFromSystemMechanicalDesign() {
    // Alternative workflow: SystemMechanicalDesign → CostEstimate
    SystemMechanicalDesign mecDesign = process.getSystemMechanicalDesign();
    mecDesign.runDesignCalculation();

    // Get cost estimate from mechanical design
    ProcessCostEstimate costEst = mecDesign.getCostEstimate();
    assertNotNull(costEst, "Cost estimate should not be null");
    assertTrue(costEst.getTotalGrassRootsCost() > 0, "Grass roots should be positive");

    System.out.println("\n=== Cost via SystemMechanicalDesign ===");
    System.out.println("Grass Roots: $" + String.format("%,.0f", costEst.getTotalGrassRootsCost()));
  }

  @Test
  void testCostEstimateReports() {
    // Calculate costs
    ProcessCostEstimate costEst = process.getCostEstimate();
    costEst.calculateAllCosts();

    // Generate summary report
    String summaryReport = costEst.generateSummaryReport();
    assertNotNull(summaryReport, "Summary report should not be null");
    assertTrue(summaryReport.contains("CAPITAL COST SUMMARY"),
        "Should contain capital cost summary");

    System.out.println("\n=== Cost Summary Report ===");
    System.out.println(summaryReport);

    // Generate equipment list report
    String equipmentReport = costEst.generateEquipmentListReport();
    assertNotNull(equipmentReport, "Equipment report should not be null");
    assertTrue(equipmentReport.contains("EQUIPMENT COST LIST"),
        "Should contain equipment cost list");

    System.out.println(equipmentReport);
  }

  @Test
  void testJsonWithCostsFromMechanicalDesign() {
    // Test the combined JSON export from SystemMechanicalDesign
    SystemMechanicalDesign mecDesign = process.getSystemMechanicalDesign();
    mecDesign.runDesignCalculation();

    String json = mecDesign.toJsonWithCosts();
    assertNotNull(json, "JSON should not be null");
    assertTrue(json.contains("mechanicalDesignSummary"), "Should contain mech design summary");
    assertTrue(json.contains("costSummary"), "Should contain cost summary");

    System.out.println("\n=== JSON with Costs (first 1500 chars) ===");
    System.out.println(json.substring(0, Math.min(json.length(), 1500)));
  }

  @Test
  void testLocationAndComplexityFactors() {
    ProcessCostEstimate costEst = process.getCostEstimate();

    // Calculate with default factors
    costEst.calculateAllCosts();
    double defaultGrassRoots = costEst.getTotalGrassRootsCost();

    // Set location factor (e.g., 1.15 for North Sea)
    costEst.setLocationFactor(1.15);
    costEst.setComplexityFactor(1.2);
    costEst.calculateAllCosts();
    double adjustedGrassRoots = costEst.getTotalGrassRootsCost();

    // Adjusted should be higher
    assertTrue(adjustedGrassRoots > defaultGrassRoots,
        "Adjusted grass roots should be higher with location and complexity factors");

    System.out.println("\n=== Location/Complexity Factor Test ===");
    System.out.println("Default Grass Roots: $" + String.format("%,.0f", defaultGrassRoots));
    System.out.println("Adjusted Grass Roots: $" + String.format("%,.0f", adjustedGrassRoots));
  }
}
