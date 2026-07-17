package neqsim.process.engineering.designcase;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Result of running one design case on an isolated process copy. */
public final class DesignCaseResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** One metric evaluation, including its limit assessment or isolated failure. */
  public static final class MetricResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String metricId;
    private final String unit;
    private final String status;
    private final Double value;
    private final String limitStatus;
    private final String message;

    MetricResult(EngineeringMetric metric, Double value, String status, String limitStatus, String message) {
      this.metricId = metric.getId();
      this.unit = metric.getUnit();
      this.status = status;
      this.value = value;
      this.limitStatus = limitStatus;
      this.message = message;
    }

    public String getStatus() {
      return status;
    }

    public String getLimitStatus() {
      return limitStatus;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("metricId", metricId);
      result.put("unit", unit);
      result.put("status", status);
      if (value != null) {
        result.put("value", value);
      }
      result.put("limitStatus", limitStatus);
      result.put("message", message);
      return result;
    }
  }

  private final EngineeringDesignCase designCase;
  private final Map<String, Double> values = new LinkedHashMap<String, Double>();
  private final Map<String, MetricResult> metricResults = new LinkedHashMap<String, MetricResult>();
  private String status = "PENDING";
  private String message = "";

  DesignCaseResult(EngineeringDesignCase designCase) {
    this.designCase = designCase;
  }

  void addValue(EngineeringMetric metric, double value) {
    values.put(metric.getId(), Double.valueOf(value));
    metricResults.put(metric.getId(),
        new MetricResult(metric, Double.valueOf(value), "CALCULATED", metric.assess(value), ""));
  }

  void failMetric(EngineeringMetric metric, String failureMessage) {
    metricResults.put(metric.getId(),
        new MetricResult(metric, null, "FAILED", "NOT_EVALUATED", failureMessage == null ? "" : failureMessage));
  }

  void finish() {
    int failures = 0;
    for (MetricResult result : metricResults.values()) {
      if ("FAILED".equals(result.getStatus())) {
        failures++;
      }
    }
    if (failures == 0) {
      status = "CALCULATED";
    } else if (values.isEmpty()) {
      status = "FAILED";
      message = "All metric evaluations failed";
    } else {
      status = "CALCULATED_WITH_METRIC_FAILURES";
      message = failures + " metric evaluation(s) failed";
    }
  }

  void fail(String failureMessage) {
    status = "FAILED";
    message = failureMessage == null ? "" : failureMessage;
    values.clear();
    metricResults.clear();
  }

  void skip(String reason) {
    status = "SKIPPED";
    message = reason == null ? "" : reason;
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

  public Map<String, MetricResult> getMetricResults() {
    return Collections.unmodifiableMap(metricResults);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("case", designCase.toMap());
    result.put("status", status);
    result.put("message", message);
    result.put("values", new LinkedHashMap<String, Double>(values));
    Map<String, Object> metrics = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, MetricResult> entry : metricResults.entrySet()) {
      metrics.put(entry.getKey(), entry.getValue().toMap());
    }
    result.put("metricResults", metrics);
    return result;
  }
}
