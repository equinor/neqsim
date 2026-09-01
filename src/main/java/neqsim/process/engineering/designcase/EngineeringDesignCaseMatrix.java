package neqsim.process.engineering.designcase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;

/** Controlled case-by-metric execution, coverage and limit-assessment matrix. */
public final class EngineeringDesignCaseMatrix implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final List<EngineeringDesignCase> designCases;
  private final List<EngineeringMetric> metrics;
  private final EngineeringDesignEnvelope envelope;

  public EngineeringDesignCaseMatrix(List<EngineeringDesignCase> designCases, List<EngineeringMetric> metrics,
      EngineeringDesignEnvelope envelope) {
    if (designCases == null || metrics == null) {
      throw new IllegalArgumentException("designCases and metrics are required");
    }
    this.designCases = new ArrayList<EngineeringDesignCase>(designCases);
    this.metrics = new ArrayList<EngineeringMetric>(metrics);
    this.envelope = envelope;
  }

  public Map<String, Object> toMap(String projectId, String revision) {
    Map<String, Object> document = new LinkedHashMap<String, Object>();
    document.put("schemaVersion", EngineeringSchemaCatalog.DESIGN_CASE_MATRIX);
    document.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.DESIGN_CASE_MATRIX));
    document.put("projectId", requireText(projectId, "projectId"));
    document.put("revision", requireText(revision, "revision"));
    List<Map<String, Object>> caseMaps = new ArrayList<Map<String, Object>>();
    Map<String, EngineeringDesignCase> casesById = new LinkedHashMap<String, EngineeringDesignCase>();
    for (EngineeringDesignCase designCase : designCases) {
      caseMaps.add(designCase.toMap());
      casesById.put(designCase.getId(), designCase);
    }
    document.put("cases", caseMaps);
    List<Map<String, Object>> metricMaps = new ArrayList<Map<String, Object>>();
    for (EngineeringMetric metric : metrics) {
      metricMaps.add(metric.toMap());
    }
    document.put("metrics", metricMaps);
    List<Map<String, Object>> executions = new ArrayList<Map<String, Object>>();
    int requiredCaseFailureCount = 0;
    int metricFailureCount = 0;
    if (envelope != null) {
      for (DesignCaseResult result : envelope.getCaseResults()) {
        executions.add(result.toMap());
        EngineeringDesignCase designCase = casesById.get(result.getDesignCase().getId());
        if (designCase != null && designCase.isRequired() && !"CALCULATED".equals(result.getStatus())) {
          requiredCaseFailureCount++;
        }
        for (DesignCaseResult.MetricResult metric : result.getMetricResults().values()) {
          if ("FAILED".equals(metric.getStatus())) {
            metricFailureCount++;
          }
        }
      }
    }
    document.put("executions", executions);
    document.put("governingValues",
        envelope == null ? new ArrayList<Map<String, Object>>() : envelope.toMap().get("governingValues"));
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("configuredCaseCount", Integer.valueOf(designCases.size()));
    summary.put("metricCount", Integer.valueOf(metrics.size()));
    summary.put("successfulCaseCount", Integer.valueOf(envelope == null ? 0 : envelope.getSuccessfulCaseCount()));
    summary.put("partialCaseCount", Integer.valueOf(envelope == null ? 0 : envelope.getPartialCaseCount()));
    summary.put("failedCaseCount", Integer.valueOf(envelope == null ? 0 : envelope.getFailedCaseCount()));
    summary.put("skippedCaseCount", Integer.valueOf(envelope == null ? 0 : envelope.getSkippedCaseCount()));
    summary.put("requiredCaseFailureCount", Integer.valueOf(requiredCaseFailureCount));
    summary.put("metricFailureCount", Integer.valueOf(metricFailureCount));
    summary.put("limitViolationCount", Integer.valueOf(envelope == null ? 0 : envelope.getLimitViolationCount()));
    document.put("summary", summary);
    return document;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
