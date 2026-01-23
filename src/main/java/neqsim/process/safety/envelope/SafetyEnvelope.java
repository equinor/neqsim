package neqsim.process.safety.envelope;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Container for safety envelope data points.
 *
 * <p>
 * Stores P-T curves for various safety limits including hydrate formation, wax appearance, CO2
 * freezing, and minimum design metal temperature (MDMT). Provides export capabilities for DCS/SCADA
 * integration.
 * </p>
 *
 * @author NeqSim team
 */
public class SafetyEnvelope {

  private final String name;
  private final EnvelopeType type;

  // P-T curve data
  private double[] pressure; // bara
  private double[] temperature; // K
  private double[] margin; // optional safety margin at each point

  // Metadata
  private String fluidDescription;
  private double referenceWaterContent; // mol fraction (for hydrate)
  private double referenceWaxContent; // mol fraction (for wax)

  /**
   * Types of safety envelopes.
   */
  public enum EnvelopeType {
    /** Hydrate formation boundary. */
    HYDRATE("Hydrate Formation"),
    /** Wax appearance temperature boundary. */
    WAX("Wax Appearance"),
    /** CO2 solid formation boundary. */
    CO2_FREEZING("CO2 Freezing"),
    /** Minimum design metal temperature. */
    MDMT("MDMT"),
    /** Phase envelope (two-phase region). */
    PHASE_ENVELOPE("Phase Envelope"),
    /** Brittle fracture avoidance. */
    BRITTLE_FRACTURE("Brittle Fracture"),
    /** Custom user-defined envelope. */
    CUSTOM("Custom");

    private final String displayName;

    EnvelopeType(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * Creates a new safety envelope.
   *
   * @param name envelope identifier
   * @param type type of safety limit
   * @param numPoints number of data points
   */
  public SafetyEnvelope(String name, EnvelopeType type, int numPoints) {
    this.name = name;
    this.type = type;
    this.pressure = new double[numPoints];
    this.temperature = new double[numPoints];
    this.margin = new double[numPoints];
    this.fluidDescription = "";
  }

  // Package-private setters for SafetyEnvelopeCalculator

  void setDataPoint(int index, double p, double t) {
    setDataPoint(index, p, t, 0.0);
  }

  void setDataPoint(int index, double p, double t, double safetyMargin) {
    if (index >= 0 && index < pressure.length) {
      pressure[index] = p;
      temperature[index] = t;
      margin[index] = safetyMargin;
    }
  }

  void setFluidDescription(String description) {
    this.fluidDescription = description;
  }

  void setReferenceWaterContent(double waterContent) {
    this.referenceWaterContent = waterContent;
  }

  void setReferenceWaxContent(double waxContent) {
    this.referenceWaxContent = waxContent;
  }

  // Public getters

  public String getName() {
    return name;
  }

  public EnvelopeType getType() {
    return type;
  }

  public String getFluidDescription() {
    return fluidDescription;
  }

  public int getNumberOfPoints() {
    return pressure.length;
  }

  public double[] getPressure() {
    return Arrays.copyOf(pressure, pressure.length);
  }

  public double[] getTemperature() {
    return Arrays.copyOf(temperature, temperature.length);
  }

  public double[] getMargin() {
    return Arrays.copyOf(margin, margin.length);
  }

  public double getReferenceWaterContent() {
    return referenceWaterContent;
  }

  public double getReferenceWaxContent() {
    return referenceWaxContent;
  }

  /**
   * Gets the temperature limit at a given pressure.
   *
   * <p>
   * Interpolates between data points if necessary.
   * </p>
   *
   * @param pressureBara pressure in bara
   * @return temperature limit in Kelvin, or NaN if outside range
   */
  public double getTemperatureAtPressure(double pressureBara) {
    // Find bracketing points
    for (int i = 0; i < pressure.length - 1; i++) {
      if ((pressure[i] <= pressureBara && pressure[i + 1] >= pressureBara)
          || (pressure[i] >= pressureBara && pressure[i + 1] <= pressureBara)) {
        // Linear interpolation
        double dp = pressure[i + 1] - pressure[i];
        if (Math.abs(dp) < 1e-10) {
          return temperature[i];
        }
        double fraction = (pressureBara - pressure[i]) / dp;
        return temperature[i] + fraction * (temperature[i + 1] - temperature[i]);
      }
    }
    return Double.NaN;
  }

  /**
   * Gets the temperature limit with safety margin applied.
   *
   * @param pressureBara pressure in bara
   * @return temperature limit minus margin in Kelvin
   */
  public double getSafeTemperatureAtPressure(double pressureBara) {
    double temp = getTemperatureAtPressure(pressureBara);
    if (Double.isNaN(temp)) {
      return temp;
    }

    // Interpolate margin
    for (int i = 0; i < pressure.length - 1; i++) {
      if ((pressure[i] <= pressureBara && pressure[i + 1] >= pressureBara)
          || (pressure[i] >= pressureBara && pressure[i + 1] <= pressureBara)) {
        double dp = pressure[i + 1] - pressure[i];
        if (Math.abs(dp) < 1e-10) {
          return temp - margin[i];
        }
        double fraction = (pressureBara - pressure[i]) / dp;
        double interpMargin = margin[i] + fraction * (margin[i + 1] - margin[i]);
        return temp - interpMargin;
      }
    }
    return temp;
  }

  /**
   * Checks if an operating point is within the safe envelope.
   *
   * @param pressureBara operating pressure in bara
   * @param temperatureK operating temperature in Kelvin
   * @return true if safe (above hydrate/WAT/MDMT curves, below phase envelope upper limit)
   */
  public boolean isOperatingPointSafe(double pressureBara, double temperatureK) {
    double limitTemp = getSafeTemperatureAtPressure(pressureBara);
    if (Double.isNaN(limitTemp)) {
      return true; // Outside envelope range, assume safe
    }

    // For hydrate, WAT, MDMT: operating temp must be ABOVE the limit
    // For phase envelope: depends on context (bubble vs dew point)
    switch (type) {
      case HYDRATE:
      case WAX:
      case MDMT:
      case BRITTLE_FRACTURE:
      case CO2_FREEZING:
        return temperatureK > limitTemp;
      case PHASE_ENVELOPE:
        // For phase envelope, we're checking if inside two-phase region
        // This is simplified - full implementation would check both bubble and dew
        return true;
      default:
        return true;
    }
  }

  /**
   * Calculates the margin to the safety limit at given conditions.
   *
   * @param pressureBara operating pressure in bara
   * @param temperatureK operating temperature in Kelvin
   * @return margin in Kelvin (positive = safe, negative = unsafe)
   */
  public double calculateMarginToLimit(double pressureBara, double temperatureK) {
    double limitTemp = getTemperatureAtPressure(pressureBara);
    if (Double.isNaN(limitTemp)) {
      return Double.POSITIVE_INFINITY; // Outside range
    }

    switch (type) {
      case HYDRATE:
      case WAX:
      case MDMT:
      case BRITTLE_FRACTURE:
      case CO2_FREEZING:
        return temperatureK - limitTemp;
      default:
        return temperatureK - limitTemp;
    }
  }

  /**
   * Exports envelope to CSV format for DCS/SCADA import.
   *
   * @param filename output file path
   */
  public void exportToCSV(String filename) {
    try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
      writer.println("# Safety Envelope: " + name);
      writer.println("# Type: " + type.getDisplayName());
      writer.println("# Fluid: " + fluidDescription);
      writer.println();
      writer.println("Pressure_bara,Temperature_K,Temperature_C,Margin_K");

      for (int i = 0; i < pressure.length; i++) {
        writer.printf("%.4f,%.4f,%.4f,%.4f%n", pressure[i], temperature[i], temperature[i] - 273.15,
            margin[i]);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to export safety envelope: " + e.getMessage(), e);
    }
  }

  /**
   * Exports envelope to JSON format.
   *
   * @param filename output file path
   */
  public void exportToJSON(String filename) {
    try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
      writer.println("{");
      writer.println("  \"name\": \"" + name + "\",");
      writer.println("  \"type\": \"" + type.name() + "\",");
      writer.println("  \"displayType\": \"" + type.getDisplayName() + "\",");
      writer.println("  \"fluidDescription\": \"" + fluidDescription + "\",");
      writer.println("  \"points\": [");

      for (int i = 0; i < pressure.length; i++) {
        String comma = (i < pressure.length - 1) ? "," : "";
        writer.printf(
            "    {\"pressure\": %.4f, \"temperature\": %.4f, \"temperatureC\": %.4f, \"margin\": %.4f}%s%n",
            pressure[i], temperature[i], temperature[i] - 273.15, margin[i], comma);
      }

      writer.println("  ]");
      writer.println("}");

    } catch (IOException e) {
      throw new RuntimeException("Failed to export safety envelope: " + e.getMessage(), e);
    }
  }

  /**
   * Exports to OSIsoft PI-compatible format.
   *
   * @param tagPrefix PI tag prefix for the data
   * @param filename output file path
   */
  public void exportToPIFormat(String tagPrefix, String filename) {
    try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
      writer.println("# PI DataLink Export");
      writer.println("# Tag format: " + tagPrefix + "_P{pressure}_LIMIT");
      writer.println();

      for (int i = 0; i < pressure.length; i++) {
        String tag = String.format("%s_P%.0f_LIMIT", tagPrefix, pressure[i]);
        writer.printf("%s,%.4f%n", tag, temperature[i] - 273.15); // Export in Celsius
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to export to PI format: " + e.getMessage(), e);
    }
  }

  /**
   * Exports to Seeq-compatible format.
   *
   * @param filename output file path
   */
  public void exportToSeeq(String filename) {
    try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
      writer.println("Name,Type,Interpolation Method,Maximum Interpolation");
      writer.println(name + "," + type.getDisplayName() + ",Linear,1h");
      writer.println();
      writer.println("Pressure (bara),Limit Temperature (°C),Safety Margin (K)");

      for (int i = 0; i < pressure.length; i++) {
        writer.printf("%.4f,%.4f,%.4f%n", pressure[i], temperature[i] - 273.15, margin[i]);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to export to Seeq format: " + e.getMessage(), e);
    }
  }

  @Override
  public String toString() {
    double minP = Arrays.stream(pressure).min().orElse(0);
    double maxP = Arrays.stream(pressure).max().orElse(0);
    double minT = Arrays.stream(temperature).min().orElse(0);
    double maxT = Arrays.stream(temperature).max().orElse(0);

    return String.format("SafetyEnvelope[%s, %s, P=%.1f-%.1f bara, T=%.1f-%.1f K, %d points]", name,
        type.getDisplayName(), minP, maxP, minT, maxT, pressure.length);
  }
}
