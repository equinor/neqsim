package neqsim.process.safety.sif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Input identity and actual final-element evidence checks for a closed-loop safety function.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ClosedLoopSafetyFunctionIdentityTest {
  /** One mutable channel cannot manufacture two votes or advance its qualification timer twice. */
  @Test
  void duplicateChannelObjectCannotManufactureRedundancy() {
    SafetyFunctionChannel a = channel("PT-A");
    assertThrows(IllegalArgumentException.class,
        () -> builder().addChannel(a).addChannel(a).addChannel(channel("PT-C")).build());
  }

  /** Distinct channel objects with the same normalized tag do not represent distinct configured inputs. */
  @Test
  void duplicateNormalizedChannelTagIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> builder().addChannel(channel(" PT-A ")).addChannel(channel("PT-A")).addChannel(channel("PT-C")).build());
  }

  /** Native evidence names the actual final element returned by the configured native logic. */
  @Test
  void nativeEvidenceRetainsActualFinalElementIdentity() {
    ClosedLoopSafetyFunction function = builder().addChannel(channel("PT-A")).addChannel(channel("PT-B"))
        .addChannel(channel("PT-C")).build();
    Map<String, Object> evidence = function.getDynamicSafetyScenarioEvidence();
    Object targets = evidence.get("finalElementTargets");
    assertNotNull(targets);
    List<?> values = (List<?>) targets;
    assertEquals(1, values.size());
    Map<?, ?> target = (Map<?, ?>) values.get(0);
    assertEquals("XV-001", target.get("name"));
    assertEquals(ESDValve.class.getName(), target.get("class"));
  }

  /** Pause/reactivation must resume the same qualification timers rather than remain paused or restart them. */
  @Test
  void pausedFunctionResumesWithoutDiscardingQualifiedDemandTime() {
    ClosedLoopSafetyFunction.Builder b = builder().logicSolverDelaySeconds(0.3);
    for (String tag : new String[] {"PT-A", "PT-B", "PT-C"}) {
      b.addChannel(
          SafetyFunctionChannel.highTrip(tag, "bara", 41.0, process -> 42.0).responseDelaySeconds(0.3).build());
    }
    ClosedLoopSafetyFunction function = b.build();
    function.activate();
    function.execute(0.1);
    function.deactivate();
    function.execute(10.0);
    function.activate();
    assertEquals(LogicState.RUNNING, function.getState());
    function.execute(0.1);
    assertNull(function.getDynamicSafetyScenarioEvidence().get("firstVoteSeconds"));
    function.execute(0.1);
    assertEquals(0.3, ((Number) function.getDynamicSafetyScenarioEvidence().get("firstVoteSeconds")).doubleValue(),
        1.0e-12);
  }

  /** A pre-vote pause cannot activate the final element, and the exposed input list cannot be structurally edited. */
  @Test
  void noDemandPauseDoesNotInventActuationAndChannelsAreUnmodifiable() {
    RecordingLogic child = new RecordingLogic(new ESDValve("XV-001"));
    SafetyFunctionChannel input = channel("PT-A");
    ClosedLoopSafetyFunction function = ClosedLoopSafetyFunction
        .builder("SIF-001", "no demand", new ProcessSystem(), VotingPattern.ONE_OUT_OF_ONE, child).addChannel(input)
        .build();
    assertSame(input, function.getChannels().get(0));
    assertThrows(UnsupportedOperationException.class, () -> function.getChannels().clear());
    function.activate();
    function.execute(0.1);
    function.deactivate();
    function.activate();
    function.execute(0.1);
    assertEquals(0, child.activations);
    assertEquals(LogicState.IDLE, child.getState());
    assertNull(function.getDynamicSafetyScenarioEvidence().get("firstVoteSeconds"));
  }

  /** A paused actuated child resumes its progress; a failed child is not restarted by parent reactivation. */
  @Test
  void postActuationPausePreservesChildProgressAndFailureState() {
    RecordingLogic child = new RecordingLogic(new ESDValve("XV-001"));
    ClosedLoopSafetyFunction function = ClosedLoopSafetyFunction
        .builder("SIF-001", "child progress", new ProcessSystem(), VotingPattern.ONE_OUT_OF_ONE, child)
        .addChannel(SafetyFunctionChannel.highTrip("PT-A", "bara", 41.0, process -> 42.0).build()).build();
    function.activate();
    function.execute(0.1);
    function.deactivate();
    function.activate();
    function.execute(0.1);
    assertEquals(0.2, child.executedSeconds, 1.0e-12);
    assertEquals(2, child.activations);
    child.state = LogicState.FAILED;
    function.deactivate();
    function.activate();
    assertEquals(2, child.activations);
    assertEquals(LogicState.FAILED, child.state);
  }

  /** Parent reset invokes child bookkeeping reset once and exposes the actual child identity. */
  @Test
  void resetCallsTheActualChildOnce() {
    RecordingLogic child = new RecordingLogic(new ESDValve("XV-001"));
    ClosedLoopSafetyFunction function = ClosedLoopSafetyFunction
        .builder("SIF-001", "reset count", new ProcessSystem(), VotingPattern.ONE_OUT_OF_ONE, child)
        .addChannel(channel("PT-A")).build();
    assertSame(child, function.getFinalElementLogic());
    function.activate();
    assertEquals(1, child.resets);
    function.reset();
    assertEquals(2, child.resets);
  }

  /**
   * Builds a pure scalar channel for constructor-contract tests only, not a physical scenario.
   *
   * @param tag synthetic input identifier
   * @return independently allocated channel
   */
  private static SafetyFunctionChannel channel(String tag) {
    return SafetyFunctionChannel.highTrip(tag, "bara", 41.0, process -> 40.0).responseDelaySeconds(0.1).build();
  }

  /** @return unbuilt function whose final target is an actual native ESDValve object */
  private static ClosedLoopSafetyFunction.Builder builder() {
    return ClosedLoopSafetyFunction.builder("SIF-001", "identity contract", new ProcessSystem(),
        VotingPattern.TWO_OUT_OF_THREE, new RecordingLogic(new ESDValve("XV-001")));
  }

  /**
   * Pure logic unit-test observer; no physical response claim is made by these constructor checks.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class RecordingLogic implements ProcessLogic {
    private static final long serialVersionUID = 1L;
    private final ESDValve target;
    private LogicState state = LogicState.IDLE;
    private int activations;
    private int resets;
    private double executedSeconds;

    /**
     * Stores the actual target used in native evidence.
     *
     * @param target actual native valve object
     */
    private RecordingLogic(ESDValve target) {
      this.target = target;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
      return "recording final element";
    }

    /** {@inheritDoc} */
    @Override
    public LogicState getState() {
      return state;
    }

    /** {@inheritDoc} */
    @Override
    public void activate() {
      state = LogicState.RUNNING;
      activations++;
    }

    /** {@inheritDoc} */
    @Override
    public void deactivate() {
      state = LogicState.PAUSED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean reset() {
      state = LogicState.IDLE;
      executedSeconds = 0.0;
      resets++;
      return true;
    }

    /** {@inheritDoc} */
    @Override
    public void execute(double timeStep) {
      executedSeconds += timeStep;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isActive() {
      return state == LogicState.RUNNING;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isComplete() {
      return state == LogicState.COMPLETED;
    }

    /** {@inheritDoc} */
    @Override
    public List<ProcessEquipmentInterface> getTargetEquipment() {
      List<ProcessEquipmentInterface> result = new ArrayList<ProcessEquipmentInterface>();
      result.add(target);
      return result;
    }

    /** {@inheritDoc} */
    @Override
    public String getStatusDescription() {
      return state.name();
    }
  }
}
