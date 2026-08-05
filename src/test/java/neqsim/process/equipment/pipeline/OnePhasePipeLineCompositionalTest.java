package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

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
    assertFalse(pipe.isConservativeCompositionalTracking());
  }

  @Test
  @DisplayName("OnePhasePipeLine should expose validated conservative pulse diagnostics")
  void testValidatedConservativePulseDiagnostics() {
    SystemInterface baselineGas = createTransmissionGas(0.95, 0.05);
    SystemInterface pulseGas = createTransmissionGas(0.80, 0.20);
    Stream inlet = new Stream("conservative inlet", baselineGas);
    inlet.setFlowRate(50.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = createTransmissionPipe(inlet);
    pipe.setConservativeCompositionalTracking(true);
    pipe.setStoreSpeciesConservationHistory(true);
    pipe.setFailOnNonConvergence(true);

    UUID id = UUID.randomUUID();
    pipe.run(id);
    pipe.runConservativeTransient(new double[] { 0.0, 30.0, 60.0, 90.0 },
        new SystemInterface[] { pulseGas, pulseGas, baselineGas }, 1, id);

    OnePhaseSpeciesConservationHistory history = pipe.getSpeciesConservationHistory();
    assertEquals(3, history.size());
    assertArrayEquals(new double[] { 30.0, 60.0, 90.0 }, history.getElapsedTimeSeconds(), 0.0);
    for (OnePhaseSpeciesConservationReport report : history.getReports()) {
      assertTrue(report.isConverged(), report.getMessage());
      assertTrue(report.getMaximumRelativeInventoryResidual() <= 1.0e-8, report.getMessage());
      assertTrue(report.getMaximumThermodynamicMassFractionError() <= 1.0e-10, report.getMessage());
    }

    OnePhaseSpeciesConservationReport latest = pipe.getSpeciesConservationReport();
    assertTrue(latest.isConverged(), latest.getMessage());
    assertTrue(pipe.getConvergenceReport().isConverged(), pipe.getConvergenceReport().getMessage());
    assertArrayEquals(latest.getFinalInventoryKg(), history.getReport(2).getFinalInventoryKg(), 0.0);
    assertArrayEquals(latest.getMassFractionProfile()[1], pipe.getConservativeMassFractionProfile("nitrogen"), 0.0);
    assertTrue(history.toJson().contains("\"elapsedTimeSeconds\""));
  }

  @Test
  @DisplayName("Validated conservative tracking should reject phase appearance before advancing")
  void testValidatedConservativeTrackingRejectsPhaseAppearance() {
    SystemInterface baselineGas = createTransmissionGas(0.95, 0.05);
    Stream inlet = new Stream("phase-limit inlet", baselineGas);
    inlet.setFlowRate(50.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = createTransmissionPipe(inlet);
    pipe.setConservativeCompositionalTracking(true);
    pipe.setStoreSpeciesConservationHistory(true);
    pipe.setFailOnNonConvergence(true);
    pipe.run(UUID.randomUUID());

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> pipe.runConservativeTransient(new double[] { 0.0, 30.0 },
            new SystemInterface[] { createKnownGasOilFluid() }, 1, UUID.randomUUID()));

    assertTrue(exception.getMessage().contains("one gas phase only"));
    assertEquals(0.0, pipe.getSimulationTime(), 0.0, "Rejected phase appearance must not advance the pipeline clock.");
  }

  @Test
  @DisplayName("Validated conservative tracking should reject reversed inlet flow before advancing")
  void testValidatedConservativeTrackingRejectsReversedFlow() {
    SystemInterface baselineGas = createTransmissionGas(0.95, 0.05);
    Stream inlet = new Stream("flow-limit inlet", baselineGas);
    inlet.setFlowRate(50.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = createTransmissionPipe(inlet);
    pipe.setConservativeCompositionalTracking(true);
    pipe.setStoreSpeciesConservationHistory(true);
    pipe.setFailOnNonConvergence(true);
    pipe.run(UUID.randomUUID());

    SystemInterface reversedGas = createTransmissionGas(0.80, 0.20);
    reversedGas.setTotalFlowRate(-50.0, "kg/sec");
    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> pipe.runConservativeTransient(new double[] { 0.0, 30.0 }, new SystemInterface[] { reversedGas }, 1,
            UUID.randomUUID()));

    assertTrue(exception.getMessage().contains("strictly positive inlet mass flow only"));
    assertEquals(0.0, pipe.getSimulationTime(), 0.0, "Rejected reversed flow must not advance the pipeline clock.");
  }

  @Test
  @Tag("slow")
  @DisplayName("OnePhasePipeLine should conserve a 30-minute pulse and recover")
  void testValidatedThirtyMinutePulseBreakthroughAndRecovery() {
    SystemInterface baselineGas = createTransmissionGas(0.95, 0.05);
    SystemInterface pulseGas = createTransmissionGas(0.80, 0.20);
    Stream inlet = new Stream("30-minute pulse inlet", baselineGas);
    inlet.setFlowRate(50.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = createTransmissionPipe(inlet);
    pipe.setConservativeCompositionalTracking(true);
    pipe.setStoreSpeciesConservationHistory(true);
    pipe.setFailOnNonConvergence(true);

    UUID id = UUID.randomUUID();
    pipe.run(id);
    double baselineOutlet = pipe.getOutletMassFraction("nitrogen");
    pipe.runConservativeTransient(new double[] { 0.0, 1800.0, 5400.0 }, new SystemInterface[] { pulseGas, baselineGas },
        60, id);

    OnePhaseSpeciesConservationHistory history = pipe.getSpeciesConservationHistory();
    assertEquals(120, history.size());
    assertEquals(30.0, history.getElapsedTimeSeconds()[0], 0.0);
    assertEquals(1800.0, history.getElapsedTimeSeconds()[59], 0.0);
    assertEquals(5400.0, history.getElapsedTimeSeconds()[119], 0.0);

    int nitrogen = componentIndex(history.getReport(0), "nitrogen");
    double initialInventoryKg = history.getReport(0).getInitialInventoryKg()[nitrogen];
    double cumulativeInletKg = 0.0;
    double cumulativeOutletKg = 0.0;
    double pulseEndOutlet = Double.NaN;
    for (int step = 0; step < history.size(); step++) {
      OnePhaseSpeciesConservationReport report = history.getReport(step);
      assertTrue(report.isConverged(), report.getMessage());
      assertTrue(report.getMaximumRelativeInventoryResidual() <= 1.0e-8, report.getMessage());
      cumulativeInletKg += report.getInletBoundaryMassKg()[nitrogen];
      cumulativeOutletKg += report.getOutletBoundaryMassKg()[nitrogen];
      if (step == 59) {
        pulseEndOutlet = last(report.getMassFractionProfile()[nitrogen]);
      }
    }

    OnePhaseSpeciesConservationReport lastReport = history.getReport(history.size() - 1);
    double finalInventoryKg = lastReport.getFinalInventoryKg()[nitrogen];
    double cumulativeResidualKg = finalInventoryKg - initialInventoryKg - cumulativeInletKg + cumulativeOutletKg;
    double pulseInletMassFraction = history.getReport(0).getInletBoundaryMassKg()[nitrogen]
        / sum(history.getReport(0).getInletBoundaryMassKg());
    double eventAmplitude = pulseInletMassFraction - baselineOutlet;
    double recoveredOutlet = last(lastReport.getMassFractionProfile()[nitrogen]);

    assertTrue(pulseEndOutlet > baselineOutlet + 0.70 * eventAmplitude,
        "The 30-minute pulse must break through before the inlet returns to baseline.");
    assertEquals(baselineOutlet, recoveredOutlet, 2.0e-3 * eventAmplitude);
    assertEquals(0.0, cumulativeResidualKg, Math.max(Math.max(initialInventoryKg, cumulativeInletKg), 1.0) * 1.0e-7);
    logger.info(
        "Conservative OnePhasePipeLine pulse: baseline outlet={}, pulse-end outlet={}, recovered outlet={}, cumulative nitrogen residual={} kg",
        baselineOutlet, pulseEndOutlet, recoveredOutlet, cumulativeResidualKg);
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

  private static OnePhasePipeLine createTransmissionPipe(Stream inlet) {
    OnePhasePipeLine pipe = new OnePhasePipeLine("3 km conservative gas pipe", inlet);
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(12);
    pipe.setPipeDiameters(new double[] { 0.5, 0.5 });
    pipe.setLegPositions(new double[] { 0.0, 3000.0 });
    pipe.setHeightProfile(new double[] { 0.0, 0.0 });
    pipe.setPipeWallRoughness(new double[] { 1.0e-5, 1.0e-5 });
    pipe.setOuterTemperatures(new double[] { 288.15, 288.15 });
    return pipe;
  }

  private static SystemInterface createTransmissionGas(double methaneMoleFraction, double nitrogenMoleFraction) {
    SystemInterface gas = new SystemSrkEos(288.15, 70.0);
    gas.addComponent("methane", methaneMoleFraction);
    gas.addComponent("nitrogen", nitrogenMoleFraction);
    gas.createDatabase(true);
    gas.setMixingRule("classic");
    gas.setTotalFlowRate(50.0, "kg/sec");
    gas.init(0);
    gas.init(1);
    return gas;
  }

  private static SystemInterface createKnownGasOilFluid() {
    SystemInterface fluid = new SystemPrEos(424.0, 186.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("n-heptane", 30.0);
    fluid.setMixingRule("classic");
    ((EosMixingRulesInterface) fluid.getPhase(0).getMixingRule()).setBinaryInteractionParameter(0, 1, 0.05);
    ((EosMixingRulesInterface) fluid.getPhase(1).getMixingRule()).setBinaryInteractionParameter(0, 1, 0.05);
    fluid.setTotalFlowRate(50.0, "kg/sec");
    return fluid;
  }

  private static int componentIndex(OnePhaseSpeciesConservationReport report, String componentName) {
    String[] names = report.getComponentNames();
    for (int component = 0; component < names.length; component++) {
      if (names[component].equalsIgnoreCase(componentName)) {
        return component;
      }
    }
    throw new IllegalArgumentException("Missing component in conservative report: " + componentName);
  }

  private static double last(double[] values) {
    return values[values.length - 1];
  }

  private static double sum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
  }
}
