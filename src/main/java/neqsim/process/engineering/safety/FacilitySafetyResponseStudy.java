package neqsim.process.engineering.safety;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareResult;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;

/**
 * Review handoff joining dynamic protection, compressor-trip, disposal-system, and process-limit evidence.
 *
 * <p>
 * This class does not replace any underlying simulation. It preserves the complete structured results from the dynamic
 * safety-scenario and coupled relief/blowdown/flare calculations, then reports cross-system findings and evidence gaps
 * for engineering review.
 * </p>
 */
public final class FacilitySafetyResponseStudy implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Named result from an independently executed protection scenario. */
  private static final class ProtectionResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final DynamicSafetyScenarioResult result;

    private ProtectionResult(String name, DynamicSafetyScenarioResult result) {
      this.name = requireText(name, "protection result name");
      if (result == null) {
        throw new IllegalArgumentException("dynamic protection result is required");
      }
      this.result = result;
    }
  }

  private final String studyId;
  private final boolean scenarioSelectionReviewed;
  private final List<String> evidenceReferences;
  private final List<ProtectionResult> protectionResults;
  private final CoupledReliefBlowdownFlareResult disposalResult;
  private final List<CompressorTripResponse> compressorTripResponses;
  private final List<ProcessSafetyConstraint> constraints;

  private FacilitySafetyResponseStudy(Builder builder) {
    studyId = requireText(builder.studyId, "studyId");
    scenarioSelectionReviewed = builder.scenarioSelectionReviewed;
    evidenceReferences = Collections.unmodifiableList(new ArrayList<String>(builder.evidenceReferences));
    protectionResults = Collections.unmodifiableList(new ArrayList<ProtectionResult>(builder.protectionResults));
    disposalResult = builder.disposalResult;
    compressorTripResponses = Collections
        .unmodifiableList(new ArrayList<CompressorTripResponse>(builder.compressorTripResponses));
    constraints = Collections.unmodifiableList(new ArrayList<ProcessSafetyConstraint>(builder.constraints));
    if (protectionResults.isEmpty()) {
      throw new IllegalStateException("at least one dynamic protection result is required");
    }
    if (disposalResult == null) {
      throw new IllegalStateException("coupled relief/blowdown/flare result is required");
    }
    if (compressorTripResponses.isEmpty()) {
      throw new IllegalStateException("at least one compressor-trip response is required");
    }
    if (constraints.isEmpty()) {
      throw new IllegalStateException("at least one process safety constraint is required");
    }
  }

  /** @param studyId controlled facility-response study identifier @return study builder */
  public static Builder builder(String studyId) {
    return new Builder(studyId);
  }

  /** @return true only when every modeled response and required process limit is acceptable */
  public boolean isTechnicallyAcceptable() {
    for (ProtectionResult protection : protectionResults) {
      if (!protection.result.isPassed()) {
        return false;
      }
    }
    if (!disposalResult.isCapacityAcceptable()) {
      return false;
    }
    for (CompressorTripResponse response : compressorTripResponses) {
      if (!response.isPassed()) {
        return false;
      }
    }
    for (ProcessSafetyConstraint constraint : constraints) {
      if (constraint.isRequired() && !constraint.isMet()) {
        return false;
      }
    }
    return true;
  }

  /** @return true when scenario review and all controlled evidence references are present */
  public boolean isEvidenceComplete() {
    if (!scenarioSelectionReviewed || evidenceReferences.isEmpty()) {
      return false;
    }
    for (CompressorTripResponse response : compressorTripResponses) {
      if (!response.isEvidenceComplete()) {
        return false;
      }
    }
    for (ProcessSafetyConstraint constraint : constraints) {
      if (!constraint.isEvidenceComplete()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Indicates that the package may enter accountable engineering review.
   *
   * @return technical and evidence gate; never an engineering approval
   */
  public boolean isReadyForEngineeringReview() {
    return isTechnicallyAcceptable() && isEvidenceComplete();
  }

  /** @return explicit technical and evidence findings */
  public List<String> getFindings() {
    List<String> findings = new ArrayList<String>();
    for (ProtectionResult protection : protectionResults) {
      if (!protection.result.isPassed()) {
        findings.add("Dynamic protection scenario failed: " + protection.name);
      }
    }
    if (!disposalResult.isCapacityAcceptable()) {
      findings.add("Coupled relief/blowdown/flare capacity is not acceptable");
    }
    for (CompressorTripResponse response : compressorTripResponses) {
      if (!response.isPassed()) {
        findings.add("Compressor-trip response failed: " + response.getCompressorTag());
      }
      if (!response.isEvidenceComplete()) {
        findings.add("Compressor-trip evidence is missing: " + response.getCompressorTag());
      }
    }
    for (ProcessSafetyConstraint constraint : constraints) {
      if (constraint.isRequired() && !constraint.isMet()) {
        findings.add("Required process safety constraint failed: " + constraint.getId());
      }
      if (!constraint.isEvidenceComplete()) {
        findings.add("Constraint evidence is missing: " + constraint.getId());
      }
    }
    if (!scenarioSelectionReviewed) {
      findings.add("Scenario selection has not been reviewed");
    }
    if (evidenceReferences.isEmpty()) {
      findings.add("Facility-level evidence references are missing");
    }
    findings.add("Accountable engineering approval remains required");
    return Collections.unmodifiableList(findings);
  }

  /** @return structured review package retaining the underlying engine outputs */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "facility_safety_response_study.v1");
    result.put("studyId", studyId);
    result.put("scenarioSelectionReviewed", Boolean.valueOf(scenarioSelectionReviewed));
    result.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    List<Map<String, Object>> protectionMaps = new ArrayList<Map<String, Object>>();
    for (ProtectionResult protection : protectionResults) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("name", protection.name);
      item.put("result", protection.result.toMap());
      protectionMaps.add(item);
    }
    result.put("dynamicProtectionResults", protectionMaps);
    result.put("coupledReliefBlowdownFlareResult", disposalResult.toMap());
    List<Map<String, Object>> compressorMaps = new ArrayList<Map<String, Object>>();
    for (CompressorTripResponse response : compressorTripResponses) {
      compressorMaps.add(response.toMap());
    }
    result.put("compressorTripResponses", compressorMaps);
    List<Map<String, Object>> constraintMaps = new ArrayList<Map<String, Object>>();
    for (ProcessSafetyConstraint constraint : constraints) {
      constraintMaps.add(constraint.toMap());
    }
    result.put("processSafetyConstraints", constraintMaps);
    result.put("technicallyAcceptable", Boolean.valueOf(isTechnicallyAcceptable()));
    result.put("evidenceComplete", Boolean.valueOf(isEvidenceComplete()));
    result.put("readyForEngineeringReview", Boolean.valueOf(isReadyForEngineeringReview()));
    result.put("findings", new ArrayList<String>(getFindings()));
    result.put("silTargetInferred", Boolean.FALSE);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  /** @return pretty-printed JSON review package */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }

  /** Builder for a facility safety-response review package. */
  public static final class Builder {
    private final String studyId;
    private boolean scenarioSelectionReviewed;
    private final List<String> evidenceReferences = new ArrayList<String>();
    private final List<ProtectionResult> protectionResults = new ArrayList<ProtectionResult>();
    private CoupledReliefBlowdownFlareResult disposalResult;
    private final List<CompressorTripResponse> compressorTripResponses = new ArrayList<CompressorTripResponse>();
    private final List<ProcessSafetyConstraint> constraints = new ArrayList<ProcessSafetyConstraint>();

    private Builder(String studyId) {
      this.studyId = studyId;
    }

    /** @param value true after accountable scenario-selection review @return this builder */
    public Builder scenarioSelectionReviewed(boolean value) {
      scenarioSelectionReviewed = value;
      return this;
    }

    /** @param value controlled facility-level source reference @return this builder */
    public Builder addEvidenceReference(String value) {
      String normalized = value == null ? "" : value.trim();
      if (!normalized.isEmpty() && !evidenceReferences.contains(normalized)) {
        evidenceReferences.add(normalized);
      }
      return this;
    }

    /** @param name scenario name @param value executed dynamic result @return this builder */
    public Builder addProtectionResult(String name, DynamicSafetyScenarioResult value) {
      protectionResults.add(new ProtectionResult(name, value));
      return this;
    }

    /** @param value executed coupled relief/blowdown/flare result @return this builder */
    public Builder disposalResult(CoupledReliefBlowdownFlareResult value) {
      disposalResult = value;
      return this;
    }

    /** @param value captured compressor-trip response @return this builder */
    public Builder addCompressorTripResponse(CompressorTripResponse value) {
      if (value == null) {
        throw new IllegalArgumentException("compressor-trip response is required");
      }
      compressorTripResponses.add(value);
      return this;
    }

    /** @param value controlled process-response constraint @return this builder */
    public Builder addConstraint(ProcessSafetyConstraint value) {
      if (value == null) {
        throw new IllegalArgumentException("process safety constraint is required");
      }
      constraints.add(value);
      return this;
    }

    /** @return validated facility response study */
    public FacilitySafetyResponseStudy build() {
      return new FacilitySafetyResponseStudy(this);
    }
  }

  private static String requireText(String value, String field) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return normalized;
  }
}
