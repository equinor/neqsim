package neqsim.process.logic.esd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.action.EnergizeESDValveAction;
import neqsim.process.logic.action.ParallelActionGroup;
import neqsim.process.logic.action.SetValveOpeningAction;
import neqsim.process.logic.action.TripValveAction;

/**
 * Prevents action-type, order or delay changes from retaining the same closure-policy readback.
 *
 * @author ESOL
 * @version 1.0
 */
class ESDLogicTripConfigurationTest {
  /** Reads a native trip definition without executing its target. */
  @Test
  void nativeTripReadbackIsPureAndExplicit() {
    ESDValve valve = new ESDValve("XV-101");
    ESDLogic logic = trip(valve, 0.25);
    Map<String, Object> configuration = logic.getTripActionConfiguration();
    assertEquals("neqsim.esd_trip_action_configuration.v1", configuration.get("schemaVersion"));
    assertEquals(Boolean.TRUE, configuration.get("supportedTripPolicy"));
    assertEquals(configuration, logic.getTripActionConfiguration());
    assertTrue(valve.isEnergized());
    assertEquals(100.0, valve.getPercentValveOpening(), 0.0);
  }

  /** The same logical name and physical target cannot conceal an opening operation. */
  @Test
  void openingActionsCannotMasqueradeAsTripActions() {
    ESDValve valve = new ESDValve("XV-101");
    Map<String, Object> closing = trip(valve, 0.0).getTripActionConfiguration();
    ESDLogic energize = new ESDLogic("ESD");
    energize.addAction(new EnergizeESDValveAction(valve), 0.0);
    ESDLogic opening = new ESDLogic("ESD");
    opening.addAction(new SetValveOpeningAction(valve, 100.0), 0.0);
    assertNotEquals(closing, energize.getTripActionConfiguration());
    assertNotEquals(closing, opening.getTripActionConfiguration());
    assertEquals(Boolean.FALSE, energize.getTripActionConfiguration().get("supportedTripPolicy"));
    assertEquals(Boolean.FALSE, opening.getTripActionConfiguration().get("supportedTripPolicy"));
    assertTrue(valve.isEnergized());
  }

  /** Preserves a changed configured delay in the readback used by higher-level fingerprints. */
  @Test
  void delayChangesTheConfiguration() {
    ESDValve valve = new ESDValve("XV-101");
    assertNotEquals(trip(valve, 0.0).getTripActionConfiguration(), trip(valve, 0.5).getTripActionConfiguration());
  }

  /** Captures actual stroke and fail-action changes rather than retaining only a valve name. */
  @Test
  void actuatorConfigurationChangesTheReadback() {
    ESDValve valve = new ESDValve("XV-101");
    ESDLogic logic = trip(valve, 0.0);
    Map<String, Object> initial = logic.getTripActionConfiguration();
    valve.setStrokeTime(valve.getStrokeTime() + 1.0);
    Map<String, Object> changedStroke = logic.getTripActionConfiguration();
    assertNotEquals(initial, changedStroke);
    valve.setFailSafePosition(100.0);
    assertNotEquals(changedStroke, logic.getTripActionConfiguration());
    assertTrue(valve.isEnergized());
  }

  /** Preserves parallel structure and child order with actual target names. */
  @Test
  void parallelChildOrderRemainsVisible() {
    ESDValve first = new ESDValve("XV-101");
    ESDValve second = new ESDValve("XV-102");
    ParallelActionGroup forward = new ParallelActionGroup("Parallel");
    forward.addAction(new TripValveAction(first));
    forward.addAction(new TripValveAction(second));
    ParallelActionGroup reverse = new ParallelActionGroup("Parallel");
    reverse.addAction(new TripValveAction(second));
    reverse.addAction(new TripValveAction(first));
    ESDLogic one = new ESDLogic("ESD");
    one.addAction(forward, 0.0);
    ESDLogic two = new ESDLogic("ESD");
    two.addAction(reverse, 0.0);
    assertEquals(Boolean.TRUE, one.getTripActionConfiguration().get("supportedTripPolicy"));
    assertNotEquals(one.getTripActionConfiguration(), two.getTripActionConfiguration());
  }

  /** Rejects custom overriding action types even when they inherit the same target getter. */
  @Test
  void overridingTripSubclassIsNotTheSupportedNativePolicy() {
    ESDLogic logic = new ESDLogic("ESD");
    logic.addAction(new TripSubclass(new ESDValve("XV-101")), 0.0);
    assertEquals(Boolean.FALSE, logic.getTripActionConfiguration().get("supportedTripPolicy"));
  }

  /** Rejects empty and cyclic groups without recursing forever or issuing commands. */
  @Test
  void emptyAndCyclicDefinitionsAreUnsupported() {
    ESDLogic empty = new ESDLogic("ESD");
    assertEquals(Boolean.FALSE, empty.getTripActionConfiguration().get("supportedTripPolicy"));
    ParallelActionGroup group = new ParallelActionGroup("Cyclic");
    group.addAction(group);
    ESDLogic cyclic = new ESDLogic("ESD");
    cyclic.addAction(group, 0.0);
    Map<String, Object> configuration = cyclic.getTripActionConfiguration();
    assertEquals(Boolean.FALSE, configuration.get("supportedTripPolicy"));
    Map<String, Object> action = actionAt(configuration);
    List<Map<String, Object>> children = childrenOf(action);
    assertEquals(Boolean.TRUE, children.get(0).get("cycleDetected"));
    assertTrue(cyclic.getTargetEquipment().isEmpty());
    assertTrue(cyclic.getTargetEquipmentCoverageIssue().contains("Cyclic"));
    assertFalse(cyclic.isActive());
  }

  /**
   * Builds a native one-step configuration for metadata tests.
   *
   * @param valve actual target
   * @param delay configured step delay
   * @return unexecuted logic
   */
  private static ESDLogic trip(ESDValve valve, double delay) {
    ESDLogic logic = new ESDLogic("ESD");
    logic.addAction(new TripValveAction(valve), delay);
    return logic;
  }

  /**
   * Extracts the first step's action for a controlled metadata assertion.
   *
   * @param configuration native configuration
   * @return first action definition
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> actionAt(Map<String, Object> configuration) {
    List<Map<String, Object>> steps = (List<Map<String, Object>>) configuration.get("steps");
    return (Map<String, Object>) steps.get(0).get("action");
  }

  /**
   * Extracts the child definitions of a controlled parallel action.
   *
   * @param action parallel action definition
   * @return child metadata
   */
  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> childrenOf(Map<String, Object> action) {
    return (List<Map<String, Object>>) action.get("children");
  }

  /**
   * Unreviewed derived action used to prove that a familiar base type does not establish its semantics.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class TripSubclass extends TripValveAction {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a deliberately unsupported derived action.
     *
     * @param valve actual target
     */
    private TripSubclass(ESDValve valve) {
      super(valve);
    }

    /** Does not deliver the inherited trip behaviour. */
    @Override
    public void execute() {
      // Deliberate semantic change; the readback must reject this derived class.
    }
  }
}
