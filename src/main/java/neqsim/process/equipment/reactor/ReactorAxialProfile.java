package neqsim.process.equipment.reactor;

import java.io.Serializable;

/**
 * Container for axial profiles from plug flow reactor simulation.
 *
 * <p>
 * Stores temperature, pressure, conversion, molar flow, and reaction rate profiles along the
 * reactor length. Provides interpolation and export methods for post-processing and visualization.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * PlugFlowReactor pfr = new PlugFlowReactor("PFR", feed);
 * pfr.run();
 * ReactorAxialProfile profile = pfr.getAxialProfile();
 * double[] temps = profile.getTemperatureProfile();
 * String json = profile.toJson();
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public class ReactorAxialProfile implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Axial positions [m]. */
  private double[] position;

  /** Temperature profile [K]. */
  private double[] temperature;

  /** Pressure profile [bara]. */
  private double[] pressure;

  /** Conversion profile of key component [-]. */
  private double[] conversion;

  /** Molar flow profiles [mol/s] indexed as [step][component]. */
  private double[][] molarFlows;

  /** Total reaction rate at each position [mol/(m3*s)]. */
  private double[] reactionRate;

  /** Component names corresponding to molarFlows columns. */
  private String[] componentNames;

  /** Number of axial steps stored. */
  private int numberOfSteps;

  /**
   * Constructor for ReactorAxialProfile.
   *
   * @param numberOfSteps number of axial discretization points
   * @param numberOfComponents number of chemical components
   * @param componentNames names of components
   */
  public ReactorAxialProfile(int numberOfSteps, int numberOfComponents, String[] componentNames) {
    this.numberOfSteps = numberOfSteps;
    this.componentNames = componentNames;
    this.position = new double[numberOfSteps];
    this.temperature = new double[numberOfSteps];
    this.pressure = new double[numberOfSteps];
    this.conversion = new double[numberOfSteps];
    this.reactionRate = new double[numberOfSteps];
    this.molarFlows = new double[numberOfSteps][numberOfComponents];
  }

  /**
   * Set data at a specific axial step.
   *
   * @param step step index (0-based)
   * @param z axial position [m]
   * @param temp temperature [K]
   * @param press pressure [bara]
   * @param conv conversion of key component [-]
   * @param rate total reaction rate [mol/(m3*s)]
   * @param flows molar flows for each component [mol/s]
   */
  public void setData(int step, double z, double temp, double press, double conv, double rate,
      double[] flows) {
    position[step] = z;
    temperature[step] = temp;
    pressure[step] = press;
    conversion[step] = conv;
    reactionRate[step] = rate;
    if (flows != null) {
      System.arraycopy(flows, 0, molarFlows[step], 0,
          Math.min(flows.length, molarFlows[step].length));
    }
  }

  /**
   * Get interpolated temperature at a given axial position.
   *
   * @param z axial position [m]
   * @return interpolated temperature [K]
   */
  public double getTemperatureAt(double z) {
    return interpolate(position, temperature, z);
  }

  /**
   * Get interpolated conversion at a given axial position.
   *
   * @param z axial position [m]
   * @return interpolated conversion [-]
   */
  public double getConversionAt(double z) {
    return interpolate(position, conversion, z);
  }

  /**
   * Get interpolated pressure at a given axial position.
   *
   * @param z axial position [m]
   * @return interpolated pressure [bara]
   */
  public double getPressureAt(double z) {
    return interpolate(position, pressure, z);
  }

  /**
   * Linear interpolation helper.
   *
   * @param xArr independent variable array
   * @param yArr dependent variable array
   * @param x target interpolation point
   * @return interpolated y value
   */
  private double interpolate(double[] xArr, double[] yArr, double x) {
    if (numberOfSteps < 2) {
      return yArr[0];
    }
    if (x <= xArr[0]) {
      return yArr[0];
    }
    if (x >= xArr[numberOfSteps - 1]) {
      return yArr[numberOfSteps - 1];
    }
    for (int i = 0; i < numberOfSteps - 1; i++) {
      if (x >= xArr[i] && x <= xArr[i + 1]) {
        double fraction = (x - xArr[i]) / (xArr[i + 1] - xArr[i]);
        return yArr[i] + fraction * (yArr[i + 1] - yArr[i]);
      }
    }
    return yArr[numberOfSteps - 1];
  }

  /**
   * Export profile data as JSON string.
   *
   * @return JSON representation of the axial profile
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"numberOfSteps\": ").append(numberOfSteps).append(",\n");
    sb.append("  \"components\": [");
    for (int i = 0; i < componentNames.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append("\"").append(componentNames[i]).append("\"");
    }
    sb.append("],\n");

    sb.append("  \"position_m\": ").append(arrayToJson(position)).append(",\n");
    sb.append("  \"temperature_K\": ").append(arrayToJson(temperature)).append(",\n");
    sb.append("  \"pressure_bara\": ").append(arrayToJson(pressure)).append(",\n");
    sb.append("  \"conversion\": ").append(arrayToJson(conversion)).append(",\n");
    sb.append("  \"reactionRate_mol_m3s\": ").append(arrayToJson(reactionRate)).append(",\n");

    sb.append("  \"molarFlows_mol_s\": [\n");
    for (int i = 0; i < numberOfSteps; i++) {
      sb.append("    ").append(arrayToJson(molarFlows[i]));
      if (i < numberOfSteps - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");
    sb.append("}");

    return sb.toString();
  }

  /**
   * Export profile data as CSV string.
   *
   * @return CSV representation with header row
   */
  public String toCSV() {
    StringBuilder sb = new StringBuilder();

    // Header
    sb.append("Position_m,Temperature_K,Pressure_bara,Conversion,ReactionRate_mol_m3s");
    for (String name : componentNames) {
      sb.append(",F_").append(name).append("_mol_s");
    }
    sb.append("\n");

    // Data rows
    for (int i = 0; i < numberOfSteps; i++) {
      sb.append(String.format("%.6f,%.4f,%.6f,%.8f,%.6e", position[i], temperature[i], pressure[i],
          conversion[i], reactionRate[i]));
      for (int j = 0; j < componentNames.length; j++) {
        sb.append(String.format(",%.8e", molarFlows[i][j]));
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * Convert a double array to JSON array string.
   *
   * @param arr the array to convert
   * @return JSON array string
   */
  private String arrayToJson(double[] arr) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < arr.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      if (Double.isNaN(arr[i]) || Double.isInfinite(arr[i])) {
        sb.append("null");
      } else {
        sb.append(String.format("%.8g", arr[i]));
      }
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * Get the position array.
   *
   * @return axial position array [m]
   */
  public double[] getPositionProfile() {
    return position;
  }

  /**
   * Get the temperature profile.
   *
   * @return temperature array [K]
   */
  public double[] getTemperatureProfile() {
    return temperature;
  }

  /**
   * Get the pressure profile.
   *
   * @return pressure array [bara]
   */
  public double[] getPressureProfile() {
    return pressure;
  }

  /**
   * Get the conversion profile.
   *
   * @return conversion array [-]
   */
  public double[] getConversionProfile() {
    return conversion;
  }

  /**
   * Get the reaction rate profile.
   *
   * @return reaction rate array [mol/(m3*s)]
   */
  public double[] getReactionRateProfile() {
    return reactionRate;
  }

  /**
   * Get the molar flow profiles.
   *
   * @return 2D array [step][component] of molar flows [mol/s]
   */
  public double[][] getMolarFlowProfiles() {
    return molarFlows;
  }

  /**
   * Get the component names.
   *
   * @return array of component names
   */
  public String[] getComponentNames() {
    return componentNames;
  }

  /**
   * Get the number of axial steps.
   *
   * @return number of steps
   */
  public int getNumberOfSteps() {
    return numberOfSteps;
  }
}
