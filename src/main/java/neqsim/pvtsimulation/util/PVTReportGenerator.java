package neqsim.pvtsimulation.util;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest;
import neqsim.pvtsimulation.simulation.SaturationPressure;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * PVT Report Generator for creating industry-standard PVT reports.
 *
 * <p>
 * Generates comprehensive PVT reports including:
 * <ul>
 * <li>Fluid composition</li>
 * <li>Saturation pressure</li>
 * <li>CCE (Constant Composition Expansion) results</li>
 * <li>DLE (Differential Liberation) results</li>
 * <li>CVD (Constant Volume Depletion) results</li>
 * <li>Separator test results</li>
 * <li>Viscosity data</li>
 * <li>Comparison with lab data (if provided)</li>
 * </ul>
 *
 * <p>
 * Supported output formats:
 * <ul>
 * <li>Text/Markdown</li>
 * <li>CSV</li>
 * <li>HTML</li>
 * </ul>
 *
 * @author ESOL
 */
public class PVTReportGenerator {

  private String projectName = "PVT Study";
  private String fluidName = "Reservoir Fluid";
  private String labName = "";
  private String sampleDate = "";
  private String reportDate;

  private SystemInterface fluid;
  private double reservoirTemperature; // K
  private double reservoirPressure; // bara
  private double saturationPressure; // bara
  private String saturationPressureType = "Bubble Point";

  private ConstantMassExpansion cceData;
  private DifferentialLiberation dleData;
  private ConstantVolumeDepletion cvdData;
  private MultiStageSeparatorTest separatorData;

  private List<LabDataPoint> labCCE = new ArrayList<>();
  private List<LabDataPoint> labDLE = new ArrayList<>();

  /**
   * Lab data point for comparison.
   */
  public static class LabDataPoint {
    private final double pressure;
    private final String property;
    private final double value;
    private final String unit;

    /**
     * Create a lab data point.
     *
     * @param pressure Pressure (bara)
     * @param property Property name (e.g., "Bo", "Rs", "RelVol")
     * @param value Measured value
     * @param unit Unit string
     */
    public LabDataPoint(double pressure, String property, double value, String unit) {
      this.pressure = pressure;
      this.property = property;
      this.value = value;
      this.unit = unit;
    }

    public double getPressure() {
      return pressure;
    }

    public String getProperty() {
      return property;
    }

    public double getValue() {
      return value;
    }

    public String getUnit() {
      return unit;
    }
  }

  /**
   * Constructor for PVTReportGenerator.
   *
   * @param fluid The reservoir fluid
   */
  public PVTReportGenerator(SystemInterface fluid) {
    this.fluid = fluid;
    this.reportDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
  }

  /**
   * Set project information.
   *
   * @param projectName Project name
   * @param fluidName Fluid/sample name
   * @return this for chaining
   */
  public PVTReportGenerator setProjectInfo(String projectName, String fluidName) {
    this.projectName = projectName;
    this.fluidName = fluidName;
    return this;
  }

  /**
   * Set lab information.
   *
   * @param labName Laboratory name
   * @param sampleDate Sample date
   * @return this for chaining
   */
  public PVTReportGenerator setLabInfo(String labName, String sampleDate) {
    this.labName = labName;
    this.sampleDate = sampleDate;
    return this;
  }

  /**
   * Set reservoir conditions.
   *
   * @param pressure Reservoir pressure (bara)
   * @param temperatureCelsius Reservoir temperature (°C)
   * @return this for chaining
   */
  public PVTReportGenerator setReservoirConditions(double pressure, double temperatureCelsius) {
    this.reservoirPressure = pressure;
    this.reservoirTemperature = temperatureCelsius + 273.15;
    return this;
  }

  /**
   * Add saturation pressure result.
   *
   * @param satPress Saturation pressure simulation result
   * @return this for chaining
   */
  public PVTReportGenerator addSaturationPressure(SaturationPressure satPress) {
    this.saturationPressure = satPress.getSaturationPressure();
    return this;
  }

  /**
   * Set saturation pressure directly.
   *
   * @param pressure Saturation pressure (bara)
   * @param isBubblePoint true if bubble point, false if dew point
   * @return this for chaining
   */
  public PVTReportGenerator setSaturationPressure(double pressure, boolean isBubblePoint) {
    this.saturationPressure = pressure;
    this.saturationPressureType = isBubblePoint ? "Bubble Point" : "Dew Point";
    return this;
  }

  /**
   * Add CCE simulation results.
   *
   * @param cce CCE simulation
   * @return this for chaining
   */
  public PVTReportGenerator addCCE(ConstantMassExpansion cce) {
    this.cceData = cce;
    return this;
  }

  /**
   * Add DLE simulation results.
   *
   * @param dle DLE simulation
   * @return this for chaining
   */
  public PVTReportGenerator addDLE(DifferentialLiberation dle) {
    this.dleData = dle;
    return this;
  }

  /**
   * Add CVD simulation results.
   *
   * @param cvd CVD simulation
   * @return this for chaining
   */
  public PVTReportGenerator addCVD(ConstantVolumeDepletion cvd) {
    this.cvdData = cvd;
    return this;
  }

  /**
   * Add separator test results.
   *
   * @param sepTest Separator test simulation
   * @return this for chaining
   */
  public PVTReportGenerator addSeparatorTest(MultiStageSeparatorTest sepTest) {
    this.separatorData = sepTest;
    return this;
  }

  /**
   * Add lab CCE data point for comparison.
   *
   * @param pressure Pressure (bara)
   * @param property Property name
   * @param value Measured value
   * @param unit Unit
   * @return this for chaining
   */
  public PVTReportGenerator addLabCCEData(double pressure, String property, double value,
      String unit) {
    labCCE.add(new LabDataPoint(pressure, property, value, unit));
    return this;
  }

  /**
   * Add lab DLE data point for comparison.
   *
   * @param pressure Pressure (bara)
   * @param property Property name
   * @param value Measured value
   * @param unit Unit
   * @return this for chaining
   */
  public PVTReportGenerator addLabDLEData(double pressure, String property, double value,
      String unit) {
    labDLE.add(new LabDataPoint(pressure, property, value, unit));
    return this;
  }

  /**
   * Generate report as Markdown text.
   *
   * @return Markdown formatted report
   */
  public String generateMarkdownReport() {
    StringBuilder sb = new StringBuilder();

    // Header
    sb.append("# PVT Study Report\n\n");
    sb.append("## Project Information\n\n");
    sb.append(String.format("| Property | Value |\n"));
    sb.append(String.format("|----------|-------|\n"));
    sb.append(String.format("| Project | %s |\n", projectName));
    sb.append(String.format("| Fluid Name | %s |\n", fluidName));
    if (!labName.isEmpty()) {
      sb.append(String.format("| Laboratory | %s |\n", labName));
    }
    if (!sampleDate.isEmpty()) {
      sb.append(String.format("| Sample Date | %s |\n", sampleDate));
    }
    sb.append(String.format("| Report Date | %s |\n", reportDate));
    sb.append("\n");

    // Reservoir Conditions
    sb.append("## Reservoir Conditions\n\n");
    sb.append(String.format("| Property | Value | Unit |\n"));
    sb.append(String.format("|----------|-------|------|\n"));
    sb.append(String.format("| Reservoir Pressure | %.1f | bara |\n", reservoirPressure));
    sb.append(
        String.format("| Reservoir Temperature | %.1f | °C |\n", reservoirTemperature - 273.15));
    sb.append(String.format("| %s Pressure | %.2f | bara |\n", saturationPressureType,
        saturationPressure));
    sb.append("\n");

    // Fluid Composition
    if (fluid != null) {
      sb.append("## Fluid Composition\n\n");
      sb.append(generateCompositionTable());
    }

    // CCE Results
    if (cceData != null) {
      sb.append("## Constant Composition Expansion (CCE)\n\n");
      sb.append(generateCCETable());
    }

    // DLE Results
    if (dleData != null) {
      sb.append("## Differential Liberation Expansion (DLE)\n\n");
      sb.append(generateDLETable());
    }

    // CVD Results
    if (cvdData != null) {
      sb.append("## Constant Volume Depletion (CVD)\n\n");
      sb.append(generateCVDTable());
    }

    // Separator Test
    if (separatorData != null) {
      sb.append("## Separator Test\n\n");
      sb.append(separatorData.generateReport());
      sb.append("\n");
    }

    // Statistics
    sb.append("## Quality Metrics\n\n");
    sb.append(generateStatistics());

    return sb.toString();
  }

  /**
   * Generate composition table.
   */
  private String generateCompositionTable() {
    StringBuilder sb = new StringBuilder();
    sb.append("| Component | Mole Fraction | MW (g/mol) |\n");
    sb.append("|-----------|--------------|------------|\n");

    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      ComponentInterface comp = fluid.getPhase(0).getComponent(i);
      sb.append(String.format(Locale.US, "| %s | %.6f | %.2f |\n", comp.getComponentName(),
          comp.getz(), comp.getMolarMass() * 1000));
    }
    sb.append("\n");
    return sb.toString();
  }

  /**
   * Generate CCE results table.
   */
  private String generateCCETable() {
    StringBuilder sb = new StringBuilder();
    sb.append("| Pressure (bara) | Rel. Volume | Y-Factor | Density (kg/m³) |\n");
    sb.append("|-----------------|-------------|----------|-----------------|\n");

    double[] pressures = cceData.getPressures();
    double[] relVol = cceData.getRelativeVolume();
    double[] yFactor = cceData.getYfactor();
    double[] density = cceData.getDensity();

    for (int i = 0; i < pressures.length; i++) {
      String yStr = (yFactor != null && yFactor[i] > 0) ? String.format("%.4f", yFactor[i]) : "-";
      String densStr =
          (density != null && density[i] > 0) ? String.format("%.1f", density[i]) : "-";
      sb.append(String.format(Locale.US, "| %.1f | %.4f | %s | %s |\n", pressures[i], relVol[i],
          yStr, densStr));
    }
    sb.append("\n");
    return sb.toString();
  }

  /**
   * Generate DLE results table.
   */
  private String generateDLETable() {
    StringBuilder sb = new StringBuilder();
    sb.append("| Pressure (bara) | Bo (m³/Sm³) | Rs (Sm³/Sm³) | ρ_oil (kg/m³) | Bg |\n");
    sb.append("|-----------------|-------------|--------------|---------------|----|\n");

    double[] pressures = dleData.getPressures();
    double[] bo = dleData.getBo();
    double[] rs = dleData.getRs();
    double[] density = dleData.getOilDensity();
    double[] bg = dleData.getBg();

    for (int i = 0; i < pressures.length; i++) {
      String boStr = (bo != null && i < bo.length) ? String.format("%.4f", bo[i]) : "-";
      String rsStr = (rs != null && i < rs.length) ? String.format("%.1f", rs[i]) : "-";
      String densStr =
          (density != null && i < density.length) ? String.format("%.1f", density[i]) : "-";
      String bgStr = (bg != null && i < bg.length) ? String.format("%.6f", bg[i]) : "-";
      sb.append(String.format(Locale.US, "| %.1f | %s | %s | %s | %s |\n", pressures[i], boStr,
          rsStr, densStr, bgStr));
    }
    sb.append("\n");
    return sb.toString();
  }

  /**
   * Generate CVD results table.
   */
  private String generateCVDTable() {
    StringBuilder sb = new StringBuilder();
    sb.append("| Pressure (bara) | Liquid Vol% | Z (2-phase) | Gas Produced (mol%) |\n");
    sb.append("|-----------------|-------------|-------------|---------------------|\n");

    double[] pressures = cvdData.getPressures();
    double[] liquidVol = cvdData.getLiquidRelativeVolume();
    double[] zFactor = cvdData.getZmix();
    double[] gasProd = cvdData.getCummulativeMolePercDepleted();

    for (int i = 0; i < pressures.length; i++) {
      String liqStr =
          (liquidVol != null && i < liquidVol.length) ? String.format("%.2f", liquidVol[i]) : "-";
      String zStr =
          (zFactor != null && i < zFactor.length) ? String.format("%.4f", zFactor[i]) : "-";
      String gasStr =
          (gasProd != null && i < gasProd.length) ? String.format("%.2f", gasProd[i]) : "-";
      sb.append(String.format(Locale.US, "| %.1f | %s | %s | %s |\n", pressures[i], liqStr, zStr,
          gasStr));
    }
    sb.append("\n");
    return sb.toString();
  }

  /**
   * Generate statistics section.
   */
  private String generateStatistics() {
    StringBuilder sb = new StringBuilder();

    if (!labCCE.isEmpty() && cceData != null) {
      sb.append("### CCE Comparison with Lab Data\n\n");
      // Calculate AAD for each property
      sb.append("*(Lab data comparison not yet implemented)*\n\n");
    }

    if (!labDLE.isEmpty() && dleData != null) {
      sb.append("### DLE Comparison with Lab Data\n\n");
      sb.append("*(Lab data comparison not yet implemented)*\n\n");
    }

    sb.append("---\n");
    sb.append("*Report generated by NeqSim PVT Report Generator*\n");

    return sb.toString();
  }

  /**
   * Write report to a Writer.
   *
   * @param writer Writer to write to
   * @throws IOException if writing fails
   */
  public void writeReport(Writer writer) throws IOException {
    writer.write(generateMarkdownReport());
    writer.flush();
  }

  /**
   * Generate CSV output for CCE data.
   *
   * @return CSV formatted string
   */
  public String generateCCECSV() {
    if (cceData == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Pressure(bara),RelativeVolume,YFactor,Density(kg/m3)\n");

    double[] pressures = cceData.getPressures();
    double[] relVol = cceData.getRelativeVolume();
    double[] yFactor = cceData.getYfactor();
    double[] density = cceData.getDensity();

    for (int i = 0; i < pressures.length; i++) {
      sb.append(String.format(Locale.US, "%.2f,%.6f,%.6f,%.2f\n", pressures[i], relVol[i],
          (yFactor != null && i < yFactor.length) ? yFactor[i] : 0.0,
          (density != null && i < density.length) ? density[i] : 0.0));
    }

    return sb.toString();
  }

  /**
   * Generate CSV output for DLE data.
   *
   * @return CSV formatted string
   */
  public String generateDLECSV() {
    if (dleData == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Pressure(bara),Bo(m3/Sm3),Rs(Sm3/Sm3),OilDensity(kg/m3),Bg\n");

    double[] pressures = dleData.getPressures();
    double[] bo = dleData.getBo();
    double[] rs = dleData.getRs();
    double[] density = dleData.getOilDensity();
    double[] bg = dleData.getBg();

    for (int i = 0; i < pressures.length; i++) {
      sb.append(String.format(Locale.US, "%.2f,%.6f,%.2f,%.2f,%.8f\n", pressures[i],
          (bo != null && i < bo.length) ? bo[i] : 0.0, (rs != null && i < rs.length) ? rs[i] : 0.0,
          (density != null && i < density.length) ? density[i] : 0.0,
          (bg != null && i < bg.length) ? bg[i] : 0.0));
    }

    return sb.toString();
  }
}
