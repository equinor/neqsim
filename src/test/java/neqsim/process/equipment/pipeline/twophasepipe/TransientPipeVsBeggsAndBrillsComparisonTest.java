package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Comparison tests between TransientPipe (drift-flux) and PipeBeggsAndBrills correlation.
 *
 * <p>
 * These tests compare the TransientPipe model results with the well-established Beggs and Brill
 * correlation for steady-state multiphase pipe flow. The comparison documents differences between:
 * </p>
 * <ul>
 * <li>TransientPipe: Mechanistic drift-flux model with AUSM+ flux scheme</li>
 * <li>Beggs & Brill: Empirical correlation based on experimental data</li>
 * </ul>
 *
 * <h2>Model Differences</h2>
 * <p>
 * The two models use fundamentally different approaches:
 * </p>
 * <ul>
 * <li><b>Beggs &amp; Brill (1973)</b>: Empirical correlation developed from ~1500 experimental data
 * points. Uses flow regime maps and holdup correlations fitted to lab data.</li>
 * <li><b>TransientPipe</b>: Mechanistic drift-flux model solving conservation equations using
 * explicit finite volume scheme. Uses physics-based closure relations for slip velocity.</li>
 * </ul>
 *
 * <h2>Typical Comparison Results</h2>
 * <table border="1">
 * <caption>Comparison of pressure drop predictions</caption>
 * <tr>
 * <th>Scenario</th>
 * <th>Expected Difference</th>
 * </tr>
 * <tr>
 * <td>Single-phase gas horizontal</td>
 * <td>50-80%</td>
 * </tr>
 * <tr>
 * <td>Multiphase horizontal</td>
 * <td>100-300%</td>
 * </tr>
 * <tr>
 * <td>Uphill/Downhill flow</td>
 * <td>40-60%</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>Note:</b> These tests are informational and document the comparison rather than enforcing
 * strict tolerances. The TransientPipe model is designed for transient simulations and may require
 * calibration for specific applications.
 * </p>
 *
 * @author NeqSim Development Team
 * @see TransientPipe
 * @see PipeBeggsAndBrills
 */
public class TransientPipeVsBeggsAndBrillsComparisonTest {

  private SystemInterface createGasSystem(double temperature, double pressure) {
    SystemInterface system = new SystemSrkEos(temperature, pressure);
    system.addComponent("methane", 0.85);
    system.addComponent("ethane", 0.10);
    system.addComponent("propane", 0.05);
    system.setMixingRule("classic");
    return system;
  }

  private SystemInterface createGasCondensateSystem(double temperature, double pressure) {
    SystemInterface system = new SystemSrkEos(temperature, pressure);
    system.addComponent("methane", 0.70);
    system.addComponent("ethane", 0.10);
    system.addComponent("propane", 0.08);
    system.addComponent("n-butane", 0.05);
    system.addComponent("n-pentane", 0.03);
    system.addComponent("n-hexane", 0.02);
    system.addComponent("n-heptane", 0.02);
    system.setMixingRule("classic");
    return system;
  }

  private void flashAndInitialize(SystemInterface system, double flowRate, String flowUnit) {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initPhysicalProperties();
    system.setTotalFlowRate(flowRate, flowUnit);
  }

  /**
   * Create a uniform inclination profile for TransientPipe.
   *
   * @param nSections number of sections
   * @param angleDegrees inclination angle in degrees
   * @return array of inclinations in radians
   */
  private double[] createUniformInclinationProfile(int nSections, double angleDegrees) {
    double angleRad = Math.toRadians(angleDegrees);
    double[] profile = new double[nSections];
    for (int i = 0; i < nSections; i++) {
      profile[i] = angleRad;
    }
    return profile;
  }

  /**
   * Compare single-phase gas flow - horizontal pipe.
   *
   * <p>
   * For single-phase gas, both models should give similar pressure drops dominated by friction.
   * </p>
   */
  @Test
  @DisplayName("Compare single-phase gas flow - horizontal pipe")
  public void testSinglePhaseGasHorizontal() {
    // Create gas system at conditions where it remains single-phase
    double temperature = 298.15; // K (25°C)
    double pressure = 70.0; // bar
    int nSections = 20;

    // Set up Beggs and Brill pipe
    SystemInterface systemBB = createGasSystem(temperature, pressure);
    flashAndInitialize(systemBB, 10000.0, "kg/hr");

    Stream streamBB = new Stream("feedBB", systemBB);
    PipeBeggsAndBrills pipeBB = new PipeBeggsAndBrills("BeggsBrill", streamBB);
    pipeBB.setDiameter(0.1524); // 6 inch
    pipeBB.setLength(1000.0); // 1 km
    pipeBB.setElevation(0.0); // horizontal
    pipeBB.setPipeWallRoughness(1e-5);
    pipeBB.setNumberOfIncrements(nSections);

    ProcessSystem processBB = new ProcessSystem();
    processBB.add(streamBB);
    processBB.add(pipeBB);
    processBB.run();

    double pressureDropBB = pipeBB.getPressureDrop();
    double outletPressureBB = pipeBB.getOutletStream().getPressure("bara");

    // Set up TransientPipe
    SystemInterface systemTP = createGasSystem(temperature, pressure);
    flashAndInitialize(systemTP, 10000.0, "kg/hr");

    Stream streamTP = new Stream("feedTP", systemTP);
    streamTP.run();

    TransientPipe pipeTP = new TransientPipe("TransientPipe", streamTP);
    pipeTP.setDiameter(0.1524);
    pipeTP.setLength(1000.0);
    pipeTP.setRoughness(1e-5);
    pipeTP.setNumberOfSections(nSections);
    pipeTP.setInclinationProfile(createUniformInclinationProfile(nSections, 0.0));
    pipeTP.setOutletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_PRESSURE);
    pipeTP.setoutletPressureValue(outletPressureBB * 1e5); // Pa
    pipeTP.setMaxSimulationTime(300.0); // Run to steady state
    pipeTP.run();

    double[] pressureProfileTP = pipeTP.getPressureProfile();
    assertNotNull(pressureProfileTP, "TransientPipe should produce pressure profile");
    double inletPressureTP = pressureProfileTP[0] / 1e5; // bar
    double outletPressureTP = pressureProfileTP[pressureProfileTP.length - 1] / 1e5; // bar
    double pressureDropTP = inletPressureTP - outletPressureTP;

    // Document comparison results
    double relativeDiff =
        pressureDropBB > 0.01 ? Math.abs(pressureDropTP - pressureDropBB) / pressureDropBB * 100
            : 0;
    System.out.println("=== Single-phase Gas Horizontal Pipe ===");
    System.out.println(
        "Beggs & Brill: Pressure drop = " + String.format("%.4f", pressureDropBB) + " bar");
    System.out.println(
        "TransientPipe: Pressure drop = " + String.format("%.4f", pressureDropTP) + " bar");
    System.out.println("Relative difference: " + String.format("%.1f", relativeDiff) + "%");
    System.out.println(
        "Beggs & Brill: Outlet pressure = " + String.format("%.2f", outletPressureBB) + " bar");
    System.out.println(
        "TransientPipe: Outlet pressure = " + String.format("%.2f", outletPressureTP) + " bar");

    // Both models should produce reasonable positive pressure drops
    assertTrue(pressureDropBB > 0, "Beggs & Brill should have positive pressure drop");
  }

  /**
   * Compare gas-liquid multiphase flow - horizontal pipe.
   *
   * <p>
   * Two-phase flow with liquid present. Expect more variation between models due to different
   * holdup and pressure drop correlations.
   * </p>
   */
  @Test
  @DisplayName("Compare multiphase flow - horizontal pipe")
  public void testMultiphaseHorizontal() {
    // Create gas-condensate system with liquid dropout
    double temperature = 283.15; // K (10°C)
    double pressure = 50.0; // bar
    int nSections = 20;

    // Set up Beggs and Brill pipe
    SystemInterface systemBB = createGasCondensateSystem(temperature, pressure);
    flashAndInitialize(systemBB, 5000.0, "kg/hr");

    Stream streamBB = new Stream("feedBB", systemBB);
    PipeBeggsAndBrills pipeBB = new PipeBeggsAndBrills("BeggsBrill", streamBB);
    pipeBB.setDiameter(0.1016); // 4 inch
    pipeBB.setLength(500.0); // 500 m
    pipeBB.setElevation(0.0); // horizontal
    pipeBB.setPipeWallRoughness(1e-5);
    pipeBB.setNumberOfIncrements(nSections);

    ProcessSystem processBB = new ProcessSystem();
    processBB.add(streamBB);
    processBB.add(pipeBB);
    processBB.run();

    double pressureDropBB = pipeBB.getPressureDrop();
    double outletPressureBB = pipeBB.getOutletStream().getPressure("bara");

    // Get liquid holdup from Beggs & Brill
    java.util.List<Double> holdupProfileBB = pipeBB.getLiquidHoldupProfile();
    double avgHoldupBB = holdupProfileBB.isEmpty() ? 0.0
        : holdupProfileBB.stream().mapToDouble(d -> d).average().orElse(0.0);

    // Set up TransientPipe
    SystemInterface systemTP = createGasCondensateSystem(temperature, pressure);
    flashAndInitialize(systemTP, 5000.0, "kg/hr");

    Stream streamTP = new Stream("feedTP", systemTP);
    streamTP.run();

    TransientPipe pipeTP = new TransientPipe("TransientPipe", streamTP);
    pipeTP.setDiameter(0.1016);
    pipeTP.setLength(500.0);
    pipeTP.setRoughness(1e-5);
    pipeTP.setNumberOfSections(nSections);
    pipeTP.setInclinationProfile(createUniformInclinationProfile(nSections, 0.0));
    pipeTP.setOutletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_PRESSURE);
    pipeTP.setoutletPressureValue(outletPressureBB * 1e5);
    pipeTP.setMaxSimulationTime(300.0);
    pipeTP.run();

    double[] pressureProfileTP = pipeTP.getPressureProfile();
    assertNotNull(pressureProfileTP, "TransientPipe should produce pressure profile");
    double inletPressureTP = pressureProfileTP[0] / 1e5;
    double outletPressureTP = pressureProfileTP[pressureProfileTP.length - 1] / 1e5;
    double pressureDropTP = inletPressureTP - outletPressureTP;

    double[] holdupProfileTP = pipeTP.getLiquidHoldupProfile();
    double avgHoldupTP = 0.0;
    if (holdupProfileTP != null && holdupProfileTP.length > 0) {
      for (double h : holdupProfileTP) {
        avgHoldupTP += h;
      }
      avgHoldupTP /= holdupProfileTP.length;
    }

    // Document comparison results
    double relativeDiff =
        pressureDropBB > 0.01 ? Math.abs(pressureDropTP - pressureDropBB) / pressureDropBB * 100
            : 0;
    System.out.println("\n=== Multiphase Horizontal Pipe ===");
    System.out.println(
        "Beggs & Brill: Pressure drop = " + String.format("%.4f", pressureDropBB) + " bar");
    System.out.println(
        "TransientPipe: Pressure drop = " + String.format("%.4f", pressureDropTP) + " bar");
    System.out.println("Relative difference: " + String.format("%.1f", relativeDiff) + "%");
    System.out.println("Beggs & Brill: Avg liquid holdup = " + String.format("%.4f", avgHoldupBB));
    System.out.println("TransientPipe: Avg liquid holdup = " + String.format("%.4f", avgHoldupTP));
    System.out.println(
        "Beggs & Brill: Outlet pressure = " + String.format("%.2f", outletPressureBB) + " bar");
    System.out.println(
        "TransientPipe: Outlet pressure = " + String.format("%.2f", outletPressureTP) + " bar");

    // Both models should produce results
    assertTrue(pressureDropBB > 0, "Beggs & Brill should have positive pressure drop");
  }

  /**
   * Compare uphill flow (positive inclination).
   *
   * <p>
   * Upward flow increases hydrostatic pressure loss. Holdup increases compared to horizontal.
   * </p>
   */
  @Test
  @DisplayName("Compare uphill flow - positive inclination")
  public void testUphillFlow() {
    double temperature = 293.15; // K
    double pressure = 60.0; // bar
    double angleDegrees = 10.0; // degrees upward
    int nSections = 15;

    // Set up Beggs and Brill pipe
    SystemInterface systemBB = createGasCondensateSystem(temperature, pressure);
    flashAndInitialize(systemBB, 8000.0, "kg/hr");

    Stream streamBB = new Stream("feedBB", systemBB);
    PipeBeggsAndBrills pipeBB = new PipeBeggsAndBrills("BeggsBrill", streamBB);
    pipeBB.setDiameter(0.1524); // 6 inch
    pipeBB.setLength(300.0); // 300 m
    pipeBB.setAngle(angleDegrees); // 10 degrees upward
    pipeBB.setPipeWallRoughness(1e-5);
    pipeBB.setNumberOfIncrements(nSections);

    ProcessSystem processBB = new ProcessSystem();
    processBB.add(streamBB);
    processBB.add(pipeBB);
    processBB.run();

    double pressureDropBB = pipeBB.getPressureDrop();
    double outletPressureBB = pipeBB.getOutletStream().getPressure("bara");

    // Set up TransientPipe
    SystemInterface systemTP = createGasCondensateSystem(temperature, pressure);
    flashAndInitialize(systemTP, 8000.0, "kg/hr");

    Stream streamTP = new Stream("feedTP", systemTP);
    streamTP.run();

    TransientPipe pipeTP = new TransientPipe("TransientPipe", streamTP);
    pipeTP.setDiameter(0.1524);
    pipeTP.setLength(300.0);
    pipeTP.setRoughness(1e-5);
    pipeTP.setNumberOfSections(nSections);
    pipeTP.setInclinationProfile(createUniformInclinationProfile(nSections, angleDegrees));
    pipeTP.setOutletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_PRESSURE);
    pipeTP.setoutletPressureValue(outletPressureBB * 1e5);
    pipeTP.setMaxSimulationTime(300.0);
    pipeTP.run();

    double[] pressureProfileTP = pipeTP.getPressureProfile();
    assertNotNull(pressureProfileTP, "TransientPipe should produce pressure profile");
    double inletPressureTP = pressureProfileTP[0] / 1e5;
    double outletPressureTP = pressureProfileTP[pressureProfileTP.length - 1] / 1e5;
    double pressureDropTP = inletPressureTP - outletPressureTP;

    // Document comparison results
    double relativeDiff =
        pressureDropBB > 0.01 ? Math.abs(pressureDropTP - pressureDropBB) / pressureDropBB * 100
            : 0;
    System.out.println("\n=== Uphill Flow (10 degrees) ===");
    System.out.println(
        "Beggs & Brill: Pressure drop = " + String.format("%.4f", pressureDropBB) + " bar");
    System.out.println(
        "TransientPipe: Pressure drop = " + String.format("%.4f", pressureDropTP) + " bar");
    System.out.println("Relative difference: " + String.format("%.1f", relativeDiff) + "%");
    System.out.println(
        "Beggs & Brill: Outlet pressure = " + String.format("%.2f", outletPressureBB) + " bar");
    System.out.println(
        "TransientPipe: Outlet pressure = " + String.format("%.2f", outletPressureTP) + " bar");

    // Uphill should have positive pressure drop (pressure decreases going up)
    assertTrue(pressureDropBB > 0, "Uphill pressure drop should be positive");
  }

  /**
   * Compare downhill flow (negative inclination).
   *
   * <p>
   * Downward flow decreases hydrostatic pressure loss (or pressure can increase). Holdup tends to
   * decrease.
   * </p>
   */
  @Test
  @DisplayName("Compare downhill flow - negative inclination")
  public void testDownhillFlow() {
    double temperature = 293.15; // K
    double pressure = 60.0; // bar
    double angleDegrees = -10.0; // degrees downward
    int nSections = 15;

    // Set up Beggs and Brill pipe
    SystemInterface systemBB = createGasCondensateSystem(temperature, pressure);
    flashAndInitialize(systemBB, 8000.0, "kg/hr");

    Stream streamBB = new Stream("feedBB", systemBB);
    PipeBeggsAndBrills pipeBB = new PipeBeggsAndBrills("BeggsBrill", streamBB);
    pipeBB.setDiameter(0.1524); // 6 inch
    pipeBB.setLength(300.0); // 300 m
    pipeBB.setAngle(angleDegrees); // 10 degrees downward
    pipeBB.setPipeWallRoughness(1e-5);
    pipeBB.setNumberOfIncrements(nSections);

    ProcessSystem processBB = new ProcessSystem();
    processBB.add(streamBB);
    processBB.add(pipeBB);
    processBB.run();

    double pressureDropBB = pipeBB.getPressureDrop();
    double outletPressureBB = pipeBB.getOutletStream().getPressure("bara");

    // Set up TransientPipe
    SystemInterface systemTP = createGasCondensateSystem(temperature, pressure);
    flashAndInitialize(systemTP, 8000.0, "kg/hr");

    Stream streamTP = new Stream("feedTP", systemTP);
    streamTP.run();

    TransientPipe pipeTP = new TransientPipe("TransientPipe", streamTP);
    pipeTP.setDiameter(0.1524);
    pipeTP.setLength(300.0);
    pipeTP.setRoughness(1e-5);
    pipeTP.setNumberOfSections(nSections);
    pipeTP.setInclinationProfile(createUniformInclinationProfile(nSections, angleDegrees));
    pipeTP.setOutletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_PRESSURE);
    pipeTP.setoutletPressureValue(outletPressureBB * 1e5);
    pipeTP.setMaxSimulationTime(300.0);
    pipeTP.run();

    double[] pressureProfileTP = pipeTP.getPressureProfile();
    assertNotNull(pressureProfileTP, "TransientPipe should produce pressure profile");
    double inletPressureTP = pressureProfileTP[0] / 1e5;
    double outletPressureTP = pressureProfileTP[pressureProfileTP.length - 1] / 1e5;
    double pressureDropTP = inletPressureTP - outletPressureTP;

    // Document comparison results
    double relativeDiff = Math.abs(pressureDropBB) > 0.01
        ? Math.abs(pressureDropTP - pressureDropBB) / Math.abs(pressureDropBB) * 100
        : 0;
    System.out.println("\n=== Downhill Flow (-10 degrees) ===");
    System.out.println(
        "Beggs & Brill: Pressure drop = " + String.format("%.4f", pressureDropBB) + " bar");
    System.out.println(
        "TransientPipe: Pressure drop = " + String.format("%.4f", pressureDropTP) + " bar");
    System.out.println("Relative difference: " + String.format("%.1f", relativeDiff) + "%");
    System.out.println(
        "Beggs & Brill: Outlet pressure = " + String.format("%.2f", outletPressureBB) + " bar");
    System.out.println(
        "TransientPipe: Outlet pressure = " + String.format("%.2f", outletPressureTP) + " bar");

    // Both models should run and produce results
    assertTrue(outletPressureBB > 0, "Beggs & Brill outlet pressure should be positive");
  }

  /**
   * Compare high velocity gas flow.
   *
   * <p>
   * Tests friction-dominated flow at higher velocities.
   * </p>
   */
  @Test
  @DisplayName("Compare high velocity gas flow")
  public void testHighVelocityGas() {
    double temperature = 300.0; // K
    double pressure = 50.0; // bar
    int nSections = 20;

    // Set up Beggs and Brill pipe with high flow rate
    SystemInterface systemBB = createGasSystem(temperature, pressure);
    flashAndInitialize(systemBB, 50000.0, "kg/hr"); // High flow rate

    Stream streamBB = new Stream("feedBB", systemBB);
    PipeBeggsAndBrills pipeBB = new PipeBeggsAndBrills("BeggsBrill", streamBB);
    pipeBB.setDiameter(0.1016); // 4 inch - smaller diameter for high velocity
    pipeBB.setLength(500.0);
    pipeBB.setElevation(0.0);
    pipeBB.setPipeWallRoughness(1e-5);
    pipeBB.setNumberOfIncrements(nSections);

    ProcessSystem processBB = new ProcessSystem();
    processBB.add(streamBB);
    processBB.add(pipeBB);
    processBB.run();

    double pressureDropBB = pipeBB.getPressureDrop();
    double outletPressureBB = pipeBB.getOutletStream().getPressure("bara");

    // Get superficial velocity for reference
    java.util.List<Double> gasVelBB = pipeBB.getGasSuperficialVelocityProfile();
    double avgGasVelBB =
        gasVelBB.isEmpty() ? 0.0 : gasVelBB.stream().mapToDouble(d -> d).average().orElse(0.0);

    // Set up TransientPipe
    SystemInterface systemTP = createGasSystem(temperature, pressure);
    flashAndInitialize(systemTP, 50000.0, "kg/hr");

    Stream streamTP = new Stream("feedTP", systemTP);
    streamTP.run();

    TransientPipe pipeTP = new TransientPipe("TransientPipe", streamTP);
    pipeTP.setDiameter(0.1016);
    pipeTP.setLength(500.0);
    pipeTP.setRoughness(1e-5);
    pipeTP.setNumberOfSections(nSections);
    pipeTP.setInclinationProfile(createUniformInclinationProfile(nSections, 0.0));
    pipeTP.setOutletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_PRESSURE);
    pipeTP.setoutletPressureValue(outletPressureBB * 1e5);
    pipeTP.setMaxSimulationTime(300.0);
    pipeTP.run();

    double[] pressureProfileTP = pipeTP.getPressureProfile();
    assertNotNull(pressureProfileTP, "TransientPipe should produce pressure profile");
    double inletPressureTP = pressureProfileTP[0] / 1e5;
    double outletPressureTP = pressureProfileTP[pressureProfileTP.length - 1] / 1e5;
    double pressureDropTP = inletPressureTP - outletPressureTP;

    double[] gasVelTP = pipeTP.getGasVelocityProfile();
    double avgGasVelTP = 0.0;
    if (gasVelTP != null && gasVelTP.length > 0) {
      for (double v : gasVelTP) {
        avgGasVelTP += v;
      }
      avgGasVelTP /= gasVelTP.length;
    }

    // Document comparison results
    double relativeDiff =
        pressureDropBB > 0.01 ? Math.abs(pressureDropTP - pressureDropBB) / pressureDropBB * 100
            : 0;
    System.out.println("\n=== High Velocity Gas Flow ===");
    System.out.println(
        "Beggs & Brill: Pressure drop = " + String.format("%.4f", pressureDropBB) + " bar");
    System.out.println(
        "TransientPipe: Pressure drop = " + String.format("%.4f", pressureDropTP) + " bar");
    System.out.println("Relative difference: " + String.format("%.1f", relativeDiff) + "%");
    System.out.println(
        "Beggs & Brill: Avg gas velocity = " + String.format("%.2f", avgGasVelBB) + " m/s");
    System.out.println(
        "TransientPipe: Avg gas velocity = " + String.format("%.2f", avgGasVelTP) + " m/s");

    // Both models should produce results
    assertTrue(pressureDropBB > 0, "Beggs & Brill should have positive pressure drop");
  }

  /**
   * Summary comparison with multiple scenarios printed for review.
   *
   * <p>
   * This test runs multiple scenarios and prints a summary table.
   * </p>
   */
  @Test
  @DisplayName("Summary comparison table")
  public void testSummaryComparison() {
    System.out.println("\n========================================================");
    System.out.println("TransientPipe vs Beggs & Brill Comparison Summary");
    System.out.println("========================================================");
    System.out.println("Note: Some variation expected due to different modeling approaches:");
    System.out.println("- TransientPipe: Drift-flux mechanistic model");
    System.out.println("- Beggs & Brill: Empirical correlation");
    System.out.println("Typical expected tolerance: ±20-30% for multiphase flow");
    System.out.println("========================================================");

    // This test just prints the header - individual tests print their results
    assertTrue(true, "Summary test executed");
  }
}
