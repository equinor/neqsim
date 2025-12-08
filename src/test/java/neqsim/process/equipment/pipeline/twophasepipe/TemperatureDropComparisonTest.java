package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test for temperature drop in multiphase flow pipelines.
 *
 * <p>
 * Compares thermal behavior between:
 * <ul>
 * <li><b>TwoFluidPipe</b>: Modern two-fluid model with separate phase velocities</li>
 * <li><b>PipeBeggsAndBrills</b>: Industry-standard correlation-based model</li>
 * </ul>
 *
 * <p>
 * Notes:
 * <ul>
 * <li>TwoFluidPipe is adiabatic by default (no heat loss to surroundings)</li>
 * <li>PipeBeggsAndBrills includes heat transfer modeling</li>
 * <li>Temperature differences may arise from pressure drop and Joule-Thomson expansion effects</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TemperatureDropComparisonTest {

  private SystemInterface testFluid;
  private Stream inletStream;

  /**
   * Set up a two-phase gas-condensate fluid for testing.
   */
  @BeforeEach
  void setUp() {
    // Create a two-phase gas-condensate fluid
    // Typical subsea export pipeline conditions
    testFluid = new SystemSrkEos(303.15, 50.0); // 30°C, 50 bar initial
    testFluid.addComponent("methane", 0.85);
    testFluid.addComponent("ethane", 0.08);
    testFluid.addComponent("propane", 0.04);
    testFluid.addComponent("n-heptane", 0.03);
    testFluid.setMixingRule("classic");

    // Create inlet stream
    inletStream = new Stream("inlet", testFluid);
    inletStream.setFlowRate(10.0, "kg/sec");
    inletStream.setTemperature(30.0, "C"); // 303.15 K
    inletStream.setPressure(50.0, "bara");
    inletStream.run();
  }

  /**
   * Test TwoFluidPipe temperature profile is initialized correctly.
   *
   * <p>
   * TwoFluidPipe is adiabatic (no heat loss), so inlet ≈ outlet for isothermal pipe.
   * </p>
   */
  @Test
  void testTwoFluidPipeTemperatureProfile() {
    TwoFluidPipe pipe = new TwoFluidPipe("test-pipe", inletStream);
    pipe.setLength(5000.0); // 5 km
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(50);

    // Create flat elevation profile (horizontal)
    double[] elevations = new double[50];
    for (int i = 0; i < 50; i++) {
      elevations[i] = 0.0;
    }
    pipe.setElevationProfile(elevations);
    pipe.run();

    double[] tempProfile = pipe.getTemperatureProfile();

    // Assert profile exists and has correct length
    assertNotNull(tempProfile, "Temperature profile should not be null");
    assertEquals(50, tempProfile.length, "Profile should have 50 sections");

    // All temperatures should be positive (Kelvin)
    for (int i = 0; i < tempProfile.length; i++) {
      assertTrue(tempProfile[i] > 0, "Temperature at section " + i + " must be positive (K)");
    }

    // For adiabatic pipe, temperature should remain roughly constant
    double inletTemp = tempProfile[0];
    double outletTemp = tempProfile[tempProfile.length - 1];
    assertEquals(inletTemp, outletTemp, 1.0,
        "Adiabatic pipe should have minimal temperature change. Inlet="
            + inletTemp + ", Outlet=" + outletTemp);
  }

  /**
   * Test TwoFluidPipe temperature profile is monotonic.
   */
  @Test
  void testTwoFluidPipeTemperatureMonotonicity() {
    TwoFluidPipe pipe = new TwoFluidPipe("monotonic-test", inletStream);
    pipe.setLength(3000.0);
    pipe.setDiameter(0.25);
    pipe.setNumberOfSections(100);

    double[] elev = new double[100];
    for (int i = 0; i < 100; i++) {
      elev[i] = 0.0;
    }
    pipe.setElevationProfile(elev);
    pipe.run();

    double[] tempProfile = pipe.getTemperatureProfile();

    // Temperature profile should show no oscillations
    // (smooth transitions between sections)
    double maxTemp = tempProfile[0];
    double minTemp = tempProfile[0];

    for (int i = 1; i < tempProfile.length; i++) {
      double temp = tempProfile[i];
      assertTrue(temp <= maxTemp + 1.0, // Allow small numerical tolerance
          "Temperature should not spike at section " + i);
      maxTemp = Math.max(maxTemp, temp);
      minTemp = Math.min(minTemp, temp);
    }

    // Verify reasonable temperature range
    double tempRange = maxTemp - minTemp;
    assertTrue(tempRange < 10.0, "Temperature range should be less than 10°C");
  }

  /**
   * Test temperature comparison: TwoFluidPipe vs PipeBeggsAndBrills.
   *
   * <p>
   * PipeBeggsAndBrills includes heat transfer, so should show temperature change if configured.
   * </p>
   */
  @Test
  void testTemperatureComparisonWithBeggsBrills() {
    // ========== TwoFluidPipe ==========
    TwoFluidPipe twoFluidPipe = new TwoFluidPipe("line-1", inletStream);
    twoFluidPipe.setLength(3000.0); // 3 km
    twoFluidPipe.setDiameter(0.3);
    twoFluidPipe.setNumberOfSections(30);

    double[] elevations = new double[30];
    for (int i = 0; i < 30; i++) {
      elevations[i] = 0.0; // Horizontal
    }
    twoFluidPipe.setElevationProfile(elevations);
    twoFluidPipe.run();

    double[] twoFluidTemp = twoFluidPipe.getTemperatureProfile();
    double twoFluidInlet = twoFluidTemp[0];
    double twoFluidOutlet = twoFluidTemp[twoFluidTemp.length - 1];

    // ========== PipeBeggsAndBrills ==========
    PipeBeggsAndBrills bbPipe = new PipeBeggsAndBrills("line-1-bb", inletStream);
    bbPipe.setLength(3000.0);
    bbPipe.setDiameter(0.3);
    bbPipe.setNumberOfIncrements(30);
    bbPipe.setAngle(0.0); // Horizontal
    bbPipe.setConstantSurfaceTemperature(5.0, "C"); // Seabed temperature
    bbPipe.setHeatTransferCoefficient(25.0); // W/(m²·K)
    bbPipe.run();

    double bbInletTemp = bbPipe.getInletStream().getThermoSystem().getTemperature("K");
    double bbOutletTemp = bbPipe.getOutletStream().getThermoSystem().getTemperature("K");

    // Both pipes should have positive temperatures
    assertTrue(twoFluidInlet > 0, "TwoFluidPipe inlet should be positive");
    assertTrue(bbInletTemp > 0, "Beggs & Brill inlet should be positive");

    // Beggs & Brill with surface temperature should show some cooling
    // TwoFluidPipe is adiabatic so temperature change comes from pressure/expansion effects only
    assertTrue(bbOutletTemp >= 0, "Beggs & Brill outlet should be physical");
    assertTrue(twoFluidOutlet >= 0, "TwoFluidPipe outlet should be physical");

    System.out.println("TwoFluidPipe inlet: " + twoFluidInlet + " K");
    System.out.println("TwoFluidPipe outlet: " + twoFluidOutlet + " K");
    System.out.println("Beggs & Brill inlet: " + bbInletTemp + " K");
    System.out.println("Beggs & Brill outlet: " + bbOutletTemp + " K");
  }

  /**
   * Test temperature profile initialization for uphill pipeline.
   */
  @Test
  void testUphillPipelineTemperature() {
    TwoFluidPipe pipe = new TwoFluidPipe("uphill-pipe", inletStream);
    pipe.setLength(2000.0); // 2 km
    pipe.setDiameter(0.25);
    pipe.setNumberOfSections(40);

    // Create uphill profile (10° inclination)
    double[] elevations = new double[40];
    double angleRad = Math.toRadians(10.0);
    for (int i = 0; i < 40; i++) {
      elevations[i] = (i * 2000.0 / 40.0) * Math.tan(angleRad);
    }
    pipe.setElevationProfile(elevations);
    pipe.run();

    double[] tempProfile = pipe.getTemperatureProfile();

    // Assert profile exists
    assertNotNull(tempProfile, "Temperature profile should not be null");
    assertEquals(40, tempProfile.length, "Profile should have 40 sections");

    // All temperatures should be positive
    for (double temp : tempProfile) {
      assertTrue(temp > 0, "Temperature must be positive (K)");
    }

    // Temperature should be roughly constant (adiabatic)
    double inletTemp = tempProfile[0];
    double outletTemp = tempProfile[tempProfile.length - 1];
    assertEquals(inletTemp, outletTemp, 1.5, "Uphill adiabatic pipe should have minimal change");
  }

  /**
   * Test temperature profile reproducibility.
   */
  @Test
  void testTemperatureReproducibility() {
    // First run
    TwoFluidPipe pipe1 = new TwoFluidPipe("repro-test-1", inletStream);
    pipe1.setLength(1500.0);
    pipe1.setDiameter(0.25);
    pipe1.setNumberOfSections(30);
    double[] elev1 = new double[30];
    for (int i = 0; i < 30; i++) {
      elev1[i] = 0.0;
    }
    pipe1.setElevationProfile(elev1);
    pipe1.run();

    double[] tempProfile1 = pipe1.getTemperatureProfile();

    // Second run (identical conditions)
    Stream inlet2 = new Stream("inlet-2", testFluid.clone());
    inlet2.setFlowRate(10.0, "kg/sec");
    inlet2.setTemperature(30.0, "C");
    inlet2.setPressure(50.0, "bara");
    inlet2.run();

    TwoFluidPipe pipe2 = new TwoFluidPipe("repro-test-2", inlet2);
    pipe2.setLength(1500.0);
    pipe2.setDiameter(0.25);
    pipe2.setNumberOfSections(30);
    double[] elev2 = new double[30];
    for (int i = 0; i < 30; i++) {
      elev2[i] = 0.0;
    }
    pipe2.setElevationProfile(elev2);
    pipe2.run();

    double[] tempProfile2 = pipe2.getTemperatureProfile();

    // Profiles should match point-by-point (reproducible)
    assertEquals(tempProfile1.length, tempProfile2.length, "Profiles should have same length");
    for (int i = 0; i < tempProfile1.length; i++) {
      assertEquals(tempProfile1[i], tempProfile2[i], 1e-9,
          "Temperature at section " + i + " should be reproducible");
    }
  }

  /**
   * Test temperature profile with varying flow rates.
   */
  @Test
  void testTemperatureWithVaryingFlowRate() {
    // Low flow case
    Stream lowFlowStream = new Stream("low-flow", testFluid.clone());
    lowFlowStream.setFlowRate(5.0, "kg/sec");
    lowFlowStream.setTemperature(30.0, "C");
    lowFlowStream.setPressure(50.0, "bara");
    lowFlowStream.run();

    TwoFluidPipe lowFlowPipe = new TwoFluidPipe("low-flow-pipe", lowFlowStream);
    lowFlowPipe.setLength(2000.0);
    lowFlowPipe.setDiameter(0.3);
    lowFlowPipe.setNumberOfSections(40);
    double[] elev1 = new double[40];
    for (int i = 0; i < 40; i++) {
      elev1[i] = 0.0;
    }
    lowFlowPipe.setElevationProfile(elev1);
    lowFlowPipe.run();

    double[] lowFlowTemp = lowFlowPipe.getTemperatureProfile();

    // High flow case
    Stream highFlowStream = new Stream("high-flow", testFluid.clone());
    highFlowStream.setFlowRate(20.0, "kg/sec");
    highFlowStream.setTemperature(30.0, "C");
    highFlowStream.setPressure(50.0, "bara");
    highFlowStream.run();

    TwoFluidPipe highFlowPipe = new TwoFluidPipe("high-flow-pipe", highFlowStream);
    highFlowPipe.setLength(2000.0);
    highFlowPipe.setDiameter(0.3);
    highFlowPipe.setNumberOfSections(40);
    double[] elev2 = new double[40];
    for (int i = 0; i < 40; i++) {
      elev2[i] = 0.0;
    }
    highFlowPipe.setElevationProfile(elev2);
    highFlowPipe.run();

    double[] highFlowTemp = highFlowPipe.getTemperatureProfile();

    // Both should have positive temperatures
    assertTrue(lowFlowTemp[0] > 0, "Low flow inlet should be positive");
    assertTrue(highFlowTemp[0] > 0, "High flow inlet should be positive");

    // For adiabatic pipe, flow rate shouldn't dramatically affect temperature
    // (adiabatic means no heat loss, so T should remain relatively constant)
    double lowFlowDrop = lowFlowTemp[0] - lowFlowTemp[lowFlowTemp.length - 1];
    double highFlowDrop = highFlowTemp[0] - highFlowTemp[highFlowTemp.length - 1];

    System.out.println("Low flow temperature drop: " + lowFlowDrop + " K");
    System.out.println("High flow temperature drop: " + highFlowDrop + " K");

    // Both should be small (adiabatic)
    assertTrue(Math.abs(lowFlowDrop) < 1.0, "Low flow drop should be minimal (adiabatic)");
    assertTrue(Math.abs(highFlowDrop) < 1.0, "High flow drop should be minimal (adiabatic)");
  }

  /**
   * Test physical bounds on temperature.
   */
  @Test
  void testTemperaturePhysicalBounds() {
    TwoFluidPipe pipe = new TwoFluidPipe("bounds-test", inletStream);
    pipe.setLength(3000.0);
    pipe.setDiameter(0.25);
    pipe.setNumberOfSections(50);
    double[] elev = new double[50];
    for (int i = 0; i < 50; i++) {
      elev[i] = 0.0;
    }
    pipe.setElevationProfile(elev);
    pipe.run();

    double[] tempProfile = pipe.getTemperatureProfile();
    double inletTemp = tempProfile[0];

    // All temperatures must be positive (Kelvin scale)
    for (int i = 0; i < tempProfile.length; i++) {
      assertTrue(tempProfile[i] > 0,
          "Temperature at section " + i + " must be positive (K): " + tempProfile[i]);
    }

    // Outlet should be reasonable (not lower than absolute zero, not higher than inlet + margin)
    double outletTemp = tempProfile[tempProfile.length - 1];
    assertTrue(outletTemp <= inletTemp + 5.0, // Allow small margin for numerical effects
        "Outlet temp should not be much higher than inlet (adiabatic conditions)");
  }
}
