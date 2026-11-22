package neqsim.process.logic.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChart;
import neqsim.process.equipment.compressor.CompressorState;
import neqsim.process.equipment.compressor.ShutdownProfile;
import org.junit.jupiter.api.Test;

/**
 * Checks observed native shaft response and command-path failures of compressor shutdown actions.
 *
 * @author NeqSim study validation
 * @version 1.0
 */
class TripCompressorActionTest {
  /**
   * Creates a running shaft with a declared rate limit and no invented driver-inertia model.
   *
   * @return actual native compressor
   */
  private Compressor running() {
    Compressor compressor = new Compressor("K-100");
    compressor.setSpeed(1000.0);
    ((CompressorChart) compressor.getCompressorChart()).setMaxSpeedCurve(2000.0);
    compressor.setMaxDecelerationRate(1000.0);
    compressor.setOperatingState(CompressorState.RUNNING);
    return compressor;
  }

  /** Verifies a command is incomplete until the native profile advances the actual shaft to rest. */
  @Test
  void observesActualShaftAndDoesNotRestartAnExecutedRamp() {
    Compressor compressor = running();
    TripCompressorAction action = new TripCompressorAction(compressor);
    assertFalse(action.isComplete());
    action.execute();
    assertEquals(CompressorState.SHUTDOWN, compressor.getOperatingState());
    assertEquals(1000.0, compressor.getSpeed(), 0.0);
    assertFalse(action.isComplete());
    compressor.updateDynamicState(0.5);
    assertEquals(750.0, compressor.getSpeed(), 1.0e-9);
    action.execute();
    compressor.updateDynamicState(0.5);
    assertEquals(500.0, compressor.getSpeed(), 1.0e-9);
    assertFalse(action.isComplete());
    compressor.updateDynamicState(1.0);
    assertEquals(0.0, compressor.getSpeed(), 0.0);
    assertTrue(action.isComplete());
    action.reset();
    assertFalse(action.isComplete());
    assertEquals(0.0, compressor.getSpeed(), 0.0);
  }

  /** Verifies a failed command path cannot acquire completion or stop the native shaft. */
  @Test
  void unavailableCommandRemainsIncompleteUntilActuallyIssued() {
    Compressor compressor = running();
    TripCompressorAction action = new TripCompressorAction(compressor);
    action.setCommandAvailable(false);
    action.execute();
    compressor.updateDynamicState(1.0);
    assertEquals(CompressorState.RUNNING, compressor.getOperatingState());
    assertEquals(1000.0, compressor.getSpeed(), 0.0);
    assertFalse(action.isComplete());
    action.setCommandAvailable(true);
    action.execute();
    compressor.updateDynamicState(0.5);
    assertEquals(750.0, compressor.getSpeed(), 1.0e-9);
    assertFalse(action.isComplete());
  }

  /** Checks actual target identity, immutable ownership and invalid completion thresholds. */
  @Test
  void retainsActualTargetAndRejectsInvalidThresholds() {
    Compressor compressor = running();
    TripCompressorAction action = new TripCompressorAction(compressor);
    assertSame(compressor, action.getTargetEquipment().get(0));
    assertSame(compressor, action.getCompressor());
    assertTrue(action.isTargetEquipmentIdentityComplete());
    assertEquals("K-100", action.getTargetName());
    assertThrows(UnsupportedOperationException.class, () -> action.getTargetEquipment().clear());
    assertThrows(IllegalArgumentException.class,
        () -> new TripCompressorAction(compressor, ShutdownProfile.ShutdownType.EMERGENCY, Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> new TripCompressorAction(compressor, ShutdownProfile.ShutdownType.EMERGENCY, -1.0));
  }
}
