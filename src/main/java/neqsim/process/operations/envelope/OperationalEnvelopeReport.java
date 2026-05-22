package neqsim.process.operations.envelope;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Report from an operational envelope evaluation.
 *
 * @author ESOL
 * @version 1.0
 */
public final class OperationalEnvelopeReport implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Overall operating-envelope status. */
  public enum EnvelopeStatus {
    /** All margins are normal. */
    NORMAL(0),
    /** One or more margins are narrowing. */
    NARROWING(1),
    /** One or more margins are in warning. */
    WARNING(2),
    /** One or more margins are critical. */
    CRITICAL(3),
    /** One or more margins are violated. */
    VIOLATED(4);

    private final int rank;

    /**
     * Creates an envelope status.
     *
     * @param rank severity rank where higher values are more severe
     */
    EnvelopeStatus(int rank) {
      this.rank = rank;
    }

    /**
     * Returns the severity rank.
     *
     * @return severity rank
     */
    public int getRank() {
      return rank;
    }
  }

  private final long timestampMillis;
  private final double evaluationTimeSeconds;
  private final EnvelopeStatus overallStatus;
  private final List<OperationalMargin> margins;
  private final List<TripPrediction> tripPredictions;
  private final List<MitigationSuggestion> mitigationSuggestions;
  private final String summary;

  /**
   * Creates a report.
   *
   * @param timestampMillis evaluation timestamp in milliseconds
   * @param evaluationTimeSeconds wall-clock evaluation time in seconds
   * @param margins ranked operating margins
   * @param tripPredictions trip predictions
   * @param mitigationSuggestions mitigation suggestions
   */
  public OperationalEnvelopeReport(long timestampMillis, double evaluationTimeSeconds,
      List<OperationalMargin> margins, List<TripPrediction> tripPredictions,
      List<MitigationSuggestion> mitigationSuggestions) {
    this.timestampMillis = timestampMillis;
    this.evaluationTimeSeconds = evaluationTimeSeconds;
    this.margins = copyMargins(margins);
    this.tripPredictions = copyTrips(tripPredictions);
    this.mitigationSuggestions = copyMitigations(mitigationSuggestions);
    this.overallStatus = determineOverallStatus(this.margins);
    this.summary = buildSummary(overallStatus, this.margins, this.tripPredictions);
  }

  /**
   * Returns the timestamp.
   *
   * @return timestamp in milliseconds
   */
  public long getTimestampMillis() {
    return timestampMillis;
  }

  /**
   * Returns evaluation time.
   *
   * @return evaluation time in seconds
   */
  public double getEvaluationTimeSeconds() {
    return evaluationTimeSeconds;
  }

  /**
   * Returns the overall status.
   *
   * @return overall envelope status
   */
  public EnvelopeStatus getOverallStatus() {
    return overallStatus;
  }

  /**
   * Returns ranked margins.
   *
   * @return unmodifiable margin list
   */
  public List<OperationalMargin> getMargins() {
    return Collections.unmodifiableList(margins);
  }

  /**
   * Returns trip predictions.
   *
   * @return unmodifiable trip prediction list
   */
  public List<TripPrediction> getTripPredictions() {
    return Collections.unmodifiableList(tripPredictions);
  }

  /**
   * Returns mitigation suggestions.
   *
   * @return unmodifiable mitigation suggestion list
   */
  public List<MitigationSuggestion> getMitigationSuggestions() {
    return Collections.unmodifiableList(mitigationSuggestions);
  }

  /**
   * Returns the summary.
   *
   * @return report summary
   */
  public String getSummary() {
    return summary;
  }

  /**
   * Returns the count of margins at or above warning level.
   *
   * @return warning or worse margin count
   */
  public int getWarningOrWorseCount() {
    int count = 0;
    for (OperationalMargin margin : margins) {
      if (margin.getStatus().getRank() >= OperationalMargin.Status.WARNING.getRank()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Returns whether a high-urgency trip prediction exists.
   *
   * @return true when a high or imminent trip is predicted
   */
  public boolean hasHighUrgencyPrediction() {
    for (TripPrediction prediction : tripPredictions) {
      if (prediction.getSeverity().getRank() >= TripPrediction.Severity.HIGH.getRank()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Converts the report to JSON.
   *
   * @return JSON object representation
   */
  public JsonObject toJsonObject() {
    JsonObject json = new JsonObject();
    json.addProperty("timestampMillis", timestampMillis);
    json.addProperty("evaluationTimeSeconds", evaluationTimeSeconds);
    json.addProperty("overallStatus", overallStatus.name());
    json.addProperty("summary", summary);
    json.addProperty("marginCount", margins.size());
    json.addProperty("warningOrWorseCount", getWarningOrWorseCount());
    json.addProperty("highUrgencyPrediction", hasHighUrgencyPrediction());

    JsonArray marginArray = new JsonArray();
    for (OperationalMargin margin : margins) {
      marginArray.add(margin.toJsonObject());
    }
    json.add("rankedMargins", marginArray);

    JsonArray tripArray = new JsonArray();
    for (TripPrediction prediction : tripPredictions) {
      tripArray.add(prediction.toJsonObject());
    }
    json.add("tripPredictions", tripArray);

    JsonArray mitigationArray = new JsonArray();
    for (MitigationSuggestion suggestion : mitigationSuggestions) {
      mitigationArray.add(suggestion.toJsonObject());
    }
    json.add("mitigationSuggestions", mitigationArray);
    return json;
  }

  /**
   * Converts the report to a JSON string.
   *
   * @return pretty-printed JSON string
   */
  public String toJson() {
    return GSON.toJson(toJsonObject());
  }

  /**
   * Copies and sorts margin data.
   *
   * @param source source margins
   * @return sorted margin copy
   */
  private static List<OperationalMargin> copyMargins(List<OperationalMargin> source) {
    List<OperationalMargin> copy = source == null ? new ArrayList<OperationalMargin>()
        : new ArrayList<OperationalMargin>(source);
    Collections.sort(copy);
    return copy;
  }

  /**
   * Copies and sorts trip predictions.
   *
   * @param source source predictions
   * @return sorted prediction copy
   */
  private static List<TripPrediction> copyTrips(List<TripPrediction> source) {
    List<TripPrediction> copy = source == null ? new ArrayList<TripPrediction>()
        : new ArrayList<TripPrediction>(source);
    Collections.sort(copy);
    return copy;
  }

  /**
   * Copies and sorts mitigation suggestions.
   *
   * @param source source suggestions
   * @return sorted suggestion copy
   */
  private static List<MitigationSuggestion> copyMitigations(List<MitigationSuggestion> source) {
    List<MitigationSuggestion> copy = source == null ? new ArrayList<MitigationSuggestion>()
        : new ArrayList<MitigationSuggestion>(source);
    Collections.sort(copy);
    return copy;
  }

  /**
   * Determines the overall status from all margins.
   *
   * @param margins margin list
   * @return worst status mapped to envelope status
   */
  private static EnvelopeStatus determineOverallStatus(List<OperationalMargin> margins) {
    EnvelopeStatus status = EnvelopeStatus.NORMAL;
    for (OperationalMargin margin : margins) {
      EnvelopeStatus candidate = mapStatus(margin.getStatus());
      if (candidate.getRank() > status.getRank()) {
        status = candidate;
      }
    }
    return status;
  }

  /**
   * Maps margin status to envelope status.
   *
   * @param status margin status
   * @return envelope status
   */
  private static EnvelopeStatus mapStatus(OperationalMargin.Status status) {
    if (status == OperationalMargin.Status.VIOLATED) {
      return EnvelopeStatus.VIOLATED;
    }
    if (status == OperationalMargin.Status.CRITICAL) {
      return EnvelopeStatus.CRITICAL;
    }
    if (status == OperationalMargin.Status.WARNING) {
      return EnvelopeStatus.WARNING;
    }
    if (status == OperationalMargin.Status.NARROWING) {
      return EnvelopeStatus.NARROWING;
    }
    return EnvelopeStatus.NORMAL;
  }

  /**
   * Builds a short report summary.
   *
   * @param status overall status
   * @param margins ranked margins
   * @param predictions trip predictions
   * @return summary text
   */
  private static String buildSummary(EnvelopeStatus status, List<OperationalMargin> margins,
      List<TripPrediction> predictions) {
    if (margins.isEmpty()) {
      return "No capacity constraints were available for operational envelope evaluation.";
    }
    StringBuilder builder = new StringBuilder();
    builder.append("Operational envelope status ").append(status.name()).append(" with ")
        .append(margins.size()).append(" evaluated margins");
    if (!predictions.isEmpty()) {
      builder.append(" and ").append(predictions.size()).append(" trip prediction(s)");
    }
    builder.append(".");
    return builder.toString();
  }
}