package neqsim.thermodynamicoperations.propertygenerator;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.google.gson.GsonBuilder;

/**
 * Quality checks of a generated OLGA property table.
 *
 * <p>
 * The report states whether OLGA can load the table (every value finite, densities and transport properties positive),
 * counts monotonicity violations of density along pressure and of enthalpy and entropy along temperature, separately
 * for nodes where the phase exists and for extrapolated nodes, and measures continuity across phase boundaries: the
 * largest relative jump between neighbouring nodes where the phase existence changes, against the largest jump between
 * neighbouring existing nodes. A boundary jump much larger than the interior jump marks a discontinuous extrapolation.
 * Retrograde liquid density legitimately falls with pressure, so violations at existing nodes are reported, not treated
 * as errors. {@link #writeCsv(String)} exports every node for plotting.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class OlgaTableQualityReport {
  /** Columns OLGA requires to be strictly positive. */
  private static final Set<String> POSITIVE = new HashSet<String>(Arrays.asList("ROG", "ROHL", "ROWT", "DROGDP",
      "DROHLDP", "DROWTDP", "VISG", "VISHL", "VISWT", "CPG", "CPHL", "CPWT", "TCG", "TCHL", "TCWT"));
  /** Columns that must not decrease with pressure. */
  private static final Set<String> INCREASING_WITH_PRESSURE = new HashSet<String>(Arrays.asList("ROG", "ROHL", "ROWT"));
  /** Columns that must not decrease with temperature. */
  private static final Set<String> INCREASING_WITH_TEMPERATURE = new HashSet<String>(
      Arrays.asList("HG", "HHL", "HWT", "SEG", "SEHL", "SEWT"));

  /**
   * Summary of one table column.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static class ColumnSummary {
    /** OLGA column keyword. */
    public String keyword;
    /** Smallest finite value. */
    public double minimum = Double.NaN;
    /** Largest finite value. */
    public double maximum = Double.NaN;
    /** Number of NaN or infinite values. */
    public int nonFiniteCount;
    /** Number of non-positive values in a column that must be positive. */
    public int nonPositiveCount;
    /** Monotonicity violations between two nodes where the phase exists. */
    public int monotonicityViolationsExisting;
    /** Monotonicity violations involving an extrapolated node. */
    public int monotonicityViolationsExtrapolated;
    /** Largest relative jump between neighbouring nodes where the phase exists. */
    public double maxInteriorJump;
    /** Largest relative jump between neighbouring nodes across a phase boundary. */
    public double maxBoundaryJump;
    /** Number of nodes where the phase exists, -1 when the column does not depend on a phase. */
    public int existingNodes = -1;
  }

  private final String[] keywords;
  private final double[][][] values;
  private final double[] pressures;
  private final double[] temperatures;
  private final boolean[][][] present;
  private final List<ColumnSummary> summaries = new ArrayList<ColumnSummary>();

  /**
   * Builds the report.
   *
   * @param keywords column keywords
   * @param values column values indexed [column][pressure][temperature]
   * @param pressuresBara grid pressures [bara]
   * @param temperaturesC grid temperatures [C]
   * @param present phase-existence mask per column, a null entry for columns that do not depend on one phase
   */
  public OlgaTableQualityReport(String[] keywords, double[][][] values, double[] pressuresBara, double[] temperaturesC,
      boolean[][][] present) {
    this.keywords = keywords.clone();
    this.values = copy(values);
    this.pressures = pressuresBara.clone();
    this.temperatures = temperaturesC.clone();
    this.present = copy(present);
    for (int k = 0; k < keywords.length; k++) {
      summaries.add(summarize(k));
    }
  }

  /**
   * Deep-copies a three-dimensional value array.
   *
   * @param source array to copy, may be null
   * @return independent copy with the same null entries
   */
  private static double[][][] copy(double[][][] source) {
    if (source == null) {
      return null;
    }
    double[][][] result = new double[source.length][][];
    for (int k = 0; k < source.length; k++) {
      if (source[k] != null) {
        result[k] = new double[source[k].length][];
        for (int i = 0; i < source[k].length; i++) {
          result[k][i] = source[k][i] == null ? null : source[k][i].clone();
        }
      }
    }
    return result;
  }

  /**
   * Deep-copies a three-dimensional mask array.
   *
   * @param source array to copy, may be null
   * @return independent copy with the same null entries
   */
  private static boolean[][][] copy(boolean[][][] source) {
    if (source == null) {
      return null;
    }
    boolean[][][] result = new boolean[source.length][][];
    for (int k = 0; k < source.length; k++) {
      if (source[k] != null) {
        result[k] = new boolean[source[k].length][];
        for (int i = 0; i < source[k].length; i++) {
          result[k][i] = source[k][i] == null ? null : source[k][i].clone();
        }
      }
    }
    return result;
  }

  /**
   * Summarizes one column.
   *
   * @param k column index
   * @return summary
   */
  private ColumnSummary summarize(int k) {
    ColumnSummary summary = new ColumnSummary();
    summary.keyword = keywords[k];
    double[][] column = values[k];
    boolean[][] mask = present == null ? null : present[k];
    boolean positive = POSITIVE.contains(keywords[k]);
    if (mask != null) {
      summary.existingNodes = 0;
    }
    for (int i = 0; i < pressures.length; i++) {
      for (int j = 0; j < temperatures.length; j++) {
        double value = column[i][j];
        if (mask != null && mask[i][j]) {
          summary.existingNodes++;
        }
        if (!Double.isFinite(value)) {
          summary.nonFiniteCount++;
          continue;
        }
        summary.minimum = Double.isNaN(summary.minimum) ? value : Math.min(summary.minimum, value);
        summary.maximum = Double.isNaN(summary.maximum) ? value : Math.max(summary.maximum, value);
        if (positive && value <= 0.0) {
          summary.nonPositiveCount++;
        }
        if (i + 1 < pressures.length) {
          compare(summary, column, mask, i, j, i + 1, j, INCREASING_WITH_PRESSURE.contains(keywords[k]));
        }
        if (j + 1 < temperatures.length) {
          compare(summary, column, mask, i, j, i, j + 1, INCREASING_WITH_TEMPERATURE.contains(keywords[k]));
        }
      }
    }
    return summary;
  }

  /**
   * Compares two neighbouring nodes.
   *
   * @param summary summary to update
   * @param column column values
   * @param mask phase-existence mask, or null
   * @param i1 first pressure index
   * @param j1 first temperature index
   * @param i2 second pressure index
   * @param j2 second temperature index
   * @param mustIncrease true when the value must not decrease from the first to the second node
   */
  private static void compare(ColumnSummary summary, double[][] column, boolean[][] mask, int i1, int j1, int i2,
      int j2, boolean mustIncrease) {
    double a = column[i1][j1];
    double b = column[i2][j2];
    if (!Double.isFinite(b)) {
      return;
    }
    boolean existsA = mask == null || mask[i1][j1];
    boolean existsB = mask == null || mask[i2][j2];
    if (mustIncrease && b < a) {
      if (existsA && existsB) {
        summary.monotonicityViolationsExisting++;
      } else {
        summary.monotonicityViolationsExtrapolated++;
      }
    }
    double jump = Math.abs(b - a) / Math.max(Math.max(Math.abs(a), Math.abs(b)), 1.0e-30);
    if (existsA && existsB) {
      summary.maxInteriorJump = Math.max(summary.maxInteriorJump, jump);
    } else if (existsA != existsB) {
      summary.maxBoundaryJump = Math.max(summary.maxBoundaryJump, jump);
    }
  }

  /**
   * Reports whether OLGA can load the table: every value finite and required columns positive.
   *
   * @return true when loadable
   */
  public boolean isLoadable() {
    for (ColumnSummary summary : summaries) {
      if (summary.nonFiniteCount > 0 || summary.nonPositiveCount > 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Counts monotonicity violations at extrapolated nodes over all columns.
   *
   * @return number of violations
   */
  public int getExtrapolatedMonotonicityViolations() {
    int count = 0;
    for (ColumnSummary summary : summaries) {
      count += summary.monotonicityViolationsExtrapolated;
    }
    return count;
  }

  /**
   * Returns the summary of one column.
   *
   * @param keyword OLGA column keyword
   * @return summary, or null when the table has no such column
   */
  public ColumnSummary getColumn(String keyword) {
    for (ColumnSummary summary : summaries) {
      if (summary.keyword.equals(keyword)) {
        return summary;
      }
    }
    return null;
  }

  /**
   * Returns all column summaries.
   *
   * @return unmodifiable list of summaries
   */
  public List<ColumnSummary> getColumns() {
    return Collections.unmodifiableList(summaries);
  }

  /**
   * Serializes the report.
   *
   * @return JSON text
   */
  public String toJson() {
    java.util.Map<String, Object> root = new java.util.LinkedHashMap<String, Object>();
    root.put("loadable", isLoadable());
    root.put("pressureCount", pressures.length);
    root.put("temperatureCount", temperatures.length);
    root.put("columns", summaries);
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(root);
  }

  /**
   * Writes every node in long format for plotting: pressure, temperature, keyword, value and phase existence.
   *
   * @param filename output CSV file
   * @throws IOException if the file cannot be written
   */
  public void writeCsv(String filename) throws IOException {
    try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), "utf-8"))) {
      writer.write("pressure_bara,temperature_C,keyword,value,phase_exists\n");
      for (int k = 0; k < keywords.length; k++) {
        boolean[][] mask = present == null ? null : present[k];
        for (int i = 0; i < pressures.length; i++) {
          for (int j = 0; j < temperatures.length; j++) {
            String exists = mask == null ? "" : Boolean.toString(mask[i][j]);
            writer.write(
                pressures[i] + "," + temperatures[j] + "," + keywords[k] + "," + values[k][i][j] + "," + exists + "\n");
          }
        }
      }
    }
  }
}
