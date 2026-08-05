package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsolver.AdvectionScheme;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseSpeciesConservationReport;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.OnePhaseSpeciesConservationHistory;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for OnePhasePipeLine transient compositional tracking.
 *
 * <p>
 * These tests verify the integration of compositional tracking with the process simulation framework, including
 * advection scheme selection and gas switching scenarios.
 * </p>
 *
 * @author ESOL
 */
public class OnePhasePipeLineCompositionalTest {
  private static final Logger logger = LogManager.getLogger(OnePhasePipeLineCompositionalTest.class);

  private SystemInterface naturalGas;
  private SystemInterface nitrogen;

  @BeforeEach
  void setUp() {
    // Create natural gas (methane-rich)
    naturalGas = new SystemSrkEos(280.0, 50.0);
    naturalGas.addComponent("methane", 0.90);
    naturalGas.addComponent("nitrogen", 0.10);
    naturalGas.createDatabase(true);
    naturalGas.setMixingRule("classic");
    naturalGas.init(0);
    naturalGas.init(1);

    // Create nitrogen-rich gas
    nitrogen = new SystemSrkEos(280.0, 50.0);
    nitrogen.addComponent("methane", 0.10);
    nitrogen.addComponent("nitrogen", 0.90);
    nitrogen.createDatabase(true);
    nitrogen.setMixingRule("classic");
    nitrogen.init(0);
    nitrogen.init(1);
  }

  @Test
  @DisplayName("OnePhasePipeLine should support advection scheme selection")
  void testAdvectionSchemeSelection() {
    Stream inlet = new Stream("inlet", naturalGas);
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = new OnePhasePipeLine("TestPipe", inlet);

    // Default should be first-order upwind
    assertEquals(AdvectionScheme.FIRST_ORDER_UPWIND, pipe.getAdvectionScheme());

    // Should be able to set different schemes
    pipe.setAdvectionScheme(AdvectionScheme.TVD_VAN_LEER);
    assertEquals(AdvectionScheme.TVD_VAN_LEER, pipe.getAdvectionScheme());

    pipe.setAdvectionScheme(AdvectionScheme.TVD_SUPERBEE);
    assertEquals(AdvectionScheme.TVD_SUPERBEE, pipe.getAdvectionScheme());
  }

  @Test
  @DisplayName("OnePhasePipeLine should support compositional tracking mode")
  void testCompositionalTrackingMode() {
    Stream inlet = new Stream("inlet", naturalGas);
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = new OnePhasePipeLine("TestPipe", inlet);

    // Default should be disabled
    assertEquals(false, pipe.isCompositionalTracking());

    // Should be able to enable
    pipe.setCompositionalTracking(true);
    assertTrue(pipe.isCompositionalTracking());
  }

  @Test
  @DisplayName("OnePhasePipeLine should expose conservative diagnostics and time-aligned outlet history")
  void testConservativeCompositionalTrackingApi() {
    Stream inlet = new Stream("inlet", naturalGas);
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = createPipe("ConservativePipe", inlet, 10, 100.0, 0.1);
    pipe.setConservativeCompositionalTracking(true);
    pipe.setStoreSpeciesConservationHistory(true);
    pipe.setFailOnNonConvergence(true);
    pipe.setInternalTimeStep(1.0);

    assertTrue(pipe.isConservativeCompositionalTracking());
    assertTrue(pipe.isSpeciesConservationHistoryStorageEnabled());
    assertTrue(pipe.isFailOnNonConvergence());
    assertFalse(pipe.isCompositionalTracking());

    UUID id = UUID.randomUUID();
    pipe.run(id);
    SystemInterface changedGas = nitrogen.clone();
    changedGas.setTotalFlowRate(1.0, "kg/sec");
    inlet.setThermoSystem(changedGas);
    inlet.run(id);
    pipe.runTransient(2.0, id);

    OnePhaseSpeciesConservationReport report = pipe.getSpeciesConservationReport();
    OnePhaseSpeciesConservationHistory history = pipe.getSpeciesConservationHistory();
    assertTrue(report.isConverged(), report.getMessage());
    assertTrue(pipe.getConvergenceReport().isConverged(), pipe.getConvergenceReport().getMessage());
    assertTrue(report.getMaximumRelativeInventoryResidual() <= 1.0e-8, report.getMessage());
    assertEquals(2, history.size());
    assertArrayEquals(new double[] { 1.0, 2.0 }, history.getElapsedTimeSeconds(), 0.0);

    double[] nitrogenProfile = pipe.getConservativeMassFractionProfile("nitrogen");
    double[] nitrogenOutletHistory = pipe.getConservativeOutletMassFractionHistory("nitrogen");
    assertEquals(2, nitrogenOutletHistory.length);
    assertEquals(nitrogenProfile[nitrogenProfile.length - 1], pipe.getConservativeOutletMassFraction("nitrogen"), 0.0);
    assertEquals(nitrogenOutletHistory[nitrogenOutletHistory.length - 1],
        pipe.getConservativeOutletMassFraction("nitrogen"), 0.0);
  }

  @Test
  @DisplayName("Conservative OnePhasePipeLine should reject phase appearance explicitly")
  void testConservativeModeRejectsPhaseAppearance() {
    Stream inlet = new Stream("inlet", naturalGas);
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = createPipe("ConservativePipe", inlet, 10, 100.0, 0.1);
    pipe.setConservativeCompositionalTracking(true);
    pipe.setFailOnNonConvergence(true);
    UUID id = UUID.randomUUID();
    pipe.run(id);

    SystemInterface twoPhaseFluid = new SystemSrkEos(260.0, 20.0);
    twoPhaseFluid.addComponent("methane", 0.50);
    twoPhaseFluid.addComponent("n-heptane", 0.50);
    twoPhaseFluid.createDatabase(true);
    twoPhaseFluid.setMixingRule("classic");
    twoPhaseFluid.setTotalFlowRate(1.0, "kg/sec");
    new ThermodynamicOperations(twoPhaseFluid).TPflash();
    assertTrue(twoPhaseFluid.getNumberOfPhases() > 1, "The rejection test requires a two-phase inlet state.");
    inlet.setThermoSystem(twoPhaseFluid);
    inlet.run(id);

    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> pipe.runTransient(1.0, id));
    assertTrue(exception.getMessage().contains("does not support phase appearance"));
  }

  @Test
  @Tag("slow")
  @DisplayName("OnePhasePipeLine should reproduce the validated 3 km finite species pulse")
  void testThreeKilometreConservativePulse() {
    SystemInterface baselineGas = createPulseGas(0.95, 0.05);
    SystemInterface pulseGas = createPulseGas(0.80, 0.20);
    Stream inlet = new Stream("Kristin inlet", baselineGas);
    inlet.setFlowRate(50.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = createPipe("Export pipe to Karsto", inlet, 12, 3000.0, 0.5);
    pipe.setConservativeCompositionalTracking(true);
    pipe.setStoreSpeciesConservationHistory(true);
    pipe.setFailOnNonConvergence(true);
    pipe.setInternalTimeStep(60.0);

    UUID id = UUID.randomUUID();
    pipe.run(id);
    double baselineOutlet = pipe.getOutletMassFraction("nitrogen");

    inlet.setThermoSystem(pulseGas);
    inlet.run(id);
    pipe.runTransient(1800.0, id);
    OnePhaseSpeciesConservationHistory pulseHistory = pipe.getSpeciesConservationHistory();
    double[] pulseOutlet = pipe.getConservativeOutletMassFractionHistory("nitrogen");
    assertEquals(30, pulseHistory.size());
    assertEquals(1800.0, pulseHistory.getElapsedTimeSeconds()[pulseHistory.size() - 1], 0.0);

    inlet.setThermoSystem(baselineGas.clone());
    inlet.run(id);
    pipe.runTransient(3600.0, id);
    OnePhaseSpeciesConservationHistory recoveryHistory = pipe.getSpeciesConservationHistory();
    double recoveredOutlet = pipe.getConservativeOutletMassFraction("nitrogen");
    assertEquals(60, recoveryHistory.size());
    assertEquals(3600.0, recoveryHistory.getElapsedTimeSeconds()[recoveryHistory.size() - 1], 0.0);

    double pulseInlet = pulseHistory.getReport(0).getInletBoundaryMassKg()[1]
        / sum(pulseHistory.getReport(0).getInletBoundaryMassKg());
    double eventAmplitude = pulseInlet - baselineOutlet;
    assertTrue(maximum(pulseOutlet) > baselineOutlet + 0.70 * eventAmplitude,
        "The finite inlet event should break through at the high-level pipeline outlet.");
    assertEquals(baselineOutlet, recoveredOutlet, 2.0e-3 * eventAmplitude,
        "The outlet should recover after the pulse is purged.");

    assertConservativeHistory(pulseHistory);
    assertConservativeHistory(recoveryHistory);
  }

  @Test
  @DisplayName("OnePhasePipeLine steady-state should update outlet stream")
  void testSteadyStateRun() {
    Stream inlet = new Stream("inlet", naturalGas);
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = new OnePhasePipeLine("TestPipe", inlet);
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(10);
    pipe.setPipeDiameters(new double[] { 0.1, 0.1 });
    pipe.setLegPositions(new double[] { 0.0, 100.0 });
    pipe.setHeightProfile(new double[] { 0.0, 0.0 });
    pipe.setPipeWallRoughness(new double[] { 1e-5, 1e-5 });
    pipe.setOuterTemperatures(new double[] { 280.0, 280.0 });

    pipe.run();

    // Outlet stream should be created and have properties
    assertNotNull(pipe.getOutletStream());
    assertTrue(pipe.getOutletStream().getPressure() > 0);
    assertTrue(pipe.getOutletStream().getTemperature() > 0);
  }

  @Test
  @DisplayName("OnePhasePipeLine should track simulation time during transient")
  void testTransientSimulationTime() {
    Stream inlet = new Stream("inlet", naturalGas);
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = new OnePhasePipeLine("TestPipe", inlet);
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(10);
    pipe.setPipeDiameters(new double[] { 0.1, 0.1 });
    pipe.setLegPositions(new double[] { 0.0, 100.0 });
    pipe.setHeightProfile(new double[] { 0.0, 0.0 });
    pipe.setPipeWallRoughness(new double[] { 1e-5, 1e-5 });
    pipe.setOuterTemperatures(new double[] { 280.0, 280.0 });

    // Initial steady state
    UUID id = UUID.randomUUID();
    pipe.run(id);
    assertEquals(0.0, pipe.getSimulationTime(), 1e-10);

    // Run transient steps
    pipe.runTransient(1.0, id);
    assertEquals(1.0, pipe.getSimulationTime(), 0.1);

    pipe.runTransient(2.0, id);
    assertEquals(3.0, pipe.getSimulationTime(), 0.1);

    // Reset should work
    pipe.resetSimulationTime();
    assertEquals(0.0, pipe.getSimulationTime(), 1e-10);
  }

  @Test
  @DisplayName("OnePhasePipeLine should work with ProcessSystem")
  void testWithProcessSystem() {
    Stream inlet = new Stream("inlet", naturalGas);
    inlet.setFlowRate(1.0, "kg/sec");

    OnePhasePipeLine pipe = new OnePhasePipeLine("TestPipe", inlet);
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(10);
    pipe.setPipeDiameters(new double[] { 0.1, 0.1 });
    pipe.setLegPositions(new double[] { 0.0, 100.0 });
    pipe.setHeightProfile(new double[] { 0.0, 0.0 });
    pipe.setPipeWallRoughness(new double[] { 1e-5, 1e-5 });
    pipe.setOuterTemperatures(new double[] { 280.0, 280.0 });
    pipe.setAdvectionScheme(AdvectionScheme.TVD_VAN_LEER);
    pipe.setCompositionalTracking(true);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);

    // Run initial steady state
    process.run();

    assertNotNull(pipe.getOutletStream());
    assertTrue(pipe.getOutletStream().getPressure() > 0);

    // Run transient loop using direct pipe.runTransient (avoiding ProcessSystem serialization)
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 3; i++) {
      inlet.run(id); // Update inlet
      pipe.runTransient(1.0, id); // Run pipe transient directly
    }

    // Should have advanced time
    assertTrue(pipe.getSimulationTime() > 0);
  }

  @Test
  @DisplayName("OnePhasePipeLine should provide composition profiles")
  void testCompositionProfiles() {
    Stream inlet = new Stream("inlet", naturalGas);
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = new OnePhasePipeLine("TestPipe", inlet);
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(10);
    pipe.setPipeDiameters(new double[] { 0.1, 0.1 });
    pipe.setLegPositions(new double[] { 0.0, 100.0 });
    pipe.setHeightProfile(new double[] { 0.0, 0.0 });
    pipe.setPipeWallRoughness(new double[] { 1e-5, 1e-5 });
    pipe.setOuterTemperatures(new double[] { 280.0, 280.0 });

    pipe.run();

    // Get profiles
    double[] methaneProfile = pipe.getCompositionProfile("methane");
    double[] pressureProfile = pipe.getPressureProfile("bara");
    double[] tempProfile = pipe.getTemperatureProfile("K");
    double[] velocityProfile = pipe.getVelocityProfile();

    // Profiles should have same length (system may add boundary nodes)
    int nNodes = methaneProfile.length;
    assertTrue(nNodes >= 10, "Should have at least 10 nodes");
    assertEquals(nNodes, pressureProfile.length);
    assertEquals(nNodes, tempProfile.length);
    assertEquals(nNodes, velocityProfile.length);

    // All values should be positive
    for (int i = 0; i < nNodes; i++) {
      assertTrue(methaneProfile[i] >= 0 && methaneProfile[i] <= 1, "Methane mass fraction should be in [0,1]");
      assertTrue(pressureProfile[i] > 0, "Pressure should be positive");
      assertTrue(tempProfile[i] > 0, "Temperature should be positive");
    }
  }

  @Test
  @DisplayName("OnePhasePipeLine should provide outlet composition accessors")
  void testOutletCompositionAccessors() {
    Stream inlet = new Stream("inlet", naturalGas);
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = new OnePhasePipeLine("TestPipe", inlet);
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(10);
    pipe.setPipeDiameters(new double[] { 0.1, 0.1 });
    pipe.setLegPositions(new double[] { 0.0, 100.0 });
    pipe.setHeightProfile(new double[] { 0.0, 0.0 });
    pipe.setPipeWallRoughness(new double[] { 1e-5, 1e-5 });
    pipe.setOuterTemperatures(new double[] { 280.0, 280.0 });

    pipe.run();

    // Get outlet composition
    double methaneMassFrac = pipe.getOutletMassFraction("methane");
    double methaneMoleFrac = pipe.getOutletMoleFraction("methane");

    assertTrue(methaneMassFrac > 0 && methaneMassFrac <= 1, "Methane mass fraction should be in (0,1]");
    assertTrue(methaneMoleFrac > 0 && methaneMoleFrac <= 1, "Methane mole fraction should be in (0,1]");

    // For natural gas, methane should be dominant
    assertTrue(methaneMoleFrac > 0.5, "Methane should be dominant component");
  }

  @Test
  @DisplayName("Advection scheme properties should be accessible")
  void testAdvectionSchemeProperties() {
    logger.info("=== Advection Scheme Selection for Gas Switching ===\n");

    logger.info("Scheme                  | Order | Max CFL | Dispersion Reduction");
    logger.info("------------------------|-------|---------|---------------------");

    for (AdvectionScheme scheme : AdvectionScheme.values()) {
      logger.printf(org.apache.logging.log4j.Level.INFO, "%-23s | %5d | %7.1f | %dx%n", scheme.getDisplayName(),
          scheme.getOrder(), scheme.getMaxCFL(), Math.round(1.0 / scheme.getDispersionReductionFactor()));
    }

    logger.info("RECOMMENDATION for gas switching:");
    logger.info("  - TVD_VAN_LEER: Best balance of accuracy and stability");
    logger.info("  - TVD_SUPERBEE: Sharpest fronts, use for critical tracking");
    logger.info("  - FIRST_ORDER_UPWIND: Use only for coarse/quick estimates");
  }

  private static OnePhasePipeLine createPipe(String name, Stream inlet, int nodes, double lengthMetres,
      double diameterMetres) {
    OnePhasePipeLine pipe = new OnePhasePipeLine(name, inlet);
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(nodes);
    pipe.setPipeDiameters(new double[] { diameterMetres, diameterMetres });
    pipe.setLegPositions(new double[] { 0.0, lengthMetres });
    pipe.setHeightProfile(new double[] { 0.0, 0.0 });
    pipe.setPipeWallRoughness(new double[] { 1.0e-5, 1.0e-5 });
    pipe.setOuterTemperatures(new double[] { 288.15, 288.15 });
    pipe.setWallHeatTransferCoefficients(new double[] { 0.0, 0.0 });
    pipe.setOuterHeatTransferCoefficients(new double[] { 0.0, 0.0 });
    return pipe;
  }

  private static SystemInterface createPulseGas(double methane, double nitrogenFraction) {
    SystemInterface gas = new SystemSrkEos(288.15, 70.0);
    gas.addComponent("methane", methane);
    gas.addComponent("nitrogen", nitrogenFraction);
    gas.createDatabase(true);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(3);
    gas.initPhysicalProperties();
    gas.setTotalFlowRate(50.0, "kg/sec");
    return gas;
  }

  private static void assertConservativeHistory(OnePhaseSpeciesConservationHistory history) {
    for (OnePhaseSpeciesConservationReport report : history.getReports()) {
      assertTrue(report.isConverged(), report.getMessage());
      assertTrue(report.getMaximumRelativeInventoryResidual() <= 1.0e-8, report.getMessage());
      assertTrue(report.getMinimumMassFraction() >= 0.0, report.getMessage());
      assertTrue(report.getMaximumMassFraction() <= 1.0, report.getMessage());
    }
  }

  private static double maximum(double[] values) {
    double maximum = Double.NEGATIVE_INFINITY;
    for (double value : values) {
      maximum = Math.max(maximum, value);
    }
    return maximum;
  }

  private static double sum(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    return sum;
  }
}
