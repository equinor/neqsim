package neqsim.process.equipment.pipeline.twophasepipe.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import neqsim.process.equipment.pipeline.TwoFluidPipe;

/**
 * Benchmark comparison harness for TwoFluidPipe profiles against external simulator or field data.
 *
 * <p>
 * The expected CSV columns are:
 * {@code case,time_s,position_m,variable,value,abs_tolerance,rel_tolerance,source}. This intentionally
 * uses a simple numeric export format that can be produced from OLGA, LedaFlow, field historians, or
 * spreadsheet post-processing.
 * </p>
 */
public final class TwoFluidBenchmarkHarness {
  private TwoFluidBenchmarkHarness() {}

  /**
   * Read benchmark points from a comma-separated file.
   *
   * @param csvPath CSV file path
   * @return benchmark points
   * @throws IOException if the file cannot be read
   */
  public static List<BenchmarkPoint> readCsv(Path csvPath) throws IOException {
    List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
    if (lines.isEmpty()) {
      return Collections.emptyList();
    }

    Map<String, Integer> header = parseHeader(lines.get(0));
    List<BenchmarkPoint> points = new ArrayList<>();
    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }

      String[] fields = line.split(",", -1);
      points.add(new BenchmarkPoint(getString(fields, header, "case", ""),
          getDouble(fields, header, "time_s", 0.0), getDouble(fields, header, "position_m", 0.0),
          getString(fields, header, "variable", ""), getDouble(fields, header, "value", 0.0),
          getDouble(fields, header, "abs_tolerance", 0.0),
          getDouble(fields, header, "rel_tolerance", 0.0),
          getString(fields, header, "source", "")));
    }
    return points;
  }

  /**
   * Capture a steady-state or transient snapshot from a pipe.
   *
   * @param pipe solved pipe model
   * @return benchmark snapshot
   */
  public static Snapshot capture(TwoFluidPipe pipe) {
    Map<String, double[]> variables = new HashMap<>();
    double[] pressurePa = pipe.getPressureProfile();
    variables.put("pressure_pa", pressurePa);
    variables.put("pressure_bara", scale(pressurePa, 1.0e-5));
    variables.put("temperature_k", pipe.getTemperatureProfile());
    variables.put("liquid_holdup", pipe.getLiquidHoldupProfile());
    variables.put("water_cut", pipe.getWaterCutProfile());
    variables.put("oil_holdup", pipe.getOilHoldupProfile());
    variables.put("water_holdup", pipe.getWaterHoldupProfile());
    variables.put("gas_velocity_m_s", pipe.getGasVelocityProfile());
    variables.put("liquid_velocity_m_s", pipe.getLiquidVelocityProfile());
    variables.put("oil_velocity_m_s", pipe.getOilVelocityProfile());
    variables.put("water_velocity_m_s", pipe.getWaterVelocityProfile());
    return new Snapshot(pipe.getSimulationTime(), pipe.getPositionProfile(), variables);
  }

  /**
   * Compare one snapshot against benchmark points.
   *
   * @param snapshot model snapshot
   * @param referencePoints reference points
   * @return comparison result
   */
  public static Comparison compare(Snapshot snapshot, List<BenchmarkPoint> referencePoints) {
    return compare(Collections.singletonList(snapshot), referencePoints);
  }

  /**
   * Compare transient snapshots against benchmark points using linear interpolation in time and
   * position.
   *
   * @param snapshots model snapshots
   * @param referencePoints reference points
   * @return comparison result
   */
  public static Comparison compare(List<Snapshot> snapshots, List<BenchmarkPoint> referencePoints) {
    if (snapshots == null || snapshots.isEmpty()) {
      throw new IllegalArgumentException("At least one model snapshot is required");
    }

    List<Snapshot> sortedSnapshots = new ArrayList<>(snapshots);
    sortedSnapshots.sort(Comparator.comparingDouble(Snapshot::getTimeSeconds));

    List<ComparisonRow> rows = new ArrayList<>();
    for (BenchmarkPoint point : referencePoints) {
      double modelValue = interpolate(sortedSnapshots, point);
      rows.add(new ComparisonRow(point, modelValue));
    }
    return new Comparison(rows);
  }

  private static double interpolate(List<Snapshot> snapshots, BenchmarkPoint point) {
    if (snapshots.size() == 1) {
      return snapshots.get(0).valueAt(point.getVariable(), point.getPositionMeters());
    }

    Snapshot lower = snapshots.get(0);
    Snapshot upper = snapshots.get(snapshots.size() - 1);
    for (int i = 0; i < snapshots.size() - 1; i++) {
      Snapshot left = snapshots.get(i);
      Snapshot right = snapshots.get(i + 1);
      if (point.getTimeSeconds() >= left.getTimeSeconds()
          && point.getTimeSeconds() <= right.getTimeSeconds()) {
        lower = left;
        upper = right;
        break;
      }
    }

    double lowerValue = lower.valueAt(point.getVariable(), point.getPositionMeters());
    double upperValue = upper.valueAt(point.getVariable(), point.getPositionMeters());
    double dt = upper.getTimeSeconds() - lower.getTimeSeconds();
    if (Math.abs(dt) < 1e-12) {
      return lowerValue;
    }

    double fraction = (point.getTimeSeconds() - lower.getTimeSeconds()) / dt;
    fraction = Math.max(0.0, Math.min(1.0, fraction));
    return lowerValue + fraction * (upperValue - lowerValue);
  }

  private static Map<String, Integer> parseHeader(String line) {
    String[] fields = line.split(",", -1);
    Map<String, Integer> header = new HashMap<>();
    for (int i = 0; i < fields.length; i++) {
      header.put(normalize(fields[i]), i);
    }
    return header;
  }

  private static String getString(String[] fields, Map<String, Integer> header, String key,
      String defaultValue) {
    Integer index = header.get(normalize(key));
    if (index == null || index >= fields.length) {
      return defaultValue;
    }
    return fields[index].trim();
  }

  private static double getDouble(String[] fields, Map<String, Integer> header, String key,
      double defaultValue) {
    String value = getString(fields, header, key, "");
    if (value.isEmpty()) {
      return defaultValue;
    }
    return Double.parseDouble(value);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private static double[] scale(double[] values, double factor) {
    double[] scaled = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      scaled[i] = values[i] * factor;
    }
    return scaled;
  }

  /** One reference sample from an external simulator or field data set. */
  public static final class BenchmarkPoint {
    private final String caseName;
    private final double timeSeconds;
    private final double positionMeters;
    private final String variable;
    private final double value;
    private final double absoluteTolerance;
    private final double relativeTolerance;
    private final String source;

    public BenchmarkPoint(String caseName, double timeSeconds, double positionMeters,
        String variable, double value, double absoluteTolerance, double relativeTolerance,
        String source) {
      if (!Double.isFinite(timeSeconds) || !Double.isFinite(positionMeters)
          || !Double.isFinite(value) || !Double.isFinite(absoluteTolerance)
          || !Double.isFinite(relativeTolerance)) {
        throw new IllegalArgumentException("Benchmark point contains non-finite numeric values");
      }
      if (absoluteTolerance < 0.0 || relativeTolerance < 0.0) {
        throw new IllegalArgumentException("Benchmark tolerances must be non-negative");
      }
      this.caseName = caseName;
      this.timeSeconds = timeSeconds;
      this.positionMeters = positionMeters;
      this.variable = normalize(variable);
      this.value = value;
      this.absoluteTolerance = absoluteTolerance;
      this.relativeTolerance = relativeTolerance;
      this.source = source;
    }

    public String getCaseName() {
      return caseName;
    }

    public double getTimeSeconds() {
      return timeSeconds;
    }

    public double getPositionMeters() {
      return positionMeters;
    }

    public String getVariable() {
      return variable;
    }

    public double getValue() {
      return value;
    }

    public double getAbsoluteTolerance() {
      return absoluteTolerance;
    }

    public double getRelativeTolerance() {
      return relativeTolerance;
    }

    public String getSource() {
      return source;
    }
  }

  /** Model profiles captured at one simulation time. */
  public static final class Snapshot {
    private final double timeSeconds;
    private final double[] positionsMeters;
    private final Map<String, double[]> variables;

    public Snapshot(double timeSeconds, double[] positionsMeters, Map<String, double[]> variables) {
      if (!Double.isFinite(timeSeconds)) {
        throw new IllegalArgumentException("Snapshot time must be finite");
      }
      this.timeSeconds = timeSeconds;
      this.positionsMeters = positionsMeters.clone();
      this.variables = new HashMap<>();
      for (Map.Entry<String, double[]> entry : variables.entrySet()) {
        this.variables.put(normalize(entry.getKey()), entry.getValue().clone());
      }
    }

    public double getTimeSeconds() {
      return timeSeconds;
    }

    public Set<String> getAvailableVariables() {
      return new HashSet<>(variables.keySet());
    }

    public double valueAt(String variable, double positionMeters) {
      double[] values = variables.get(normalize(variable));
      if (values == null || values.length == 0) {
        throw new IllegalArgumentException("No model profile for variable: " + variable);
      }
      if (positionsMeters.length != values.length) {
        throw new IllegalArgumentException("Position and value profile lengths differ for "
            + variable + ": " + positionsMeters.length + " vs " + values.length);
      }
      return interpolatePosition(positionMeters, positionsMeters, values);
    }

    private double interpolatePosition(double x, double[] positions, double[] values) {
      if (x <= positions[0]) {
        return values[0];
      }
      int last = positions.length - 1;
      if (x >= positions[last]) {
        return values[last];
      }
      for (int i = 0; i < last; i++) {
        if (x >= positions[i] && x <= positions[i + 1]) {
          double dx = positions[i + 1] - positions[i];
          if (Math.abs(dx) < 1e-12) {
            return values[i];
          }
          double fraction = (x - positions[i]) / dx;
          return values[i] + fraction * (values[i + 1] - values[i]);
        }
      }
      return values[last];
    }
  }

  /** One benchmark comparison row. */
  public static final class ComparisonRow {
    private final BenchmarkPoint reference;
    private final double modelValue;

    private ComparisonRow(BenchmarkPoint reference, double modelValue) {
      this.reference = reference;
      this.modelValue = modelValue;
    }

    public BenchmarkPoint getReference() {
      return reference;
    }

    public double getModelValue() {
      return modelValue;
    }

    public double getAbsoluteError() {
      return Math.abs(modelValue - reference.getValue());
    }

    public double getRelativeError() {
      double scale = Math.max(Math.abs(reference.getValue()), 1e-12);
      return getAbsoluteError() / scale;
    }

    public boolean isPassed() {
      return getAbsoluteError() <= reference.getAbsoluteTolerance()
          || getRelativeError() <= reference.getRelativeTolerance();
    }
  }

  /** Aggregate comparison result. */
  public static final class Comparison {
    private final List<ComparisonRow> rows;

    private Comparison(List<ComparisonRow> rows) {
      this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
    }

    public List<ComparisonRow> getRows() {
      return rows;
    }

    public boolean isPassed() {
      return getFailureCount() == 0;
    }

    public int getFailureCount() {
      int failures = 0;
      for (ComparisonRow row : rows) {
        if (!row.isPassed()) {
          failures++;
        }
      }
      return failures;
    }

    public double getMaximumRelativeError() {
      double max = 0.0;
      for (ComparisonRow row : rows) {
        max = Math.max(max, row.getRelativeError());
      }
      return max;
    }

    public String failureSummary() {
      StringBuilder summary = new StringBuilder();
      for (ComparisonRow row : rows) {
        if (!row.isPassed()) {
          BenchmarkPoint point = row.getReference();
          summary.append(point.getCaseName()).append(" ").append(point.getVariable())
              .append(" t=").append(point.getTimeSeconds()).append("s x=")
              .append(point.getPositionMeters()).append("m ref=").append(point.getValue())
              .append(" model=").append(row.getModelValue()).append(" absErr=")
              .append(row.getAbsoluteError()).append(" relErr=").append(row.getRelativeError())
              .append(System.lineSeparator());
        }
      }
      return summary.toString();
    }
  }
}
