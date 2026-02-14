package neqsim.examples;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.readwrite.EclipseFluidReadWrite;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Example demonstrating how to export a NeqSim fluid to Eclipse E300
 * compositional format and read
 * it back.
 *
 * <p>
 * This example shows:
 * <ul>
 * <li>Creating a compositional fluid in NeqSim</li>
 * <li>Exporting to Eclipse E300 format with full EOS parameters</li>
 * <li>Reading the E300 file back into NeqSim</li>
 * <li>Comparing original and imported fluid properties</li>
 * </ul>
 * </p>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public class EclipseE300ExportImportExample {

  /**
   * Main method demonstrating the E300 export/import workflow.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    try {
      // Step 1: Create a compositional fluid
      System.out.println("=== Step 1: Creating compositional fluid ===");
      SystemInterface originalFluid = createFluid();
      originalFluid.init(0);
      originalFluid.init(1);

      // Run flash calculation at reservoir conditions
      originalFluid.setPressure(200.0, "bara");
      originalFluid.setTemperature(100.0, "C");
      ThermodynamicOperations ops = new ThermodynamicOperations(originalFluid);
      ops.TPflash();
      originalFluid.initPhysicalProperties();

      System.out.println("Original fluid composition:");
      printFluidSummary(originalFluid);

      // Step 2: Export to E300 file using the new API
      System.out.println("\n=== Step 2: Exporting to E300 format ===");
      String outputFile = "exported_fluid.e300";
      EclipseFluidReadWrite.write(originalFluid, outputFile, 100.0); // 100 C reservoir temp
      System.out.println("Exported to: " + outputFile);

      // You can also get the E300 content as a string for inspection
      String e300Content = EclipseFluidReadWrite.toE300String(originalFluid, 100.0);
      System.out.println("\nFirst 500 characters of E300 content:");
      System.out.println(e300Content.substring(0, Math.min(500, e300Content.length())));

      // Step 3: Read the E300 file back
      System.out.println("\n=== Step 3: Reading E300 file back ===");
      SystemInterface importedFluid = EclipseFluidReadWrite.read(outputFile);

      // Set same conditions as original
      importedFluid.setPressure(200.0, "bara");
      importedFluid.setTemperature(100.0, "C");
      ThermodynamicOperations ops2 = new ThermodynamicOperations(importedFluid);
      ops2.TPflash();
      importedFluid.initPhysicalProperties();

      System.out.println("Imported fluid composition:");
      printFluidSummary(importedFluid);

      // Step 4: Compare properties
      System.out.println("\n=== Step 4: Comparing properties ===");
      compareFluidProperties(originalFluid, importedFluid);

      System.out.println("\n=== Export/Import completed successfully! ===");

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Creates a sample compositional fluid with typical reservoir oil composition.
   *
   * @return configured SystemInterface
   */
  private static SystemInterface createFluid() {
    SystemInterface fluid = new SystemSrkEos(373.15, 200.0);

    // Add defined components
    fluid.addComponent("nitrogen", 0.34);
    fluid.addComponent("CO2", 3.53);
    fluid.addComponent("methane", 70.78);
    fluid.addComponent("ethane", 8.94);
    fluid.addComponent("propane", 5.05);
    fluid.addComponent("i-butane", 0.85);
    fluid.addComponent("n-butane", 1.68);
    fluid.addComponent("i-pentane", 0.62);
    fluid.addComponent("n-pentane", 0.79);
    fluid.addComponent("n-hexane", 0.83);

    // Add C7+ pseudo-components as TBP fractions
    // Parameters: name, mole%, MW(kg/mol), density(g/cm3)
    fluid.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324);
    fluid.addTBPfraction("C8", 1.06, 104.6 / 1000.0, 0.7602);
    fluid.addTBPfraction("C9", 0.79, 119.1 / 1000.0, 0.7677);
    fluid.addTBPfraction("C10", 0.57, 133.0 / 1000.0, 0.790);
    fluid.addTBPfraction("C12", 2.11, 180.0 / 1000.0, 0.820);

    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(true);

    return fluid;
  }

  /**
   * Prints a summary of fluid properties.
   *
   * @param fluid the fluid to summarize
   */
  private static void printFluidSummary(SystemInterface fluid) {
    System.out.println("  Number of components: " + fluid.getNumberOfComponents());
    System.out.println("  Temperature: " + String.format("%.2f", fluid.getTemperature("C")) + " C");
    System.out.println("  Pressure: " + String.format("%.2f", fluid.getPressure("bara")) + " bara");
    System.out.println("  Number of phases: " + fluid.getNumberOfPhases());

    if (fluid.hasPhaseType("gas")) {
      System.out.println("  Gas density: "
          + String.format("%.2f", fluid.getGasPhase().getDensity("kg/m3")) + " kg/m3");
      System.out.println("  Gas Z-factor: " + String.format("%.4f", fluid.getGasPhase().getZ()));
    }
    if (fluid.hasPhaseType("oil")) {
      System.out.println("  Oil density: "
          + String.format("%.2f", fluid.getPhase("oil").getDensity("kg/m3")) + " kg/m3");
    }
  }

  /**
   * Compares properties between two fluids.
   *
   * @param original original fluid
   * @param imported imported fluid
   */
  private static void compareFluidProperties(SystemInterface original, SystemInterface imported) {
    System.out.println("Property comparison:");
    System.out.println("  Original components: " + original.getNumberOfComponents());
    System.out.println("  Imported components: " + imported.getNumberOfComponents());

    if (original.hasPhaseType("gas") && imported.hasPhaseType("gas")) {
      double origGasDens = original.getGasPhase().getDensity("kg/m3");
      double impGasDens = imported.getGasPhase().getDensity("kg/m3");
      double gasDiff = Math.abs(origGasDens - impGasDens) / origGasDens * 100;
      System.out.println("  Gas density difference: " + String.format("%.2f", gasDiff) + "%");
    }

    if (original.hasPhaseType("oil") && imported.hasPhaseType("oil")) {
      double origOilDens = original.getPhase("oil").getDensity("kg/m3");
      double impOilDens = imported.getPhase("oil").getDensity("kg/m3");
      double oilDiff = Math.abs(origOilDens - impOilDens) / origOilDens * 100;
      System.out.println("  Oil density difference: " + String.format("%.2f", oilDiff) + "%");
    }
  }
}
