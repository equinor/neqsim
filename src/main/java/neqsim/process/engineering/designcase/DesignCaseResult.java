package neqsim.process.engineering.designcase;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Result of running one design case on an isolated process copy. */
public final class DesignCaseResult implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final EngineeringDesignCase designCase;
  private final Map<String, Double> values = new LinkedHashMap<String, Double>();
  private String status = "CALCULATED";
  private String message = "";

  DesignCaseResult(EngineeringDesignCase designCase) {
    this.designCase = designCase;
  }

  void addValue(String metricId, double value) {
    values.put(metricId, Double.valueOf(value));
  }

  void fail(String failureMessage) {
    status = "FAILED";
    message = failureMessage == null ? "" : failureMessage;
    values.clear();
  }

  public EngineeringDesignCase getDesignCase() {
    return designCase;
  }

  public String getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public Map<String, Double> getValues() {
    return Collections.unmodifiableMap(values);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("case", designCase.toMap());
    result.put("status", status);
    result.put("message", message);
    result.put("values", new LinkedHashMap<String, Double>(values));
    return result;
  }
}
