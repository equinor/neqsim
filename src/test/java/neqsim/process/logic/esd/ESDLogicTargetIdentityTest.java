package neqsim.process.logic.esd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.LogicAction;
import neqsim.process.logic.action.CloseValveAction;
import neqsim.process.logic.action.ConditionalAction;
import neqsim.process.logic.action.OpenValveAction;
import neqsim.process.logic.action.ParallelActionGroup;
import neqsim.process.logic.action.TripValveAction;

/**
 * Checks actual configured action targets independently of declared tag strings.
 *
 * @author ESOL
 * @version 1.0
 */
class ESDLogicTargetIdentityTest {
  /** Preserves reference identity and order while deduplicating repeated operations on one valve. */
  @Test
  void nativeTargetsDeduplicateByObjectReference() {
    ESDValve first = new ESDValve("ESDV-A");
    ESDValve second = new ESDValve("ESDV-B");
    ESDLogic logic = new ESDLogic("ESD");
    logic.addAction(new CloseValveAction(first), 0.0);
    logic.addAction(new OpenValveAction(first), 1.0);
    logic.addAction(new TripValveAction(second), 0.0);
    assertEquals(2, logic.getTargetEquipment().size());
    assertSame(first, logic.getTargetEquipment().get(0));
    assertSame(second, logic.getTargetEquipment().get(1));
    assertNull(logic.getTargetEquipmentCoverageIssue());
    assertTrue(first.isEnergized());
    assertEquals(100.0, first.getPercentValveOpening(), 0.0);
  }

  /** Equal tag strings do not silently merge two distinct physical objects. */
  @Test
  void equalNamesDoNotReplaceActualIdentity() {
    ESDValve first = new ESDValve("DUPLICATE");
    ESDValve second = new ESDValve("DUPLICATE");
    ESDLogic logic = new ESDLogic("ESD");
    logic.addAction(new TripValveAction(first), 0.0);
    logic.addAction(new TripValveAction(second), 0.0);
    assertEquals(2, logic.getTargetEquipment().size());
    assertSame(first, logic.getTargetEquipment().get(0));
    assertSame(second, logic.getTargetEquipment().get(1));
  }

  /** An opaque action cannot acquire a physical binding merely by returning an existing tag. */
  @Test
  void declaredNameDoesNotInventAReference() {
    ESDValve valve = new ESDValve("ESDV-A");
    ESDLogic logic = new ESDLogic("ESD");
    logic.addAction(new TripValveAction(valve), 0.0);
    logic.addAction(new NamedOnlyAction(), 0.0);
    assertEquals(1, logic.getTargetEquipment().size());
    assertSame(valve, logic.getTargetEquipment().get(0));
    assertNotNull(logic.getTargetEquipmentCoverageIssue());
    assertTrue(logic.getTargetEquipmentCoverageIssue().contains("action 1"));
  }

  /** Includes both possible conditional branches without executing either branch. */
  @Test
  void nestedConditionalAndParallelTargetsStayActual() {
    ESDValve first = new ESDValve("ESDV-A");
    ESDValve second = new ESDValve("ESDV-B");
    ParallelActionGroup group = new ParallelActionGroup("Group");
    group.addAction(new ConditionalAction(null, new TripValveAction(first), new TripValveAction(second), "Branches"));
    group.addAction(new TripValveAction(first));
    ESDLogic logic = new ESDLogic("ESD");
    logic.addAction(group, 0.0);
    assertEquals(2, logic.getTargetEquipment().size());
    assertSame(first, logic.getTargetEquipment().get(0));
    assertSame(second, logic.getTargetEquipment().get(1));
    assertNull(logic.getTargetEquipmentCoverageIssue());
    assertTrue(first.isEnergized());
    assertTrue(second.isEnergized());
  }

  /** Surfaces a missing native target instead of claiming an empty fully bound action. */
  @Test
  void missingNativeTargetIsExplicitlyIncomplete() {
    ESDLogic logic = new ESDLogic("ESD");
    logic.addAction(new TripValveAction(null), 0.0);
    assertTrue(logic.getTargetEquipment().isEmpty());
    assertNotNull(logic.getTargetEquipmentCoverageIssue());
  }

  /**
   * Deliberately opaque custom action with a misleading declared equipment name.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class NamedOnlyAction implements LogicAction {
    /** Does not operate equipment. */
    @Override
    public void execute() {
      // The target identity remains unknown regardless of this action's tag claim.
    }

    /** @return description */
    @Override
    public String getDescription() {
      return "Opaque operation";
    }

    /** @return true */
    @Override
    public boolean isComplete() {
      return true;
    }

    /** @return a declared tag which must not be heuristically bound */
    @Override
    public String getTargetName() {
      return "ESDV-A";
    }
  }
}
