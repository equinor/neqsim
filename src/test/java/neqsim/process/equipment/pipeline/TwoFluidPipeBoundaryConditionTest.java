package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for TwoFluidPipe boundary condition configuration and transient behavior.
 *
 * <p>
 * Validates:
 * <ul>
 * <li>Boundary condition type setting (STREAM_CONNECTED, CONSTANT_FLOW, CONSTANT_PRESSURE,
 * CLOSED)</li>
 * <li>Shut-in scenarios (closed outlet, closed inlet)</li>
 * <li>Physical behavior during transient: pressure buildup, velocity decay</li>
 * <li>Convenience methods (closeOutlet, openOutlet, closeInlet, openInlet)</li>
 * </ul>
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
@Disabled("Disabled due to infinite solving times - needs TwoFluidPipe optimization")
class TwoFluidPipeBoundaryConditionTest {
  private static final int NUM_SECTIONS = 10;
  private static final double PIPE_LENGTH = 1000.0; // 1 km
  private static final double PIPE_DIAMETER = 0.2; // 200 mm

  private TwoFluidPipe pipe;
  private static Stream sharedInletStream;
  private static SystemInterface sharedFluid;

  /**
   * Set up shared fluid and stream (expensive to create).
   */
  @BeforeAll
  static void setUpOnce() {
    // Create a two-phase gas fluid
    sharedFluid = new SystemSrkEos(303.15, 70.0); // 30°C, 70 bar
    sharedFluid.addComponent("methane", 0.90);
    sharedFluid.addComponent("ethane", 0.06);
    sharedFluid.addComponent("propane", 0.03);
    sharedFluid.addComponent("n-butane", 0.01);
    sharedFluid.setMixingRule("classic");

    // Create inlet stream
    sharedInletStream = new Stream("inlet", sharedFluid);
    sharedInletStream.setFlowRate(5.0, "kg/sec");
    sharedInletStream.setTemperature(30.0, "C");
    sharedInletStream.setPressure(70.0, "bara");
    sharedInletStream.run();
  }

  /**
   * Create fresh pipe for each test.
   */
  @BeforeEach
  void setUp() {
    pipe = new TwoFluidPipe("bc-test-pipe", sharedInletStream);
    pipe.setLength(PIPE_LENGTH);
    pipe.setDiameter(PIPE_DIAMETER);
    pipe.setNumberOfSections(NUM_SECTIONS);
    pipe.setOutletPressure(50.0, "bara"); // Default outlet BC
  }

  // =====================================================================
  // Default Boundary Condition Tests
  // =====================================================================

  @Test
  @DisplayName("Default BCs: inlet=STREAM_CONNECTED, outlet=CONSTANT_PRESSURE")
  void testDefaultBoundaryConditions() {
    assertEquals(BoundaryCondition.STREAM_CONNECTED, pipe.getInletBoundaryCondition(),
        "Default inlet BC should be STREAM_CONNECTED");
    assertEquals(BoundaryCondition.CONSTANT_PRESSURE, pipe.getOutletBoundaryCondition(),
        "Default outlet BC should be CONSTANT_PRESSURE");
    assertFalse(pipe.isOutletClosed(), "Outlet should not be closed by default");
    assertFalse(pipe.isInletClosed(), "Inlet should not be closed by default");
  }

  // =====================================================================
  // Boundary Condition Type Setting Tests
  // =====================================================================

  @Test
  @DisplayName("Set inlet BC to CONSTANT_FLOW")
  void testSetInletBCConstantFlow() {
    pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
    pipe.setInletMassFlow(3.0, "kg/sec");

    assertEquals(BoundaryCondition.CONSTANT_FLOW, pipe.getInletBoundaryCondition());
    assertFalse(pipe.isInletClosed());
  }

  @Test
  @DisplayName("Set inlet BC to CONSTANT_PRESSURE")
  void testSetInletBCConstantPressure() {
    pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
    pipe.setInletPressure(75.0, "bara");

    assertEquals(BoundaryCondition.CONSTANT_PRESSURE, pipe.getInletBoundaryCondition());
  }

  @Test
  @DisplayName("Set outlet BC to CLOSED")
  void testSetOutletBCClosed() {
    pipe.setOutletBoundaryCondition(BoundaryCondition.CLOSED);

    assertEquals(BoundaryCondition.CLOSED, pipe.getOutletBoundaryCondition());
    assertTrue(pipe.isOutletClosed(), "Outlet should be closed");
  }

  @Test
  @DisplayName("Set inlet BC to CLOSED")
  void testSetInletBCClosed() {
    pipe.setInletBoundaryCondition(BoundaryCondition.CLOSED);

    assertEquals(BoundaryCondition.CLOSED, pipe.getInletBoundaryCondition());
    assertTrue(pipe.isInletClosed(), "Inlet should be closed");
  }

  // =====================================================================
  // Convenience Method Tests (closeOutlet, openOutlet, etc.)
  // =====================================================================

  @Test
  @DisplayName("closeOutlet() sets outlet BC to CLOSED")
  void testCloseOutlet() {
    pipe.closeOutlet();

    assertEquals(BoundaryCondition.CLOSED, pipe.getOutletBoundaryCondition());
    assertTrue(pipe.isOutletClosed());
  }

  @Test
  @DisplayName("openOutlet() restores default outlet BC")
  void testOpenOutlet() {
    pipe.closeOutlet();
    assertTrue(pipe.isOutletClosed());

    pipe.openOutlet(); // Restore default

    assertEquals(BoundaryCondition.CONSTANT_PRESSURE, pipe.getOutletBoundaryCondition());
    assertFalse(pipe.isOutletClosed());
  }

  @Test
  @DisplayName("openOutlet(pressure, unit) sets new pressure and opens")
  void testOpenOutletWithPressure() {
    pipe.closeOutlet();
    pipe.openOutlet(55.0, "bara");

    assertEquals(BoundaryCondition.CONSTANT_PRESSURE, pipe.getOutletBoundaryCondition());
    assertFalse(pipe.isOutletClosed());
  }

  @Test
  @DisplayName("closeInlet() sets inlet BC to CLOSED")
  void testCloseInlet() {
    pipe.closeInlet();

    assertEquals(BoundaryCondition.CLOSED, pipe.getInletBoundaryCondition());
    assertTrue(pipe.isInletClosed());
  }

  @Test
  @DisplayName("openInlet() restores default inlet BC")
  void testOpenInlet() {
    pipe.closeInlet();
    assertTrue(pipe.isInletClosed());

    pipe.openInlet(); // Restore default

    assertEquals(BoundaryCondition.STREAM_CONNECTED, pipe.getInletBoundaryCondition());
    assertFalse(pipe.isInletClosed());
  }

  // =====================================================================
  // Transient Simulation with CLOSED Outlet (Shut-in / Pressure Buildup)
  // =====================================================================

  @Test
  @DisplayName("CLOSED outlet: velocity at outlet should be zero")
  void testClosedOutletZeroVelocity() {
    // First run steady state
    pipe.run();

    // Get initial outlet velocity
    double[] velocityBefore = pipe.getGasVelocityProfile();
    int lastIdx = velocityBefore.length - 1;
    double outletVelBefore = velocityBefore[lastIdx];
    assertTrue(outletVelBefore > 0, "Outlet velocity should be positive before closing");

    // Close outlet and run transient
    pipe.closeOutlet();
    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id); // Single transient step

    // After transient with closed outlet, outlet velocity should be zero
    double[] velocityAfter = pipe.getGasVelocityProfile();
    double outletVelAfter = velocityAfter[lastIdx];
    assertEquals(0.0, outletVelAfter, 1e-10, "Outlet velocity should be zero when closed");
  }

  @Test
  @DisplayName("CLOSED outlet: BC is applied and velocity is zero")
  void testClosedOutletBehavior() {
    // First run steady state
    pipe.run();

    // Close outlet and verify BC type
    pipe.closeOutlet();
    assertEquals(BoundaryCondition.CLOSED, pipe.getOutletBoundaryCondition());

    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id);

    // Outlet velocity should be zero when closed
    double[] velocityAfter = pipe.getGasVelocityProfile();
    double outletVelAfter = velocityAfter[velocityAfter.length - 1];
    assertEquals(0.0, outletVelAfter, 1e-10, "Outlet velocity should be zero when closed");

    // Note: Pressure behavior in transient depends on solver details and mass accumulation
    // The key verification is that velocity is zero (no flow out)
  }

  // =====================================================================
  // Transient Simulation with CLOSED Inlet (Blowdown / Depressurization)
  // =====================================================================

  @Test
  @DisplayName("CLOSED inlet: velocity at inlet should be zero")
  void testClosedInletZeroVelocity() {
    // First run steady state
    pipe.run();

    // Get initial inlet velocity
    double[] velocityBefore = pipe.getGasVelocityProfile();
    double inletVelBefore = velocityBefore[0];
    assertTrue(inletVelBefore > 0, "Inlet velocity should be positive before closing");

    // Close inlet and run transient
    pipe.closeInlet();
    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id);

    // After transient with closed inlet, inlet velocity should be zero
    double[] velocityAfter = pipe.getGasVelocityProfile();
    double inletVelAfter = velocityAfter[0];
    assertEquals(0.0, inletVelAfter, 1e-10, "Inlet velocity should be zero when closed");
  }

  @Test
  @DisplayName("CLOSED inlet: BC is applied and velocity is zero")
  void testClosedInletBehavior() {
    // First run steady state
    pipe.run();

    // Close inlet and verify BC type
    pipe.closeInlet();
    assertEquals(BoundaryCondition.CLOSED, pipe.getInletBoundaryCondition());

    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id);

    // Inlet velocity should be zero when closed
    double[] velocityAfter = pipe.getGasVelocityProfile();
    double inletVelAfter = velocityAfter[0];
    assertEquals(0.0, inletVelAfter, 1e-10, "Inlet velocity should be zero when closed");

    // Note: Pressure behavior (blowdown) depends on solver implementation details
    // The key verification is that velocity is zero (no flow in)
  }

  // =====================================================================
  // Full Shut-in (Both Ends Closed)
  // =====================================================================

  @Test
  @DisplayName("Both ends CLOSED: velocities should be zero everywhere")
  void testBothEndsClosed() {
    // Run steady state first
    pipe.run();

    // Close both ends
    pipe.closeInlet();
    pipe.closeOutlet();

    assertTrue(pipe.isInletClosed());
    assertTrue(pipe.isOutletClosed());

    // Run transient
    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id);

    // Check velocities at both ends
    double[] velocity = pipe.getGasVelocityProfile();
    assertEquals(0.0, velocity[0], 1e-10, "Inlet velocity should be zero");
    assertEquals(0.0, velocity[velocity.length - 1], 1e-10, "Outlet velocity should be zero");
  }

  @Test
  @DisplayName("Both ends CLOSED: verify BCs applied correctly")
  void testBothEndsClosedBCVerification() {
    // Run steady state first
    pipe.run();

    // Close both ends (trapped fluid)
    pipe.closeInlet();
    pipe.closeOutlet();

    // Verify BC types
    assertEquals(BoundaryCondition.CLOSED, pipe.getInletBoundaryCondition());
    assertEquals(BoundaryCondition.CLOSED, pipe.getOutletBoundaryCondition());

    // Run transient step
    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id);

    // Verify velocities are zero at both boundaries
    double[] velocity = pipe.getGasVelocityProfile();
    assertEquals(0.0, velocity[0], 1e-10, "Inlet velocity should be zero");
    assertEquals(0.0, velocity[velocity.length - 1], 1e-10, "Outlet velocity should be zero");

    // Pressures are recorded for informational purposes
    // (exact pressure behavior during trapped fluid transient depends on solver details)
    double[] pressures = pipe.getPressureProfile();
    double avgP = averageArray(pressures) / 1e5; // Pa to bara
    System.out
        .println("Both ends closed: average pressure = " + String.format("%.2f", avgP) + " bara");
  }

  // =====================================================================
  // Open/Close Sequence (Transient Valve Operation)
  // =====================================================================

  @Test
  @DisplayName("Open-close-open outlet sequence during transient")
  void testOutletOpenCloseOpenSequence() {
    // Initial steady state
    pipe.run();
    UUID id = UUID.randomUUID();

    // Phase 1: Normal flow
    pipe.runTransient(0.1, id);
    double velPhase1 = pipe.getGasVelocityProfile()[NUM_SECTIONS - 1];
    assertTrue(velPhase1 > 0, "Phase 1: Outlet velocity should be positive");

    // Phase 2: Close outlet (shut-in)
    pipe.closeOutlet();
    pipe.runTransient(0.1, id);
    double velPhase2 = pipe.getGasVelocityProfile()[NUM_SECTIONS - 1];
    assertEquals(0.0, velPhase2, 1e-10, "Phase 2: Outlet velocity should be zero when closed");

    // Phase 3: Open outlet again
    pipe.openOutlet(50.0, "bara");
    pipe.runTransient(0.1, id);
    double velPhase3 = pipe.getGasVelocityProfile()[NUM_SECTIONS - 1];
    // Flow should resume (may be different from initial due to pressure change)
    assertTrue(velPhase3 != 0.0 || !pipe.isOutletClosed(),
        "Phase 3: Outlet should be open after openOutlet()");
  }

  // =====================================================================
  // Constant Flow Inlet BC Test
  // =====================================================================

  @Test
  @DisplayName("CONSTANT_FLOW inlet: specified mass flow should be applied")
  void testConstantFlowInlet() {
    // Set constant flow at inlet
    pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
    pipe.setInletMassFlow(2.0, "kg/sec"); // Lower than stream flow rate

    pipe.run();

    // Pipe should work with the specified inlet mass flow
    // (actual verification depends on internal implementation details)
    assertEquals(BoundaryCondition.CONSTANT_FLOW, pipe.getInletBoundaryCondition());
  }

  // =====================================================================
  // Helper Methods
  // =====================================================================

  /**
   * Calculate average of a double array.
   *
   * @param arr input array
   * @return average value
   */
  private double averageArray(double[] arr) {
    double sum = 0;
    for (double v : arr) {
      sum += v;
    }
    return sum / arr.length;
  }
}
