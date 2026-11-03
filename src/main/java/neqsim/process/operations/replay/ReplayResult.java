package neqsim.process.operations.replay;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Result of a {@link ProcessReplay}: per-step records, residual statistics, baseline standardisation and change-point
 * detection, with CSV and JSON export.
 *
 * <p>
 * Residual statistics only use converged steps with a finite residual. Windows are given as inclusive start and
 * exclusive end row indices; {@link #rowIndexAtOrAfter(double)} converts a time stamp to an index.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ReplayResult implements Serializable {
  private static final long serialVersionUID = 1L;
  /** Minimum number of rows used to estimate the new residual level after a detected change. */
  public static final int LEVEL_SEGMENT_ROWS = 10;

  private final List<String> inputTags;
  private final List<String> outputTags;
  private final List<ReplayStepRecord> steps = new ArrayList<ReplayStepRecord>();
  private final boolean warmStart;
  private final Map<String, WindowStatistics> baseline = new LinkedHashMap<String, WindowStatistics>();
  private int baselineStart = -1;
  private int baselineEnd = -1;

  /** Bias, RMS and spread of residuals over a window. */
  public static final class WindowStatistics implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String tag;
    private final int start;
    private final int end;
    private final int count;
    private final double mean;
    private final double standardDeviation;
    private final double rms;

    /**
     * Creates window statistics.
     *
     * @param tag measured tag
     * @param start inclusive start row
     * @param end exclusive end row
     * @param count number of residuals used
     * @param mean mean residual (bias)
     * @param standardDeviation sample standard deviation of the residuals
     * @param rms root mean square of the residuals
     */
    WindowStatistics(String tag, int start, int end, int count, double mean, double standardDeviation, double rms) {
      this.tag = tag;
      this.start = start;
      this.end = end;
      this.count = count;
      this.mean = mean;
      this.standardDeviation = standardDeviation;
      this.rms = rms;
    }

    /**
     * Returns the measured tag.
     *
     * @return tag
     */
    public String getTag() {
      return tag;
    }

    /**
     * Returns the inclusive start row.
     *
     * @return start row index
     */
    public int getStart() {
      return start;
    }

    /**
     * Returns the exclusive end row.
     *
     * @return end row index
     */
    public int getEnd() {
      return end;
    }

    /**
     * Returns the number of residuals in the window.
     *
     * @return count
     */
    public int getCount() {
      return count;
    }

    /**
     * Returns the mean residual (bias, predicted minus measured).
     *
     * @return bias, NaN when the window has no residual
     */
    public double getBias() {
      return mean;
    }

    /**
     * Returns the sample standard deviation of the residuals.
     *
     * @return standard deviation, 0 for a single residual, NaN for none
     */
    public double getStandardDeviation() {
      return standardDeviation;
    }

    /**
     * Returns the root mean square of the residuals.
     *
     * @return RMS, NaN when the window has no residual
     */
    public double getRms() {
      return rms;
    }

    /**
     * Converts the statistics to JSON.
     *
     * @return JSON object
     */
    JsonObject toJsonObject() {
      JsonObject json = new JsonObject();
      json.addProperty("tag", tag);
      json.addProperty("start", start);
      json.addProperty("end", end);
      json.addProperty("count", count);
      addNumber(json, "bias", mean);
      addNumber(json, "standardDeviation", standardDeviation);
      addNumber(json, "rms", rms);
      return json;
    }
  }

  /** A detected shift of the standardised residual of one tag. */
  public static final class ChangePoint implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String tag;
    private final int index;
    private final double time;
    private final int detectionIndex;
    private final int direction;

    /**
     * Creates a change point.
     *
     * @param tag measured tag
     * @param index estimated row index where the shift started
     * @param time time stamp of that row
     * @param detectionIndex row index at which the CUSUM crossed the threshold
     * @param direction +1 for an upward shift of the residual, -1 for a downward shift
     */
    ChangePoint(String tag, int index, double time, int detectionIndex, int direction) {
      this.tag = tag;
      this.index = index;
      this.time = time;
      this.detectionIndex = detectionIndex;
      this.direction = direction;
    }

    /**
     * Returns the measured tag.
     *
     * @return tag
     */
    public String getTag() {
      return tag;
    }

    /**
     * Returns the estimated row index where the shift started.
     *
     * @return row index
     */
    public int getIndex() {
      return index;
    }

    /**
     * Returns the time stamp of the estimated start of the shift.
     *
     * @return time stamp
     */
    public double getTime() {
      return time;
    }

    /**
     * Returns the row index at which the shift was confirmed.
     *
     * @return detection row index
     */
    public int getDetectionIndex() {
      return detectionIndex;
    }

    /**
     * Returns the direction of the shift.
     *
     * @return +1 for upward, -1 for downward
     */
    public int getDirection() {
      return direction;
    }

    /**
     * Converts the change point to JSON.
     *
     * @return JSON object
     */
    JsonObject toJsonObject() {
      JsonObject json = new JsonObject();
      json.addProperty("tag", tag);
      json.addProperty("index", index);
      addNumber(json, "time", time);
      json.addProperty("detectionIndex", detectionIndex);
      json.addProperty("direction", direction);
      return json;
    }
  }

  /**
   * Creates an empty result.
   *
   * @param inputTags input tags in binding order
   * @param outputTags measured output tags in binding order
   * @param warmStart whether the model state carried over between steps
   */
  ReplayResult(List<String> inputTags, List<String> outputTags, boolean warmStart) {
    this.inputTags = new ArrayList<String>(inputTags);
    this.outputTags = new ArrayList<String>(outputTags);
    this.warmStart = warmStart;
  }

  /**
   * Appends a step record.
   *
   * @param record the step record
   */
  void addStep(ReplayStepRecord record) {
    steps.add(record);
  }

  /**
   * Returns all step records in time order.
   *
   * @return unmodifiable list of records
   */
  public List<ReplayStepRecord> getSteps() {
    return Collections.unmodifiableList(steps);
  }

  /**
   * Returns one step record.
   *
   * @param index row index
   * @return the record
   */
  public ReplayStepRecord getStep(int index) {
    return steps.get(index);
  }

  /**
   * Returns the number of replayed rows.
   *
   * @return step count
   */
  public int size() {
    return steps.size();
  }

  /**
   * Returns the input tags.
   *
   * @return unmodifiable list of input tags
   */
  public List<String> getInputTags() {
    return Collections.unmodifiableList(inputTags);
  }

  /**
   * Returns the measured output tags.
   *
   * @return unmodifiable list of output tags
   */
  public List<String> getOutputTags() {
    return Collections.unmodifiableList(outputTags);
  }

  /**
   * Returns whether the model state carried over between steps.
   *
   * @return true for warm start
   */
  public boolean isWarmStart() {
    return warmStart;
  }

  /**
   * Counts steps with a given status.
   *
   * @param status status to count
   * @return number of steps
   */
  public int count(ReplayStepRecord.Status status) {
    int n = 0;
    for (ReplayStepRecord step : steps) {
      if (step.getStatus() == status) {
        n++;
      }
    }
    return n;
  }

  /**
   * Returns the number of converged steps.
   *
   * @return converged step count
   */
  public int getConvergedCount() {
    return count(ReplayStepRecord.Status.CONVERGED);
  }

  /**
   * Returns the number of steps that were run but did not converge, failed, or had invalid input.
   *
   * @return failed step count (skipped steps excluded)
   */
  public int getFailedCount() {
    return count(ReplayStepRecord.Status.NOT_CONVERGED) + count(ReplayStepRecord.Status.FAILED)
        + count(ReplayStepRecord.Status.INPUT_INVALID);
  }

  /**
   * Returns the row index of the first step whose time stamp is at least the given time.
   *
   * @param time time stamp
   * @return row index, or {@link #size()} when none
   */
  public int rowIndexAtOrAfter(double time) {
    for (ReplayStepRecord step : steps) {
      if (step.getTime() >= time) {
        return step.getIndex();
      }
    }
    return steps.size();
  }

  /**
   * Computes residual statistics of a tag over a window of rows.
   *
   * @param tag measured output tag
   * @param start inclusive start row index
   * @param end exclusive end row index
   * @return window statistics; bias and RMS are NaN when no converged residual lies in the window
   */
  public WindowStatistics getWindowStatistics(String tag, int start, int end) {
    int from = Math.max(0, start);
    int to = Math.min(steps.size(), end);
    double sum = 0.0;
    double sumSq = 0.0;
    int n = 0;
    for (int i = from; i < to; i++) {
      ReplayStepRecord step = steps.get(i);
      double residual = step.getResidual(tag);
      if (step.isConverged() && ReplayBinding.isFinite(residual)) {
        sum += residual;
        sumSq += residual * residual;
        n++;
      }
    }
    if (n == 0) {
      return new WindowStatistics(tag, from, to, 0, Double.NaN, Double.NaN, Double.NaN);
    }
    double mean = sum / n;
    double variance = 0.0;
    if (n > 1) {
      double ss = 0.0;
      for (int i = from; i < to; i++) {
        ReplayStepRecord step = steps.get(i);
        double residual = step.getResidual(tag);
        if (step.isConverged() && ReplayBinding.isFinite(residual)) {
          ss += (residual - mean) * (residual - mean);
        }
      }
      variance = ss / (n - 1);
    }
    return new WindowStatistics(tag, from, to, n, mean, Math.sqrt(variance), Math.sqrt(sumSq / n));
  }

  /**
   * Computes residual statistics of all output tags over a window of rows.
   *
   * @param start inclusive start row index
   * @param end exclusive end row index
   * @return map from tag to statistics in output-binding order
   */
  public Map<String, WindowStatistics> getWindowStatistics(int start, int end) {
    Map<String, WindowStatistics> stats = new LinkedHashMap<String, WindowStatistics>();
    for (String tag : outputTags) {
      stats.put(tag, getWindowStatistics(tag, start, end));
    }
    return stats;
  }

  /**
   * Sets the baseline window used for standardised residuals and change-point detection.
   *
   * @param start inclusive start row index
   * @param end exclusive end row index
   * @return this result
   */
  public ReplayResult setBaselineWindow(int start, int end) {
    if (end <= start) {
      throw new IllegalArgumentException("baseline end must be after start");
    }
    baselineStart = start;
    baselineEnd = end;
    baseline.clear();
    for (String tag : outputTags) {
      baseline.put(tag, getWindowStatistics(tag, start, end));
    }
    return this;
  }

  /**
   * Checks whether a baseline window is set.
   *
   * @return true when {@link #setBaselineWindow(int, int)} has been called
   */
  public boolean hasBaseline() {
    return baselineStart >= 0;
  }

  /**
   * Returns the baseline statistics of a tag.
   *
   * @param tag measured output tag
   * @return baseline statistics, or null when no baseline is set
   */
  public WindowStatistics getBaseline(String tag) {
    return baseline.get(tag);
  }

  /**
   * Returns the residuals of a tag standardised with the baseline mean and standard deviation.
   *
   * @param tag measured output tag
   * @param minSigma lower bound on the baseline standard deviation, guarding against a near-perfect baseline
   * @return one value per row; NaN for rows without a converged residual
   * @throws IllegalStateException if no baseline window is set
   */
  public double[] getStandardizedResiduals(String tag, double minSigma) {
    WindowStatistics stats = baseline.get(tag);
    if (stats == null) {
      throw new IllegalStateException("set a baseline window before standardising residuals");
    }
    double mean = ReplayBinding.isFinite(stats.getBias()) ? stats.getBias() : 0.0;
    double sigma = ReplayBinding.isFinite(stats.getStandardDeviation()) ? stats.getStandardDeviation() : 0.0;
    sigma = Math.max(sigma, Math.max(minSigma, Double.MIN_NORMAL));
    double[] z = new double[steps.size()];
    for (int i = 0; i < steps.size(); i++) {
      ReplayStepRecord step = steps.get(i);
      double residual = step.getResidual(tag);
      z[i] = step.isConverged() && ReplayBinding.isFinite(residual) ? (residual - mean) / sigma : Double.NaN;
    }
    return z;
  }

  /**
   * Detects shifts in the residual of a tag with a two-sided tabular CUSUM on the standardised residuals.
   *
   * <p>
   * With {@code z} the standardised residual, {@code S+ = max(0, S+ + z - k)} and {@code S- = max(0, S- - z - k)}. A
   * change is confirmed when either sum exceeds {@code h}; the change time is the maximum-likelihood mean-shift split
   * between the row after the sum was last zero and the confirmation row. The sums are then reset and the reference
   * level is moved to the mean standardised residual over at least {@value #LEVEL_SEGMENT_ROWS} rows from the change
   * (the analysis is offline, so later rows may be used), so a sustained shift is reported once and later shifts are
   * measured from the new level. Rows without a residual are skipped.
   * </p>
   *
   * @param tag measured output tag
   * @param slack reference value {@code k} in standard deviations (typically 0.5)
   * @param threshold decision interval {@code h} in standard deviations (typically 4 to 5)
   * @param minSigma lower bound on the baseline standard deviation
   * @return detected change points in time order
   * @throws IllegalStateException if no baseline window is set
   */
  public List<ChangePoint> detectChangePoints(String tag, double slack, double threshold, double minSigma) {
    double[] z = getStandardizedResiduals(tag, minSigma);
    List<ChangePoint> points = new ArrayList<ChangePoint>();
    double upper = 0.0;
    double lower = 0.0;
    int upperStart = -1;
    int lowerStart = -1;
    double level = 0.0;
    for (int i = 0; i < z.length; i++) {
      if (!ReplayBinding.isFinite(z[i])) {
        continue;
      }
      double deviation = z[i] - level;
      upper = Math.max(0.0, upper + deviation - slack);
      lower = Math.max(0.0, lower - deviation - slack);
      if (upper == 0.0) {
        upperStart = -1;
      } else if (upperStart < 0) {
        upperStart = i;
      }
      if (lower == 0.0) {
        lowerStart = -1;
      } else if (lowerStart < 0) {
        lowerStart = i;
      }
      if (upper > threshold || lower > threshold) {
        boolean up = upper > threshold;
        int start = refineChangeIndex(z, up ? upperStart : lowerStart, i, level);
        points.add(new ChangePoint(tag, start, steps.get(start).getTime(), i, up ? 1 : -1));
        double sum = 0.0;
        int n = 0;
        int segmentEnd = Math.min(z.length, Math.max(i + 1, start + LEVEL_SEGMENT_ROWS));
        for (int j = start; j < segmentEnd; j++) {
          if (ReplayBinding.isFinite(z[j])) {
            sum += z[j];
            n++;
          }
        }
        level = n > 0 ? sum / n : z[i];
        upper = 0.0;
        lower = 0.0;
        upperStart = -1;
        lowerStart = -1;
      }
    }
    return points;
  }

  /**
   * Refines a CUSUM change-time estimate with the maximum-likelihood split for a mean shift: among candidate start rows
   * between the last zero of the CUSUM and the detection row, select the row maximising
   * {@code n * (mean(z[t..end]) - level)^2}.
   *
   * @param z standardised residuals (NaN for missing rows)
   * @param first first candidate row (first row after the CUSUM was last zero)
   * @param end detection row (inclusive)
   * @param level reference level before the change
   * @return estimated row where the shift started
   */
  private static int refineChangeIndex(double[] z, int first, int end, double level) {
    int best = first < 0 ? end : first;
    double bestScore = Double.NEGATIVE_INFINITY;
    double sum = 0.0;
    int n = 0;
    for (int t = end; t >= Math.max(0, first); t--) {
      if (!ReplayBinding.isFinite(z[t])) {
        continue;
      }
      sum += z[t] - level;
      n++;
      double score = sum * sum / n;
      if (score >= bestScore) {
        bestScore = score;
        best = t;
      }
    }
    return best;
  }

  /**
   * Detects change points for all output tags with default settings ({@code k = 0.5}, {@code h = 5},
   * {@code minSigma = 1e-9}).
   *
   * @return map from tag to detected change points
   * @throws IllegalStateException if no baseline window is set
   */
  public Map<String, List<ChangePoint>> detectChangePoints() {
    Map<String, List<ChangePoint>> all = new LinkedHashMap<String, List<ChangePoint>>();
    for (String tag : outputTags) {
      all.put(tag, detectChangePoints(tag, 0.5, 5.0, 1.0e-9));
    }
    return all;
  }

  /**
   * Returns a one-line summary with step counts.
   *
   * @return summary text
   */
  public String getSummary() {
    return String.format(Locale.ROOT,
        "ProcessReplay: %d steps, %d converged, %d not converged, %d failed, %d input invalid, %d skipped (%s start)",
        steps.size(), getConvergedCount(), count(ReplayStepRecord.Status.NOT_CONVERGED),
        count(ReplayStepRecord.Status.FAILED), count(ReplayStepRecord.Status.INPUT_INVALID),
        count(ReplayStepRecord.Status.SKIPPED), warmStart ? "warm" : "cold");
  }

  /**
   * Returns the CSV header used by {@link #toCsv()}.
   *
   * @return list of column names
   */
  public List<String> getCsvColumns() {
    List<String> columns = new ArrayList<String>();
    columns.add("index");
    columns.add("time");
    columns.add("status");
    columns.add("attempts");
    for (String tag : inputTags) {
      columns.add("input:" + tag);
    }
    for (String tag : outputTags) {
      columns.add("predicted:" + tag);
      columns.add("measured:" + tag);
      columns.add("residual:" + tag);
      if (hasBaseline()) {
        columns.add("z:" + tag);
      }
    }
    columns.add("message");
    return columns;
  }

  /**
   * Exports the step records as CSV. Missing numbers are empty cells; standardised residual columns are included when a
   * baseline is set.
   *
   * @return CSV text with a header row
   */
  public String toCsv() {
    Map<String, double[]> z = new LinkedHashMap<String, double[]>();
    if (hasBaseline()) {
      for (String tag : outputTags) {
        z.put(tag, getStandardizedResiduals(tag, 1.0e-9));
      }
    }
    StringBuilder sb = new StringBuilder();
    sb.append(join(getCsvColumns())).append('\n');
    for (int i = 0; i < steps.size(); i++) {
      ReplayStepRecord step = steps.get(i);
      List<String> cells = new ArrayList<String>();
      cells.add(Integer.toString(step.getIndex()));
      cells.add(format(step.getTime()));
      cells.add(step.getStatus().name());
      cells.add(Integer.toString(step.getAttempts()));
      for (String tag : inputTags) {
        cells.add(format(step.getInputs().get(tag)));
      }
      for (String tag : outputTags) {
        cells.add(format(step.getPredicted().get(tag)));
        cells.add(format(step.getMeasured().get(tag)));
        cells.add(format(step.getResiduals().get(tag)));
        if (hasBaseline()) {
          cells.add(format(z.get(tag)[i]));
        }
      }
      cells.add(quote(step.getMessage()));
      sb.append(join(cells)).append('\n');
    }
    return sb.toString();
  }

  /**
   * Converts the result to a JSON object with summary, baseline, change points and steps.
   *
   * @return JSON object
   */
  public JsonObject toJsonObject() {
    JsonObject json = new JsonObject();
    JsonObject summary = new JsonObject();
    summary.addProperty("steps", steps.size());
    for (ReplayStepRecord.Status status : ReplayStepRecord.Status.values()) {
      summary.addProperty(status.name().toLowerCase(Locale.ROOT), count(status));
    }
    summary.addProperty("warmStart", warmStart);
    summary.addProperty("text", getSummary());
    json.add("summary", summary);

    JsonArray inputs = new JsonArray();
    for (String tag : inputTags) {
      inputs.add(tag);
    }
    json.add("inputTags", inputs);
    JsonArray outputs = new JsonArray();
    for (String tag : outputTags) {
      outputs.add(tag);
    }
    json.add("outputTags", outputs);

    JsonObject overall = new JsonObject();
    for (WindowStatistics stats : getWindowStatistics(0, steps.size()).values()) {
      overall.add(stats.getTag(), stats.toJsonObject());
    }
    json.add("overallStatistics", overall);

    if (hasBaseline()) {
      JsonObject base = new JsonObject();
      for (WindowStatistics stats : baseline.values()) {
        base.add(stats.getTag(), stats.toJsonObject());
      }
      json.add("baseline", base);
      JsonArray changes = new JsonArray();
      for (List<ChangePoint> list : detectChangePoints().values()) {
        for (ChangePoint point : list) {
          changes.add(point.toJsonObject());
        }
      }
      json.add("changePoints", changes);
    }

    JsonArray stepArray = new JsonArray();
    for (ReplayStepRecord step : steps) {
      JsonObject item = new JsonObject();
      item.addProperty("index", step.getIndex());
      addNumber(item, "time", step.getTime());
      item.addProperty("status", step.getStatus().name());
      item.addProperty("attempts", step.getAttempts());
      item.addProperty("terminationReason", step.getTerminationReason());
      item.addProperty("message", step.getMessage());
      item.add("inputs", toJson(step.getInputs()));
      item.add("predicted", toJson(step.getPredicted()));
      item.add("measured", toJson(step.getMeasured()));
      item.add("residuals", toJson(step.getResiduals()));
      stepArray.add(item);
    }
    json.add("steps", stepArray);
    return json;
  }

  /**
   * Exports the result as pretty-printed JSON.
   *
   * @return JSON text
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(toJsonObject());
  }

  /**
   * Writes the CSV export to a file.
   *
   * @param path target file path
   * @throws IOException if the file cannot be written
   */
  public void writeCsv(String path) throws IOException {
    Files.write(Paths.get(path), toCsv().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Writes the JSON export to a file.
   *
   * @param path target file path
   * @throws IOException if the file cannot be written
   */
  public void writeJson(String path) throws IOException {
    Files.write(Paths.get(path), toJson().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Converts a map of values to a JSON object, writing non-finite values as null.
   *
   * @param values map to convert
   * @return JSON object
   */
  private static JsonObject toJson(Map<String, Double> values) {
    JsonObject json = new JsonObject();
    for (Map.Entry<String, Double> entry : values.entrySet()) {
      addNumber(json, entry.getKey(), entry.getValue() == null ? Double.NaN : entry.getValue().doubleValue());
    }
    return json;
  }

  /**
   * Adds a number to a JSON object, writing non-finite values as null.
   *
   * @param json target object
   * @param name property name
   * @param value value
   */
  private static void addNumber(JsonObject json, String name, double value) {
    if (ReplayBinding.isFinite(value)) {
      json.addProperty(name, value);
    } else {
      json.add(name, com.google.gson.JsonNull.INSTANCE);
    }
  }

  /**
   * Formats a number for CSV.
   *
   * @param value value, may be null
   * @return text, empty for null or non-finite values
   */
  private static String format(Double value) {
    if (value == null || !ReplayBinding.isFinite(value.doubleValue())) {
      return "";
    }
    return Double.toString(value.doubleValue());
  }

  /**
   * Quotes a CSV text cell.
   *
   * @param text cell text
   * @return quoted text with doubled inner quotes
   */
  private static String quote(String text) {
    String value = text == null ? "" : text.replace("\r", " ").replace("\n", " ");
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  /**
   * Joins CSV cells with commas.
   *
   * @param cells cell texts
   * @return CSV line
   */
  private static String join(List<String> cells) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(cells.get(i));
    }
    return sb.toString();
  }
}
