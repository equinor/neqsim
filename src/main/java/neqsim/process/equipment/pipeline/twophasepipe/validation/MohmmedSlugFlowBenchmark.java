package neqsim.process.equipment.pipeline.twophasepipe.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reader and complete-case comparison for the measured Mohmmed et al. slug-flow tables.
 *
 * <p>
 * Reference: Data in Brief 16 (2018), 527-530, DOI 10.1016/j.dib.2017.11.026. The accompanying CC BY 4.0 fixture
 * preserves all 75 numeric observations and source-cell provenance. It is experimental data, not the output of a
 * correlation. Upstream station labels conflict between article and spreadsheet, translation-velocity stations are
 * unspecified, and pointwise uncertainties are absent. Consequently an engineering-tolerance pass is not a claim of
 * complete dynamic validation. Predictions must be generated independently, without using measured outcomes as initial
 * conditions for the same outcome.
 * </p>
 */
public final class MohmmedSlugFlowBenchmark {
  /** Engineering tolerance declared before running model comparisons; not measurement uncertainty. */
  public static final double TRANSLATION_RELATIVE_TOLERANCE = 0.20;
  /** Engineering tolerance declared before running model comparisons; not measurement uncertainty. */
  public static final double LENGTH_RELATIVE_TOLERANCE = 0.30;
  /** Engineering tolerance declared before running model comparisons; not measurement uncertainty. */
  public static final double FREQUENCY_RELATIVE_TOLERANCE = 0.30;

  private static final String HEADER = "point_id,quantity,jg_m_s,jl_m_s,measured_value,unit,station_label,"
      + "station_min_x_over_d,station_max_x_over_d,diameter_m,temperature_k,pressure_pa,source_file,source_sheet,"
      + "source_gas_cell,source_liquid_cell,source_value_cell,flags";

  private MohmmedSlugFlowBenchmark() {
  }

  /**
   * Read the exact unquoted numeric fixture format, rejecting missing or malformed provenance.
   *
   * @param reader caller-owned CSV reader
   * @return immutable list of immutable measurements
   * @throws IOException if the header, numbers, identifiers or source fields are invalid
   */
  public static List<Point> readCsv(Reader reader) throws IOException {
    BufferedReader input = new BufferedReader(reader);
    if (!HEADER.equals(input.readLine())) {
      throw new IOException("Unexpected Mohmmed slug measurement CSV header");
    }
    List<Point> points = new ArrayList<Point>();
    Set<String> identifiers = new HashSet<String>();
    String line;
    int lineNumber = 1;
    while ((line = input.readLine()) != null) {
      lineNumber++;
      if (line.trim().isEmpty()) {
        continue;
      }
      String[] fields = line.split(",", -1);
      try {
        if (fields.length != 18) {
          throw new IllegalArgumentException("Expected 18 fields");
        }
        Point point = new Point(fields);
        if (!identifiers.add(point.id)) {
          throw new IllegalArgumentException("Duplicate point identifier " + point.id);
        }
        points.add(point);
      } catch (IllegalArgumentException exception) {
        throw new IOException("Invalid Mohmmed measurement at line " + lineNumber, exception);
      }
    }
    if (points.isEmpty()) {
      throw new IOException("No measured points");
    }
    return Collections.unmodifiableList(points);
  }

  /**
   * Compare every supplied measurement; missing, nonfinite or nonpositive predictions fail.
   *
   * <p>
   * Use the full loaded dataset for the full benchmark. A deliberately scoped subset must be reported as such. No
   * nearest-condition matching, source-location correction, or interpolation of experimental outcomes is performed.
   * </p>
   *
   * @param points measured points for the declared comparison scope
   * @param predictions predictions indexed by exact source point identifier and in the reference units
   * @return immutable rows including missing predictions, preserving the input order
   */
  public static List<ComparisonRow> compare(List<Point> points, Map<String, Double> predictions) {
    if (points == null || points.isEmpty() || predictions == null) {
      throw new IllegalArgumentException("Measurements and predictions are required");
    }
    Set<String> identifiers = new HashSet<String>();
    List<ComparisonRow> rows = new ArrayList<ComparisonRow>();
    for (Point point : points) {
      if (point == null || !identifiers.add(point.id)) {
        throw new IllegalArgumentException("Null or duplicate measurement");
      }
      Double predicted = predictions.get(point.id);
      rows.add(new ComparisonRow(point, predicted == null ? Double.NaN : predicted.doubleValue()));
    }
    if (!identifiers.containsAll(predictions.keySet())) {
      throw new IllegalArgumentException("Prediction contains an unknown measurement identifier");
    }
    return Collections.unmodifiableList(rows);
  }

  private static double positive(String value) {
    double number = Double.parseDouble(value);
    if (!Double.isFinite(number) || number <= 0.0) {
      throw new IllegalArgumentException("Expected a finite positive number");
    }
    return number;
  }

  /** Immutable experimental observation with exact spreadsheet provenance. */
  public static final class Point {
    /** Stable source-cell identifier. */
    public final String id;
    /** translation_velocity, length_over_diameter, or frequency. */
    public final String quantity;
    /** Superficial gas velocity, m/s, as tabulated. */
    public final double superficialGasVelocity;
    /** Superficial liquid velocity, m/s, as tabulated. */
    public final double superficialLiquidVelocity;
    /** Measured value in {@link #unit}. */
    public final double measuredValue;
    /** m/s, dimensionless 1, or Hz. */
    public final String unit;
    /** Original station interpretation, retaining the 54D/58D conflict. */
    public final String stationLabel;
    /** Lower possible station x/D; NaN when the velocity station is unspecified. */
    public final double stationMinimumDiameters;
    /** Upper possible station x/D; NaN when the velocity station is unspecified. */
    public final double stationMaximumDiameters;
    /** Inner diameter from spreadsheet legend and the cited measurement-method paper, m. */
    public final double diameter;
    /** Article room temperature converted to K. */
    public final double temperature;
    /** Article atmospheric pressure converted to Pa; not a recorded pressure trace. */
    public final double pressure;
    /** Original supplementary filename. */
    public final String sourceFile;
    /** OOXML worksheet path. */
    public final String sourceSheet;
    /** Source cell for superficial gas velocity. */
    public final String sourceGasCell;
    /** Source cell for superficial liquid velocity. */
    public final String sourceLiquidCell;
    /** Source cell for measured outcome. */
    public final String sourceValueCell;
    /** Source limitations, separated by semicolons. */
    public final String flags;

    private Point(String[] fields) {
      for (int index = 0; index < fields.length; index++) {
        if (index != 7 && index != 8 && fields[index].trim().isEmpty()) {
          throw new IllegalArgumentException("Missing field " + index);
        }
      }
      id = fields[0];
      quantity = fields[1];
      superficialGasVelocity = positive(fields[2]);
      superficialLiquidVelocity = positive(fields[3]);
      measuredValue = positive(fields[4]);
      unit = fields[5];
      stationLabel = fields[6];
      stationMinimumDiameters = fields[7].isEmpty() ? Double.NaN : positive(fields[7]);
      stationMaximumDiameters = fields[8].isEmpty() ? Double.NaN : positive(fields[8]);
      if (Double.isNaN(stationMinimumDiameters) != Double.isNaN(stationMaximumDiameters)
          || stationMinimumDiameters > stationMaximumDiameters) {
        throw new IllegalArgumentException("Invalid station bounds");
      }
      diameter = positive(fields[9]);
      temperature = positive(fields[10]);
      pressure = positive(fields[11]);
      sourceFile = fields[12];
      sourceSheet = fields[13];
      sourceGasCell = fields[14];
      sourceLiquidCell = fields[15];
      sourceValueCell = fields[16];
      flags = fields[17];
      if (!("translation_velocity".equals(quantity) && "m/s".equals(unit))
          && !("length_over_diameter".equals(quantity) && "1".equals(unit))
          && !("frequency".equals(quantity) && "Hz".equals(unit))) {
        throw new IllegalArgumentException("Unknown quantity or inconsistent unit");
      }
    }

    /**
     * Get the fixed engineering relative tolerance. This is not an experimental confidence interval.
     *
     * @return dimensionless relative tolerance
     */
    public double getRelativeTolerance() {
      if ("translation_velocity".equals(quantity)) {
        return TRANSLATION_RELATIVE_TOLERANCE;
      }
      return "frequency".equals(quantity) ? FREQUENCY_RELATIVE_TOLERANCE : LENGTH_RELATIVE_TOLERANCE;
    }
  }

  /** Immutable comparison row; a finite tolerance pass does not remove the source limitations. */
  public static final class ComparisonRow {
    /** Original measured point. */
    public final Point point;
    /** Prediction in the measured units, or NaN when missing. */
    public final double predictedValue;
    /** Absolute relative error; positive infinity for missing or invalid predictions. */
    public final double absoluteRelativeError;
    /** Whether the independently predicted outcome is within the predeclared engineering tolerance. */
    public final boolean withinEngineeringTolerance;

    private ComparisonRow(Point point, double predictedValue) {
      this.point = point;
      this.predictedValue = predictedValue;
      absoluteRelativeError = Double.isFinite(predictedValue) && predictedValue > 0.0
          ? Math.abs(predictedValue - point.measuredValue) / point.measuredValue
          : Double.POSITIVE_INFINITY;
      withinEngineeringTolerance = absoluteRelativeError <= point.getRelativeTolerance();
    }
  }
}
