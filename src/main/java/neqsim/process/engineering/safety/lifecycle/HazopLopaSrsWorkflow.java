package neqsim.process.engineering.safety.lifecycle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.safety.lifecycle.SafetyRequirementSpecificationDraft.TripDirection;
import neqsim.process.safety.risk.sis.LOPAResult;

/** Converts controlled HAZOP/LOPA inputs into credited-layer evidence and an unapproved draft SRS. */
public final class HazopLopaSrsWorkflow {
  private HazopLopaSrsWorkflow() {
  }

  /** Process and lifecycle values needed to populate the draft SRS if LOPA identifies a gap. */
  public static final class SrsDesignInputs implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String srsRequirementId;
    private final String sifTag;
    private final String lopaReference;
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

    private SrsDesignInputs(Builder builder) {
      srsRequirementId = requireText(builder.srsRequirementId, "srsRequirementId");
      sifTag = requireText(builder.sifTag, "sifTag");
      lopaReference = requireText(builder.lopaReference, "lopaReference");
      processVariable = requireText(builder.processVariable, "processVariable");
      tripDirection = builder.tripDirection;
      tripSetpoint = finite(builder.tripSetpoint, "tripSetpoint");
      tripSetpointUnit = requireText(builder.tripSetpointUnit, "tripSetpointUnit");
      safeState = requireText(builder.safeState, "safeState");
      maximumResponseTimeSeconds = positive(builder.maximumResponseTimeSeconds,
          "maximumResponseTimeSeconds");
      votingArchitecture = requireText(builder.votingArchitecture, "votingArchitecture");
      proofTestIntervalHours = positive(builder.proofTestIntervalHours, "proofTestIntervalHours");
      resetPolicy = requireText(builder.resetPolicy, "resetPolicy");
      bypassPolicy = requireText(builder.bypassPolicy, "bypassPolicy");
    }

    public static Builder builder(String srsRequirementId, String sifTag, String lopaReference) {
      return new Builder(srsRequirementId, sifTag, lopaReference);
    }

    String getSrsRequirementId() {
      return srsRequirementId;
    }

    String getSifTag() {
      return sifTag;
    }

    String getLopaReference() {
      return lopaReference;
    }

    String getProcessVariable() {
      return processVariable;
    }

    TripDirection getTripDirection() {
      return tripDirection;
    }

    double getTripSetpoint() {
      return tripSetpoint;
    }

    String getTripSetpointUnit() {
      return tripSetpointUnit;
    }

    String getSafeState() {
      return safeState;
    }

    double getMaximumResponseTimeSeconds() {
      return maximumResponseTimeSeconds;
    }

    String getVotingArchitecture() {
      return votingArchitecture;
    }

    double getProofTestIntervalHours() {
      return proofTestIntervalHours;
    }

    String getResetPolicy() {
      return resetPolicy;
    }

    String getBypassPolicy() {
      return bypassPolicy;
    }

    /** Builder for draft-SRS design inputs. */
    public static final class Builder {
      private final String srsRequirementId;
      private final String sifTag;
      private final String lopaReference;
      private String processVariable;
      private TripDirection tripDirection;
      private double tripSetpoint = Double.NaN;
      private String tripSetpointUnit;
      private String safeState;
      private double maximumResponseTimeSeconds;
      private String votingArchitecture;
      private double proofTestIntervalHours;
      private String resetPolicy;
      private String bypassPolicy;

      private Builder(String srsRequirementId, String sifTag, String lopaReference) {
        this.srsRequirementId = srsRequirementId;
        this.sifTag = sifTag;
        this.lopaReference = lopaReference;
      }

      public Builder trip(String variable, TripDirection direction, double setpoint, String unit) {
        if (direction == null) {
          throw new IllegalArgumentException("tripDirection must not be null");
        }
        processVariable = variable;
        tripDirection = direction;
        tripSetpoint = setpoint;
        tripSetpointUnit = unit;
        return this;
      }

      public Builder safeState(String value) {
        safeState = value;
        return this;
      }

      public Builder maximumResponseTimeSeconds(double value) {
        maximumResponseTimeSeconds = value;
        return this;
      }

      public Builder votingArchitecture(String value) {
        votingArchitecture = value;
        return this;
      }

      public Builder proofTestIntervalHours(double value) {
        proofTestIntervalHours = value;
        return this;
      }

      public Builder resetPolicy(String value) {
        resetPolicy = value;
        return this;
      }

      public Builder bypassPolicy(String value) {
        bypassPolicy = value;
        return this;
      }

      public SrsDesignInputs build() {
        return new SrsDesignInputs(this);
      }
    }
  }

  /** Immutable LOPA and draft-SRS handoff result. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final LopaScenarioDefinition scenario;
    private final LOPAResult lopaResult;
    private final List<Map<String, Object>> layerAssessments;
    private final List<String> findings;
    private final SafetyRequirementSpecificationDraft srsDraft;

    private Result(LopaScenarioDefinition scenario, LOPAResult lopaResult,
        List<Map<String, Object>> layerAssessments, List<String> findings,
        SafetyRequirementSpecificationDraft srsDraft) {
      this.scenario = scenario;
      this.lopaResult = lopaResult;
      this.layerAssessments = Collections
          .unmodifiableList(new ArrayList<Map<String, Object>>(layerAssessments));
      this.findings = Collections.unmodifiableList(new ArrayList<String>(findings));
      this.srsDraft = srsDraft;
    }

    public LOPAResult getLopaResult() {
      return lopaResult;
    }

    public SafetyRequirementSpecificationDraft getSrsDraft() {
      return srsDraft;
    }

    public List<String> getFindings() {
      return findings;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("schemaVersion", "hazop_lopa_srs_handoff.v1");
      map.put("scenario", scenario.toMap());
      map.put("lopa", lopaResult.toMap());
      map.put("layerAssessments", layerAssessments);
      map.put("findings", new ArrayList<String>(findings));
      map.put("srsDraft", srsDraft == null ? null : srsDraft.toMap());
      map.put("srsDraftCreated", Boolean.valueOf(srsDraft != null));
      map.put("hazopWorkshopApprovalRequired", Boolean.TRUE);
      map.put("lopaApprovalRequired", Boolean.TRUE);
      map.put("srsApprovalRequired", Boolean.TRUE);
      map.put("silTargetInferredFromProcessSimulation", Boolean.FALSE);
      map.put("fitForConstruction", Boolean.FALSE);
      return map;
    }
  }

  /**
   * Runs the credit eligibility screen and LOPA arithmetic, then creates a draft SRS only when a risk gap remains.
   *
   * @param scenario controlled HAZOP/LOPA scenario inputs
   * @param srsInputs proposed SIF functional requirements
   * @return traceable lifecycle handoff
   */
  public static Result run(LopaScenarioDefinition scenario, SrsDesignInputs srsInputs) {
    if (scenario == null || srsInputs == null) {
      throw new IllegalArgumentException("scenario and srsInputs are required");
    }
    LOPAResult lopa = new LOPAResult(scenario.getScenarioId());
    lopa.setInitiatingEventFrequency(scenario.getInitiatingEventFrequencyPerYear());
    lopa.setTargetFrequency(scenario.getTargetFrequencyPerYear());
    double frequency = scenario.getInitiatingEventFrequencyPerYear();
    List<Map<String, Object>> layerAssessments = new ArrayList<Map<String, Object>>();
    List<String> findings = new ArrayList<String>();
    for (ProtectionLayerDefinition layer : scenario.getProtectionLayers()) {
      Map<String, Object> assessment = new LinkedHashMap<String, Object>(layer.toMap());
      assessment.put("frequencyBeforePerYear", Double.valueOf(frequency));
      if (layer.isCreditEligible()) {
        double after = frequency * layer.getProbabilityOfFailureOnDemand();
        lopa.addLayer(layer.getName(), layer.getProbabilityOfFailureOnDemand(), frequency, after);
        frequency = after;
        assessment.put("credited", Boolean.TRUE);
        assessment.put("frequencyAfterPerYear", Double.valueOf(after));
      } else {
        assessment.put("credited", Boolean.FALSE);
        assessment.put("frequencyAfterPerYear", Double.valueOf(frequency));
        for (String reason : layer.getCreditFindings()) {
          findings.add(layer.getId() + ": " + reason);
        }
      }
      layerAssessments.add(assessment);
    }
    lopa.setMitigatedFrequency(frequency);
    SafetyRequirementSpecificationDraft srsDraft = null;
    if (!lopa.isTargetMet()) {
      srsDraft = new SafetyRequirementSpecificationDraft(srsInputs.getSrsRequirementId(), srsInputs.getSifTag(),
          scenario, srsInputs, lopa.getRequiredAdditionalSIL(), lopa.getRequiredAdditionalRRF());
      findings.add("LOPA gap requires accountable review of the generated draft SRS and SIL target");
    } else {
      findings.add("Supplied credited layers meet the target; no additional SIF draft was generated");
    }
    return new Result(scenario, lopa, layerAssessments, findings, srsDraft);
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static double finite(double value, String field) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be finite");
    }
    return value;
  }

  private static double positive(double value, String field) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(field + " must be finite and positive");
    }
    return value;
  }
}
