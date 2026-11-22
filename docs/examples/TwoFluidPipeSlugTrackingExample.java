package examples;

import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker;
import neqsim.process.equipment.pipeline.twophasepipe.SlugTracker;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Example demonstrating slug tracking capabilities in the TwoFluidPipe model.
 *
 * <p>
 * This example shows how the Two-Fluid model tracks:
 * <ul>
 * <li>Terrain-induced liquid accumulation at low points</li>
 * <li>Slug initiation from accumulated liquid</li>
 * <li>Slug propagation through the pipeline (Lagrangian tracking)</li>
 * <li>Slug statistics at the outlet</li>
 * </ul>
 * </p>
 *
 * <p>
 * The pipeline has significant terrain variations designed to induce terrain-induced slugging
 * behavior.
 * </p>
 */
public class TwoFluidPipeSlugTrackingExample {
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeSlugTrackingExample.class);

  public static void main(String[] args) {
    logger.info("=============================================================");
    logger.info("  TwoFluidPipe Slug Tracking Example");
    logger.info("  Demonstrating terrain-induced slugging");
    logger.info("=============================================================");
    logger.info("");

    // Pipeline configuration - shorter pipe with more aggressive terrain for slug formation
    double pipeLength = 20000; // 20 km
    double pipeDiameter = 0.3; // 300 mm ID
    int numberOfSections = 100;
    double roughness = 0.00005; // 50 μm

    // Flow conditions that favor slug formation (lower velocity = more accumulation)
    double inletTemperature = 40.0; // °C
    double inletPressure = 50.0; // bara (lower pressure = more gas)
    double flowRate = 15.0; // kg/s (lower flow rate for terrain-induced slugging)

    // Heat transfer
    double heatTransferCoeff = 15.0; // W/(m²·K)
    double ambientTemp = 10.0; // °C

    // Create terrain with low points for liquid accumulation
    // Profile: dips and rises to trap liquid
    double[] elevationProfile = createSlugInducingTerrain(numberOfSections, pipeLength);

    logger.info("Pipeline Configuration:");
    logger.info("{}", String.format("  Length:              %.1f km%n", pipeLength / 1000));
    logger.info("{}", String.format("  Diameter:            %.0f mm%n", pipeDiameter * 1000));
    logger.info("{}", String.format("  Inlet temperature:   %.1f °C%n", inletTemperature));
    logger.info("{}", String.format("  Inlet pressure:      %.1f bara%n", inletPressure));
    logger.info("{}", String.format("  Flow rate:           %.1f kg/s%n", flowRate));
    logger.info("{}", String.format("  Number of sections:  %d%n", numberOfSections));
    logger.info("");

    // Print terrain summary
    double minElev = Double.MAX_VALUE, maxElev = Double.MIN_VALUE;
    int lowPointCount = 0;
    for (int i = 1; i < elevationProfile.length - 1; i++) {
      double elev = elevationProfile[i];
      minElev = Math.min(minElev, elev);
      maxElev = Math.max(maxElev, elev);
      if (elev < elevationProfile[i - 1] && elev <= elevationProfile[i + 1]) {
        lowPointCount++;
      }
    }
    logger.info("{}", String.format("Terrain: Min = %.1f m, Max = %.1f m, Low points = %d%n%n", minElev, maxElev,
        lowPointCount));

    // Create fluid - gas-condensate with water
    SystemInterface fluid = createGasCondensateWithWater(inletTemperature, inletPressure);

    // Create inlet stream
    Stream inlet = new Stream("SlugTrackingInlet", fluid);
    inlet.setFlowRate(flowRate, "kg/sec");
    inlet.setTemperature(inletTemperature, "C");
    inlet.setPressure(inletPressure, "bara");
    inlet.run();

    // Create and configure TwoFluidPipe with slug tracking enabled
    TwoFluidPipe pipe = new TwoFluidPipe("SlugTrackingPipe", inlet);
    pipe.setLength(pipeLength);
    pipe.setDiameter(pipeDiameter);
    pipe.setNumberOfSections(numberOfSections);
    pipe.setRoughness(roughness);
    pipe.setElevationProfile(elevationProfile);
    pipe.setHeatTransferCoefficient(heatTransferCoeff);
    pipe.setSurfaceTemperature(ambientTemp, "C");
    pipe.setEnableSlugTracking(true);
    pipe.setThermodynamicUpdateInterval(100);

    // First run steady-state to initialize
    logger.info("Running initial steady-state...");
    pipe.run();

    // Lower the critical holdup threshold to trigger slugs sooner
    pipe.getAccumulationTracker().setCriticalHoldup(0.35);

    // Then run transient simulation for slug formation and propagation
    double totalSimTime = 1800.0; // 30 minutes for slug formation
    double timeStep = 1.0; // 1 second steps
    int reportInterval = 300; // Report every 5 minutes

    logger.info("Running transient simulation (30 minutes)...");
    logger.info("(This may take a minute...)");
    long startTime = System.currentTimeMillis();

    java.util.UUID calcId = java.util.UUID.randomUUID();
    double simTime = 0;
    int stepCount = 0;

    while (simTime < totalSimTime) {
      pipe.runTransient(timeStep, calcId);
      simTime += timeStep;
      stepCount++;

      if (stepCount % reportInterval == 0) {
        LiquidAccumulationTracker tracker = pipe.getAccumulationTracker();
        int overflowing = tracker.getOverflowingZones().size();
        logger.info("{}", String.format("  t=%.0fs: %d slugs active, %d generated, %d zones overflowing%n",
            simTime, pipe.getSlugTracker().getSlugCount(),
            pipe.getSlugTracker().getTotalSlugsGenerated(), overflowing));
      }
    }

    long elapsed = System.currentTimeMillis() - startTime;
    logger.info("{}", String.format("Simulation completed in %.1f seconds%n%n", elapsed / 1000.0));

    // Print results
    logger.info("=============================================================");
    logger.info("  PIPELINE RESULTS");
    logger.info("=============================================================");
    logger.info("");

    double[] pressureProfile = pipe.getPressureProfile();
    double[] temperatureProfile = pipe.getTemperatureProfile();
    double[] liquidHoldup = pipe.getLiquidHoldupProfile();
    double[] oilHoldup = pipe.getOilHoldupProfile();
    double[] waterHoldup = pipe.getWaterHoldupProfile();

    double pressureDrop = (pressureProfile[0] - pressureProfile[pressureProfile.length - 1]) / 1e5;
    double outletTemp = temperatureProfile[temperatureProfile.length - 1] - 273.15;

    logger.info("{}", String.format("Pressure drop:        %.2f bar%n", pressureDrop));
    logger.info("{}", String.format("Outlet temperature:   %.1f °C%n", outletTemp));
    logger.info("{}", String.format("Liquid inventory:     %.2f m³%n", pipe.getLiquidInventory("m3")));
    logger.info("");

    // Print accumulation zone analysis
    logger.info("=============================================================");
    logger.info("  LIQUID ACCUMULATION ZONES");
    logger.info("=============================================================");
    logger.info("");

    LiquidAccumulationTracker accTracker = pipe.getAccumulationTracker();
    logger.info("{}", String.format("Number of accumulation zones: %d%n",
        accTracker.getAccumulationZones().size()));
    logger.info("{}", String.format("Total accumulated volume:     %.3f m³%n",
        accTracker.getTotalAccumulatedVolume()));
    logger.info("{}", String.format("Overflowing zones:           %d%n", accTracker.getOverflowingZones().size()));
    logger.info("");

    int zoneNum = 1;
    for (LiquidAccumulationTracker.AccumulationZone zone : accTracker.getAccumulationZones()) {
      // Find max holdup in this zone from actual section data
      double maxHoldupInZone = 0;
      for (int idx : zone.sectionIndices) {
        if (idx < liquidHoldup.length) {
          maxHoldupInZone = Math.max(maxHoldupInZone, liquidHoldup[idx]);
        }
      }
      int sectionCount = zone.sectionIndices.size();
      logger.info("{}", String.format(
          "Zone %d: Position %.0f-%.0f m, %d sections, max holdup=%.2f, %.1f%% full%s%n", zoneNum++,
          zone.startPosition, zone.endPosition, sectionCount, maxHoldupInZone,
          100.0 * zone.liquidVolume / zone.maxVolume, zone.isOverflowing ? " [OVERFLOWING]" : ""));
    }
    logger.info("");

    // Print slug tracking results
    logger.info("=============================================================");
    logger.info("  SLUG TRACKING RESULTS");
    logger.info("=============================================================");
    logger.info("");

    SlugTracker slugTracker = pipe.getSlugTracker();

    logger.info("{}", String.format("Slugs generated (total):     %d%n", slugTracker.getTotalSlugsGenerated()));
    logger.info("{}", String.format("Slugs merged:                %d%n", slugTracker.getTotalSlugsMerged()));
    logger.info("{}", String.format("Active slugs in pipe:        %d%n", slugTracker.getSlugCount()));
    logger.info("{}", String.format("Slugs arrived at outlet:     %d%n", pipe.getOutletSlugCount()));
    logger.info("{}", String.format("Total slug volume at outlet: %.3f m³%n", pipe.getTotalSlugVolumeAtOutlet()));
    logger.info("{}", String.format("Max slug length at outlet:   %.1f m%n", pipe.getMaxSlugLengthAtOutlet()));
    logger.info("{}", String.format("Max slug volume at outlet:   %.3f m³%n", pipe.getMaxSlugVolumeAtOutlet()));
    logger.info("");

    // List active slugs
    if (slugTracker.getSlugCount() > 0) {
      logger.info("Active Slugs:");
      for (SlugTracker.SlugUnit slug : slugTracker.getSlugs()) {
        logger.info("{}", String.format(
            "  Slug #%d: Front=%.0fm, Length=%.1fm, Velocity=%.2f m/s, Volume=%.3f m³%s%n", slug.id,
            slug.frontPosition, slug.slugBodyLength, slug.frontVelocity, slug.liquidVolume,
            slug.isTerrainInduced ? " [terrain-induced]" : ""));
      }
      logger.info("");
    }

    // Mass conservation check
    double massError = slugTracker.getMassConservationError();
    logger.info("{}", String.format("Mass conservation error:     %.6f kg (should be ~0)%n", massError));
    logger.info("");

    // Print detailed profile at key locations
    logger.info("=============================================================");
    logger.info("  DETAILED HOLDUP PROFILE");
    logger.info("=============================================================");
    logger.info("");

    logger.info(
        "Position (km) | Elevation (m) | P (bar) | T (°C)  | Liquid | Oil    | Water  | In Slug");
    logger.info(
        "--------------|---------------|---------|---------|--------|--------|--------|--------");

    double dx = pipeLength / numberOfSections;
    for (int i = 0; i < numberOfSections; i += numberOfSections / 10) {
      double pos = (i + 0.5) * dx / 1000;
      String inSlug = "";
      // Check if any active slug covers this position
      double posMeters = pos * 1000;
      for (SlugTracker.SlugUnit slug : slugTracker.getSlugs()) {
        if (posMeters >= slug.tailPosition && posMeters <= slug.frontPosition) {
          inSlug = "  YES";
          break;
        }
      }
      logger.info("{}", String.format("%13.1f | %13.1f | %7.1f | %7.1f | %6.3f | %6.4f | %6.4f |%s%n", pos,
          elevationProfile[i], pressureProfile[i] / 1e5, temperatureProfile[i] - 273.15,
          liquidHoldup[i], oilHoldup[i], waterHoldup[i], inSlug));
    }
    logger.info("");

    // Print slug statistics summary
    logger.info("=============================================================");
    logger.info("  SLUG STATISTICS SUMMARY");
    logger.info("=============================================================");
    logger.info("");
    logger.info("{}", pipe.getSlugStatisticsSummary());
  }

  /**
   * Creates terrain profile designed to induce slug formation.
   *
   * <p>
   * The terrain has several low points where liquid will accumulate, and uphill sections that
   * create resistance to liquid flow, promoting slug formation.
   */
  private static double[] createSlugInducingTerrain(int nSections, double pipeLength) {
    double[] elevation = new double[nSections];
    double dx = pipeLength / nSections;

    for (int i = 0; i < nSections; i++) {
      double x = i * dx;
      double xNorm = x / pipeLength;

      // Base: slight downward slope
      double base = -5 * xNorm;

      // Add low points at specific locations
      // Low point 1 at 20%
      double lp1 = -15 * Math.exp(-Math.pow((xNorm - 0.2) / 0.05, 2));
      // Low point 2 at 45%
      double lp2 = -20 * Math.exp(-Math.pow((xNorm - 0.45) / 0.06, 2));
      // Low point 3 at 70%
      double lp3 = -12 * Math.exp(-Math.pow((xNorm - 0.70) / 0.04, 2));

      // Add hills between low points (to create dams)
      double h1 = 8 * Math.exp(-Math.pow((xNorm - 0.30) / 0.04, 2));
      double h2 = 10 * Math.exp(-Math.pow((xNorm - 0.55) / 0.05, 2));
      double h3 = 6 * Math.exp(-Math.pow((xNorm - 0.80) / 0.04, 2));

      // Final riser section at outlet
      double riser = 0;
      if (xNorm > 0.9) {
        riser = 20 * (xNorm - 0.9) / 0.1;
      }

      elevation[i] = base + lp1 + lp2 + lp3 + h1 + h2 + h3 + riser;
    }

    return elevation;
  }

  /**
   * Creates a gas condensate fluid with water.
   */
  private static SystemInterface createGasCondensateWithWater(double temp, double pressure) {
    SystemInterface fluid = new SystemSrkCPAstatoil(temp + 273.15, pressure);

    // Lean gas condensate composition
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);
    fluid.addComponent("n-hexane", 0.005);
    fluid.addComponent("n-heptane", 0.003);
    fluid.addComponent("n-octane", 0.002);

    // Water for three-phase behavior
    fluid.addComponent("water", 0.04);

    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    return fluid;
  }
}
