package examples;

import java.util.UUID;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Example demonstrating transient liquid (water and oil) accumulation in a long subsea pipeline
 * using the two-fluid model.
 *
 * <p>
 * This example simulates a gas condensate flow with water through an 80 km, 0.5 m diameter
 * pipeline, showing how liquid accumulates as a function of feed flow rate.
 * </p>
 *
 * <h2>Key Features Demonstrated:</h2>
 * <ul>
 * <li>Three-phase flow (gas + oil/condensate + water)</li>
 * <li>Transient liquid accumulation dynamics</li>
 * <li>Effect of flow rate changes on liquid holdup</li>
 * <li>Water and oil separation along the pipeline</li>
 * <li>Long pipeline simulation with terrain effects</li>
 * </ul>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public class TransientPipelineLiquidAccumulationExample {
  /**
   * Main entry point.
   *
   * @param args Command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println("=============================================================");
    System.out.println("  Transient Pipeline Liquid Accumulation Simulation");
    System.out.println("  80 km, 0.5 m diameter subsea pipeline");
    System.out.println("  Gas condensate with water");
    System.out.println("=============================================================\n");

    // Run the simulation for different flow rates
    runFlowRateSensitivityStudy();
  }

  /**
   * Creates a gas condensate fluid with water (three-phase system).
   *
   * @param temperature Temperature in Celsius
   * @param pressure Pressure in bara
   * @return Configured fluid system
   */
  private static SystemInterface createGasCondensateWithWater(double temperature, double pressure) {
    // Use CPA equation of state for accurate water modeling
    SystemInterface fluid = new SystemSrkCPAstatoil(temperature + 273.15, pressure);

    // Rich gas condensate composition (more liquid dropout)
    // Lighter components (gas phase)
    fluid.addComponent("nitrogen", 1.0); // mol%
    fluid.addComponent("CO2", 2.5);
    fluid.addComponent("methane", 65.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 6.0);
    fluid.addComponent("i-butane", 2.0);
    fluid.addComponent("n-butane", 3.0);

    // Heavier components (condensate/oil phase) - increased for more liquid
    fluid.addComponent("i-pentane", 2.5);
    fluid.addComponent("n-pentane", 3.0);
    fluid.addComponent("n-hexane", 2.5);
    fluid.addComponent("n-heptane", 2.0);
    fluid.addComponent("n-octane", 1.0);

    // Water
    fluid.addComponent("water", 1.5); // 1.5 mol% water

    // Set mixing rule for CPA (rule 10 for water with hydrocarbons)
    fluid.setMixingRule(10);

    // Enable multi-phase check for three-phase flash
    fluid.setMultiPhaseCheck(true);

    return fluid;
  }

  /**
   * Runs a flow rate sensitivity study showing liquid accumulation at different flow rates.
   */
  public static void runFlowRateSensitivityStudy() {
    // Pipeline parameters
    double pipeLength = 80000.0; // 80 km
    double pipeDiameter = 0.5; // 0.5 m (500 mm ID)
    int numberOfSections = 80; // 1000 m per section (reduced for faster simulation)
    double inletTemperature = 60.0; // °C (typical wellhead temperature)
    double inletPressure = 120.0; // bara (typical wellhead pressure)
    double outletPressure = 40.0; // bara (delivery pressure)

    // Flow rates to simulate (kg/s) - higher flow rates
    double[] flowRates = {50.0, 100.0, 150.0};

    System.out.println("Pipeline Configuration:");
    System.out.println("  Length:           " + (pipeLength / 1000) + " km");
    System.out.println("  Diameter:         " + (pipeDiameter * 1000) + " mm");
    System.out.println("  Inlet temperature:" + inletTemperature + " °C");
    System.out.println("  Inlet pressure:   " + inletPressure + " bara");
    System.out.println("  Outlet pressure:  " + outletPressure + " bara");
    System.out.println("  Number of sections: " + numberOfSections);
    System.out.println();

    // Create terrain profile with some undulations
    double[] elevationProfile = createSubseaTerrainProfile(numberOfSections, pipeLength);

    // Results storage
    System.out.println("=============================================================");
    System.out.println("  STEADY-STATE RESULTS AT DIFFERENT FLOW RATES");
    System.out.println("=============================================================");
    System.out.println();
    System.out.printf("%-12s %-15s %-15s %-15s %-15s%n", "Flow Rate", "Liquid Inv.", "Water Holdup",
        "Oil Holdup", "Pressure Drop");
    System.out.printf("%-12s %-15s %-15s %-15s %-15s%n", "(kg/s)", "(m³)", "(avg)", "(avg)",
        "(bar)");
    System.out.println("-------------------------------------------------------------");

    for (double flowRate : flowRates) {
      // Create fluid
      SystemInterface fluid = createGasCondensateWithWater(inletTemperature, inletPressure);

      // Create inlet stream
      Stream inlet = new Stream("GasCondensateFeed", fluid);
      inlet.setFlowRate(flowRate, "kg/sec");
      inlet.setTemperature(inletTemperature, "C");
      inlet.setPressure(inletPressure, "bara");
      inlet.run();

      // Create two-fluid pipe
      TwoFluidPipe pipe = new TwoFluidPipe("SubseaPipeline", inlet);
      pipe.setLength(pipeLength);
      pipe.setDiameter(pipeDiameter);
      pipe.setNumberOfSections(numberOfSections);
      pipe.setRoughness(4.5e-5); // Typical steel roughness
      pipe.setElevationProfile(elevationProfile);
      pipe.setOutletPressure(outletPressure, "bara");
      pipe.setThermodynamicUpdateInterval(50); // Update less frequently for speed

      // Enable heat transfer for realistic temperature profile
      pipe.setHeatTransferCoefficient(25.0); // W/(m²·K) - typical for bare pipe in seawater
      pipe.setSurfaceTemperature(4.0, "C"); // Seabed temperature ~4°C

      // Run steady-state
      pipe.run();

      // Get results
      double liquidInventory = pipe.getLiquidInventory("m3");
      double[] pressureProfile = pipe.getPressureProfile();
      double[] waterHoldup = pipe.getWaterHoldupProfile();
      double[] oilHoldup = pipe.getOilHoldupProfile();

      // Calculate averages
      double avgWaterHoldup = calculateAverage(waterHoldup);
      double avgOilHoldup = calculateAverage(oilHoldup);
      double pressureDrop =
          (pressureProfile[0] - pressureProfile[pressureProfile.length - 1]) / 1e5;

      System.out.printf("%-12.1f %-15.2f %-15.4f %-15.4f %-15.2f%n", flowRate, liquidInventory,
          avgWaterHoldup, avgOilHoldup, pressureDrop);
    }

    System.out.println();

    // Now run detailed transient simulation for one flow rate
    runDetailedTransientSimulation(pipeLength, pipeDiameter, numberOfSections, elevationProfile,
        inletTemperature, inletPressure, outletPressure);
  }

  /**
   * Creates a subsea terrain profile with valleys and hills.
   *
   * @param numberOfSections Number of pipe sections
   * @param totalLength Total pipe length in meters
   * @return Elevation profile array
   */
  private static double[] createSubseaTerrainProfile(int numberOfSections, double totalLength) {
    double[] elevations = new double[numberOfSections];
    double dx = totalLength / numberOfSections;

    // Undulating pipeline profile starting at 0m and ending at 0m
    // Multiple hills and valleys to promote liquid accumulation
    for (int i = 0; i < numberOfSections; i++) {
      double x = i * dx;
      double xNorm = x / totalLength;

      // No net elevation change (start at 0, end at 0)
      double baseElevation = 0.0;

      // Add significant undulations (hills and valleys)
      double undulation = 0;

      // Valley 1: around 15% along the pipe (deep)
      undulation += -80.0 * Math.exp(-Math.pow((xNorm - 0.15) / 0.06, 2));

      // Hill 1: around 30% along the pipe
      undulation += 60.0 * Math.exp(-Math.pow((xNorm - 0.30) / 0.05, 2));

      // Valley 2: around 45% along the pipe (deeper - main accumulation zone)
      undulation += -120.0 * Math.exp(-Math.pow((xNorm - 0.45) / 0.08, 2));

      // Hill 2: around 60% along the pipe
      undulation += 50.0 * Math.exp(-Math.pow((xNorm - 0.60) / 0.05, 2));

      // Valley 3: around 75% along the pipe
      undulation += -70.0 * Math.exp(-Math.pow((xNorm - 0.75) / 0.06, 2));

      // Hill 3: around 90% along the pipe (rising back to 0)
      undulation += 40.0 * Math.exp(-Math.pow((xNorm - 0.90) / 0.05, 2));

      elevations[i] = baseElevation + undulation;
    }

    return elevations;
  }

  /**
   * Runs a detailed transient simulation showing liquid accumulation during ramp-up.
   */
  private static void runDetailedTransientSimulation(double pipeLength, double pipeDiameter,
      int numberOfSections, double[] elevationProfile, double inletTemperature,
      double inletPressure, double outletPressure) {
    System.out.println("=============================================================");
    System.out.println("  TRANSIENT SIMULATION: FLOW RATE RAMP-UP");
    System.out.println("  Starting from 50 kg/s, ramping to 150 kg/s over 2 hours");
    System.out.println("  Total simulation time: 4 hours");
    System.out.println("=============================================================");
    System.out.println();

    // Create fluid and inlet stream
    SystemInterface fluid = createGasCondensateWithWater(inletTemperature, inletPressure);
    Stream inlet = new Stream("GasCondensateFeed", fluid);
    inlet.setFlowRate(50.0, "kg/sec"); // Start at higher flow
    inlet.setTemperature(inletTemperature, "C");
    inlet.setPressure(inletPressure, "bara");
    inlet.run();

    // Create pipe
    TwoFluidPipe pipe = new TwoFluidPipe("SubseaPipeline", inlet);
    pipe.setLength(pipeLength);
    pipe.setDiameter(pipeDiameter);
    pipe.setNumberOfSections(numberOfSections);
    pipe.setRoughness(4.5e-5);
    pipe.setElevationProfile(elevationProfile);
    pipe.setOutletPressure(outletPressure, "bara");
    pipe.setHeatTransferCoefficient(25.0);
    pipe.setSurfaceTemperature(4.0, "C");
    pipe.setThermodynamicUpdateInterval(100); // Update less frequently for speed

    // Initialize with steady state at low flow
    pipe.run();

    double initialInventory = pipe.getLiquidInventory("m3");
    System.out.printf("Initial steady-state (50 kg/s):%n");
    System.out.printf("  Liquid inventory: %.2f m³%n", initialInventory);
    System.out.println();

    // Transient simulation parameters
    double dt = 120.0; // 120 second time step (larger for faster simulation)
    double rampDuration = 7200.0; // 2 hours ramp
    double totalSimTime = 14400.0; // 4 hours total (includes stabilization)
    double startFlowRate = 50.0;
    double endFlowRate = 150.0;
    double reportInterval = 1800.0; // Report every 30 minutes

    UUID runId = UUID.randomUUID();

    System.out.printf("%-12s %-12s %-15s %-15s %-15s%n", "Time (min)", "Flow (kg/s)", "Liquid (m³)",
        "Avg Water HL", "Avg Oil HL");
    System.out.println("-------------------------------------------------------------");

    // Print initial state at t=0 (before any transient steps)
    System.out.printf("%-12.1f %-12.1f %-15.2f %-15.4f %-15.4f%n", 0.0, startFlowRate,
        initialInventory, calculateAverage(pipe.getWaterHoldupProfile()),
        calculateAverage(pipe.getOilHoldupProfile()));

    // Run transient simulation (starting from dt, not 0)
    for (double t = dt; t <= totalSimTime; t += dt) {
      // Calculate current flow rate (linear ramp during first 2 hours)
      double flowRate;
      if (t <= rampDuration) {
        flowRate = startFlowRate + (endFlowRate - startFlowRate) * (t / rampDuration);
      } else {
        flowRate = endFlowRate; // Hold at final rate
      }

      // Update inlet stream
      inlet.setFlowRate(flowRate, "kg/sec");
      inlet.run();

      // Run transient step
      pipe.runTransient(dt, runId);

      // Report at specified intervals (not every step - much faster)
      if (t == 0 || Math.abs(t % reportInterval) < dt / 2 || t >= totalSimTime - dt) {
        double liquidInventory = pipe.getLiquidInventory("m3");
        double avgWaterHoldup = calculateAverage(pipe.getWaterHoldupProfile());
        double avgOilHoldup = calculateAverage(pipe.getOilHoldupProfile());

        System.out.printf("%-12.1f %-12.1f %-15.2f %-15.4f %-15.4f%n", t / 60.0, flowRate,
            liquidInventory, avgWaterHoldup, avgOilHoldup);
      }
    }

    double finalInventory = pipe.getLiquidInventory("m3");
    System.out.println();
    System.out.printf("Final steady-state (150 kg/s):%n");
    System.out.printf("  Liquid inventory: %.2f m³%n", finalInventory);
    System.out.printf("  Inventory change: %.2f m³%n", finalInventory - initialInventory);
    System.out.println();

    // Print holdup profile at key locations
    printHoldupProfileSummary(pipe, elevationProfile);
  }

  /**
   * Prints a summary of liquid holdup along the pipeline.
   */
  private static void printHoldupProfileSummary(TwoFluidPipe pipe, double[] elevationProfile) {
    System.out.println("=============================================================");
    System.out.println("  HOLDUP PROFILE ALONG PIPELINE (FINAL STATE)");
    System.out.println("=============================================================");
    System.out.println();

    double[] positions = pipe.getPositionProfile();
    double[] pressures = pipe.getPressureProfile();
    double[] temperatures = pipe.getTemperatureProfile();
    double[] waterHoldups = pipe.getWaterHoldupProfile();
    double[] oilHoldups = pipe.getOilHoldupProfile();
    double[] liquidHoldups = pipe.getLiquidHoldupProfile();

    System.out.printf("%-10s %-12s %-12s %-12s %-12s %-12s %-12s%n", "Position", "Elevation",
        "Pressure", "Temp", "Liq Holdup", "Water HL", "Oil HL");
    System.out.printf("%-10s %-12s %-12s %-12s %-12s %-12s %-12s%n", "(km)", "(m)", "(bara)",
        "(°C)", "(-)", "(-)", "(-)");
    System.out.println("-------------------------------------------------------------------------");

    // Print every 10 km
    int step = positions.length / 8;
    for (int i = 0; i < positions.length; i += step) {
      System.out.printf("%-10.1f %-12.1f %-12.1f %-12.1f %-12.4f %-12.4f %-12.4f%n",
          positions[i] / 1000.0, elevationProfile[i], pressures[i] / 1e5, temperatures[i] - 273.15,
          liquidHoldups[i], waterHoldups[i], oilHoldups[i]);
    }
    // Print final position
    int last = positions.length - 1;
    System.out.printf("%-10.1f %-12.1f %-12.1f %-12.1f %-12.4f %-12.4f %-12.4f%n",
        positions[last] / 1000.0, elevationProfile[last], pressures[last] / 1e5,
        temperatures[last] - 273.15, liquidHoldups[last], waterHoldups[last], oilHoldups[last]);

    System.out.println();

    // Identify accumulation zones
    System.out.println("Liquid Accumulation Zones (valleys):");
    double maxHoldup = 0;
    int maxIdx = 0;
    for (int i = 0; i < liquidHoldups.length; i++) {
      if (liquidHoldups[i] > maxHoldup) {
        maxHoldup = liquidHoldups[i];
        maxIdx = i;
      }
    }
    System.out.printf("  Maximum holdup: %.4f at %.1f km (elevation: %.1f m)%n", maxHoldup,
        positions[maxIdx] / 1000.0, elevationProfile[maxIdx]);
  }

  /**
   * Calculates the average of an array.
   */
  private static double calculateAverage(double[] values) {
    if (values == null || values.length == 0) {
      return 0.0;
    }
    double sum = 0.0;
    for (double v : values) {
      sum += v;
    }
    return sum / values.length;
  }
}
