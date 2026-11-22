package neqsim.process.operations.replay;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Column-oriented time series consumed by {@link ProcessReplay}.
 *
 * <p>
 * Time stamps are plain doubles in a unit of the caller's choice (for example seconds since the start of the window or
 * epoch seconds). Each column holds one value per time stamp; missing values are {@code NaN}.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ReplayTimeSeries implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double[] timestamps;
  private final Map<String, double[]> columns = new LinkedHashMap<String, double[]>();

  /**
   * Creates a time series with the given time stamps and no columns.
   *
   * @param timestamps time stamp per row; must not be null or empty
   */
  public ReplayTimeSeries(double[] timestamps) {
    if (timestamps == null || timestamps.length == 0) {
      throw new IllegalArgumentException("timestamps must not be empty");
    }
    this.timestamps = Arrays.copyOf(timestamps, timestamps.length);
  }

  /**
   * Creates a time series from time stamps and a column map.
   *
   * @param timestamps time stamp per row
   * @param columns map from tag to values; each array must have the same length as the time stamps
   */
  public ReplayTimeSeries(double[] timestamps, Map<String, double[]> columns) {
    this(timestamps);
    if (columns != null) {
      for (Map.Entry<String, double[]> entry : columns.entrySet()) {
        addColumn(entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Creates a time series with equidistant time stamps {@code 0, step, 2 step, ...}.
   *
   * @param rows number of rows; must be at least 1
   * @param step time step between rows
   * @return empty time series with the generated time stamps
   */
  public static ReplayTimeSeries uniform(int rows, double step) {
    if (rows < 1) {
      throw new IllegalArgumentException("rows must be at least 1");
    }
    double[] time = new double[rows];
    for (int i = 0; i < rows; i++) {
      time[i] = i * step;
    }
    return new ReplayTimeSeries(time);
  }

  /**
   * Reads a comma-separated file whose first column is the time stamp and whose header names the tags. Empty or
   * non-numeric cells become {@code NaN}.
   *
   * @param path path of the CSV file
   * @return the time series
   * @throws IOException if the file cannot be read
   */
  public static ReplayTimeSeries fromCsv(String path) throws IOException {
    List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
    return fromCsvLines(lines);
  }

  /**
   * Parses CSV lines whose first column is the time stamp and whose header names the tags.
   *
   * @param lines CSV lines including the header
   * @return the time series
   */
  public static ReplayTimeSeries fromCsvLines(List<String> lines) {
    List<String> rows = new ArrayList<String>();
    for (String line : lines) {
      if (line != null && !line.trim().isEmpty()) {
        rows.add(line);
      }
    }
    if (rows.size() < 2) {
      throw new IllegalArgumentException("CSV must contain a header and at least one data row");
    }
    String[] header = rows.get(0).split(",", -1);
    int n = rows.size() - 1;
    double[] time = new double[n];
    double[][] values = new double[header.length][n];
    for (int r = 0; r < n; r++) {
      String[] cells = rows.get(r + 1).split(",", -1);
      for (int c = 0; c < header.length; c++) {
        double value = c < cells.length ? parse(cells[c]) : Double.NaN;
        if (c == 0) {
          time[r] = value;
        } else {
          values[c][r] = value;
        }
      }
    }
    ReplayTimeSeries series = new ReplayTimeSeries(time);
    for (int c = 1; c < header.length; c++) {
      series.addColumn(header[c].trim(), values[c]);
    }
    return series;
  }

  /**
   * Parses a CSV cell as a double.
   *
   * @param cell text cell
   * @return parsed value, or NaN when empty or not numeric
   */
  private static double parse(String cell) {
    String text = cell == null ? "" : cell.trim();
    if (text.isEmpty()) {
      return Double.NaN;
    }
    try {
      return Double.parseDouble(text);
    } catch (NumberFormatException ex) {
      return Double.NaN;
    }
  }

  /**
   * Adds or replaces a column.
   *
   * @param tag column name
   * @param values one value per time stamp
   * @return this time series
   */
  public ReplayTimeSeries addColumn(String tag, double[] values) {
    if (tag == null || tag.trim().isEmpty()) {
      throw new IllegalArgumentException("tag must not be empty");
    }
    if (values == null || values.length != timestamps.length) {
      throw new IllegalArgumentException("column '" + tag + "' must have " + timestamps.length + " values, was "
          + (values == null ? 0 : values.length));
    }
    columns.put(tag.trim(), Arrays.copyOf(values, values.length));
    return this;
  }

  /**
   * Returns the number of rows.
   *
   * @return row count
   */
  public int size() {
    return timestamps.length;
  }

  /**
   * Returns the time stamp of a row.
   *
   * @param row row index
   * @return time stamp
   */
  public double getTime(int row) {
    return timestamps[row];
  }

  /**
   * Checks whether a column exists.
   *
   * @param tag column name
   * @return true when the column exists
   */
  public boolean hasTag(String tag) {
    return columns.containsKey(tag);
  }

  /**
   * Returns a value, or {@code NaN} when the column does not exist.
   *
   * @param tag column name
   * @param row row index
   * @return value or NaN
   */
  public double getValue(String tag, int row) {
    double[] values = columns.get(tag);
    return values == null ? Double.NaN : values[row];
  }

  /**
   * Returns the values of one row for all columns.
   *
   * @param row row index
   * @return map from tag to value in column order
   */
  public Map<String, Double> getRow(int row) {
    Map<String, Double> values = new LinkedHashMap<String, Double>();
    for (Map.Entry<String, double[]> entry : columns.entrySet()) {
      values.put(entry.getKey(), entry.getValue()[row]);
    }
    return values;
  }

  /**
   * Returns the column names in insertion order.
   *
   * @return unmodifiable list of tags
   */
  public List<String> getTags() {
    return Collections.unmodifiableList(new ArrayList<String>(columns.keySet()));
  }

  /**
   * Returns the index of the first row whose time stamp is at least the given time.
   *
   * @param time time stamp to search for
   * @return row index, or {@link #size()} when all time stamps are earlier
   */
  public int indexAtOrAfter(double time) {
    for (int i = 0; i < timestamps.length; i++) {
      if (timestamps[i] >= time) {
        return i;
      }
    }
    return timestamps.length;
  }
}
