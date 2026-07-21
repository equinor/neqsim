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

  /** @return whether every configured metric has a governing value and no case failed */
  public boolean isComplete() {
    return envelope.isComplete();
  }

  /** @return whether the complete envelope is assessed and within every configured limit */
  public boolean isAccepted() {
    return envelope.isAccepted();
  }

  /**
   * Require a complete governing envelope.
   *
   * @return this report when complete
   * @throws EngineeringCaseExecutionException when incomplete; the exception retains this report
   */
  public EngineeringCaseRunReport requireComplete() {
    if (!isComplete()) {
      throw new EngineeringCaseExecutionException(
          "Engineering case set " + caseSetId + " did not produce a complete governing envelope", this);
    }
    return this;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "engineering_case_run.v1");
    result.put("caseSetId", caseSetId);
    result.put("definitionFingerprint", definitionFingerprint);
    result.put("resultFingerprint", resultFingerprint);
    result.put("envelope", envelope.toMap());
    result.put("complete", Boolean.valueOf(isComplete()));
    result.put("accepted", Boolean.valueOf(isAccepted()));
    result.put("isolatedProcessCopies", Boolean.TRUE);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }
}
