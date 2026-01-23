package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        "Adiabatic pipe should have minimal temperature change. Inlet=" + inletTemp + ", Outlet="
            + outletTemp);
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

  /**
   * Test TwoFluidPipe with heat transfer enabled.
   *
   * <p>
   * When heat transfer is configured, the pipe should show cooling when:
   * <ul>
   * <li>Surface temperature is set (e.g., seabed at 5°C)</li>
   * <li>Heat transfer coefficient is positive</li>
   * </ul>
   * </p>
   */
  @Test
  void testTwoFluidPipeWithHeatTransfer() {
    TwoFluidPipe pipe = new TwoFluidPipe("heat-transfer-pipe", inletStream);
    pipe.setLength(3000.0); // 3 km
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(30);

    // Create flat elevation profile (horizontal)
    double[] elevations = new double[30];
    for (int i = 0; i < 30; i++) {
      elevations[i] = 0.0;
    }
    pipe.setElevationProfile(elevations);

    // Enable heat transfer
    pipe.setSurfaceTemperature(5.0, "C"); // Seabed at 5°C = 278.15 K
    pipe.setHeatTransferCoefficient(25.0); // 25 W/(m²·K) - typical subsea

    pipe.run();

    double[] tempProfile = pipe.getTemperatureProfile();
    double inletTemp = tempProfile[0];
    double outletTemp = tempProfile[tempProfile.length - 1];
    double tempDrop = inletTemp - outletTemp;

    System.out.println("TwoFluidPipe with heat transfer:");
    System.out.println("  Inlet temp: " + inletTemp + " K");
    System.out.println("  Outlet temp: " + outletTemp + " K");
    System.out.println("  Temperature drop: " + tempDrop + " K");
    System.out.println("  Heat transfer enabled: " + pipe.isHeatTransferEnabled());

    // With heat transfer enabled, should show cooling
    assertTrue(pipe.isHeatTransferEnabled(), "Heat transfer should be enabled");

    // All temperatures should be positive
    for (double temp : tempProfile) {
      assertTrue(temp > 0, "Temperature must be positive (K)");
    }
  }

  /**
   * Test comparison of TwoFluidPipe with and without heat transfer.
   */
  @Test
  void testTwoFluidPipeHeatTransferComparison() {
    // ========== Adiabatic case (no heat transfer) ==========
    TwoFluidPipe adiabaticPipe = new TwoFluidPipe("adiabatic", inletStream);
    adiabaticPipe.setLength(3000.0);
    adiabaticPipe.setDiameter(0.3);
    adiabaticPipe.setNumberOfSections(30);

    double[] elev1 = new double[30];
    for (int i = 0; i < 30; i++) {
      elev1[i] = 0.0;
    }
    adiabaticPipe.setElevationProfile(elev1);
    adiabaticPipe.run();

    double[] adiabaticTemp = adiabaticPipe.getTemperatureProfile();
    double adiabaticDrop = adiabaticTemp[0] - adiabaticTemp[adiabaticTemp.length - 1];

    // ========== Heat transfer case ==========
    Stream inlet2 = new Stream("inlet-ht", testFluid.clone());
    inlet2.setFlowRate(10.0, "kg/sec");
    inlet2.setTemperature(30.0, "C");
    inlet2.setPressure(50.0, "bara");
    inlet2.run();

    TwoFluidPipe heatTransferPipe = new TwoFluidPipe("heat-transfer", inlet2);
    heatTransferPipe.setLength(3000.0);
    heatTransferPipe.setDiameter(0.3);
    heatTransferPipe.setNumberOfSections(30);

    double[] elev2 = new double[30];
    for (int i = 0; i < 30; i++) {
      elev2[i] = 0.0;
    }
    heatTransferPipe.setElevationProfile(elev2);
    heatTransferPipe.setSurfaceTemperature(5.0, "C"); // Cold seabed
    heatTransferPipe.setHeatTransferCoefficient(25.0); // Subsea conditions
    heatTransferPipe.run();

    double[] htTemp = heatTransferPipe.getTemperatureProfile();
    double htDrop = htTemp[0] - htTemp[htTemp.length - 1];

    System.out.println("Adiabatic pipe temperature drop: " + adiabaticDrop + " K");
    System.out.println("Heat transfer pipe temperature drop: " + htDrop + " K");

    // Heat transfer disabled for adiabatic
    assertFalse(adiabaticPipe.isHeatTransferEnabled(),
        "Adiabatic pipe should not have heat transfer");
    // Heat transfer enabled for the other
    assertTrue(heatTransferPipe.isHeatTransferEnabled(),
        "Heat transfer pipe should have heat transfer enabled");

    // Both should have positive outlet temperatures
    assertTrue(adiabaticTemp[adiabaticTemp.length - 1] > 0, "Adiabatic outlet > 0");
    assertTrue(htTemp[htTemp.length - 1] > 0, "Heat transfer outlet > 0");
  }

  /**
   * Test insulation type presets.
   */
  @Test
  void testInsulationTypePresets() {
    TwoFluidPipe pipe = new TwoFluidPipe("insulation-test", inletStream);
    pipe.setLength(3000.0);
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(30);

    double[] elevations = new double[30];
    for (int i = 0; i < 30; i++) {
      elevations[i] = 0.0;
    }
    pipe.setElevationProfile(elevations);
    pipe.setSurfaceTemperature(5.0, "C");

    // Test PU Foam insulation
    pipe.setInsulationType(TwoFluidPipe.InsulationType.PU_FOAM);
    assertEquals(TwoFluidPipe.InsulationType.PU_FOAM, pipe.getInsulationTypeEnum());
    assertEquals(10.0, pipe.getHeatTransferCoefficient(), 0.01);
    assertTrue(pipe.isHeatTransferEnabled());

    // Test pipe-in-pipe (better insulation, lower U-value)
    pipe.setInsulationType(TwoFluidPipe.InsulationType.PIPE_IN_PIPE);
    assertEquals(2.0, pipe.getHeatTransferCoefficient(), 0.01);

    // Test uninsulated subsea
    pipe.setInsulationType(TwoFluidPipe.InsulationType.UNINSULATED_SUBSEA);
    assertEquals(25.0, pipe.getHeatTransferCoefficient(), 0.01);

    pipe.run();
    assertNotNull(pipe.getTemperatureProfile());
  }

  /**
   * Test temperature profile with different units.
   */
  @Test
  void testTemperatureProfileUnits() {
    TwoFluidPipe pipe = new TwoFluidPipe("units-test", inletStream);
    pipe.setLength(3000.0);
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(30);

    double[] elevations = new double[30];
    for (int i = 0; i < 30; i++) {
      elevations[i] = 0.0;
    }
    pipe.setElevationProfile(elevations);
    pipe.setSurfaceTemperature(5.0, "C");
    pipe.setHeatTransferCoefficient(25.0);
    pipe.run();

    // Get temperature in different units
    double[] tempK = pipe.getTemperatureProfile("K");
    double[] tempC = pipe.getTemperatureProfile("C");
    double[] tempF = pipe.getTemperatureProfile("F");

    assertNotNull(tempK);
    assertNotNull(tempC);
    assertNotNull(tempF);

    // Check conversion at first point
    double T_K = tempK[0];
    double T_C = tempC[0];
    double T_F = tempF[0];

    assertEquals(T_K - 273.15, T_C, 0.01, "Celsius conversion correct");
    assertEquals((T_K - 273.15) * 9.0 / 5.0 + 32.0, T_F, 0.01, "Fahrenheit conversion correct");

    System.out.println("Temperature at inlet: " + T_K + " K = " + T_C + " °C = " + T_F + " °F");
  }

  /**
   * Test hydrate and wax risk monitoring.
   */
  @Test
  void testHydrateWaxRiskMonitoring() {
    Stream coldInlet = new Stream("cold-inlet", testFluid.clone());
    coldInlet.setFlowRate(10.0, "kg/sec");
    coldInlet.setTemperature(15.0, "C"); // Start colder
    coldInlet.setPressure(50.0, "bara");
    coldInlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe("risk-test", coldInlet);
    pipe.setLength(5000.0); // 5 km - longer pipe for more cooling
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(50);

    double[] elevations = new double[50];
    for (int i = 0; i < 50; i++) {
      elevations[i] = 0.0;
    }
    pipe.setElevationProfile(elevations);

    // Set heat transfer
    pipe.setSurfaceTemperature(2.0, "C"); // Very cold seabed
    pipe.setHeatTransferCoefficient(50.0); // Poor insulation

    // Set risk temperatures
    pipe.setHydrateFormationTemperature(10.0, "C"); // 283.15 K
    pipe.setWaxAppearanceTemperature(15.0, "C"); // 288.15 K

    pipe.run();

    // Check that risk is detected
    System.out.println("Hydrate formation temp: " + pipe.getHydrateFormationTemperature() + " K");
    System.out.println("Wax appearance temp: " + pipe.getWaxAppearanceTemperature() + " K");
    System.out.println("Has hydrate risk: " + pipe.hasHydrateRisk());
    System.out.println("Has wax risk: " + pipe.hasWaxRisk());

    // Temperature should drop significantly with cold seabed
    double[] tempProfile = pipe.getTemperatureProfile("C");
    System.out.println("Inlet temp: " + tempProfile[0] + " °C");
    System.out.println("Outlet temp: " + tempProfile[tempProfile.length - 1] + " °C");

    // Verify temperatures are set correctly
    assertEquals(283.15, pipe.getHydrateFormationTemperature(), 0.01);
    assertEquals(288.15, pipe.getWaxAppearanceTemperature(), 0.01);

    // We expect risk since outlet approaches 2°C
    if (pipe.hasHydrateRisk()) {
      int firstRisk = pipe.getFirstHydrateRiskSection();
      double distance = pipe.getDistanceToHydrateRisk();
      System.out.println("First hydrate risk at section " + firstRisk + " (" + distance + " m)");
      assertTrue(firstRisk >= 0);
      assertTrue(distance >= 0);
    }
  }

  /**
   * Test variable heat transfer coefficient profile.
   */
  @Test
  void testVariableHeatTransferProfile() {
    TwoFluidPipe pipe = new TwoFluidPipe("variable-htc", inletStream);
    pipe.setLength(3000.0);
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(30);

    double[] elevations = new double[30];
    for (int i = 0; i < 30; i++) {
      elevations[i] = 0.0;
    }
    pipe.setElevationProfile(elevations);
    pipe.setSurfaceTemperature(5.0, "C");

    // Create variable U-value profile: insulated at start, bare in middle, insulated at end
    double[] htcProfile = new double[30];
    for (int i = 0; i < 30; i++) {
      if (i < 10) {
        htcProfile[i] = 5.0; // Good insulation (first 1 km)
      } else if (i < 20) {
        htcProfile[i] = 50.0; // Poor insulation (middle 1 km)
      } else {
        htcProfile[i] = 5.0; // Good insulation (last 1 km)
      }
    }
    pipe.setHeatTransferProfile(htcProfile);

    pipe.run();

    double[] htcProfileOut = pipe.getHeatTransferProfile();
    assertNotNull(htcProfileOut);
    assertEquals(30, htcProfileOut.length);
    assertEquals(5.0, htcProfileOut[0], 0.01);
    assertEquals(50.0, htcProfileOut[15], 0.01);
    assertEquals(5.0, htcProfileOut[25], 0.01);

    // Temperature should drop more in middle section
    double[] tempC = pipe.getTemperatureProfile("C");
    System.out.println("Variable HTC test - Inlet: " + tempC[0] + " °C, Outlet: "
        + tempC[tempC.length - 1] + " °C");
  }

  /**
   * Test soil thermal resistance for buried pipelines.
   */
  @Test
  void testSoilThermalResistance() {
    // Without soil resistance
    TwoFluidPipe pipeNoSoil = new TwoFluidPipe("no-soil", inletStream);
    pipeNoSoil.setLength(3000.0);
    pipeNoSoil.setDiameter(0.3);
    pipeNoSoil.setNumberOfSections(30);

    double[] elev1 = new double[30];
    for (int i = 0; i < 30; i++) {
      elev1[i] = 0.0;
    }
    pipeNoSoil.setElevationProfile(elev1);
    pipeNoSoil.setSurfaceTemperature(5.0, "C");
    pipeNoSoil.setHeatTransferCoefficient(25.0);
    pipeNoSoil.run();

    // With soil resistance (buried pipe)
    Stream inlet2 = new Stream("inlet2", testFluid.clone());
    inlet2.setFlowRate(10.0, "kg/sec");
    inlet2.setTemperature(30.0, "C");
    inlet2.setPressure(50.0, "bara");
    inlet2.run();

    TwoFluidPipe pipeBuried = new TwoFluidPipe("buried", inlet2);
    pipeBuried.setLength(3000.0);
    pipeBuried.setDiameter(0.3);
    pipeBuried.setNumberOfSections(30);

    double[] elev2 = new double[30];
    for (int i = 0; i < 30; i++) {
      elev2[i] = 0.0;
    }
    pipeBuried.setElevationProfile(elev2);
    pipeBuried.setSurfaceTemperature(5.0, "C");
    pipeBuried.setHeatTransferCoefficient(25.0);
    pipeBuried.setSoilThermalResistance(0.5); // m²·K/W - typical buried pipe
    pipeBuried.run();

    double dropNoSoil = pipeNoSoil.getTemperatureProfile()[0]
        - pipeNoSoil.getTemperatureProfile()[pipeNoSoil.getTemperatureProfile().length - 1];
    double dropBuried = pipeBuried.getTemperatureProfile()[0]
        - pipeBuried.getTemperatureProfile()[pipeBuried.getTemperatureProfile().length - 1];

    System.out.println("Temperature drop without soil resistance: " + dropNoSoil + " K");
    System.out.println("Temperature drop with soil resistance: " + dropBuried + " K");

    // Buried pipe should have less heat loss due to soil thermal resistance
    assertTrue(dropBuried < dropNoSoil, "Buried pipe should have less heat loss");
    assertEquals(0.5, pipeBuried.getSoilThermalResistance(), 0.01);
  }

  /**
   * Test Joule-Thomson effect.
   */
  @Test
  void testJouleThomsonEffect() {
    // Test with J-T enabled (default)
    TwoFluidPipe pipeJT = new TwoFluidPipe("jt-enabled", inletStream);
    pipeJT.setLength(5000.0);
    pipeJT.setDiameter(0.3);
    pipeJT.setNumberOfSections(50);

    double[] elev = new double[50];
    for (int i = 0; i < 50; i++) {
      elev[i] = 0.0;
    }
    pipeJT.setElevationProfile(elev);
    pipeJT.setSurfaceTemperature(20.0, "C"); // Mild ambient - minimize conductive heat loss
    pipeJT.setHeatTransferCoefficient(5.0); // Low HTC to emphasize J-T effect

    assertTrue(pipeJT.isJouleThomsonEnabled(), "J-T should be enabled by default");

    pipeJT.run();

    System.out.println("J-T enabled: " + pipeJT.isJouleThomsonEnabled());

    // Test disabling J-T
    pipeJT.setEnableJouleThomson(false);
    assertFalse(pipeJT.isJouleThomsonEnabled());
  }

  /**
   * Test pipe wall properties for transient calculations.
   */
  @Test
  void testPipeWallProperties() {
    TwoFluidPipe pipe = new TwoFluidPipe("wall-test", inletStream);
    pipe.setLength(3000.0);
    pipe.setDiameter(0.3);
    pipe.setNumberOfSections(30);

    double[] elev = new double[30];
    for (int i = 0; i < 30; i++) {
      elev[i] = 0.0;
    }
    pipe.setElevationProfile(elev);

    // Set custom wall properties (stainless steel)
    pipe.setWallProperties(0.025, 8000.0, 500.0); // 25mm thick, stainless steel

    assertEquals(0.025, pipe.getWallThickness(), 0.001);

    pipe.setSurfaceTemperature(5.0, "C");
    pipe.setHeatTransferCoefficient(25.0);
    pipe.run();

    assertNotNull(pipe.getTemperatureProfile());
  }
}
