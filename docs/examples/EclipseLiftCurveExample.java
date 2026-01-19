package neqsim.examples;

import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.fielddevelopment.reservoir.EclipseLiftCurveGenerator;
import neqsim.process.fielddevelopment.reservoir.EclipseLiftCurveGenerator.VfpTableData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example demonstrating how to generate Eclipse VFP (lift curve) tables from a NeqSim Beggs and
 * Brill pipeline simulation.
 *
 * <p>
 * This example shows:
 * <ul>
 * <li>Creating a reservoir fluid composition</li>
 * <li>Setting up a PipeBeggsAndBrills pipeline for a vertical riser</li>
 * <li>Configuring VFP table parameters (rates, pressures, water cuts, GORs)</li>
 * <li>Generating VFPPROD tables for Eclipse reservoir simulation</li>
 * <li>Exporting to Eclipse INCLUDE file format</li>
 * </ul>
 * </p>
 *
 * <h2>Eclipse VFP Tables</h2>
 * <p>
 * VFP (Vertical Flow Performance) tables describe the relationship between:
 * <ul>
 * <li>BHP (Bottom Hole Pressure) - the target output</li>
 * <li>THP (Tubing Head Pressure) - surface/wellhead pressure</li>
 * <li>Flow rate - liquid or gas rate at standard conditions</li>
 * <li>WCT (Water Cut) - fraction of water in liquid</li>
 * <li>GOR (Gas-Oil Ratio) - gas to oil ratio</li>
 * <li>ALQ (Artificial Lift Quantity) - optional, e.g., gas lift rate</li>
 * </ul>
 * </p>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public class EclipseLiftCurveExample {

  /**
   * Main method demonstrating lift curve generation workflow.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    try {
      System.out.println("=== Eclipse Lift Curve Generator Example ===\n");

      // Step 1: Create a typical reservoir fluid
      System.out.println("Step 1: Creating reservoir fluid composition...");
      SystemInterface fluid = createReservoirFluid();
      System.out.println("Fluid created with " + fluid.getNumberOfComponents() + " components");

      // Step 2: Create inlet stream and pipeline
      System.out.println("\nStep 2: Setting up pipeline/riser...");
      Stream inlet = new Stream("wellstream", fluid);
      inlet.setFlowRate(50000, "kg/hr");
      inlet.run();

      // Create a vertical riser (typical subsea to platform)
      PipeBeggsAndBrills riser = new PipeBeggsAndBrills("production_riser", inlet);
      riser.setDiameter(0.1524); // 6 inch (internal diameter)
      riser.setLength(1500.0); // 1500 m (measured depth)
      riser.setElevation(1500.0); // 1500 m vertical rise
      riser.setNumberOfIncrements(30);
      riser.setPipeWallRoughness(4.5e-5); // Typical steel roughness

      System.out.println("Riser configured:");
      System.out.println("  Diameter: " + (riser.getDiameter() * 1000) + " mm");
      System.out.println("  Length: " + riser.getLength() + " m");
      System.out.println("  Elevation: 1500 m (vertical riser)");

      // Step 3: Create lift curve generator
      System.out.println("\nStep 3: Creating VFP table generator...");
      EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(riser, fluid);

      // Configure pipeline parameters explicitly (needed because getElevation()
      // returns 0 before pipeline.run())
      generator.setPipelineParameters(0.1524, 1500.0, 1500.0);
      generator.setInletTemperature(80.0, "C"); // Reservoir temperature

      // Step 4: Configure VFP table dimensions
      System.out.println("\nStep 4: Configuring VFP table parameters...");

      // Flow rates: 500 to 8000 Sm3/day liquid
      generator.setFlowRateRange(500, 8000, 8);
      System.out.println("  Flow rates: 500 - 8000 Sm3/day (8 points)");

      // THP: 20 to 80 bara
      generator.setThpRange(20, 80, 7);
      System.out.println("  THP: 20 - 80 bara (7 points)");

      // Water cut: 0 to 80%
      generator.setWaterCutRange(0.0, 0.8, 5);
      System.out.println("  Water cut: 0 - 80% (5 points)");

      // GOR: 100 to 500 Sm3/Sm3
      generator.setGorRange(100, 500, 5);
      System.out.println("  GOR: 100 - 500 Sm3/Sm3 (5 points)");

      int totalPoints = 8 * 7 * 5 * 5;
      System.out.println("  Total BHP points to calculate: " + totalPoints);

      // Step 5: Generate VFP table
      System.out.println("\nStep 5: Generating VFPPROD table (this may take a moment)...");
      long startTime = System.currentTimeMillis();
      VfpTableData vfpTable = generator.generateVfpTable(1, "PROD-A1");
      long elapsed = System.currentTimeMillis() - startTime;
      System.out.println("VFP table generated in " + elapsed + " ms");

      // Step 6: Display some sample results
      System.out.println("\nStep 6: Sample BHP values (bara):");
      System.out.println("(For THP=40 bara, WCT=0.2, GOR=200 Sm3/Sm3)");
      System.out.println("-----------------------------------------");
      System.out.printf("%-15s %-10s%n", "Flow (Sm3/d)", "BHP (bara)");
      System.out.println("-----------------------------------------");

      double[] flowRates = vfpTable.getFlowRates();
      double[][][][][] bhpValues = vfpTable.getBhpValues();

      // Find indices for THP=40, WCT=0.2, GOR=200
      int thpIndex = 3; // Approximately 40 bara
      int wctIndex = 1; // 0.2 water cut
      int gorIndex = 1; // 200 Sm3/Sm3

      for (int i = 0; i < flowRates.length; i++) {
        double bhp = bhpValues[i][thpIndex][wctIndex][gorIndex][0];
        System.out.printf("%-15.0f %-10.2f%n", flowRates[i], bhp);
      }

      // Step 7: Export to Eclipse format
      System.out.println("\nStep 7: Exporting to Eclipse format...");
      String eclipseKeywords = generator.getEclipseKeywords();
      System.out
          .println("Eclipse keywords generated (" + eclipseKeywords.length() + " characters)");

      // Show first part of Eclipse output
      System.out.println("\n--- First 1000 characters of Eclipse output ---");
      System.out.println(eclipseKeywords.substring(0, Math.min(1000, eclipseKeywords.length())));
      System.out.println("...\n");

      // Step 8: Export to file (optional)
      String outputFile = "vfp_prod_a1.inc";
      generator.exportToFile(outputFile);
      System.out.println("VFP table exported to: " + outputFile);

      // Step 9: Also export to CSV for analysis
      String csvOutput = generator.exportToCsv();
      System.out.println("\nCSV export available with " + csvOutput.split("\n").length + " rows");

      // Step 10: JSON export for programmatic use
      String jsonOutput = generator.toJson();
      System.out.println("JSON export available (" + jsonOutput.length() + " characters)");

      System.out.println("\n=== Example completed successfully! ===");

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Creates a typical reservoir oil/gas fluid composition.
   *
   * @return configured SystemInterface
   */
  private static SystemInterface createReservoirFluid() {
    // Create SRK equation of state system at reservoir conditions
    SystemInterface fluid = new SystemSrkEos(353.15, 150.0); // 80°C, 150 bara

    // Typical North Sea reservoir fluid composition (mole fractions)
    fluid.addComponent("nitrogen", 0.5);
    fluid.addComponent("CO2", 2.0);
    fluid.addComponent("methane", 65.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 5.0);
    fluid.addComponent("i-butane", 1.0);
    fluid.addComponent("n-butane", 2.0);
    fluid.addComponent("i-pentane", 1.0);
    fluid.addComponent("n-pentane", 1.0);
    fluid.addComponent("n-hexane", 2.0);
    fluid.addComponent("n-heptane", 5.0);
    fluid.addComponent("n-octane", 3.0);
    fluid.addComponent("n-nonane", 2.0);
    fluid.addComponent("water", 2.5);

    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.init(1);

    return fluid;
  }
}
