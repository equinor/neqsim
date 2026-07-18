package neqsim.process.engineering.handover;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fail-closed assessment of a NeqSim staging package prepared for CFIHOS 2.0 transformation. */
public final class Cfihos20HandoverReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Readiness decision for the staging package. */
  public enum Status {
    READY_FOR_PRINCIPAL_TRANSFORMATION, INCOMPLETE
  }

  /** One actionable mapping or package finding. */
  public static final class Finding implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String severity;
    private final String code;
    private final String sourceId;
    private final String message;
    private final String requiredAction;

    Finding(String severity, String code, String sourceId, String message, String requiredAction) {
      this.severity = severity;
      this.code = code;
      this.sourceId = sourceId;
      this.message = message;
      this.requiredAction = requiredAction;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("severity", severity);
      result.put("code", code);
      result.put("sourceId", sourceId);
      result.put("message", message);
      result.put("requiredAction", requiredAction);
      return result;
    }
  }

  private final String projectId;
  private final String revision;
  private final Cfihos20ReferenceDataMapping referenceDataMapping;
  private final Map<String, Integer> recordCounts;
  private final List<Finding> findings;
  private final Map<String, String> fileDigests;
  private final Status status;

  Cfihos20HandoverReport(String projectId, String revision, Cfihos20ReferenceDataMapping referenceDataMapping,
      Map<String, Integer> recordCounts, List<Finding> findings, Map<String, String> fileDigests) {
    this.projectId = projectId;
    this.revision = revision;
    this.referenceDataMapping = referenceDataMapping;
    this.recordCounts = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(recordCounts));
    this.findings = Collections.unmodifiableList(new ArrayList<Finding>(findings));
    this.fileDigests = Collections.unmodifiableMap(new LinkedHashMap<String, String>(fileDigests));
    status = hasBlockers(findings) ? Status.INCOMPLETE : Status.READY_FOR_PRINCIPAL_TRANSFORMATION;
  }

  public Status getStatus() {
    return status;
  }

  public boolean isReadyForPrincipalTransformation() {
    return status == Status.READY_FOR_PRINCIPAL_TRANSFORMATION;
  }

  public List<Finding> getFindings() {
    return findings;
  }

  public Map<String, String> getFileDigests() {
    return fileDigests;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_cfihos_20_handover_assessment.v1");
    result.put("projectId", projectId);
    result.put("revision", revision);
    result.put("cfihosVersion", "2.0");
    result.put("status", status.name());
    result.put("readyForPrincipalTransformation", Boolean.valueOf(isReadyForPrincipalTransformation()));
    result.put("referenceDataMapping", referenceDataMapping.toMap());
    result.put("recordCounts", recordCounts);
    List<Map<String, Object>> findingMaps = new ArrayList<Map<String, Object>>();
    for (Finding finding : findings) {
      findingMaps.add(finding.toMap());
    }
    result.put("findings", findingMaps);
    result.put("fileDigests", fileDigests);
    result.put("cfihosConformanceClaim", Boolean.FALSE);
    result.put("principalAcceptanceRequired", Boolean.TRUE);
    result.put("targetSystemTransformationRequired", Boolean.TRUE);
    result.put("governance",
        "NeqSim prepares a controlled staging package; the Principal owns CFIHOS acceptance, contract requirements and target-system loading");
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private static boolean hasBlockers(List<Finding> findings) {
    for (Finding finding : findings) {
      if ("BLOCKER".equals(finding.severity)) {
        return true;
      }
    }
    return false;
  }
}
