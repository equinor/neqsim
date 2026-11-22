package neqsim.process.logic.esd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.LogicAction;
import neqsim.process.logic.LogicCondition;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.action.CloseValveAction;
import neqsim.process.logic.action.ConditionalAction;
import neqsim.process.logic.action.ParallelActionGroup;
import neqsim.process.logic.action.SetValveOpeningAction;
import neqsim.process.logic.action.TripValveAction;

/**
 * Discriminating rearm, pause and object-graph checks for native emergency sequences.
 *
 * @author ESOL
 * @version 1.0
 */
class ESDLogicLifecycleTest {
  /** Legacy uid1 fixture written by the unchanged release3.28.0 ESD class with transient actions. */
  private static final String LEGACY_STREAM_BASE64 = "rO0ABXNyACFuZXFzaW0ucHJvY2Vzcy5sb2dpYy5lc2QuRVNETG9naWMAAAAAAAAAAQIABUkAEmN1cnJl"
      + "bnRBY3Rpb25JbmRleEQADGN1cnJlbnREZWxheUQAC2VsYXBzZWRUaW1lTAAEbmFtZXQAEkxqYXZhL2xh"
      + "bmcvU3RyaW5nO0wABXN0YXRldAAhTG5lcXNpbS9wcm9jZXNzL2xvZ2ljL0xvZ2ljU3RhdGU7eHAAAAAA"
      + "AAAAAAAAAAAAAAAAAAAAAHQAFWxlZ2FjeSByZWNvcmRlZCBncmFwaH5yAB9uZXFzaW0ucHJvY2Vzcy5sb2dp"
      + "Yy5Mb2dpY1N0YXRlAAAAAAAAAAASAAB4cgAOamF2YS5sYW5nLkVudW0AAAAAAAAAABIAAHhwdAAESURMRQ==";

  /**
   * Rejects a real legacy stream whose transient action definition cannot be reconstructed.
   *
   * @throws Exception if the serialized fixture cannot be opened
   */
  @Test
  void legacyDiscardedActionGraphCannotBecomeAnEmptyPassingSequence() throws Exception {
    byte[] bytes = Base64.getDecoder().decode(LEGACY_STREAM_BASE64);
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      InvalidObjectException failure = assertThrows(InvalidObjectException.class, () -> input.readObject());
      assertTrue(failure.getMessage().contains("action definitions are absent"));
    }
  }

  /** Proves that a second demand calls the same native trip action again. */
  @Test
  void repeatedDemandRearmsTripAction() {
    ESDValve valve = new ESDValve("ESDV-101");
    ESDLogic logic = new ESDLogic("ESD-101");
    logic.addAction(new TripValveAction(valve), 0.0);
    logic.activate();
    logic.execute(0.1);
    assertFalse(valve.isEnergized());
    assertTrue(valve.isClosing());
    assertTrue(logic.reset());
    assertFalse(valve.isEnergized(), "Logic reset must not energize its equipment");
    valve.reset();
    assertTrue(valve.isEnergized());
    logic.activate();
    logic.execute(0.1);
    assertFalse(valve.isEnergized(), "A rearmed action must deliver the second trip");
  }

  /** Checks a fresh cycle after completion, even without a separate logic reset call. */
  @Test
  void activationAfterCompletionRearmsBuiltInAction() {
    ThrottlingValve valve = new ThrottlingValve("XV-101");
    valve.setPercentValveOpening(80.0);
    ESDLogic logic = new ESDLogic("ESD-101");
    logic.addAction(new CloseValveAction(valve), 0.0);
    logic.activate();
    logic.execute(0.1);
    assertTrue(logic.isComplete());
    valve.setPercentValveOpening(75.0);
    logic.activate();
    logic.execute(0.1);
    assertEquals(0.0, valve.getPercentValveOpening(), 0.0);
    assertTrue(logic.isComplete());
  }

  /** Keeps elapsed time and the unfinished delay across a pause, without replaying a completed step. */
  @Test
  void pauseResumePreservesStepAndDelay() {
    CountingAction first = new CountingAction();
    CountingAction second = new CountingAction();
    ESDLogic logic = new ESDLogic("ESD-101");
    logic.addAction(first, 0.0);
    logic.addAction(second, 1.0);
    logic.activate();
    logic.execute(0.25);
    logic.execute(0.25);
    logic.execute(0.25);
    assertEquals(1, logic.getCurrentActionIndex());
    logic.deactivate();
    assertEquals(LogicState.PAUSED, logic.getState());
    logic.execute(20.0);
    assertEquals(0.75, logic.getElapsedTime(), 0.0);
    logic.activate();
    assertEquals(1, logic.getCurrentActionIndex());
    logic.execute(0.25);
    logic.execute(0.25);
    assertEquals(0, second.calls);
    logic.execute(0.25);
    assertEquals(1, first.calls, "Resume must not replay the completed first step");
    assertEquals(1, second.calls);
    assertTrue(logic.isComplete());
    assertEquals(1.5, logic.getElapsedTime(), 0.0);
  }

  /** Verifies that resetting nested parallel bookkeeping does not itself command equipment. */
  @Test
  void parallelResetRearmsChildrenWithoutChangingEquipment() {
    ESDValve tripValve = new ESDValve("ESDV-101");
    ThrottlingValve controlValve = new ThrottlingValve("LCV-101");
    controlValve.setPercentValveOpening(70.0);
    ParallelActionGroup group = new ParallelActionGroup("Isolate");
    group.addAction(new TripValveAction(tripValve));
    group.addAction(new SetValveOpeningAction(controlValve, 10.0));
    ESDLogic logic = new ESDLogic("ESD-101");
    logic.addAction(group, 0.0);
    logic.activate();
    logic.execute(0.1);
    assertFalse(tripValve.isEnergized());
    assertEquals(10.0, controlValve.getPercentValveOpening(), 0.0);
    logic.reset();
    assertFalse(tripValve.isEnergized());
    assertTrue(tripValve.isClosing());
    assertEquals(10.0, controlValve.getPercentValveOpening(), 0.0);
    assertFalse(group.isComplete());
    tripValve.reset();
    controlValve.setPercentValveOpening(60.0);
    logic.activate();
    logic.execute(0.1);
    assertFalse(tripValve.isEnergized());
    assertEquals(10.0, controlValve.getPercentValveOpening(), 0.0);
  }

  /** Re-evaluates a changed condition and re-arms both possible branch operations. */
  @Test
  void conditionalResetReevaluatesAndRearmsBothBranches() {
    MutableCondition condition = new MutableCondition();
    ThrottlingValve valve = new ThrottlingValve("XV-101");
    ConditionalAction action = new ConditionalAction(condition, new SetValveOpeningAction(valve, 20.0),
        new SetValveOpeningAction(valve, 80.0), "Select opening");
    condition.value = true;
    action.execute();
    assertEquals(20.0, valve.getPercentValveOpening(), 0.0);
    action.reset();
    assertEquals(20.0, valve.getPercentValveOpening(), 0.0);
    condition.value = false;
    action.execute();
    assertEquals(80.0, valve.getPercentValveOpening(), 0.0);
    action.reset();
    condition.value = true;
    action.execute();
    assertEquals(20.0, valve.getPercentValveOpening(), 0.0);
  }

  /**
   * Retains the sequence and restored equipment aliases in one serialized study graph.
   *
   * @throws Exception if serialization or restoration fails
   */
  @Test
  void serializationPreservesActionGraphAndEquipmentAlias() throws Exception {
    Bundle original = new Bundle();
    Bundle restored = roundTrip(original);
    assertNotSame(original.valve, restored.valve);
    assertEquals(1, restored.logic.getActionCount());
    restored.logic.activate();
    restored.logic.execute(0.1);
    assertFalse(restored.valve.isEnergized());
    assertTrue(original.valve.isEnergized(), "The copied study must not trip the original equipment");
  }

  /**
   * Preserves a paused in-flight sequence rather than restoring a null or empty action list.
   *
   * @throws Exception if serialization or restoration fails
   */
  @Test
  void serializationPreservesPausedProgress() throws Exception {
    ESDLogic original = new ESDLogic("ESD-101");
    original.addAction(new CountingAction(), 1.0);
    original.activate();
    original.execute(0.25);
    original.execute(0.25);
    original.deactivate();
    ESDLogic restored = roundTrip(original);
    assertEquals(LogicState.PAUSED, restored.getState());
    assertEquals(0.5, restored.getElapsedTime(), 0.0);
    restored.activate();
    restored.execute(0.25);
    restored.execute(0.25);
    restored.execute(0.25);
    assertTrue(restored.isComplete());
    assertEquals(1.25, restored.getElapsedTime(), 0.0);
  }

  /** Rejects unsupported custom object graphs rather than silently discarding safety actions. */
  @Test
  void nonSerializableActionGraphFailsAtWrite() {
    ESDLogic logic = new ESDLogic("ESD-101");
    logic.addAction(new NonSerializableAction(), 0.0);
    assertThrows(NotSerializableException.class, () -> roundTrip(logic));
  }

  /** Rejects malformed delay definitions before they can become an immediate safety action. */
  @Test
  void invalidDelaysFailBeforeChangingSequence() {
    ESDLogic logic = new ESDLogic("ESD-101");
    for (double delay : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0}) {
      assertThrows(IllegalArgumentException.class, () -> logic.addAction(new CountingAction(), delay));
    }
    assertThrows(IllegalArgumentException.class, () -> logic.addAction(null, 0.0));
    assertEquals(0, logic.getActionCount());
  }

  /** Rejects invalid intervals without advancing the active sequence or executing its action. */
  @Test
  void invalidTimeDoesNotMutateActiveLogic() {
    ESDLogic logic = new ESDLogic("ESD-101");
    CountingAction action = new CountingAction();
    logic.addAction(action, 0.0);
    logic.activate();
    for (double interval : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.1}) {
      assertThrows(IllegalArgumentException.class, () -> logic.execute(interval));
    }
    assertEquals(0, action.calls);
    assertEquals(0.0, logic.getElapsedTime(), 0.0);
    assertEquals(0, logic.getCurrentActionIndex());
    logic.execute(0.0);
    assertTrue(logic.isComplete());
  }

  /**
   * Copies one complete serializable object graph with native Java serialization.
   *
   * @param original original graph
   * @param <T> graph type
   * @return restored graph with internal aliases retained
   * @throws IOException if the graph is unsupported or cannot be written/read
   * @throws ClassNotFoundException if a graph class cannot be restored
   */
  @SuppressWarnings("unchecked")
  private static <T extends Serializable> T roundTrip(T original) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (T) input.readObject();
    }
  }

  /**
   * Serializable counter used to distinguish resuming from restarting a step.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class CountingAction implements LogicAction, Serializable {
    private static final long serialVersionUID = 1L;
    private int calls;

    /** Records one actual invocation. */
    @Override
    public void execute() {
      calls++;
    }

    /** @return description of this test operation */
    @Override
    public String getDescription() {
      return "Count one operation";
    }

    /** @return true after the first invocation */
    @Override
    public boolean isComplete() {
      return calls > 0;
    }

    /** @return synthetic target tag */
    @Override
    public String getTargetName() {
      return "COUNTER";
    }
  }

  /**
   * Serializable condition whose input can change between isolated demands.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class MutableCondition implements LogicCondition, Serializable {
    private static final long serialVersionUID = 1L;
    private boolean value;

    /** @return current condition value */
    @Override
    public boolean evaluate() {
      return value;
    }

    /** @return condition description */
    @Override
    public String getDescription() {
      return "Controlled condition";
    }

    /** @return no physical target for this branch-only unit test */
    @Override
    public ProcessEquipmentInterface getTargetEquipment() {
      return null;
    }

    /** @return current boolean text */
    @Override
    public String getCurrentValue() {
      return Boolean.toString(value);
    }

    /** @return expected boolean text */
    @Override
    public String getExpectedValue() {
      return "true";
    }
  }

  /**
   * Captures logic and its native target in one graph to test alias preservation.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class Bundle implements Serializable {
    private static final long serialVersionUID = 1L;
    private final ESDValve valve = new ESDValve("ESDV-101");
    private final ESDLogic logic = new ESDLogic("ESD-101");

    /** Builds the shared action-to-valve reference. */
    private Bundle() {
      logic.addAction(new TripValveAction(valve), 0.0);
    }
  }

  /**
   * Intentionally unsupported custom action payload.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class NonSerializableAction implements LogicAction {
    /** Does nothing. */
    @Override
    public void execute() {
      // Intentionally stateless; serialization support is still explicit.
    }

    /** @return description */
    @Override
    public String getDescription() {
      return "Unsupported custom payload";
    }

    /** @return true */
    @Override
    public boolean isComplete() {
      return true;
    }

    /** @return target tag */
    @Override
    public String getTargetName() {
      return "CUSTOM";
    }
  }
}
