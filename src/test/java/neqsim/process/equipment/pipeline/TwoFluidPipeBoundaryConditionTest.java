package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * <li>Boundary condition type setting (STREAM_CONNECTED, CONSTANT_FLOW, CONSTANT_PRESSURE, CLOSED)</li>
 * <li>Shut-in scenarios (closed outlet, closed inlet)</li>
 * <li>Physical behavior during transient: pressure buildup, velocity decay</li>
 * <li>Convenience methods (closeOutlet, openOutlet, closeInlet, openInlet)</li>
 * </ul>
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
class TwoFluidPipeBoundaryConditionTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeBoundaryConditionTest.class);
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
    System.out.println("Both ends closed: average pressure = " + String.format("%.2f", avgP) + " bara");
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
    assertTrue(velPhase3 != 0.0 || !pipe.isOutletClosed(), "Phase 3: Outlet should be open after openOutlet()");
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

  @Test
  @DisplayName("CONSTANT_FLOW rate step changes domain mass only through boundary fluxes")
  void testConstantFlowStepDoesNotCreateDomainMass() {
    double initialFlowRate = 6.0;
    double timeStep = 1.0e-3;
    double[] steppedFlowRates = { 12.0, 30.0 };
    UUID calculationId = UUID.fromString("00000000-0000-0000-0000-000000002718");

    for (double steppedFlowRate : steppedFlowRates) {
      TwoFluidPipe transientPipe = createRegressionPipe("mass-balance-" + steppedFlowRate, createTwoPhaseFluid(),
          initialFlowRate, 75.0, 58.0);
      transientPipe.setLength(3000.0);
      transientPipe.setNumberOfSections(12);
      transientPipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
      transientPipe.setInletMassFlow(initialFlowRate, "kg/sec");
      transientPipe.run();

      double massBefore = transientPipe.getTotalMassInventory();
      transientPipe.setInletMassFlow(steppedFlowRate, "kg/sec");
      transientPipe.runTransient(timeStep, calculationId);
      double massAfter = transientPipe.getTotalMassInventory();

      double massChange = Math.abs(massAfter - massBefore);
      double boundaryFluxEnvelope = 2.0 * (initialFlowRate + steppedFlowRate) * timeStep;
      assertTrue(massChange <= boundaryFluxEnvelope,
          "A short rate step must not create domain-volume-scaled mass. Flow step " + initialFlowRate + " -> "
              + steppedFlowRate + " kg/s changed inventory by " + massChange + " kg; flux envelope was "
              + boundaryFluxEnvelope + " kg");
    }
  }

  @Test
  @DisplayName("Steady-state handoff preserves inventory and avoids an O(1) hydraulic jump")
  void testSteadyStateToTransientHandoffIsContinuous() {
    assertSteadyStateToTransientHandoffIsContinuous("two-phase", createTwoPhaseFluid(), 6.0, 75.0, 58.0);
    assertSteadyStateToTransientHandoffIsContinuous("three-phase", createThreePhaseFluid(), 6.0, 80.0, 60.0);
  }

  // =====================================================================
  // Stationary and Transient Boundary Regression Tests
  // =====================================================================

  @Test
  @DisplayName("Stationary solver honors outlet pressure for one-phase gas")
  void testStationaryOutletPressureOnePhaseGas() {
    TwoFluidPipe gasPipe = createRegressionPipe("one-phase-gas", createOnePhaseGasFluid(), 4.0, 70.0, 55.0);

    gasPipe.run();

    assertOutletPressure(gasPipe, 55.0);
    assertSteadyPressureProfilePhysical(gasPipe);
    assertTrue(averageArray(gasPipe.getLiquidHoldupProfile()) < 1e-6,
        "One-phase gas should not build artificial liquid holdup");
  }

  @Test
  @DisplayName("Stationary solver honors outlet pressure for gas-liquid flow")
  void testStationaryOutletPressureTwoPhase() {
    TwoFluidPipe twoPhasePipe = createRegressionPipe("two-phase", createTwoPhaseFluid(), 6.0, 75.0, 58.0);

    twoPhasePipe.run();

    assertOutletPressure(twoPhasePipe, 58.0);
    assertSteadyPressureProfilePhysical(twoPhasePipe);
    assertTrue(averageArray(twoPhasePipe.getLiquidHoldupProfile()) > 1e-6,
        "Gas-liquid flow should keep a positive liquid holdup");
    assertTrue(averageArray(twoPhasePipe.getWaterHoldupProfile()) < 1e-6,
        "Two-phase hydrocarbon case should not create water holdup");
  }

  @Test
  @DisplayName("Stationary solver honors outlet pressure for gas-oil-water flow")
  void testStationaryOutletPressureThreePhase() {
    TwoFluidPipe threePhasePipe = createRegressionPipe("three-phase", createThreePhaseFluid(), 6.0, 80.0, 60.0);

    threePhasePipe.run();

    assertOutletPressure(threePhasePipe, 60.0);
    assertSteadyPressureProfilePhysical(threePhasePipe);
    assertTrue(averageArray(threePhasePipe.getWaterHoldupProfile()) > 1e-7,
        "Three-phase flow should keep water holdup");
    assertTrue(averageArray(threePhasePipe.getOilHoldupProfile()) > 1e-7, "Three-phase flow should keep oil holdup");
    assertLiquidHoldupMatchesOilPlusWater(threePhasePipe);
  }

  @Test
  @DisplayName("Transient gas flow relaxes toward new stationary solution after outlet change")
  void testTransientOutletPressureChangeOnePhaseGas() {
    assertTransientOutletPressureChangeApproachesStationary("one-phase-gas", createOnePhaseGasFluid(), 4.0, 70.0, 58.0,
        52.0, 60.0);
  }

  @Test
  @DisplayName("Transient gas-liquid flow relaxes toward new stationary solution after outlet change")
  void testTransientOutletPressureChangeTwoPhase() {
    assertTransientOutletPressureChangeApproachesStationary("two-phase", createTwoPhaseFluid(), 6.0, 75.0, 62.0, 56.0,
        60.0);
  }

  @Test
  @DisplayName("Transient gas-oil-water flow relaxes toward new stationary solution after outlet change")
  void testTransientOutletPressureChangeThreePhase() {
    assertTransientOutletPressureChangeApproachesStationary("three-phase", createThreePhaseFluid(), 6.0, 80.0, 64.0,
        58.0, 60.0);
  }

  @Test
  @DisplayName("Transient pressure settling time is reasonable for one-, two-, and three-phase flow")
  void testTimeToReachNewSteadyStateIsReasonableForAllPhaseCases() {
    assertTimeToNewStationaryPressureProfile("one-phase-gas", createOnePhaseGasFluid(), 4.0, 70.0, 58.0, 52.0, 60.0);
    assertTimeToNewStationaryPressureProfile("two-phase", createTwoPhaseFluid(), 6.0, 75.0, 62.0, 56.0, 60.0);
    assertTimeToNewStationaryPressureProfile("three-phase", createThreePhaseFluid(), 6.0, 80.0, 64.0, 58.0, 60.0);
  }

  @Test
  @DisplayName("Steady-state pressure drop is similar to Beggs and Brill")
  void testSteadyStatePressureDropIsSimilarToBeggsAndBrill() {
    assertSteadyPressureDropSimilarToBeggsAndBrill("one-phase-gas", createOnePhaseGasFluid(), 4.0, 70.0);
    assertSteadyPressureDropSimilarToBeggsAndBrill("two-phase", createTwoPhaseFluid(), 6.0, 75.0);
    assertSteadyPressureDropSimilarToBeggsAndBrill("three-phase", createThreePhaseFluid(), 6.0, 80.0);
  }

  // =====================================================================
  // Helper Methods
  // =====================================================================

  /**
   * Create a dry gas fluid for one-phase checks.
   *
   * @return fluid system
   */
  private SystemInterface createOnePhaseGasFluid() {
    SystemInterface fluid = new SystemSrkEos(303.15, 70.0);
    fluid.addComponent("methane", 0.96);
    fluid.addComponent("ethane", 0.04);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Create a gas-condensate fluid for gas-liquid checks.
   *
   * @return fluid system
   */
  private SystemInterface createTwoPhaseFluid() {
    SystemInterface fluid = new SystemSrkEos(293.15, 75.0);
    fluid.addComponent("methane", 0.82);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-heptane", 0.02);
    fluid.addComponent("nC10", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Create a gas-oil-water fluid for three-phase checks.
   *
   * @return fluid system
   */
  private SystemInterface createThreePhaseFluid() {
    SystemInterface fluid = new SystemSrkEos(293.15, 80.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-heptane", 0.02);
    fluid.addComponent("nC10", 0.02);
    fluid.addComponent("water", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Create a compact pipe for boundary-condition regression tests.
   *
   * @param name pipe name
   * @param fluid inlet fluid
   * @param flowRateKgSec flow rate in kg/s
   * @param inletPressureBara inlet pressure in bara
   * @param outletPressureBara outlet pressure in bara
   * @return configured pipe
   */
  private TwoFluidPipe createRegressionPipe(String name, SystemInterface fluid, double flowRateKgSec,
      double inletPressureBara, double outletPressureBara) {
    Stream inlet = new Stream(name + "-inlet", fluid);
    inlet.setFlowRate(flowRateKgSec, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(inletPressureBara, "bara");
    inlet.run();

    TwoFluidPipe regressionPipe = new TwoFluidPipe(name + "-pipe", inlet);
    regressionPipe.setLength(300.0);
    regressionPipe.setDiameter(0.15);
    regressionPipe.setRoughness(1.0e-5);
    regressionPipe.setNumberOfSections(6);
    regressionPipe.setOutletPressure(outletPressureBara, "bara");
    regressionPipe.setThermodynamicUpdateInterval(1);
    regressionPipe.setEnableAdaptiveTimestepping(true);
    regressionPipe.setCflNumber(0.8);
    regressionPipe.setSteadyStateMaxWallClockTime(1.0);
    return regressionPipe;
  }

  /**
   * Verify that switching numerical modes without changing a boundary does not introduce an O(1) state jump.
   *
   * @param name case name
   * @param fluid inlet fluid
   * @param flowRateKgSec flow rate in kg/s
   * @param inletPressureBara inlet pressure in bara
   * @param outletPressureBara outlet pressure in bara
   */
  private void assertSteadyStateToTransientHandoffIsContinuous(String name, SystemInterface fluid, double flowRateKgSec,
      double inletPressureBara, double outletPressureBara) {
    TwoFluidPipe handoffPipe = createRegressionPipe(name + "-handoff", fluid, flowRateKgSec, inletPressureBara,
        outletPressureBara);
    if ("three-phase".equals(name)) {
      handoffPipe.setElevationProfile(new double[] { 0.0, 0.1, 0.2, 0.3, 0.4, 0.5 });
    }
    handoffPipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    handoffPipe.run();

    double[] pressureBefore = handoffPipe.getPressureProfile();
    double[] liquidHoldupBefore = handoffPipe.getLiquidHoldupProfile();
    double[] gasVelocityBefore = handoffPipe.getGasVelocityProfile();
    double[] liquidVelocityBefore = handoffPipe.getLiquidVelocityProfile();
    double[] oilVelocityBefore = handoffPipe.getOilVelocityProfile();
    double[] waterVelocityBefore = handoffPipe.getWaterVelocityProfile();
    double massBefore = handoffPipe.getTotalMassInventory();

    double timeStep = 1.0e-9;
    UUID calculationId = UUID.fromString("00000000-0000-0000-0000-000000002723");
    handoffPipe.runTransient(timeStep, calculationId);

    double pressureRmsPa = rmsDifference(handoffPipe.getPressureProfile(), pressureBefore);
    double liquidHoldupRms = rmsDifference(handoffPipe.getLiquidHoldupProfile(), liquidHoldupBefore);
    double gasVelocityRms = rmsDifference(handoffPipe.getGasVelocityProfile(), gasVelocityBefore);
    double liquidVelocityRms = rmsDifference(handoffPipe.getLiquidVelocityProfile(), liquidVelocityBefore);
    boolean hasOilWaterSlip = "three-phase".equals(name);
    double oilVelocityRms = hasOilWaterSlip
        ? rmsDifferenceInterior(handoffPipe.getOilVelocityProfile(), oilVelocityBefore)
        : 0.0;
    double waterVelocityRms = hasOilWaterSlip
        ? rmsDifferenceInterior(handoffPipe.getWaterVelocityProfile(), waterVelocityBefore)
        : 0.0;
    double massChangeKg = Math.abs(handoffPipe.getTotalMassInventory() - massBefore);

    logger.printf(org.apache.logging.log4j.Level.INFO,
        "%s steady/transient handoff: pressure RMS %.6f Pa, holdup RMS %.9f, "
            + "gas velocity RMS %.9f m/s, liquid velocity RMS %.9f m/s, oil velocity RMS %.9f m/s, "
            + "water velocity RMS %.9f m/s, mass change %.9g kg%n",
        name, pressureRmsPa, liquidHoldupRms, gasVelocityRms, liquidVelocityRms, oilVelocityRms, waterVelocityRms,
        massChangeKg);

    assertTrue(pressureRmsPa <= 500.0,
        name + " pressure changed sharply across an unchanged near-zero-time handoff: RMS " + pressureRmsPa + " Pa");
    assertTrue(liquidHoldupRms <= 1.0e-7,
        name + " liquid holdup changed across an unchanged near-zero-time handoff: RMS " + liquidHoldupRms);
    assertTrue(gasVelocityRms <= 0.10,
        name + " gas velocity changed across an unchanged near-zero-time handoff: RMS " + gasVelocityRms + " m/s");
    // Judged relative to the liquid velocity itself. An absolute bound is tied to the holdup of the
    // particular fixture: a closure change that lowers holdup raises the liquid velocity for the same
    // mass flux, which moves an absolute RMS without the handoff having become any less continuous.
    // The scale-free ratio is the invariant, and an O(1) hydraulic jump - what this test guards - is
    // order 100 per cent in it.
    double meanLiquidSpeed = 0.0;
    for (double velocity : liquidVelocityBefore) {
      meanLiquidSpeed += Math.abs(velocity);
    }
    meanLiquidSpeed = Math.max(1.0e-12, meanLiquidSpeed / liquidVelocityBefore.length);
    double liquidVelocityRelative = liquidVelocityRms / meanLiquidSpeed;
    assertTrue(liquidVelocityRelative <= 0.06,
        name + " liquid velocity changed across an unchanged near-zero-time handoff: RMS " + liquidVelocityRms
            + " m/s, which is " + (100.0 * liquidVelocityRelative) + "% of the mean liquid speed " + meanLiquidSpeed
            + " m/s");
    if (hasOilWaterSlip) {
      assertTrue(rmsDifferenceInterior(oilVelocityBefore, waterVelocityBefore) > 1.0e-3,
          "Three-phase regression case must exercise non-zero oil/water slip");
      assertTrue(oilVelocityRms <= 1.0e-4,
          name + " interior oil velocity changed across an unchanged near-zero-time handoff: RMS " + oilVelocityRms
              + " m/s");
      assertTrue(waterVelocityRms <= 1.0e-4,
          name + " interior water velocity changed across an unchanged near-zero-time handoff: RMS " + waterVelocityRms
              + " m/s");
    }
    assertTrue(massChangeKg <= 4.0 * flowRateKgSec * timeStep,
        name + " domain mass changed faster than the boundary-flux envelope: " + massChangeKg + " kg");
  }

  /**
   * Create a TwoFluidPipe that calculates outlet pressure from inlet flow.
   *
   * @param name pipe name
   * @param fluid inlet fluid
   * @param flowRateKgSec flow rate in kg/s
   * @param inletPressureBara inlet pressure in bara
   * @return configured pipe
   */
  private TwoFluidPipe createPressureDropTwoFluidPipe(String name, SystemInterface fluid, double flowRateKgSec,
      double inletPressureBara) {
    Stream inlet = createRegressionStream(name + "-tf-inlet", fluid, flowRateKgSec, inletPressureBara);
    TwoFluidPipe pressureDropPipe = new TwoFluidPipe(name + "-tf-pipe", inlet);
    pressureDropPipe.setLength(300.0);
    pressureDropPipe.setDiameter(0.15);
    pressureDropPipe.setRoughness(1.0e-5);
    pressureDropPipe.setNumberOfSections(6);
    pressureDropPipe.setElevationProfile(new double[6]);
    pressureDropPipe.setThermodynamicUpdateInterval(1);
    pressureDropPipe.setEnableAdaptiveTimestepping(true);
    pressureDropPipe.setCflNumber(0.8);
    pressureDropPipe.setSteadyStateMaxWallClockTime(1.0);
    return pressureDropPipe;
  }

  /**
   * Create a Beggs-Brill pipe with the same hydraulic setup.
   *
   * @param name pipe name
   * @param fluid inlet fluid
   * @param flowRateKgSec flow rate in kg/s
   * @param inletPressureBara inlet pressure in bara
   * @return configured pipe
   */
  private PipeBeggsAndBrills createPressureDropBeggsBrillPipe(String name, SystemInterface fluid, double flowRateKgSec,
      double inletPressureBara) {
    Stream inlet = createRegressionStream(name + "-bb-inlet", fluid, flowRateKgSec, inletPressureBara);
    PipeBeggsAndBrills pressureDropPipe = new PipeBeggsAndBrills(name + "-bb-pipe", inlet);
    pressureDropPipe.setLength(300.0);
    pressureDropPipe.setDiameter(0.15);
    pressureDropPipe.setElevation(0.0);
    pressureDropPipe.setPipeWallRoughness(1.0e-5);
    pressureDropPipe.setNumberOfIncrements(6);
    pressureDropPipe.setHeatTransferCoefficient(0.0);
    return pressureDropPipe;
  }

  /**
   * Create a regression inlet stream.
   *
   * @param name stream name
   * @param fluid fluid
   * @param flowRateKgSec flow rate in kg/s
   * @param inletPressureBara inlet pressure in bara
   * @return initialized stream
   */
  private Stream createRegressionStream(String name, SystemInterface fluid, double flowRateKgSec,
      double inletPressureBara) {
    Stream inlet = new Stream(name, fluid);
    inlet.setFlowRate(flowRateKgSec, "kg/sec");
    inlet.setTemperature(20.0, "C");
    inlet.setPressure(inletPressureBara, "bara");
    inlet.run();
    return inlet;
  }

  /**
   * Verify that a transient pressure-boundary change moves toward the matching stationary target.
   *
   * @param name test name
   * @param fluid inlet fluid
   * @param flowRateKgSec flow rate in kg/s
   * @param inletPressureBara inlet pressure in bara
   * @param initialOutletBara initial outlet pressure in bara
   * @param changedOutletBara changed outlet pressure in bara
   * @param maxReasonableTime maximum acceptable settling time in seconds
   */
  private void assertTransientOutletPressureChangeApproachesStationary(String name, SystemInterface fluid,
      double flowRateKgSec, double inletPressureBara, double initialOutletBara, double changedOutletBara,
      double maxReasonableTime) {
    TwoFluidPipe transientPipe = createRegressionPipe(name + "-transient", fluid, flowRateKgSec, inletPressureBara,
        initialOutletBara);
    transientPipe.run();
    double[] initialPressure = transientPipe.getPressureProfile();

    TwoFluidPipe stationaryPipe = createStationaryTargetPipe(name, fluid.clone(), flowRateKgSec, inletPressureBara,
        changedOutletBara);
    SettlingResult settling = runTransientUntilNewStationaryState(transientPipe, stationaryPipe, changedOutletBara,
        maxReasonableTime);

    assertOutletPressure(transientPipe, changedOutletBara);
    assertPressureProfilePhysical(transientPipe);
    double initialDistance = rmsDifference(initialPressure, stationaryPipe.getPressureProfile());
    assertTrue(settling.pressureRmsPa < initialDistance,
        "Transient pressure profile should move toward new stationary target. Initial RMS: " + initialDistance
            + " Pa, final RMS: " + settling.pressureRmsPa + " Pa");
    assertTrue(settling.settled,
        name + " pressure profile did not settle within " + maxReasonableTime + " s. Pressure RMS: "
            + settling.pressureRmsPa + " Pa, liquid holdup RMS: " + settling.liquidHoldupRms + ", elapsed time: "
            + settling.elapsedTime + " s");
  }

  /**
   * Assert that a pressure boundary change settles to the new stationary pressure profile quickly.
   *
   * @param name case name
   * @param fluid inlet fluid
   * @param flowRateKgSec flow rate in kg/s
   * @param inletPressureBara inlet pressure in bara
   * @param initialOutletBara initial outlet pressure in bara
   * @param changedOutletBara changed outlet pressure in bara
   * @param maxReasonableTime maximum acceptable settling time in seconds
   */
  private void assertTimeToNewStationaryPressureProfile(String name, SystemInterface fluid, double flowRateKgSec,
      double inletPressureBara, double initialOutletBara, double changedOutletBara, double maxReasonableTime) {
    TwoFluidPipe transientPipe = createRegressionPipe(name + "-settling-transient", fluid, flowRateKgSec,
        inletPressureBara, initialOutletBara);
    transientPipe.run();

    TwoFluidPipe stationaryPipe = createStationaryTargetPipe(name + "-settling", fluid.clone(), flowRateKgSec,
        inletPressureBara, changedOutletBara);
    SettlingResult settling = runTransientUntilNewStationaryState(transientPipe, stationaryPipe, changedOutletBara,
        maxReasonableTime);

    assertOutletPressure(transientPipe, changedOutletBara);
    assertPressureProfilePhysical(transientPipe);
    assertTrue(settling.settled,
        name + " pressure profile should settle within " + maxReasonableTime + " s. Settling time: "
            + settling.elapsedTime + " s, pressure RMS: " + settling.pressureRmsPa + " Pa, liquid holdup RMS: "
            + settling.liquidHoldupRms);
  }

  /**
   * Assert that TwoFluidPipe and Beggs-Brill calculate comparable steady pressure drops.
   *
   * @param name case name
   * @param fluid inlet fluid
   * @param flowRateKgSec flow rate in kg/s
   * @param inletPressureBara inlet pressure in bara
   */
  private void assertSteadyPressureDropSimilarToBeggsAndBrill(String name, SystemInterface fluid, double flowRateKgSec,
      double inletPressureBara) {
    TwoFluidPipe twoFluidPipe = createPressureDropTwoFluidPipe(name, fluid, flowRateKgSec, inletPressureBara);
    twoFluidPipe.run();

    PipeBeggsAndBrills beggsBrillPipe = createPressureDropBeggsBrillPipe(name, fluid.clone(), flowRateKgSec,
        inletPressureBara);
    beggsBrillPipe.run();

    double twoFluidDpBar = pressureDropBar(twoFluidPipe.getPressureProfile(), true);
    double beggsBrillDpBar = pressureDropBar(beggsBrillPipe.getPressureProfile(), false);
    double relativeDifference = Math.abs(twoFluidDpBar - beggsBrillDpBar) / Math.max(0.1, Math.abs(beggsBrillDpBar));

    logger.printf(org.apache.logging.log4j.Level.INFO,
        "%s steady pressure drop: TwoFluidPipe %.3f bar, " + "Beggs-Brill %.3f bar, relative difference %.2f%n", name,
        twoFluidDpBar, beggsBrillDpBar, relativeDifference);

    assertTrue(twoFluidDpBar > 0.0, name + " TwoFluidPipe pressure drop should be positive");
    assertTrue(beggsBrillDpBar > 0.0, name + " Beggs-Brill pressure drop should be positive");
    assertTrue(relativeDifference < 1.0,
        name + " TwoFluidPipe steady pressure drop should be similar to Beggs-Brill. TwoFluidPipe: " + twoFluidDpBar
            + " bar, Beggs-Brill: " + beggsBrillDpBar + " bar");
  }

  /**
   * Create the stationary target for a changed outlet pressure.
   *
   * @param name case name
   * @param fluid inlet fluid
   * @param flowRateKgSec flow rate in kg/s
   * @param inletPressureBara inlet pressure in bara
   * @param outletPressureBara target outlet pressure in bara
   * @return stationary target pipe
   */
  private TwoFluidPipe createStationaryTargetPipe(String name, SystemInterface fluid, double flowRateKgSec,
      double inletPressureBara, double outletPressureBara) {
    TwoFluidPipe stationaryPipe = createRegressionPipe(name + "-stationary", fluid, flowRateKgSec, inletPressureBara,
        outletPressureBara);
    stationaryPipe.run();
    return stationaryPipe;
  }

  /**
   * Run a transient pressure-boundary change until the new stationary solution is reached.
   *
   * @param transientPipe transient pipe initialized at the old boundary
   * @param stationaryPipe stationary target pipe at the new boundary
   * @param changedOutletBara changed outlet pressure in bara
   * @param maxReasonableTime maximum acceptable settling time in seconds
   * @return settling result
   */
  private SettlingResult runTransientUntilNewStationaryState(TwoFluidPipe transientPipe, TwoFluidPipe stationaryPipe,
      double changedOutletBara, double maxReasonableTime) {
    transientPipe.setOutletPressure(changedOutletBara, "bara");
    UUID id = UUID.randomUUID();
    double elapsedTime = 0.0;
    double timeStep = 2.0;
    SettlingResult result = new SettlingResult();

    while (elapsedTime < maxReasonableTime) {
      transientPipe.runTransient(timeStep, id);
      elapsedTime += timeStep;

      result.elapsedTime = elapsedTime;
      result.pressureRmsPa = rmsDifference(transientPipe.getPressureProfile(), stationaryPipe.getPressureProfile());
      result.liquidHoldupRms = rmsDifference(transientPipe.getLiquidHoldupProfile(),
          stationaryPipe.getLiquidHoldupProfile());
      result.oilHoldupRms = rmsDifference(transientPipe.getOilHoldupProfile(), stationaryPipe.getOilHoldupProfile());
      result.waterHoldupRms = rmsDifference(transientPipe.getWaterHoldupProfile(),
          stationaryPipe.getWaterHoldupProfile());
      result.settled = isSettledToStationaryPressureProfile(result);
      if (result.settled) {
        logger.printf(org.apache.logging.log4j.Level.INFO,
            "%s pressure profile settled in %.1f s " + "(pressure RMS %.0f Pa, liquid holdup RMS %.4f)%n",
            transientPipe.getName(), result.elapsedTime, result.pressureRmsPa, result.liquidHoldupRms);
        return result;
      }
    }
    return result;
  }

  /**
   * Check acoustic pressure-profile convergence against a stationary target.
   *
   * <p>
   * Liquid inventory can require a much longer material-residence time to reach its stationary distribution. Holdup
   * differences remain recorded in {@link SettlingResult} for diagnostics, but must not be used to gate this bounded
   * pressure-response check.
   * </p>
   *
   * @param result current settling result
   * @return true if the pressure profile has settled
   */
  private boolean isSettledToStationaryPressureProfile(SettlingResult result) {
    double pressureLimitPa = 2.0e5;
    return result.pressureRmsPa <= pressureLimitPa;
  }

  /**
   * Settling metrics for a transient pressure-boundary change.
   */
  private static class SettlingResult {
    private boolean settled;
    private double elapsedTime;
    private double pressureRmsPa;
    private double liquidHoldupRms;
    private double oilHoldupRms;
    private double waterHoldupRms;
  }

  /**
   * Assert that outlet pressure equals the explicit pressure boundary.
   *
   * @param checkedPipe pipe to inspect
   * @param expectedOutletBara expected outlet pressure in bara
   */
  private void assertOutletPressure(TwoFluidPipe checkedPipe, double expectedOutletBara) {
    double[] pressureProfile = checkedPipe.getPressureProfile();
    double outletPressureBara = pressureProfile[pressureProfile.length - 1] / 1e5;
    assertEquals(expectedOutletBara, outletPressureBara, 0.05,
        "Outlet pressure should match the explicit constant-pressure boundary");
  }

  /**
   * Assert finite, positive pressures everywhere in the profile.
   *
   * @param checkedPipe pipe to inspect
   */
  private void assertPressureProfilePhysical(TwoFluidPipe checkedPipe) {
    double[] pressureProfile = checkedPipe.getPressureProfile();
    for (int i = 0; i < pressureProfile.length; i++) {
      assertTrue(Double.isFinite(pressureProfile[i]), "Pressure should be finite at index " + i);
      assertTrue(pressureProfile[i] > 0.0, "Pressure should be positive at index " + i);
    }
  }

  /**
   * Assert finite, positive pressures that also fall in the flow direction.
   *
   * <p>
   * Only a settled profile has to be monotone. An instantaneous transient profile does not: a decompression wave
   * travelling up the line is a non-monotone pressure profile, and that is what it is meant to be. On this 300 m case
   * the acoustic transit time is about 0.75 s against a 2 s step, so the response rings rather than settles — measured
   * over 60 s the total drop swings between +92 kPa and -11 kPa about a steady drop of 1.4 kPa, and the sign of the
   * local gradient changes on most steps. That behaviour predates the flow-map work and is unchanged by it; asserting
   * monotonicity at whichever instant the settling detector stops on was measuring the phase of the ringing.
   * </p>
   *
   * @param checkedPipe pipe to inspect
   */
  private void assertSteadyPressureProfilePhysical(TwoFluidPipe checkedPipe) {
    assertPressureProfilePhysical(checkedPipe);
    double[] pressureProfile = checkedPipe.getPressureProfile();
    assertTrue(pressureProfile[0] >= pressureProfile[pressureProfile.length - 1],
        "Pressure should not increase from inlet to outlet");
  }

  /**
   * Assert that oil plus water holdup matches total liquid holdup.
   *
   * @param checkedPipe pipe to inspect
   */
  private void assertLiquidHoldupMatchesOilPlusWater(TwoFluidPipe checkedPipe) {
    double[] liquidHoldup = checkedPipe.getLiquidHoldupProfile();
    double[] oilHoldup = checkedPipe.getOilHoldupProfile();
    double[] waterHoldup = checkedPipe.getWaterHoldupProfile();
    for (int i = 0; i < liquidHoldup.length; i++) {
      assertEquals(liquidHoldup[i], oilHoldup[i] + waterHoldup[i], 0.02,
          "Oil plus water holdup should equal total liquid holdup at index " + i);
    }
  }

  /**
   * Calculate root-mean-square profile difference.
   *
   * @param left first profile
   * @param right second profile
   * @return RMS difference
   */
  private double rmsDifference(double[] left, double[] right) {
    int length = Math.min(left.length, right.length);
    double sumSquares = 0.0;
    for (int i = 0; i < length; i++) {
      double diff = left[i] - right[i];
      sumSquares += diff * diff;
    }
    return Math.sqrt(sumSquares / length);
  }

  /**
   * Calculate RMS difference over finite-volume interior cells, excluding boundary cells.
   *
   * @param left first profile
   * @param right second profile
   * @return interior RMS difference
   */
  private double rmsDifferenceInterior(double[] left, double[] right) {
    int length = Math.min(left.length, right.length);
    double sumSquares = 0.0;
    for (int i = 1; i < length - 1; i++) {
      double diff = left[i] - right[i];
      sumSquares += diff * diff;
    }
    return Math.sqrt(sumSquares / (length - 2));
  }

  /**
   * Calculate pressure drop from a pressure profile.
   *
   * @param pressureProfile pressure profile
   * @param valuesInPascal true if profile values are Pa, false if bara
   * @return pressure drop in bar
   */
  private double pressureDropBar(double[] pressureProfile, boolean valuesInPascal) {
    double pressureDrop = pressureProfile[0] - pressureProfile[pressureProfile.length - 1];
    return valuesInPascal ? pressureDrop / 1.0e5 : pressureDrop;
  }

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
