package examples;

import java.util.UUID;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comparison of Slug Tracking between Two-Fluid Model and Drift-Flux Model.
 *
 * <p>
 * This example runs both pipeline models with identical terrain and flow conditions, comparing
 * their slug tracking predictions:
 * <ul>
 * <li>Number of terrain-induced slugs generated</li>
 * <li>Slug characteristics (length, volume, velocity)</li>
 * <li>Liquid accumulation zone behavior</li>
 * <li>Slug arrival frequency at outlet</li>
 * </ul>
 *
 * <h2>Model Differences for Slug Tracking:</h2>
 * <ul>
 * <li><b>Two-Fluid Model:</b> Tracks oil and water separately within slugs. Better captures
 * stratified-to-slug transition and oil-water segregation effects.</li>
 * <li><b>Drift-Flux Model:</b> Treats liquid as a single phase. Uses empirical slip relations which
 * are well-validated for typical production systems.</li>
 * </ul>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public class SlugTrackingComparisonExample {

  /**
   * Main entry point.
   *
   * @param args Command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println("=============================================================");
    System.out.println("  Slug Tracking Comparison: Two-Fluid vs Drift-Flux");
    System.out.println("  20 km pipeline with terrain-induced slugging");
    System.out.println("=============================================================\n");

    runSlugTrackingComparison();
  }

  /**
   * Creates a gas-oil fluid suitable for slug tracking comparison.
   *
   * @param temperature Temperature in Celsius
   * @param pressure Pressure in bara
   * @return Configured fluid system
   */
  private static SystemInterface createFluid(double temperature, double pressure) {
    SystemInterface fluid = new SystemSrkEos(temperature + 273.15, pressure);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-pentane", 0.10);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Creates a terrain profile with low points that accumulate liquid.
   */
  private static double[] createTerrainProfile(int sections, double length) {
    double[] elevations = new double[sections];
    double dx = length / sections;

    for (int i = 0; i < sections; i++) {
      double x = i * dx;
      double xNorm = x / length;
      double elevation = 0.0;

      // Create 4 distinct valleys where liquid accumulates
      // Valley 1: around 20%
      elevation += -15.0 * Math.exp(-Math.pow((xNorm - 0.20) / 0.05, 2));
      // Valley 2: around 40%
      elevation += -20.0 * Math.exp(-Math.pow((xNorm - 0.40) / 0.06, 2));
      // Valley 3: around 60%
      elevation += -12.0 * Math.exp(-Math.pow((xNorm - 0.60) / 0.04, 2));
      // Valley 4: around 80%
      elevation += -18.0 * Math.exp(-Math.pow((xNorm - 0.80) / 0.05, 2));

      // Add some small hills between valleys
      elevation += 8.0 * Math.exp(-Math.pow((xNorm - 0.30) / 0.03, 2));
      elevation += 10.0 * Math.exp(-Math.pow((xNorm - 0.50) / 0.03, 2));
      elevation += 6.0 * Math.exp(-Math.pow((xNorm - 0.70) / 0.03, 2));

      elevations[i] = elevation;
    }
    return elevations;
  }

  /**
   * Runs the slug tracking comparison between models.
   */
  public static void runSlugTrackingComparison() {
    // Common pipeline parameters
    double length = 20000.0; // 20 km
    double diameter = 0.3; // 300 mm (12 inch)
    int sections = 100;
    double pressure = 50.0; // bara
    double temperature = 15.0; // Celsius
    double flowRate = 15.0; // kg/s
    double simulationTime = 2 * 60 * 60.0; // 2 hours for longer accumulation

    double[] terrain = createTerrainProfile(sections, length);

    System.out.println("Configuration:");
    System.out.printf("  Length:          %.0f m (%.1f km)%n", length, length / 1000);
    System.out.printf("  Diameter:        %.0f mm%n", diameter * 1000);
    System.out.printf("  Sections:        %d%n", sections);
    System.out.printf("  Inlet pressure:  %.0f bara%n", pressure);
    System.out.printf("  Flow rate:       %.1f kg/s%n", flowRate);
    System.out.printf("  Simulation time: %.0f minutes%n", simulationTime / 60);
    System.out.println();

    // Print terrain summary
    double minElev = Double.MAX_VALUE, maxElev = Double.MIN_VALUE;
    int numValleys = 0;
    for (int i = 1; i < terrain.length - 1; i++) {
      minElev = Math.min(minElev, terrain[i]);
      maxElev = Math.max(maxElev, terrain[i]);
      // Count local minima (valleys)
      if (terrain[i] < terrain[i - 1] && terrain[i] < terrain[i + 1]) {
        numValleys++;
      }
    }
    System.out.printf("Terrain: %.1f to %.1f m elevation, %d valleys detected%n%n", minElev,
        maxElev, numValleys);

    // =====================
    // Run Two-Fluid Model
    // =====================
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Running Two-Fluid Model (7-equation)...");
    System.out.println("-------------------------------------------------------------");

    SystemInterface fluid1 = createFluid(temperature, pressure);
    Stream inlet1 = new Stream("TwoFluidInlet", fluid1);
    inlet1.setFlowRate(flowRate, "kg/sec");
    inlet1.setTemperature(temperature, "C");
    inlet1.setPressure(pressure, "bara");
    inlet1.run();

    TwoFluidPipe twoFluidPipe = new TwoFluidPipe("TwoFluidPipe", inlet1);
    twoFluidPipe.setLength(length);
    twoFluidPipe.setDiameter(diameter);
    twoFluidPipe.setNumberOfSections(sections);
    twoFluidPipe.setElevationProfile(terrain);
    twoFluidPipe.setRoughness(4.5e-5);
    twoFluidPipe.setEnableSlugTracking(true);
    // Lower critical holdup threshold to trigger slugs earlier
    twoFluidPipe.getAccumulationTracker().setCriticalHoldup(0.35);

    long startTime1 = System.currentTimeMillis();
    twoFluidPipe.run();

    // Run transient simulation
    double dt = 1.0; // 1 second steps
    int steps = (int) (simulationTime / dt);
    UUID id = UUID.randomUUID();
    for (int i = 0; i < steps; i++) {
      twoFluidPipe.runTransient(dt, id);
      if (i % 900 == 0 && i > 0) { // Report every 15 minutes
        System.out.printf("  Progress: %.0f%% (time=%.0f min, %d slugs, fill=%.1f%%)%n",
            100.0 * i / steps, i / 60.0, twoFluidPipe.getSlugTracker().getTotalSlugsGenerated(),
            100.0 * twoFluidPipe.getAccumulationTracker().getAccumulationZones().get(0).liquidVolume
                / twoFluidPipe.getAccumulationTracker().getAccumulationZones().get(0).maxVolume);
      }
    }
    long elapsed1 = System.currentTimeMillis() - startTime1;

    // Collect Two-Fluid results
    int tf_slugsGenerated = twoFluidPipe.getSlugTracker().getTotalSlugsGenerated();
    int tf_slugsMerged = twoFluidPipe.getSlugTracker().getTotalSlugsMerged();
    int tf_activeSlugs = twoFluidPipe.getSlugTracker().getSlugs().size();
    int tf_outletSlugs = twoFluidPipe.getOutletSlugCount();
    double tf_maxLength = twoFluidPipe.getSlugTracker().getMaxSlugLength();
    double tf_maxVolume = 0;
    for (var slug : twoFluidPipe.getSlugTracker().getSlugs()) {
      tf_maxVolume = Math.max(tf_maxVolume, slug.liquidVolume);
    }
    int tf_accumZones = twoFluidPipe.getAccumulationTracker().getAccumulationZones().size();

    System.out.printf("  Completed in %.1f seconds%n%n", elapsed1 / 1000.0);

    // =====================
    // Run Drift-Flux Model
    // =====================
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Running Drift-Flux Model (4-equation)...");
    System.out.println("-------------------------------------------------------------");

    SystemInterface fluid2 = createFluid(temperature, pressure);
    Stream inlet2 = new Stream("DriftFluxInlet", fluid2);
    inlet2.setFlowRate(flowRate, "kg/sec");
    inlet2.setTemperature(temperature, "C");
    inlet2.setPressure(pressure, "bara");
    inlet2.run();

    TransientPipe driftFluxPipe = new TransientPipe("DriftFluxPipe", inlet2);
    driftFluxPipe.setLength(length);
    driftFluxPipe.setDiameter(diameter);
    driftFluxPipe.setNumberOfSections(sections);
    driftFluxPipe.setElevationProfile(terrain);
    driftFluxPipe.setRoughness(4.5e-5);
    // Slug tracking is enabled by default when accumulationTracker and slugTracker are initialized
    // Use shorter simulation for Drift-Flux since it generates slugs quickly
    double dfSimTime = 30 * 60.0; // 30 minutes is enough for Drift-Flux
    driftFluxPipe.setMaxSimulationTime(dfSimTime);
    // Lower critical holdup threshold to be consistent with Two-Fluid
    driftFluxPipe.getAccumulationTracker().setCriticalHoldup(0.35);
    // Time step is calculated internally based on CFL condition

    long startTime2 = System.currentTimeMillis();
    driftFluxPipe.run();
    long elapsed2 = System.currentTimeMillis() - startTime2;

    // Collect Drift-Flux results
    int df_slugsGenerated = driftFluxPipe.getSlugTracker().getTotalSlugsGenerated();
    int df_slugsMerged = driftFluxPipe.getSlugTracker().getTotalSlugsMerged();
    int df_activeSlugs = driftFluxPipe.getSlugTracker().getSlugs().size();
    // TransientPipe doesn't track outlet slugs separately, estimate from slugs past last section
    int df_outletSlugs = 0;
    double pipeLength = length;
    for (var slug : driftFluxPipe.getSlugTracker().getSlugs()) {
      if (slug.frontPosition >= pipeLength * 0.95) {
        df_outletSlugs++;
      }
    }
    double df_maxLength = driftFluxPipe.getSlugTracker().getMaxSlugLength();
    double df_maxVolume = 0;
    for (var slug : driftFluxPipe.getSlugTracker().getSlugs()) {
      df_maxVolume = Math.max(df_maxVolume, slug.liquidVolume);
    }
    int df_accumZones = driftFluxPipe.getAccumulationTracker().getAccumulationZones().size();

    System.out.printf("  Completed in %.1f seconds%n%n", elapsed2 / 1000.0);

    // =====================
    // Print Comparison
    // =====================
    System.out.println("=============================================================");
    System.out.println("  SLUG TRACKING COMPARISON RESULTS");
    System.out.println("=============================================================");
    System.out.println();
    System.out.printf("%-30s %15s %15s%n", "Metric", "Two-Fluid", "Drift-Flux");
    System.out.println("-------------------------------------------------------------");
    System.out.printf("%-30s %15d %15d%n", "Slugs Generated", tf_slugsGenerated, df_slugsGenerated);
    System.out.printf("%-30s %15d %15d%n", "Slugs Merged", tf_slugsMerged, df_slugsMerged);
    System.out.printf("%-30s %15d %15d%n", "Active Slugs at End", tf_activeSlugs, df_activeSlugs);
    System.out.printf("%-30s %15d %15d%n", "Slugs at Outlet", tf_outletSlugs, df_outletSlugs);
    System.out.printf("%-30s %15.1f %15.1f%n", "Max Slug Length (m)", tf_maxLength, df_maxLength);
    System.out.printf("%-30s %15.1f %15.1f%n", "Max Slug Volume (m³)", tf_maxVolume, df_maxVolume);
    System.out.printf("%-30s %15d %15d%n", "Accumulation Zones", tf_accumZones, df_accumZones);
    System.out.printf("%-30s %15.1f %15.1f%n", "Computation Time (s)", elapsed1 / 1000.0,
        elapsed2 / 1000.0);
    System.out.println("-------------------------------------------------------------");
    System.out.println();

    // Calculate slug frequencies
    double tf_freq = tf_outletSlugs > 0 ? tf_outletSlugs / simulationTime : 0;
    double df_freq = df_outletSlugs > 0 ? df_outletSlugs / simulationTime : 0;
    System.out.printf("Slug Frequency at Outlet:%n");
    System.out.printf("  Two-Fluid:  %.4f Hz (1 slug every %.1f s)%n", tf_freq,
        tf_freq > 0 ? 1 / tf_freq : Double.POSITIVE_INFINITY);
    System.out.printf("  Drift-Flux: %.4f Hz (1 slug every %.1f s)%n", df_freq,
        df_freq > 0 ? 1 / df_freq : Double.POSITIVE_INFINITY);
    System.out.println();

    // Analysis
    System.out.println("=============================================================");
    System.out.println("  ANALYSIS");
    System.out.println("=============================================================");
    System.out.println();

    double slugDiff = Math.abs(tf_slugsGenerated - df_slugsGenerated);
    double avgSlugs = (tf_slugsGenerated + df_slugsGenerated) / 2.0;
    double percentDiff = avgSlugs > 0 ? 100.0 * slugDiff / avgSlugs : 0;

    System.out.printf("Slug count difference: %.0f (%.1f%% relative)%n", slugDiff, percentDiff);
    System.out.println();

    if (percentDiff < 20) {
      System.out.println("Models show GOOD AGREEMENT in slug generation.");
      System.out
          .println("For this gas-dominant flow, both models predict similar terrain slugging.");
    } else if (percentDiff < 50) {
      System.out.println("Models show MODERATE DIFFERENCE in slug generation.");
      System.out.println("This may be due to different holdup predictions at terrain low points.");
    } else {
      System.out.println("Models show SIGNIFICANT DIFFERENCE in slug generation.");
      System.out.println("This is expected for systems with:");
      System.out.println("  - High liquid loading");
      System.out.println("  - Oil-water stratification effects");
      System.out.println("  - Complex terrain with multiple accumulation zones");
    }

    System.out.println();
    System.out.println("Key model differences affecting slug tracking:");
    System.out.println("  1. Two-Fluid tracks oil and water separately within slugs");
    System.out.println("  2. Drift-Flux uses empirical slip relations (faster computation)");
    System.out.println("  3. Two-Fluid captures interfacial momentum transfer more accurately");
    System.out.println("  4. Both use the same SlugTracker and LiquidAccumulationTracker");
    System.out.println();
    System.out.println("Recommendation:");
    if (elapsed1 > 3 * elapsed2) {
      System.out.println("  Use Drift-Flux for routine analysis (faster).");
      System.out.println("  Use Two-Fluid when oil-water separation in slugs is important.");
    } else {
      System.out.println("  Models have similar computation time.");
      System.out.println("  Use Two-Fluid for three-phase systems with oil-water effects.");
      System.out.println("  Use Drift-Flux for standard gas-liquid systems.");
    }

    // =====================
    // Detailed Diagnostics
    // =====================
    System.out.println();
    System.out.println("=============================================================");
    System.out.println("  DETAILED DIAGNOSTICS");
    System.out.println("=============================================================");
    System.out.println();

    // Compare holdup profiles at key locations
    double[] tf_holdup = twoFluidPipe.getLiquidHoldupProfile();
    double[] df_holdup = driftFluxPipe.getLiquidHoldupProfile();

    System.out.println("Liquid Holdup Comparison at Key Locations:");
    System.out.printf("%-15s %15s %15s %15s%n", "Location", "Two-Fluid", "Drift-Flux", "Terrain");
    System.out.println("-------------------------------------------------------------");
    int[] checkPoints = {0, 20, 40, 60, 80, 99}; // Inlet, valleys at 20%,40%,60%,80%, outlet
    for (int idx : checkPoints) {
      double tfH = (tf_holdup != null && idx < tf_holdup.length) ? tf_holdup[idx] : 0;
      double dfH = (df_holdup != null && idx < df_holdup.length) ? df_holdup[idx] : 0;
      double elev = terrain[idx];
      System.out.printf("Section %-6d %15.3f %15.3f %15.1f m%n", idx, tfH, dfH, elev);
    }
    System.out.println();

    // Compare accumulation zones
    System.out.println("Accumulation Zone Details:");
    System.out.println();
    System.out.println("Two-Fluid Model Zones:");
    for (var zone : twoFluidPipe.getAccumulationTracker().getAccumulationZones()) {
      int startSec = zone.sectionIndices.isEmpty() ? -1 : zone.sectionIndices.get(0);
      System.out.printf("  Zone at section %d: volume=%.2f m³, fill=%.1f%%%n", startSec,
          zone.liquidVolume, 100.0 * zone.liquidVolume / zone.maxVolume);
    }
    System.out.println();
    System.out.println("Drift-Flux Model Zones:");
    for (var zone : driftFluxPipe.getAccumulationTracker().getAccumulationZones()) {
      int startSec = zone.sectionIndices.isEmpty() ? -1 : zone.sectionIndices.get(0);
      System.out.printf("  Zone at section %d: volume=%.2f m³, fill=%.1f%%%n", startSec,
          zone.liquidVolume, 100.0 * zone.liquidVolume / zone.maxVolume);
    }
    System.out.println();

    // Root cause analysis
    System.out.println("ROOT CAUSE OF DIFFERENCE:");
    double avgTfHoldup = 0, avgDfHoldup = 0;
    if (tf_holdup != null && df_holdup != null) {
      for (int i = 0; i < Math.min(tf_holdup.length, df_holdup.length); i++) {
        avgTfHoldup += tf_holdup[i];
        avgDfHoldup += df_holdup[i];
      }
      int n = Math.min(tf_holdup.length, df_holdup.length);
      avgTfHoldup /= n;
      avgDfHoldup /= n;
    }
    System.out.printf("  Average liquid holdup: Two-Fluid=%.3f, Drift-Flux=%.3f%n", avgTfHoldup,
        avgDfHoldup);
    if (avgDfHoldup > avgTfHoldup * 1.5) {
      System.out.println("  -> Drift-Flux predicts higher holdup -> faster liquid accumulation");
      System.out.println("  -> This leads to more frequent slug initiation");
    } else if (avgTfHoldup > avgDfHoldup * 1.5) {
      System.out.println("  -> Two-Fluid predicts higher holdup -> faster liquid accumulation");
    } else {
      System.out.println("  -> Similar average holdup, difference may be in local accumulation");
    }
  }
}
