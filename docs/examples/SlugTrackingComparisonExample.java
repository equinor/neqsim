package examples;

import java.util.UUID;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.AccumulationZone;
import neqsim.process.equipment.pipeline.twophasepipe.SlugTracker.SlugUnit;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
  private static final Logger logger = LogManager.getLogger(SlugTrackingComparisonExample.class);


  /**
   * Main entry point.
   *
   * @param args Command line arguments (not used)
   */
  public static void main(String[] args) {
    logger.info("=============================================================");
    logger.info("  Slug Tracking Comparison: Two-Fluid vs Drift-Flux");
    logger.info("  20 km pipeline with terrain-induced slugging");
    logger.info("=============================================================\n");

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

    logger.info("Configuration:");
    logger.info("{}", String.format("  Length:          %.0f m (%.1f km)", length, length / 1000));
    logger.info("{}", String.format("  Diameter:        %.0f mm", diameter * 1000));
    logger.info("{}", String.format("  Sections:        %d", sections));
    logger.info("{}", String.format("  Inlet pressure:  %.0f bara", pressure));
    logger.info("{}", String.format("  Flow rate:       %.1f kg/s", flowRate));
    logger.info("{}", String.format("  Simulation time: %.0f minutes", simulationTime / 60));
    logger.info("");

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
    logger.info("{}", String.format("Terrain: %.1f to %.1f m elevation, %d valleys detected\n", minElev,
        maxElev, numValleys));

    // =====================
    // Run Two-Fluid Model
    // =====================
    logger.info("-------------------------------------------------------------");
    logger.info("  Running Two-Fluid Model (7-equation)...");
    logger.info("-------------------------------------------------------------");

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
        logger.info("{}", String.format("  Progress: %.0f%% (time=%.0f min, %d slugs, fill=%.1f%%)",
            100.0 * i / steps, i / 60.0, twoFluidPipe.getSlugTracker().getTotalSlugsGenerated(),
            100.0 * twoFluidPipe.getAccumulationTracker().getAccumulationZones().get(0).liquidVolume
                / twoFluidPipe.getAccumulationTracker().getAccumulationZones().get(0).maxVolume));
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
    for (SlugUnit slug : twoFluidPipe.getSlugTracker().getSlugs()) {
      tf_maxVolume = Math.max(tf_maxVolume, slug.liquidVolume);
    }
    int tf_accumZones = twoFluidPipe.getAccumulationTracker().getAccumulationZones().size();

    logger.info("{}", String.format("  Completed in %.1f seconds\n", elapsed1 / 1000.0));

    // =====================
    // Run Drift-Flux Model
    // =====================
    logger.info("-------------------------------------------------------------");
    logger.info("  Running Drift-Flux Model (4-equation)...");
    logger.info("-------------------------------------------------------------");

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
    for (SlugUnit slug : driftFluxPipe.getSlugTracker().getSlugs()) {
      if (slug.frontPosition >= pipeLength * 0.95) {
        df_outletSlugs++;
      }
    }
    double df_maxLength = driftFluxPipe.getSlugTracker().getMaxSlugLength();
    double df_maxVolume = 0;
    for (SlugUnit slug : driftFluxPipe.getSlugTracker().getSlugs()) {
      df_maxVolume = Math.max(df_maxVolume, slug.liquidVolume);
    }
    int df_accumZones = driftFluxPipe.getAccumulationTracker().getAccumulationZones().size();

    logger.info("{}", String.format("  Completed in %.1f seconds\n", elapsed2 / 1000.0));

    // =====================
    // Print Comparison
    // =====================
    logger.info("=============================================================");
    logger.info("  SLUG TRACKING COMPARISON RESULTS");
    logger.info("=============================================================");
    logger.info("");
    logger.info("{}", String.format("%-30s %15s %15s", "Metric", "Two-Fluid", "Drift-Flux"));
    logger.info("-------------------------------------------------------------");
    logger.info("{}", String.format("%-30s %15d %15d", "Slugs Generated", tf_slugsGenerated, df_slugsGenerated));
    logger.info("{}", String.format("%-30s %15d %15d", "Slugs Merged", tf_slugsMerged, df_slugsMerged));
    logger.info("{}", String.format("%-30s %15d %15d", "Active Slugs at End", tf_activeSlugs, df_activeSlugs));
    logger.info("{}", String.format("%-30s %15d %15d", "Slugs at Outlet", tf_outletSlugs, df_outletSlugs));
    logger.info("{}", String.format("%-30s %15.1f %15.1f", "Max Slug Length (m)", tf_maxLength, df_maxLength));
    logger.info("{}", String.format("%-30s %15.1f %15.1f", "Max Slug Volume (m³)", tf_maxVolume, df_maxVolume));
    logger.info("{}", String.format("%-30s %15d %15d", "Accumulation Zones", tf_accumZones, df_accumZones));
    logger.info("{}", String.format("%-30s %15.1f %15.1f", "Computation Time (s)", elapsed1 / 1000.0,
        elapsed2 / 1000.0));
    logger.info("-------------------------------------------------------------");
    logger.info("");

    // Calculate slug frequencies
    double tf_freq = tf_outletSlugs > 0 ? tf_outletSlugs / simulationTime : 0;
    double df_freq = df_outletSlugs > 0 ? df_outletSlugs / simulationTime : 0;
    logger.info("{}", String.format("Slug Frequency at Outlet:"));
    logger.info("{}", String.format("  Two-Fluid:  %.4f Hz (1 slug every %.1f s)", tf_freq,
        tf_freq > 0 ? 1 / tf_freq : Double.POSITIVE_INFINITY));
    logger.info("{}", String.format("  Drift-Flux: %.4f Hz (1 slug every %.1f s)", df_freq,
        df_freq > 0 ? 1 / df_freq : Double.POSITIVE_INFINITY));
    logger.info("");

    // Analysis
    logger.info("=============================================================");
    logger.info("  ANALYSIS");
    logger.info("=============================================================");
    logger.info("");

    double slugDiff = Math.abs(tf_slugsGenerated - df_slugsGenerated);
    double avgSlugs = (tf_slugsGenerated + df_slugsGenerated) / 2.0;
    double percentDiff = avgSlugs > 0 ? 100.0 * slugDiff / avgSlugs : 0;

    logger.info("{}", String.format("Slug count difference: %.0f (%.1f%% relative)", slugDiff, percentDiff));
    logger.info("");

    if (percentDiff < 20) {
      logger.info("Models show GOOD AGREEMENT in slug generation.");
      logger.info("For this gas-dominant flow, both models predict similar terrain slugging.");
    } else if (percentDiff < 50) {
      logger.info("Models show MODERATE DIFFERENCE in slug generation.");
      logger.info("This may be due to different holdup predictions at terrain low points.");
    } else {
      logger.info("Models show SIGNIFICANT DIFFERENCE in slug generation.");
      logger.info("This is expected for systems with:");
      logger.info("  - High liquid loading");
      logger.info("  - Oil-water stratification effects");
      logger.info("  - Complex terrain with multiple accumulation zones");
    }

    logger.info("");
    logger.info("Key model differences affecting slug tracking:");
    logger.info("  1. Two-Fluid tracks oil and water separately within slugs");
    logger.info("  2. Drift-Flux uses empirical slip relations (faster computation)");
    logger.info("  3. Two-Fluid captures interfacial momentum transfer more accurately");
    logger.info("  4. Both use the same SlugTracker and LiquidAccumulationTracker");
    logger.info("");
    logger.info("Recommendation:");
    if (elapsed1 > 3 * elapsed2) {
      logger.info("  Use Drift-Flux for routine analysis (faster).");
      logger.info("  Use Two-Fluid when oil-water separation in slugs is important.");
    } else {
      logger.info("  Models have similar computation time.");
      logger.info("  Use Two-Fluid for three-phase systems with oil-water effects.");
      logger.info("  Use Drift-Flux for standard gas-liquid systems.");
    }

    // =====================
    // Detailed Diagnostics
    // =====================
    logger.info("");
    logger.info("=============================================================");
    logger.info("  DETAILED DIAGNOSTICS");
    logger.info("=============================================================");
    logger.info("");

    // Compare holdup profiles at key locations
    double[] tf_holdup = twoFluidPipe.getLiquidHoldupProfile();
    double[] df_holdup = driftFluxPipe.getLiquidHoldupProfile();

    logger.info("Liquid Holdup Comparison at Key Locations:");
    logger.info("{}", String.format("%-15s %15s %15s %15s", "Location", "Two-Fluid", "Drift-Flux", "Terrain"));
    logger.info("-------------------------------------------------------------");
    int[] checkPoints = {0, 20, 40, 60, 80, 99}; // Inlet, valleys at 20%,40%,60%,80%, outlet
    for (int idx : checkPoints) {
      double tfH = (tf_holdup != null && idx < tf_holdup.length) ? tf_holdup[idx] : 0;
      double dfH = (df_holdup != null && idx < df_holdup.length) ? df_holdup[idx] : 0;
      double elev = terrain[idx];
      logger.info("{}", String.format("Section %-6d %15.3f %15.3f %15.1f m", idx, tfH, dfH, elev));
    }
    logger.info("");

    // Compare accumulation zones
    logger.info("Accumulation Zone Details:");
    logger.info("");
    logger.info("Two-Fluid Model Zones:");
    for (AccumulationZone zone : twoFluidPipe.getAccumulationTracker().getAccumulationZones()) {
      int startSec = zone.sectionIndices.isEmpty() ? -1 : zone.sectionIndices.get(0);
      logger.info("{}", String.format("  Zone at section %d: volume=%.2f m³, fill=%.1f%%", startSec,
          zone.liquidVolume, 100.0 * zone.liquidVolume / zone.maxVolume));
    }
    logger.info("");
    logger.info("Drift-Flux Model Zones:");
    for (AccumulationZone zone : driftFluxPipe.getAccumulationTracker().getAccumulationZones()) {
      int startSec = zone.sectionIndices.isEmpty() ? -1 : zone.sectionIndices.get(0);
      logger.info("{}", String.format("  Zone at section %d: volume=%.2f m³, fill=%.1f%%", startSec,
          zone.liquidVolume, 100.0 * zone.liquidVolume / zone.maxVolume));
    }
    logger.info("");

    // Root cause analysis
    logger.info("ROOT CAUSE OF DIFFERENCE:");
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
    logger.info("{}", String.format("  Average liquid holdup: Two-Fluid=%.3f, Drift-Flux=%.3f", avgTfHoldup,
        avgDfHoldup));
    if (avgDfHoldup > avgTfHoldup * 1.5) {
      logger.info("  -> Drift-Flux predicts higher holdup -> faster liquid accumulation");
      logger.info("  -> This leads to more frequent slug initiation");
    } else if (avgTfHoldup > avgDfHoldup * 1.5) {
      logger.info("  -> Two-Fluid predicts higher holdup -> faster liquid accumulation");
    } else {
      logger.info("  -> Similar average holdup, difference may be in local accumulation");
    }
  }
}
