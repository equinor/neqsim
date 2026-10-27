package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Test class for transient separator simulation using improved VU flash calculations. This test replicates a complex
 * oil processing scenario with control systems.
 */
public class TransientSeparatorVUFlashTest {
  private static final Logger logger = LogManager.getLogger(TransientSeparatorVUFlashTest.class);

  // Process parameters
  private static final double TIME_STEP = 10.0; // seconds
  private static final double TOTAL_TIME = 300.0; // seconds (reduced for testing)
  private static final String SEP_ORIENTATION = "horizontal";
  private static final double SEP_LENGTH = 5.0; // meters
  private static final double SEP_DIAMETER = 1.0; // meters
  private static final double SEP_LIQUID_LEVEL = 0.75; // initial level
  private static final double PRESSURE_SP = 7.0; // bars
  private static final double LEVEL_SP = 0.8;
  private static final double FLOW_SP = 50.0;
  private static final double INLET_VALVE_OPENING = 80.0; // %
  private static final double INLET_VALVE_PRESSURE = 8.0; // bar
  private static final double GAS_VALVE_OPENING = 25.0; // %
  private static final double GAS_VALVE_PRESSURE = 2.0; // bar
  private static final double LIQUID_VALVE_OPENING = 25.0; // %
  private static final double LIQUID_VALVE_PRESSURE = 2.0; // bar

  // Well conditions
  private static final double P_WELL = 9.0; // barg
  private static final double T_WELL = 40.0; // C
  private static final double FLOW_RATE = 400.0; // kg/hr

  private SystemInterface wellfluid;
  private ProcessSystem oilProcess;

  @BeforeEach
  void setUp() {
    // Setup inlet stream composition and EOS
    wellfluid = new SystemPrEos(298.15, 1.01325);
    wellfluid.addComponent("CO2", 1.5870);
    wellfluid.addComponent("methane", 52.51);
    wellfluid.addComponent("ethane", 6.24);
    wellfluid.addComponent("propane", 4.23);
    wellfluid.addComponent("i-butane", 0.855);
    wellfluid.addComponent("n-butane", 2.213);
    wellfluid.addComponent("i-pentane", 1.124);
    wellfluid.addComponent("n-pentane", 1.271);
    wellfluid.addComponent("n-hexane", 2.289);

    // Add TBP fractions
    wellfluid.addTBPfraction("C7+_cut1", 0.8501, 108.47 / 1000.0, 0.7411);
    wellfluid.addTBPfraction("C7+_cut2", 1.2802, 120.4 / 1000.0, 0.755);
    wellfluid.addTBPfraction("C7+_cut3", 1.6603, 133.64 / 1000.0, 0.7695);
    wellfluid.addTBPfraction("C7+_cut4", 6.5311, 164.70 / 1000.0, 0.799);
    wellfluid.addTBPfraction("C7+_cut5", 6.3311, 215.94 / 1000.0, 0.8387);
    wellfluid.addTBPfraction("C7+_cut6", 4.9618, 273.34 / 1000.0, 0.8754);
    wellfluid.addTBPfraction("C7+_cut7", 2.9105, 334.92 / 1000.0, 0.90731);
    wellfluid.addTBPfraction("C7+_cut8", 30.0505, 412.79 / 1000.0, 0.9575);

    wellfluid.setPressure(P_WELL);
    wellfluid.setTemperature(273.15 + T_WELL);
    wellfluid.setMixingRule("classic");
  }

  @Test
  @DisplayName("Test transient separator simulation with VU flash calculations")
  void testTransientSeparatorWithVUFlash() {
    // Construct the process

    // Well stream
    Stream wellStream = new Stream("well stream", wellfluid);
    wellStream.setFlowRate(FLOW_RATE, "kg/hr");

    // Inlet valve
    ThrottlingValve lcv00 = new ThrottlingValve("LCV-00", wellStream);
    lcv00.setPercentValveOpening(INLET_VALVE_OPENING);
    lcv00.setOutletPressure(INLET_VALVE_PRESSURE);
    lcv00.setCalculateSteadyState(false);
    lcv00.setMinimumValveOpening(0.01);
    lcv00.setMaximumValveOpening(85.0);
    lcv00.setCv(120.0);

    // 1st stage separator
    Separator v001 = new Separator("V-001", lcv00.getOutletStream());
    v001.setCalculateSteadyState(false);
    v001.setOrientation(SEP_ORIENTATION);
    v001.setSeparatorLength(SEP_LENGTH);
    v001.setInternalDiameter(SEP_DIAMETER);
    v001.setLiquidLevel(SEP_LIQUID_LEVEL);

    // Oil outlet control valve
    ThrottlingValve lcv001 = new ThrottlingValve("LCV-001", v001.getLiquidOutStream());
    lcv001.setPercentValveOpening(LIQUID_VALVE_OPENING);
    lcv001.setOutletPressure(LIQUID_VALVE_PRESSURE);
    lcv001.setCalculateSteadyState(false);
    lcv001.setMinimumValveOpening(1.0);
    lcv001.setMaximumValveOpening(85.0);

    // Gas outlet control valve
    ThrottlingValve pcv001 = new ThrottlingValve("PCV-001", v001.getGasOutStream());
    pcv001.setPercentValveOpening(GAS_VALVE_OPENING);
    pcv001.setOutletPressure(GAS_VALVE_PRESSURE);
    pcv001.setCalculateSteadyState(false);
    pcv001.setMinimumValveOpening(0.01);
    pcv001.setCv(80.0);

    // Measurement devices
    LevelTransmitter lt01 = new LevelTransmitter(v001);
    lt01.setMaximumValue(0.99);
    lt01.setMinimumValue(0.01);

    PressureTransmitter pt01 = new PressureTransmitter(v001.getGasOutStream());
    pt01.setUnit("bar");
    pt01.setMaximumValue(50.0);
    pt01.setMinimumValue(1.0);

    VolumeFlowTransmitter ft01 = new VolumeFlowTransmitter(lcv001.getOutletStream());
    ft01.setUnit("kg/hr");
    ft01.setMinimumValue(1.0);
    ft01.setMaximumValue(500.0);

    // Controllers
    ControllerDeviceBaseClass lc01 = new ControllerDeviceBaseClass();
    lc01.setTransmitter(lt01);
    lc01.setReverseActing(true);
    lc01.setControllerSetPoint(LEVEL_SP);
    lc01.setControllerParameters(1.0, 400.0, 0.0); // Kp, Ti, Td
    lc01.setOutputLimits(0.0, 100.0);

    ControllerDeviceBaseClass pc01 = new ControllerDeviceBaseClass();
    pc01.setTransmitter(pt01);
    pc01.setReverseActing(false);
    pc01.setControllerSetPoint(PRESSURE_SP);
    pc01.setControllerParameters(1.0, 400.0, 0.0);
    pc01.setOutputLimits(0.0, 100.0);

    ControllerDeviceBaseClass fc01 = new ControllerDeviceBaseClass();
    fc01.setTransmitter(ft01);
    fc01.setReverseActing(false);
    fc01.setControllerSetPoint(FLOW_SP);
    fc01.setControllerParameters(0.1, 1.0, 0.0);

    // Process system
    oilProcess = new ProcessSystem("oil process");
    oilProcess.add(wellStream);
    oilProcess.add(lcv00);
    oilProcess.add(v001);
    oilProcess.add(lcv001);
    oilProcess.add(pcv001);
    oilProcess.add(lt01);

    // Set controllers
    lcv00.setController(lc01);
    pcv001.setController(pc01);
    lcv001.setController(fc01);

    // Initial steady state run
    oilProcess.run();

    // Verify initial conditions
    assertNotNull(v001);
    assertTrue(v001.getLiquidLevel() >= 0.0 && v001.getLiquidLevel() <= 1.0);
    assertTrue(v001.getThermoSystem().getPressure() > 0.0);

    // Store initial state
    double initialLevel = v001.getLiquidLevel();
    double initialPressure = v001.getThermoSystem().getPressure();

    logger.printf(org.apache.logging.log4j.Level.INFO, "Initial conditions: Level=%.3f, Pressure=%.3f bar%n",
        initialLevel, initialPressure);

    // Data collection for monitoring
    List<Double> timeData = new ArrayList<>();
    List<Double> levelData = new ArrayList<>();
    List<Double> pressureData = new ArrayList<>();
    List<Double> liquidVolumeData = new ArrayList<>();
    List<Double> valveOpeningData = new ArrayList<>();

    // Setup transient simulation
    oilProcess.setTimeStep(TIME_STEP);
    oilProcess.runTransient();

    // Run until the historically accepted rejected VU iterate is reached.
    int steps = (int) (TOTAL_TIME / TIME_STEP);
    for (int i = 0; i < steps; i++) {
      double currentTime = oilProcess.getTime();
      double currentLevel = v001.getLiquidLevel();
      double currentPressure = v001.getThermoSystem().getPressure();
      double liquidVolume = v001.calcLiquidVolume();
      double valveOpening = lcv00.getPercentValveOpening();

      timeData.add(currentTime);
      levelData.add(currentLevel);
      pressureData.add(currentPressure);
      liquidVolumeData.add(liquidVolume);
      valveOpeningData.add(valveOpening);

      assertTrue(currentLevel >= 0.0 && currentLevel <= 1.0, "Liquid level must be between 0 and 1");
      assertTrue(currentPressure > 0.0, "Pressure must be positive");
      assertTrue(liquidVolume >= 0.0, "Liquid volume must be non-negative");
      assertTrue(valveOpening >= 0.0 && valveOpening <= 100.0, "Valve opening must be between 0 and 100%");

      double separatorTimeBeforeStep = v001.getTime();
      java.util.UUID separatorIdBeforeStep = v001.getCalculationIdentifier();
      double levelBeforeStep = v001.getLiquidLevel();
      double pressureBeforeStep = v001.getThermoSystem().getPressure();
      double temperatureBeforeStep = v001.getThermoSystem().getTemperature();
      double energyBeforeStep = v001.getThermoSystem().getInternalEnergy();
      double[] componentMolesBeforeStep = componentMoles(v001.getThermoSystem());
      Stream gasOutletIdentity = (Stream) v001.getGasOutStream();
      Stream liquidOutletIdentity = (Stream) v001.getLiquidOutStream();
      UUID gasOutletIdBeforeStep = gasOutletIdentity.getCalculationIdentifier();
      UUID liquidOutletIdBeforeStep = liquidOutletIdentity.getCalculationIdentifier();
      double gasOutletTimeBeforeStep = gasOutletIdentity.getTime();
      double liquidOutletTimeBeforeStep = liquidOutletIdentity.getTime();
      StreamInterface mixerOutletIdentity = v001.inletStreamMixer.getOutletStream();
      UUID mixerIdBeforeStep = v001.inletStreamMixer.getCalculationIdentifier();
      UUID mixerOutletIdBeforeStep = mixerOutletIdentity.getCalculationIdentifier();
      double mixerTimeBeforeStep = v001.inletStreamMixer.getTime();
      double mixerOutletTimeBeforeStep = mixerOutletIdentity.getTime();
      boolean mixerRecalculationBeforeStep = v001.inletStreamMixer.needRecalculation();
      double[] mixerInventoryBeforeStep = componentMoles(mixerOutletIdentity.getThermoSystem());
      SystemInterface gasOutletBeforeStep = gasOutletIdentity.getThermoSystem().clone();
      SystemInterface liquidOutletBeforeStep = liquidOutletIdentity.getThermoSystem().clone();

      try {
        oilProcess.runTransient(TIME_STEP);
      } catch (IllegalStateException expected) {
        assertTrue(expected.getMessage().contains("rejected transient VU state"));
        assertTrue(expected.getMessage().contains("volumeResidual="));
        assertEquals(separatorTimeBeforeStep, v001.getTime(), 0.0, "Rejected step must not advance separator time");
        assertEquals(separatorIdBeforeStep, v001.getCalculationIdentifier(),
            "Rejected step must restore the separator calculation identifier");
        assertEquals(levelBeforeStep, v001.getLiquidLevel(), 0.0, "Rejected step must restore the liquid level");
        assertEquals(pressureBeforeStep, v001.getThermoSystem().getPressure(), 0.0,
            "Rejected step must restore vessel pressure");
        assertEquals(temperatureBeforeStep, v001.getThermoSystem().getTemperature(), 0.0,
            "Rejected step must restore vessel temperature");
        assertEquals(energyBeforeStep, v001.getThermoSystem().getInternalEnergy(),
            Math.max(Math.abs(energyBeforeStep), 1.0) * 1e-12, "Rejected step must restore vessel energy");
        assertArrayEquals(componentMolesBeforeStep, componentMoles(v001.getThermoSystem()), 1e-12,
            "Rejected step must restore every component inventory");
        assertSame(gasOutletIdentity, v001.getGasOutStream(), "Gas outlet object identity must be stable");
        assertSame(liquidOutletIdentity, v001.getLiquidOutStream(), "Liquid outlet object identity must be stable");
        assertEquals(gasOutletIdBeforeStep, gasOutletIdentity.getCalculationIdentifier(),
            "Rejected step must restore the gas outlet calculation identifier");
        assertEquals(liquidOutletIdBeforeStep, liquidOutletIdentity.getCalculationIdentifier(),
            "Rejected step must restore the liquid outlet calculation identifier");
        assertEquals(gasOutletTimeBeforeStep, gasOutletIdentity.getTime(), 0.0,
            "Rejected step must restore gas outlet time");
        assertEquals(liquidOutletTimeBeforeStep, liquidOutletIdentity.getTime(), 0.0,
            "Rejected step must restore liquid outlet time");
        assertSame(mixerOutletIdentity, v001.inletStreamMixer.getOutletStream(),
            "Mixer outlet object identity must be stable");
        assertEquals(mixerIdBeforeStep, v001.inletStreamMixer.getCalculationIdentifier(),
            "Rejected step must restore the mixer calculation identifier");
        assertEquals(mixerOutletIdBeforeStep, mixerOutletIdentity.getCalculationIdentifier(),
            "Rejected step must restore the mixer outlet calculation identifier");
        assertEquals(mixerTimeBeforeStep, v001.inletStreamMixer.getTime(), 0.0,
            "Rejected step must restore mixer time");
        assertEquals(mixerOutletTimeBeforeStep, mixerOutletIdentity.getTime(), 0.0,
            "Rejected step must restore mixer outlet time");
        assertEquals(mixerRecalculationBeforeStep, v001.inletStreamMixer.needRecalculation(),
            "Rejected step must restore mixer recalculation caches");
        assertArrayEquals(mixerInventoryBeforeStep, componentMoles(mixerOutletIdentity.getThermoSystem()), 1e-12,
            "Rejected step must restore mixer outlet state");
        assertArrayEquals(componentMoles(gasOutletBeforeStep), componentMoles(v001.getGasOutStream().getThermoSystem()),
            1e-12, "Rejected step must restore the gas outlet state");
        assertArrayEquals(componentMoles(liquidOutletBeforeStep),
            componentMoles(v001.getLiquidOutStream().getThermoSystem()), 1e-12,
            "Rejected step must restore the liquid outlet state");
        return;
      }
    }
    fail("The historical rejected VU iterate was not reached");
  }

  @Test
  @DisplayName("Transactional process restores clock and separator state after rejected VU flash")
  void transactionalProcessRestoresRejectedSeparatorStep() {
    Stream feed = new Stream("transaction feed", wellfluid.clone());
    feed.setFlowRate(FLOW_RATE, "kg/hr");
    Separator separator = new Separator("transaction separator", feed);
    separator.setOrientation(SEP_ORIENTATION);
    separator.setSeparatorLength(SEP_LENGTH);
    separator.setInternalDiameter(SEP_DIAMETER);
    separator.setLiquidLevel(SEP_LIQUID_LEVEL);

    ProcessSystem process = new ProcessSystem("transaction process");
    process.add(feed);
    process.add(separator);
    process.run();
    separator.setCalculateSteadyState(false);
    separator.initializeTransientCalculation();

    // Force a numerically impossible vessel target without changing the solver tolerances.
    separator.gasVolume = 1.0e-12;
    separator.liquidVolume = 0.0;

    double processTime = process.getTime();
    double separatorTime = separator.getTime();
    UUID separatorId = separator.getCalculationIdentifier();
    double[] inventory = componentMoles(separator.getThermoSystem());
    double pressure = separator.getThermoSystem().getPressure();
    Stream gasOutlet = (Stream) separator.getGasOutStream();
    Stream liquidOutlet = (Stream) separator.getLiquidOutStream();

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> process.runTransientTransactional(5.0, UUID.randomUUID()));
    assertTrue(failure.getMessage().contains("rejected transient VU state"));
    assertEquals(processTime, process.getTime(), 0.0);
    assertEquals(separatorTime, separator.getTime(), 0.0);
    assertEquals(separatorId, separator.getCalculationIdentifier());
    assertEquals(pressure, separator.getThermoSystem().getPressure(), 0.0);
    assertArrayEquals(inventory, componentMoles(separator.getThermoSystem()), 1e-12);
    assertSame(gasOutlet, separator.getGasOutStream());
    assertSame(liquidOutlet, separator.getLiquidOutStream());
  }

  @Test
  @DisplayName("Three-phase separator restores water, oil and gas state after rejected VU flash")
  void threePhaseSeparatorRestoresRejectedStep() {
    SystemInterface fluid = new SystemSrkCPAstatoil(315.15, 10.0);
    fluid.addComponent("methane", 72.0);
    fluid.addComponent("n-heptane", 14.0);
    fluid.addComponent("water", 40.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("three-phase feed", fluid);
    feed.setFlowRate(721.0, "kg/hr");
    feed.run();
    ThreePhaseSeparator separator = new ThreePhaseSeparator("three-phase transaction separator", feed);
    separator.setInternalDiameter(1.0);
    separator.setSeparatorLength(3.0);
    separator.setLiquidLevel(0.5);
    separator.run();
    separator.setCalculateSteadyState(false);
    separator.initializeTransientCalculation();

    separator.gasVolume = 1.0e-12;
    separator.liquidVolume = 0.0;
    double waterLevel = separator.getWaterLevel();
    double oilLevel = separator.getOilLevel();
    double[] inventory = componentMoles(separator.getThermoSystem());
    Stream gasOutlet = (Stream) separator.getGasOutStream();
    Stream oilOutlet = (Stream) separator.getOilOutStream();
    Stream waterOutlet = (Stream) separator.getWaterOutStream();
    UUID gasOutletId = gasOutlet.getCalculationIdentifier();
    UUID oilOutletId = oilOutlet.getCalculationIdentifier();
    UUID waterOutletId = waterOutlet.getCalculationIdentifier();
    double[] gasInventory = componentMoles(gasOutlet.getThermoSystem());
    double[] oilInventory = componentMoles(oilOutlet.getThermoSystem());
    double[] waterInventory = componentMoles(waterOutlet.getThermoSystem());

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> separator.runTransient(5.0, UUID.randomUUID()));
    assertTrue(failure.getMessage().contains("rejected transient VU state"));
    assertEquals(waterLevel, separator.getWaterLevel(), 0.0);
    assertEquals(oilLevel, separator.getOilLevel(), 0.0);
    assertArrayEquals(inventory, componentMoles(separator.getThermoSystem()), 1e-12);
    assertSame(gasOutlet, separator.getGasOutStream());
    assertSame(oilOutlet, separator.getOilOutStream());
    assertSame(waterOutlet, separator.getWaterOutStream());
    assertEquals(gasOutletId, gasOutlet.getCalculationIdentifier());
    assertEquals(oilOutletId, oilOutlet.getCalculationIdentifier());
    assertEquals(waterOutletId, waterOutlet.getCalculationIdentifier());
    assertArrayEquals(gasInventory, componentMoles(gasOutlet.getThermoSystem()), 1e-12);
    assertArrayEquals(oilInventory, componentMoles(oilOutlet.getThermoSystem()), 1e-12);
    assertArrayEquals(waterInventory, componentMoles(waterOutlet.getThermoSystem()), 1e-12);
  }

  @Test
  @DisplayName("Unqualified separator subclasses fail transient transaction coverage")
  void separatorSubclassFailsClosed() {
    Stream feed = new Stream("subclass feed", wellfluid.clone());
    feed.setFlowRate(FLOW_RATE, "kg/hr");
    UnsupportedSeparator separator = new UnsupportedSeparator("unsupported separator", feed);
    separator.run();

    assertTrue(separator.getTransientStateCoverageIssue().contains("must extend the snapshot"));
    ProcessSystem process = new ProcessSystem("unsupported transaction process");
    process.add(feed);
    process.add(separator);
    IllegalStateException failure = assertThrows(IllegalStateException.class, process::beginTransientStepTransaction);
    assertTrue(failure.getMessage().contains("incomplete transient state coverage"));
  }

  @Test
  @DisplayName("Accepted separator steps close component, energy and volume balances under refinement")
  void acceptedStepsCloseBalancesAndRefine() {
    Separator balanceSeparator = createRefinementSeparator("balance separator");
    balanceSeparator.inletStreamMixer.run(UUID.randomUUID());
    double dt = 1.0;
    SystemInterface vesselBefore = balanceSeparator.getThermoSystem().clone();
    SystemInterface inlet = balanceSeparator.inletStreamMixer.getOutletStream().getThermoSystem();
    SystemInterface gasOutlet = balanceSeparator.getGasOutStream().getThermoSystem();
    SystemInterface liquidOutlet = balanceSeparator.getLiquidOutStream().getThermoSystem();
    boolean hasLiquid = vesselBefore.hasPhaseType("oil") || vesselBefore.hasPhaseType("aqueous");
    double expectedEnergy = vesselBefore.getInternalEnergy()
        + dt * (inlet.getEnthalpy() - gasOutlet.getEnthalpy() - (hasLiquid ? liquidOutlet.getEnthalpy() : 0.0));
    double[] expectedInventory = componentMoles(vesselBefore);
    for (int componentIndex = 0; componentIndex < expectedInventory.length; componentIndex++) {
      expectedInventory[componentIndex] += dt * (inlet.getComponent(componentIndex).getNumberOfmoles()
          - gasOutlet.getComponent(componentIndex).getNumberOfmoles()
          - (hasLiquid ? liquidOutlet.getComponent(componentIndex).getNumberOfmoles() : 0.0));
    }
    double specifiedVolume = balanceSeparator.gasVolume + balanceSeparator.liquidVolume;

    balanceSeparator.runTransient(dt, UUID.randomUUID());

    assertRelativeClosure(balanceSeparator.getThermoSystem().getVolume("m3"), specifiedVolume, 1.0e-3,
        "accepted volume");
    assertRelativeClosure(balanceSeparator.getThermoSystem().getInternalEnergy(), expectedEnergy, 1.0e-3,
        "accepted energy");
    assertArrayEquals(expectedInventory, componentMoles(balanceSeparator.getThermoSystem()), 1.0e-9,
        "accepted VU flash must conserve every updated component inventory");

    double[][] refinedStates = assertTimeout(Duration.ofSeconds(20),
        () -> new double[][] {runRefinedTrajectory(4.0), runRefinedTrajectory(2.0), runRefinedTrajectory(1.0)},
        "accepted separator refinement must retain bounded runtime");
    double[] coarse = refinedStates[0];
    double[] medium = refinedStates[1];
    double[] fine = refinedStates[2];
    double coarseToMedium = normalizedDistance(coarse, medium);
    double mediumToFine = normalizedDistance(medium, fine);
    assertTrue(coarseToMedium > 1.0e-10, "the disturbed trajectory must exercise timestep sensitivity");
    assertTrue(mediumToFine < coarseToMedium,
        "pressure, temperature and level must converge under timestep refinement: coarse-medium=" + coarseToMedium
            + ", medium-fine=" + mediumToFine);
  }

  @Test
  @DisplayName("Test VU flash convergence in separator")
  void testVUFlashConvergence() {
    // Create a simpler test focusing on VU flash performance
    Stream wellStream = new Stream("well stream", wellfluid);
    wellStream.setFlowRate(FLOW_RATE, "kg/hr");

    Separator separator = new Separator("Test-Separator", wellStream);
    separator.setCalculateSteadyState(false);
    separator.setOrientation("horizontal");
    separator.setSeparatorLength(3.0);
    separator.setInternalDiameter(1.5);
    separator.setLiquidLevel(0.5);

    ProcessSystem testProcess = new ProcessSystem("test process");
    testProcess.add(wellStream);
    testProcess.add(separator);

    // Run initial calculation
    testProcess.run();

    double initialVolume = separator.getThermoSystem().getVolume();
    double initialInternalEnergy = separator.getThermoSystem().getInternalEnergy();

    assertNotNull(separator.getThermoSystem());
    assertTrue(initialVolume > 0.0, "Initial volume should be positive");
    assertTrue(Double.isFinite(initialInternalEnergy), "Initial internal energy should be finite");

    // Test transient behavior with VU flash
    testProcess.setTimeStep(5.0);
    testProcess.runTransient();

    for (int i = 0; i < 10; i++) {
      double volume = separator.getThermoSystem().getVolume();
      double internalEnergy = separator.getThermoSystem().getInternalEnergy();
      double pressure = separator.getThermoSystem().getPressure();
      double temperature = separator.getThermoSystem().getTemperature();

      // Verify VU flash results are physically meaningful
      assertTrue(volume > 0.0, "Volume should be positive");
      assertTrue(Double.isFinite(internalEnergy), "Internal energy should be finite");
      assertTrue(pressure > 0.0, "Pressure should be positive");
      assertTrue(temperature > 0.0, "Temperature should be positive");

      testProcess.runTransient(5.0);
    }

    logger.info("VU flash convergence test completed successfully");
  }

  /**
   * Calculate variance of a list of values
   */
  private double calculateVariance(List<Double> values) {
    if (values.size() < 2) {
      return 0.0;
    }

    double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    double variance = values.stream().mapToDouble(val -> Math.pow(val - mean, 2)).average().orElse(0.0);
    return variance;
  }

  /**
   * Returns total component inventories in system component order.
   *
   * @param system thermodynamic system
   * @return component mole inventory
   */
  private static double[] componentMoles(SystemInterface system) {
    int componentCount = system.getPhase(0).getNumberOfComponents();
    double[] result = new double[componentCount];
    for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
      result[componentIndex] = system.getComponent(componentIndex).getNumberOfmoles();
    }
    return result;
  }

  /**
   * Creates an initialized separator with a repeatable inlet-rate disturbance.
   *
   * @param name separator name
   * @return initialized dynamic separator
   */
  private static Separator createRefinementSeparator(String name) {
    SystemInterface fluid = new SystemPrEos(303.15, 12.0);
    fluid.addComponent("methane", 80.0);
    fluid.addComponent("n-heptane", 20.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream(name + " feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();
    Separator separator = new Separator(name, feed);
    separator.setInternalDiameter(1.0);
    separator.setSeparatorLength(3.0);
    separator.setLiquidLevel(0.5);
    separator.run();
    separator.setCalculateSteadyState(false);
    separator.initializeTransientCalculation();
    feed.setFlowRate(140.0, "kg/hr");
    feed.run();
    return separator;
  }

  /**
   * Runs the disturbed separator to a common endpoint.
   *
   * @param dt timestep in seconds
   * @return pressure, temperature and liquid level at eight seconds
   */
  private static double[] runRefinedTrajectory(double dt) {
    Separator separator = createRefinementSeparator("refinement " + dt);
    int steps = (int) Math.round(8.0 / dt);
    for (int step = 0; step < steps; step++) {
      separator.runTransient(dt, UUID.randomUUID());
      assertRelativeClosure(separator.getThermoSystem().getVolume("m3"), separator.gasVolume + separator.liquidVolume,
          1.0e-3, "refined accepted volume");
    }
    return new double[] {separator.getThermoSystem().getPressure(), separator.getThermoSystem().getTemperature(),
        separator.getLiquidLevel()};
  }

  /**
   * Computes a scale-normalized Euclidean state distance.
   *
   * @param left first state
   * @param right second state
   * @return normalized distance
   */
  private static double normalizedDistance(double[] left, double[] right) {
    double squared = 0.0;
    for (int index = 0; index < left.length; index++) {
      double scale = Math.max(Math.max(Math.abs(left[index]), Math.abs(right[index])), 1.0);
      double difference = (left[index] - right[index]) / scale;
      squared += difference * difference;
    }
    return Math.sqrt(squared);
  }

  /**
   * Asserts the caller-owned extensive-property acceptance gate.
   *
   * @param actual returned value
   * @param specified requested value
   * @param tolerance relative tolerance
   * @param label assertion label
   */
  private static void assertRelativeClosure(double actual, double specified, double tolerance, String label) {
    double residual = Math.abs(actual - specified) / Math.max(Math.abs(specified), 1.0);
    assertTrue(Double.isFinite(residual) && residual <= tolerance,
        label + " residual " + residual + " exceeds " + tolerance);
  }

  /** Deliberately unqualified subclass used to verify fail-closed snapshot coverage. */
  private static final class UnsupportedSeparator extends Separator {
    private static final long serialVersionUID = 1000L;

    /**
     * Creates the test separator.
     *
     * @param name separator name
     * @param inlet inlet stream
     */
    private UnsupportedSeparator(String name, Stream inlet) {
      super(name, inlet);
    }
  }
}
