package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsolver.AdvectionScheme;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for OnePhasePipeLine transient compositional tracking.
 *
 * <p>
 * These tests verify the integration of compositional tracking with the process simulation
 * framework, including advection scheme selection and gas switching scenarios.
 * </p>
 *
 * @author ESOL
 */
public class OnePhasePipeLineCompositionalTest {
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
  @DisplayName("OnePhasePipeLine steady-state should update outlet stream")
  void testSteadyStateRun() {
    Stream inlet = new Stream("inlet", naturalGas);
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();

    OnePhasePipeLine pipe = new OnePhasePipeLine("TestPipe", inlet);
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(10);
    pipe.setPipeDiameters(new double[] {0.1, 0.1});
    pipe.setLegPositions(new double[] {0.0, 100.0});
    pipe.setHeightProfile(new double[] {0.0, 0.0});
    pipe.setPipeWallRoughness(new double[] {1e-5, 1e-5});
    pipe.setOuterTemperatures(new double[] {280.0, 280.0});

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
    pipe.setPipeDiameters(new double[] {0.1, 0.1});
    pipe.setLegPositions(new double[] {0.0, 100.0});
    pipe.setHeightProfile(new double[] {0.0, 0.0});
    pipe.setPipeWallRoughness(new double[] {1e-5, 1e-5});
    pipe.setOuterTemperatures(new double[] {280.0, 280.0});

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
    pipe.setPipeDiameters(new double[] {0.1, 0.1});
    pipe.setLegPositions(new double[] {0.0, 100.0});
    pipe.setHeightProfile(new double[] {0.0, 0.0});
    pipe.setPipeWallRoughness(new double[] {1e-5, 1e-5});
    pipe.setOuterTemperatures(new double[] {280.0, 280.0});
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
    pipe.setPipeDiameters(new double[] {0.1, 0.1});
    pipe.setLegPositions(new double[] {0.0, 100.0});
    pipe.setHeightProfile(new double[] {0.0, 0.0});
    pipe.setPipeWallRoughness(new double[] {1e-5, 1e-5});
    pipe.setOuterTemperatures(new double[] {280.0, 280.0});

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
      assertTrue(methaneProfile[i] >= 0 && methaneProfile[i] <= 1,
          "Methane mass fraction should be in [0,1]");
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
    pipe.setPipeDiameters(new double[] {0.1, 0.1});
    pipe.setLegPositions(new double[] {0.0, 100.0});
    pipe.setHeightProfile(new double[] {0.0, 0.0});
    pipe.setPipeWallRoughness(new double[] {1e-5, 1e-5});
    pipe.setOuterTemperatures(new double[] {280.0, 280.0});

    pipe.run();

    // Get outlet composition
    double methaneMassFrac = pipe.getOutletMassFraction("methane");
    double methaneMoleFrac = pipe.getOutletMoleFraction("methane");

    assertTrue(methaneMassFrac > 0 && methaneMassFrac <= 1,
        "Methane mass fraction should be in (0,1]");
    assertTrue(methaneMoleFrac > 0 && methaneMoleFrac <= 1,
        "Methane mole fraction should be in (0,1]");

    // For natural gas, methane should be dominant
    assertTrue(methaneMoleFrac > 0.5, "Methane should be dominant component");
  }

  @Test
  @DisplayName("Advection scheme properties should be accessible")
  void testAdvectionSchemeProperties() {
    System.out.println("=== Advection Scheme Selection for Gas Switching ===\n");

    System.out.println("Scheme                  | Order | Max CFL | Dispersion Reduction");
    System.out.println("------------------------|-------|---------|---------------------");

    for (AdvectionScheme scheme : AdvectionScheme.values()) {
      System.out.printf("%-23s | %5d | %7.1f | %dx%n", scheme.getDisplayName(), scheme.getOrder(),
          scheme.getMaxCFL(), Math.round(1.0 / scheme.getDispersionReductionFactor()));
    }

    System.out.println();
    System.out.println("RECOMMENDATION for gas switching:");
    System.out.println("  - TVD_VAN_LEER: Best balance of accuracy and stability");
    System.out.println("  - TVD_SUPERBEE: Sharpest fronts, use for critical tracking");
    System.out.println("  - FIRST_ORDER_UPWIND: Use only for coarse/quick estimates");
  }
}
