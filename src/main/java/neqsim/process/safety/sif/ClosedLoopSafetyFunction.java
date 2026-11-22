package neqsim.process.safety.sif;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.scenario.DynamicSafetyScenarioEvidenceProvider;

/**
 * Closed-loop sensor, voting, logic-solver, and final-element execution for dynamic SIF verification.
 *
 * <p>
 * The loop is deliberately bound to the isolated {@link ProcessSystem} copy created by the dynamic scenario runner. It
 * can therefore verify process detection and real final-element response without modifying the design case.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ClosedLoopSafetyFunction implements ProcessLogic, DynamicSafetyScenarioEvidenceProvider {
  private static final long serialVersionUID = 1000L;

  private final String sifId;
  private final String name;
  private final ProcessSystem process;
  private final VotingPattern votingPattern;
  private final double logicSolverDelaySeconds;
  private final ProcessLogic finalElementLogic;
  private final List<SafetyFunctionChannel> channels;
  private final List<Map<String, Object>> trace = new ArrayList<Map<String, Object>>();
  private LogicState state = LogicState.IDLE;
  private double elapsedSeconds;
  private double voteElapsedSeconds;
  private Double firstVoteSeconds;
  private Double finalElementActuationSeconds;
  private boolean finalElementPausedWithLoop;

  /**
   * Validates cardinality and distinct configured input identities before any channel can be sampled.
   *
   * @param builder complete loop configuration
   */
  private ClosedLoopSafetyFunction(Builder builder) {
    sifId = requireText(builder.sifId, "sifId");
    name = requireText(builder.name, "name");
    process = builder.process;
    votingPattern = builder.votingPattern;
    logicSolverDelaySeconds = nonNegative(builder.logicSolverDelaySeconds, "logicSolverDelaySeconds");
    finalElementLogic = builder.finalElementLogic;
    channels = new ArrayList<SafetyFunctionChannel>(builder.channels);
    if (channels.size() != votingPattern.getTotalSensors()) {
      throw new IllegalArgumentException("Voting pattern " + votingPattern + " requires "
          + votingPattern.getTotalSensors() + " channels but " + channels.size() + " were supplied");
    }
    Set<SafetyFunctionChannel> identities = Collections
        .newSetFromMap(new IdentityHashMap<SafetyFunctionChannel, Boolean>());
    Set<String> tags = new HashSet<String>();
    for (SafetyFunctionChannel channel : channels) {
      if (!identities.add(channel)) {
        throw new IllegalArgumentException("Voting inputs must be distinct channel objects");
      }
      if (!tags.add(channel.getTag())) {
        throw new IllegalArgumentException("Voting inputs must have distinct channel tags: " + channel.getTag());
      }
    }
  }

  /**
   * Starts a closed-loop SIF definition.
   *
   * @param sifId controlled SIF identifier
   * @param name descriptive name
   * @param process isolated process model used by the scenario
   * @param votingPattern sensor voting pattern
   * @param finalElementLogic ESD, HIPPS, or other final-element logic
   * @return SIF builder
   */
  public static Builder builder(String sifId, String name, ProcessSystem process, VotingPattern votingPattern,
      ProcessLogic finalElementLogic) {
    return new Builder(sifId, name, process, votingPattern, finalElementLogic);
  }

  @Override
  public String getName() {
    return name;
  }

  /** @return controlled SIF identifier */
  public String getSifId() {
    return sifId;
  }

  /**
   * Returns the actual configured channel identities without allowing membership edits.
   *
   * @return unmodifiable snapshot containing the actual channel objects
   */
  public List<SafetyFunctionChannel> getChannels() {
    return Collections.unmodifiableList(new ArrayList<SafetyFunctionChannel>(channels));
  }

  /** @return actual configured final-element logic, for identity/coverage inspection */
  public ProcessLogic getFinalElementLogic() {
    return finalElementLogic;
  }

  @Override
  public LogicState getState() {
    return state;
  }

  @Override
  public void activate() {
    if (state == LogicState.PAUSED) {
      state = LogicState.RUNNING;
      if (finalElementPausedWithLoop && finalElementLogic.getState() == LogicState.PAUSED) {
        finalElementLogic.activate();
      }
      finalElementPausedWithLoop = false;
      return;
    }
    if (state == LogicState.IDLE || state == LogicState.COMPLETED || state == LogicState.FAILED) {
      clearExecutionState();
      finalElementLogic.reset();
      state = LogicState.RUNNING;
    }
  }

  @Override
  public void deactivate() {
    if (state == LogicState.RUNNING) {
      state = LogicState.PAUSED;
      finalElementPausedWithLoop = finalElementLogic.isActive();
      if (finalElementPausedWithLoop) {
        finalElementLogic.deactivate();
      }
    }
  }

  @Override
  public boolean reset() {
    clearExecutionState();
    state = LogicState.IDLE;
    return finalElementLogic.reset();
  }

  @Override
  public void execute(double timeStep) {
    if (state != LogicState.RUNNING) {
      return;
    }
    if (!Double.isFinite(timeStep) || timeStep <= 0.0) {
      throw new IllegalArgumentException("timeStep must be finite and positive");
    }
    elapsedSeconds += timeStep;
    int trippedCount = 0;
    int availableCount = 0;
    List<Map<String, Object>> channelEvidence = new ArrayList<Map<String, Object>>();
    for (SafetyFunctionChannel channel : channels) {
      SafetyFunctionChannel.Sample sample = channel.sample(process, timeStep);
      if (sample.isTripped()) {
        trippedCount++;
      }
      if (sample.isAvailable()) {
        availableCount++;
      }
      channelEvidence.add(sample.toMap());
    }

    boolean vote = votingPattern.evaluate(trippedCount);
    if (vote) {
      if (firstVoteSeconds == null) {
        firstVoteSeconds = Double.valueOf(elapsedSeconds);
        voteElapsedSeconds = 0.0;
      } else {
        voteElapsedSeconds += timeStep;
      }
      if (finalElementActuationSeconds == null && voteElapsedSeconds + 1.0e-12 >= logicSolverDelaySeconds) {
        finalElementLogic.activate();
        finalElementActuationSeconds = Double.valueOf(elapsedSeconds);
      }
    }
    if (finalElementLogic.isActive()) {
      finalElementLogic.execute(timeStep);
    }
    if (finalElementLogic.isComplete()) {
      state = LogicState.COMPLETED;
    }

    Map<String, Object> loopSample = new LinkedHashMap<String, Object>();
    loopSample.put("timeSeconds", Double.valueOf(elapsedSeconds));
    loopSample.put("trippedChannelCount", Integer.valueOf(trippedCount));
    loopSample.put("availableChannelCount", Integer.valueOf(availableCount));
    loopSample.put("vote", Boolean.valueOf(vote));
    loopSample.put("channels", channelEvidence);
    loopSample.put("finalElementState", finalElementLogic.getState().name());
    trace.add(loopSample);
  }

  @Override
  public boolean isActive() {
    return state == LogicState.RUNNING;
  }

  @Override
  public boolean isComplete() {
    return state == LogicState.COMPLETED;
  }

  @Override
  public List<ProcessEquipmentInterface> getTargetEquipment() {
    return finalElementLogic.getTargetEquipment();
  }

  @Override
  public String getStatusDescription() {
    if (firstVoteSeconds == null) {
      return name + " - " + state.name() + " (monitoring " + votingPattern + ")";
    }
    return name + " - " + state.name() + " (voted at " + firstVoteSeconds + " s; final element "
        + finalElementLogic.getState().name() + ")";
  }

  @Override
  public Map<String, Object> getDynamicSafetyScenarioEvidence() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "closed_loop_sif_evidence.v1");
    result.put("sifId", sifId);
    result.put("name", name);
    result.put("votingPattern", votingPattern.getNotation());
    result.put("logicSolverDelaySeconds", Double.valueOf(logicSolverDelaySeconds));
    List<Map<String, Object>> channelConfiguration = new ArrayList<Map<String, Object>>();
    for (SafetyFunctionChannel channel : channels) {
      channelConfiguration.add(channel.configurationMap());
    }
    result.put("channelConfiguration", channelConfiguration);
    result.put("firstVoteSeconds", firstVoteSeconds);
    result.put("finalElementActuationSeconds", finalElementActuationSeconds);
    result.put("finalElementLogic", finalElementLogic.getName());
    result.put("finalElementLogicClass", finalElementLogic.getClass().getName());
    if (finalElementLogic.getClass() == ESDLogic.class) {
      result.put("finalElementActionConfiguration", ((ESDLogic) finalElementLogic).getTripActionConfiguration());
      result.put("finalElementTargetCoverageIssue", ((ESDLogic) finalElementLogic).getTargetEquipmentCoverageIssue());
    } else {
      Map<String, Object> unsupported = new LinkedHashMap<String, Object>();
      unsupported.put("supportedTripPolicy", Boolean.FALSE);
      unsupported.put("reason", "Final-element logic has no reviewed exact native trip-action policy");
      result.put("finalElementActionConfiguration", unsupported);
    }
    result.put("finalElementState", finalElementLogic.getState().name());
    List<Map<String, Object>> targets = new ArrayList<Map<String, Object>>();
    for (ProcessEquipmentInterface target : finalElementLogic.getTargetEquipment()) {
      Map<String, Object> identity = new LinkedHashMap<String, Object>();
      identity.put("name", target.getName());
      identity.put("class", target.getClass().getName());
      targets.add(identity);
    }
    result.put("finalElementTargets", targets);
    result.put("trace", new ArrayList<Map<String, Object>>(trace));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  /** Clears a new execution cycle; resuming a paused cycle deliberately does not call this method. */
  private void clearExecutionState() {
    elapsedSeconds = 0.0;
    voteElapsedSeconds = 0.0;
    firstVoteSeconds = null;
    finalElementActuationSeconds = null;
    finalElementPausedWithLoop = false;
    trace.clear();
    for (SafetyFunctionChannel channel : channels) {
      channel.reset();
    }
  }

  /**
   * Builder for a closed-loop safety function.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Builder {
    private final String sifId;
    private final String name;
    private final ProcessSystem process;
    private final VotingPattern votingPattern;
    private final ProcessLogic finalElementLogic;
    private final List<SafetyFunctionChannel> channels = new ArrayList<SafetyFunctionChannel>();
    private double logicSolverDelaySeconds;

    /**
     * Creates an unbuilt function bound to an isolated process copy.
     *
     * @param sifId controlled function identity
     * @param name function name
     * @param process actual isolated process
     * @param votingPattern declared fixed voting pattern
     * @param finalElementLogic actual native final-element logic
     */
    private Builder(String sifId, String name, ProcessSystem process, VotingPattern votingPattern,
        ProcessLogic finalElementLogic) {
      if (process == null || votingPattern == null || finalElementLogic == null) {
        throw new IllegalArgumentException("process, votingPattern, and finalElementLogic are required");
      }
      this.sifId = sifId;
      this.name = name;
      this.process = process;
      this.votingPattern = votingPattern;
      this.finalElementLogic = finalElementLogic;
    }

    /** @param value sensor channel @return this builder */
    public Builder addChannel(SafetyFunctionChannel value) {
      if (value == null) {
        throw new IllegalArgumentException("channel must not be null");
      }
      channels.add(value);
      return this;
    }

    /** @param value logic solver delay in seconds @return this builder */
    public Builder logicSolverDelaySeconds(double value) {
      logicSolverDelaySeconds = value;
      return this;
    }

    /** @return validated closed-loop SIF */
    public ClosedLoopSafetyFunction build() {
      return new ClosedLoopSafetyFunction(this);
    }
  }

  /**
   * Validates normalized configuration text.
   *
   * @param value configured text
   * @param field configuration field name
   * @return trimmed nonempty value
   */
  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  /**
   * Validates a finite nonnegative duration.
   *
   * @param value configured duration
   * @param field configuration field name
   * @return validated value
   */
  private static double nonNegative(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(field + " must be finite and non-negative");
    }
    return value;
  }
}
