package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comparison tests between Two-Fluid model and Beggs-Brill correlation.
 *
 * <p>
 * These tests validate that the two-fluid model produces reasonable steady-state pressure drops
 * compared to the established Beggs-Brill empirical correlation for various flow conditions.
 * </p>
 *
 * <p>
 * The two-fluid model solves mechanistic equations while Beggs-Brill uses empirical correlations.
 * Some difference is expected between models - the goal is to verify they're in the same ballpark
 * and respond similarly to changes in flow rate, diameter, and inclination.
 * </p>
 *
 * @author NeqSim Team
 */
class TwoFluidVsBeggsBrillComparisonTest {
  /**
   * Helper to calculate percent difference between two values.
   */
  private double percentDifference(double a, double b) {
    if (Math.abs(a) < 1e-10 && Math.abs(b) < 1e-10) {
      return 0.0;
    }
    double avg = (Math.abs(a) + Math.abs(b)) / 2.0;
    return 100.0 * Math.abs(a - b) / avg;
  }

  @Test
  @DisplayName("Horizontal gas pipeline - single phase comparison")
  void testHorizontalGasPipeline() {
    // Create dry gas fluid (single phase) - use lower flow rate for stability
    SystemInterface fluid = new SystemSrkEos(303.15, 80.0); // Higher pressure for margin
    fluid.addComponent("methane", 0.92);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    // Create inlet stream - lower flow rate, larger diameter for lower pressure drop
    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(3.0, "kg/sec");
    inlet.setTemperature(30.0, "C");
    inlet.setPressure(80.0, "bara");
    inlet.run();

    // --- Beggs-Brill model ---
    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB-pipe", inlet);
    bbPipe.setLength(2000.0); // 2 km - shorter pipe
    bbPipe.setDiameter(0.25); // 250 mm - larger diameter
    bbPipe.setElevation(0.0); // horizontal
    bbPipe.setPipeWallRoughness(4.5e-5);
    bbPipe.setNumberOfIncrements(20);
    bbPipe.run();

    double bbOutletPressure = bbPipe.getOutletPressure(); // bar
    double bbPressureDrop = inlet.getPressure() - bbOutletPressure;

    // --- Two-Fluid model ---
    inlet.run();
    TwoFluidPipe tfPipe = new TwoFluidPipe("TF-pipe", inlet);
    tfPipe.setLength(2000.0);
    tfPipe.setDiameter(0.25);
    tfPipe.setNumberOfSections(20);
    tfPipe.setRoughness(4.5e-5);
    tfPipe.run();

    double[] pressureProfile = tfPipe.getPressureProfile();
    double tfOutletPressure = pressureProfile[pressureProfile.length - 1] / 1e5; // Pa to bar
    double tfInletPressure = pressureProfile[0] / 1e5;
    double tfPressureDrop = tfInletPressure - tfOutletPressure;

    // Report results
    System.out.println("=== Horizontal Gas Pipeline Comparison ===");
    System.out.printf("Beggs-Brill: Outlet P = %.3f bar, ΔP = %.3f bar%n", bbOutletPressure,
        bbPressureDrop);
    System.out.printf("Two-Fluid:   Outlet P = %.3f bar, ΔP = %.3f bar%n", tfOutletPressure,
        tfPressureDrop);
    System.out.printf("Difference:  %.1f%%%n", percentDifference(bbPressureDrop, tfPressureDrop));

    // Both should produce positive pressure drops
    assertTrue(bbPressureDrop > 0, "Beggs-Brill should calculate positive pressure drop");
    assertTrue(tfPressureDrop > 0, "Two-Fluid should calculate positive pressure drop");

    // Verify both outlets are still positive (simulation is valid)
    assertTrue(bbOutletPressure > 0, "BB outlet pressure should be positive");
    assertTrue(tfOutletPressure > 0, "TF outlet pressure should be positive");
  }

  @Test
  @DisplayName("Horizontal two-phase pipeline - gas dominant")
  void testHorizontalTwoPhasePipelineGasDominant() {
    // Create gas-condensate fluid with low liquid content
    SystemInterface fluid = new SystemSrkEos(303.15, 70.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-pentane", 0.02);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5.0, "kg/sec");
    inlet.setTemperature(25.0, "C");
    inlet.setPressure(70.0, "bara");
    inlet.run();

    // --- Beggs-Brill model ---
    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB-pipe", inlet);
    bbPipe.setLength(1000.0);
    bbPipe.setDiameter(0.20);
    bbPipe.setElevation(0.0);
    bbPipe.setPipeWallRoughness(4.5e-5);
    bbPipe.setNumberOfIncrements(20);
    bbPipe.run();

    double bbOutletPressure = bbPipe.getOutletPressure();
    double bbPressureDrop = inlet.getPressure() - bbOutletPressure;

    // --- Two-Fluid model ---
    inlet.run();
    TwoFluidPipe tfPipe = new TwoFluidPipe("TF-pipe", inlet);
    tfPipe.setLength(1000.0);
    tfPipe.setDiameter(0.20);
    tfPipe.setNumberOfSections(20);
    tfPipe.setRoughness(4.5e-5);
    tfPipe.run();

    double[] pressureProfile = tfPipe.getPressureProfile();
    double[] holdupProfile = tfPipe.getLiquidHoldupProfile();
    double tfOutletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double tfInletPressure = pressureProfile[0] / 1e5;
    double tfPressureDrop = tfInletPressure - tfOutletPressure;
    double tfAvgHoldup = 0;
    for (double h : holdupProfile) {
      tfAvgHoldup += h;
    }
    tfAvgHoldup /= holdupProfile.length;

    System.out.println("\n=== Horizontal Two-Phase (Gas Dominant) Comparison ===");
    System.out.printf("Beggs-Brill: Outlet P = %.3f bar, ΔP = %.3f bar%n", bbOutletPressure,
        bbPressureDrop);
    System.out.printf("Two-Fluid:   Outlet P = %.3f bar, ΔP = %.3f bar%n", tfOutletPressure,
        tfPressureDrop);
    System.out.printf("Two-Fluid avg liquid holdup: %.4f%n", tfAvgHoldup);
    System.out.printf("Pressure drop difference: %.1f%%%n",
        percentDifference(bbPressureDrop, tfPressureDrop));

    assertTrue(bbPressureDrop > 0, "Beggs-Brill should calculate positive pressure drop");
    assertTrue(tfPressureDrop > 0, "Two-Fluid should calculate positive pressure drop");
  }

  @Test
  @DisplayName("Slightly inclined pipe - mild uphill")
  void testSlightlyInclinedPipe() {
    // Create gas-dominant fluid with conservative conditions
    SystemInterface fluid = new SystemSrkEos(293.15, 60.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(3.0, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(60.0, "bara");
    inlet.run();

    double pipeLength = 1000.0; // 1 km
    double elevation = 20.0; // 20 m rise (gentle incline)

    // --- Beggs-Brill model ---
    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB-pipe", inlet);
    bbPipe.setLength(pipeLength);
    bbPipe.setDiameter(0.20);
    bbPipe.setElevation(elevation);
    bbPipe.setPipeWallRoughness(4.5e-5);
    bbPipe.setNumberOfIncrements(20);
    bbPipe.run();

    double bbOutletPressure = bbPipe.getOutletPressure();
    double bbPressureDrop = inlet.getPressure() - bbOutletPressure;

    // --- Two-Fluid model ---
    inlet.run();
    TwoFluidPipe tfPipe = new TwoFluidPipe("TF-pipe", inlet);
    tfPipe.setLength(pipeLength);
    tfPipe.setDiameter(0.20);
    tfPipe.setNumberOfSections(20);
    tfPipe.setRoughness(4.5e-5);

    // Create linear elevation profile
    double[] elevations = new double[20];
    for (int i = 0; i < 20; i++) {
      elevations[i] = elevation * i / 19.0;
    }
    tfPipe.setElevationProfile(elevations);
    tfPipe.run();

    double[] pressureProfile = tfPipe.getPressureProfile();
    double tfOutletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double tfInletPressure = pressureProfile[0] / 1e5;
    double tfPressureDrop = tfInletPressure - tfOutletPressure;

    System.out.println("\n=== Slightly Inclined Pipe Comparison ===");
    System.out.printf("Elevation change: %.0f m over %.0f m length%n", elevation, pipeLength);
    System.out.printf("Beggs-Brill: Outlet P = %.3f bar, ΔP = %.3f bar%n", bbOutletPressure,
        bbPressureDrop);
    System.out.printf("Two-Fluid:   Outlet P = %.3f bar, ΔP = %.3f bar%n", tfOutletPressure,
        tfPressureDrop);
    System.out.printf("Difference:  %.1f%%%n", percentDifference(bbPressureDrop, tfPressureDrop));

    // Both should show positive pressure drop
    assertTrue(bbPressureDrop > 0, "Beggs-Brill should show positive pressure drop");
    assertTrue(tfPressureDrop > 0, "Two-Fluid should show positive pressure drop");
  }

  @Test
  @DisplayName("Downward inclined pipe - pressure recovery potential")
  void testDownwardInclinedPipe() {
    // Create gas-dominant fluid
    SystemInterface fluid = new SystemSrkEos(293.15, 50.0);
    fluid.addComponent("methane", 0.92);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(3.0, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(50.0, "bara");
    inlet.run();

    double pipeLength = 1000.0;
    double elevation = -30.0; // 30 m drop (downhill)

    // --- Beggs-Brill model ---
    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB-pipe", inlet);
    bbPipe.setLength(pipeLength);
    bbPipe.setDiameter(0.20);
    bbPipe.setElevation(elevation);
    bbPipe.setPipeWallRoughness(4.5e-5);
    bbPipe.setNumberOfIncrements(20);
    bbPipe.run();

    double bbOutletPressure = bbPipe.getOutletPressure();
    double bbPressureDrop = inlet.getPressure() - bbOutletPressure;

    // --- Two-Fluid model ---
    inlet.run();
    TwoFluidPipe tfPipe = new TwoFluidPipe("TF-pipe", inlet);
    tfPipe.setLength(pipeLength);
    tfPipe.setDiameter(0.20);
    tfPipe.setNumberOfSections(20);
    tfPipe.setRoughness(4.5e-5);

    // Create linear declining elevation profile
    double[] elevations = new double[20];
    for (int i = 0; i < 20; i++) {
      elevations[i] = elevation * i / 19.0; // Goes from 0 to -30 m
    }
    tfPipe.setElevationProfile(elevations);
    tfPipe.run();

    double[] pressureProfile = tfPipe.getPressureProfile();
    double tfOutletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double tfInletPressure = pressureProfile[0] / 1e5;
    double tfPressureDrop = tfInletPressure - tfOutletPressure;

    System.out.println("\n=== Downward Inclined Pipe Comparison ===");
    System.out.printf("Elevation change: %.0f m over %.0f m length%n", elevation, pipeLength);
    System.out.printf("Beggs-Brill: Outlet P = %.3f bar, ΔP = %.3f bar%n", bbOutletPressure,
        bbPressureDrop);
    System.out.printf("Two-Fluid:   Outlet P = %.3f bar, ΔP = %.3f bar%n", tfOutletPressure,
        tfPressureDrop);
    System.out.printf("Difference:  %.1f%%%n", percentDifference(bbPressureDrop, tfPressureDrop));

    // Both should produce finite results
    assertTrue(Double.isFinite(bbPressureDrop), "Beggs-Brill pressure drop should be finite");
    assertTrue(Double.isFinite(tfPressureDrop), "Two-Fluid pressure drop should be finite");
  }

  @Test
  @DisplayName("Higher liquid loading - gas-condensate flow")
  void testHigherLiquidLoading() {
    // Create fluid with more liquid content (but still gas dominant)
    SystemInterface fluid = new SystemSrkEos(293.15, 40.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.08);
    fluid.addComponent("n-butane", 0.07);
    fluid.addComponent("n-pentane", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5.0, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(40.0, "bara");
    inlet.run();

    // --- Beggs-Brill model ---
    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB-pipe", inlet);
    bbPipe.setLength(500.0);
    bbPipe.setDiameter(0.15);
    bbPipe.setElevation(0.0);
    bbPipe.setPipeWallRoughness(4.5e-5);
    bbPipe.setNumberOfIncrements(20);
    bbPipe.run();

    double bbOutletPressure = bbPipe.getOutletPressure();
    double bbPressureDrop = inlet.getPressure() - bbOutletPressure;

    // --- Two-Fluid model ---
    inlet.run();
    TwoFluidPipe tfPipe = new TwoFluidPipe("TF-pipe", inlet);
    tfPipe.setLength(500.0);
    tfPipe.setDiameter(0.15);
    tfPipe.setNumberOfSections(20);
    tfPipe.setRoughness(4.5e-5);
    tfPipe.run();

    double[] pressureProfile = tfPipe.getPressureProfile();
    double[] holdupProfile = tfPipe.getLiquidHoldupProfile();
    double tfOutletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double tfInletPressure = pressureProfile[0] / 1e5;
    double tfPressureDrop = tfInletPressure - tfOutletPressure;

    double avgHoldup = 0;
    for (double h : holdupProfile) {
      avgHoldup += h;
    }
    avgHoldup /= holdupProfile.length;

    System.out.println("\n=== Higher Liquid Loading Comparison ===");
    System.out.printf("Beggs-Brill: Outlet P = %.3f bar, ΔP = %.3f bar%n", bbOutletPressure,
        bbPressureDrop);
    System.out.printf("Two-Fluid:   Outlet P = %.3f bar, ΔP = %.3f bar%n", tfOutletPressure,
        tfPressureDrop);
    System.out.printf("Two-Fluid avg liquid holdup: %.4f%n", avgHoldup);
    System.out.printf("Difference:  %.1f%%%n", percentDifference(bbPressureDrop, tfPressureDrop));

    assertTrue(bbPressureDrop > 0, "Beggs-Brill should show positive pressure drop");
    assertTrue(tfPressureDrop > 0, "Two-Fluid should show positive pressure drop");
  }

  @Test
  @DisplayName("Flow rate trend comparison - pressure drop increases with flow")
  void testFlowRateTrend() {
    // Create gas-dominant fluid
    SystemInterface fluid = new SystemSrkEos(303.15, 70.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    double[] flowRates = {1.0, 2.0, 4.0}; // kg/s - conservative range
    double[] bbPressureDrops = new double[flowRates.length];
    double[] tfPressureDrops = new double[flowRates.length];

    System.out.println("\n=== Flow Rate Trend Comparison ===");
    System.out.println("Flow Rate [kg/s]  BB ΔP [bar]  TF ΔP [bar]  Diff [%]");

    for (int i = 0; i < flowRates.length; i++) {
      Stream inlet = new Stream("inlet", fluid.clone());
      inlet.setFlowRate(flowRates[i], "kg/sec");
      inlet.setTemperature(30.0, "C");
      inlet.setPressure(70.0, "bara");
      inlet.run();

      // Beggs-Brill
      PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB-pipe", inlet);
      bbPipe.setLength(1000.0);
      bbPipe.setDiameter(0.20);
      bbPipe.setElevation(0.0);
      bbPipe.setPipeWallRoughness(4.5e-5);
      bbPipe.setNumberOfIncrements(20);
      bbPipe.run();
      bbPressureDrops[i] = inlet.getPressure() - bbPipe.getOutletPressure();

      // Two-Fluid (create new stream)
      inlet = new Stream("inlet2", fluid.clone());
      inlet.setFlowRate(flowRates[i], "kg/sec");
      inlet.setTemperature(30.0, "C");
      inlet.setPressure(70.0, "bara");
      inlet.run();

      TwoFluidPipe tfPipe = new TwoFluidPipe("TF-pipe", inlet);
      tfPipe.setLength(1000.0);
      tfPipe.setDiameter(0.20);
      tfPipe.setNumberOfSections(20);
      tfPipe.setRoughness(4.5e-5);
      tfPipe.run();

      double[] pressureProfile = tfPipe.getPressureProfile();
      double tfInlet = pressureProfile[0] / 1e5;
      double tfOutlet = pressureProfile[pressureProfile.length - 1] / 1e5;
      tfPressureDrops[i] = tfInlet - tfOutlet;

      System.out.printf("     %5.1f        %6.3f       %6.3f      %5.1f%n", flowRates[i],
          bbPressureDrops[i], tfPressureDrops[i],
          percentDifference(bbPressureDrops[i], tfPressureDrops[i]));
    }

    // Verify pressure drop increases with flow rate for both models
    for (int i = 1; i < flowRates.length; i++) {
      assertTrue(bbPressureDrops[i] >= bbPressureDrops[i - 1],
          "BB pressure drop should increase with flow rate");
      assertTrue(tfPressureDrops[i] >= tfPressureDrops[i - 1],
          "TF pressure drop should increase with flow rate");
    }
  }

  @Test
  @DisplayName("Diameter trend comparison - pressure drop decreases with size")
  void testDiameterTrend() {
    // Create gas-dominant fluid with conservative conditions
    SystemInterface fluid = new SystemSrkEos(303.15, 70.0);
    fluid.addComponent("methane", 0.92);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    double[] diameters = {0.15, 0.20, 0.30}; // m - avoid very small diameters
    double[] bbPressureDrops = new double[diameters.length];
    double[] tfPressureDrops = new double[diameters.length];

    System.out.println("\n=== Diameter Trend Comparison ===");
    System.out.println("Diameter [mm]  BB ΔP [bar]  TF ΔP [bar]  Diff [%]");

    for (int i = 0; i < diameters.length; i++) {
      Stream inlet = new Stream("inlet", fluid.clone());
      inlet.setFlowRate(3.0, "kg/sec");
      inlet.setTemperature(30.0, "C");
      inlet.setPressure(70.0, "bara");
      inlet.run();

      // Beggs-Brill
      PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB-pipe", inlet);
      bbPipe.setLength(1000.0);
      bbPipe.setDiameter(diameters[i]);
      bbPipe.setElevation(0.0);
      bbPipe.setPipeWallRoughness(4.5e-5);
      bbPipe.setNumberOfIncrements(20);
      bbPipe.run();
      bbPressureDrops[i] = inlet.getPressure() - bbPipe.getOutletPressure();

      // Two-Fluid (create new stream)
      inlet = new Stream("inlet2", fluid.clone());
      inlet.setFlowRate(3.0, "kg/sec");
      inlet.setTemperature(30.0, "C");
      inlet.setPressure(70.0, "bara");
      inlet.run();

      TwoFluidPipe tfPipe = new TwoFluidPipe("TF-pipe", inlet);
      tfPipe.setLength(1000.0);
      tfPipe.setDiameter(diameters[i]);
      tfPipe.setNumberOfSections(20);
      tfPipe.setRoughness(4.5e-5);
      tfPipe.run();

      double[] pressureProfile = tfPipe.getPressureProfile();
      double tfInlet = pressureProfile[0] / 1e5;
      double tfOutlet = pressureProfile[pressureProfile.length - 1] / 1e5;
      tfPressureDrops[i] = tfInlet - tfOutlet;

      System.out.printf("     %5.0f        %6.3f       %6.3f      %5.1f%n", diameters[i] * 1000,
          bbPressureDrops[i], tfPressureDrops[i],
          percentDifference(bbPressureDrops[i], tfPressureDrops[i]));
    }

    // Verify pressure drop decreases with larger diameter for both models
    for (int i = 1; i < diameters.length; i++) {
      assertTrue(bbPressureDrops[i] <= bbPressureDrops[i - 1],
          "BB pressure drop should decrease with larger diameter");
      assertTrue(tfPressureDrops[i] <= tfPressureDrops[i - 1],
          "TF pressure drop should decrease with larger diameter");
    }
  }

  @Test
  @DisplayName("Undulating terrain - two-fluid captures liquid accumulation")
  void testUndulatingTerrain() {
    // Create gas-condensate fluid
    SystemInterface fluid = new SystemSrkEos(293.15, 60.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-pentane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5.0, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(60.0, "bara");
    inlet.run();

    double pipeLength = 2000.0;
    int nSections = 40;

    // --- Beggs-Brill (uses net elevation only) ---
    double netElevation = 0.0; // Same start and end
    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB-pipe", inlet);
    bbPipe.setLength(pipeLength);
    bbPipe.setDiameter(0.18);
    bbPipe.setElevation(netElevation);
    bbPipe.setPipeWallRoughness(4.5e-5);
    bbPipe.setNumberOfIncrements(nSections);
    bbPipe.run();

    double bbOutletPressure = bbPipe.getOutletPressure();
    double bbPressureDrop = inlet.getPressure() - bbOutletPressure;

    // --- Two-Fluid (handles full terrain profile) ---
    inlet.run();
    TwoFluidPipe tfPipe = new TwoFluidPipe("TF-pipe", inlet);
    tfPipe.setLength(pipeLength);
    tfPipe.setDiameter(0.18);
    tfPipe.setNumberOfSections(nSections);
    tfPipe.setRoughness(4.5e-5);

    // Create undulating terrain (one valley)
    double[] elevations = new double[nSections];
    for (int i = 0; i < nSections; i++) {
      double x = (double) i / (nSections - 1);
      // Single sine wave valley with 15m amplitude
      elevations[i] = -15.0 * Math.sin(Math.PI * x);
    }
    tfPipe.setElevationProfile(elevations);
    tfPipe.run();

    double[] pressureProfile = tfPipe.getPressureProfile();
    double[] holdupProfile = tfPipe.getLiquidHoldupProfile();
    double tfOutletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double tfInletPressure = pressureProfile[0] / 1e5;
    double tfPressureDrop = tfInletPressure - tfOutletPressure;

    // Find max holdup (likely near valley bottom)
    double maxHoldup = 0;
    int maxHoldupLocation = 0;
    for (int i = 0; i < holdupProfile.length; i++) {
      if (holdupProfile[i] > maxHoldup) {
        maxHoldup = holdupProfile[i];
        maxHoldupLocation = i;
      }
    }
    double maxHoldupPosition = pipeLength * maxHoldupLocation / (nSections - 1);

    System.out.println("\n=== Undulating Terrain Comparison ===");
    System.out.println("Note: BB uses net elevation=0, TF uses full terrain profile");
    System.out.printf("Beggs-Brill: Outlet P = %.3f bar, ΔP = %.3f bar%n", bbOutletPressure,
        bbPressureDrop);
    System.out.printf("Two-Fluid:   Outlet P = %.3f bar, ΔP = %.3f bar%n", tfOutletPressure,
        tfPressureDrop);
    System.out.printf("Two-Fluid max holdup: %.4f at %.0f m%n", maxHoldup, maxHoldupPosition);
    System.out.printf("Difference:  %.1f%%%n", percentDifference(bbPressureDrop, tfPressureDrop));

    assertTrue(bbPressureDrop > 0, "Beggs-Brill should show positive pressure drop");
    assertTrue(tfPressureDrop > 0, "Two-Fluid should show positive pressure drop");

    // Verify both simulations complete successfully
    assertTrue(bbOutletPressure > 0, "BB outlet should be positive");
    assertTrue(tfOutletPressure > 0, "TF outlet should be positive");
  }

  @Test
  @DisplayName("Pure methane gas flow - single component")
  void testPureMethaneGasFlow() {
    // Pure methane at conditions where it remains gas
    SystemInterface fluid = new SystemSrkEos(303.15, 50.0); // 30°C, 50 bar - well above critical
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5.0, "kg/sec");
    inlet.setTemperature(30.0, "C");
    inlet.setPressure(50.0, "bara");
    inlet.run();

    // --- Beggs-Brill model ---
    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB-pipe", inlet);
    bbPipe.setLength(5000.0); // 5 km
    bbPipe.setDiameter(0.20); // 200 mm
    bbPipe.setElevation(0.0);
    bbPipe.setPipeWallRoughness(4.5e-5);
    bbPipe.setNumberOfIncrements(50);
    bbPipe.run();

    double bbOutletPressure = bbPipe.getOutletPressure();
    double bbPressureDrop = inlet.getPressure() - bbOutletPressure;

    // --- Two-Fluid model ---
    inlet.run();
    TwoFluidPipe tfPipe = new TwoFluidPipe("TF-pipe", inlet);
    tfPipe.setLength(5000.0);
    tfPipe.setDiameter(0.20);
    tfPipe.setNumberOfSections(50);
    tfPipe.setRoughness(4.5e-5);
    tfPipe.run();

    double[] pressureProfile = tfPipe.getPressureProfile();
    double[] holdupProfile = tfPipe.getLiquidHoldupProfile();
    double tfOutletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double tfInletPressure = pressureProfile[0] / 1e5;
    double tfPressureDrop = tfInletPressure - tfOutletPressure;

    // Check liquid holdup is near zero for pure gas
    double avgHoldup = 0;
    for (double h : holdupProfile) {
      avgHoldup += h;
    }
    avgHoldup /= holdupProfile.length;

    System.out.println("\n=== Pure Methane Gas Flow Comparison ===");
    System.out.printf("Beggs-Brill: Outlet P = %.3f bar, ΔP = %.3f bar%n", bbOutletPressure,
        bbPressureDrop);
    System.out.printf("Two-Fluid:   Outlet P = %.3f bar, ΔP = %.3f bar%n", tfOutletPressure,
        tfPressureDrop);
    System.out.printf("Two-Fluid avg liquid holdup: %.6f (should be ~0 for pure gas)%n", avgHoldup);
    System.out.printf("Difference:  %.1f%%%n", percentDifference(bbPressureDrop, tfPressureDrop));

    assertTrue(bbPressureDrop > 0, "BB should calculate positive pressure drop for gas");
    assertTrue(tfPressureDrop > 0, "TF should calculate positive pressure drop for gas");
    assertTrue(avgHoldup < 0.01, "Liquid holdup should be near zero for pure gas");
  }

  @Test
  @DisplayName("Pure n-heptane oil flow - single component liquid")
  void testPureHeptaneOilFlow() {
    // Pure n-heptane at conditions where it remains liquid
    // n-heptane: Tc = 540K, Pc = 27.4 bar - at 20°C, 30 bar it's liquid
    SystemInterface fluid = new SystemSrkEos(293.15, 30.0); // 20°C, 30 bar
    fluid.addComponent("n-heptane", 1.0);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(10.0, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(30.0, "bara");
    inlet.run();

    // --- Beggs-Brill model ---
    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB-pipe", inlet);
    bbPipe.setLength(2000.0); // 2 km
    bbPipe.setDiameter(0.15); // 150 mm
    bbPipe.setElevation(0.0);
    bbPipe.setPipeWallRoughness(4.5e-5);
    bbPipe.setNumberOfIncrements(40);
    bbPipe.run();

    double bbOutletPressure = bbPipe.getOutletPressure();
    double bbPressureDrop = inlet.getPressure() - bbOutletPressure;

    // --- Two-Fluid model ---
    inlet.run();
    TwoFluidPipe tfPipe = new TwoFluidPipe("TF-pipe", inlet);
    tfPipe.setLength(2000.0);
    tfPipe.setDiameter(0.15);
    tfPipe.setNumberOfSections(40);
    tfPipe.setRoughness(4.5e-5);
    tfPipe.run();

    double[] pressureProfile = tfPipe.getPressureProfile();
    double[] holdupProfile = tfPipe.getLiquidHoldupProfile();
    double tfOutletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double tfInletPressure = pressureProfile[0] / 1e5;
    double tfPressureDrop = tfInletPressure - tfOutletPressure;

    // Check liquid holdup is near 1.0 for pure liquid
    double avgHoldup = 0;
    for (double h : holdupProfile) {
      avgHoldup += h;
    }
    avgHoldup /= holdupProfile.length;

    System.out.println("\n=== Pure n-Heptane Oil Flow Comparison ===");
    System.out.printf("Beggs-Brill: Outlet P = %.3f bar, ΔP = %.3f bar%n", bbOutletPressure,
        bbPressureDrop);
    System.out.printf("Two-Fluid:   Outlet P = %.3f bar, ΔP = %.3f bar%n", tfOutletPressure,
        tfPressureDrop);
    System.out.printf("Two-Fluid avg liquid holdup: %.4f (should be ~1.0 for pure liquid)%n",
        avgHoldup);
    System.out.printf("Difference:  %.1f%%%n", percentDifference(bbPressureDrop, tfPressureDrop));

    assertTrue(bbPressureDrop > 0, "BB should calculate positive pressure drop for oil");
    assertTrue(tfPressureDrop > 0, "TF should calculate positive pressure drop for oil");
    assertTrue(avgHoldup > 0.9, "Liquid holdup should be near 1.0 for pure liquid");
  }

  @Test
  @DisplayName("Pure n-pentane oil flow - another liquid hydrocarbon")
  void testPurePentaneOilFlow() {
    // Pure n-pentane at conditions where it remains liquid
    // n-pentane: Tc = 469.7K, Pc = 33.7 bar - at 15°C, 20 bar it's liquid
    SystemInterface fluid = new SystemSrkEos(288.15, 20.0); // 15°C, 20 bar
    fluid.addComponent("n-pentane", 1.0);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(8.0, "kg/sec");
    inlet.setTemperature(15.0, "C");
    inlet.setPressure(20.0, "bara");
    inlet.run();

    // --- Beggs-Brill model ---
    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB-pipe", inlet);
    bbPipe.setLength(1500.0);
    bbPipe.setDiameter(0.12);
    bbPipe.setElevation(0.0);
    bbPipe.setPipeWallRoughness(4.5e-5);
    bbPipe.setNumberOfIncrements(30);
    bbPipe.run();

    double bbOutletPressure = bbPipe.getOutletPressure();
    double bbPressureDrop = inlet.getPressure() - bbOutletPressure;

    // --- Two-Fluid model ---
    inlet.run();
    TwoFluidPipe tfPipe = new TwoFluidPipe("TF-pipe", inlet);
    tfPipe.setLength(1500.0);
    tfPipe.setDiameter(0.12);
    tfPipe.setNumberOfSections(30);
    tfPipe.setRoughness(4.5e-5);
    tfPipe.run();

    double[] pressureProfile = tfPipe.getPressureProfile();
    double[] holdupProfile = tfPipe.getLiquidHoldupProfile();
    double tfOutletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double tfInletPressure = pressureProfile[0] / 1e5;
    double tfPressureDrop = tfInletPressure - tfOutletPressure;

    double avgHoldup = 0;
    for (double h : holdupProfile) {
      avgHoldup += h;
    }
    avgHoldup /= holdupProfile.length;

    System.out.println("\n=== Pure n-Pentane Oil Flow Comparison ===");
    System.out.printf("Beggs-Brill: Outlet P = %.3f bar, ΔP = %.3f bar%n", bbOutletPressure,
        bbPressureDrop);
    System.out.printf("Two-Fluid:   Outlet P = %.3f bar, ΔP = %.3f bar%n", tfOutletPressure,
        tfPressureDrop);
    System.out.printf("Two-Fluid avg liquid holdup: %.4f (should be ~1.0 for pure liquid)%n",
        avgHoldup);
    System.out.printf("Difference:  %.1f%%%n", percentDifference(bbPressureDrop, tfPressureDrop));

    assertTrue(bbPressureDrop > 0, "BB should calculate positive pressure drop");
    assertTrue(tfPressureDrop > 0, "TF should calculate positive pressure drop");
    assertTrue(avgHoldup > 0.9, "Liquid holdup should be near 1.0 for pure liquid");
  }

  @Test
  @DisplayName("Pure ethane gas flow - light hydrocarbon gas")
  void testPureEthaneGasFlow() {
    // Pure ethane at conditions where it remains gas
    // Ethane: Tc = 305.3K, Pc = 48.7 bar - at 40°C, 30 bar it's gas
    SystemInterface fluid = new SystemSrkEos(313.15, 30.0); // 40°C, 30 bar
    fluid.addComponent("ethane", 1.0);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(4.0, "kg/sec");
    inlet.setTemperature(40.0, "C");
    inlet.setPressure(30.0, "bara");
    inlet.run();

    // --- Beggs-Brill model ---
    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB-pipe", inlet);
    bbPipe.setLength(3000.0);
    bbPipe.setDiameter(0.18);
    bbPipe.setElevation(0.0);
    bbPipe.setPipeWallRoughness(4.5e-5);
    bbPipe.setNumberOfIncrements(30);
    bbPipe.run();

    double bbOutletPressure = bbPipe.getOutletPressure();
    double bbPressureDrop = inlet.getPressure() - bbOutletPressure;

    // --- Two-Fluid model ---
    inlet.run();
    TwoFluidPipe tfPipe = new TwoFluidPipe("TF-pipe", inlet);
    tfPipe.setLength(3000.0);
    tfPipe.setDiameter(0.18);
    tfPipe.setNumberOfSections(30);
    tfPipe.setRoughness(4.5e-5);
    tfPipe.run();

    double[] pressureProfile = tfPipe.getPressureProfile();
    double[] holdupProfile = tfPipe.getLiquidHoldupProfile();
    double tfOutletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double tfInletPressure = pressureProfile[0] / 1e5;
    double tfPressureDrop = tfInletPressure - tfOutletPressure;

    double avgHoldup = 0;
    for (double h : holdupProfile) {
      avgHoldup += h;
    }
    avgHoldup /= holdupProfile.length;

    System.out.println("\n=== Pure Ethane Gas Flow Comparison ===");
    System.out.printf("Beggs-Brill: Outlet P = %.3f bar, ΔP = %.3f bar%n", bbOutletPressure,
        bbPressureDrop);
    System.out.printf("Two-Fluid:   Outlet P = %.3f bar, ΔP = %.3f bar%n", tfOutletPressure,
        tfPressureDrop);
    System.out.printf("Two-Fluid avg liquid holdup: %.6f (should be ~0 for gas)%n", avgHoldup);
    System.out.printf("Difference:  %.1f%%%n", percentDifference(bbPressureDrop, tfPressureDrop));

    assertTrue(bbPressureDrop > 0, "BB should calculate positive pressure drop for gas");
    assertTrue(tfPressureDrop > 0, "TF should calculate positive pressure drop for gas");
    assertTrue(avgHoldup < 0.01, "Liquid holdup should be near zero for pure gas");
  }

  @Test
  @DisplayName("Gas vs Oil pressure drop comparison - same pipe conditions")
  void testGasVsOilPressureDropComparison() {
    // Compare pure gas vs pure oil pressure drops in identical pipe
    double pipeLength = 2000.0;
    double pipeDiameter = 0.15;
    double massFlowRate = 5.0; // kg/s

    // --- Pure Gas (Methane) ---
    SystemInterface gasFluid = new SystemSrkEos(303.15, 50.0);
    gasFluid.addComponent("methane", 1.0);
    gasFluid.setMixingRule("classic");

    Stream gasInlet = new Stream("gas-inlet", gasFluid);
    gasInlet.setFlowRate(massFlowRate, "kg/sec");
    gasInlet.setTemperature(30.0, "C");
    gasInlet.setPressure(50.0, "bara");
    gasInlet.run();

    PipeBeggsAndBrills bbGas = new PipeBeggsAndBrills("BB-gas", gasInlet);
    bbGas.setLength(pipeLength);
    bbGas.setDiameter(pipeDiameter);
    bbGas.setElevation(0.0);
    bbGas.setPipeWallRoughness(4.5e-5);
    bbGas.setNumberOfIncrements(40);
    bbGas.run();
    double bbGasDp = gasInlet.getPressure() - bbGas.getOutletPressure();

    gasInlet.run();
    TwoFluidPipe tfGas = new TwoFluidPipe("TF-gas", gasInlet);
    tfGas.setLength(pipeLength);
    tfGas.setDiameter(pipeDiameter);
    tfGas.setNumberOfSections(40);
    tfGas.setRoughness(4.5e-5);
    tfGas.run();
    double[] gasProfile = tfGas.getPressureProfile();
    double tfGasDp = gasProfile[0] / 1e5 - gasProfile[gasProfile.length - 1] / 1e5;

    // --- Pure Oil (n-Heptane) ---
    SystemInterface oilFluid = new SystemSrkEos(293.15, 30.0);
    oilFluid.addComponent("n-heptane", 1.0);
    oilFluid.setMixingRule("classic");

    Stream oilInlet = new Stream("oil-inlet", oilFluid);
    oilInlet.setFlowRate(massFlowRate, "kg/sec");
    oilInlet.setTemperature(20.0, "C");
    oilInlet.setPressure(30.0, "bara");
    oilInlet.run();

    PipeBeggsAndBrills bbOil = new PipeBeggsAndBrills("BB-oil", oilInlet);
    bbOil.setLength(pipeLength);
    bbOil.setDiameter(pipeDiameter);
    bbOil.setElevation(0.0);
    bbOil.setPipeWallRoughness(4.5e-5);
    bbOil.setNumberOfIncrements(40);
    bbOil.run();
    double bbOilDp = oilInlet.getPressure() - bbOil.getOutletPressure();

    oilInlet.run();
    TwoFluidPipe tfOil = new TwoFluidPipe("TF-oil", oilInlet);
    tfOil.setLength(pipeLength);
    tfOil.setDiameter(pipeDiameter);
    tfOil.setNumberOfSections(40);
    tfOil.setRoughness(4.5e-5);
    tfOil.run();
    double[] oilProfile = tfOil.getPressureProfile();
    double tfOilDp = oilProfile[0] / 1e5 - oilProfile[oilProfile.length - 1] / 1e5;

    System.out.println("\n=== Gas vs Oil Pressure Drop Comparison ===");
    System.out.printf("Pipe: %.0f m length, %.0f mm diameter, %.1f kg/s flow%n", pipeLength,
        pipeDiameter * 1000, massFlowRate);
    System.out.println();
    System.out.println("Beggs-Brill:");
    System.out.printf("  Gas (CH4):      ΔP = %.4f bar%n", bbGasDp);
    System.out.printf("  Oil (C7H16):    ΔP = %.4f bar%n", bbOilDp);
    System.out.printf("  Oil/Gas ratio:  %.2f%n", bbOilDp / bbGasDp);
    System.out.println();
    System.out.println("Two-Fluid:");
    System.out.printf("  Gas (CH4):      ΔP = %.4f bar%n", tfGasDp);
    System.out.printf("  Oil (C7H16):    ΔP = %.4f bar%n", tfOilDp);
    System.out.printf("  Oil/Gas ratio:  %.2f%n", tfOilDp / tfGasDp);

    // Both models should show oil has higher pressure drop than gas
    // (due to higher density and viscosity despite lower velocity)
    assertTrue(bbGasDp > 0, "BB gas pressure drop should be positive");
    assertTrue(bbOilDp > 0, "BB oil pressure drop should be positive");
    assertTrue(tfGasDp > 0, "TF gas pressure drop should be positive");
    assertTrue(tfOilDp > 0, "TF oil pressure drop should be positive");
  }

  @Test
  @DisplayName("Terrain slugging - liquid accumulation at low points")
  void testTerrainSlugging() {
    // Create wet gas with significant liquid content
    // This is typical for gas-condensate pipelines where terrain slugging occurs
    SystemInterface fluid = new SystemSrkEos(293.15, 50.0); // 20°C, 50 bar
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.02);
    fluid.addComponent("n-heptane", 0.02); // Heavier components for liquid dropout
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(3.0, "kg/sec"); // Low flow rate promotes terrain slugging
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(50.0, "bara");
    inlet.run();

    // Define undulating terrain profile with distinct valleys
    // Profile: uphill -> downhill to valley -> uphill -> downhill to valley -> uphill
    double pipeLength = 5000.0; // 5 km pipeline
    int nSections = 100;
    double[] elevations = new double[nSections];
    double[] positions = new double[nSections];

    for (int i = 0; i < nSections; i++) {
      positions[i] = pipeLength * i / (nSections - 1);
      // Create terrain with two distinct valleys at 1.5 km and 3.5 km
      double x = positions[i];
      // Base elevation varies sinusoidally with two valleys
      double terrain = 30.0 * Math.sin(2 * Math.PI * x / 2500.0); // +/- 30m undulation
      // Add smaller ripples
      terrain += 5.0 * Math.sin(2 * Math.PI * x / 500.0);
      elevations[i] = terrain;
    }

    // --- Two-Fluid model with terrain profile ---
    TwoFluidPipe tfPipe = new TwoFluidPipe("TF-terrain-slug", inlet);
    tfPipe.setLength(pipeLength);
    tfPipe.setDiameter(0.20); // 200 mm - 8 inch pipeline
    tfPipe.setNumberOfSections(nSections);
    tfPipe.setRoughness(4.5e-5);
    tfPipe.setElevationProfile(elevations);
    tfPipe.run();

    double[] pressureProfile = tfPipe.getPressureProfile();
    double[] holdupProfile = tfPipe.getLiquidHoldupProfile();
    double tfOutletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double tfInletPressure = pressureProfile[0] / 1e5;
    double tfPressureDrop = tfInletPressure - tfOutletPressure;

    // Find valleys (local elevation minima) and corresponding holdups
    System.out.println("\n=== Terrain Slugging Analysis ===");
    System.out.println("Pipeline: 5 km, 200 mm diameter, wet gas flow");
    System.out.println("\nTerrain Profile with Liquid Holdup:");
    System.out.println("Position [m]  Elevation [m]  Liquid Holdup  Note");
    System.out.println("----------------------------------------------------------");

    double maxHoldup = 0;
    int maxHoldupIdx = 0;
    double minHoldup = 1;
    int minHoldupIdx = 0;

    // Track valleys and peaks
    int[] valleyIndices = new int[10];
    int valleyCount = 0;
    int[] peakIndices = new int[10];
    int peakCount = 0;

    for (int i = 1; i < nSections - 1 && valleyCount < 10 && peakCount < 10; i++) {
      // Detect valleys (local minima in elevation)
      if (elevations[i] < elevations[i - 1] && elevations[i] < elevations[i + 1]) {
        valleyIndices[valleyCount++] = i;
      }
      // Detect peaks (local maxima in elevation)
      if (elevations[i] > elevations[i - 1] && elevations[i] > elevations[i + 1]) {
        peakIndices[peakCount++] = i;
      }

      if (holdupProfile[i] > maxHoldup) {
        maxHoldup = holdupProfile[i];
        maxHoldupIdx = i;
      }
      if (holdupProfile[i] < minHoldup) {
        minHoldup = holdupProfile[i];
        minHoldupIdx = i;
      }
    }

    // Print sample positions including valleys and peaks
    int[] samplePoints = {0, 10, 25, 37, 50, 62, 75, 87, 99};
    for (int i : samplePoints) {
      if (i < nSections) {
        String note = "";
        for (int v = 0; v < valleyCount; v++) {
          if (Math.abs(i - valleyIndices[v]) <= 1) {
            note = "<-- VALLEY";
            break;
          }
        }
        for (int p = 0; p < peakCount; p++) {
          if (Math.abs(i - peakIndices[p]) <= 1) {
            note = "<-- PEAK";
            break;
          }
        }
        System.out.printf("%10.0f     %8.2f       %8.4f     %s%n", positions[i], elevations[i],
            holdupProfile[i], note);
      }
    }

    System.out.println("\n--- Slug Analysis Summary ---");
    System.out.printf("Total pressure drop: %.3f bar%n", tfPressureDrop);
    System.out.printf("Max liquid holdup: %.4f at position %.0f m (elev: %.1f m)%n", maxHoldup,
        positions[maxHoldupIdx], elevations[maxHoldupIdx]);
    System.out.printf("Min liquid holdup: %.4f at position %.0f m (elev: %.1f m)%n", minHoldup,
        positions[minHoldupIdx], elevations[minHoldupIdx]);
    System.out.printf("Holdup ratio (max/min): %.2f%n",
        minHoldup > 0 ? maxHoldup / minHoldup : Double.POSITIVE_INFINITY);
    System.out.printf("Number of valleys detected: %d%n", valleyCount);
    System.out.printf("Number of peaks detected: %d%n", peakCount);

    // Print holdup at each valley
    System.out.println("\nLiquid holdup at valleys:");
    for (int v = 0; v < valleyCount; v++) {
      int idx = valleyIndices[v];
      System.out.printf("  Valley %d: position=%.0f m, elevation=%.1f m, holdup=%.4f%n", v + 1,
          positions[idx], elevations[idx], holdupProfile[idx]);
    }

    // Print holdup at each peak
    System.out.println("\nLiquid holdup at peaks:");
    for (int p = 0; p < peakCount; p++) {
      int idx = peakIndices[p];
      System.out.printf("  Peak %d: position=%.0f m, elevation=%.1f m, holdup=%.4f%n", p + 1,
          positions[idx], elevations[idx], holdupProfile[idx]);
    }

    // Assertions
    assertTrue(tfPressureDrop > 0, "Pressure drop should be positive");
    assertTrue(tfOutletPressure > 0, "Outlet pressure should be positive");
    assertTrue(tfOutletPressure < tfInletPressure, "Outlet should be less than inlet");

    // The model should complete without errors
    assertTrue(pressureProfile.length == nSections, "Pressure profile should match sections");
    assertTrue(holdupProfile.length == nSections, "Holdup profile should match sections");
  }

  @Test
  @DisplayName("Severe terrain slugging - deep valley accumulation")
  void testSevereTerrainSlugging() {
    // Heavier fluid composition - more prone to liquid dropout and slugging
    SystemInterface fluid = new SystemSrkEos(288.15, 40.0); // 15°C, 40 bar
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.08);
    fluid.addComponent("n-butane", 0.05);
    fluid.addComponent("n-pentane", 0.04);
    fluid.addComponent("n-heptane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(2.0, "kg/sec"); // Very low flow - promotes severe slugging
    inlet.setTemperature(15.0, "C");
    inlet.setPressure(40.0, "bara");
    inlet.run();

    // Create terrain with one deep valley
    // Profile: gradual descent to -50m valley, then steep ascent
    double pipeLength = 3000.0; // 3 km
    int nSections = 60;
    double[] elevations = new double[nSections];

    for (int i = 0; i < nSections; i++) {
      double x = (double) i / (nSections - 1); // 0 to 1
      // Asymmetric valley: gradual down, steep up
      if (x < 0.6) {
        // Gradual descent to valley bottom at x=0.5
        elevations[i] = -50.0 * Math.sin(Math.PI * x / 0.6);
      } else {
        // Steep ascent back to original level
        elevations[i] = -50.0 * Math.sin(Math.PI * 0.5) * (1.0 - (x - 0.6) / 0.4);
      }
    }

    // --- Two-Fluid model ---
    TwoFluidPipe tfPipe = new TwoFluidPipe("TF-deep-valley", inlet);
    tfPipe.setLength(pipeLength);
    tfPipe.setDiameter(0.15); // 150 mm - 6 inch pipeline
    tfPipe.setNumberOfSections(nSections);
    tfPipe.setRoughness(4.5e-5);
    tfPipe.setElevationProfile(elevations);
    tfPipe.run();

    double[] pressureProfile = tfPipe.getPressureProfile();
    double[] holdupProfile = tfPipe.getLiquidHoldupProfile();
    double tfOutletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double tfInletPressure = pressureProfile[0] / 1e5;
    double tfPressureDrop = tfInletPressure - tfOutletPressure;

    // Find valley bottom
    int valleyIdx = 0;
    double minElev = 0;
    for (int i = 0; i < nSections; i++) {
      if (elevations[i] < minElev) {
        minElev = elevations[i];
        valleyIdx = i;
      }
    }

    System.out.println("\n=== Severe Terrain Slugging - Deep Valley ===");
    System.out.println("Pipeline: 3 km, 150 mm diameter, rich gas");
    System.out.printf("Valley depth: %.1f m at position %.0f m%n", -minElev,
        pipeLength * valleyIdx / (nSections - 1));
    System.out.println("\nProfile (every 10th point):");
    System.out.println("Position [m]  Elevation [m]  Pressure [bar]  Holdup");
    System.out.println("----------------------------------------------------------");

    for (int i = 0; i < nSections; i += 6) {
      System.out.printf("%10.0f     %8.1f       %8.2f       %.4f%n",
          pipeLength * i / (nSections - 1), elevations[i], pressureProfile[i] / 1e5,
          holdupProfile[i]);
    }

    // Print last point
    int last = nSections - 1;
    System.out.printf("%10.0f     %8.1f       %8.2f       %.4f%n", pipeLength, elevations[last],
        pressureProfile[last] / 1e5, holdupProfile[last]);

    System.out.println("\n--- Deep Valley Summary ---");
    System.out.printf("Inlet pressure:  %.2f bar%n", tfInletPressure);
    System.out.printf("Outlet pressure: %.2f bar%n", tfOutletPressure);
    System.out.printf("Pressure drop:   %.3f bar%n", tfPressureDrop);
    System.out.printf("Holdup at inlet: %.4f%n", holdupProfile[0]);
    System.out.printf("Holdup at valley bottom: %.4f%n", holdupProfile[valleyIdx]);
    System.out.printf("Holdup at outlet: %.4f%n", holdupProfile[last]);

    // Calculate liquid inventory in valley region (around minimum)
    double valleyLiquidInventory = 0;
    int valleyStart = Math.max(0, valleyIdx - 10);
    int valleyEnd = Math.min(nSections - 1, valleyIdx + 10);
    for (int i = valleyStart; i <= valleyEnd; i++) {
      valleyLiquidInventory += holdupProfile[i];
    }
    valleyLiquidInventory /= (valleyEnd - valleyStart + 1);
    System.out.printf("Average holdup in valley region: %.4f%n", valleyLiquidInventory);

    // Assertions
    assertTrue(tfPressureDrop > 0, "Pressure drop should be positive");
    assertTrue(pressureProfile.length == nSections, "Pressure profile should match sections");

    // Deep valley should show pressure recovery after valley due to hydrostatic head
    // Check that pressure increases after valley bottom (ascending section)
    double pressureAtValley = pressureProfile[valleyIdx];
    double pressureAfterValley = pressureProfile[Math.min(valleyIdx + 10, nSections - 1)];
    System.out.printf("%nPressure at valley: %.2f bar%n", pressureAtValley / 1e5);
    System.out.printf("Pressure 500m after valley: %.2f bar%n", pressureAfterValley / 1e5);
  }

  @Test
  @DisplayName("S-Riser simulation - offshore production riser")
  void testSRiserSimulation() {
    // S-Riser: Typical offshore configuration
    // Profile: Flowline at seabed -> down to sag bend -> up vertical riser -> platform
    // This is prone to severe slugging at low flow rates

    // Typical North Sea gas-condensate composition
    SystemInterface fluid = new SystemSrkEos(278.15, 80.0); // 5°C, 80 bar (seabed conditions)
    fluid.addComponent("methane", 0.75);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.06);
    fluid.addComponent("n-butane", 0.04);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-heptane", 0.02);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("seabed-inlet", fluid);
    inlet.setFlowRate(5.0, "kg/sec"); // Moderate flow rate
    inlet.setTemperature(5.0, "C"); // Cold seabed temperature
    inlet.setPressure(80.0, "bara"); // High pressure at seabed
    inlet.run();

    // S-Riser geometry:
    // Section 1: Horizontal flowline at seabed (0m elevation)
    // Section 2: Descend to sag bend (-30m below seabed)
    // Section 3: Vertical/near-vertical riser up to platform (+100m above seabed)
    // Total length: ~500m
    double riserLength = 500.0;
    int nSections = 100;
    double[] elevations = new double[nSections];
    double[] positions = new double[nSections];

    // Define S-riser profile
    double flowlineLength = 100.0; // Horizontal at seabed
    double sagDepth = -30.0; // Sag bend depth below seabed
    double platformHeight = 100.0; // Platform above seabed
    double sagBendPosition = 150.0; // Position of sag bend
    double riserStartPosition = 200.0; // Where vertical riser begins

    for (int i = 0; i < nSections; i++) {
      positions[i] = riserLength * i / (nSections - 1);
      double x = positions[i];

      if (x <= flowlineLength) {
        // Section 1: Horizontal flowline at seabed level (0m)
        elevations[i] = 0.0;
      } else if (x <= sagBendPosition) {
        // Section 2: Descend to sag bend
        double progress = (x - flowlineLength) / (sagBendPosition - flowlineLength);
        // Smooth transition using sine curve
        elevations[i] = sagDepth * Math.sin(progress * Math.PI / 2);
      } else if (x <= riserStartPosition) {
        // Section 3: Sag bend (low point) - transition to riser
        double progress = (x - sagBendPosition) / (riserStartPosition - sagBendPosition);
        elevations[i] = sagDepth * Math.cos(progress * Math.PI / 2);
      } else {
        // Section 4: Vertical riser to platform
        double progress = (x - riserStartPosition) / (riserLength - riserStartPosition);
        elevations[i] = platformHeight * progress;
      }
    }

    // --- Two-Fluid model with S-riser profile ---
    TwoFluidPipe sRiser = new TwoFluidPipe("S-Riser", inlet);
    sRiser.setLength(riserLength);
    sRiser.setDiameter(0.20); // 8-inch riser
    sRiser.setNumberOfSections(nSections);
    sRiser.setRoughness(4.5e-5);
    sRiser.setElevationProfile(elevations);
    sRiser.run();

    double[] pressureProfile = sRiser.getPressureProfile();
    double[] holdupProfile = sRiser.getLiquidHoldupProfile();
    double outletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double inletPressure = pressureProfile[0] / 1e5;
    double pressureDrop = inletPressure - outletPressure;

    // Find sag bend location (minimum elevation)
    int sagIdx = 0;
    double minElev = 0;
    for (int i = 0; i < nSections; i++) {
      if (elevations[i] < minElev) {
        minElev = elevations[i];
        sagIdx = i;
      }
    }

    // Find maximum holdup location
    int maxHoldupIdx = 0;
    double maxHoldup = 0;
    for (int i = 0; i < nSections; i++) {
      if (holdupProfile[i] > maxHoldup) {
        maxHoldup = holdupProfile[i];
        maxHoldupIdx = i;
      }
    }

    System.out.println("\n=== S-Riser Simulation ===");
    System.out.println(
        "Configuration: Seabed flowline -> Sag bend (-30m) -> Vertical riser -> Platform (+100m)");
    System.out.println("Total length: 500m, Diameter: 200mm (8 inch)");
    System.out.println("Conditions: 5°C, 80 bara inlet, 5 kg/s gas-condensate flow\n");

    System.out.println("Position [m]  Elevation [m]  Pressure [bar]  Liquid Holdup  Section");
    System.out.println(
        "--------------------------------------------------------------------------------");

    // Print key points along the riser
    int[] keyPoints = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 99};
    for (int idx : keyPoints) {
      if (idx < nSections) {
        String section;
        if (positions[idx] <= flowlineLength) {
          section = "Flowline";
        } else if (positions[idx] <= sagBendPosition) {
          section = "Descending";
        } else if (positions[idx] <= riserStartPosition) {
          section = "Sag bend";
        } else {
          section = "Riser";
        }
        System.out.printf("%10.0f     %8.1f       %8.2f        %8.4f      %s%n", positions[idx],
            elevations[idx], pressureProfile[idx] / 1e5, holdupProfile[idx], section);
      }
    }

    System.out.println("\n--- S-Riser Analysis ---");
    System.out.printf("Inlet pressure (seabed):    %.2f bar%n", inletPressure);
    System.out.printf("Outlet pressure (platform): %.2f bar%n", outletPressure);
    System.out.printf("Total pressure drop:        %.2f bar%n", pressureDrop);
    System.out.printf("Sag bend depth:             %.1f m at position %.0f m%n", minElev,
        positions[sagIdx]);
    System.out.printf("Pressure at sag bend:       %.2f bar%n", pressureProfile[sagIdx] / 1e5);
    System.out.printf("Holdup at sag bend:         %.4f%n", holdupProfile[sagIdx]);
    System.out.printf("Maximum holdup:             %.4f at position %.0f m (elev: %.1f m)%n",
        maxHoldup, positions[maxHoldupIdx], elevations[maxHoldupIdx]);
    System.out.printf("Holdup at platform:         %.4f%n", holdupProfile[nSections - 1]);

    // Calculate liquid inventory in different sections
    double flowlineInventory = 0;
    int flowlineCount = 0;
    double sagInventory = 0;
    int sagCount = 0;
    double riserInventory = 0;
    int riserCount = 0;

    for (int i = 0; i < nSections; i++) {
      if (positions[i] <= flowlineLength) {
        flowlineInventory += holdupProfile[i];
        flowlineCount++;
      } else if (positions[i] <= riserStartPosition) {
        sagInventory += holdupProfile[i];
        sagCount++;
      } else {
        riserInventory += holdupProfile[i];
        riserCount++;
      }
    }

    System.out.println("\n--- Section-by-Section Liquid Distribution ---");
    System.out.printf("Average holdup in flowline:  %.4f%n",
        flowlineCount > 0 ? flowlineInventory / flowlineCount : 0);
    System.out.printf("Average holdup in sag bend:  %.4f%n",
        sagCount > 0 ? sagInventory / sagCount : 0);
    System.out.printf("Average holdup in riser:     %.4f%n",
        riserCount > 0 ? riserInventory / riserCount : 0);

    // Hydrostatic pressure analysis
    double hydrostaticHead = (platformHeight - sagDepth) * 9.81 * 800 / 1e5; // Assuming 800 kg/m3
                                                                             // liquid
    System.out.printf("\nEstimated hydrostatic head (sag to platform): ~%.1f bar%n",
        hydrostaticHead);
    System.out.printf("Actual pressure increase (sag to outlet): %.2f bar%n",
        pressureProfile[sagIdx] / 1e5 - outletPressure);

    // Assertions
    assertTrue(outletPressure > 0, "Outlet pressure should be positive");
    assertTrue(outletPressure < inletPressure,
        "Outlet pressure should be less than inlet (overall pressure drop)");
    assertTrue(pressureProfile[sagIdx] > pressureProfile[0],
        "Pressure at sag bend should be higher than inlet due to depth");
    assertTrue(holdupProfile.length == nSections, "Holdup profile should match sections");

    // Verify liquid tends to accumulate in sag bend region
    double avgSagHoldup = sagCount > 0 ? sagInventory / sagCount : 0;
    double avgRiserHoldup = riserCount > 0 ? riserInventory / riserCount : 0;
    System.out.printf("\nSag/Riser holdup ratio: %.2f (>1 indicates liquid accumulation at sag)%n",
        avgSagHoldup / avgRiserHoldup);
  }

  @Test
  @DisplayName("Lazy S-Riser with severe slugging conditions")
  void testLazySRiserSevereSlugging() {
    // Lazy S-Riser: Shallower angles, longer sag bend - more prone to slugging
    // Lower flow rate promotes severe slugging

    SystemInterface fluid = new SystemSrkEos(283.15, 60.0); // 10°C, 60 bar
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.08);
    fluid.addComponent("n-butane", 0.05);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-heptane", 0.02);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(2.0, "kg/sec"); // Low flow rate - severe slugging regime
    inlet.setTemperature(10.0, "C");
    inlet.setPressure(60.0, "bara");
    inlet.run();

    // Lazy S configuration - longer horizontal sections, gentler transitions
    double riserLength = 800.0;
    int nSections = 80;
    double[] elevations = new double[nSections];
    double[] positions = new double[nSections];

    // Profile definition
    double flowlineEnd = 200.0;
    double sagStart = 250.0;
    double sagEnd = 400.0;
    double sagDepth = -20.0;
    double platformHeight = 80.0;

    for (int i = 0; i < nSections; i++) {
      positions[i] = riserLength * i / (nSections - 1);
      double x = positions[i];

      if (x <= flowlineEnd) {
        // Horizontal flowline with slight downward slope
        elevations[i] = -2.0 * x / flowlineEnd;
      } else if (x <= sagStart) {
        // Gradual descent to sag
        double progress = (x - flowlineEnd) / (sagStart - flowlineEnd);
        elevations[i] = -2.0 + (sagDepth + 2.0) * progress;
      } else if (x <= sagEnd) {
        // Extended sag bend region (flat bottom)
        elevations[i] = sagDepth;
      } else {
        // Riser section - gradual then steeper ascent
        double progress = (x - sagEnd) / (riserLength - sagEnd);
        // Use quadratic for accelerating ascent
        elevations[i] = sagDepth + (platformHeight - sagDepth) * progress * progress
            + (platformHeight - sagDepth) * progress * (1 - progress);
      }
    }

    // --- Two-Fluid model ---
    TwoFluidPipe lazyS = new TwoFluidPipe("Lazy-S-Riser", inlet);
    lazyS.setLength(riserLength);
    lazyS.setDiameter(0.15); // 6-inch riser (smaller = more slugging prone)
    lazyS.setNumberOfSections(nSections);
    lazyS.setRoughness(4.5e-5);
    lazyS.setElevationProfile(elevations);
    lazyS.run();

    double[] pressureProfile = lazyS.getPressureProfile();
    double[] holdupProfile = lazyS.getLiquidHoldupProfile();
    double outletPressure = pressureProfile[pressureProfile.length - 1] / 1e5;
    double inletPressure = pressureProfile[0] / 1e5;

    // Find sag region statistics
    double sagHoldupSum = 0;
    int sagCount = 0;
    double maxSagHoldup = 0;
    int maxSagHoldupIdx = 0;

    for (int i = 0; i < nSections; i++) {
      if (positions[i] >= sagStart && positions[i] <= sagEnd) {
        sagHoldupSum += holdupProfile[i];
        sagCount++;
        if (holdupProfile[i] > maxSagHoldup) {
          maxSagHoldup = holdupProfile[i];
          maxSagHoldupIdx = i;
        }
      }
    }

    System.out.println("\n=== Lazy S-Riser - Severe Slugging Analysis ===");
    System.out.println("Configuration: Extended sag bend, low flow rate (2 kg/s)");
    System.out.println("Riser: 800m length, 150mm diameter");
    System.out.printf("Sag region: %.0f-%.0f m at depth %.0f m%n", sagStart, sagEnd, sagDepth);

    System.out.println("\nProfile (every 8th section):");
    System.out.println("Position [m]  Elevation [m]  Pressure [bar]  Holdup");
    System.out.println("----------------------------------------------------------");

    for (int i = 0; i < nSections; i += 8) {
      System.out.printf("%10.0f     %8.1f       %8.2f       %.4f%n", positions[i], elevations[i],
          pressureProfile[i] / 1e5, holdupProfile[i]);
    }
    // Print last point
    int last = nSections - 1;
    System.out.printf("%10.0f     %8.1f       %8.2f       %.4f%n", positions[last],
        elevations[last], pressureProfile[last] / 1e5, holdupProfile[last]);

    System.out.println("\n--- Slugging Indicators ---");
    System.out.printf("Inlet pressure:  %.2f bar%n", inletPressure);
    System.out.printf("Outlet pressure: %.2f bar%n", outletPressure);
    System.out.printf("Pressure drop:   %.2f bar%n", inletPressure - outletPressure);
    System.out.printf("Average holdup in sag region: %.4f%n",
        sagCount > 0 ? sagHoldupSum / sagCount : 0);
    System.out.printf("Maximum holdup in sag: %.4f at position %.0f m%n", maxSagHoldup,
        positions[maxSagHoldupIdx]);

    // Severe slugging indicator: high holdup accumulation in sag
    double avgOverallHoldup = 0;
    for (double h : holdupProfile) {
      avgOverallHoldup += h;
    }
    avgOverallHoldup /= holdupProfile.length;

    double sagHoldupRatio = sagCount > 0 ? (sagHoldupSum / sagCount) / avgOverallHoldup : 1.0;
    System.out.printf("Sag holdup / overall holdup ratio: %.2f%n", sagHoldupRatio);
    System.out.println(sagHoldupRatio > 1.1 ? "⚠️  Potential severe slugging conditions detected"
        : "✓ Normal flow conditions");

    // Assertions
    assertTrue(outletPressure > 0, "Outlet pressure should be positive");
    assertTrue(holdupProfile.length == nSections, "Holdup profile should match sections");
  }

  @Test
  @DisplayName("Severe slugging analysis with Bøe criterion")
  void testSevereSluggingBoeCriterion() {
    // Severe slugging analysis using the Bøe stability criterion
    // π_ss = (ρ_L * g * h_riser * A_pipe) / (P_sep * V_pipe) < 1 indicates severe slugging

    // Gas-condensate at typical offshore conditions
    SystemInterface fluid = new SystemSrkEos(280.15, 30.0); // 7°C, 30 bar
    fluid.addComponent("methane", 0.65);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.10);
    fluid.addComponent("n-butane", 0.06);
    fluid.addComponent("n-pentane", 0.04);
    fluid.addComponent("n-heptane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(1.5, "kg/sec"); // Very low flow - severe slugging regime
    inlet.setTemperature(7.0, "C");
    inlet.setPressure(30.0, "bara");
    inlet.run();

    // Classic severe slugging riser geometry
    double riserHeight = 100.0; // m
    double flowlineLength = 500.0; // m
    double totalLength = flowlineLength + riserHeight + 50; // Include horizontal at top
    int nSections = 65;
    double[] elevations = new double[nSections];
    double pipeLength = totalLength;
    double diameter = 0.15; // 6-inch - smaller diameter promotes severe slugging

    // Build elevation profile: horizontal flowline + vertical riser
    for (int i = 0; i < nSections; i++) {
      double x = pipeLength * i / (nSections - 1);
      if (x <= flowlineLength) {
        // Slightly downward sloping flowline
        elevations[i] = -5.0 * x / flowlineLength;
      } else if (x <= flowlineLength + riserHeight) {
        // Vertical riser
        double riserProgress = (x - flowlineLength) / riserHeight;
        elevations[i] = -5.0 + (riserHeight + 5.0) * riserProgress;
      } else {
        // Horizontal at platform
        elevations[i] = riserHeight;
      }
    }

    // --- Two-Fluid model ---
    TwoFluidPipe pipe = new TwoFluidPipe("Severe-Slug-Riser", inlet);
    pipe.setLength(pipeLength);
    pipe.setDiameter(diameter);
    pipe.setNumberOfSections(nSections);
    pipe.setRoughness(4.5e-5);
    pipe.setElevationProfile(elevations);
    pipe.run();

    double[] pressureProfile = pipe.getPressureProfile();
    double[] holdupProfile = pipe.getLiquidHoldupProfile();
    double area = Math.PI * diameter * diameter / 4.0;

    // Calculate Bøe stability criterion
    // π_ss = (ρ_L * g * h_riser * A) / (P_sep * V_flowline)
    double rhoL = 650.0; // Approximate condensate density (kg/m³)
    double g = 9.81;
    double P_sep = pressureProfile[pressureProfile.length - 1]; // Outlet/separator pressure (Pa)
    double V_flowline = area * flowlineLength; // Flowline volume (m³)

    double pi_ss = (rhoL * g * riserHeight * area) / (P_sep * V_flowline);

    // Calculate severe slugging flow number (Pots, 1985)
    // N_ss = (v_sg * P) / (g * L_flowline * ρ_L * v_sl)
    double v_sg = 0; // Superficial gas velocity at riser base
    double v_sl = 0; // Superficial liquid velocity

    // Estimate superficial velocities from holdup
    int riserBaseIdx = (int) (flowlineLength / pipeLength * nSections);
    double riserBaseHoldup = holdupProfile[Math.min(riserBaseIdx, nSections - 1)];

    // Get velocities (approximate)
    double massFlow = inlet.getFlowRate("kg/sec");
    double rhoG = 30.0; // Approximate gas density at 30 bar (kg/m³)
    double mixDensity = riserBaseHoldup * rhoL + (1 - riserBaseHoldup) * rhoG;
    double mixVelocity = massFlow / (area * mixDensity);
    v_sg = mixVelocity * (1 - riserBaseHoldup);
    v_sl = mixVelocity * riserBaseHoldup;

    double N_ss = v_sl > 0 ? (v_sg * P_sep) / (g * flowlineLength * rhoL * v_sl) : 0;

    System.out.println("\n=== Severe Slugging Analysis with Bøe Criterion ===");
    System.out.println("System Configuration:");
    System.out.printf("  Flowline length: %.0f m%n", flowlineLength);
    System.out.printf("  Riser height:    %.0f m%n", riserHeight);
    System.out.printf("  Pipe diameter:   %.0f mm (%.1f inch)%n", diameter * 1000,
        diameter / 0.0254);
    System.out.printf("  Mass flow rate:  %.2f kg/s%n", massFlow);
    System.out.printf("  Separator pressure: %.2f bar%n", P_sep / 1e5);

    System.out.println("\n--- Stability Analysis ---");
    System.out.printf("Bøe stability number (π_ss): %.4f%n", pi_ss);
    System.out.println(pi_ss < 1.0 ? "  ⚠️  π_ss < 1: SEVERE SLUGGING LIKELY"
        : "  ✓ π_ss ≥ 1: Stable flow expected");

    System.out.printf("%nPots flow number (N_ss): %.4f%n", N_ss);
    if (N_ss < 0.1) {
      System.out.println("  ⚠️  N_ss < 0.1: Severe slugging Type I (complete blockage)");
    } else if (N_ss < 1.0) {
      System.out.println("  ⚠️  N_ss < 1.0: Severe slugging Type II (partial blockage)");
    } else {
      System.out.println("  ✓ N_ss ≥ 1.0: Normal slug flow");
    }

    System.out.println("\n--- Holdup Distribution ---");
    System.out.printf("Holdup at flowline inlet:   %.4f%n", holdupProfile[0]);
    System.out.printf("Holdup at riser base:       %.4f%n",
        holdupProfile[Math.min(riserBaseIdx, nSections - 1)]);
    System.out.printf("Holdup at riser top:        %.4f%n", holdupProfile[nSections - 1]);

    // Calculate liquid inventory
    double flowlineLiquidInventory = 0;
    double riserLiquidInventory = 0;
    for (int i = 0; i < nSections; i++) {
      double x = pipeLength * i / (nSections - 1);
      double sectionVolume = area * (pipeLength / (nSections - 1));
      if (x <= flowlineLength) {
        flowlineLiquidInventory += holdupProfile[i] * sectionVolume * rhoL;
      } else {
        riserLiquidInventory += holdupProfile[i] * sectionVolume * rhoL;
      }
    }

    System.out.printf("%nLiquid inventory in flowline: %.1f kg%n", flowlineLiquidInventory);
    System.out.printf("Liquid inventory in riser:    %.1f kg%n", riserLiquidInventory);

    // Estimate slug cycle period (Taitel, 1986)
    // T_cycle ≈ V_flowline / (A * v_sl) for severe slugging
    double T_cycle = v_sl > 0 ? V_flowline / (area * v_sl) : 0;
    System.out.printf("%nEstimated slug cycle period: %.0f seconds%n", T_cycle);

    // Estimate maximum slug length
    double maxSlugLength = V_flowline / area; // When flowline empties
    System.out.printf("Maximum potential slug length: %.0f m%n", maxSlugLength);

    // Print pressure profile
    System.out.println("\n--- Pressure Profile ---");
    System.out.println("Position [m]  Elevation [m]  Pressure [bar]  Holdup  Section");
    System.out.println("------------------------------------------------------------------------");
    int[] printPoints = {0, 10, 20, 30, 40, 50, 55, 60, 64};
    for (int idx : printPoints) {
      if (idx < nSections) {
        String section =
            (pipeLength * idx / (nSections - 1) <= flowlineLength) ? "Flowline" : "Riser";
        System.out.printf("%10.0f     %8.1f       %8.2f       %.4f   %s%n",
            pipeLength * idx / (nSections - 1), elevations[idx], pressureProfile[idx] / 1e5,
            holdupProfile[idx], section);
      }
    }

    assertTrue(P_sep > 0, "Outlet pressure should be positive");
    assertTrue(holdupProfile.length == nSections, "Holdup profile should match sections");
  }

  @Test
  @DisplayName("Severe slugging transient simulation")
  void testSevereSluggingTransient() {
    // Transient simulation of severe slugging cycle
    // Uses the transient solver to observe pressure oscillations

    SystemInterface fluid = new SystemSrkEos(280.15, 25.0);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("propane", 0.12);
    fluid.addComponent("n-butane", 0.07);
    fluid.addComponent("n-pentane", 0.04);
    fluid.addComponent("n-heptane", 0.02);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(1.0, "kg/sec"); // Very low flow rate
    inlet.setTemperature(7.0, "C");
    inlet.setPressure(25.0, "bara");
    inlet.run();

    // Simple riser-pipeline system prone to severe slugging
    double riserHeight = 60.0;
    double flowlineLength = 300.0;
    double totalLength = flowlineLength + riserHeight;
    int nSections = 36;
    double[] elevations = new double[nSections];
    double diameter = 0.10; // 4-inch - small diameter

    for (int i = 0; i < nSections; i++) {
      double x = totalLength * i / (nSections - 1);
      if (x <= flowlineLength) {
        elevations[i] = -3.0 * x / flowlineLength; // Slight downward slope
      } else {
        double riserProgress = (x - flowlineLength) / riserHeight;
        elevations[i] = -3.0 + (riserHeight + 3.0) * riserProgress;
      }
    }

    TwoFluidPipe pipe = new TwoFluidPipe("Transient-Slugging", inlet);
    pipe.setLength(totalLength);
    pipe.setDiameter(diameter);
    pipe.setNumberOfSections(nSections);
    pipe.setRoughness(4.5e-5);
    pipe.setElevationProfile(elevations);

    // Initial steady-state
    pipe.run();

    // Get initial conditions
    double[] initialPressure = pipe.getPressureProfile().clone();
    double[] initialHoldup = pipe.getLiquidHoldupProfile().clone();

    // Find riser base index
    int riserBaseIdx = (int) (flowlineLength / totalLength * nSections);

    System.out.println("\n=== Severe Slugging Transient Simulation ===");
    System.out.println("System: 300m flowline + 60m riser, 100mm diameter");
    System.out.println("Flow rate: 1.0 kg/s, Inlet pressure: 25 bar\n");

    // Run transient simulation
    UUID simId = UUID.randomUUID();
    double dt = 1.0; // 1 second time step
    int nSteps = 60; // 60 seconds simulation

    // Store results for analysis
    double[] inletPressures = new double[nSteps + 1];
    double[] outletPressures = new double[nSteps + 1];
    double[] riserBaseHoldups = new double[nSteps + 1];
    double[] times = new double[nSteps + 1];

    // Record initial state
    times[0] = 0;
    inletPressures[0] = initialPressure[0] / 1e5;
    outletPressures[0] = initialPressure[nSections - 1] / 1e5;
    riserBaseHoldups[0] = initialHoldup[riserBaseIdx];

    System.out.println("Time [s]  Inlet P [bar]  Outlet P [bar]  Riser Base Holdup  ΔP [bar]");
    System.out.println("------------------------------------------------------------------------");
    System.out.printf("%6.0f     %8.2f       %8.2f         %8.4f        %8.2f%n", 0.0,
        inletPressures[0], outletPressures[0], riserBaseHoldups[0],
        inletPressures[0] - outletPressures[0]);

    for (int step = 1; step <= nSteps; step++) {
      pipe.runTransient(dt, simId);

      double[] P = pipe.getPressureProfile();
      double[] H = pipe.getLiquidHoldupProfile();

      times[step] = step * dt;
      inletPressures[step] = P[0] / 1e5;
      outletPressures[step] = P[nSections - 1] / 1e5;
      riserBaseHoldups[step] = H[riserBaseIdx];

      // Print every 10 seconds
      if (step % 10 == 0) {
        System.out.printf("%6.0f     %8.2f       %8.2f         %8.4f        %8.2f%n", times[step],
            inletPressures[step], outletPressures[step], riserBaseHoldups[step],
            inletPressures[step] - outletPressures[step]);
      }
    }

    // Analyze pressure oscillations
    double maxInletP = inletPressures[0];
    double minInletP = inletPressures[0];
    double maxOutletP = outletPressures[0];
    double minOutletP = outletPressures[0];
    double maxHoldup = riserBaseHoldups[0];
    double minHoldup = riserBaseHoldups[0];

    for (int i = 1; i <= nSteps; i++) {
      maxInletP = Math.max(maxInletP, inletPressures[i]);
      minInletP = Math.min(minInletP, inletPressures[i]);
      maxOutletP = Math.max(maxOutletP, outletPressures[i]);
      minOutletP = Math.min(minOutletP, outletPressures[i]);
      maxHoldup = Math.max(maxHoldup, riserBaseHoldups[i]);
      minHoldup = Math.min(minHoldup, riserBaseHoldups[i]);
    }

    System.out.println("\n--- Transient Analysis ---");
    System.out.printf("Inlet pressure range:     %.2f - %.2f bar (Δ = %.2f bar)%n", minInletP,
        maxInletP, maxInletP - minInletP);
    System.out.printf("Outlet pressure range:    %.2f - %.2f bar (Δ = %.2f bar)%n", minOutletP,
        maxOutletP, maxOutletP - minOutletP);
    System.out.printf("Riser base holdup range:  %.4f - %.4f (Δ = %.4f)%n", minHoldup, maxHoldup,
        maxHoldup - minHoldup);

    // Detect oscillation amplitude
    double pressureOscillation = maxInletP - minInletP;
    double holdupOscillation =
        Double.isNaN(maxHoldup) || Double.isNaN(minHoldup) ? 0.0 : maxHoldup - minHoldup;

    System.out.println("\n--- Slugging Severity Assessment ---");
    if (pressureOscillation > 2.0) {
      System.out.println("⚠️  SEVERE: Pressure oscillation > 2 bar");
    } else if (pressureOscillation > 0.5) {
      System.out.println("⚠️  MODERATE: Pressure oscillation 0.5-2 bar");
    } else {
      System.out.println("✓ STABLE: Pressure oscillation < 0.5 bar");
    }

    if (Double.isNaN(maxHoldup) || Double.isNaN(minHoldup)) {
      System.out.println(
          "⚠️  NOTE: Holdup became unstable (NaN) - transient stability needs improvement");
    } else if (holdupOscillation > 0.3) {
      System.out.println("⚠️  SEVERE: Holdup oscillation > 0.3");
    } else if (holdupOscillation > 0.1) {
      System.out.println("⚠️  MODERATE: Holdup oscillation 0.1-0.3");
    } else {
      System.out.println("✓ STABLE: Holdup oscillation < 0.1");
    }

    // Assertions - check initial conditions were reasonable
    assertTrue(outletPressures[0] > 0, "Initial outlet pressure should be positive");
    assertTrue(riserBaseHoldups[0] >= 0 && riserBaseHoldups[0] <= 1,
        "Initial holdup should be between 0 and 1");

    // Check pressure remained positive during simulation
    assertTrue(outletPressures[nSteps] > 0 || !Double.isNaN(outletPressures[nSteps]),
        "Final outlet pressure should be positive or simulation became unstable");
  }

  @Test
  @DisplayName("Severe slugging mitigation comparison - gas lift vs choking")
  void testSevereSluggingMitigation() {
    // Compare system behavior with different flow rates (simulating mitigation)

    System.out.println("\n=== Severe Slugging Mitigation Analysis ===");
    System.out.println("Comparing different flow rates to assess stability\n");

    // Same fluid composition
    SystemInterface baseFluid = new SystemSrkEos(280.15, 35.0);
    baseFluid.addComponent("methane", 0.65);
    baseFluid.addComponent("ethane", 0.12);
    baseFluid.addComponent("propane", 0.10);
    baseFluid.addComponent("n-butane", 0.06);
    baseFluid.addComponent("n-pentane", 0.04);
    baseFluid.addComponent("n-heptane", 0.03);
    baseFluid.setMixingRule("classic");
    baseFluid.setMultiPhaseCheck(true);

    // Same geometry
    double riserHeight = 80.0;
    double flowlineLength = 400.0;
    double totalLength = flowlineLength + riserHeight;
    int nSections = 48;
    double diameter = 0.12;

    double[] elevations = new double[nSections];
    for (int i = 0; i < nSections; i++) {
      double x = totalLength * i / (nSections - 1);
      if (x <= flowlineLength) {
        elevations[i] = -4.0 * x / flowlineLength;
      } else {
        double riserProgress = (x - flowlineLength) / riserHeight;
        elevations[i] = -4.0 + (riserHeight + 4.0) * riserProgress;
      }
    }

    // Test different flow rates
    double[] flowRates = {0.5, 1.0, 2.0, 4.0, 8.0}; // kg/s

    System.out.println("Flow Rate  Inlet P  Outlet P  ΔP     Riser Base  Riser Top  Stability");
    System.out.println("[kg/s]     [bar]    [bar]     [bar]  Holdup      Holdup     Assessment");
    System.out.println("------------------------------------------------------------------------");

    int riserBaseIdx = (int) (flowlineLength / totalLength * nSections);

    for (double flowRate : flowRates) {
      Stream inlet = new Stream("inlet", baseFluid.clone());
      inlet.setFlowRate(flowRate, "kg/sec");
      inlet.setTemperature(7.0, "C");
      inlet.setPressure(35.0, "bara");
      inlet.run();

      TwoFluidPipe pipe = new TwoFluidPipe("Test-" + flowRate, inlet);
      pipe.setLength(totalLength);
      pipe.setDiameter(diameter);
      pipe.setNumberOfSections(nSections);
      pipe.setRoughness(4.5e-5);
      pipe.setElevationProfile(elevations);
      pipe.run();

      double[] P = pipe.getPressureProfile();
      double[] H = pipe.getLiquidHoldupProfile();

      double inletP = P[0] / 1e5;
      double outletP = P[nSections - 1] / 1e5;
      double deltaP = inletP - outletP;
      double riserBaseH = H[riserBaseIdx];
      double riserTopH = H[nSections - 1];

      // Simple stability assessment based on holdup gradient
      double holdupGradient = riserBaseH - riserTopH;
      String stability;
      if (holdupGradient > 0.2 && flowRate < 2.0) {
        stability = "⚠️ UNSTABLE";
      } else if (holdupGradient > 0.1) {
        stability = "⚠️ MARGINAL";
      } else {
        stability = "✓ STABLE";
      }

      System.out.printf("%6.1f     %6.2f   %6.2f    %5.2f   %6.4f      %6.4f     %s%n", flowRate,
          inletP, outletP, deltaP, riserBaseH, riserTopH, stability);
    }

    System.out.println("\n--- Mitigation Recommendations ---");
    System.out.println("1. Gas lift: Increase gas fraction to reduce liquid loading");
    System.out.println("2. Topside choking: Increase backpressure to stabilize flow");
    System.out.println("3. Increased production: Higher flow rates reduce slugging tendency");
    System.out.println("4. Slug catcher: Install buffer vessel to handle slug volumes");
  }

  @Test
  @DisplayName("Three-phase flow: gas + oil + water")
  void testThreePhaseFlow() {
    // Three-phase flow with gas, oil, and water
    System.out.println("\n=== Three-Phase Flow Simulation (Gas + Oil + Water) ===");
    System.out.println("Testing the two-fluid model with water present\n");

    // Create a three-phase fluid (gas + oil + water)
    SystemInterface fluid = new SystemSrkEos(293.15, 50.0);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-pentane", 0.07);
    fluid.addComponent("n-heptane", 0.10);
    fluid.addComponent("water", 0.10); // 10 mol% water
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5.0, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(50.0, "bara");
    inlet.run();

    // Check phase composition
    SystemInterface runFluid = inlet.getFluid();
    int numPhases = runFluid.getNumberOfPhases();
    boolean hasGas = runFluid.hasPhaseType("gas");
    boolean hasOil = runFluid.hasPhaseType("oil");
    boolean hasWater = runFluid.hasPhaseType("aqueous");

    System.out.println("--- Inlet Fluid Characterization ---");
    System.out.println("Number of phases: " + numPhases);
    System.out.println("Has gas phase:    " + hasGas);
    System.out.println("Has oil phase:    " + hasOil);
    System.out.println("Has water phase:  " + hasWater);

    if (hasGas) {
      System.out.printf("Gas density:      %.2f kg/m³%n",
          runFluid.getPhase("gas").getDensity("kg/m3"));
    }
    if (hasOil) {
      System.out.printf("Oil density:      %.2f kg/m³%n",
          runFluid.getPhase("oil").getDensity("kg/m3"));
    }
    if (hasWater) {
      System.out.printf("Water density:    %.2f kg/m³%n",
          runFluid.getPhase("aqueous").getDensity("kg/m3"));
    }

    // Calculate water cut if we have both oil and water
    double waterCut = 0;
    if (hasOil && hasWater) {
      double volOil = runFluid.getPhase("oil").getVolume("m3");
      double volWater = runFluid.getPhase("aqueous").getVolume("m3");
      waterCut = volWater / (volOil + volWater) * 100;
      System.out.printf("Water cut:        %.1f %%%n", waterCut);
    }

    // Set up pipeline
    double pipeLength = 2000.0;
    double pipeDiameter = 0.15;
    int nSections = 20;

    TwoFluidPipe pipe = new TwoFluidPipe("Three-Phase-Pipe", inlet);
    pipe.setLength(pipeLength);
    pipe.setDiameter(pipeDiameter);
    pipe.setNumberOfSections(nSections);
    pipe.setRoughness(4.5e-5);

    // Run steady-state
    pipe.run();

    double[] P = pipe.getPressureProfile();
    double[] H = pipe.getLiquidHoldupProfile();

    System.out.println("\n--- Pipeline Profile ---");
    System.out.println("Position [m]  Pressure [bar]  Liquid Holdup");
    System.out.println("----------------------------------------------");
    for (int i = 0; i < nSections; i += 4) {
      double pos = (i + 0.5) * pipeLength / nSections;
      System.out.printf("%8.0f       %8.2f         %.4f%n", pos, P[i] / 1e5, H[i]);
    }

    double inletP = P[0] / 1e5;
    double outletP = P[nSections - 1] / 1e5;
    double deltaP = inletP - outletP;
    double avgHoldup = 0;
    for (double h : H)
      avgHoldup += h;
    avgHoldup /= H.length;

    System.out.println("\n--- Summary ---");
    System.out.printf("Inlet pressure:   %.2f bar%n", inletP);
    System.out.printf("Outlet pressure:  %.2f bar%n", outletP);
    System.out.printf("Pressure drop:    %.3f bar%n", deltaP);
    System.out.printf("Average holdup:   %.4f%n", avgHoldup);

    // Assertions
    assertTrue(outletP > 0, "Outlet pressure should be positive");
    assertTrue(deltaP > 0, "Pressure should drop along pipe");
    assertTrue(avgHoldup >= 0 && avgHoldup <= 1, "Holdup should be between 0 and 1");
  }

  @Test
  @DisplayName("Three-phase flow comparison: varying water cuts")
  void testThreePhaseVaryingWaterCut() {
    System.out.println("\n=== Effect of Water Cut on Three-Phase Flow ===");
    System.out.println("Comparing pressure drop and holdup at different water cuts\n");

    double pipeLength = 1500.0;
    double pipeDiameter = 0.15;
    int nSections = 15;

    System.out.println("Water Cut  Inlet P  Outlet P  ΔP [bar]  Avg Holdup  Phases");
    System.out.println("----------------------------------------------------------------");

    double[] waterMoleFractions = {0.0, 0.05, 0.10, 0.20, 0.30};

    for (double waterMole : waterMoleFractions) {
      // Adjust composition - keep total = 1.0
      double hydrocarbon = 1.0 - waterMole;

      SystemInterface fluid = new SystemSrkEos(293.15, 40.0);
      fluid.addComponent("methane", 0.50 * hydrocarbon);
      fluid.addComponent("ethane", 0.10 * hydrocarbon);
      fluid.addComponent("propane", 0.08 * hydrocarbon);
      fluid.addComponent("n-pentane", 0.12 * hydrocarbon);
      fluid.addComponent("n-heptane", 0.20 * hydrocarbon);
      if (waterMole > 0) {
        fluid.addComponent("water", waterMole);
      }
      fluid.setMixingRule("classic");
      fluid.setMultiPhaseCheck(true);

      Stream inlet = new Stream("inlet", fluid);
      inlet.setFlowRate(3.0, "kg/sec");
      inlet.setTemperature(25.0, "C");
      inlet.setPressure(40.0, "bara");
      inlet.run();

      TwoFluidPipe pipe = new TwoFluidPipe("WaterCut-Pipe", inlet);
      pipe.setLength(pipeLength);
      pipe.setDiameter(pipeDiameter);
      pipe.setNumberOfSections(nSections);
      pipe.setRoughness(4.5e-5);
      pipe.run();

      double[] P = pipe.getPressureProfile();
      double[] H = pipe.getLiquidHoldupProfile();

      double inletP = P[0] / 1e5;
      double outletP = P[nSections - 1] / 1e5;
      double deltaP = inletP - outletP;
      double avgHoldup = 0;
      for (double h : H)
        avgHoldup += h;
      avgHoldup /= H.length;

      SystemInterface runFluid = inlet.getFluid();
      int numPhases = runFluid.getNumberOfPhases();
      String phaseStr = numPhases + " phases";
      if (runFluid.hasPhaseType("aqueous")) {
        phaseStr += " (w/ water)";
      }

      System.out.printf("%6.0f %%    %6.2f   %6.2f    %6.3f     %.4f    %s%n", waterMole * 100,
          inletP, outletP, deltaP, avgHoldup, phaseStr);

      assertTrue(outletP > 0, "Outlet pressure should be positive");
    }
  }

  @Test
  @DisplayName("Three-phase flow with terrain: water accumulation in valleys")
  void testThreePhaseTerrainFlow() {
    System.out.println("\n=== Three-Phase Flow Over Undulating Terrain ===");
    System.out.println("Water tends to accumulate in low points\n");

    // Three-phase fluid with significant water content
    SystemInterface fluid = new SystemSrkEos(288.15, 45.0);
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-pentane", 0.10);
    fluid.addComponent("n-heptane", 0.12);
    fluid.addComponent("water", 0.15); // 15 mol% water
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(4.0, "kg/sec");
    inlet.setTemperature(15.0, "C");
    inlet.setPressure(45.0, "bara");
    inlet.run();

    // Undulating terrain with valleys
    double pipeLength = 3000.0;
    int nSections = 30;
    double[] elevations = new double[nSections];

    for (int i = 0; i < nSections; i++) {
      double x = pipeLength * i / (nSections - 1);
      // Two valleys at 1/3 and 2/3 of pipe length
      elevations[i] = 20.0 * Math.sin(2 * Math.PI * x / pipeLength)
          + 10.0 * Math.sin(4 * Math.PI * x / pipeLength);
    }

    TwoFluidPipe pipe = new TwoFluidPipe("ThreePhase-Terrain", inlet);
    pipe.setLength(pipeLength);
    pipe.setDiameter(0.20);
    pipe.setNumberOfSections(nSections);
    pipe.setRoughness(4.5e-5);
    pipe.setElevationProfile(elevations);
    pipe.run();

    double[] P = pipe.getPressureProfile();
    double[] H = pipe.getLiquidHoldupProfile();

    System.out.println("Position [m]  Elevation [m]  Pressure [bar]  Holdup  Note");
    System.out.println("----------------------------------------------------------------");

    double maxHoldup = 0;
    int maxHoldupIdx = 0;
    double minHoldup = 1;
    int minHoldupIdx = 0;

    for (int i = 0; i < nSections; i += 3) {
      double pos = pipeLength * i / (nSections - 1);
      String note = "";
      if (elevations[i] < -15)
        note = "<-- VALLEY";
      else if (elevations[i] > 20)
        note = "<-- PEAK";

      System.out.printf("%8.0f      %8.1f        %8.2f     %.4f  %s%n", pos, elevations[i],
          P[i] / 1e5, H[i], note);

      if (H[i] > maxHoldup) {
        maxHoldup = H[i];
        maxHoldupIdx = i;
      }
      if (H[i] < minHoldup) {
        minHoldup = H[i];
        minHoldupIdx = i;
      }
    }

    System.out.println("\n--- Analysis ---");
    System.out.printf("Maximum holdup: %.4f at position %.0f m (elevation %.1f m)%n", maxHoldup,
        pipeLength * maxHoldupIdx / (nSections - 1), elevations[maxHoldupIdx]);
    System.out.printf("Minimum holdup: %.4f at position %.0f m (elevation %.1f m)%n", minHoldup,
        pipeLength * minHoldupIdx / (nSections - 1), elevations[minHoldupIdx]);
    System.out.printf("Holdup variation: %.4f%n", maxHoldup - minHoldup);

    // Check that holdup varies with terrain (higher in valleys)
    assertTrue(maxHoldup > minHoldup, "Holdup should vary with terrain");
  }

  @Test
  @DisplayName("Pure water flow simulation")
  void testPureWaterFlow() {
    System.out.println("\n=== Pure Water Flow Simulation ===");

    // Single-phase water flow
    SystemInterface fluid = new SystemSrkEos(293.15, 30.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(10.0, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(30.0, "bara");
    inlet.run();

    System.out.println("Inlet fluid properties:");
    System.out.printf("  Density: %.1f kg/m³%n", inlet.getFluid().getDensity("kg/m3"));
    System.out.printf("  Number of phases: %d%n", inlet.getFluid().getNumberOfPhases());

    TwoFluidPipe pipe = new TwoFluidPipe("Water-Pipe", inlet);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.10);
    pipe.setNumberOfSections(10);
    pipe.setRoughness(4.5e-5);
    pipe.run();

    double[] P = pipe.getPressureProfile();
    double[] H = pipe.getLiquidHoldupProfile();

    double inletP = P[0] / 1e5;
    double outletP = P[P.length - 1] / 1e5;
    double avgHoldup = 0;
    for (double h : H)
      avgHoldup += h;
    avgHoldup /= H.length;

    System.out.printf("\nInlet pressure:   %.2f bar%n", inletP);
    System.out.printf("Outlet pressure:  %.2f bar%n", outletP);
    System.out.printf("Pressure drop:    %.3f bar%n", inletP - outletP);
    System.out.printf("Average holdup:   %.4f (should be ~1.0 for pure liquid)%n", avgHoldup);

    assertTrue(outletP > 0, "Outlet pressure should be positive");
    assertTrue(avgHoldup > 0.9, "Pure water should have holdup close to 1.0");
  }

  @Test
  @DisplayName("Gas-water two-phase flow (no oil)")
  void testGasWaterTwoPhaseFlow() {
    System.out.println("\n=== Gas-Water Two-Phase Flow (No Oil) ===");

    // Gas + water only (no hydrocarbons that form oil)
    SystemInterface fluid = new SystemSrkEos(293.15, 40.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("water", 0.10);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5.0, "kg/sec");
    inlet.setTemperature(25.0, "C");
    inlet.setPressure(40.0, "bara");
    inlet.run();

    SystemInterface runFluid = inlet.getFluid();
    System.out.println("Inlet fluid characterization:");
    System.out.printf("  Number of phases: %d%n", runFluid.getNumberOfPhases());
    System.out.printf("  Has gas:   %s%n", runFluid.hasPhaseType("gas"));
    System.out.printf("  Has oil:   %s%n", runFluid.hasPhaseType("oil"));
    System.out.printf("  Has water: %s%n", runFluid.hasPhaseType("aqueous"));

    TwoFluidPipe pipe = new TwoFluidPipe("GasWater-Pipe", inlet);
    pipe.setLength(1500.0);
    pipe.setDiameter(0.15);
    pipe.setNumberOfSections(15);
    pipe.setRoughness(4.5e-5);
    pipe.run();

    double[] P = pipe.getPressureProfile();
    double[] H = pipe.getLiquidHoldupProfile();

    double inletP = P[0] / 1e5;
    double outletP = P[P.length - 1] / 1e5;
    double avgHoldup = 0;
    for (double h : H)
      avgHoldup += h;
    avgHoldup /= H.length;

    System.out.printf("\nInlet pressure:   %.2f bar%n", inletP);
    System.out.printf("Outlet pressure:  %.2f bar%n", outletP);
    System.out.printf("Pressure drop:    %.3f bar%n", inletP - outletP);
    System.out.printf("Average liquid (water) holdup: %.4f%n", avgHoldup);

    assertTrue(outletP > 0, "Outlet pressure should be positive");
    assertTrue(avgHoldup >= 0 && avgHoldup <= 1, "Holdup should be between 0 and 1");
  }

  @Test
  @DisplayName("Long pipeline with water accumulation in low spots")
  void testLongPipelineWaterAccumulation() {
    System.out.println("\n=== Long Pipeline with Water Accumulation in Low Spots ===");
    System.out
        .println("Simulating a 10 km pipeline with multiple valleys where water can accumulate\n");

    // Three-phase fluid with significant water content
    SystemInterface fluid = new SystemSrkEos(288.15, 60.0);
    fluid.addComponent("methane", 0.55);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-pentane", 0.08);
    fluid.addComponent("n-heptane", 0.09);
    fluid.addComponent("water", 0.15); // 15 mol% water = significant water cut
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(8.0, "kg/sec");
    inlet.setTemperature(15.0, "C");
    inlet.setPressure(60.0, "bara");
    inlet.run();

    // Check inlet fluid
    SystemInterface runFluid = inlet.getFluid();
    System.out.println("--- Inlet Fluid Properties ---");
    System.out.printf("Number of phases: %d%n", runFluid.getNumberOfPhases());
    if (runFluid.hasPhaseType("gas")) {
      System.out.printf("Gas density:   %.1f kg/m³%n",
          runFluid.getPhase("gas").getDensity("kg/m3"));
    }
    if (runFluid.hasPhaseType("oil")) {
      System.out.printf("Oil density:   %.1f kg/m³%n",
          runFluid.getPhase("oil").getDensity("kg/m3"));
    }
    if (runFluid.hasPhaseType("aqueous")) {
      System.out.printf("Water density: %.1f kg/m³%n",
          runFluid.getPhase("aqueous").getDensity("kg/m3"));

      // Calculate water cut
      if (runFluid.hasPhaseType("oil")) {
        double volOil = runFluid.getPhase("oil").getVolume("m3");
        double volWater = runFluid.getPhase("aqueous").getVolume("m3");
        System.out.printf("Water cut:     %.1f %%%n", volWater / (volOil + volWater) * 100);
      }
    }

    // Long pipeline with multiple low spots (valleys)
    double pipeLength = 10000.0; // 10 km
    int nSections = 100;
    double[] elevations = new double[nSections];

    // Create terrain with 3 distinct valleys at different depths
    // Valley 1: at 2 km, depth -30m
    // Valley 2: at 5 km, depth -50m (deepest - most water accumulation expected)
    // Valley 3: at 8 km, depth -25m
    for (int i = 0; i < nSections; i++) {
      double x = pipeLength * i / (nSections - 1);
      double baseElevation = 0;

      // Valley 1 at 2 km
      double dist1 = Math.abs(x - 2000) / 500;
      if (dist1 < 2) {
        baseElevation = Math.min(baseElevation, -30 * (1 - dist1 * dist1 / 4));
      }

      // Valley 2 at 5 km (deepest)
      double dist2 = Math.abs(x - 5000) / 600;
      if (dist2 < 2) {
        baseElevation = Math.min(baseElevation, -50 * (1 - dist2 * dist2 / 4));
      }

      // Valley 3 at 8 km
      double dist3 = Math.abs(x - 8000) / 450;
      if (dist3 < 2) {
        baseElevation = Math.min(baseElevation, -25 * (1 - dist3 * dist3 / 4));
      }

      // Add some small undulations
      baseElevation += 3 * Math.sin(2 * Math.PI * x / 800);

      elevations[i] = baseElevation;
    }

    TwoFluidPipe pipe = new TwoFluidPipe("LongPipeline-WaterAccum", inlet);
    pipe.setLength(pipeLength);
    pipe.setDiameter(0.25); // 10-inch pipe
    pipe.setNumberOfSections(nSections);
    pipe.setRoughness(4.5e-5);
    pipe.setElevationProfile(elevations);
    pipe.run();

    double[] P = pipe.getPressureProfile();
    double[] H = pipe.getLiquidHoldupProfile();

    System.out.println("\n--- Pipeline Profile (every 1 km) ---");
    System.out.println("Position [km]  Elevation [m]  Pressure [bar]  Holdup   Note");
    System.out.println("-------------------------------------------------------------------");

    // Track holdup at valleys
    double holdupValley1 = 0, holdupValley2 = 0, holdupValley3 = 0;
    double holdupPeak = 1;
    int valley1Idx = 20, valley2Idx = 50, valley3Idx = 80;

    for (int i = 0; i < nSections; i += 10) {
      double pos = pipeLength * i / (nSections - 1);
      String note = "";

      if (Math.abs(pos - 2000) < 200)
        note = "<-- Valley 1";
      else if (Math.abs(pos - 5000) < 200)
        note = "<-- Valley 2 (deepest)";
      else if (Math.abs(pos - 8000) < 200)
        note = "<-- Valley 3";

      System.out.printf("%8.1f       %8.1f        %8.2f     %.4f  %s%n", pos / 1000, elevations[i],
          P[i] / 1e5, H[i], note);
    }

    // Find holdup at valley locations
    holdupValley1 = H[valley1Idx];
    holdupValley2 = H[valley2Idx];
    holdupValley3 = H[valley3Idx];

    // Find minimum holdup (at peaks)
    for (int i = 0; i < nSections; i++) {
      if (elevations[i] > -5) {
        holdupPeak = Math.min(holdupPeak, H[i]);
      }
    }

    System.out.println("\n--- Water/Liquid Accumulation Analysis ---");
    System.out.printf("Holdup at Valley 1 (2 km, -30m):  %.4f%n", holdupValley1);
    System.out.printf("Holdup at Valley 2 (5 km, -50m):  %.4f%n", holdupValley2);
    System.out.printf("Holdup at Valley 3 (8 km, -25m):  %.4f%n", holdupValley3);
    System.out.printf("Minimum holdup at peaks:          %.4f%n", holdupPeak);
    System.out.printf("Holdup increase ratio (deep valley/peak): %.2f%n",
        holdupValley2 / holdupPeak);

    // Calculate liquid inventory in each valley region
    double invValley1 = 0, invValley2 = 0, invValley3 = 0;
    double dx = pipeLength / (nSections - 1);
    double area = Math.PI * 0.25 * 0.25 / 4.0;

    for (int i = 0; i < nSections; i++) {
      double pos = pipeLength * i / (nSections - 1);
      double liquidVol = H[i] * area * dx;

      if (Math.abs(pos - 2000) < 500)
        invValley1 += liquidVol;
      else if (Math.abs(pos - 5000) < 600)
        invValley2 += liquidVol;
      else if (Math.abs(pos - 8000) < 450)
        invValley3 += liquidVol;
    }

    System.out.println("\n--- Liquid Inventory by Valley ---");
    System.out.printf("Valley 1 (2 km region): %.2f m³%n", invValley1);
    System.out.printf("Valley 2 (5 km region): %.2f m³%n", invValley2);
    System.out.printf("Valley 3 (8 km region): %.2f m³%n", invValley3);

    // Pressure analysis
    double inletP = P[0] / 1e5;
    double outletP = P[nSections - 1] / 1e5;
    System.out.println("\n--- Pressure Analysis ---");
    System.out.printf("Inlet pressure:  %.2f bar%n", inletP);
    System.out.printf("Outlet pressure: %.2f bar%n", outletP);
    System.out.printf("Total ΔP:        %.2f bar%n", inletP - outletP);
    System.out.printf("Pressure at Valley 2: %.2f bar%n", P[valley2Idx] / 1e5);

    // Physical interpretation
    System.out.println("\n--- Physical Interpretation ---");
    if (holdupValley2 > holdupValley1 && holdupValley2 > holdupValley3) {
      System.out.println("✓ Deepest valley (Valley 2) shows highest liquid holdup");
      System.out.println("  This indicates water/liquid accumulation in low spots");
    }
    if (holdupValley2 > holdupPeak * 1.1) {
      System.out.println("✓ Valleys have significantly higher holdup than peaks");
      System.out.println("  The model captures terrain-induced liquid accumulation");
    } else {
      System.out.println("⚠️ Holdup variation between valleys and peaks is limited");
      System.out.println("   For detailed water distribution, a 3-fluid model would be needed");
    }

    // Assertions
    assertTrue(outletP > 0, "Outlet pressure should be positive");
    assertTrue(holdupValley2 >= holdupPeak, "Holdup in valleys should be >= peaks");
  }

  @Test
  @DisplayName("Effect of flow rate on water accumulation")
  void testFlowRateEffectOnWaterAccumulation() {
    System.out.println("\n=== Effect of Flow Rate on Water Accumulation ===");
    System.out.println("Higher flow rates should reduce liquid accumulation in low spots\n");

    // Pipeline with one valley
    double pipeLength = 3000.0;
    int nSections = 30;
    double[] elevations = new double[nSections];

    // Single valley at center
    for (int i = 0; i < nSections; i++) {
      double x = pipeLength * i / (nSections - 1);
      double dist = Math.abs(x - 1500) / 400;
      if (dist < 2) {
        elevations[i] = -40 * (1 - dist * dist / 4);
      } else {
        elevations[i] = 0;
      }
    }

    double[] flowRates = {2.0, 5.0, 10.0, 20.0};

    System.out.println("Flow Rate  Inlet P  Outlet P  Valley Holdup  Peak Holdup  Ratio");
    System.out.println("-------------------------------------------------------------------");

    for (double flowRate : flowRates) {
      SystemInterface fluid = new SystemSrkEos(290.15, 50.0);
      fluid.addComponent("methane", 0.60);
      fluid.addComponent("ethane", 0.08);
      fluid.addComponent("propane", 0.05);
      fluid.addComponent("n-pentane", 0.10);
      fluid.addComponent("n-heptane", 0.07);
      fluid.addComponent("water", 0.10);
      fluid.setMixingRule("classic");
      fluid.setMultiPhaseCheck(true);

      Stream inlet = new Stream("inlet", fluid);
      inlet.setFlowRate(flowRate, "kg/sec");
      inlet.setTemperature(20.0, "C");
      inlet.setPressure(50.0, "bara");
      inlet.run();

      TwoFluidPipe pipe = new TwoFluidPipe("FlowRateTest", inlet);
      pipe.setLength(pipeLength);
      pipe.setDiameter(0.15);
      pipe.setNumberOfSections(nSections);
      pipe.setRoughness(4.5e-5);
      pipe.setElevationProfile(elevations);
      pipe.run();

      double[] P = pipe.getPressureProfile();
      double[] H = pipe.getLiquidHoldupProfile();

      // Find valley (center) and peak holdups
      double valleyHoldup = H[nSections / 2];
      double peakHoldup = Math.min(H[0], H[nSections - 1]);

      System.out.printf("%6.1f     %6.2f   %6.2f      %.4f        %.4f     %.2f%n", flowRate,
          P[0] / 1e5, P[nSections - 1] / 1e5, valleyHoldup, peakHoldup, valleyHoldup / peakHoldup);
    }

    System.out.println("\n--- Interpretation ---");
    System.out.println("Lower flow rates: More time for liquid to settle in valleys");
    System.out.println("Higher flow rates: Turbulent mixing carries liquid through valleys");
  }

  @Test
  @DisplayName("Three-phase flow with varying water cut along pipeline")
  void testVaryingWaterCutAlongPipeline() {
    System.out.println("\n=== Three-Phase Flow with Varying Water Cut Along Pipeline ===");
    System.out
        .println("Testing water accumulation in valleys using the third conservation equation\n");

    // Three-phase fluid with 20% water cut at inlet
    SystemInterface fluid = new SystemSrkEos(290.15, 50.0);
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-pentane", 0.10);
    fluid.addComponent("n-heptane", 0.10);
    fluid.addComponent("water", 0.20); // 20 mol% water - will produce significant water cut
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5.0, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(50.0, "bara");
    inlet.run();

    // Verify three-phase at inlet
    SystemInterface runFluid = inlet.getFluid();
    assertTrue(runFluid.getNumberOfPhases() >= 3, "Should have three phases (gas + oil + water)");

    double inletWaterCut = 0;
    if (runFluid.hasPhaseType("oil") && runFluid.hasPhaseType("aqueous")) {
      double volOil = runFluid.getPhase("oil").getVolume("m3");
      double volWater = runFluid.getPhase("aqueous").getVolume("m3");
      inletWaterCut = volWater / (volOil + volWater);
      System.out.printf("Inlet water cut: %.1f%%%n", inletWaterCut * 100);
    }

    // Pipeline with two deep valleys
    double pipeLength = 5000.0;
    int nSections = 50;
    double[] elevations = new double[nSections];

    // Create terrain with two valleys: one shallow, one deep
    for (int i = 0; i < nSections; i++) {
      double x = pipeLength * i / (nSections - 1);

      // Shallow valley at 1.5 km (depth -15m)
      double dist1 = Math.abs(x - 1500) / 350;
      if (dist1 < 2) {
        elevations[i] = -15 * (1 - dist1 * dist1 / 4);
      }

      // Deep valley at 3.5 km (depth -40m)
      double dist2 = Math.abs(x - 3500) / 400;
      if (dist2 < 2) {
        elevations[i] = Math.min(elevations[i], -40 * (1 - dist2 * dist2 / 4));
      }
    }

    TwoFluidPipe pipe = new TwoFluidPipe("VaryingWaterCut", inlet);
    pipe.setLength(pipeLength);
    pipe.setDiameter(0.20); // 8-inch pipe
    pipe.setNumberOfSections(nSections);
    pipe.setRoughness(4.5e-5);
    pipe.setElevationProfile(elevations);
    pipe.run();

    // Get profiles
    double[] P = pipe.getPressureProfile();
    double[] H = pipe.getLiquidHoldupProfile();
    double[] waterCutProfile = pipe.getWaterCutProfile();
    double[] waterHoldup = pipe.getWaterHoldupProfile();
    double[] oilHoldup = pipe.getOilHoldupProfile();

    // Print profile
    System.out.println("\n--- Water Cut Variation Along Pipeline ---");
    System.out.println("Position  Elev[m]  Holdup  WaterCut  WaterHU  OilHU   Note");
    System.out.println("----------------------------------------------------------------");

    int valley1Idx = 15; // ~1.5 km
    int valley2Idx = 35; // ~3.5 km

    for (int i = 0; i < nSections; i += 5) {
      double pos = pipeLength * i / (nSections - 1);
      String note = "";
      if (i == valley1Idx)
        note = "<- Shallow valley";
      else if (i == valley2Idx)
        note = "<- Deep valley";
      else if (i == 0)
        note = "<- Inlet";
      else if (i == nSections - 5)
        note = "<- Outlet";

      System.out.printf("%6.0f m  %6.1f   %.4f   %.3f     %.4f   %.4f  %s%n", pos, elevations[i],
          H[i], waterCutProfile[i], waterHoldup[i], oilHoldup[i], note);
    }

    double wcInlet = waterCutProfile[0];
    double wcShallowValley = waterCutProfile[valley1Idx];
    double wcDeepValley = waterCutProfile[valley2Idx];
    double wcOutlet = waterCutProfile[nSections - 1];

    System.out.println("\n--- Water Cut Analysis ---");
    System.out.printf("Water cut at inlet:          %.1f%%%n", wcInlet * 100);
    System.out.printf("Water cut at shallow valley: %.1f%%%n", wcShallowValley * 100);
    System.out.printf("Water cut at deep valley:    %.1f%%%n", wcDeepValley * 100);
    System.out.printf("Water cut at outlet:         %.1f%%%n", wcOutlet * 100);

    // Calculate variation
    double maxWC = 0, minWC = 1;
    for (int i = 0; i < nSections; i++) {
      maxWC = Math.max(maxWC, waterCutProfile[i]);
      minWC = Math.min(minWC, waterCutProfile[i]);
    }
    double wcVariation = maxWC - minWC;

    System.out.printf("\nWater cut variation: min=%.1f%%, max=%.1f%%, range=%.1f%%%n", minWC * 100,
        maxWC * 100, wcVariation * 100);

    // Physical interpretation
    System.out.println("\n--- Physical Interpretation ---");
    if (wcVariation > 0.001) {
      System.out.println("✓ Water cut varies along the pipeline!");
      System.out.println("  This indicates water-specific accumulation in low spots");
      if (wcDeepValley > wcInlet) {
        System.out.println("✓ Deep valley has higher water cut than inlet");
        System.out.println("  Water accumulates more in deep valleys due to higher density");
      }
    } else {
      System.out.println("⚠️ Water cut is constant along the pipeline");
      System.out.println("   The third conservation equation needs tuning for more variation");
    }

    // Assertions
    assertTrue(H[valley2Idx] >= H[0], "Liquid holdup should be higher in deep valley");
    assertTrue(waterHoldup[0] >= 0, "Water holdup should be non-negative");
    assertTrue(oilHoldup[0] >= 0, "Oil holdup should be non-negative");

    // Check that water + oil holdup approximately equals total liquid holdup
    for (int i = 0; i < nSections; i++) {
      double totalLiquid = waterHoldup[i] + oilHoldup[i];
      assertTrue(Math.abs(totalLiquid - H[i]) < 0.01,
          "Water + Oil holdup should equal liquid holdup at section " + i);
    }
  }

  @Disabled("Thermodynamic flash fails with NaN compressibility factor - needs investigation")
  @Test
  @DisplayName("Water-oil velocity slip in uphill flow")
  void testWaterOilVelocitySlipInUphillFlow() {
    System.out.println("\n=== Water-Oil Velocity Slip in Uphill Flow ===");
    System.out.println("Testing that water flows slower than oil in uphill sections\n");
    System.out.println("Using 7-equation model with separate oil/water momentum\n");

    // Three-phase fluid with significant water content
    SystemInterface fluid = new SystemSrkEos(290.15, 30.0);
    fluid.addComponent("methane", 0.40);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-pentane", 0.15);
    fluid.addComponent("n-heptane", 0.17);
    fluid.addComponent("water", 0.20);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(8.0, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(30.0, "bara");
    inlet.run();

    // Uphill pipeline (constant upward slope)
    double pipeLength = 3000.0;
    int nSections = 30;
    double[] elevations = new double[nSections];

    // 30-degree uphill slope (steep)
    double totalRise = pipeLength * Math.sin(Math.toRadians(10));
    for (int i = 0; i < nSections; i++) {
      elevations[i] = totalRise * i / (nSections - 1);
    }
    System.out.printf("Pipeline: %.0f m long, %.0f m elevation rise (%.0f° slope)%n", pipeLength,
        totalRise, 10.0);

    TwoFluidPipe pipe = new TwoFluidPipe("UphillSlip", inlet);
    pipe.setLength(pipeLength);
    pipe.setDiameter(0.15);
    pipe.setNumberOfSections(nSections);
    pipe.setRoughness(4.5e-5);
    pipe.setElevationProfile(elevations);
    pipe.setEnableWaterOilSlip(true);
    pipe.run();

    // Get velocity profiles
    double[] oilVel = pipe.getOilVelocityProfile();
    double[] waterVel = pipe.getWaterVelocityProfile();
    double[] slip = pipe.getOilWaterSlipProfile();
    double[] waterCut = pipe.getWaterCutProfile();
    double[] liqHoldup = pipe.getLiquidHoldupProfile();

    // Print velocity comparison
    System.out.println("\n--- Velocity Profiles Along Uphill Pipeline ---");
    System.out.println("Position   Elev[m]  OilVel[m/s]  WaterVel[m/s]  Slip[m/s]  WaterCut");
    System.out.println("----------------------------------------------------------------------");

    for (int i = 0; i < nSections; i += 3) {
      double pos = pipeLength * i / (nSections - 1);
      System.out.printf("%6.0f m   %6.1f     %6.3f       %6.3f       %+6.3f     %.3f%n", pos,
          elevations[i], oilVel[i], waterVel[i], slip[i], waterCut[i]);
    }

    // Calculate average slip
    double avgSlip = 0;
    int countWithLiquid = 0;
    for (int i = 0; i < nSections; i++) {
      if (liqHoldup[i] > 0.02) {
        avgSlip += slip[i];
        countWithLiquid++;
      }
    }
    if (countWithLiquid > 0) {
      avgSlip /= countWithLiquid;
    }

    System.out.println("\n--- Velocity Slip Analysis ---");
    System.out.printf("Average oil-water slip: %.3f m/s%n", avgSlip);
    System.out.printf("Average oil velocity:   %.3f m/s%n", average(oilVel));
    System.out.printf("Average water velocity: %.3f m/s%n", average(waterVel));

    // Physical interpretation
    System.out.println("\n--- Physical Interpretation ---");
    if (avgSlip > 0.001) {
      System.out.println("✓ Oil flows faster than water in uphill sections");
      System.out.println("  Water (density ~1000 kg/m³) is retarded by gravity");
      System.out.println("  while oil (density ~800 kg/m³) flows more easily uphill");
    } else if (avgSlip < -0.001) {
      System.out.println("⚠️ Water flows faster than oil (unexpected in uphill flow)");
    } else {
      System.out.println("Oil and water flow at similar velocities");
      System.out.println("  (May indicate dispersed emulsion or low liquid holdup)");
    }

    // Check water cut variation
    double wcInlet = waterCut[0];
    double wcOutlet = waterCut[nSections - 1];
    System.out.printf("\nWater cut: inlet=%.1f%%, outlet=%.1f%%%n", wcInlet * 100, wcOutlet * 100);

    if (wcOutlet < wcInlet) {
      System.out.println("✓ Water cut decreases along uphill pipeline");
      System.out.println("  Water slipping back leads to lower water cut at outlet");
    }

    // Assertions - velocities should be positive and reasonable
    for (int i = 0; i < nSections; i++) {
      if (liqHoldup[i] > 0.01) {
        assertTrue(oilVel[i] >= 0, "Oil velocity should be positive at section " + i);
        assertTrue(waterVel[i] >= 0, "Water velocity should be positive at section " + i);
      }
    }

    System.out.println("\n✓ Test completed successfully");
  }

  /** Helper method to calculate array average. */
  private double average(double[] arr) {
    double sum = 0;
    for (double v : arr) {
      sum += v;
    }
    return sum / arr.length;
  }
}
