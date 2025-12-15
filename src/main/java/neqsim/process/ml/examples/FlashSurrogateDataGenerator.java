package neqsim.process.ml.examples;

import neqsim.process.ml.TrainingDataCollector;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Example: Generate training data for a flash calculation surrogate model.
 *
 * <p>
 * This demonstrates how to collect training data for neural network surrogates that can replace
 * expensive thermodynamic calculations during RL training or real-time control.
 *
 * <p>
 * The surrogate predicts:
 * <ul>
 * <li>Vapor fraction from (T, P) inputs</li>
 * <li>Phase densities and enthalpies</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class FlashSurrogateDataGenerator {

  /**
   * Generate training data for a simple flash surrogate.
   *
   * @param args command line args
   */
  public static void main(String[] args) {
    System.out.println("=== Flash Surrogate Training Data Generation ===\n");

    // Create data collector
    TrainingDataCollector collector = new TrainingDataCollector("flash_surrogate");

    // Define inputs
    collector.defineInput("temperature", "K", 200.0, 400.0);
    collector.defineInput("pressure", "bar", 1.0, 100.0);

    // Define outputs
    collector.defineOutput("vapor_fraction", "mole_frac", 0.0, 1.0);
    collector.defineOutput("gas_density", "kg/m3", 0.0, 200.0);
    collector.defineOutput("liquid_density", "kg/m3", 400.0, 800.0);
    collector.defineOutput("gas_enthalpy", "kJ/kg", -500.0, 500.0);
    collector.defineOutput("liquid_enthalpy", "kJ/kg", -500.0, 200.0);

    // Create base fluid system
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.08);
    fluid.addComponent("n-butane", 0.05);
    fluid.addComponent("n-pentane", 0.04);
    fluid.addComponent("n-hexane", 0.03);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

    // Generate training data by sampling T-P space
    int nTemp = 20;
    int nPres = 20;
    double tMin = 220.0;
    double tMax = 380.0;
    double pMin = 5.0;
    double pMax = 80.0;

    System.out.printf("Generating %d samples...\n\n", nTemp * nPres);

    for (int i = 0; i < nTemp; i++) {
      double temp = tMin + (tMax - tMin) * i / (nTemp - 1);

      for (int j = 0; j < nPres; j++) {
        double pres = pMin + (pMax - pMin) * j / (nPres - 1);

        // Run flash calculation
        fluid.setTemperature(temp, "K");
        fluid.setPressure(pres, "bar");
        ops.TPflash();
        fluid.init(2);
        fluid.initPhysicalProperties();

        // Record sample
        collector.startSample();
        collector.recordInput("temperature", temp);
        collector.recordInput("pressure", pres);

        // Record outputs
        double vaporFrac = fluid.hasPhaseType("gas") ? fluid.getPhase("gas").getBeta() : 0.0;
        collector.recordOutput("vapor_fraction", vaporFrac);

        if (fluid.hasPhaseType("gas")) {
          collector.recordOutput("gas_density", fluid.getPhase("gas").getDensity("kg/m3"));
          collector.recordOutput("gas_enthalpy", fluid.getPhase("gas").getEnthalpy("kJ/kg"));
        } else {
          collector.recordOutput("gas_density", 0.0);
          collector.recordOutput("gas_enthalpy", 0.0);
        }

        if (fluid.hasPhaseType("oil")) {
          collector.recordOutput("liquid_density", fluid.getPhase("oil").getDensity("kg/m3"));
          collector.recordOutput("liquid_enthalpy", fluid.getPhase("oil").getEnthalpy("kJ/kg"));
        } else {
          collector.recordOutput("liquid_density", 0.0);
          collector.recordOutput("liquid_enthalpy", 0.0);
        }

        collector.endSample();
      }
    }

    // Print summary
    System.out.println(collector.getSummary());

    // Export CSV (just show first few lines)
    System.out.println("CSV Preview (first 10 lines):");
    System.out.println("---");
    String csv = collector.toCSV();
    String[] lines = csv.split("\n");
    for (int i = 0; i < Math.min(10, lines.length); i++) {
      System.out.println(lines[i]);
    }
    System.out.println("...");
    System.out.println("---\n");

    // Show statistics
    System.out.println("Input Statistics:");
    collector.getInputStatistics().forEach((name, stats) -> {
      System.out.printf("  %s: mean=%.2f, std=%.2f\n", name, stats.get("mean"), stats.get("std"));
    });

    System.out.println("\nOutput Statistics:");
    collector.getOutputStatistics().forEach((name, stats) -> {
      System.out.printf("  %s: mean=%.4f, std=%.4f\n", name, stats.get("mean"), stats.get("std"));
    });

    System.out.println("\n=== Data Generation Complete ===");
    System.out.println("Use collector.exportCSV(\"path.csv\") to save training data.");
  }
}
