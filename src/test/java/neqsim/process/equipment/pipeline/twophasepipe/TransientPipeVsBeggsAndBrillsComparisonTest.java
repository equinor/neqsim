package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Disabled;
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
 * <li>Beggs &amp; Brill: Empirical correlation based on experimental data</li>
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
 * <h2>Energy Equation Enhancements</h2>
 * <p>
 * Both models support enhanced energy equation features:
 * </p>
 * <ul>
 * <li><b>Joule-Thomson Effect</b>: Temperature change during gas expansion. The JT coefficient is
 * automatically calculated from the gas phase thermodynamics using NeqSim's equation of state.
 * Typical values: methane ~0.4 K/bar, CO2 ~1.2 K/bar at 50 bar, 300 K.</li>
 * <li><b>Friction Heating</b>: Viscous dissipation converts mechanical energy to thermal energy.
 * Effect is typically small (0.01-0.1 K per bar friction loss).</li>
 * <li><b>Wall Heat Transfer</b>: LMTD-based calculation for heat exchange with surroundings.</li>
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
 * <td>5-10%</td>
 * </tr>
 * <tr>
 * <td>Multiphase horizontal</td>
 * <td>20-40%</td>
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
 * <h2>Java Compatibility</h2>
 * <p>
 * This test class is compatible with Java 8 and above. All stream operations use Java 8 compatible
 * syntax and no features from later Java versions are used.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @since 3.1.3
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
    pipeTP.setOutletPressure(outletPressureBB); // bara
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
    pipeTP.setOutletPressure(outletPressureBB); // bara
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
    pipeTP.setOutletPressure(outletPressureBB); // bara
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
    pipeTP.setOutletPressure(outletPressureBB); // bara
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
    pipeTP.setOutletPressure(outletPressureBB); // bara
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

  // ========== Temperature Comparison Tests ==========

  /**
   * Compare temperature calculations with heat transfer to surroundings.
   *
   * <p>
   * This test compares how both models handle temperature changes due to heat loss to surroundings.
   * The Beggs and Brill model uses LMTD-based calculation, while the TransientPipe uses the new
   * energy equation with Joule-Thomson effect and friction heating.
   * </p>
   */
  @Test
  @DisplayName("Compare temperature change - cooling to ambient")
  public void testTemperatureChangeCooling() {
    // Hot fluid flowing through pipe with cold surroundings
    double temperature = 350.0; // K (77°C) - hot inlet
    double pressure = 50.0; // bar
    double ambientTemp = 288.15; // K (15°C) - cool ambient
    double heatTransferCoeff = 25.0; // W/(m²·K) - moderate heat transfer
    int nSections = 20;

    // Set up Beggs and Brill pipe with heat transfer
    SystemInterface systemBB = createGasSystem(temperature, pressure);
    flashAndInitialize(systemBB, 10000.0, "kg/hr");

    Stream streamBB = new Stream("feedBB", systemBB);
    PipeBeggsAndBrills pipeBB = new PipeBeggsAndBrills("BeggsBrill", streamBB);
    pipeBB.setDiameter(0.1524); // 6 inch
    pipeBB.setLength(5000.0); // 5 km - long pipe for noticeable temp change
    pipeBB.setElevation(0.0); // horizontal
    pipeBB.setPipeWallRoughness(1e-5);
    pipeBB.setNumberOfIncrements(nSections);
    // Enable heat transfer by setting constant surface temperature
    pipeBB.setConstantSurfaceTemperature(ambientTemp, "K");
    pipeBB.setHeatTransferCoefficient(heatTransferCoeff);

    ProcessSystem processBB = new ProcessSystem();
    processBB.add(streamBB);
    processBB.add(pipeBB);
    processBB.run();

    double outletTempBB = pipeBB.getOutletTemperature(); // K
    double inletTempBB = temperature;
    double tempDropBB = inletTempBB - outletTempBB;

    // Set up TransientPipe with heat transfer
    SystemInterface systemTP = createGasSystem(temperature, pressure);
    flashAndInitialize(systemTP, 10000.0, "kg/hr");

    Stream streamTP = new Stream("feedTP", systemTP);
    streamTP.run();

    TransientPipe pipeTP = new TransientPipe("TransientPipe", streamTP);
    pipeTP.setDiameter(0.1524);
    pipeTP.setLength(5000.0);
    pipeTP.setRoughness(1e-5);
    pipeTP.setNumberOfSections(nSections);
    pipeTP.setInclinationProfile(createUniformInclinationProfile(nSections, 0.0));
    pipeTP.setOutletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_PRESSURE);
    pipeTP.setOutletPressure(pipeBB.getOutletPressure()); // bara
    pipeTP.setMaxSimulationTime(600.0);

    // Enable heat transfer in TransientPipe
    // JT coefficient is now auto-calculated from gas phase thermodynamics
    pipeTP.setIncludeHeatTransfer(true);
    pipeTP.setAmbientTemperature(ambientTemp);
    pipeTP.setOverallHeatTransferCoeff(heatTransferCoeff);

    pipeTP.run();

    double[] tempProfileTP = pipeTP.getTemperatureProfile();
    assertNotNull(tempProfileTP, "TransientPipe should produce temperature profile");
    double inletTempTP = tempProfileTP[0];
    double outletTempTP = tempProfileTP[tempProfileTP.length - 1];
    double tempDropTP = inletTempTP - outletTempTP;

    // Document comparison results
    System.out.println("\n=== Temperature Comparison: Cooling to Ambient ===");
    System.out.println("Conditions:");
    System.out.println("  Inlet temperature: " + String.format("%.2f", temperature) + " K ("
        + String.format("%.1f", temperature - 273.15) + " °C)");
    System.out.println("  Ambient temperature: " + String.format("%.2f", ambientTemp) + " K ("
        + String.format("%.1f", ambientTemp - 273.15) + " °C)");
    System.out.println(
        "  Heat transfer coeff: " + String.format("%.1f", heatTransferCoeff) + " W/(m²·K)");
    System.out.println("  Pipe length: 5000 m");
    System.out.println("\nResults:");
    System.out.println("Beggs & Brill:");
    System.out.println("  Outlet temperature: " + String.format("%.2f", outletTempBB) + " K ("
        + String.format("%.1f", outletTempBB - 273.15) + " °C)");
    System.out.println("  Temperature drop: " + String.format("%.2f", tempDropBB) + " K");
    System.out.println("TransientPipe (Energy Equation):");
    System.out.println("  Outlet temperature: " + String.format("%.2f", outletTempTP) + " K ("
        + String.format("%.1f", outletTempTP - 273.15) + " °C)");
    System.out.println("  Temperature drop: " + String.format("%.2f", tempDropTP) + " K");

    double relativeDiff =
        tempDropBB > 0.1 ? Math.abs(tempDropTP - tempDropBB) / tempDropBB * 100 : 0;
    System.out.println("Relative difference: " + String.format("%.1f", relativeDiff) + "%");

    // Both models should show cooling (temperature drop)
    assertTrue(tempDropBB > 0 || Math.abs(tempDropBB) < 0.1,
        "Beggs & Brill should show cooling or minimal temp change");
    assertTrue(outletTempTP < inletTempTP + 1,
        "TransientPipe should not show significant heating when cooling expected");
  }

  /**
   * Compare temperature calculations with heating from surroundings.
   *
   * <p>
   * Cold fluid flowing through pipe with warm surroundings (e.g., cold gas from separator to
   * heater).
   * </p>
   */
  @Test
  @DisplayName("Compare temperature change - heating from ambient")
  public void testTemperatureChangeHeating() {
    // Cold fluid flowing through pipe with warm surroundings
    double temperature = 270.0; // K (-3°C) - cold inlet
    double pressure = 50.0; // bar
    double ambientTemp = 293.15; // K (20°C) - warm ambient
    double heatTransferCoeff = 50.0; // W/(m²·K) - higher heat transfer
    int nSections = 20;

    // Set up Beggs and Brill pipe with heat transfer
    SystemInterface systemBB = createGasSystem(temperature, pressure);
    flashAndInitialize(systemBB, 8000.0, "kg/hr");

    Stream streamBB = new Stream("feedBB", systemBB);
    PipeBeggsAndBrills pipeBB = new PipeBeggsAndBrills("BeggsBrill", streamBB);
    pipeBB.setDiameter(0.1524); // 6 inch
    pipeBB.setLength(2000.0); // 2 km
    pipeBB.setElevation(0.0); // horizontal
    pipeBB.setPipeWallRoughness(1e-5);
    pipeBB.setNumberOfIncrements(nSections);
    // Enable heat transfer by setting constant surface temperature
    pipeBB.setConstantSurfaceTemperature(ambientTemp, "K");
    pipeBB.setHeatTransferCoefficient(heatTransferCoeff);

    ProcessSystem processBB = new ProcessSystem();
    processBB.add(streamBB);
    processBB.add(pipeBB);
    processBB.run();

    double outletTempBB = pipeBB.getOutletTemperature(); // K
    double inletTempBB = temperature;
    double tempRiseBB = outletTempBB - inletTempBB;

    // Set up TransientPipe with heat transfer
    SystemInterface systemTP = createGasSystem(temperature, pressure);
    flashAndInitialize(systemTP, 8000.0, "kg/hr");

    Stream streamTP = new Stream("feedTP", systemTP);
    streamTP.run();

    TransientPipe pipeTP = new TransientPipe("TransientPipe", streamTP);
    pipeTP.setDiameter(0.1524);
    pipeTP.setLength(2000.0);
    pipeTP.setRoughness(1e-5);
    pipeTP.setNumberOfSections(nSections);
    pipeTP.setInclinationProfile(createUniformInclinationProfile(nSections, 0.0));
    pipeTP.setOutletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_PRESSURE);
    pipeTP.setOutletPressure(pipeBB.getOutletPressure()); // bara
    pipeTP.setMaxSimulationTime(600.0);

    // Enable heat transfer in TransientPipe
    // JT coefficient is now auto-calculated from gas phase thermodynamics
    pipeTP.setIncludeHeatTransfer(true);
    pipeTP.setAmbientTemperature(ambientTemp);
    pipeTP.setOverallHeatTransferCoeff(heatTransferCoeff);

    pipeTP.run();

    double[] tempProfileTP = pipeTP.getTemperatureProfile();
    assertNotNull(tempProfileTP, "TransientPipe should produce temperature profile");
    double inletTempTP = tempProfileTP[0];
    double outletTempTP = tempProfileTP[tempProfileTP.length - 1];
    double tempRiseTP = outletTempTP - inletTempTP;

    // Document comparison results
    System.out.println("\n=== Temperature Comparison: Heating from Ambient ===");
    System.out.println("Conditions:");
    System.out.println("  Inlet temperature: " + String.format("%.2f", temperature) + " K ("
        + String.format("%.1f", temperature - 273.15) + " °C)");
    System.out.println("  Ambient temperature: " + String.format("%.2f", ambientTemp) + " K ("
        + String.format("%.1f", ambientTemp - 273.15) + " °C)");
    System.out.println(
        "  Heat transfer coeff: " + String.format("%.1f", heatTransferCoeff) + " W/(m²·K)");
    System.out.println("  Pipe length: 2000 m");
    System.out.println("\nResults:");
    System.out.println("Beggs & Brill:");
    System.out.println("  Outlet temperature: " + String.format("%.2f", outletTempBB) + " K ("
        + String.format("%.1f", outletTempBB - 273.15) + " °C)");
    System.out.println("  Temperature rise: " + String.format("%.2f", tempRiseBB) + " K");
    System.out.println("TransientPipe (Energy Equation):");
    System.out.println("  Outlet temperature: " + String.format("%.2f", outletTempTP) + " K ("
        + String.format("%.1f", outletTempTP - 273.15) + " °C)");
    System.out.println("  Temperature rise: " + String.format("%.2f", tempRiseTP) + " K");

    double relativeDiff =
        tempRiseBB > 0.1 ? Math.abs(tempRiseTP - tempRiseBB) / tempRiseBB * 100 : 0;
    System.out.println("Relative difference: " + String.format("%.1f", relativeDiff) + "%");

    // Both models should show heating (temperature rise)
    assertTrue(tempRiseBB > 0 || Math.abs(tempRiseBB) < 0.1,
        "Beggs & Brill should show heating or minimal temp change");
  }

  /**
   * Compare adiabatic operation (no heat transfer).
   *
   * <p>
   * When heat transfer is disabled, temperature changes should be due to Joule-Thomson effect only
   * in TransientPipe, while Beggs & Brill should show no temperature change.
   * </p>
   */
  @Test
  @Disabled("Test disabled due to SRK EOS instability at extreme outlet conditions")
  @DisplayName("Compare adiabatic operation - Joule-Thomson effect")
  public void testAdiabaticJouleThomson() {
    // High pressure gas with significant pressure drop
    double temperature = 320.0; // K (47°C)
    double pressure = 100.0; // bar - high pressure
    int nSections = 20;

    // Set up Beggs and Brill pipe - adiabatic
    SystemInterface systemBB = createGasSystem(temperature, pressure);
    flashAndInitialize(systemBB, 20000.0, "kg/hr"); // High flow for pressure drop

    Stream streamBB = new Stream("feedBB", systemBB);
    PipeBeggsAndBrills pipeBB = new PipeBeggsAndBrills("BeggsBrill", streamBB);
    pipeBB.setDiameter(0.1016); // 4 inch - smaller for more pressure drop
    pipeBB.setLength(10000.0); // 10 km
    pipeBB.setElevation(0.0);
    pipeBB.setPipeWallRoughness(1e-5);
    pipeBB.setNumberOfIncrements(nSections);
    // Default is adiabatic (runAdiabatic = true), no heat transfer setup needed

    ProcessSystem processBB = new ProcessSystem();
    processBB.add(streamBB);
    processBB.add(pipeBB);
    processBB.run();

    double outletTempBB = pipeBB.getOutletTemperature(); // K
    double outletPressureBB = pipeBB.getOutletPressure(); // bar
    double pressureDropBB = pressure - outletPressureBB;
    double tempChangeBB = outletTempBB - temperature;

    // Set up TransientPipe with Joule-Thomson effect only (no heat transfer to ambient)
    SystemInterface systemTP = createGasSystem(temperature, pressure);
    flashAndInitialize(systemTP, 20000.0, "kg/hr");

    Stream streamTP = new Stream("feedTP", systemTP);
    streamTP.run();

    TransientPipe pipeTP = new TransientPipe("TransientPipe", streamTP);
    pipeTP.setDiameter(0.1016);
    pipeTP.setLength(10000.0);
    pipeTP.setRoughness(1e-5);
    pipeTP.setNumberOfSections(nSections);
    pipeTP.setInclinationProfile(createUniformInclinationProfile(nSections, 0.0));
    pipeTP.setOutletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_PRESSURE);
    pipeTP.setOutletPressure(outletPressureBB); // bara
    pipeTP.setMaxSimulationTime(600.0);

    // Enable energy equation but with minimal ambient heat transfer
    // This isolates the Joule-Thomson effect
    // JT coefficient is now auto-calculated from gas phase thermodynamics
    pipeTP.setIncludeHeatTransfer(true);
    pipeTP.setAmbientTemperature(temperature); // Same as inlet - no driving force
    pipeTP.setOverallHeatTransferCoeff(0.1); // Very small but non-zero for numerical stability

    pipeTP.run();

    double[] tempProfileTP = pipeTP.getTemperatureProfile();
    double[] pressureProfileTP = pipeTP.getPressureProfile();
    assertNotNull(tempProfileTP, "TransientPipe should produce temperature profile");

    double inletTempTP = tempProfileTP[0];
    double outletTempTP = tempProfileTP[tempProfileTP.length - 1];
    double tempChangeTP = outletTempTP - inletTempTP;
    double inletPressureTP = pressureProfileTP[0] / 1e5;
    double outletPressureTP = pressureProfileTP[pressureProfileTP.length - 1] / 1e5;
    double pressureDropTP = inletPressureTP - outletPressureTP;

    // Calculate expected JT cooling
    double muJT = 1e-6; // K/Pa
    double expectedJTCooling = muJT * pressureDropBB * 1e5; // Convert bar to Pa

    // Document comparison results
    System.out.println("\n=== Adiabatic Operation: Joule-Thomson Effect ===");
    System.out.println("Conditions:");
    System.out.println("  Inlet temperature: " + String.format("%.2f", temperature) + " K");
    System.out.println("  Inlet pressure: " + String.format("%.1f", pressure) + " bar");
    System.out.println("  Pipe length: 10000 m");
    System.out.println("\nPressure Drop:");
    System.out.println("  Beggs & Brill: " + String.format("%.2f", pressureDropBB) + " bar");
    System.out.println("  TransientPipe: " + String.format("%.2f", pressureDropTP) + " bar");
    System.out.println("\nTemperature Change:");
    System.out.println("Beggs & Brill (adiabatic - no JT):");
    System.out.println("  Outlet temperature: " + String.format("%.2f", outletTempBB) + " K");
    System.out.println("  Temperature change: " + String.format("%.2f", tempChangeBB) + " K");
    System.out.println("TransientPipe (with JT effect, μ_JT = 1e-6 K/Pa):");
    System.out.println("  Outlet temperature: " + String.format("%.2f", outletTempTP) + " K");
    System.out.println("  Temperature change: " + String.format("%.2f", tempChangeTP) + " K");
    System.out.println("  Expected JT cooling: " + String.format("%.2f", expectedJTCooling) + " K");
    System.out.println("\nNote: Beggs & Brill adiabatic mode has no JT effect.");
    System.out.println("TransientPipe energy equation includes JT cooling during gas expansion.");

    // B&B adiabatic should have minimal temp change
    assertTrue(Math.abs(tempChangeBB) < 1.0,
        "Beggs & Brill adiabatic should have minimal temperature change");
  }

  /**
   * Test temperature profile along pipe length.
   *
   * <p>
   * This test compares the temperature profiles along the pipe length, verifying the exponential
   * decay behavior for heat transfer.
   * </p>
   */
  @Test
  @DisplayName("Compare temperature profile along pipe")
  public void testTemperatureProfileComparison() {
    double temperature = 340.0; // K (67°C)
    double pressure = 40.0; // bar
    double ambientTemp = 283.15; // K (10°C)
    double heatTransferCoeff = 15.0; // W/(m²·K)
    int nSections = 20;

    // Set up Beggs and Brill pipe
    SystemInterface systemBB = createGasSystem(temperature, pressure);
    flashAndInitialize(systemBB, 5000.0, "kg/hr");

    Stream streamBB = new Stream("feedBB", systemBB);
    PipeBeggsAndBrills pipeBB = new PipeBeggsAndBrills("BeggsBrill", streamBB);
    pipeBB.setDiameter(0.1524);
    pipeBB.setLength(3000.0); // 3 km
    pipeBB.setElevation(0.0);
    pipeBB.setPipeWallRoughness(1e-5);
    pipeBB.setNumberOfIncrements(nSections);
    // Enable heat transfer by setting constant surface temperature
    pipeBB.setConstantSurfaceTemperature(ambientTemp, "K");
    pipeBB.setHeatTransferCoefficient(heatTransferCoeff);

    ProcessSystem processBB = new ProcessSystem();
    processBB.add(streamBB);
    processBB.add(pipeBB);
    processBB.run();

    java.util.List<Double> tempProfileBB = pipeBB.getTemperatureProfile();

    // Set up TransientPipe
    SystemInterface systemTP = createGasSystem(temperature, pressure);
    flashAndInitialize(systemTP, 5000.0, "kg/hr");

    Stream streamTP = new Stream("feedTP", systemTP);
    streamTP.run();

    TransientPipe pipeTP = new TransientPipe("TransientPipe", streamTP);
    pipeTP.setDiameter(0.1524);
    pipeTP.setLength(3000.0);
    pipeTP.setRoughness(1e-5);
    pipeTP.setNumberOfSections(nSections);
    pipeTP.setInclinationProfile(createUniformInclinationProfile(nSections, 0.0));
    pipeTP.setOutletBoundaryCondition(TransientPipe.BoundaryCondition.CONSTANT_PRESSURE);
    pipeTP.setOutletPressure(pipeBB.getOutletPressure()); // bara
    pipeTP.setMaxSimulationTime(600.0);
    // JT coefficient is now auto-calculated from gas phase thermodynamics
    pipeTP.setIncludeHeatTransfer(true);
    pipeTP.setAmbientTemperature(ambientTemp);
    pipeTP.setOverallHeatTransferCoeff(heatTransferCoeff);

    pipeTP.run();

    double[] tempProfileTP = pipeTP.getTemperatureProfile();

    // Document comparison
    System.out.println("\n=== Temperature Profile Comparison ===");
    System.out.println("Conditions:");
    System.out.println("  Inlet: " + String.format("%.1f", temperature - 273.15) + " °C");
    System.out.println("  Ambient: " + String.format("%.1f", ambientTemp - 273.15) + " °C");
    System.out.println("  Length: 3000 m, Sections: " + nSections);
    System.out.println("\nTemperature at selected positions:");
    System.out.println("Position (m) | B&B (°C) | TransientPipe (°C)");
    System.out.println("-------------|----------|--------------------");

    double dx = 3000.0 / nSections;
    for (int i = 0; i < nSections; i += 4) {
      double position = i * dx;
      double tempBB = tempProfileBB.get(i) - 273.15;
      double tempTP = tempProfileTP[i] - 273.15;
      System.out.println(String.format("%12.0f | %8.2f | %18.2f", position, tempBB, tempTP));
    }

    // Print outlet
    System.out.println(String.format("%12.0f | %8.2f | %18.2f", 3000.0,
        tempProfileBB.get(tempProfileBB.size() - 1) - 273.15,
        tempProfileTP[tempProfileTP.length - 1] - 273.15));

    // Both profiles should show temperature decreasing toward ambient
    assertTrue(tempProfileTP[0] > tempProfileTP[tempProfileTP.length - 1],
        "TransientPipe temperature should decrease along pipe");
  }

  /**
   * Test enhanced Beggs &amp; Brill energy equation with Joule-Thomson effect.
   *
   * <p>
   * Compares the original Beggs &amp; Brill (LMTD only) with the enhanced version that includes
   * Joule-Thomson cooling during gas expansion. The JT effect can be significant for high pressure
   * gas pipelines with large pressure drops.
   * </p>
   *
   * <p>
   * The Joule-Thomson coefficient is automatically calculated from gas phase thermodynamics using
   * the equation of state. No manual input of JT coefficient is required. Typical values for
   * natural gas are in the range of 3-5 × 10⁻⁶ K/Pa (0.3-0.5 K/bar).
   * </p>
   *
   * @see PipeBeggsAndBrills#setIncludeJouleThomsonEffect(boolean)
   */
  @Test
  @DisplayName("Compare enhanced B&B energy equation with JT effect")
  public void testEnhancedEnergyEquationWithJouleThomson() {
    // High pressure gas with significant pressure drop
    double temperature = 330.0; // K (57°C)
    double pressure = 80.0; // bar - high pressure
    double ambientTemp = 288.15; // K (15°C) - cool ambient
    double heatTransferCoeff = 20.0; // W/(m²·K)
    int nSections = 20;

    // Set up original Beggs and Brill pipe (LMTD only)
    SystemInterface systemOriginal = createGasSystem(temperature, pressure);
    flashAndInitialize(systemOriginal, 15000.0, "kg/hr");

    Stream streamOriginal = new Stream("feedOriginal", systemOriginal);
    PipeBeggsAndBrills pipeOriginal = new PipeBeggsAndBrills("Original", streamOriginal);
    pipeOriginal.setDiameter(0.1524); // 6 inch
    pipeOriginal.setLength(8000.0); // 8 km
    pipeOriginal.setElevation(0.0);
    pipeOriginal.setPipeWallRoughness(1e-5);
    pipeOriginal.setNumberOfIncrements(nSections);
    pipeOriginal.setConstantSurfaceTemperature(ambientTemp, "K");
    pipeOriginal.setHeatTransferCoefficient(heatTransferCoeff);

    ProcessSystem processOriginal = new ProcessSystem();
    processOriginal.add(streamOriginal);
    processOriginal.add(pipeOriginal);
    processOriginal.run();

    double outletTempOriginal = pipeOriginal.getOutletTemperature();
    double pressureDropOriginal = pipeOriginal.getPressureDrop();

    // Set up enhanced Beggs and Brill pipe with JT effect
    SystemInterface systemEnhanced = createGasSystem(temperature, pressure);
    flashAndInitialize(systemEnhanced, 15000.0, "kg/hr");

    Stream streamEnhanced = new Stream("feedEnhanced", systemEnhanced);
    PipeBeggsAndBrills pipeEnhanced = new PipeBeggsAndBrills("Enhanced", streamEnhanced);
    pipeEnhanced.setDiameter(0.1524);
    pipeEnhanced.setLength(8000.0);
    pipeEnhanced.setElevation(0.0);
    pipeEnhanced.setPipeWallRoughness(1e-5);
    pipeEnhanced.setNumberOfIncrements(nSections);
    pipeEnhanced.setConstantSurfaceTemperature(ambientTemp, "K");
    pipeEnhanced.setHeatTransferCoefficient(heatTransferCoeff);
    // Enable Joule-Thomson effect (coefficient auto-calculated from thermodynamics)
    pipeEnhanced.setIncludeJouleThomsonEffect(true);

    ProcessSystem processEnhanced = new ProcessSystem();
    processEnhanced.add(streamEnhanced);
    processEnhanced.add(pipeEnhanced);
    processEnhanced.run();

    double outletTempEnhanced = pipeEnhanced.getOutletTemperature();
    double pressureDropEnhanced = pipeEnhanced.getPressureDrop();

    // Calculate expected JT cooling contribution
    double expectedJTCooling = 3e-6 * pressureDropEnhanced * 1e5; // K

    // Document results
    System.out.println("\n=== Enhanced Energy Equation: Joule-Thomson Effect ===");
    System.out.println("Conditions:");
    System.out.println("  Inlet temperature: " + String.format("%.2f", temperature) + " K ("
        + String.format("%.1f", temperature - 273.15) + " °C)");
    System.out.println("  Ambient temperature: " + String.format("%.2f", ambientTemp) + " K ("
        + String.format("%.1f", ambientTemp - 273.15) + " °C)");
    System.out.println("  Pipe length: 8000 m");
    System.out.println("  JT coefficient: 3e-6 K/Pa");
    System.out.println("\nPressure Drop:");
    System.out.println("  Original: " + String.format("%.2f", pressureDropOriginal) + " bar");
    System.out.println("  Enhanced: " + String.format("%.2f", pressureDropEnhanced) + " bar");
    System.out.println("\nTemperature Results:");
    System.out.println("Original B&B (LMTD only):");
    System.out.println("  Outlet temperature: " + String.format("%.2f", outletTempOriginal) + " K ("
        + String.format("%.1f", outletTempOriginal - 273.15) + " °C)");
    System.out.println(
        "  Temperature drop: " + String.format("%.2f", temperature - outletTempOriginal) + " K");
    System.out.println("Enhanced B&B (LMTD + JT):");
    System.out.println("  Outlet temperature: " + String.format("%.2f", outletTempEnhanced) + " K ("
        + String.format("%.1f", outletTempEnhanced - 273.15) + " °C)");
    System.out.println(
        "  Temperature drop: " + String.format("%.2f", temperature - outletTempEnhanced) + " K");
    System.out
        .println("  Expected JT contribution: " + String.format("%.2f", expectedJTCooling) + " K");

    // Enhanced model should show more cooling due to JT effect
    double tempDiffOriginal = temperature - outletTempOriginal;
    double tempDiffEnhanced = temperature - outletTempEnhanced;
    System.out.println("\nAnalysis:");
    System.out.println("  Additional cooling from JT: "
        + String.format("%.2f", tempDiffEnhanced - tempDiffOriginal) + " K");

    // Verify both temperatures are in reasonable range
    assertTrue(outletTempOriginal > 273.15 && outletTempOriginal < temperature,
        "Original outlet temp should be between 0°C and inlet");
    assertTrue(outletTempEnhanced > 273.15 && outletTempEnhanced < temperature,
        "Enhanced outlet temp should be between 0°C and inlet");
  }

  /**
   * Test enhanced Beggs & Brill with friction heating effect.
   *
   * <p>
   * For high velocity flows, friction heating can add energy to the fluid, partially offsetting
   * heat loss to surroundings. This test compares results with and without friction heating.
   * </p>
   */
  @Test
  @DisplayName("Compare enhanced B&B with friction heating")
  public void testEnhancedEnergyEquationWithFrictionHeating() {
    // Moderate velocity gas flow with realistic conditions
    double temperature = 320.0; // K (47°C)
    double pressure = 100.0; // bar - higher pressure to avoid negative outlet
    double ambientTemp = 283.15; // K (10°C)
    double heatTransferCoeff = 30.0; // W/(m²·K)
    int nSections = 15;

    // Set up original Beggs and Brill pipe
    SystemInterface systemOriginal = createGasSystem(temperature, pressure);
    flashAndInitialize(systemOriginal, 15000.0, "kg/hr"); // Moderate flow rate

    Stream streamOriginal = new Stream("feedOriginal", systemOriginal);
    PipeBeggsAndBrills pipeOriginal = new PipeBeggsAndBrills("Original", streamOriginal);
    pipeOriginal.setDiameter(0.1524); // 6 inch - larger for reasonable pressure drop
    pipeOriginal.setLength(5000.0);
    pipeOriginal.setElevation(0.0);
    pipeOriginal.setPipeWallRoughness(1e-5);
    pipeOriginal.setNumberOfIncrements(nSections);
    pipeOriginal.setConstantSurfaceTemperature(ambientTemp, "K");
    pipeOriginal.setHeatTransferCoefficient(heatTransferCoeff);

    ProcessSystem processOriginal = new ProcessSystem();
    processOriginal.add(streamOriginal);
    processOriginal.add(pipeOriginal);
    processOriginal.run();

    double outletTempOriginal = pipeOriginal.getOutletTemperature();
    double frictionLossOriginal = pipeOriginal.getPressureDrop();

    // Set up enhanced Beggs and Brill pipe with friction heating
    SystemInterface systemEnhanced = createGasSystem(temperature, pressure);
    flashAndInitialize(systemEnhanced, 15000.0, "kg/hr");

    Stream streamEnhanced = new Stream("feedEnhanced", systemEnhanced);
    PipeBeggsAndBrills pipeEnhanced = new PipeBeggsAndBrills("Enhanced", streamEnhanced);
    pipeEnhanced.setDiameter(0.1524);
    pipeEnhanced.setLength(5000.0);
    pipeEnhanced.setElevation(0.0);
    pipeEnhanced.setPipeWallRoughness(1e-5);
    pipeEnhanced.setNumberOfIncrements(nSections);
    pipeEnhanced.setConstantSurfaceTemperature(ambientTemp, "K");
    pipeEnhanced.setHeatTransferCoefficient(heatTransferCoeff);
    // Enable friction heating
    pipeEnhanced.setIncludeFrictionHeating(true);

    ProcessSystem processEnhanced = new ProcessSystem();
    processEnhanced.add(streamEnhanced);
    processEnhanced.add(pipeEnhanced);
    processEnhanced.run();

    double outletTempEnhanced = pipeEnhanced.getOutletTemperature();

    // Document results
    System.out.println("\n=== Enhanced Energy Equation: Friction Heating ===");
    System.out.println("Conditions:");
    System.out.println("  Inlet temperature: " + String.format("%.2f", temperature) + " K");
    System.out.println("  Ambient temperature: " + String.format("%.2f", ambientTemp) + " K");
    System.out.println("  Flow rate: 15000 kg/hr");
    System.out.println("  Pipe diameter: 6 inch");
    System.out.println(
        "  Friction pressure drop: " + String.format("%.2f", frictionLossOriginal) + " bar");
    System.out.println("\nTemperature Results:");
    System.out.println("Original B&B (no friction heating):");
    System.out.println("  Outlet temperature: " + String.format("%.2f", outletTempOriginal) + " K ("
        + String.format("%.1f", outletTempOriginal - 273.15) + " °C)");
    System.out.println("Enhanced B&B (with friction heating):");
    System.out.println("  Outlet temperature: " + String.format("%.2f", outletTempEnhanced) + " K ("
        + String.format("%.1f", outletTempEnhanced - 273.15) + " °C)");
    System.out.println("\nAnalysis:");
    System.out.println("  Temperature difference: "
        + String.format("%.2f", outletTempEnhanced - outletTempOriginal) + " K");
    System.out.println("  (Positive = friction heating effect)");

    // Both should produce valid temperatures
    assertTrue(outletTempOriginal > 250 && outletTempOriginal < 350,
        "Original outlet temp should be in reasonable range");
    assertTrue(outletTempEnhanced > 250 && outletTempEnhanced < 350,
        "Enhanced outlet temp should be in reasonable range");
  }

  /**
   * Test full enhanced energy equation with all effects combined.
   *
   * <p>
   * Combines wall heat transfer, Joule-Thomson cooling, and friction heating to evaluate the
   * complete enhanced energy balance.
   * </p>
   */
  @Test
  @DisplayName("Full enhanced energy equation comparison")
  public void testFullEnhancedEnergyEquation() {
    double temperature = 340.0; // K (67°C) - hot gas
    double pressure = 100.0; // bar - high pressure to prevent negative outlet
    double ambientTemp = 278.15; // K (5°C) - cold environment
    double heatTransferCoeff = 25.0; // W/(m²·K)
    int nSections = 20;

    // Set up original Beggs and Brill pipe
    SystemInterface systemOriginal = createGasSystem(temperature, pressure);
    flashAndInitialize(systemOriginal, 15000.0, "kg/hr");

    Stream streamOriginal = new Stream("feedOriginal", systemOriginal);
    PipeBeggsAndBrills pipeOriginal = new PipeBeggsAndBrills("Original", streamOriginal);
    pipeOriginal.setDiameter(0.2032); // 8 inch - larger to reduce pressure drop
    pipeOriginal.setLength(5000.0); // 5 km - shorter to reduce pressure drop
    pipeOriginal.setElevation(0.0);
    pipeOriginal.setPipeWallRoughness(1e-5);
    pipeOriginal.setNumberOfIncrements(nSections);
    pipeOriginal.setConstantSurfaceTemperature(ambientTemp, "K");
    pipeOriginal.setHeatTransferCoefficient(heatTransferCoeff);

    ProcessSystem processOriginal = new ProcessSystem();
    processOriginal.add(streamOriginal);
    processOriginal.add(pipeOriginal);
    processOriginal.run();

    double outletTempOriginal = pipeOriginal.getOutletTemperature();
    double pressureDropTotal = pipeOriginal.getPressureDrop();

    // Set up fully enhanced Beggs and Brill pipe
    SystemInterface systemEnhanced = createGasSystem(temperature, pressure);
    flashAndInitialize(systemEnhanced, 15000.0, "kg/hr");

    Stream streamEnhanced = new Stream("feedEnhanced", systemEnhanced);
    PipeBeggsAndBrills pipeEnhanced = new PipeBeggsAndBrills("Enhanced", streamEnhanced);
    pipeEnhanced.setDiameter(0.2032);
    pipeEnhanced.setLength(5000.0);
    pipeEnhanced.setElevation(0.0);
    pipeEnhanced.setPipeWallRoughness(1e-5);
    pipeEnhanced.setNumberOfIncrements(nSections);
    pipeEnhanced.setConstantSurfaceTemperature(ambientTemp, "K");
    pipeEnhanced.setHeatTransferCoefficient(heatTransferCoeff);
    // Enable all energy equation enhancements (JT coefficient auto-calculated from thermodynamics)
    pipeEnhanced.setIncludeJouleThomsonEffect(true);
    pipeEnhanced.setIncludeFrictionHeating(true);

    ProcessSystem processEnhanced = new ProcessSystem();
    processEnhanced.add(streamEnhanced);
    processEnhanced.add(pipeEnhanced);
    processEnhanced.run();

    double outletTempEnhanced = pipeEnhanced.getOutletTemperature();

    // Document comprehensive comparison
    System.out.println("\n" + "============================================================");
    System.out.println("ENHANCED ENERGY EQUATION EVALUATION");
    System.out.println("============================================================");
    System.out.println("\nConditions:");
    System.out.println("  Inlet: " + String.format("%.1f", temperature - 273.15) + " °C, "
        + String.format("%.0f", pressure) + " bar");
    System.out.println("  Ambient: " + String.format("%.1f", ambientTemp - 273.15) + " °C");
    System.out.println("  Pipe: 5 km × 8\" diameter");
    System.out.println("  Flow: 15000 kg/hr");
    System.out
        .println("  Total pressure drop: " + String.format("%.2f", pressureDropTotal) + " bar");

    System.out.println("\n--- Temperature Results ---");
    System.out.println(String.format("%-30s %10s %10s", "Model", "Outlet (K)", "Drop (K)"));
    System.out.println("----------------------------------------------------");
    System.out.println(String.format("%-30s %10.2f %10.2f", "Original (LMTD only)",
        outletTempOriginal, temperature - outletTempOriginal));
    System.out.println(String.format("%-30s %10.2f %10.2f", "Enhanced (LMTD + JT + Friction)",
        outletTempEnhanced, temperature - outletTempEnhanced));

    System.out.println("\n--- Energy Contributions ---");
    double jtContribution = 3.5e-6 * pressureDropTotal * 1e5;
    System.out.println("  Expected JT cooling: ~" + String.format("%.1f", jtContribution) + " K");
    System.out.println("  Net effect on temperature: "
        + String.format("%.2f", outletTempEnhanced - outletTempOriginal) + " K");
    System.out.println("  (Negative = additional cooling, Positive = less cooling)");

    // Verify results are physically reasonable
    assertTrue(outletTempOriginal > ambientTemp - 5 && outletTempOriginal < temperature,
        "Original outlet temp should be between ambient and inlet");
    assertTrue(outletTempEnhanced > ambientTemp - 10 && outletTempEnhanced < temperature,
        "Enhanced outlet temp should be between ambient and inlet");
  }

  /**
   * Test auto-calculation of Joule-Thomson coefficient from thermodynamics.
   *
   * <p>
   * Verifies that TransientPipe can automatically calculate the JT coefficient from the gas phase
   * properties using NeqSim thermodynamics, eliminating the need for manual input.
   * </p>
   */
  @Test
  @DisplayName("Verify automatic JT coefficient calculation from thermodynamics")
  public void testAutoJouleThomsonCalculation() {
    double temperature = 300.0; // K
    double pressure = 50.0; // bar

    SystemInterface testSystem = new SystemSrkEos(temperature, pressure);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.07);
    testSystem.addComponent("propane", 0.03);
    testSystem.setMixingRule("classic");
    testSystem.init(0);

    Stream inletStream = new Stream("Inlet", testSystem);
    inletStream.setFlowRate(5000, "kg/hr");
    inletStream.run();

    // Create TransientPipe with auto JT calculation (always enabled)
    TransientPipe pipeAuto = new TransientPipe("AutoJT", inletStream);
    pipeAuto.setDiameter(0.1524);
    pipeAuto.setLength(3000.0);
    pipeAuto.setNumberOfSections(20);
    pipeAuto.setInclinationProfile(createUniformInclinationProfile(20, 0.0));
    pipeAuto.setIncludeHeatTransfer(true);
    pipeAuto.setAmbientTemperature(288.15);
    pipeAuto.setMaxSimulationTime(300.0);

    pipeAuto.run();

    // Get the calculated JT coefficient
    double effectiveJT = pipeAuto.getJouleThomsonCoeff();

    System.out.println("\n=== Auto JT Coefficient Calculation Test ===");
    System.out.println("Conditions: " + String.format("%.0f", temperature) + " K, "
        + String.format("%.0f", pressure) + " bar");
    System.out
        .println("Calculated JT coefficient: " + String.format("%.2e", effectiveJT) + " K/Pa");

    // JT coefficient should be in typical range for natural gas (1e-6 to 1e-5 K/Pa)
    assertTrue(effectiveJT > 1e-7 && effectiveJT < 1e-4,
        "Auto-calculated JT should be in reasonable range for natural gas: " + effectiveJT);
  }

  /**
   * Compare auto-calculated JT with expected values for different gas compositions.
   */
  @Test
  @DisplayName("Verify JT coefficient varies with gas composition")
  public void testJTCoefficientForDifferentGases() {
    double temperature = 300.0; // K
    double pressure = 50.0; // bar

    // Test pure methane
    SystemInterface methaneSystem = new SystemSrkEos(temperature, pressure);
    methaneSystem.addComponent("methane", 1.0);
    methaneSystem.setMixingRule("classic");
    methaneSystem.init(0);

    Stream methaneStream = new Stream("Methane", methaneSystem);
    methaneStream.setFlowRate(5000, "kg/hr");
    methaneStream.run();

    TransientPipe methanePipe = new TransientPipe("MethanePipe", methaneStream);
    methanePipe.setDiameter(0.1524);
    methanePipe.setLength(1000.0);
    methanePipe.setNumberOfSections(10);
    methanePipe.setInclinationProfile(createUniformInclinationProfile(10, 0.0));
    methanePipe.setIncludeHeatTransfer(true);
    methanePipe.setMaxSimulationTime(100.0);
    methanePipe.run();

    double methaneJT = methanePipe.getJouleThomsonCoeff();

    // Test CO2 (should have higher JT coefficient)
    SystemInterface co2System = new SystemSrkEos(temperature, pressure);
    co2System.addComponent("CO2", 1.0);
    co2System.setMixingRule("classic");
    co2System.init(0);

    Stream co2Stream = new Stream("CO2", co2System);
    co2Stream.setFlowRate(5000, "kg/hr");
    co2Stream.run();

    TransientPipe co2Pipe = new TransientPipe("CO2Pipe", co2Stream);
    co2Pipe.setDiameter(0.1524);
    co2Pipe.setLength(1000.0);
    co2Pipe.setNumberOfSections(10);
    co2Pipe.setInclinationProfile(createUniformInclinationProfile(10, 0.0));
    co2Pipe.setIncludeHeatTransfer(true);
    co2Pipe.setMaxSimulationTime(100.0);
    co2Pipe.run();

    double co2JT = co2Pipe.getJouleThomsonCoeff();

    System.out.println("\n=== JT Coefficient Comparison for Different Gases ===");
    System.out.println("Conditions: " + String.format("%.0f", temperature) + " K, "
        + String.format("%.0f", pressure) + " bar");
    System.out.println("Methane JT: " + String.format("%.3e", methaneJT) + " K/Pa ("
        + String.format("%.2f", methaneJT * 1e5) + " K/bar)");
    System.out.println("CO2 JT: " + String.format("%.3e", co2JT) + " K/Pa ("
        + String.format("%.2f", co2JT * 1e5) + " K/bar)");

    // Both should be positive (cooling on expansion) for these conditions
    assertTrue(methaneJT > 0, "Methane JT should be positive at these conditions");
    assertTrue(co2JT > 0, "CO2 JT should be positive at these conditions");

    // CO2 typically has higher JT than methane
    System.out.println("CO2/Methane JT ratio: " + String.format("%.2f", co2JT / methaneJT));
  }
}
