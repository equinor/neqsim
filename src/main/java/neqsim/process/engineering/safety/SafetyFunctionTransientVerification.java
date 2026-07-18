package neqsim.process.engineering.safety;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Verification of an ESD or HIPPS response against a NeqSim dynamic pressure trace. */
public final class SafetyFunctionTransientVerification implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String functionId;
  private final double tripSetPressureBara;
  private final double maximumAllowablePressureBara;
  private final double requiredClosureTimeSeconds;

  public SafetyFunctionTransientVerification(String functionId, double tripSetPressureBara,
      double maximumAllowablePressureBara, double requiredClosureTimeSeconds) {
    this.functionId = functionId;
    this.tripSetPressureBara = tripSetPressureBara;
    this.maximumAllowablePressureBara = maximumAllowablePressureBara;
    this.requiredClosureTimeSeconds = requiredClosureTimeSeconds;
  }

  public Map<String, Object> verify(double[] timeSeconds, double[] pressureBara,
      double[] valveOpeningPercent) {
    if (timeSeconds == null || pressureBara == null || valveOpeningPercent == null
        || timeSeconds.length == 0 || timeSeconds.length != pressureBara.length
        || timeSeconds.length != valveOpeningPercent.length) {
      throw new IllegalArgumentException("time, pressure and valve-opening traces must have equal non-zero length");
    }
    double tripTime = Double.NaN;
    double closedTime = Double.NaN;
    double maximumPressure = pressureBara[0];
    for (int i = 0; i < timeSeconds.length; i++) {
      maximumPressure = Math.max(maximumPressure, pressureBara[i]);
      if (Double.isNaN(tripTime) && pressureBara[i] >= tripSetPressureBara) {
        tripTime = timeSeconds[i];
      }
      if (!Double.isNaN(tripTime) && Double.isNaN(closedTime) && valveOpeningPercent[i] <= 1.0) {
        closedTime = timeSeconds[i];
      }
    }
    double responseTime = Double.isNaN(closedTime) || Double.isNaN(tripTime)
        ? Double.POSITIVE_INFINITY : closedTime - tripTime;
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_safety_transient_verification.v1");
    result.put("functionId", functionId);
    result.put("tripSetPressureBara", Double.valueOf(tripSetPressureBara));
    result.put("maximumAllowablePressureBara", Double.valueOf(maximumAllowablePressureBara));
    result.put("maximumSimulatedPressureBara", Double.valueOf(maximumPressure));
    result.put("requiredClosureTimeSeconds", Double.valueOf(requiredClosureTimeSeconds));
    result.put("actualClosureTimeSeconds", Double.valueOf(responseTime));
    result.put("tripDetected", Boolean.valueOf(!Double.isNaN(tripTime)));
    result.put("pressureCriterionPassed", Boolean.valueOf(maximumPressure <= maximumAllowablePressureBara));
    result.put("closureCriterionPassed", Boolean.valueOf(responseTime <= requiredClosureTimeSeconds));
    result.put("passed", Boolean.valueOf(maximumPressure <= maximumAllowablePressureBara
        && responseTime <= requiredClosureTimeSeconds));
    result.put("status", "DYNAMIC_MODEL_VERIFICATION_REVIEW_REQUIRED");
    result.put("standardReferences", java.util.Arrays.asList("IEC 61511", "NORSOK S-001", "API 521"));
    return result;
  }
}
