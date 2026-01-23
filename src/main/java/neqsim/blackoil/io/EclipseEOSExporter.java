package neqsim.blackoil.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import neqsim.blackoil.BlackOilConverter;
import neqsim.blackoil.BlackOilPVTTable;
import neqsim.thermo.system.SystemInterface;

/**
 * Eclipse reservoir simulator EOS/PVT keyword exporter.
 *
 * <p>
 * Exports NeqSim compositional fluids or Black-Oil PVT tables to Eclipse-compatible include files
 * containing PVTO, PVTG, PVTW, and DENSITY keywords.
 * </p>
 *
 * <h2>Supported Eclipse Keywords</h2>
 * <ul>
 * <li><b>PVTO</b> - Live oil PVT table (Rs, P, Bo, viscosity)</li>
 * <li><b>PVTG</b> - Wet gas PVT table (Rv, P, Bg, viscosity)</li>
 * <li><b>PVTW</b> - Water PVT properties</li>
 * <li><b>DENSITY</b> - Stock tank densities</li>
 * <li><b>PVCO</b> - Alternative compressed oil format</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * {@code
 * SystemInterface fluid = new SystemSrkEos(373.15, 200.0);
 * fluid.addComponent("methane", 0.7);
 * fluid.addComponent("n-heptane", 0.3);
 * fluid.setMixingRule("classic");
 *
 * EclipseEOSExporter.toFile(fluid, Path.of("PVT.INC"));
 * }
 * </pre>
 *
 * @author esol
 * @version 1.0
 * @see BlackOilConverter
 * @see BlackOilPVTTable
 */
public final class EclipseEOSExporter {

  /**
   * Unit system for Eclipse export.
   */
  public enum Units {
    /** Metric units: bar, kg/m³, Sm³/Sm³, mPa·s. */
    METRIC,
    /** Field units: psia, lb/ft³, scf/stb, cp. */
    FIELD
  }

  /**
   * Configuration for Eclipse export.
   */
  public static final class ExportConfig {
    private Units units = Units.METRIC;
    private double referenceTemperature = 288.15; // 15°C
    private double standardPressure = 1.01325; // bar
    private double standardTemperature = 288.15; // K
    private double[] pressureGrid = null;
    private boolean includeHeader = true;
    private boolean includePVTO = true;
    private boolean includePVTG = true;
    private boolean includePVTW = true;
    private boolean includeDensity = true;
    private String comment = "";

    /**
     * Set the unit system.
     *
     * @param units unit system
     * @return this config for chaining
     */
    public ExportConfig setUnits(Units units) {
      this.units = units;
      return this;
    }

    /**
     * Set reference temperature for PVT table generation.
     *
     * @param temperature temperature in Kelvin
     * @return this config for chaining
     */
    public ExportConfig setReferenceTemperature(double temperature) {
      this.referenceTemperature = temperature;
      return this;
    }

    /**
     * Set standard conditions for stock tank properties.
     *
     * @param pressure pressure in bar
     * @param temperature temperature in Kelvin
     * @return this config for chaining
     */
    public ExportConfig setStandardConditions(double pressure, double temperature) {
      this.standardPressure = pressure;
      this.standardTemperature = temperature;
      return this;
    }

    /**
     * Set pressure grid for PVT table generation.
     *
     * @param pressures pressures in bar
     * @return this config for chaining
     */
    public ExportConfig setPressureGrid(double[] pressures) {
      this.pressureGrid = pressures != null ? pressures.clone() : null;
      return this;
    }

    /**
     * Enable or disable header comments in output.
     *
     * @param include true to include header
     * @return this config for chaining
     */
    public ExportConfig setIncludeHeader(boolean include) {
      this.includeHeader = include;
      return this;
    }

    /**
     * Enable or disable PVTO keyword output.
     *
     * @param include true to include PVTO
     * @return this config for chaining
     */
    public ExportConfig setIncludePVTO(boolean include) {
      this.includePVTO = include;
      return this;
    }

    /**
     * Enable or disable PVTG keyword output.
     *
     * @param include true to include PVTG
     * @return this config for chaining
     */
    public ExportConfig setIncludePVTG(boolean include) {
      this.includePVTG = include;
      return this;
    }

    /**
     * Enable or disable PVTW keyword output.
     *
     * @param include true to include PVTW
     * @return this config for chaining
     */
    public ExportConfig setIncludePVTW(boolean include) {
      this.includePVTW = include;
      return this;
    }

    /**
     * Enable or disable DENSITY keyword output.
     *
     * @param include true to include DENSITY
     * @return this config for chaining
     */
    public ExportConfig setIncludeDensity(boolean include) {
      this.includeDensity = include;
      return this;
    }

    /**
     * Add a custom comment to the header.
     *
     * @param comment comment string
     * @return this config for chaining
     */
    public ExportConfig setComment(String comment) {
      this.comment = comment != null ? comment : "";
      return this;
    }
  }

  private EclipseEOSExporter() {
    // Utility class
  }

  /**
   * Export a compositional fluid to an Eclipse include file using default settings.
   *
   * @param fluid NeqSim compositional fluid
   * @param outputPath output file path
   * @throws IOException if writing fails
   */
  public static void toFile(SystemInterface fluid, Path outputPath) throws IOException {
    toFile(fluid, outputPath, new ExportConfig());
  }

  /**
   * Export a compositional fluid to an Eclipse include file.
   *
   * @param fluid NeqSim compositional fluid
   * @param outputPath output file path
   * @param config export configuration
   * @throws IOException if writing fails
   */
  public static void toFile(SystemInterface fluid, Path outputPath, ExportConfig config)
      throws IOException {
    Objects.requireNonNull(fluid, "fluid cannot be null");
    Objects.requireNonNull(outputPath, "outputPath cannot be null");
    Objects.requireNonNull(config, "config cannot be null");

    try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
      toWriter(fluid, writer, config);
    }
  }

  /**
   * Export a compositional fluid to an Eclipse format string.
   *
   * @param fluid NeqSim compositional fluid
   * @return Eclipse include file content
   */
  public static String toString(SystemInterface fluid) {
    return toString(fluid, new ExportConfig());
  }

  /**
   * Export a compositional fluid to an Eclipse format string.
   *
   * @param fluid NeqSim compositional fluid
   * @param config export configuration
   * @return Eclipse include file content
   */
  public static String toString(SystemInterface fluid, ExportConfig config) {
    Objects.requireNonNull(fluid, "fluid cannot be null");
    Objects.requireNonNull(config, "config cannot be null");

    StringBuilder sb = new StringBuilder();
    try {
      toWriter(fluid, new StringBuilderWriter(sb), config);
    } catch (IOException e) {
      throw new RuntimeException("Unexpected IOException writing to StringBuilder", e);
    }
    return sb.toString();
  }

  /**
   * Export a Black-Oil PVT table to an Eclipse include file.
   *
   * @param pvt Black-Oil PVT table
   * @param rhoOilSc oil density at standard conditions (kg/m³)
   * @param rhoGasSc gas density at standard conditions (kg/m³)
   * @param rhoWaterSc water density at standard conditions (kg/m³)
   * @param outputPath output file path
   * @throws IOException if writing fails
   */
  public static void toFile(BlackOilPVTTable pvt, double rhoOilSc, double rhoGasSc,
      double rhoWaterSc, Path outputPath) throws IOException {
    toFile(pvt, rhoOilSc, rhoGasSc, rhoWaterSc, outputPath, new ExportConfig());
  }

  /**
   * Export a Black-Oil PVT table to an Eclipse include file.
   *
   * @param pvt Black-Oil PVT table
   * @param rhoOilSc oil density at standard conditions (kg/m³)
   * @param rhoGasSc gas density at standard conditions (kg/m³)
   * @param rhoWaterSc water density at standard conditions (kg/m³)
   * @param outputPath output file path
   * @param config export configuration
   * @throws IOException if writing fails
   */
  public static void toFile(BlackOilPVTTable pvt, double rhoOilSc, double rhoGasSc,
      double rhoWaterSc, Path outputPath, ExportConfig config) throws IOException {
    Objects.requireNonNull(pvt, "pvt cannot be null");
    Objects.requireNonNull(outputPath, "outputPath cannot be null");
    Objects.requireNonNull(config, "config cannot be null");

    try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
      writePVTTable(pvt, rhoOilSc, rhoGasSc, rhoWaterSc, writer, config);
    }
  }

  /**
   * Export a Black-Oil PVT table to an Eclipse format string.
   *
   * @param pvt Black-Oil PVT table
   * @param rhoOilSc oil density at standard conditions (kg/m³)
   * @param rhoGasSc gas density at standard conditions (kg/m³)
   * @param rhoWaterSc water density at standard conditions (kg/m³)
   * @return Eclipse include file content
   */
  public static String toString(BlackOilPVTTable pvt, double rhoOilSc, double rhoGasSc,
      double rhoWaterSc) {
    return toString(pvt, rhoOilSc, rhoGasSc, rhoWaterSc, new ExportConfig());
  }

  /**
   * Export a Black-Oil PVT table to an Eclipse format string.
   *
   * @param pvt Black-Oil PVT table
   * @param rhoOilSc oil density at standard conditions (kg/m³)
   * @param rhoGasSc gas density at standard conditions (kg/m³)
   * @param rhoWaterSc water density at standard conditions (kg/m³)
   * @param config export configuration
   * @return Eclipse include file content
   */
  public static String toString(BlackOilPVTTable pvt, double rhoOilSc, double rhoGasSc,
      double rhoWaterSc, ExportConfig config) {
    Objects.requireNonNull(pvt, "pvt cannot be null");
    Objects.requireNonNull(config, "config cannot be null");

    StringBuilder sb = new StringBuilder();
    try {
      writePVTTable(pvt, rhoOilSc, rhoGasSc, rhoWaterSc, new StringBuilderWriter(sb), config);
    } catch (IOException e) {
      throw new RuntimeException("Unexpected IOException writing to StringBuilder", e);
    }
    return sb.toString();
  }

  private static void toWriter(SystemInterface fluid, Writer writer, ExportConfig config)
      throws IOException {
    // Generate pressure grid if not provided
    double[] pressures = config.pressureGrid;
    if (pressures == null || pressures.length < 2) {
      pressures = generateDefaultPressureGrid(fluid.getPressure());
    }

    // Convert compositional fluid to Black-Oil
    BlackOilConverter.Result result = BlackOilConverter.convert(fluid, config.referenceTemperature,
        pressures, config.standardPressure, config.standardTemperature);

    writePVTTable(result.pvt, result.rho_o_sc, result.rho_g_sc, result.rho_w_sc, writer, config);
  }

  private static void writePVTTable(BlackOilPVTTable pvt, double rhoOilSc, double rhoGasSc,
      double rhoWaterSc, Writer writer, ExportConfig config) throws IOException {
    BufferedWriter bw =
        (writer instanceof BufferedWriter) ? (BufferedWriter) writer : new BufferedWriter(writer);

    // Unit conversion factors
    double pFactor = 1.0; // bar
    double rhoFactor = 1.0; // kg/m³
    double rsFactor = 1.0; // Sm³/Sm³
    double viscFactor = 1000.0; // Pa·s to mPa·s (cP)
    String pUnit = "BARSA";
    String rhoUnit = "KG/M3";
    String rsUnit = "SM3/SM3";
    String viscUnit = "CP";

    if (config.units == Units.FIELD) {
      pFactor = 14.5038; // bar to psia
      rhoFactor = 0.0624279606; // kg/m³ to lb/ft³
      rsFactor = 5.61458; // Sm³/Sm³ to scf/stb
      viscFactor = 1000.0; // Pa·s to cp
      pUnit = "PSIA";
      rhoUnit = "LB/FT3";
      rsUnit = "SCF/STB";
      viscUnit = "CP";
    }

    // Write header
    if (config.includeHeader) {
      bw.write("-- ============================================================\n");
      bw.write("-- Eclipse PVT Data Generated by NeqSim\n");
      bw.write("-- Generated: "
          + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
      bw.write("-- Units: " + config.units.name() + "\n");
      bw.write("-- Bubble Point: "
          + String.format(Locale.US, "%.2f", pvt.getBubblePointP() * pFactor) + " " + pUnit + "\n");
      if (!config.comment.isEmpty()) {
        bw.write("-- " + config.comment.replace("\n", "\n-- ") + "\n");
      }
      bw.write("-- ============================================================\n\n");
    }

    // Write DENSITY keyword
    if (config.includeDensity) {
      bw.write("-- Stock tank densities at standard conditions\n");
      bw.write("-- Oil density, Water density, Gas density (" + rhoUnit + ")\n");
      bw.write("DENSITY\n");
      bw.write(String.format(Locale.US, "  %.4f  %.4f  %.6f /\n\n", rhoOilSc * rhoFactor,
          rhoWaterSc * rhoFactor, rhoGasSc * rhoFactor));
    }

    // Generate pressure points for table
    double bubblePoint = pvt.getBubblePointP();
    List<Double> pressurePoints = generateTablePressures(bubblePoint, config);

    // Write PVTO keyword (live oil table)
    if (config.includePVTO) {
      bw.write("-- Live Oil PVT Table\n");
      bw.write("-- Rs (" + rsUnit + "), P (" + pUnit + "), Bo (RM3/SM3), Viscosity (" + viscUnit
          + ")\n");
      bw.write("PVTO\n");

      // Group by Rs value - for each Rs, we have P, Bo, mu_o
      // Below bubble point: Rs varies with P
      // At and above bubble point: Rs = Rs(Pb), P varies
      List<Double> saturatedPressures = new ArrayList<>();
      for (Double p : pressurePoints) {
        if (p <= bubblePoint) {
          saturatedPressures.add(p);
        }
      }

      for (int i = 0; i < saturatedPressures.size(); i++) {
        double pSat = saturatedPressures.get(i);
        double rs = pvt.Rs(pSat) * rsFactor;
        double bo = pvt.Bo(pSat);
        double muO = pvt.mu_o(pSat) * viscFactor;

        // Write Rs header line with first data point (at bubble point for this Rs)
        bw.write(
            String.format(Locale.US, "  %.4f  %.4f  %.6f  %.6f\n", rs, pSat * pFactor, bo, muO));

        // Add undersaturated data points (P > pSat at constant Rs)
        for (Double pUndersat : pressurePoints) {
          if (pUndersat > pSat) {
            double boUndersat = pvt.Bo(pUndersat);
            double muOUndersat = pvt.mu_o(pUndersat) * viscFactor;
            bw.write(String.format(Locale.US, "       %.4f  %.6f  %.6f\n", pUndersat * pFactor,
                boUndersat, muOUndersat));
          }
        }
        bw.write("  /\n");
      }
      bw.write("/\n\n");
    }

    // Write PVTG keyword (wet gas table)
    if (config.includePVTG) {
      bw.write("-- Wet Gas PVT Table\n");
      bw.write("-- P (" + pUnit + "), Rv (" + rsUnit + "), Bg (RM3/SM3), Viscosity (" + viscUnit
          + ")\n");
      bw.write("PVTG\n");

      for (Double p : pressurePoints) {
        double rv = pvt.Rv(p) * rsFactor;
        double bg = pvt.Bg(p);
        double muG = pvt.mu_g(p) * viscFactor;

        if (!Double.isNaN(bg) && bg > 0) {
          bw.write(
              String.format(Locale.US, "  %.4f  %.8f  %.8f  %.8f /\n", p * pFactor, rv, bg, muG));
        }
      }
      bw.write("/\n\n");
    }

    // Write PVTW keyword (water properties)
    if (config.includePVTW) {
      bw.write("-- Water PVT Properties\n");
      bw.write("-- Pref (" + pUnit + "), Bw, Compressibility (1/" + pUnit + "), Viscosity ("
          + viscUnit + "), Viscosibility (1/" + pUnit + ")\n");
      bw.write("PVTW\n");

      double pRef = bubblePoint;
      double bw_val = pvt.Bw(pRef);
      double muW = pvt.mu_w(pRef) * viscFactor;

      // Estimate water compressibility (typical value if not available)
      double cw = 4.5e-5; // 1/bar, typical for water
      double cvw = 0.0; // viscosibility

      if (config.units == Units.FIELD) {
        cw = cw / pFactor; // convert 1/bar to 1/psia
        cvw = cvw / pFactor;
      }

      if (!Double.isNaN(bw_val) && bw_val > 0) {
        bw.write(String.format(Locale.US, "  %.4f  %.6f  %.6e  %.6f  %.6e /\n", pRef * pFactor,
            bw_val, cw, muW, cvw));
      } else {
        // Default water properties if not available
        bw.write(String.format(Locale.US, "  %.4f  1.02  %.6e  0.5  0.0 /\n", pRef * pFactor, cw));
      }
      bw.write("\n");
    }

    bw.flush();
  }

  private static List<Double> generateTablePressures(double bubblePoint, ExportConfig config) {
    List<Double> pressures = new ArrayList<>();

    if (config.pressureGrid != null && config.pressureGrid.length > 0) {
      for (double p : config.pressureGrid) {
        pressures.add(p);
      }
    } else {
      // Generate default pressure points
      double pMin = Math.max(1.0, bubblePoint * 0.1);
      double pMax = bubblePoint * 1.5;
      int nPoints = 10;

      for (int i = 0; i < nPoints; i++) {
        double p = pMin + (pMax - pMin) * i / (nPoints - 1);
        pressures.add(p);
      }
      // Ensure bubble point is included
      if (!pressures.contains(bubblePoint)) {
        pressures.add(bubblePoint);
      }
    }

    pressures.sort(Double::compareTo);
    return pressures;
  }

  private static double[] generateDefaultPressureGrid(double referencePressure) {
    double pMax = Math.max(referencePressure * 1.5, 400.0);
    double pMin = Math.max(1.0, referencePressure * 0.1);
    int nPoints = 15;

    double[] pressures = new double[nPoints];
    for (int i = 0; i < nPoints; i++) {
      pressures[i] = pMin + (pMax - pMin) * i / (nPoints - 1);
    }
    return pressures;
  }

  /**
   * Simple Writer implementation that writes to a StringBuilder.
   */
  private static class StringBuilderWriter extends Writer {
    private final StringBuilder sb;

    StringBuilderWriter(StringBuilder sb) {
      this.sb = sb;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
      sb.append(cbuf, off, len);
    }

    @Override
    public void flush() {
      // No-op
    }

    @Override
    public void close() {
      // No-op
    }
  }
}
