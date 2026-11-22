/*
 * MultiScenarioVFPExample.java
 *
 * Demonstrates multi-scenario VFP table generation with varying GOR and water cut. This example
 * creates a well model and generates VFP tables for reservoir simulation.
 */
package neqsim.examples;

import java.util.function.Supplier;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.FluidMagicInput;
import neqsim.process.util.optimizer.MultiScenarioVFPGenerator;
import neqsim.process.util.optimizer.MultiScenarioVFPGenerator.VFPTable;
import neqsim.process.util.optimizer.RecombinationFlashGenerator;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Example demonstrating multi-scenario VFP generation.
 *
 * <p>
 * This example shows how to:
 * </p>
 * <ul>
 * <li>Create a reference fluid and configure GOR/WC scenarios</li>
 * <li>Generate fluids at different conditions using recombination</li>
 * <li>Build a well model using the process factory pattern</li>
 * <li>Generate a complete VFP table with all scenario combinations</li>
 * <li>Export to Eclipse VFPEXP format</li>
 * </ul>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public class MultiScenarioVFPExample {
  private static final Logger logger = LogManager.getLogger(MultiScenarioVFPExample.class);

  /**
   * Main method demonstrating VFP generation workflow.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    logger.info("======================================================================");
    logger.info("Multi-Scenario VFP Generation Example");
    logger.info("======================================================================");

    try {
      // Step 1: Create reference fluid (typical light oil)
      logger.info("\n1. Creating reference fluid...");
      SystemInterface referenceFluid = createReferenceFluid();

      // Step 2: Configure fluid input with GOR/WC scenarios
      logger.info("2. Configuring GOR and water cut scenarios...");
      FluidMagicInput fluidInput = FluidMagicInput.fromFluid(referenceFluid);

      // GOR: 80 to 350 Sm3/Sm3 (5 values) - using convenience method
      fluidInput.setGORRange(80.0, 350.0, 5);
      logger.info("   GOR values: {}", arrayToString(fluidInput.generateGORValues()));

      // Water cut: 0% to 60% (4 values) - using convenience method
      fluidInput.setWaterCutRange(0.0, 0.6, 4);
      logger.info("   WC values: {}", arrayToString(fluidInput.generateWaterCutValues()));

      // Step 3: Demonstrate recombination generator
      logger.info("\n3. Testing recombination fluid generator...");
      demonstrateRecombination(fluidInput);

      // Step 4: Create VFP generator with well model
      logger.info("\n4. Creating VFP generator with well model...");
      MultiScenarioVFPGenerator vfpGenerator = createVFPGenerator(fluidInput);

      // Step 5: Generate VFP table
      logger.info("\n5. Generating VFP table...");
      logger.info("   This may take a few minutes...");
      long startTime = System.currentTimeMillis();

      VFPTable table = vfpGenerator.generateVFPTable();

      long elapsed = System.currentTimeMillis() - startTime;
      logger.info("   Generation time: {} seconds", elapsed / 1000.0);

      // Step 6: Report results
      logger.info("\n6. VFP Table Results:");
      logger.info("   Feasible points: {} / {}", table.getFeasibleCount(), table.getTotalPoints());
      double coverage = 100.0 * table.getFeasibleCount() / table.getTotalPoints();
      logger.info("   Coverage: {}%", String.format("%.1f", coverage));

      // Check for low coverage warning
      if (coverage < 50.0) {
        logger.warn("   WARNING: Low coverage - consider adjusting pressure bounds");
      }

      // Step 7: Print sample slices
      logger.info("\n7. Sample VFP Slices:");
      printSampleSlices(table, fluidInput);

      // Step 8: Inspect process pressure diagnostics
      logger.info("\n8. Inspecting process pressure diagnostics...");
      String vfpString = vfpGenerator.toDiagnosticString();
      logger.info("   First 50 lines of output:");
      printFirstLines(vfpString, 50);

      logger.info("\n======================================================================");
      logger.info("Example complete!");
      logger.info("======================================================================");

    } catch (IllegalArgumentException e) {
      logger.error("Configuration error: {}", e.getMessage(), e);
      logger.error("Check GOR range, water cut range, and fluid composition.");
      System.exit(1);
    } catch (RuntimeException e) {
      logger.error("VFP generation failed: {}", e.getMessage(), e);
      System.exit(1);
    }
  }

  /**
   * Creates a reference fluid representing typical light oil.
   *
   * @return configured fluid system
   */
  private static SystemInterface createReferenceFluid() {
    SystemInterface fluid = new SystemSrkEos(288.15, 1.01325);

    // Light oil composition
    fluid.addComponent("nitrogen", 0.004);
    fluid.addComponent("CO2", 0.015);
    fluid.addComponent("methane", 0.40);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("i-butane", 0.01);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("i-pentane", 0.01);
    fluid.addComponent("n-pentane", 0.015);
    fluid.addComponent("n-hexane", 0.02);
    fluid.addComponent("C7", 0.406); // Lumped C7+

    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    logger.info("   Components: {}", fluid.getNumberOfComponents());

    return fluid;
  }

  /**
   * Demonstrates the recombination fluid generator.
   *
   * @param fluidInput the fluid input configuration
   */
  private static void demonstrateRecombination(FluidMagicInput fluidInput) {
    RecombinationFlashGenerator generator = new RecombinationFlashGenerator(fluidInput);

    // Generate a few sample fluids
    double[] testGORs = { 100.0, 200.0, 300.0 };
    double[] testWCs = { 0.0, 0.3 };

    for (double gor : testGORs) {
      for (double wc : testWCs) {
        SystemInterface fluid = generator.generateFluid(gor, wc, 100.0, 323.15, 30.0);

        if (fluid != null) {
          logger.info("   GOR={}, WC={}%: {} phases, density={} kg/m3", gor, wc * 100,
              fluid.getNumberOfPhases(), String.format("%.1f", fluid.getDensity("kg/m3")));
        }
      }
    }

    // Show cache statistics
    String stats = generator.getCacheStatistics();
    logger.info("   Cache: {}", stats);
  }

  /**
   * Creates the VFP generator with a well model.
   *
   * @param fluidInput the fluid input configuration
   * @return configured VFP generator
   */
  private static MultiScenarioVFPGenerator createVFPGenerator(FluidMagicInput fluidInput) {
    // Process factory creates a fresh well model for each calculation
    Supplier<ProcessSystem> wellFactory = () -> {
      ProcessSystem process = new ProcessSystem();

      // Wellhead stream - use reference fluid
      SystemInterface fluid = fluidInput.getReferenceFluid().clone();
      Stream wellhead = new Stream("wellhead", fluid);
      wellhead.setFlowRate(100.0, "m3/hr");
      process.add(wellhead);

      // Well tubing
      AdiabaticPipe tubing = new AdiabaticPipe("tubing", wellhead);
      tubing.setLength(2000.0); // 2000m TVD
      tubing.setDiameter(0.10); // 4" tubing ID
      tubing.setInletElevation(0.0);
      tubing.setOutletElevation(2000.0); // Vertical
      process.add(tubing);

      return process;
    };

    // Create generator using convenience method
    MultiScenarioVFPGenerator generator = new MultiScenarioVFPGenerator(wellFactory, "wellhead", "tubing");

    // Use convenience method to set fluid input (creates flash generator + sets
    // GOR/WC arrays)
    generator.setFluidInput(fluidInput);

    // Configure rate dimension (liquid rates at stock tank conditions)
    generator.setFlowRates(new double[] { 50.0, 100.0, 200.0, 400.0, 600.0 });
    logger.info("   Rates: [50, 100, 200, 400, 600] m3/hr");

    // Configure outlet pressure dimension (THP)
    generator.setOutletPressures(new double[] { 15.0, 25.0, 35.0, 45.0 });
    logger.info("   THPs: [15, 25, 35, 45] bara");

    // GOR and water cut dimensions already set by setFluidInput()
    logger.info("   GOR/WC dimensions from fluid input");

    // Configure pressure search bounds
    generator.setMinInletPressure(60.0);
    generator.setMaxInletPressure(400.0);
    generator.setPressureTolerance(0.5);

    int totalPoints = 5 * 4 * 4 * 5; // rates x pressures x WCs x GORs
    logger.info("   Total VFP points to calculate: {}", totalPoints);

    return generator;
  }

  /**
   * Prints sample slices of the VFP table.
   *
   * @param table      the VFP table
   * @param fluidInput the fluid input for labels
   */
  private static void printSampleSlices(VFPTable table, FluidMagicInput fluidInput) {
    double[] gorValues = fluidInput.generateGORValues();
    double[] wcValues = fluidInput.generateWaterCutValues();

    // Print slice at WC=0%, lowest GOR
    logger.info("\n   Slice: WC=0%, GOR={} Sm3/Sm3", gorValues[0]);
    table.printSlice(0, 0);

    // Print slice at WC=0%, highest GOR
    logger.info("\n   Slice: WC=0%, GOR={} Sm3/Sm3", gorValues[gorValues.length - 1]);
    table.printSlice(0, gorValues.length - 1);

    // Print slice at high WC
    if (wcValues.length > 2) {
      int wcIdx = wcValues.length - 2;
      logger.info("\n   Slice: WC={}%, GOR={} Sm3/Sm3", wcValues[wcIdx] * 100, gorValues[1]);
      table.printSlice(wcIdx, 1);
    }
  }

  /**
   * Prints the first N lines of a string.
   *
   * @param text     the text to print
   * @param maxLines maximum lines to print
   */
  private static void printFirstLines(String text, int maxLines) {
    String[] lines = text.split("\n");
    int count = Math.min(lines.length, maxLines);
    for (int i = 0; i < count; i++) {
      logger.info("   {}", lines[i]);
    }
    if (lines.length > maxLines) {
      logger.info("   ... ({} more lines)", lines.length - maxLines);
    }
  }

  /**
   * Converts a double array to a formatted string.
   *
   * @param arr the array
   * @return formatted string
   */
  private static String arrayToString(double[] arr) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < arr.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(String.format("%.1f", arr[i]));
    }
    sb.append("]");
    return sb.toString();
  }
}
