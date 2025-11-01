package neqsim.process.util.report.safety;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Immutable value object containing all information gathered by the
 * {@link ProcessSafetyReportBuilder}.
 */
public final class ProcessSafetyReport {
  /** Condition monitoring findings. */
  private final List<ConditionFinding> conditionFindings;
  /** Calculated safety margins for equipment. */
  private final List<SafetyMarginAssessment> safetyMargins;
  /** Metrics for relief and safety valves. */
  private final List<ReliefDeviceAssessment> reliefDeviceAssessments;
  /** System level KPIs. */
  private final SystemKpiSnapshot systemKpis;
  /** Serialized process snapshot produced by {@link neqsim.process.util.report.Report}. */
  private final String equipmentSnapshotJson;
  /** Scenario label supplied by the caller. */
  private final String scenarioLabel;
  /** Thresholds used when grading severities (copied for traceability). */
  private final ProcessSafetyThresholds thresholds;

  ProcessSafetyReport(String scenarioLabel, ProcessSafetyThresholds thresholds,
      List<ConditionFinding> conditionFindings, List<SafetyMarginAssessment> safetyMargins,
      List<ReliefDeviceAssessment> reliefDeviceAssessments, SystemKpiSnapshot systemKpis,
      String equipmentSnapshotJson) {
    this.scenarioLabel = scenarioLabel;
    this.thresholds = thresholds == null ? new ProcessSafetyThresholds() : thresholds;
    this.conditionFindings = conditionFindings == null ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(conditionFindings));
    this.safetyMargins = safetyMargins == null ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(safetyMargins));
    this.reliefDeviceAssessments = reliefDeviceAssessments == null ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(reliefDeviceAssessments));
    this.systemKpis = systemKpis;
    this.equipmentSnapshotJson = equipmentSnapshotJson;
  }

  public String getScenarioLabel() {
    return scenarioLabel;
  }

  public ProcessSafetyThresholds getThresholds() {
    return thresholds;
  }

  public List<ConditionFinding> getConditionFindings() {
    return conditionFindings;
  }

  public List<SafetyMarginAssessment> getSafetyMargins() {
    return safetyMargins;
  }

  public List<ReliefDeviceAssessment> getReliefDeviceAssessments() {
    return reliefDeviceAssessments;
  }

  public SystemKpiSnapshot getSystemKpis() {
    return systemKpis;
  }

  public String getEquipmentSnapshotJson() {
    return equipmentSnapshotJson;
  }

  /**
   * Serialize the report to JSON. The structure is intentionally friendly for dashboards and audit
   * archiving.
   *
   * @return JSON representation of the report
   */
  public String toJson() {
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    JsonObject root = new JsonObject();
    if (scenarioLabel != null) {
      root.addProperty("scenario", scenarioLabel);
    }
    root.add("thresholds", gson.toJsonTree(thresholds));
    root.add("systemKpis", gson.toJsonTree(systemKpis));
    root.add("conditionFindings", gson.toJsonTree(conditionFindings));
    root.add("safetyMargins", gson.toJsonTree(safetyMargins));
    root.add("reliefDevices", gson.toJsonTree(reliefDeviceAssessments));
    if (equipmentSnapshotJson != null && !equipmentSnapshotJson.trim().isEmpty()) {
      try {
        JsonElement parsed = JsonParser.parseString(equipmentSnapshotJson);
        root.add("equipment", parsed);
      } catch (Exception parseException) {
        root.addProperty("equipment", equipmentSnapshotJson);
      }
    }
    return gson.toJson(root);
  }

  /**
   * Produce a CSV representation of the key findings. The CSV is organized with a single header
   * allowing it to be imported into spreadsheets.
   *
   * @return CSV formatted summary
   */
  public String toCsv() {
    StringBuilder sb = new StringBuilder();
    sb.append("Category,Name,Metric,Value,Severity,Details\n");
    for (ConditionFinding finding : conditionFindings) {
      appendCsvRow(sb, "ConditionMonitor", finding.getUnitName(), "Message", "",
          finding.getSeverity(), finding.getMessage());
    }
    for (SafetyMarginAssessment margin : safetyMargins) {
      appendCsvRow(sb, "SafetyMargin", margin.getUnitName(), "Margin",
          formatDouble(margin.getMarginFraction()), margin.getSeverity(),
          String.format(Locale.ROOT, "design=%.3f bara, operating=%.3f bara",
              margin.getDesignPressureBar(), margin.getOperatingPressureBar()));
    }
    for (ReliefDeviceAssessment relief : reliefDeviceAssessments) {
      appendCsvRow(sb, "ReliefDevice", relief.getUnitName(), "Utilisation",
          formatDouble(relief.getUtilisationFraction()), relief.getSeverity(),
          String.format(Locale.ROOT,
              "set=%.3f bara, relieving=%.3f bara, upstream=%.3f bara, massFlow=%.3f kg/hr",
              relief.getSetPressureBar(), relief.getRelievingPressureBar(),
              relief.getUpstreamPressureBar(), relief.getMassFlowRateKgPerHr()));
    }
    if (systemKpis != null) {
      appendCsvRow(sb, "SystemKpi", scenarioLabel != null ? scenarioLabel : "process",
          "EntropyChange", formatDouble(systemKpis.getEntropyChangeKjPerK()),
          systemKpis.getEntropySeverity(), "kJ/K");
      appendCsvRow(sb, "SystemKpi", scenarioLabel != null ? scenarioLabel : "process",
          "ExergyChange", formatDouble(systemKpis.getExergyChangeKj()),
          systemKpis.getExergySeverity(), "kJ");
    }
    return sb.toString();
  }

  private static void appendCsvRow(StringBuilder sb, String category, String name, String metric,
      String value, SeverityLevel severity, String details) {
    sb.append(escapeCsv(category)).append(',').append(escapeCsv(name)).append(',')
        .append(escapeCsv(metric)).append(',').append(escapeCsv(value)).append(',')
        .append(severity == null ? "" : severity.name()).append(',')
        .append(escapeCsv(details)).append('\n');
  }

  private static String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    if (trimmed.contains(",") || trimmed.contains("\"") || trimmed.contains("\n")) {
      return '"' + trimmed.replace("\"", "\"\"") + '"';
    }
    return trimmed;
  }

  private static String formatDouble(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return "";
    }
    return String.format(Locale.ROOT, "%.4f", value);
  }

  /**
   * Produce a map structure that can easily be consumed by dashboards or REST APIs.
   *
   * @return map representation of the report
   */
  public Map<String, Object> toUiModel() {
    Map<String, Object> model = new LinkedHashMap<>();
    if (scenarioLabel != null) {
      model.put("scenario", scenarioLabel);
    }
    model.put("thresholds", thresholds);
    model.put("systemKpis", systemKpis);
    model.put("conditionFindings", conditionFindings);
    model.put("safetyMargins", safetyMargins);
    model.put("reliefDevices", reliefDeviceAssessments);
    if (equipmentSnapshotJson != null && !equipmentSnapshotJson.trim().isEmpty()) {
      model.put("equipmentJson", equipmentSnapshotJson);
    }
    return model;
  }

  /** Represents a single condition monitoring finding. */
  public static final class ConditionFinding {
    private final String unitName;
    private final String message;
    private final SeverityLevel severity;

    public ConditionFinding(String unitName, String message, SeverityLevel severity) {
      this.unitName = unitName;
      this.message = message;
      this.severity = severity;
    }

    public String getUnitName() {
      return unitName;
    }

    public String getMessage() {
      return message;
    }

    public SeverityLevel getSeverity() {
      return severity;
    }
  }

  /** Captures the pressure margin for an equipment item. */
  public static final class SafetyMarginAssessment {
    private final String unitName;
    private final double designPressureBar;
    private final double operatingPressureBar;
    private final double marginFraction;
    private final SeverityLevel severity;
    private final String notes;

    public SafetyMarginAssessment(String unitName, double designPressureBar,
        double operatingPressureBar, double marginFraction, SeverityLevel severity, String notes) {
      this.unitName = unitName;
      this.designPressureBar = designPressureBar;
      this.operatingPressureBar = operatingPressureBar;
      this.marginFraction = marginFraction;
      this.severity = severity;
      this.notes = notes;
    }

    public String getUnitName() {
      return unitName;
    }

    public double getDesignPressureBar() {
      return designPressureBar;
    }

    public double getOperatingPressureBar() {
      return operatingPressureBar;
    }

    public double getMarginFraction() {
      return marginFraction;
    }

    public SeverityLevel getSeverity() {
      return severity;
    }

    public String getNotes() {
      return notes;
    }
  }

  /** Summary of a relief valve evaluation. */
  public static final class ReliefDeviceAssessment {
    private final String unitName;
    private final double setPressureBar;
    private final double relievingPressureBar;
    private final double upstreamPressureBar;
    private final double massFlowRateKgPerHr;
    private final double utilisationFraction;
    private final SeverityLevel severity;

    public ReliefDeviceAssessment(String unitName, double setPressureBar, double relievingPressureBar,
        double upstreamPressureBar, double massFlowRateKgPerHr, double utilisationFraction,
        SeverityLevel severity) {
      this.unitName = unitName;
      this.setPressureBar = setPressureBar;
      this.relievingPressureBar = relievingPressureBar;
      this.upstreamPressureBar = upstreamPressureBar;
      this.massFlowRateKgPerHr = massFlowRateKgPerHr;
      this.utilisationFraction = utilisationFraction;
      this.severity = severity;
    }

    public String getUnitName() {
      return unitName;
    }

    public double getSetPressureBar() {
      return setPressureBar;
    }

    public double getRelievingPressureBar() {
      return relievingPressureBar;
    }

    public double getUpstreamPressureBar() {
      return upstreamPressureBar;
    }

    public double getMassFlowRateKgPerHr() {
      return massFlowRateKgPerHr;
    }

    public double getUtilisationFraction() {
      return utilisationFraction;
    }

    public SeverityLevel getSeverity() {
      return severity;
    }
  }

  /** Snapshot of aggregated system KPIs. */
  public static final class SystemKpiSnapshot {
    private final double entropyChangeKjPerK;
    private final double exergyChangeKj;
    private final SeverityLevel entropySeverity;
    private final SeverityLevel exergySeverity;

    public SystemKpiSnapshot(double entropyChangeKjPerK, double exergyChangeKj,
        SeverityLevel entropySeverity, SeverityLevel exergySeverity) {
      this.entropyChangeKjPerK = entropyChangeKjPerK;
      this.exergyChangeKj = exergyChangeKj;
      this.entropySeverity = entropySeverity;
      this.exergySeverity = exergySeverity;
    }

    public double getEntropyChangeKjPerK() {
      return entropyChangeKjPerK;
    }

    public double getExergyChangeKj() {
      return exergyChangeKj;
    }

    public SeverityLevel getEntropySeverity() {
      return entropySeverity;
    }

    public SeverityLevel getExergySeverity() {
      return exergySeverity;
    }
  }
}
