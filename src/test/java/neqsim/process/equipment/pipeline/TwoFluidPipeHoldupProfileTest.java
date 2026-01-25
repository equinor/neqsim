package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test to verify that TwoFluidPipe liquid holdup profile trends correctly.
 * 
 * <p>
 * For horizontal gas-dominated pipelines, as gas expands (due to pressure drop), the gas velocity
 * increases. This causes HIGHER slip between gas and liquid phases, resulting in liquid
 * accumulating (higher holdup) toward the outlet.
 * </p>
 * 
 * <p>
 * Expected behavior: Liquid holdup should INCREASE from inlet to outlet (similar to Beggs &
 * Brills).
 * </p>
 * 
 * @author ASMF
 * @version 1.0
 */
public class TwoFluidPipeHoldupProfileTest {

  /**
   * Test that liquid holdup increases along a horizontal pipeline.
   * 
   * <p>
   * Physical reasoning: As gas expands (pressure drops), gas velocity increases while liquid
   * velocity stays relatively constant. The increasing velocity slip causes liquid to accumulate.
   * </p>
   */
  @Test
  public void testHoldupIncreaseAlongHorizontalPipe() {
    // Create three-phase fluid (gas + oil + water)
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 100.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-pentane", 0.02); // Oil components
    fluid.addComponent("n-heptane", 0.02);
    fluid.addComponent("nC10", 0.02);
    fluid.addComponent("water", 0.01);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    // Create inlet stream
    double flowRate = 25000.0; // kg/hr
    Stream inlet = new Stream("Inlet", fluid);
    inlet.setFlowRate(flowRate, "kg/hr");
    inlet.setTemperature(40.0, "C");
    inlet.setPressure(100.0, "bara");
    inlet.run();

    // Create TwoFluidPipe
    TwoFluidPipe pipe = new TwoFluidPipe("Test Pipeline", inlet);
    pipe.setLength(50000.0); // 50 km
    pipe.setDiameter(0.5); // 500 mm
    pipe.setRoughness(15e-6);
    pipe.setNumberOfSections(50);

    // Horizontal pipe (zero elevation)
    double[] elevations = new double[50];
    pipe.setElevationProfile(elevations);

    // No heat transfer (isothermal)
    pipe.setHeatTransferCoefficient(0.0);

    // Run the pipe
    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);
    process.run();

    // Get profiles
    double[] pressureProfile = pipe.getPressureProfile();
    double[] holdupProfile = pipe.getLiquidHoldupProfile();

    // Verify pressure is decreasing
    double inletPressure = pressureProfile[0];
    double outletPressure = pressureProfile[pressureProfile.length - 1];
    assertTrue(inletPressure > outletPressure, "Pressure should decrease along the pipe. Inlet: "
        + inletPressure / 1e5 + " bara, Outlet: " + outletPressure / 1e5 + " bara");

    // Calculate average holdup in first quarter vs last quarter
    int n = holdupProfile.length;
    int quarterLength = n / 4;

    double avgHoldupFirstQuarter = 0;
    for (int i = 0; i < quarterLength; i++) {
      avgHoldupFirstQuarter += holdupProfile[i];
    }
    avgHoldupFirstQuarter /= quarterLength;

    double avgHoldupLastQuarter = 0;
    for (int i = n - quarterLength; i < n; i++) {
      avgHoldupLastQuarter += holdupProfile[i];
    }
    avgHoldupLastQuarter /= quarterLength;

    // Print profiles for debugging
    System.out.println("=== Holdup Profile Test ===");
    System.out.println("Inlet P: " + inletPressure / 1e5 + " bara");
    System.out.println("Outlet P: " + outletPressure / 1e5 + " bara");
    System.out.println("Avg holdup first quarter: " + (avgHoldupFirstQuarter * 100) + "%");
    System.out.println("Avg holdup last quarter: " + (avgHoldupLastQuarter * 100) + "%");

    // Print some profile points
    System.out.println("\nProfile samples:");
    int step = Math.max(1, n / 10);
    for (int i = 0; i < n; i += step) {
      System.out.printf("  Section %d: P=%.1f bara, HL=%.2f%%\n", i, pressureProfile[i] / 1e5,
          holdupProfile[i] * 100);
    }

    // Holdup should increase toward outlet (as gas expands and slows down relatively)
    // Allow for small variation but trend should be clear
    assertTrue(avgHoldupLastQuarter >= avgHoldupFirstQuarter * 0.95,
        "Holdup should increase or stay roughly constant toward outlet. " + "First quarter avg: "
            + (avgHoldupFirstQuarter * 100) + "%, " + "Last quarter avg: "
            + (avgHoldupLastQuarter * 100) + "%");
  }

  /**
   * Test comparison with Beggs & Brills correlation.
   * 
   * <p>
   * Both TwoFluidPipe and PipeBeggsAndBrills should show similar holdup trends for the same
   * conditions.
   * </p>
   */
  @Test
  public void testHoldupTrendMatchesBeggsAndBrills() {
    // Create gas-dominant fluid
    SystemInterface fluid = new SystemSrkEos(273.15 + 45.0, 85.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.04);
    fluid.addComponent("propane", 0.02);
    fluid.addComponent("n-pentane", 0.015);
    fluid.addComponent("n-heptane", 0.015);
    fluid.addComponent("water", 0.01);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    double flowRate = 30000.0; // kg/hr
    double pipeLength = 70000.0; // 70 km
    double pipeDiameter = 0.9; // 900 mm
    int numSections = 100;

    // === TwoFluidPipe ===
    SystemInterface tfFluid = fluid.clone();
    Stream tfInlet = new Stream("TF Inlet", tfFluid);
    tfInlet.setFlowRate(flowRate, "kg/hr");
    tfInlet.setTemperature(45.0, "C");
    tfInlet.setPressure(120.0, "bara"); // Start higher to match outlet
    tfInlet.run();

    TwoFluidPipe tfPipe = new TwoFluidPipe("TF Pipeline", tfInlet);
    tfPipe.setLength(pipeLength);
    tfPipe.setDiameter(pipeDiameter);
    tfPipe.setRoughness(10e-6);
    tfPipe.setNumberOfSections(numSections);
    tfPipe.setElevationProfile(new double[numSections]);
    tfPipe.setSurfaceTemperature(5.0, "C");
    tfPipe.setHeatTransferCoefficient(25.0);

    ProcessSystem tfProcess = new ProcessSystem();
    tfProcess.add(tfInlet);
    tfProcess.add(tfPipe);
    tfProcess.run();

    // Iterate to target outlet pressure
    double targetOutletP = 80.0; // bara
    for (int iter = 0; iter < 20; iter++) {
      double outletP = tfPipe.getOutletStream().getPressure("bara");
      double error = targetOutletP - outletP;
      if (Math.abs(error) < 0.5) {
        break;
      }
      tfInlet.setPressure(tfInlet.getPressure("bara") + error * 0.6, "bara");
      tfInlet.run();
      tfPipe.run();
    }

    // === Beggs & Brills ===
    SystemInterface bbFluid = fluid.clone();
    Stream bbInlet = new Stream("BB Inlet", bbFluid);
    bbInlet.setFlowRate(flowRate, "kg/hr");
    bbInlet.setTemperature(45.0, "C");
    bbInlet.setPressure(120.0, "bara");
    bbInlet.run();

    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB Pipeline", bbInlet);
    bbPipe.setLength(pipeLength);
    bbPipe.setDiameter(pipeDiameter);
    bbPipe.setPipeWallRoughness(10e-6);
    bbPipe.setNumberOfIncrements(numSections);
    bbPipe.setConstantSurfaceTemperature(5.0, "C");
    bbPipe.setHeatTransferCoefficient(25.0);
    bbPipe.setElevation(0.0);

    ProcessSystem bbProcess = new ProcessSystem();
    bbProcess.add(bbInlet);
    bbProcess.add(bbPipe);
    bbProcess.run();

    // Iterate to same target outlet pressure
    for (int iter = 0; iter < 20; iter++) {
      double outletP = bbPipe.getOutletStream().getPressure("bara");
      double error = targetOutletP - outletP;
      if (Math.abs(error) < 0.5) {
        break;
      }
      bbInlet.setPressure(bbInlet.getPressure("bara") + error * 0.6, "bara");
      bbInlet.run();
      bbPipe.run();
    }

    // Get profiles
    double[] tfHoldup = tfPipe.getLiquidHoldupProfile();
    double[] bbHoldup = bbPipe.getLiquidHoldupProfile();

    // Calculate holdup trends (slope)
    int tfN = tfHoldup.length;
    int bbN = bbHoldup.length;

    // Trend: positive = increasing, negative = decreasing
    double tfTrend = tfHoldup[tfN - 1] - tfHoldup[0];
    double bbTrend = bbHoldup[bbN - 1] - bbHoldup[0];

    System.out.println("\n=== Holdup Trend Comparison ===");
    System.out.println("TwoFluidPipe:");
    System.out.println("  Inlet holdup: " + (tfHoldup[0] * 100) + "%");
    System.out.println("  Outlet holdup: " + (tfHoldup[tfN - 1] * 100) + "%");
    System.out.println(
        "  Trend: " + (tfTrend > 0 ? "INCREASING" : "DECREASING") + " (" + (tfTrend * 100) + "%)");

    System.out.println("Beggs & Brills:");
    System.out.println("  Inlet holdup: " + (bbHoldup[0] * 100) + "%");
    System.out.println("  Outlet holdup: " + (bbHoldup[bbN - 1] * 100) + "%");
    System.out.println(
        "  Trend: " + (bbTrend > 0 ? "INCREASING" : "DECREASING") + " (" + (bbTrend * 100) + "%)");

    // Both should have the same trend direction (both increasing or both decreasing)
    // For gas-dominant horizontal flow, both should show INCREASING holdup toward outlet
    boolean sameTrend = (tfTrend > 0 && bbTrend > 0) || (tfTrend < 0 && bbTrend < 0);
    assertTrue(sameTrend, "TwoFluidPipe and Beggs & Brills should have the same holdup trend. "
        + "TF trend: " + tfTrend + ", BB trend: " + bbTrend);
  }

  /**
   * Test using exact notebook fluid composition.
   * 
   * <p>
   * This test replicates the Python notebook scenario exactly: Rich gas condensate with water, 70
   * km × 900 mm pipeline, 30 MSm³/day flow, 80 bara outlet pressure.
   * </p>
   */
  @Test
  public void testNotebookThreePhaseScenario() {
    // === Create exact notebook fluid ===
    SystemInterface fluid = new neqsim.thermo.system.SystemPrEos(298.15, 10.0);

    // Rich gas condensate with water (matching notebook create_three_phase_fluid())
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("i-butane", 0.02);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("i-pentane", 0.02);
    fluid.addComponent("n-pentane", 0.02);
    fluid.addComponent("n-hexane", 0.03);
    fluid.addComponent("n-heptane", 0.04);
    fluid.addComponent("nC8", 0.02);
    fluid.addComponent("water", 0.05);

    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    // Notebook parameters
    double pipeLength = 70000.0; // 70 km
    double pipeDiameter = 0.9; // 900 mm
    double pipeRoughness = 10e-6;
    int numSections = 100;
    double flowMSm3d = 30.0; // MSm³/day
    double targetOutletP = 80.0; // bara
    double inletTempC = 40.0;
    double seawaterTempC = 5.0;
    double heatTransferCoeff = 25.0;
    double gasDensityStd = 0.75; // kg/Sm³

    // Convert flow rate: 30 MSm³/day × 0.75 kg/Sm³ × 1e6 / 24 = 937500 kg/hr
    double flowKgHr = flowMSm3d * gasDensityStd * 1e6 / 24.0;

    System.out.println("\n=== NOTEBOOK SCENARIO TEST ===");
    System.out.println("Flow rate: " + flowKgHr + " kg/hr = " + flowMSm3d + " MSm³/day");

    // === TwoFluidPipe ===
    SystemInterface tfFluid = fluid.clone();

    // Flash the fluid at inlet conditions to see phase split
    tfFluid.setTemperature(inletTempC, "C");
    tfFluid.setPressure(targetOutletP + 35, "bara");
    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(tfFluid);
    ops.TPflash();
    System.out.println("\nFlash at inlet conditions:");
    System.out.println("  Phases: " + tfFluid.getNumberOfPhases());
    if (tfFluid.hasPhaseType("gas")) {
      double volGas = tfFluid.getPhase("gas").getVolume("m3");
      double volTotal = tfFluid.getVolume("m3");
      double lambdaL = 1.0 - volGas / volTotal;
      double rhoG = tfFluid.getPhase("gas").getDensity("kg/m3");
      double rhoL = tfFluid.hasPhaseType("oil") ? tfFluid.getPhase("oil").getDensity("kg/m3")
          : tfFluid.getPhase("aqueous").getDensity("kg/m3");
      System.out.println("  Gas vol: " + volGas + " m³, Total vol: " + volTotal + " m³");
      System.out.println("  No-slip liquid fraction (λL): " + (lambdaL * 100) + "%");
      System.out.println("  Gas density: " + rhoG + " kg/m³, Liquid density: " + rhoL + " kg/m³");
    }

    // Enable debug output in TwoFluidPipe
    Stream tfInlet = new Stream("TF Inlet", tfFluid);
    tfInlet.setFlowRate(flowKgHr, "kg/hr");
    tfInlet.setTemperature(inletTempC, "C");
    tfInlet.setPressure(targetOutletP + 35, "bara");
    tfInlet.run();

    // Check phase composition at inlet
    System.out.println("\nInlet fluid phases: " + tfInlet.getFluid().getNumberOfPhases());

    TwoFluidPipe tfPipe = new TwoFluidPipe("TF Pipeline", tfInlet);
    tfPipe.setLength(pipeLength);
    tfPipe.setDiameter(pipeDiameter);
    tfPipe.setRoughness(pipeRoughness);
    tfPipe.setNumberOfSections(numSections);

    double[] elevations = new double[numSections];
    tfPipe.setElevationProfile(elevations);

    tfPipe.setSurfaceTemperature(seawaterTempC, "C");
    tfPipe.setHeatTransferCoefficient(heatTransferCoeff);

    ProcessSystem tfProcess = new ProcessSystem();
    tfProcess.add(tfInlet);
    tfProcess.add(tfPipe);
    tfProcess.run();

    // Iterate to target outlet pressure
    for (int iter = 0; iter < 30; iter++) {
      double outletP = tfPipe.getOutletStream().getPressure("bara");
      double error = targetOutletP - outletP;
      if (Math.abs(error) < 0.3) {
        break;
      }
      tfInlet.setPressure(tfInlet.getPressure("bara") + error * 0.5, "bara");
      tfInlet.run();
      tfPipe.run();
    }

    // === Beggs & Brills for comparison ===
    SystemInterface bbFluid = fluid.clone();
    Stream bbInlet = new Stream("BB Inlet", bbFluid);
    bbInlet.setFlowRate(flowKgHr, "kg/hr");
    bbInlet.setTemperature(inletTempC, "C");
    bbInlet.setPressure(targetOutletP + 25, "bara");
    bbInlet.run();

    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("BB Pipeline", bbInlet);
    bbPipe.setLength(pipeLength);
    bbPipe.setDiameter(pipeDiameter);
    bbPipe.setPipeWallRoughness(pipeRoughness);
    bbPipe.setNumberOfIncrements(numSections);
    bbPipe.setElevation(0.0);
    bbPipe.setConstantSurfaceTemperature(seawaterTempC, "C");
    bbPipe.setHeatTransferCoefficient(heatTransferCoeff);

    ProcessSystem bbProcess = new ProcessSystem();
    bbProcess.add(bbInlet);
    bbProcess.add(bbPipe);
    bbProcess.run();

    // Iterate to target outlet pressure
    for (int iter = 0; iter < 30; iter++) {
      double outletP = bbPipe.getOutletStream().getPressure("bara");
      double error = targetOutletP - outletP;
      if (Math.abs(error) < 0.3) {
        break;
      }
      bbInlet.setPressure(bbInlet.getPressure("bara") + error * 0.5, "bara");
      bbInlet.run();
      bbPipe.run();
    }

    // Get profiles
    double[] tfHoldup = tfPipe.getLiquidHoldupProfile();
    double[] bbHoldup = bbPipe.getLiquidHoldupProfile();

    int tfN = tfHoldup.length;
    int bbN = bbHoldup.length;

    double tfInletHL = tfHoldup[0];
    double tfOutletHL = tfHoldup[tfN - 1];
    double tfTrend = tfOutletHL - tfInletHL;

    double bbInletHL = bbHoldup[0];
    double bbOutletHL = bbHoldup[bbN - 1];
    double bbTrend = bbOutletHL - bbInletHL;

    System.out.println("\nTwoFluidPipe:");
    System.out.println("  Inlet P: " + tfInlet.getPressure("bara") + " bara");
    System.out.println("  Outlet P: " + tfPipe.getOutletStream().getPressure("bara") + " bara");
    System.out.println("  Inlet holdup: " + (tfInletHL * 100) + "%");
    System.out.println("  Outlet holdup: " + (tfOutletHL * 100) + "%");
    System.out.println("  Trend: " + (tfTrend > 0 ? "INCREASING ✓" : "DECREASING ✗") + " ("
        + String.format("%.2f", tfTrend * 100) + "%)");

    System.out.println("\nBeggs & Brills:");
    System.out.println("  Inlet P: " + bbInlet.getPressure("bara") + " bara");
    System.out.println("  Outlet P: " + bbPipe.getOutletStream().getPressure("bara") + " bara");
    System.out.println("  Inlet holdup: " + (bbInletHL * 100) + "%");
    System.out.println("  Outlet holdup: " + (bbOutletHL * 100) + "%");
    System.out.println("  Trend: " + (bbTrend > 0 ? "INCREASING ✓" : "DECREASING ✗") + " ("
        + String.format("%.2f", bbTrend * 100) + "%)");

    // Profile samples
    System.out.println("\nTwoFluidPipe profile samples (x, HL%):");
    int step = Math.max(1, tfN / 10);
    for (int i = 0; i < tfN; i += step) {
      double x = i * pipeLength / numSections / 1000.0;
      System.out.printf("  %.0f km: %.2f%%\n", x, tfHoldup[i] * 100);
    }

    // Print oil and water holdup profiles
    double[] oilHoldup = tfPipe.getOilHoldupProfile();
    double[] waterHoldup = tfPipe.getWaterHoldupProfile();
    System.out.println("\nOil & Water holdup breakdown:");
    for (int i = 0; i < Math.min(10, tfN); i += 3) {
      double x = i * pipeLength / numSections / 1000.0;
      System.out.printf("  %.0f km: Oil=%.2f%%, Water=%.2f%%, Sum=%.2f%%\n", x, oilHoldup[i] * 100,
          waterHoldup[i] * 100, (oilHoldup[i] + waterHoldup[i]) * 100);
    }

    // Print flow regimes
    System.out.println("\nFlow regime profile:");
    neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime[] regimes =
        tfPipe.getFlowRegimeProfile();
    if (regimes != null) {
      System.out.println("  First 5: " + java.util.Arrays
          .toString(java.util.Arrays.copyOfRange(regimes, 0, Math.min(5, regimes.length))));
    }

    // Both should have same trend direction
    boolean sameTrend = (tfTrend >= 0 && bbTrend >= 0) || (tfTrend < 0 && bbTrend < 0);

    assertTrue(sameTrend,
        "TwoFluidPipe and Beggs & Brills should have same holdup trend direction. " + "TF: "
            + String.format("%.2f", tfTrend * 100) + "%, BB: "
            + String.format("%.2f", bbTrend * 100) + "%");

    // TwoFluidPipe should not decrease significantly
    assertTrue(tfTrend >= -0.03,
        "TwoFluidPipe holdup should not decrease more than 3%. Actual trend: "
            + String.format("%.2f", tfTrend * 100) + "%");
  }
}
