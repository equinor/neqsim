package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for dynamic simulation features of Compressor.
 *
 * @author esol
 */
public class CompressorDynamicSimulationTest {
  private SystemInterface gasFluid;
  private Stream inletStream;
  private Compressor compressor;

  @BeforeEach
  public void setUp() {
    gasFluid = new SystemSrkEos(298.15, 50.0);
    gasFluid.addComponent("methane", 0.85);
    gasFluid.addComponent("ethane", 0.10);
    gasFluid.addComponent("propane", 0.05);
    gasFluid.setMixingRule("classic");

    inletStream = new Stream("inlet", gasFluid);
    inletStream.setFlowRate(5000.0, "kg/hr");
    inletStream.setTemperature(25.0, "C");
    inletStream.setPressure(40.0, "bara");
    inletStream.run();

    compressor = new Compressor("K-100", inletStream);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.78);
    compressor.setSpeed(10000);
    compressor.run();
  }

  @Test
  public void testCompressorStateEnum() {
    // Test initial state
    assertEquals(CompressorState.STOPPED, compressor.getOperatingState());

    // Test state transitions
    compressor.setOperatingState(CompressorState.RUNNING);
    assertEquals(CompressorState.RUNNING, compressor.getOperatingState());
    assertTrue(compressor.getOperatingState().isOperational());
    assertFalse(compressor.getOperatingState().isStopped());

    // Test canStart
    compressor.setOperatingState(CompressorState.STOPPED);
    assertTrue(compressor.getOperatingState().canStart());

    compressor.setOperatingState(CompressorState.TRIPPED);
    assertFalse(compressor.getOperatingState().canStart());
    assertTrue(compressor.getOperatingState().requiresAcknowledgment());
  }

  @Test
  public void testDriverType() {
    // Test electric motor
    assertEquals(0.95, DriverType.ELECTRIC_MOTOR.getTypicalEfficiency(), 0.01);
    assertTrue(DriverType.ELECTRIC_MOTOR.isElectric());
    assertFalse(DriverType.ELECTRIC_MOTOR.supportsVariableSpeed());

    // Test gas turbine
    assertEquals(0.35, DriverType.GAS_TURBINE.getTypicalEfficiency(), 0.01);
    assertFalse(DriverType.GAS_TURBINE.isElectric());
    assertTrue(DriverType.GAS_TURBINE.supportsVariableSpeed());

    // Test fromName
    assertEquals(DriverType.GAS_TURBINE, DriverType.fromName("gas turbine"));
    assertEquals(DriverType.VFD_MOTOR, DriverType.fromName("VFD"));
    assertEquals(DriverType.ELECTRIC_MOTOR, DriverType.fromName("unknown"));
  }

  @Test
  public void testCompressorDriver() {
    CompressorDriver driver = new CompressorDriver(DriverType.GAS_TURBINE, 5000);
    assertEquals(5000, driver.getRatedPower(), 0.1);
    assertEquals(5500, driver.getMaxPower(), 0.1); // 110% of rated

    // Test ambient temperature derating
    driver.setAmbientTemperature(288.15); // ISO conditions
    assertEquals(5000, driver.getAvailablePower(), 0.1);

    driver.setAmbientTemperature(308.15); // 20K above ISO
    assertTrue(driver.getAvailablePower() < 5000);

    // Test power margin
    double margin = driver.getPowerMargin(4000);
    assertTrue(margin > 0);

    // Test speed calculation
    double newSpeed = driver.calculateSpeedChange(5000, 6000, 3000, 1.0);
    assertTrue(newSpeed > 5000);
    assertTrue(newSpeed < 6000); // Rate limited
  }

  @Test
  public void testStartupProfile() {
    StartupProfile profile = new StartupProfile();
    profile.setMinimumIdleSpeed(2000);
    profile.setIdleHoldTime(30.0);

    // Test target speed at different times
    double targetSpeed = profile.getTargetSpeedAtTime(0.0, 10000);
    assertEquals(0.0, targetSpeed, 0.1);

    // After antisurge opening and initial ramp
    targetSpeed = profile.getTargetSpeedAtTime(20.0, 10000);
    assertTrue(targetSpeed > 0);

    // Check total duration
    double duration = profile.getTotalDuration(10000);
    assertTrue(duration > 0);

    // Test fast profile
    StartupProfile fastProfile = StartupProfile.createFastProfile(10000);
    assertTrue(fastProfile.getTotalDuration(10000) < profile.getTotalDuration(10000));
  }

  @Test
  public void testShutdownProfile() {
    ShutdownProfile profile = new ShutdownProfile(ShutdownProfile.ShutdownType.NORMAL, 10000);

    // Test target speed decreases over time
    double speed1 = profile.getTargetSpeedAtTime(0.0, 10000);
    double speed2 = profile.getTargetSpeedAtTime(30.0, 10000);
    double speed3 = profile.getTargetSpeedAtTime(60.0, 10000);

    assertTrue(speed1 >= speed2);
    assertTrue(speed2 >= speed3);

    // Test emergency shutdown is faster
    ShutdownProfile emergency = new ShutdownProfile(ShutdownProfile.ShutdownType.EMERGENCY, 10000);
    assertTrue(emergency.getTotalDuration() < profile.getTotalDuration());
  }

  @Test
  public void testOperatingHistory() {
    compressor.enableOperatingHistory();
    assertNotNull(compressor.getOperatingHistory());

    // Record some points
    compressor.setOperatingState(CompressorState.RUNNING);
    compressor.recordOperatingPoint(0.0);
    compressor.recordOperatingPoint(1.0);
    compressor.recordOperatingPoint(2.0);

    CompressorOperatingHistory history = compressor.getOperatingHistory();
    assertEquals(3, history.getPointCount());
    assertEquals(0, history.getSurgeEventCount());

    // Test peak values
    assertNotNull(history.getPeakPower());

    // Test summary generation
    String summary = history.generateSummary();
    assertTrue(summary.contains("Operating History Summary"));

    // Test clear
    history.clear();
    assertEquals(0, history.getPointCount());
  }

  @Test
  public void testAntiSurgeControlStrategies() {
    AntiSurge antiSurge = compressor.getAntiSurge();

    // Test proportional control (default)
    antiSurge.setControlStrategy(AntiSurge.ControlStrategy.PROPORTIONAL);
    assertEquals(AntiSurge.ControlStrategy.PROPORTIONAL, antiSurge.getControlStrategy());

    // Test PID control
    antiSurge.setControlStrategy(AntiSurge.ControlStrategy.PID);
    antiSurge.setPIDParameters(2.0, 0.5, 0.1);
    antiSurge.setPIDSetpoint(0.10);

    // Test valve position update
    antiSurge.setValvePosition(0.0);
    double position = antiSurge.updateController(0.05, 0.1); // Low surge margin
    assertTrue(position >= 0.0 && position <= 1.0);

    // Test minimum recycle flow
    antiSurge.setMinimumRecycleFlow(500.0);
    double recycleFlow = antiSurge.getRecycleFlow(3000.0);
    assertTrue(recycleFlow >= 500.0);
  }

  @Test
  public void testDynamicStateUpdate() {
    // Set up for dynamic simulation
    compressor.setRotationalInertia(15.0);
    compressor.setMaxAccelerationRate(100.0);
    compressor.setMaxDecelerationRate(200.0);
    compressor.enableOperatingHistory();

    // Start compressor
    compressor.startCompressor(10000);
    assertEquals(CompressorState.STARTING, compressor.getOperatingState());

    // Simulate several time steps
    for (int i = 0; i < 100; i++) {
      compressor.updateDynamicState(0.1);
    }

    // After sufficient time, should be running
    assertTrue(compressor.getOperatingState() == CompressorState.RUNNING
        || compressor.getOperatingState() == CompressorState.STARTING);

    // Verify history was recorded
    assertTrue(compressor.getOperatingHistory().getPointCount() > 0);
  }

  @Test
  public void testSurgeMarginThresholds() {
    compressor.setSurgeWarningThreshold(0.15);
    compressor.setSurgeCriticalThreshold(0.05);

    assertEquals(0.15, compressor.getSurgeWarningThreshold(), 0.001);
    assertEquals(0.05, compressor.getSurgeCriticalThreshold(), 0.001);
  }

  @Test
  public void testPerformanceDegradation() {
    compressor.setDegradationFactor(0.95);
    assertEquals(0.95, compressor.getDegradationFactor(), 0.001);

    compressor.setFoulingFactor(0.03);
    assertEquals(0.03, compressor.getFoulingFactor(), 0.001);

    // Test operating hours tracking
    compressor.setOperatingHours(1000);
    compressor.addOperatingHours(100);
    assertEquals(1100, compressor.getOperatingHours(), 0.1);
  }

  @Test
  public void testAutoSpeedMode() {
    compressor.setAutoSpeedMode(true);
    assertTrue(compressor.isAutoSpeedMode());

    compressor.setAutoSpeedMode(false);
    assertFalse(compressor.isAutoSpeedMode());
  }

  @Test
  public void testTripAndAcknowledge() {
    compressor.setOperatingState(CompressorState.RUNNING);
    compressor.emergencyShutdown();

    assertEquals(CompressorState.TRIPPED, compressor.getOperatingState());
    assertFalse(compressor.getOperatingState().canStart());

    compressor.acknowledgeTrip();
    assertEquals(CompressorState.STANDBY, compressor.getOperatingState());
    assertTrue(compressor.getOperatingState().canStart());
  }

  @Test
  public void testResetDynamicState() {
    compressor.setOperatingState(CompressorState.RUNNING);
    compressor.enableOperatingHistory();
    compressor.recordOperatingPoint(1.0);

    compressor.resetDynamicState();
    assertEquals(CompressorState.STOPPED, compressor.getOperatingState());
  }

  @Test
  public void testEventListenerRegistration() {
    // Create a simple test listener that tracks calls
    final int[] callCount = {0};
    CompressorEventListener listener = new CompressorEventListener() {
      @Override
      public void onSurgeApproach(Compressor compressor, double surgeMargin, boolean isCritical) {
        callCount[0]++;
      }

      @Override
      public void onSurgeOccurred(Compressor compressor, double surgeMargin) {
        callCount[0]++;
      }

      @Override
      public void onSpeedLimitExceeded(Compressor compressor, double currentSpeed, double ratio) {
        callCount[0]++;
      }

      @Override
      public void onSpeedBelowMinimum(Compressor compressor, double currentSpeed, double ratio) {
        callCount[0]++;
      }

      @Override
      public void onPowerLimitExceeded(Compressor compressor, double currentPower,
          double maxPower) {
        callCount[0]++;
      }

      @Override
      public void onStateChange(Compressor compressor, CompressorState oldState,
          CompressorState newState) {
        callCount[0]++;
      }

      @Override
      public void onStoneWallApproach(Compressor compressor, double stoneWallMargin) {
        callCount[0]++;
      }

      @Override
      public void onStartupComplete(Compressor compressor) {
        callCount[0]++;
      }

      @Override
      public void onShutdownComplete(Compressor compressor) {
        callCount[0]++;
      }
    };

    compressor.addEventListener(listener);

    // Trigger a state change event
    compressor.setOperatingState(CompressorState.RUNNING);
    assertEquals(1, callCount[0]); // onStateChange should be called

    // Remove listener and verify no more events
    compressor.removeEventListener(listener);
    compressor.setOperatingState(CompressorState.STOPPED);
    assertEquals(1, callCount[0]); // Should still be 1 since listener was removed
  }

  @Test
  public void testAntiSurgeSummary() {
    AntiSurge antiSurge = compressor.getAntiSurge();
    antiSurge.setActive(true);
    antiSurge.setControlStrategy(AntiSurge.ControlStrategy.PID);
    antiSurge.setValvePosition(0.5);
    antiSurge.setCurrentSurgeFraction(0.15);

    String summary = antiSurge.getSummary();
    assertTrue(summary.contains("Anti-Surge Controller Summary"));
    assertTrue(summary.contains("PID"));
  }
}
