package neqsim.process.envelope;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Structured data container for operator dashboard displays.
 *
 * <p>
 * The {@code EnvelopeDashboardData} converts an {@link AgentEvaluationResult} into a format
 * optimized for real-time operator interfaces. It includes equipment status cards, margin gauges,
 * trend indicators, and active advisory messages.
 * </p>
 *
 * <p>
 * The primary output is JSON suitable for consumption by web dashboards, SCADA HMI systems, or
 * other operator interface technologies. No external JSON library is required — serialization uses
 * built-in string formatting.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * AgentEvaluationResult result = agent.evaluate();
 * EnvelopeDashboardData dashboard = EnvelopeDashboardData.fromResult(result);
 *
 * String json = dashboard.toJson();
 * // Send to WebSocket, write to file, push to MQTT topic, etc.
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class EnvelopeDashboardData implements Serializable {
  private static final long serialVersionUID = 1L;

  private final long timestampMillis;
  private final int cycleNumber;
  private final String overallStatus;
  private final String overallStatusColor;
  private final String summaryMessage;
  private final List<EquipmentCard> equipmentCards;
  private final List<MarginGauge> marginGauges;
  private final List<Advisory> activeAdvisories;
  private final List<TripAlert> tripAlerts;

  /**
   * Creates dashboard data from an evaluation result.
   *
   * @param result the agent evaluation result
   * @return dashboard data object
   */
  public static EnvelopeDashboardData fromResult(AgentEvaluationResult result) {
    return new EnvelopeDashboardData(result);
  }

  /**
   * Creates dashboard data from result with margin trackers for sparklines.
   *
   * @param result the evaluation result
   * @param trackers map of margin key to tracker for trend data
   * @return dashboard data with trend sparklines
   */
  public static EnvelopeDashboardData fromResult(AgentEvaluationResult result,
      Map<String, MarginTracker> trackers) {
    EnvelopeDashboardData data = new EnvelopeDashboardData(result);
    // Enrich gauges with trend data from trackers
    for (MarginGauge gauge : data.marginGauges) {
      MarginTracker tracker = trackers.get(gauge.marginKey);
      if (tracker != null && tracker.isTrendValid()) {
        gauge.trendDirection = tracker.getTrendDirection().name();
        gauge.trendRatePerSecond = tracker.getMarginRateOfChange();
        gauge.sparklineValues = tracker.getMarginHistory();
        double ttb = tracker.getTimeToBreachMinutes();
        gauge.timeToBreachMinutes = Double.isInfinite(ttb) ? -1.0 : ttb;
      }
    }
    return data;
  }

  /**
   * Private constructor that transforms an evaluation result into dashboard format.
   *
   * @param result the evaluation result
   */
  private EnvelopeDashboardData(AgentEvaluationResult result) {
    this.timestampMillis = result.getTimestampMillis();
    this.cycleNumber = result.getEvaluationCycleNumber();
    this.overallStatus = result.getOverallStatus().name();
    this.overallStatusColor = statusToColor(result.getOverallStatus());
    this.summaryMessage = result.getSummaryMessage();

    this.equipmentCards = buildEquipmentCards(result);
    this.marginGauges = buildMarginGauges(result);
    this.activeAdvisories = buildAdvisories(result);
    this.tripAlerts = buildTripAlerts(result);
  }

  /**
   * Builds equipment status cards grouped by equipment name.
   *
   * @param result evaluation result
   * @return list of equipment cards
   */
  private List<EquipmentCard> buildEquipmentCards(AgentEvaluationResult result) {
    List<EquipmentCard> cards = new ArrayList<EquipmentCard>();
    // Group margins by equipment
    java.util.Map<String, List<OperatingMargin>> byEquipment =
        new java.util.LinkedHashMap<String, List<OperatingMargin>>();
    for (OperatingMargin m : result.getRankedMargins()) {
      String eq = m.getEquipmentName();
      if (!byEquipment.containsKey(eq)) {
        byEquipment.put(eq, new ArrayList<OperatingMargin>());
      }
      byEquipment.get(eq).add(m);
    }

    for (Map.Entry<String, List<OperatingMargin>> entry : byEquipment.entrySet()) {
      EquipmentCard card = new EquipmentCard();
      card.equipmentName = entry.getKey();
      card.marginCount = entry.getValue().size();

      // Worst margin determines equipment status
      OperatingMargin.Status worstStatus = OperatingMargin.Status.NORMAL;
      double worstMarginPercent = 100.0;
      for (OperatingMargin m : entry.getValue()) {
        if (m.getSeverityScore() > getStatusScore(worstStatus)) {
          worstStatus = m.getStatus();
        }
        if (m.getMarginPercent() < worstMarginPercent) {
          worstMarginPercent = m.getMarginPercent();
        }
      }
      card.status = worstStatus.name();
      card.statusColor = marginStatusToColor(worstStatus);
      card.worstMarginPercent = worstMarginPercent;
      cards.add(card);
    }
    return cards;
  }

  /**
   * Builds margin gauge data for all margins.
   *
   * @param result evaluation result
   * @return list of margin gauges
   */
  private List<MarginGauge> buildMarginGauges(AgentEvaluationResult result) {
    List<MarginGauge> gauges = new ArrayList<MarginGauge>();
    for (OperatingMargin m : result.getRankedMargins()) {
      MarginGauge gauge = new MarginGauge();
      gauge.marginKey = m.getKey();
      gauge.equipmentName = m.getEquipmentName();
      gauge.variableName = m.getVariableName();
      gauge.marginType = m.getMarginType().name();
      gauge.direction = m.getDirection().name();
      gauge.currentValue = m.getCurrentValue();
      gauge.limitValue = m.getLimitValue();
      gauge.marginPercent = m.getMarginPercent();
      gauge.status = m.getStatus().name();
      gauge.statusColor = marginStatusToColor(m.getStatus());
      gauge.unit = m.getUnit();
      gauge.trendDirection = "UNKNOWN";
      gauge.trendRatePerSecond = 0.0;
      gauge.timeToBreachMinutes = -1.0;
      gauge.sparklineValues = new ArrayList<Double>();
      gauges.add(gauge);
    }
    return gauges;
  }

  /**
   * Builds advisory messages from mitigation actions.
   *
   * @param result evaluation result
   * @return list of advisories
   */
  private List<Advisory> buildAdvisories(AgentEvaluationResult result) {
    List<Advisory> advisories = new ArrayList<Advisory>();
    for (MitigationAction action : result.getMitigationActions()) {
      Advisory adv = new Advisory();
      adv.priority = action.getPriority().name();
      adv.message = action.getDescription();
      adv.equipment = action.getTargetEquipment();
      adv.expectedImprovement = action.getExpectedImprovement();
      adv.confidence = action.getConfidenceLevel();
      advisories.add(adv);
    }
    return advisories;
  }

  /**
   * Builds trip alert data from trip predictions.
   *
   * @param result evaluation result
   * @return list of trip alerts
   */
  private List<TripAlert> buildTripAlerts(AgentEvaluationResult result) {
    List<TripAlert> alerts = new ArrayList<TripAlert>();
    for (TripPrediction pred : result.getTripPredictions()) {
      TripAlert alert = new TripAlert();
      alert.equipmentName = pred.getEquipmentName();
      alert.marginType = pred.getMarginType().name();
      alert.severity = pred.getSeverity().name();
      alert.severityColor = tripSeverityToColor(pred.getSeverity());
      alert.currentMarginPercent = pred.getCurrentMarginPercent();
      alert.timeToTripMinutes = pred.getEstimatedTimeToTripMinutes();
      alert.confidence = pred.getConfidence();
      alert.triggeringTrend = pred.getTriggeringTrend();
      alerts.add(alert);
    }
    return alerts;
  }

  /**
   * Exports the dashboard data as a JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"timestamp\": ").append(timestampMillis).append(",\n");
    sb.append("  \"cycleNumber\": ").append(cycleNumber).append(",\n");
    sb.append("  \"overallStatus\": \"").append(overallStatus).append("\",\n");
    sb.append("  \"overallStatusColor\": \"").append(overallStatusColor).append("\",\n");
    sb.append("  \"summary\": \"").append(escapeJson(summaryMessage)).append("\",\n");

    // Equipment cards
    sb.append("  \"equipmentCards\": [\n");
    for (int i = 0; i < equipmentCards.size(); i++) {
      sb.append(equipmentCards.get(i).toJson());
      if (i < equipmentCards.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ],\n");

    // Margin gauges
    sb.append("  \"marginGauges\": [\n");
    for (int i = 0; i < marginGauges.size(); i++) {
      sb.append(marginGauges.get(i).toJson());
      if (i < marginGauges.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ],\n");

    // Trip alerts
    sb.append("  \"tripAlerts\": [\n");
    for (int i = 0; i < tripAlerts.size(); i++) {
      sb.append(tripAlerts.get(i).toJson());
      if (i < tripAlerts.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ],\n");

    // Advisories
    sb.append("  \"advisories\": [\n");
    for (int i = 0; i < activeAdvisories.size(); i++) {
      sb.append(activeAdvisories.get(i).toJson());
      if (i < activeAdvisories.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");

    sb.append("}");
    return sb.toString();
  }

  /**
   * Returns the number of equipment cards.
   *
   * @return equipment count
   */
  public int getEquipmentCount() {
    return equipmentCards.size();
  }

  /**
   * Returns the number of active trip alerts.
   *
   * @return trip alert count
   */
  public int getTripAlertCount() {
    return tripAlerts.size();
  }

  /**
   * Returns the number of active advisories.
   *
   * @return advisory count
   */
  public int getAdvisoryCount() {
    return activeAdvisories.size();
  }

  /**
   * Returns the overall status string.
   *
   * @return status
   */
  public String getOverallStatus() {
    return overallStatus;
  }

  /**
   * Returns the overall status color.
   *
   * @return color hex code
   */
  public String getOverallStatusColor() {
    return overallStatusColor;
  }

  // ── Color mapping helpers ──

  /**
   * Maps envelope status to a color hex code.
   *
   * @param status the status
   * @return hex color string
   */
  private static String statusToColor(ProcessOperatingEnvelope.EnvelopeStatus status) {
    switch (status) {
      case NORMAL:
        return "#4CAF50";
      case NARROWING:
        return "#FF9800";
      case WARNING:
        return "#FF5722";
      case CRITICAL:
        return "#F44336";
      case VIOLATED:
        return "#B71C1C";
      default:
        return "#9E9E9E";
    }
  }

  /**
   * Maps margin status to a color hex code.
   *
   * @param status the status
   * @return hex color string
   */
  private static String marginStatusToColor(OperatingMargin.Status status) {
    switch (status) {
      case NORMAL:
        return "#4CAF50";
      case ADVISORY:
        return "#FFC107";
      case WARNING:
        return "#FF9800";
      case CRITICAL:
        return "#F44336";
      case VIOLATED:
        return "#B71C1C";
      default:
        return "#9E9E9E";
    }
  }

  /**
   * Maps trip severity to a color hex code.
   *
   * @param severity the severity
   * @return hex color string
   */
  private static String tripSeverityToColor(TripPrediction.Severity severity) {
    switch (severity) {
      case LOW:
        return "#FFC107";
      case MEDIUM:
        return "#FF9800";
      case HIGH:
        return "#F44336";
      case IMMINENT:
        return "#B71C1C";
      default:
        return "#9E9E9E";
    }
  }

  /**
   * Returns numeric severity score for margin status.
   *
   * @param status the status
   * @return score (0-4)
   */
  private static int getStatusScore(OperatingMargin.Status status) {
    switch (status) {
      case VIOLATED:
        return 4;
      case CRITICAL:
        return 3;
      case WARNING:
        return 2;
      case ADVISORY:
        return 1;
      default:
        return 0;
    }
  }

  /**
   * Escapes special characters for JSON.
   *
   * @param input string to escape
   * @return escaped string
   */
  private static String escapeJson(String input) {
    if (input == null) {
      return "";
    }
    return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r",
        "\\r");
  }

  // ── Inner data classes for dashboard components ──

  /**
   * Equipment status card for dashboard display.
   *
   * @author NeqSim
   * @version 1.0
   */
  static class EquipmentCard implements Serializable {
    private static final long serialVersionUID = 1L;
    String equipmentName;
    String status;
    String statusColor;
    int marginCount;
    double worstMarginPercent;

    /**
     * Serializes to JSON.
     *
     * @return JSON string
     */
    String toJson() {
      return String.format(
          "    {\"name\": \"%s\", \"status\": \"%s\", \"color\": \"%s\", "
              + "\"marginCount\": %d, \"worstMarginPct\": %.1f}",
          escapeJson(equipmentName), status, statusColor, marginCount, worstMarginPercent);
    }
  }

  /**
   * Margin gauge data for dashboard display.
   *
   * @author NeqSim
   * @version 1.0
   */
  static class MarginGauge implements Serializable {
    private static final long serialVersionUID = 1L;
    String marginKey;
    String equipmentName;
    String variableName;
    String marginType;
    String direction;
    double currentValue;
    double limitValue;
    double marginPercent;
    String status;
    String statusColor;
    String unit;
    String trendDirection;
    double trendRatePerSecond;
    double timeToBreachMinutes;
    List<Double> sparklineValues;

    /**
     * Serializes to JSON.
     *
     * @return JSON string
     */
    String toJson() {
      StringBuilder sp = new StringBuilder("[");
      if (sparklineValues != null) {
        for (int i = 0; i < sparklineValues.size(); i++) {
          sp.append(String.format("%.4f", sparklineValues.get(i)));
          if (i < sparklineValues.size() - 1) {
            sp.append(",");
          }
        }
      }
      sp.append("]");

      return String.format(
          "    {\"key\": \"%s\", \"equipment\": \"%s\", \"variable\": \"%s\", "
              + "\"type\": \"%s\", \"direction\": \"%s\", \"current\": %.4f, "
              + "\"limit\": %.4f, \"marginPct\": %.2f, \"status\": \"%s\", "
              + "\"color\": \"%s\", \"unit\": \"%s\", \"trend\": \"%s\", "
              + "\"trendRate\": %.6f, \"ttbMin\": %.1f, \"sparkline\": %s}",
          escapeJson(marginKey), escapeJson(equipmentName), escapeJson(variableName), marginType,
          direction, currentValue, limitValue, marginPercent, status, statusColor, escapeJson(unit),
          trendDirection, trendRatePerSecond, timeToBreachMinutes, sp.toString());
    }
  }

  /**
   * Advisory message for dashboard display.
   *
   * @author NeqSim
   * @version 1.0
   */
  static class Advisory implements Serializable {
    private static final long serialVersionUID = 1L;
    String priority;
    String message;
    String equipment;
    String expectedImprovement;
    double confidence;

    /**
     * Serializes to JSON.
     *
     * @return JSON string
     */
    String toJson() {
      return String.format(
          "    {\"priority\": \"%s\", \"message\": \"%s\", \"equipment\": \"%s\", "
              + "\"improvement\": \"%s\", \"confidence\": %.2f}",
          priority, escapeJson(message), escapeJson(equipment), escapeJson(expectedImprovement),
          confidence);
    }
  }

  /**
   * Trip alert for dashboard display.
   *
   * @author NeqSim
   * @version 1.0
   */
  static class TripAlert implements Serializable {
    private static final long serialVersionUID = 1L;
    String equipmentName;
    String marginType;
    String severity;
    String severityColor;
    double currentMarginPercent;
    double timeToTripMinutes;
    double confidence;
    String triggeringTrend;

    /**
     * Serializes to JSON.
     *
     * @return JSON string
     */
    String toJson() {
      return String.format(
          "    {\"equipment\": \"%s\", \"type\": \"%s\", \"severity\": \"%s\", "
              + "\"color\": \"%s\", \"marginPct\": %.1f, \"ttbMin\": %.1f, "
              + "\"confidence\": %.2f, \"trend\": \"%s\"}",
          escapeJson(equipmentName), marginType, severity, severityColor, currentMarginPercent,
          timeToTripMinutes, confidence, escapeJson(triggeringTrend));
    }
  }
}
