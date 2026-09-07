package examples;

import java.util.UUID;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Example demonstrating the two-fluid transient multiphase pipe model.
 *
 * <p>
 * This example simulates gas-condensate flow through a 10 km pipeline with
 * terrain undulations,
 * demonstrating liquid accumulation in low points.
 * </p>
 *
 * <p>
 * The two-fluid model solves separate mass and momentum equations for each
 * phase, enabling accurate
 * prediction of:
 * </p>
 * <ul>
 * <li>Liquid holdup profile along the pipeline</li>
 * <li>Slug formation and dynamics</li>
 * <li>Pressure drop with terrain effects</li>
 * <li>Transient ramp-up and turndown behavior</li>
 * </ul>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public class TwoFluidPipeExample {
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeExample.class);

  /**
   * Main entry point.
   *
   * @param args Command line arguments (not used)
   */
  public static void main(String[] args) {
    // Example 1: Simple steady-state simulation
    logger.info("=== Example 1: Steady-State Pipeline Simulation ===\n");
    runSteadyStateExample();

    // Example 2: Transient ramp-up simulation
    logger.info("\n=== Example 2: Transient Ramp-Up Simulation ===\n");
    runTransientExample();

    // Example 3: Terrain-induced liquid accumulation
    logger.info("\n=== Example 3: Terrain Effects on Liquid Holdup ===\n");
    runTerrainExample();
  }

  /**
   * Demonstrates steady-state simulation of a horizontal pipeline.
   */
  public static void runSteadyStateExample() {
    // Create a gas-condensate fluid
    SystemInterface fluid = new SystemSrkEos(288.15, 80.0); // 15°C, 80 bar
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.01);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.02);
    fluid.addComponent("n-hexane", 0.01);
    fluid.addComponent("n-heptane", 0.01);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    // Create inlet stream
    Stream inlet = new Stream("Inlet", fluid);
    inlet.setFlowRate(50.0, "kg/sec"); // 50 kg/s
    inlet.setTemperature(15.0, "C");
    inlet.setPressure(80.0, "bara");
    inlet.run();

    // Create two-fluid pipe
    TwoFluidPipe pipe = new TwoFluidPipe("GasCondensatePipe", inlet);
    pipe.setLength(10000); // 10 km
    pipe.setDiameter(0.4); // 400 mm ID
    pipe.setNumberOfSections(200);
    pipe.setRoughness(4.5e-5); // Steel pipe roughness (45 µm)

    // Run steady-state
    pipe.run();

    // Report results
    double[] pressures = pipe.getPressureProfile();
    double[] holdups = pipe.getLiquidHoldupProfile();
    double inletP = pressures[0];
    double outletP = pressures[pressures.length - 1];

    logger.info("Pipeline Configuration:");
    logger.info("  Length: {} m", String.format("%.0f", 10000.0));
    logger.info("  Diameter: {} mm", String.format("%.0f", 400.0));
    logger.info("  Mass flow: {} kg/s", String.format("%.1f", 50.0));
    logger.info("");
    logger.info("Steady-State Results:");
    logger.info("  Inlet pressure:  {} bara", String.format("%.2f", inletP / 1e5));
    logger.info("  Outlet pressure: {} bara", String.format("%.2f", outletP / 1e5));
    logger.info("  Pressure drop:   {} bar", String.format("%.2f", (inletP - outletP) / 1e5));
    logger.info("  Liquid inventory: {} m³", String.format("%.2f", pipe.getLiquidInventory("m3")));
  }

  /**
   * Demonstrates transient ramp-up from turndown to normal rate.
   */
  public static void runTransientExample() {
    // Create simple two-phase fluid
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("n-pentane", 0.10);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    // Start at low flow (turndown condition)
    Stream inlet = new Stream("Inlet", fluid);
    inlet.setFlowRate(5.0, "kg/sec"); // Turndown rate
    inlet.run();

    // Create pipe
    TwoFluidPipe pipe = new TwoFluidPipe("TransientPipe", inlet);
    pipe.setLength(5000);
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(100);
    pipe.run();

    double initialInventory = pipe.getLiquidInventory("m3");
    logger.info("Initial liquid inventory (turndown): {} m³",
        String.format("%.2f", initialInventory));

    // Ramp up to normal rate over 10 minutes
    UUID runId = UUID.randomUUID();
    double dt = 1.0; // 1 second timestep
    double rampDuration = 600; // 10 minutes

    for (double t = 0; t < rampDuration; t += dt) {
      // Linear ramp from 5 to 50 kg/s
      double flowRate = 5.0 + (50.0 - 5.0) * (t / rampDuration);
      inlet.setFlowRate(flowRate, "kg/sec");
      inlet.run();

      pipe.runTransient(dt, runId);

      // Report every minute
      if (Math.abs(t % 60.0) < dt / 2) {
        double inventory = pipe.getLiquidInventory("m3");
        logger.info("  t={} s: Flow={} kg/s, Inventory={} m³", String.format("%.0f", t),
            String.format("%.1f", flowRate), String.format("%.2f", inventory));
      }
    }

    double finalInventory = pipe.getLiquidInventory("m3");
    logger.info("Final liquid inventory (normal rate): {} m³",
        String.format("%.2f", finalInventory));
    logger.info("Inventory change: {} m³",
        String.format("%.2f", finalInventory - initialInventory));
  }

  /**
   * Demonstrates terrain-induced liquid accumulation.
   */
  public static void runTerrainExample() {
    // Create gas-dominated fluid with some liquid
    SystemInterface fluid = new SystemSrkEos(293.15, 60.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("Inlet", fluid);
    inlet.setFlowRate(20.0, "kg/sec");
    inlet.run();

    // Create pipe with terrain profile
    TwoFluidPipe pipe = new TwoFluidPipe("TerrainPipe", inlet);
    pipe.setLength(5000);
    pipe.setDiameter(0.25);
    int nSections = 100;
    pipe.setNumberOfSections(nSections);

    // Create terrain with a valley (low point accumulation)
    double[] elevations = new double[nSections];
    for (int i = 0; i < nSections; i++) {
      double x = (double) i / (nSections - 1);
      // Valley profile: drops 50m then rises back
      if (x < 0.4) {
        elevations[i] = -50.0 * (x / 0.4);
      } else if (x < 0.6) {
        elevations[i] = -50.0;
      } else {
        elevations[i] = -50.0 * (1.0 - (x - 0.6) / 0.4);
      }
    }
    pipe.setElevationProfile(elevations);

    // Run simulation
    pipe.run();

    // Report holdup profile
    double[] holdups = pipe.getLiquidHoldupProfile();
    logger.info("Terrain Profile and Liquid Holdup:");
    logger.info("Position [m]  Elevation [m]  Holdup [-]");

    for (int i = 0; i < nSections; i += 10) {
      double position = i * 5000.0 / (nSections - 1);
      logger.info("  {}      {}        {}", String.format("%7.0f", position),
          String.format("%7.1f", elevations[i]), String.format("%.4f", holdups[i]));
    }

    // Identify liquid accumulation zone
    double maxHoldup = 0;
    int maxLocation = 0;
    for (int i = 0; i < holdups.length; i++) {
      if (holdups[i] > maxHoldup) {
        maxHoldup = holdups[i];
        maxLocation = i;
      }
    }

    double maxPosition = maxLocation * 5000.0 / (nSections - 1);
    logger.info("\nMaximum holdup: {} at position {} m (valley bottom)",
        String.format("%.4f", maxHoldup), String.format("%.0f", maxPosition));
    logger.info("Total liquid inventory: {} m³",
        String.format("%.2f", pipe.getLiquidInventory("m3")));
  }
}
