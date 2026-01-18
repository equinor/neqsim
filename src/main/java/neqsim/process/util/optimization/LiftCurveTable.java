package neqsim.process.util.optimization;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Represents a lift curve table for reservoir simulator integration.
 *
 * <p>
 * This class stores flow rate vs. pressure data in a format compatible with Eclipse reservoir
 * simulators. The table contains:
 * </p>
 * <ul>
 * <li>THP (Tubing Head Pressure / outlet pressure) values as column headers</li>
 * <li>Flow rates as row headers</li>
 * <li>BHP (Bottom Hole Pressure / inlet pressure) values in the table cells</li>
 * <li>NaN values for infeasible operating points</li>
 * </ul>
 *
 * <h2>Eclipse Format Example</h2>
 * 
 * <pre>
 * THP
 * 20 40 60
 * 1   100  120  140
 * 10  110  130  150
 * 20  120  140  NaN
 * </pre>
 *
 * <p>
 * In this format:
 * </p>
 * <ul>
 * <li>First row "THP" indicates pressure column headers</li>
 * <li>Second row contains THP values (20, 40, 60 bara)</li>
 * <li>Subsequent rows: first column is flow rate, remaining columns are BHP values</li>
 * <li>NaN indicates the operating point is infeasible (e.g., constraint violation)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class LiftCurveTable implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** THP (outlet pressure) values - column headers. */
  private double[] thpValues;

  /** Flow rate values - row headers. */
  private double[] flowRates;

  /** BHP (inlet pressure) matrix [flowIndex][thpIndex]. */
  private double[][] bhpValues;

  /** Unit for THP and BHP pressures. */
  private String pressureUnit = "bara";

  /** Unit for flow rates. */
  private String flowRateUnit = "kg/hr";

  /** Table name/identifier. */
  private String tableName = "VLP";

  /** Optional comments/metadata. */
  private String comments = "";

  /**
   * Creates an empty lift curve table.
   */
  public LiftCurveTable() {}

  /**
   * Creates a lift curve table with specified dimensions.
   *
   * @param numFlowRates number of flow rate points
   * @param numThpValues number of THP points
   */
  public LiftCurveTable(int numFlowRates, int numThpValues) {
    this.flowRates = new double[numFlowRates];
    this.thpValues = new double[numThpValues];
    this.bhpValues = new double[numFlowRates][numThpValues];

    // Initialize BHP values to NaN
    for (int i = 0; i < numFlowRates; i++) {
      Arrays.fill(bhpValues[i], Double.NaN);
    }
  }

  /**
   * Creates a lift curve table with specified values.
   *
   * @param flowRates flow rate values (row headers)
   * @param thpValues THP values (column headers)
   * @param bhpValues BHP values [flowIndex][thpIndex]
   */
  public LiftCurveTable(double[] flowRates, double[] thpValues, double[][] bhpValues) {
    this.flowRates = flowRates.clone();
    this.thpValues = thpValues.clone();
    this.bhpValues = new double[bhpValues.length][];
    for (int i = 0; i < bhpValues.length; i++) {
      this.bhpValues[i] = bhpValues[i].clone();
    }
  }

  /**
   * Converts the table to Eclipse-compatible format.
   *
   * <p>
   * Format:
   * </p>
   * 
   * <pre>
   * THP
   * thp1 thp2 thp3
   * flow1 bhp11 bhp12 bhp13
   * flow2 bhp21 bhp22 bhp23
   * </pre>
   *
   * @return Eclipse format string
   */
  public String toEclipseFormat() {
    StringBuilder sb = new StringBuilder();

    // Header line
    sb.append("THP\n");

    // THP values line
    for (int j = 0; j < thpValues.length; j++) {
      if (j > 0) {
        sb.append(" ");
      }
      sb.append(formatNumber(thpValues[j]));
    }
    sb.append("\n");

    // Data rows: flow rate followed by BHP values
    for (int i = 0; i < flowRates.length; i++) {
      sb.append(formatNumber(flowRates[i]));
      for (int j = 0; j < thpValues.length; j++) {
        sb.append("  ");
        if (Double.isNaN(bhpValues[i][j])) {
          sb.append("NaN");
        } else {
          sb.append(formatNumber(bhpValues[i][j]));
        }
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * Converts the table to CSV format.
   *
   * @return CSV format string
   */
  public String toCSV() {
    StringBuilder sb = new StringBuilder();

    // Header row: FlowRate, THP1, THP2, ...
    sb.append("FlowRate(" + flowRateUnit + ")");
    for (int j = 0; j < thpValues.length; j++) {
      sb.append(",THP=" + formatNumber(thpValues[j]) + "(" + pressureUnit + ")");
    }
    sb.append("\n");

    // Data rows
    for (int i = 0; i < flowRates.length; i++) {
      sb.append(formatNumber(flowRates[i]));
      for (int j = 0; j < thpValues.length; j++) {
        sb.append(",");
        if (Double.isNaN(bhpValues[i][j])) {
          sb.append("NaN");
        } else {
          sb.append(formatNumber(bhpValues[i][j]));
        }
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * Converts the table to a JSON representation.
   *
   * @return JSON string
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"tableName\": \"" + tableName + "\",\n");
    sb.append("  \"pressureUnit\": \"" + pressureUnit + "\",\n");
    sb.append("  \"flowRateUnit\": \"" + flowRateUnit + "\",\n");

    // THP values
    sb.append("  \"thpValues\": [");
    for (int j = 0; j < thpValues.length; j++) {
      if (j > 0) {
        sb.append(", ");
      }
      sb.append(formatNumber(thpValues[j]));
    }
    sb.append("],\n");

    // Flow rates
    sb.append("  \"flowRates\": [");
    for (int i = 0; i < flowRates.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(formatNumber(flowRates[i]));
    }
    sb.append("],\n");

    // BHP matrix
    sb.append("  \"bhpValues\": [\n");
    for (int i = 0; i < flowRates.length; i++) {
      sb.append("    [");
      for (int j = 0; j < thpValues.length; j++) {
        if (j > 0) {
          sb.append(", ");
        }
        if (Double.isNaN(bhpValues[i][j])) {
          sb.append("null");
        } else {
          sb.append(formatNumber(bhpValues[i][j]));
        }
      }
      sb.append("]");
      if (i < flowRates.length - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");

    sb.append("}");
    return sb.toString();
  }

  /**
   * Gets the raw data as a 2D array including row/column headers.
   *
   * <p>
   * Returns array where:
   * </p>
   * <ul>
   * <li>[0][0] = 0 (corner)</li>
   * <li>[0][1..n] = THP values</li>
   * <li>[1..m][0] = flow rates</li>
   * <li>[1..m][1..n] = BHP values</li>
   * </ul>
   *
   * @return 2D array with headers and data
   */
  public double[][] getRawDataWithHeaders() {
    int rows = flowRates.length + 1;
    int cols = thpValues.length + 1;
    double[][] data = new double[rows][cols];

    // Corner
    data[0][0] = 0;

    // THP header row
    for (int j = 0; j < thpValues.length; j++) {
      data[0][j + 1] = thpValues[j];
    }

    // Flow rate column and BHP data
    for (int i = 0; i < flowRates.length; i++) {
      data[i + 1][0] = flowRates[i];
      for (int j = 0; j < thpValues.length; j++) {
        data[i + 1][j + 1] = bhpValues[i][j];
      }
    }

    return data;
  }

  /**
   * Gets the BHP value at specified flow rate and THP indices.
   *
   * @param flowIndex flow rate index
   * @param thpIndex THP index
   * @return BHP value, or NaN if infeasible
   */
  public double getBHP(int flowIndex, int thpIndex) {
    return bhpValues[flowIndex][thpIndex];
  }

  /**
   * Sets the BHP value at specified indices.
   *
   * @param flowIndex flow rate index
   * @param thpIndex THP index
   * @param bhp BHP value (or NaN for infeasible)
   */
  public void setBHP(int flowIndex, int thpIndex, double bhp) {
    bhpValues[flowIndex][thpIndex] = bhp;
  }

  /**
   * Interpolates BHP for given flow rate and THP using bilinear interpolation.
   *
   * @param flowRate flow rate
   * @param thp THP value
   * @return interpolated BHP, or NaN if outside bounds or near infeasible points
   */
  public double interpolateBHP(double flowRate, double thp) {
    // Find bounding indices for flow rate
    int flowLowIdx = -1;
    int flowHighIdx = -1;
    for (int i = 0; i < flowRates.length - 1; i++) {
      if (flowRates[i] <= flowRate && flowRate <= flowRates[i + 1]) {
        flowLowIdx = i;
        flowHighIdx = i + 1;
        break;
      }
    }

    // Find bounding indices for THP
    int thpLowIdx = -1;
    int thpHighIdx = -1;
    for (int j = 0; j < thpValues.length - 1; j++) {
      if (thpValues[j] <= thp && thp <= thpValues[j + 1]) {
        thpLowIdx = j;
        thpHighIdx = j + 1;
        break;
      }
    }

    // Check if outside bounds
    if (flowLowIdx < 0 || thpLowIdx < 0) {
      return Double.NaN;
    }

    // Get corner values
    double q11 = bhpValues[flowLowIdx][thpLowIdx];
    double q12 = bhpValues[flowLowIdx][thpHighIdx];
    double q21 = bhpValues[flowHighIdx][thpLowIdx];
    double q22 = bhpValues[flowHighIdx][thpHighIdx];

    // Check for NaN corners
    if (Double.isNaN(q11) || Double.isNaN(q12) || Double.isNaN(q21) || Double.isNaN(q22)) {
      return Double.NaN;
    }

    // Bilinear interpolation
    double x = flowRates[flowLowIdx];
    double x2 = flowRates[flowHighIdx];
    double y = thpValues[thpLowIdx];
    double y2 = thpValues[thpHighIdx];

    double t = (flowRate - x) / (x2 - x);
    double u = (thp - y) / (y2 - y);

    return (1 - t) * (1 - u) * q11 + t * (1 - u) * q21 + (1 - t) * u * q12 + t * u * q22;
  }

  /**
   * Counts the number of feasible (non-NaN) points in the table.
   *
   * @return number of feasible points
   */
  public int countFeasiblePoints() {
    int count = 0;
    for (int i = 0; i < flowRates.length; i++) {
      for (int j = 0; j < thpValues.length; j++) {
        if (!Double.isNaN(bhpValues[i][j])) {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * Gets the total number of points in the table.
   *
   * @return total points
   */
  public int getTotalPoints() {
    return flowRates.length * thpValues.length;
  }

  /**
   * Gets the feasibility percentage.
   *
   * @return percentage of feasible points (0-100)
   */
  public double getFeasibilityPercent() {
    int total = getTotalPoints();
    if (total == 0) {
      return 0;
    }
    return 100.0 * countFeasiblePoints() / total;
  }

  /**
   * Formats a number for output.
   */
  private String formatNumber(double value) {
    if (Math.abs(value) < 0.01 || Math.abs(value) >= 10000) {
      return String.format("%.4e", value);
    }
    return String.format("%.4f", value);
  }

  // ============ Getters and Setters ============

  /**
   * Gets the THP values.
   *
   * @return THP values array
   */
  public double[] getThpValues() {
    return thpValues;
  }

  /**
   * Sets the THP values.
   *
   * @param thpValues THP values array
   */
  public void setThpValues(double[] thpValues) {
    this.thpValues = thpValues;
  }

  /**
   * Gets the flow rate values.
   *
   * @return flow rate values array
   */
  public double[] getFlowRates() {
    return flowRates;
  }

  /**
   * Sets the flow rate values.
   *
   * @param flowRates flow rate values array
   */
  public void setFlowRates(double[] flowRates) {
    this.flowRates = flowRates;
  }

  /**
   * Gets the BHP values matrix.
   *
   * @return BHP values [flowIndex][thpIndex]
   */
  public double[][] getBhpValues() {
    return bhpValues;
  }

  /**
   * Sets the BHP values matrix.
   *
   * @param bhpValues BHP values [flowIndex][thpIndex]
   */
  public void setBhpValues(double[][] bhpValues) {
    this.bhpValues = bhpValues;
  }

  /**
   * Gets the pressure unit.
   *
   * @return pressure unit
   */
  public String getPressureUnit() {
    return pressureUnit;
  }

  /**
   * Sets the pressure unit.
   *
   * @param pressureUnit pressure unit
   */
  public void setPressureUnit(String pressureUnit) {
    this.pressureUnit = pressureUnit;
  }

  /**
   * Gets the flow rate unit.
   *
   * @return flow rate unit
   */
  public String getFlowRateUnit() {
    return flowRateUnit;
  }

  /**
   * Sets the flow rate unit.
   *
   * @param flowRateUnit flow rate unit
   */
  public void setFlowRateUnit(String flowRateUnit) {
    this.flowRateUnit = flowRateUnit;
  }

  /**
   * Gets the table name.
   *
   * @return table name
   */
  public String getTableName() {
    return tableName;
  }

  /**
   * Sets the table name.
   *
   * @param tableName table name
   */
  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  /**
   * Gets the comments.
   *
   * @return comments
   */
  public String getComments() {
    return comments;
  }

  /**
   * Sets the comments.
   *
   * @param comments comments
   */
  public void setComments(String comments) {
    this.comments = comments;
  }

  @Override
  public String toString() {
    return String.format("LiftCurveTable[%s]: %d flow rates x %d THP values (%.1f%% feasible)",
        tableName, flowRates.length, thpValues.length, getFeasibilityPercent());
  }
}
