package neqsim.process.safety.sif;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.scenario.DynamicSafetyScenarioEvidenceProvider;

/**
 * Closed-loop sensor, voting, logic-solver, and final-element execution for dynamic SIF verification.
 *
 * <p>The loop is deliberately bound to the isolated {@link ProcessSystem} copy created by the dynamic scenario runner.
 * It can therefore verify process detection and real final-element response without modifying the design case.</p>
 */
public final class ClosedLoopSafetyFunction
    implements ProcessLogic, DynamicSafetyScenarioEvidenceProvider {
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

  @Override
  public LogicState getState() {
    return state;
  }

  @Override
  public void activate() {
    if (state == LogicState.IDLE || state == LogicState.COMPLETED || state == LogicState.FAILED) {
      clearExecutionState();
      state = LogicState.RUNNING;
    }
  }

  @Override
  public void deactivate() {
    if (state == LogicState.RUNNING) {
      state = LogicState.PAUSED;
      if (finalElementLogic.isActive()) {
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
      if (finalElementActuationSeconds == null
          && voteElapsedSeconds + 1.0e-12 >= logicSolverDelaySeconds) {
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
    result.put("finalElementState", finalElementLogic.getState().name());
    result.put("trace", new ArrayList<Map<String, Object>>(trace));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  private void clearExecutionState() {
    elapsedSeconds = 0.0;
    voteElapsedSeconds = 0.0;
    firstVoteSeconds = null;
    finalElementActuationSeconds = null;
    trace.clear();
    for (SafetyFunctionChannel channel : channels) {
      channel.reset();
    }
    finalElementLogic.reset();
  }

  /** Builder for a closed-loop safety function. */
  public static final class Builder {
    private final String sifId;
    private final String name;
    private final ProcessSystem process;
    private final VotingPattern votingPattern;
    private final ProcessLogic finalElementLogic;
    private final List<SafetyFunctionChannel> channels = new ArrayList<SafetyFunctionChannel>();
    private double logicSolverDelaySeconds;

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

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static double nonNegative(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(field + " must be finite and non-negative");
    }
    return value;
  }
}
