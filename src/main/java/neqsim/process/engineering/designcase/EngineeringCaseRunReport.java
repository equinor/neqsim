package neqsim.process.engineering.designcase;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic report from an isolated multi-case engineering simulation. */
public final class EngineeringCaseRunReport implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String caseSetId;
  private final String definitionFingerprint;
  private final String resultFingerprint;
  private final EngineeringDesignEnvelope envelope;

  EngineeringCaseRunReport(String caseSetId, String definitionFingerprint, String resultFingerprint,
      EngineeringDesignEnvelope envelope) {
    this.caseSetId = caseSetId;
    this.definitionFingerprint = definitionFingerprint;
    this.resultFingerprint = resultFingerprint;
    this.envelope = envelope;
  }

  public String getCaseSetId() {
    return caseSetId;
  }

  public String getDefinitionFingerprint() {
    return definitionFingerprint;
  }

  public String getResultFingerprint() {
    return resultFingerprint;
  }

  public EngineeringDesignEnvelope getEnvelope() {
    return envelope;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "engineering_case_run.v1");
    result.put("caseSetId", caseSetId);
    result.put("definitionFingerprint", definitionFingerprint);
    result.put("resultFingerprint", resultFingerprint);
    result.put("envelope", envelope.toMap());
    result.put("isolatedProcessCopies", Boolean.TRUE);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }
}
