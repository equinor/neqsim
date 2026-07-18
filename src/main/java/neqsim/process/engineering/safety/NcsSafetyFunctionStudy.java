package neqsim.process.engineering.safety;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.risk.sis.SILVerificationResult;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;

/** Coordinated NCS handoff for preliminary ESD/HIPPS SIL and dynamic-response verification. */
public final class NcsSafetyFunctionStudy implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String projectId;
  private final List<Map<String, Object>> functions = new ArrayList<Map<String, Object>>();

  public NcsSafetyFunctionStudy(String projectId) {
    if (projectId == null || projectId.trim().isEmpty()) {
      throw new IllegalArgumentException("projectId must not be blank");
    }
    this.projectId = projectId.trim();
  }

  public NcsSafetyFunctionStudy add(SafetyInstrumentedFunction function,
      Map<String, Object> transientVerification) {
    if (function == null || transientVerification == null) {
      throw new IllegalArgumentException("function and transient verification must not be null");
    }
    SILVerificationResult sil = new SILVerificationResult(function);
    Map<String, Object> entry = new LinkedHashMap<String, Object>();
    entry.put("safetyInstrumentedFunction", function.toMap());
    entry.put("silVerification", sil.toMap());
    entry.put("transientVerification", new LinkedHashMap<String, Object>(transientVerification));
    entry.put("approvalStatus", "REVIEW_REQUIRED");
    functions.add(entry);
    return this;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_ncs_safety_function_study.v1");
    result.put("projectId", projectId);
    result.put("functions", new ArrayList<Map<String, Object>>(functions));
    result.put("standards", java.util.Arrays.asList("NORSOK S-001", "NORSOK Z-013",
        "IEC 61508", "IEC 61511", "ISO 10418", "API 521"));
    result.put("qualificationStatus", "PRELIMINARY_SRS_AND_SIL_VERIFICATION_HANDOFF");
    result.put("fitnessForConstruction", Boolean.FALSE);
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }
}
