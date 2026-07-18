package neqsim.process.engineering.safety.lifecycle;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.engineering.EngineeringApprovalStatus;

/** Review-required draft SRS record generated from traceable, user-supplied LOPA inputs. */
public final class SafetyRequirementSpecificationDraft implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Direction of the trip from the process setpoint. */
  public enum TripDirection {
    HIGH, LOW
  }

  private final String srsRequirementId;
  private final String sifTag;
  private final String scenarioId;
  private final String hazopNodeId;
  private final String hazopDeviationId;
  private final String lopaReference;
  private final String equipmentTag;
  private final String initiatingEvent;
  private final String consequence;
  private final String frequencyBasisReference;
  private final String processVariable;
  private final TripDirection tripDirection;
  private final double tripSetpoint;
  private final String tripSetpointUnit;
  private final String safeState;
  private final double maximumResponseTimeSeconds;
  private final String votingArchitecture;
  private final double proofTestIntervalHours;
  private final String resetPolicy;
  private final String bypassPolicy;
  private final int requiredSil;
  private final double requiredRiskReductionFactor;

  SafetyRequirementSpecificationDraft(String srsRequirementId, String sifTag, LopaScenarioDefinition scenario,
      HazopLopaSrsWorkflow.SrsDesignInputs inputs, int requiredSil, double requiredRiskReductionFactor) {
    this.srsRequirementId = srsRequirementId;
    this.sifTag = sifTag;
    scenarioId = scenario.getScenarioId();
    hazopNodeId = scenario.getHazopNodeId();
    hazopDeviationId = scenario.getHazopDeviationId();
    lopaReference = inputs.getLopaReference();
    equipmentTag = scenario.getEquipmentTag();
    initiatingEvent = scenario.getInitiatingEvent();
    consequence = scenario.getConsequence();
    frequencyBasisReference = scenario.getFrequencyBasisReference();
    processVariable = inputs.getProcessVariable();
    tripDirection = inputs.getTripDirection();
    tripSetpoint = inputs.getTripSetpoint();
    tripSetpointUnit = inputs.getTripSetpointUnit();
    safeState = inputs.getSafeState();
    maximumResponseTimeSeconds = inputs.getMaximumResponseTimeSeconds();
    votingArchitecture = inputs.getVotingArchitecture();
    proofTestIntervalHours = inputs.getProofTestIntervalHours();
    resetPolicy = inputs.getResetPolicy();
    bypassPolicy = inputs.getBypassPolicy();
    this.requiredSil = requiredSil;
    this.requiredRiskReductionFactor = requiredRiskReductionFactor;
  }

  public String getSrsRequirementId() {
    return srsRequirementId;
  }

  public String getSifTag() {
    return sifTag;
  }

  public int getRequiredSil() {
    return requiredSil;
  }

  public EngineeringApprovalStatus getApprovalStatus() {
    return EngineeringApprovalStatus.REVIEW_REQUIRED;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", "safety_requirement_specification_draft.v1");
    map.put("draft", Boolean.TRUE);
    map.put("approvalStatus", EngineeringApprovalStatus.REVIEW_REQUIRED.name());
    map.put("srsRequirementId", srsRequirementId);
    map.put("sifTag", sifTag);
    map.put("scenarioId", scenarioId);
    map.put("hazopNodeId", hazopNodeId);
    map.put("hazopDeviationId", hazopDeviationId);
    map.put("lopaReference", lopaReference);
    map.put("equipmentTag", equipmentTag);
    map.put("initiatingEvent", initiatingEvent);
    map.put("consequence", consequence);
    map.put("frequencyBasisReference", frequencyBasisReference);
    map.put("processVariable", processVariable);
    map.put("tripDirection", tripDirection.name());
    map.put("tripSetpoint", Double.valueOf(tripSetpoint));
    map.put("tripSetpointUnit", tripSetpointUnit);
    map.put("safeState", safeState);
    map.put("maximumResponseTimeSeconds", Double.valueOf(maximumResponseTimeSeconds));
    map.put("votingArchitecture", votingArchitecture);
    map.put("proofTestIntervalHours", Double.valueOf(proofTestIntervalHours));
    map.put("resetPolicy", resetPolicy);
    map.put("bypassPolicy", bypassPolicy);
    map.put("requiredSil", Integer.valueOf(requiredSil));
    map.put("requiredRiskReductionFactor", Double.valueOf(requiredRiskReductionFactor));
    map.put("requiredMaximumPfd", Double.valueOf(1.0 / requiredRiskReductionFactor));
    map.put("silTargetSource", "CALCULATED_FROM_USER_SUPPLIED_LOPA_INPUTS");
    map.put("silTargetInferredFromProcessSimulation", Boolean.FALSE);
    map.put("engineeringApprovalRequired", Boolean.TRUE);
    return map;
  }
}
