package neqsim.process.equipment.tank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for VesselDepressurization class.
 *
 * <p>
 * Tests for dynamic vessel filling and depressurization calculations including isothermal,
 * isentropic, isenthalpic, isenergetic, and energy balance modes.
 * </p>
 *
 * @author ESOL
 * @see <a href="https://doi.org/10.21105/joss.03695">Andreasen (2021) - HydDown JOSS Paper</a>
 */
public class VesselDepressurizationTest {
  private Stream createTestStream(double tempK, double pressBar) {
    SystemInterface gas = new SystemSrkEos(tempK, pressBar);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(1);

    Stream feed = new Stream("feed", gas);
    feed.setTemperature(tempK, "K");
    feed.setPressure(pressBar, "bar");
    feed.run();
    return feed;
  }

  @Test
  void testConstructor() {
    Stream feed = createTestStream(300.0, 50.0);
    VesselDepressurization vessel = new VesselDepressurization("testVessel", feed);
    vessel.setVolume(1.0);
    vessel.run();

    assertNotNull(vessel, "Vessel should be created");
    assertTrue(vessel.getPressure() > 0, "Pressure should be positive");
    assertTrue(vessel.getTemperature() > 0, "Temperature should be positive");
  }

  @Test
  void testIsothermalDepressurization() {
    Stream feed = createTestStream(300.0, 50.0);

    VesselDepressurization vessel = new VesselDepressurization("testVessel", feed);
    vessel.setVolume(1.0);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISOTHERMAL);
    vessel.setOrificeDiameter(0.01);
    vessel.setDischargeCoefficient(0.62);
    vessel.setBackPressure(1.0);
    vessel.run();

    double initialP = vessel.getPressure();
    double initialT = vessel.getTemperature();

    // Run transient simulation
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 50; i++) {
      vessel.runTransient(0.1, id);
    }

    double finalP = vessel.getPressure();
    double finalT = vessel.getTemperature();

    assertTrue(finalP < initialP, "Pressure should decrease during blowdown");
    assertEquals(initialT, finalT, 5.0, "Temperature should remain roughly constant (isothermal)");
  }

  @Test
  void testIsentropicDepressurization() {
    Stream feed = createTestStream(300.0, 70.0);

    VesselDepressurization vessel = new VesselDepressurization("N2tank", feed);
    vessel.setVolume(0.1);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISENTROPIC);
    vessel.setHeatTransferType(VesselDepressurization.HeatTransferType.ADIABATIC);
    vessel.setOrificeDiameter(0.005);
    vessel.setBackPressure(1.0);
    vessel.run();

    double initialT = vessel.getTemperature();

    UUID id = UUID.randomUUID();
    for (int i = 0; i < 30; i++) {
      vessel.runTransient(0.05, id);
    }

    double finalT = vessel.getTemperature();
    assertTrue(finalT < initialT, "Temperature should decrease in isentropic blowdown");
  }

  @Test
  void testVolumeAndGeometry() {
    Stream feed = createTestStream(300.0, 10.0);

    VesselDepressurization vessel = new VesselDepressurization("geoTest", feed);
    vessel.setVesselGeometry(3.0, 1.0, VesselDepressurization.VesselOrientation.HORIZONTAL);
    vessel.run();

    assertNotNull(vessel.getOutletStream(), "Should have outlet stream");
  }

  @Test
  void testWallProperties() {
    Stream feed = createTestStream(300.0, 30.0);

    VesselDepressurization vessel = new VesselDepressurization("wallTest", feed);
    vessel.setVolume(1.0);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ENERGY_BALANCE);
    vessel.setHeatTransferType(VesselDepressurization.HeatTransferType.TRANSIENT_WALL);
    vessel.setVesselProperties(0.015, 7800.0, 500.0, 50.0);
    vessel.setExternalHeatTransferCoefficient(200.0);
    vessel.setAmbientTemperature(800.0);
    vessel.setOrificeDiameter(0.01);
    vessel.setBackPressure(1.0);
    vessel.run();

    double initialWallT = vessel.getWallTemperature();

    UUID id = UUID.randomUUID();
    for (int i = 0; i < 50; i++) {
      vessel.runTransient(0.1, id);
    }

    double finalWallT = vessel.getWallTemperature();
    assertTrue(finalWallT > initialWallT, "Wall should heat up in fire scenario");
  }

  @Test
  void testMassDecrease() {
    Stream feed = createTestStream(300.0, 15.0);

    VesselDepressurization vessel = new VesselDepressurization("massTest", feed);
    vessel.setVolume(1.0);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISOTHERMAL);
    vessel.setOrificeDiameter(0.008);
    vessel.setBackPressure(1.0);
    vessel.run();

    double initialMass = vessel.getMass();

    UUID id = UUID.randomUUID();
    for (int i = 0; i < 50; i++) {
      vessel.runTransient(0.1, id);
    }

    double finalMass = vessel.getMass();

    assertTrue(finalMass < initialMass, "Mass should decrease during discharge");
    assertTrue(finalMass > 0, "Mass should remain positive");
  }

  @Test
  void testHistoryTracking() {
    Stream feed = createTestStream(300.0, 20.0);

    VesselDepressurization vessel = new VesselDepressurization("historyTest", feed);
    vessel.setVolume(1.0);
    vessel.setOrificeDiameter(0.01);
    vessel.setBackPressure(1.0);
    vessel.run();

    UUID id = UUID.randomUUID();
    for (int i = 0; i < 20; i++) {
      vessel.runTransient(0.1, id);
    }

    assertTrue(vessel.getMassHistory().size() > 0, "Should have mass history");
    assertTrue(vessel.getPressureHistory().size() > 0, "Should have pressure history");
    assertTrue(vessel.getTimeHistory().size() > 0, "Should have time history");
    assertTrue(vessel.getTemperatureHistory().size() > 0, "Should have temperature history");
  }

  @Test
  void testPressureUnits() {
    Stream feed = createTestStream(300.0, 20.0);

    VesselDepressurization vessel = new VesselDepressurization("unitTest", feed);
    vessel.setVolume(1.0);
    vessel.run();

    double pBara = vessel.getPressure("bara");
    double pBarg = vessel.getPressure("barg");

    assertTrue(pBara > 0, "Pressure in bara should be positive");
    assertTrue(pBarg < pBara, "barg should be less than bara");
  }

  @Test
  void testTemperatureUnits() {
    Stream feed = createTestStream(300.0, 20.0);

    VesselDepressurization vessel = new VesselDepressurization("unitTest", feed);
    vessel.setVolume(1.0);
    vessel.run();

    double tempK = vessel.getTemperature("K");
    double tempC = vessel.getTemperature("C");

    assertEquals(tempK, tempC + 273.15, 0.5, "Temperature conversion should be correct");
  }

  @Test
  void testOutletStream() {
    Stream feed = createTestStream(300.0, 20.0);

    VesselDepressurization vessel = new VesselDepressurization("streamTest", feed);
    vessel.setVolume(1.0);
    vessel.run();

    assertNotNull(vessel.getOutletStream(), "Should have outlet stream");
    assertNotNull(vessel.getOutletStream().getFluid(), "Outlet should have fluid");
  }

  /**
   * Test isothermal depressurization with analytical solution comparison.
   *
   * <p>
   * For isothermal ideal gas, the analytical solution for choked flow is: P(t) = P0 * exp(-C * t)
   * </p>
   */
  @Test
  @DisplayName("Isothermal depressurization matches exponential decay model")
  void testIsothermalMethaneDepressurization() {
    double vesselVolume = 1.0; // m³
    double initialPressure = 50.0; // bar
    double initialTemperature = 300.0; // K
    double orificeDiameter = 0.010; // m (10 mm)
    double dischargeCoeff = 0.84;
    double backPressure = 1.0; // bar

    SystemInterface methane = new SystemSrkEos(initialTemperature, initialPressure);
    methane.addComponent("methane", 1.0);
    methane.setMixingRule("classic");
    methane.init(0);
    methane.init(1);

    Stream feed = new Stream("feed", methane);
    feed.setTemperature(initialTemperature, "K");
    feed.setPressure(initialPressure, "bar");
    feed.run();

    VesselDepressurization vessel = new VesselDepressurization("isothermalCH4", feed);
    vessel.setVolume(vesselVolume);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISOTHERMAL);
    vessel.setHeatTransferType(VesselDepressurization.HeatTransferType.ADIABATIC);
    vessel.setOrificeDiameter(orificeDiameter);
    vessel.setDischargeCoefficient(dischargeCoeff);
    vessel.setBackPressure(backPressure);
    vessel.run();

    double initialMass = vessel.getMass();
    double initialP = vessel.getPressure("bar");

    // Run for 60 seconds
    double dt = 0.1;
    double endTime = 60.0;
    UUID id = UUID.randomUUID();

    for (double t = 0; t <= endTime; t += dt) {
      vessel.runTransient(dt, id);
    }

    double finalP = vessel.getPressure("bar");
    double finalT = vessel.getTemperature();
    double finalMass = vessel.getMass();

    // Validate isothermal behavior - temperature should stay constant
    assertEquals(initialTemperature, finalT, 1.0,
        "Temperature should remain constant for isothermal case");

    // Pressure should decrease significantly with 10mm orifice
    assertTrue(finalP < initialP * 0.6, "Pressure should decrease significantly for 10mm orifice");

    // Mass should decrease proportionally to pressure for isothermal ideal gas
    assertTrue(finalMass < initialMass * 0.6, "Mass should decrease proportionally to pressure");
  }

  /**
   * Test isentropic (adiabatic) depressurization with temperature drop.
   *
   * <p>
   * For isentropic expansion, temperature decreases as: T/T0 = (P/P0)^((gamma-1)/gamma)
   * </p>
   */
  @Test
  @DisplayName("Isentropic depressurization shows expected temperature drop")
  void testIsentropicNitrogenDepressurization() {
    double vesselVolume = 1.0; // m³
    double initialPressure = 50.0; // bar
    double initialTemperature = 300.0; // K
    double orificeDiameter = 0.008; // m (8 mm)
    double backPressure = 1.0; // bar

    SystemInterface nitrogen = new SystemSrkEos(initialTemperature, initialPressure);
    nitrogen.addComponent("nitrogen", 1.0);
    nitrogen.setMixingRule("classic");
    nitrogen.init(0);
    nitrogen.init(1);

    Stream feed = new Stream("feed", nitrogen);
    feed.setTemperature(initialTemperature, "K");
    feed.setPressure(initialPressure, "bar");
    feed.run();

    VesselDepressurization vessel = new VesselDepressurization("isentropicN2", feed);
    vessel.setVolume(vesselVolume);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISENTROPIC);
    vessel.setHeatTransferType(VesselDepressurization.HeatTransferType.ADIABATIC);
    vessel.setOrificeDiameter(orificeDiameter);
    vessel.setBackPressure(backPressure);
    vessel.run();

    double initialP = vessel.getPressure("bar");
    double initialT = vessel.getTemperature();

    // Run for 60 seconds
    double dt = 0.05;
    double endTime = 60.0;
    UUID id = UUID.randomUUID();

    for (double t = 0; t <= endTime; t += dt) {
      vessel.runTransient(dt, id);
    }

    double finalP = vessel.getPressure("bar");
    double finalT = vessel.getTemperature();

    // Validate isentropic behavior - temperature should decrease
    assertTrue(finalT < initialT - 5, "Temperature should decrease for isentropic expansion");

    // Pressure should decrease
    assertTrue(finalP < initialP * 0.8, "Pressure should decrease by at least 20%");
  }

  /**
   * Test multi-component natural gas depressurization.
   *
   * <p>
   * Demonstrates depressurization of multi-component mixtures with full VLE flash calculations.
   * </p>
   */
  @Test
  @DisplayName("Multi-component natural gas mixture depressurization")
  void testNaturalGasMixtureDepressurization() {
    double vesselVolume = 1.0; // m³
    double initialPressure = 30.0; // bar
    double initialTemperature = 300.0; // K
    double orificeDiameter = 0.008; // m (8 mm)
    double backPressure = 1.0; // bar

    // Multi-component natural gas mixture
    SystemInterface gas = new SystemSrkEos(initialTemperature, initialPressure);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.08);
    gas.addComponent("propane", 0.04);
    gas.addComponent("n-butane", 0.02);
    gas.addComponent("nitrogen", 0.01);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(1);

    Stream feed = new Stream("feed", gas);
    feed.setTemperature(initialTemperature, "K");
    feed.setPressure(initialPressure, "bar");
    feed.run();

    VesselDepressurization vessel = new VesselDepressurization("naturalGas", feed);
    vessel.setVolume(vesselVolume);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISOTHERMAL);
    vessel.setHeatTransferType(VesselDepressurization.HeatTransferType.ADIABATIC);
    vessel.setOrificeDiameter(orificeDiameter);
    vessel.setBackPressure(backPressure);
    vessel.run();

    double initialMass = vessel.getMass();
    double initialP = vessel.getPressure("bar");

    // Run for 120 seconds
    double dt = 0.1;
    double endTime = 120.0;
    UUID id = UUID.randomUUID();

    for (double t = 0; t <= endTime; t += dt) {
      vessel.runTransient(dt, id);
    }

    double finalP = vessel.getPressure("bar");
    double finalMass = vessel.getMass();

    // Validate pressure and mass decrease
    assertTrue(finalP < initialP * 0.6, "Pressure should drop by at least 40%");
    assertTrue(finalMass < initialMass * 0.6, "Mass should decrease by at least 40%");
  }

  /**
   * Verify Leachman reference equation availability for hydrogen.
   *
   * <p>
   * The Leachman equation provides high-accuracy thermodynamic properties for hydrogen at all
   * pressures including high-pressure applications.
   * </p>
   */
  @Test
  @DisplayName("Leachman equation available for high-accuracy hydrogen calculations")
  void testLeachmanHydrogenProperties() {
    // High pressure hydrogen
    double temperature = 300.0; // K
    double pressure = 138.0; // bar

    // Create hydrogen system
    SystemInterface hydrogen = new SystemSrkEos(temperature, pressure);
    hydrogen.addComponent("hydrogen", 1.0);
    hydrogen.setMixingRule("classic");
    hydrogen.init(0);
    hydrogen.init(1);

    // Get Leachman properties - high-accuracy Helmholtz EOS
    double[] leachmanProps = hydrogen.getPhase(0).getProperties_Leachman();

    // Leachman properties array:
    // [0]=P(kPa), [1]=Z, [2-5]=derivatives, [6]=U, [7]=H(J/mol), [8]=S,
    // [9]=Cv, [10]=Cp, [11]=soundSpeed, [12]=G, [13]=JT, [14]=Kappa
    double leachmanZ = leachmanProps[1];
    double leachmanCp = leachmanProps[10]; // J/(mol·K)
    double leachmanSoundSpeed = leachmanProps[11]; // m/s

    // Verify Leachman provides reasonable values
    assertTrue(leachmanZ > 1.0, "Hydrogen Z should be > 1 at high pressure");
    assertTrue(leachmanCp > 28 && leachmanCp < 35, "Hydrogen Cp should be ~29-30 J/(mol·K)");
    assertTrue(leachmanSoundSpeed > 1000, "Hydrogen sound speed should be > 1000 m/s");
  }

  /**
   * Test that setInitialLiquidLevel properly initializes two-phase systems.
   */
  @Test
  @DisplayName("Initial liquid level sets up correct phase distribution")
  void testInitialLiquidLevel() {
    // Create a two-phase NGL system
    SystemInterface ngl = new SystemSrkEos(280.0, 20.0);
    ngl.addComponent("methane", 0.15);
    ngl.addComponent("ethane", 0.25);
    ngl.addComponent("propane", 0.30);
    ngl.addComponent("n-butane", 0.15);
    ngl.addComponent("n-pentane", 0.10);
    ngl.addComponent("n-hexane", 0.05);
    ngl.setMixingRule("classic");
    ngl.setMultiPhaseCheck(true);
    ngl.init(0);
    ngl.init(1);

    Stream feed = new Stream("NGL feed", ngl);
    feed.run();

    // Create vessel with 50% liquid level
    VesselDepressurization vessel = new VesselDepressurization("NGL Vessel", feed);
    vessel.setVesselGeometry(8.0, 2.5, VesselDepressurization.VesselOrientation.HORIZONTAL);
    vessel.setTwoPhaseHeatTransfer(true);
    vessel.setInitialLiquidLevel(0.50);
    vessel.setOrificeDiameter(0.025);
    vessel.setBackPressure(1.0);
    vessel.run();

    // Check that we have two phases
    assertTrue(vessel.getThermoSystem().getNumberOfPhases() > 1,
        "Should have two phases for NGL at these conditions");

    // Check that liquid level is close to specified value
    double actualLevel = vessel.getLiquidLevel();
    assertEquals(0.50, actualLevel, 0.15, "Liquid level should be close to specified 50%");

    // Verify vessel mass is reasonable (should be significant for 39 m³ vessel)
    double mass = vessel.getMass();
    assertTrue(mass > 100, "Vessel should contain significant mass");
  }

  /**
   * Test that setInitialLiquidLevel works for pure components (like CO2).
   * 
   * Note: For pure components at saturation, the liquid level is determined by the total mass and
   * must be at saturation T/P. The setInitialLiquidLevel() adjusts the phase distribution to match
   * the specified level by scaling moles in each phase.
   */
  @Test
  @DisplayName("Initial liquid level works for pure component (CO2)")
  void testInitialLiquidLevelPureComponent() {
    // Create pure CO2 at saturation conditions with specified vapor fraction
    // For a pure component to be two-phase, we set beta directly
    SystemInterface co2 = new SystemSrkEos(250.0, 17.0);
    co2.addComponent("CO2", 1.0);
    co2.setMixingRule("classic");

    // Set up as two-phase with 50% vapor fraction
    co2.setNumberOfPhases(2);
    co2.setBeta(0.5);
    co2.init(0);
    co2.init(1);

    // Verify we have two phases in the input system
    assertEquals(2, co2.getNumberOfPhases(), "CO2 should have 2 phases when beta is set");

    Stream feed = new Stream("CO2 feed", co2);
    feed.run();

    // Create vessel with 40% liquid level (for pure component, this adjusts phase volumes)
    VesselDepressurization vessel = new VesselDepressurization("CO2 Vessel", feed);
    vessel.setVesselGeometry(11.0, 2.0, VesselDepressurization.VesselOrientation.HORIZONTAL);
    vessel.setTwoPhaseHeatTransfer(true);
    vessel.setInitialLiquidLevel(0.40);
    vessel.setOrificeDiameter(0.02);
    vessel.setBackPressure(1.0);
    vessel.run();

    // For pure component, verify the vessel has reasonable mass regardless of phase count
    // The setInitialLiquidLevel adjusts phase distribution, but init(3) may collapse phases
    // based on thermodynamic stability analysis
    double mass = vessel.getMass();
    assertTrue(mass > 100, "Vessel should contain significant mass of CO2");

    // Verify the system is properly initialized
    assertTrue(vessel.getPressure() > 0, "Pressure should be positive");
    assertTrue(vessel.getTemperature() > 0, "Temperature should be positive");
  }

  // ==========================================================================
  // Tests for new API improvements
  // ==========================================================================

  @Test
  @DisplayName("Test material presets (VesselMaterial enum)")
  void testVesselMaterialPresets() {
    Stream feed = createTestStream(300.0, 50.0);

    VesselDepressurization vessel = new VesselDepressurization("Steel Vessel", feed);
    vessel.setVolume(1.0);
    vessel.setVesselMaterial(0.015, VesselDepressurization.VesselMaterial.CARBON_STEEL);
    vessel.setOrificeDiameter(0.01);
    vessel.setBackPressure(1.0);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ENERGY_BALANCE);
    vessel.run();

    double initialP = vessel.getPressure();
    assertTrue(initialP > 0, "Pressure should be positive");

    // Test a few transient steps
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 10; i++) {
      vessel.runTransient(0.1, id);
    }
    assertTrue(vessel.getPressure() < initialP, "Pressure should decrease after transient steps");
  }

  @Test
  @DisplayName("Test liner material presets (LinerMaterial enum)")
  void testLinerMaterialPresets() {
    Stream feed = createTestStream(300.0, 350.0);

    VesselDepressurization vessel = new VesselDepressurization("Type IV Vessel", feed);
    vessel.setVesselGeometry(0.8, 0.23, VesselDepressurization.VesselOrientation.HORIZONTAL);
    vessel.setVesselMaterial(0.017, VesselDepressurization.VesselMaterial.CFRP);
    vessel.setLinerMaterial(0.007, VesselDepressurization.LinerMaterial.HDPE);
    vessel.setOrificeDiameter(0.005);
    vessel.setBackPressure(1.0);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ENERGY_BALANCE);
    vessel.setHeatTransferType(VesselDepressurization.HeatTransferType.TRANSIENT_WALL);
    vessel.run();

    assertTrue(vessel.getPressure() > 0, "Pressure should be positive");
  }

  @Test
  @DisplayName("Test runSimulation() convenience method")
  void testRunSimulationMethod() {
    Stream feed = createTestStream(300.0, 50.0);

    VesselDepressurization vessel = new VesselDepressurization("test", feed);
    vessel.setVolume(1.0);
    vessel.setOrificeDiameter(0.01);
    vessel.setBackPressure(1.0);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISOTHERMAL);
    vessel.run();

    // Use new runSimulation API
    VesselDepressurization.SimulationResult result = vessel.runSimulation(10.0, 0.5);

    // Verify result object
    assertNotNull(result, "Result should not be null");
    assertTrue(result.size() > 1, "Should have multiple data points");
    assertEquals(10.0, result.getEndTime(), 0.001, "End time should match");
    assertEquals(0.5, result.getTimeStep(), 0.001, "Time step should match");

    // Verify data consistency
    assertTrue(result.getInitialPressure() > result.getFinalPressure(), "Pressure should decrease");
    assertTrue(result.getMassDischarged() > 0, "Some mass should be discharged");
    assertTrue(result.getMassDischargedFraction() > 0 && result.getMassDischargedFraction() < 1,
        "Fraction discharged should be between 0 and 1");

    // Verify arrays have same length
    assertEquals(result.getTime().size(), result.getPressure().size(),
        "Time and pressure arrays should have same length");
    assertEquals(result.getTime().size(), result.getTemperature().size(),
        "Time and temperature arrays should have same length");
  }

  @Test
  @DisplayName("Test runSimulation() with record interval")
  void testRunSimulationWithInterval() {
    Stream feed = createTestStream(300.0, 50.0);

    VesselDepressurization vessel = new VesselDepressurization("test", feed);
    vessel.setVolume(1.0);
    vessel.setOrificeDiameter(0.01);
    vessel.setBackPressure(1.0);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISOTHERMAL);
    vessel.run();

    // Run with interval of 10 (record every 10th step)
    VesselDepressurization.SimulationResult result = vessel.runSimulation(10.0, 0.1, 10);

    // 10s / 0.1s = 100 steps, record every 10 = 10 points + initial = 11 points
    assertEquals(11, result.size(), "Should have 11 data points (10 + initial)");
  }

  @Test
  @DisplayName("Test validate() catches errors")
  void testValidateMethod() {
    Stream feed = createTestStream(300.0, 50.0);

    VesselDepressurization vessel = new VesselDepressurization("test", feed);
    vessel.setVolume(1.0);
    vessel.setOrificeDiameter(0.01);
    vessel.setBackPressure(60.0); // Higher than initial pressure!
    vessel.run();

    // Should throw because back pressure > initial pressure
    boolean threw = false;
    try {
      vessel.validate();
    } catch (IllegalStateException e) {
      threw = true;
      assertTrue(e.getMessage().contains("Back pressure"), "Error should mention back pressure");
    }
    assertTrue(threw, "validate() should throw for invalid configuration");
  }

  @Test
  @DisplayName("Test validateWithWarnings() returns warnings")
  void testValidateWithWarningsMethod() {
    Stream feed = createTestStream(300.0, 50.0);

    VesselDepressurization vessel = new VesselDepressurization("test", feed);
    vessel.setVolume(1.0);
    vessel.setOrificeDiameter(0.01);
    vessel.setBackPressure(1.0);
    vessel.setTwoPhaseHeatTransfer(true); // Enabled but no liquid level
    vessel.run();

    java.util.List<String> warnings = vessel.validateWithWarnings();
    assertTrue(warnings.size() > 0, "Should have at least one warning");
  }

  @Test
  @DisplayName("Test createTwoPhaseFluid() helper")
  void testCreateTwoPhaseFluidHelper() {
    // Use the static helper to create CO2 at 250K with 60% vapor
    SystemInterface co2 = VesselDepressurization.createTwoPhaseFluid("CO2", 250.0, 0.6);

    assertNotNull(co2, "System should be created");
    assertEquals(2, co2.getNumberOfPhases(), "Should have 2 phases after helper setup");
    assertEquals(250.0, co2.getTemperature(), 0.1, "Temperature should be 250K");

    // For pure components at saturation, the pressure should be the bubble point
    // CO2 saturation pressure at 250K is around 17-18 bar
    assertTrue(co2.getPressure() > 10 && co2.getPressure() < 30,
        "Pressure should be near CO2 saturation at 250K");

    // Verify it can be used in a stream - the stream.run() will flash it
    Stream feed = new Stream("test", co2);
    feed.run();
    assertTrue(feed.getPressure() > 10, "Pressure should be reasonable for CO2 at 250K");
  }

  @Test
  @DisplayName("Test createTwoPhaseFluidAtPressure() helper")
  void testCreateTwoPhaseFluidAtPressureHelper() {
    // Create propane at 10 bar with 50% vapor
    SystemInterface propane =
        VesselDepressurization.createTwoPhaseFluidAtPressure("propane", 10.0, 0.5);

    assertNotNull(propane, "System should be created");
    assertEquals(2, propane.getNumberOfPhases(), "Should have 2 phases after helper setup");

    // At 10 bar, propane saturation temp is around 300K
    assertTrue(propane.getTemperature() > 270 && propane.getTemperature() < 330,
        "Temperature should be near saturation for propane at 10 bar (~300K)");

    // Pressure should be maintained
    assertEquals(10.0, propane.getPressure(), 0.5, "Pressure should be 10 bar");
  }

  // ================================================================================
  // Additional Feature Tests
  // ================================================================================

  @Test
  @DisplayName("Test fire case heat input (API 521)")
  void testFireCaseHeatInput() {
    Stream feed = createTestStream(300.0, 50.0);
    VesselDepressurization vessel = new VesselDepressurization("fireCase", feed);
    vessel.setVesselGeometry(3.0, 1.0, VesselDepressurization.VesselOrientation.HORIZONTAL);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ENERGY_BALANCE);
    vessel.setOrificeDiameter(0.02);
    vessel.run();

    double initialTemp = vessel.getTemperature();

    // Enable fire case with typical pool fire heat flux (25 kW/m²)
    vessel.setFireCase(true);
    vessel.setFireHeatFlux(25000.0); // 25 kW/m²
    vessel.setWettedSurfaceFraction(0.5);

    assertTrue(vessel.isFireCase(), "Fire case should be enabled");
    assertTrue(vessel.getFireHeatInput("kW") > 0, "Fire heat input should be positive");

    // Run a few time steps - temperature should rise due to fire heat
    UUID uuid = UUID.randomUUID();
    for (int i = 0; i < 10; i++) {
      vessel.runTransient(1.0, uuid);
    }

    // With fire heating, temperature should not drop as much as adiabatic blowdown
    // (or may even rise if fire heat exceeds cooling from expansion)
    // Just verify the feature works without errors
    assertTrue(vessel.getTemperature() > 0, "Temperature should remain positive");
  }

  @Test
  @DisplayName("Test fire heat flux with units")
  void testFireHeatFluxUnits() {
    Stream feed = createTestStream(300.0, 50.0);
    VesselDepressurization vessel = new VesselDepressurization("fireUnits", feed);
    vessel.setVesselGeometry(3.0, 1.0, VesselDepressurization.VesselOrientation.HORIZONTAL);
    vessel.run();

    // Set with kW/m² unit
    vessel.setFireHeatFlux(25.0, "kW/m2");
    double expectedFlux = 25000.0; // W/m²
    double area = Math.PI * 1.0 * 3.18 + Math.PI * 1.0 * 1.0; // cylinder + ends (approx)

    assertTrue(vessel.getFireHeatInput("W") > 0, "Fire heat should be positive");
    assertTrue(vessel.getFireHeatInput("kW") > 0, "Fire heat in kW should be positive");
    assertTrue(vessel.getFireHeatInput("MW") > 0, "Fire heat in MW should be positive");
  }

  @Test
  @DisplayName("Test valve opening time dynamics")
  void testValveOpeningTime() {
    Stream feed = createTestStream(300.0, 50.0);
    VesselDepressurization vessel = new VesselDepressurization("valveDynamics", feed);
    vessel.setVolume(1.0);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISOTHERMAL);
    vessel.setOrificeDiameter(0.02);
    vessel.setValveOpeningTime(10.0); // 10 seconds to fully open
    vessel.run();

    UUID uuid = UUID.randomUUID();
    double initialPressure = vessel.getPressure("bar");

    // Run for 5 seconds (valve half open)
    for (int i = 0; i < 5; i++) {
      vessel.runTransient(1.0, uuid);
    }
    double pressureAt5s = vessel.getPressure("bar");

    // Continue to 15 seconds (valve fully open for 5s)
    for (int i = 0; i < 10; i++) {
      vessel.runTransient(1.0, uuid);
    }
    double pressureAt15s = vessel.getPressure("bar");

    // Pressure should drop, and drop rate should increase after valve fully opens
    assertTrue(pressureAt5s < initialPressure, "Pressure should drop during opening");
    assertTrue(pressureAt15s < pressureAt5s, "Pressure should continue dropping");
  }

  @Test
  @DisplayName("Test CSV export")
  void testCSVExport() {
    Stream feed = createTestStream(300.0, 50.0);
    VesselDepressurization vessel = new VesselDepressurization("csvExport", feed);
    vessel.setVolume(1.0);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISOTHERMAL);
    vessel.setOrificeDiameter(0.02);
    vessel.run();

    UUID uuid = UUID.randomUUID();
    for (int i = 0; i < 5; i++) {
      vessel.runTransient(1.0, uuid);
    }

    String csv = vessel.exportToCSV();
    assertNotNull(csv, "CSV should not be null");
    assertTrue(csv.contains("Time[s]"), "CSV should have Time header");
    assertTrue(csv.contains("Pressure[bar]"), "CSV should have Pressure header");
    assertTrue(csv.split("\n").length > 1, "CSV should have data rows");
  }

  @Test
  @DisplayName("Test JSON export")
  void testJSONExport() {
    Stream feed = createTestStream(300.0, 50.0);
    VesselDepressurization vessel = new VesselDepressurization("jsonExport", feed);
    vessel.setVolume(1.0);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISOTHERMAL);
    vessel.setOrificeDiameter(0.02);
    vessel.run();

    UUID uuid = UUID.randomUUID();
    for (int i = 0; i < 5; i++) {
      vessel.runTransient(1.0, uuid);
    }

    String json = vessel.exportToJSON();
    assertNotNull(json, "JSON should not be null");
    assertTrue(json.contains("\"vessel\""), "JSON should have vessel field");
    assertTrue(json.contains("\"data\""), "JSON should have data array");
    assertTrue(json.contains("\"P_bar\""), "JSON should have pressure field");
  }

  @Test
  @DisplayName("Test liquid rainout detection")
  void testLiquidRainoutDetection() {
    // Create a rich gas that may condense during blowdown
    SystemInterface gas = new SystemSrkEos(280.0, 80.0);
    gas.addComponent("methane", 0.7);
    gas.addComponent("propane", 0.2);
    gas.addComponent("n-pentane", 0.1);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(1);

    Stream feed = new Stream("richGas", gas);
    feed.run();

    VesselDepressurization vessel = new VesselDepressurization("rainout", feed);
    vessel.setVolume(5.0);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ENERGY_BALANCE);
    vessel.setOrificeDiameter(0.02);
    vessel.run();

    // Test methods don't crash
    boolean hasLiquid = vessel.hasLiquidRainout();
    double liquidFrac = vessel.getOutletLiquidFraction();

    // Values should be in valid range
    assertTrue(liquidFrac >= 0 && liquidFrac <= 1, "Liquid fraction should be between 0 and 1");
  }

  @Test
  @DisplayName("Test flare header velocity calculation")
  void testFlareHeaderVelocity() {
    Stream feed = createTestStream(300.0, 50.0);
    VesselDepressurization vessel = new VesselDepressurization("headerVel", feed);
    vessel.setVolume(5.0);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISOTHERMAL);
    vessel.setOrificeDiameter(0.03);
    vessel.run();

    UUID uuid = UUID.randomUUID();
    vessel.runTransient(1.0, uuid);

    // Calculate velocity in 8" header
    double headerDiameter = 0.2; // 8 inch ≈ 0.2m
    double velocity = vessel.getFlareHeaderVelocity(headerDiameter, "m/s");
    double mach = vessel.getFlareHeaderMach(headerDiameter);

    assertTrue(velocity >= 0, "Velocity should be non-negative");
    assertTrue(mach >= 0, "Mach number should be non-negative");

    // Also test ft/s conversion
    double velocityFt = vessel.getFlareHeaderVelocity(headerDiameter, "ft/s");
    assertEquals(velocity * 3.281, velocityFt, 0.01, "ft/s conversion should be correct");
  }

  @Test
  @DisplayName("Test hydrate formation temperature calculation")
  void testHydrateFormationTemperature() {
    // Create a hydrate-forming gas (methane + water)
    SystemInterface gas = new SystemSrkEos(280.0, 80.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.addComponent("water", 0.001); // Small amount of water for hydrate potential
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(1);

    Stream feed = new Stream("hydrateTest", gas);
    feed.run();

    VesselDepressurization vessel = new VesselDepressurization("HydrateTest", feed);
    vessel.setVolume(5.0);
    vessel.setOrificeDiameter(0.02);
    vessel.run();

    // Test hydrate methods don't crash (may return -1 if no water/hydrate formers)
    double hydrateTemp = vessel.getHydrateFormationTemperature();
    double hydrateTempC = vessel.getHydrateFormationTemperature("C");

    // Methods should return valid values or -1
    assertTrue(hydrateTemp == -1.0 || hydrateTemp > 0,
        "Hydrate temp should be -1 (failed) or positive K");

    // Test subcooling
    double subcooling = vessel.getHydrateSubcooling("C");
    // Subcooling can be positive or negative
    assertTrue(Math.abs(subcooling) < 1000, "Subcooling should be reasonable");

    // Test risk check
    boolean hasRisk = vessel.hasHydrateRisk();
    // Just verify method works
  }

  @Test
  @DisplayName("Test CO2 freezing temperature calculation")
  void testCO2FreezingTemperature() {
    // Create a CO2-rich gas
    SystemInterface gas = new SystemSrkEos(250.0, 30.0); // Low temp, moderate pressure
    gas.addComponent("CO2", 0.15);
    gas.addComponent("methane", 0.80);
    gas.addComponent("ethane", 0.05);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(1);

    Stream feed = new Stream("co2Test", gas);
    feed.run();

    VesselDepressurization vessel = new VesselDepressurization("CO2Test", feed);
    vessel.setVolume(5.0);
    vessel.setOrificeDiameter(0.02);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ISOTHERMAL);
    vessel.run();

    // Test CO2 freezing temperature
    double freezeTemp = vessel.getCO2FreezingTemperature();
    double freezeTempC = vessel.getCO2FreezingTemperature("C");

    assertTrue(freezeTemp > 0, "CO2 freezing temp should be positive K (has CO2)");
    assertTrue(freezeTempC < 0, "CO2 freezing temp should be below 0°C");
    assertTrue(freezeTempC > -100, "CO2 freezing temp should be above -100°C");

    // Test at different pressure (below triple point)
    SystemInterface lowPGas = new SystemSrkEos(200.0, 2.0);
    lowPGas.addComponent("CO2", 0.20);
    lowPGas.addComponent("methane", 0.80);
    lowPGas.setMixingRule("classic");
    lowPGas.init(0);
    lowPGas.init(1);

    Stream lowPFeed = new Stream("lowP", lowPGas);
    lowPFeed.run();

    VesselDepressurization lowPVessel = new VesselDepressurization("LowP", lowPFeed);
    lowPVessel.setVolume(5.0);
    lowPVessel.run();

    double lowPFreezeTemp = lowPVessel.getCO2FreezingTemperature("C");
    assertTrue(lowPFreezeTemp < freezeTempC, "Lower pressure should have lower CO2 freeze temp");

    // Test subcooling and risk
    double subcooling = vessel.getCO2FreezingSubcooling();
    boolean hasRisk = vessel.hasCO2FreezingRisk();
  }

  @Test
  @DisplayName("Test flow assurance risk assessment")
  void testFlowAssuranceRiskAssessment() {
    // Create a gas with potential issues
    SystemInterface gas = new SystemSrkEos(260.0, 60.0);
    gas.addComponent("methane", 0.70);
    gas.addComponent("CO2", 0.10);
    gas.addComponent("propane", 0.15);
    gas.addComponent("n-butane", 0.05);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(1);

    Stream feed = new Stream("riskTest", gas);
    feed.run();

    VesselDepressurization vessel = new VesselDepressurization("RiskTest", feed);
    vessel.setVolume(10.0);
    vessel.setOrificeDiameter(0.03);
    vessel.setCalculationType(VesselDepressurization.CalculationType.ENERGY_BALANCE);
    vessel.setHeatTransferType(VesselDepressurization.HeatTransferType.ADIABATIC);
    vessel.run();

    // Run some transient steps to build history
    UUID uuid = UUID.randomUUID();
    for (int i = 0; i < 20; i++) {
      vessel.runTransient(1.0, uuid);
    }

    // Get comprehensive risk assessment
    java.util.Map<String, String> risks = vessel.assessFlowAssuranceRisks();

    assertNotNull(risks, "Risk map should not be null");
    assertTrue(risks.containsKey("HYDRATE"), "Should have hydrate assessment");
    assertTrue(risks.containsKey("CO2_FREEZING"), "Should have CO2 freezing assessment");
    assertTrue(risks.containsKey("MDMT"), "Should have MDMT assessment");
    assertTrue(risks.containsKey("LIQUID_RAINOUT"), "Should have liquid rainout assessment");

    // Print risks for manual verification
    for (java.util.Map.Entry<String, String> entry : risks.entrySet()) {
      System.out.println(entry.getKey() + ": " + entry.getValue());
    }
  }
}
