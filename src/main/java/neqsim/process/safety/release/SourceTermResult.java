package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Container for time-series release source term data.
 *
 * <p>
 * This class holds the results of a leak/rupture calculation including:
 * <ul>
 * <li>Mass flow rate vs time</li>
 * <li>Temperature vs time</li>
 * <li>Vapor/liquid split vs time</li>
 * <li>Jet properties (velocity, momentum)</li>
 * <li>Droplet size estimates for liquid releases</li>
 * </ul>
 *
 * <p>
 * The data can be exported to common QRA tools:
 * <ul>
 * <li>PHAST format</li>
 * <li>FLACS format</li>
 * <li>KFX format</li>
 * <li>OpenFOAM format</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 * @see LeakModel
 */
public class SourceTermResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String scenarioName;
  private final double holeDiameter; // m
  private final ReleaseOrientation orientation;

  // Time series data
  private double[] time; // s
  private double[] massFlowRate; // kg/s
  private double[] temperature; // K
  private double[] pressure; // Pa
  private double[] vaporMassFraction; // kg/kg
  private double[] jetVelocity; // m/s
  private double[] jetMomentum; // N
  private double[] liquidDropletSMD; // m (Sauter Mean Diameter)

  // Cumulative values
  private double totalMassReleased; // kg
  private double peakMassFlowRate; // kg/s
  private double timeToEmpty; // s

  /**
   * Creates a source term result.
   *
   * @param scenarioName name of the release scenario
   * @param holeDiameter hole diameter [m]
   * @param orientation release orientation
   * @param numPoints number of time points
   */
  public SourceTermResult(String scenarioName, double holeDiameter, ReleaseOrientation orientation,
      int numPoints) {
    this.scenarioName = scenarioName;
    this.holeDiameter = holeDiameter;
    this.orientation = orientation;

    this.time = new double[numPoints];
    this.massFlowRate = new double[numPoints];
    this.temperature = new double[numPoints];
    this.pressure = new double[numPoints];
    this.vaporMassFraction = new double[numPoints];
    this.jetVelocity = new double[numPoints];
    this.jetMomentum = new double[numPoints];
    this.liquidDropletSMD = new double[numPoints];
  }

  // Getters for time series data

  public String getScenarioName() {
    return scenarioName;
  }

  public double getHoleDiameter() {
    return holeDiameter;
  }

  public ReleaseOrientation getOrientation() {
    return orientation;
  }

  public double[] getTime() {
    return Arrays.copyOf(time, time.length);
  }

  public double[] getMassFlowRate() {
    return Arrays.copyOf(massFlowRate, massFlowRate.length);
  }

  public double[] getTemperature() {
    return Arrays.copyOf(temperature, temperature.length);
  }

  public double[] getPressure() {
    return Arrays.copyOf(pressure, pressure.length);
  }

  public double[] getVaporMassFraction() {
    return Arrays.copyOf(vaporMassFraction, vaporMassFraction.length);
  }

  public double[] getJetVelocity() {
    return Arrays.copyOf(jetVelocity, jetVelocity.length);
  }

  public double[] getJetMomentum() {
    return Arrays.copyOf(jetMomentum, jetMomentum.length);
  }

  public double[] getLiquidDropletSMD() {
    return Arrays.copyOf(liquidDropletSMD, liquidDropletSMD.length);
  }

  public double getTotalMassReleased() {
    return totalMassReleased;
  }

  public double getPeakMassFlowRate() {
    return peakMassFlowRate;
  }

  public double getTimeToEmpty() {
    return timeToEmpty;
  }

  public int getNumberOfPoints() {
    return time.length;
  }

  // Package-private setters for LeakModel to populate

  void setDataPoint(int index, double t, double mdot, double T, double P, double vaporFrac,
      double velocity, double momentum, double smd) {
    if (index >= 0 && index < time.length) {
      time[index] = t;
      massFlowRate[index] = mdot;
      temperature[index] = T;
      pressure[index] = P;
      vaporMassFraction[index] = vaporFrac;
      jetVelocity[index] = velocity;
      jetMomentum[index] = momentum;
      liquidDropletSMD[index] = smd;
    }
  }

  void setTotalMassReleased(double mass) {
    this.totalMassReleased = mass;
  }

  void setPeakMassFlowRate(double peak) {
    this.peakMassFlowRate = peak;
  }

  void setTimeToEmpty(double time) {
    this.timeToEmpty = time;
  }

  /**
   * Exports the source term to PHAST-compatible CSV format.
   *
   * @param filename output filename
   */
  public void exportToPHAST(String filename) {
    StringBuilder sb = new StringBuilder();
    sb.append("# PHAST Source Term Export\n");
    sb.append("# Scenario: ").append(scenarioName).append("\n");
    sb.append("# Hole Diameter: ").append(holeDiameter * 1000).append(" mm\n");
    sb.append("# Orientation: ").append(orientation).append("\n");
    sb.append("#\n");
    sb.append("Time(s),MassRate(kg/s),Temperature(K),Pressure(Pa),VaporFraction(-)\n");

    for (int i = 0; i < time.length; i++) {
      sb.append(String.format("%.3f,%.6f,%.2f,%.0f,%.4f%n", time[i], massFlowRate[i],
          temperature[i], pressure[i], vaporMassFraction[i]));
    }

    writeToFile(filename, sb.toString());
  }

  /**
   * Exports the source term to FLACS-compatible format.
   *
   * @param filename output filename
   */
  public void exportToFLACS(String filename) {
    StringBuilder sb = new StringBuilder();
    sb.append("! FLACS Source Term Export\n");
    sb.append("! Scenario: ").append(scenarioName).append("\n");
    sb.append("! Generated by NeqSim\n");
    sb.append("!\n");
    sb.append("LEAK\n");
    sb.append("  DIAMETER = ").append(holeDiameter).append("\n");
    sb.append("  DIRECTION = ").append(orientation.isHorizontal() ? "HORIZONTAL" : "VERTICAL")
        .append("\n");
    sb.append("  TIME_DEPENDENT = YES\n");
    sb.append("!\n");
    sb.append("! Time(s)  MassRate(kg/s)  Temp(K)  VaporFrac\n");

    for (int i = 0; i < time.length; i++) {
      sb.append(String.format("  %.3f  %.6f  %.2f  %.4f%n", time[i], massFlowRate[i],
          temperature[i], vaporMassFraction[i]));
    }

    sb.append("END\n");

    writeToFile(filename, sb.toString());
  }

  /**
   * Exports the source term to KFX-compatible format.
   *
   * @param filename output filename
   */
  public void exportToKFX(String filename) {
    StringBuilder sb = new StringBuilder();
    sb.append("# KFX Source Term Export\n");
    sb.append("# Scenario: ").append(scenarioName).append("\n");
    sb.append("#\n");
    sb.append("$RELEASE\n");
    sb.append("  TYPE = JET\n");
    sb.append("  DIAMETER = ").append(holeDiameter).append(" m\n");
    sb.append("  ANGLE = ").append(orientation.getAngle()).append(" deg\n");
    sb.append("$END\n");
    sb.append("\n");
    sb.append("$TIME_SERIES\n");
    sb.append("# t(s), mdot(kg/s), T(K), P(Pa), vap(-), vel(m/s)\n");

    for (int i = 0; i < time.length; i++) {
      sb.append(String.format("%.3f, %.6f, %.2f, %.0f, %.4f, %.2f%n", time[i], massFlowRate[i],
          temperature[i], pressure[i], vaporMassFraction[i], jetVelocity[i]));
    }

    sb.append("$END\n");

    writeToFile(filename, sb.toString());
  }

  /**
   * Exports the source term for OpenFOAM simulation.
   *
   * @param directory output directory
   */
  public void exportToOpenFOAM(String directory) {
    // Mass flow rate file
    StringBuilder massFile = new StringBuilder();
    massFile.append(
        "/*--------------------------------*- C++ -*----------------------------------*\\\n");
    massFile.append(
        "| =========                 |                                                 |\n");
    massFile.append(
        "| \\\\      /  F ield         | OpenFOAM: The Open Source CFD Toolbox           |\n");
    massFile.append(
        "|  \\\\    /   O peration     | Generated by NeqSim                             |\n");
    massFile.append(
        "|   \\\\  /    A nd           |                                                 |\n");
    massFile.append(
        "|    \\\\/     M anipulation  |                                                 |\n");
    massFile.append(
        "\\*---------------------------------------------------------------------------*/\n");
    massFile.append("(\n");

    for (int i = 0; i < time.length; i++) {
      massFile.append(String.format("    (%.6f %.8e)%n", time[i], massFlowRate[i]));
    }

    massFile.append(");\n");

    writeToFile(directory + "/massFlowRate", massFile.toString());

    // Temperature file
    StringBuilder tempFile = new StringBuilder();
    tempFile.append("(\n");
    for (int i = 0; i < time.length; i++) {
      tempFile.append(String.format("    (%.6f %.2f)%n", time[i], temperature[i]));
    }
    tempFile.append(");\n");

    writeToFile(directory + "/temperature", tempFile.toString());
  }

  /**
   * Exports to generic CSV format with all data.
   *
   * @param filename output filename
   */
  public void exportToCSV(String filename) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        "Time_s,MassFlowRate_kg_s,Temperature_K,Pressure_Pa,VaporFraction,JetVelocity_m_s,JetMomentum_N,DropletSMD_m\n");

    for (int i = 0; i < time.length; i++) {
      sb.append(String.format("%.4f,%.6f,%.2f,%.0f,%.4f,%.2f,%.2f,%.6e%n", time[i], massFlowRate[i],
          temperature[i], pressure[i], vaporMassFraction[i], jetVelocity[i], jetMomentum[i],
          liquidDropletSMD[i]));
    }

    writeToFile(filename, sb.toString());
  }

  /**
   * Exports to JSON format.
   *
   * @param filename output filename
   */
  public void exportToJSON(String filename) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"scenarioName\": \"").append(scenarioName).append("\",\n");
    sb.append("  \"holeDiameter_m\": ").append(holeDiameter).append(",\n");
    sb.append("  \"orientation\": \"").append(orientation.name()).append("\",\n");
    sb.append("  \"totalMassReleased_kg\": ").append(totalMassReleased).append(",\n");
    sb.append("  \"peakMassFlowRate_kg_s\": ").append(peakMassFlowRate).append(",\n");
    sb.append("  \"timeToEmpty_s\": ").append(timeToEmpty).append(",\n");
    sb.append("  \"timeSeries\": [\n");

    for (int i = 0; i < time.length; i++) {
      sb.append("    {");
      sb.append("\"t\": ").append(String.format("%.4f", time[i])).append(", ");
      sb.append("\"mdot\": ").append(String.format("%.6f", massFlowRate[i])).append(", ");
      sb.append("\"T\": ").append(String.format("%.2f", temperature[i])).append(", ");
      sb.append("\"P\": ").append(String.format("%.0f", pressure[i])).append(", ");
      sb.append("\"vf\": ").append(String.format("%.4f", vaporMassFraction[i])).append(", ");
      sb.append("\"vel\": ").append(String.format("%.2f", jetVelocity[i])).append(", ");
      sb.append("\"mom\": ").append(String.format("%.2f", jetMomentum[i]));
      sb.append("}");
      if (i < time.length - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }

    sb.append("  ]\n");
    sb.append("}\n");

    writeToFile(filename, sb.toString());
  }

  private void writeToFile(String filename, String content) {
    try (java.io.FileWriter writer = new java.io.FileWriter(filename)) {
      writer.write(content);
    } catch (java.io.IOException e) {
      throw new RuntimeException("Failed to write source term to file: " + filename, e);
    }
  }

  @Override
  public String toString() {
    return String.format(
        "SourceTermResult[%s, d=%.1fmm, %s, peak=%.3f kg/s, total=%.1f kg, t_empty=%.1f s]",
        scenarioName, holeDiameter * 1000, orientation, peakMassFlowRate, totalMassReleased,
        timeToEmpty);
  }
}
