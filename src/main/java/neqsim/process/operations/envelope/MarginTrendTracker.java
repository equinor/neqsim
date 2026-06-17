package neqsim.process.operations.envelope;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks margin history and estimates time to zero margin with a linear trend.
 *
 * <p>
 * This is intended for screening from tagreader snapshots or MCP-provided history. It is not a
 * replacement for dynamic simulation; it simply converts repeated margin samples into an advisory
 * time-to-limit estimate.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class MarginTrendTracker implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String marginKey;
  private final List<MarginSample> samples = new ArrayList<MarginSample>();

  /**
   * Creates a tracker for one margin key.
   *
   * @param marginKey margin key in the form {@code equipment.constraint}
   */
  public MarginTrendTracker(String marginKey) {
    this.marginKey = marginKey == null ? "" : marginKey.trim();
  }

  /**
   * Adds a margin sample.
   *
   * @param timestampSeconds sample timestamp in seconds
   * @param marginPercent margin value in percent
   * @return this tracker for fluent setup
   */
  public MarginTrendTracker addSample(double timestampSeconds, double marginPercent) {
    samples.add(new MarginSample(timestampSeconds, marginPercent));
    Collections.sort(samples);
    return this;
  }

  /**
   * Adds the current margin sample at the supplied timestamp.
   *
   * @param timestampSeconds current timestamp in seconds
   * @param margin current margin
   * @return this tracker for fluent setup
   */
  public MarginTrendTracker addCurrentMargin(double timestampSeconds, OperationalMargin margin) {
    if (margin != null) {
      addSample(timestampSeconds, margin.getMarginPercent());
    }
    return this;
  }

  /**
   * Returns the margin key.
   *
   * @return margin key
   */
  public String getMarginKey() {
    return marginKey;
  }

  /**
   * Returns the samples in timestamp order.
   *
   * @return unmodifiable sample list
   */
  public List<MarginSample> getSamples() {
    return Collections.unmodifiableList(samples);
  }

  /**
   * Returns the latest margin sample.
   *
   * @return latest sample or null when no samples exist
   */
  public MarginSample getLatestSample() {
    return samples.isEmpty() ? null : samples.get(samples.size() - 1);
  }

  /**
   * Estimates time until the margin reaches zero.
   *
   * @return time to zero margin in seconds, or {@link Double#NaN} when not estimable
   */
  public double estimateTimeToLimitSeconds() {
    if (samples.size() < 2) {
      return Double.NaN;
    }
    MarginSample latest = getLatestSample();
    if (latest.getMarginPercent() <= 0.0) {
      return 0.0;
    }
    double slope = calculateSlopePercentPerSecond();
    if (Double.isNaN(slope) || slope >= 0.0) {
      return Double.NaN;
    }
    return -latest.getMarginPercent() / slope;
  }

  /**
   * Estimates confidence in the linear trend.
   *
   * @return confidence from 0.0 to 1.0
   */
  public double estimateConfidence() {
    if (samples.size() < 2) {
      return 0.0;
    }
    double rSquared = calculateRSquared();
    double sampleFactor = Math.min(1.0, samples.size() / 6.0);
    if (Double.isNaN(rSquared)) {
      return 0.25 * sampleFactor;
    }
    return Math.max(0.0, Math.min(1.0, rSquared * sampleFactor));
  }

  /**
   * Returns a short trend description.
   *
   * @return trend description
   */
  public String getTrendDescription() {
    double slope = calculateSlopePercentPerSecond();
    if (Double.isNaN(slope)) {
      return "insufficient history";
    }
    if (slope < 0.0) {
      return "margin decreasing";
    }
    if (slope > 0.0) {
      return "margin increasing";
    }
    return "margin stable";
  }

  /**
   * Converts the tracker to JSON.
   *
   * @return JSON object representation
   */
  public JsonObject toJsonObject() {
    JsonObject json = new JsonObject();
    json.addProperty("marginKey", marginKey);
    json.addProperty("sampleCount", samples.size());
    json.addProperty("trend", getTrendDescription());
    json.addProperty("timeToLimitSeconds", estimateTimeToLimitSeconds());
    json.addProperty("confidence", estimateConfidence());
    JsonArray array = new JsonArray();
    for (MarginSample sample : samples) {
      array.add(sample.toJsonObject());
    }
    json.add("samples", array);
    return json;
  }

  /**
   * Calculates the least-squares margin slope.
   *
   * @return slope in percent per second, or {@link Double#NaN} when not estimable
   */
  private double calculateSlopePercentPerSecond() {
    if (samples.size() < 2) {
      return Double.NaN;
    }
    double meanTime = 0.0;
    double meanMargin = 0.0;
    for (MarginSample sample : samples) {
      meanTime += sample.getTimestampSeconds();
      meanMargin += sample.getMarginPercent();
    }
    meanTime /= samples.size();
    meanMargin /= samples.size();

    double numerator = 0.0;
    double denominator = 0.0;
    for (MarginSample sample : samples) {
      double timeDelta = sample.getTimestampSeconds() - meanTime;
      numerator += timeDelta * (sample.getMarginPercent() - meanMargin);
      denominator += timeDelta * timeDelta;
    }
    if (denominator <= 0.0) {
      return Double.NaN;
    }
    return numerator / denominator;
  }

  /**
   * Calculates the coefficient of determination for the linear trend.
   *
   * @return R-squared value, or {@link Double#NaN} when not estimable
   */
  private double calculateRSquared() {
    if (samples.size() < 2) {
      return Double.NaN;
    }
    double slope = calculateSlopePercentPerSecond();
    if (Double.isNaN(slope)) {
      return Double.NaN;
    }
    double meanTime = 0.0;
    double meanMargin = 0.0;
    for (MarginSample sample : samples) {
      meanTime += sample.getTimestampSeconds();
      meanMargin += sample.getMarginPercent();
    }
    meanTime /= samples.size();
    meanMargin /= samples.size();

    double intercept = meanMargin - slope * meanTime;
    double ssResidual = 0.0;
    double ssTotal = 0.0;
    for (MarginSample sample : samples) {
      double predicted = intercept + slope * sample.getTimestampSeconds();
      double residual = sample.getMarginPercent() - predicted;
      ssResidual += residual * residual;
      double total = sample.getMarginPercent() - meanMargin;
      ssTotal += total * total;
    }
    if (ssTotal <= 0.0) {
      return 1.0;
    }
    return 1.0 - ssResidual / ssTotal;
  }

  /** Sample in a margin trend. */
  public static final class MarginSample implements Serializable, Comparable<MarginSample> {
    private static final long serialVersionUID = 1L;

    private final double timestampSeconds;
    private final double marginPercent;

    /**
     * Creates a margin sample.
     *
     * @param timestampSeconds timestamp in seconds
     * @param marginPercent margin value in percent
     */
    public MarginSample(double timestampSeconds, double marginPercent) {
      this.timestampSeconds = timestampSeconds;
      this.marginPercent = marginPercent;
    }

    /**
     * Returns the timestamp.
     *
     * @return timestamp in seconds
     */
    public double getTimestampSeconds() {
      return timestampSeconds;
    }

    /**
     * Returns the margin.
     *
     * @return margin value in percent
     */
    public double getMarginPercent() {
      return marginPercent;
    }

    /**
     * Converts the sample to JSON.
     *
     * @return JSON object representation
     */
    public JsonObject toJsonObject() {
      JsonObject json = new JsonObject();
      json.addProperty("timestampSeconds", timestampSeconds);
      json.addProperty("marginPercent", marginPercent);
      return json;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(MarginSample other) {
      return Double.compare(timestampSeconds, other.timestampSeconds);
    }
  }
}