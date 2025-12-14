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
 * CMG (Computer Modelling Group) reservoir simulator EOS/PVT exporter.
 *
 * <p>
 * Exports NeqSim compositional fluids or Black-Oil PVT tables to CMG-compatible format for use in
 * IMEX, GEM, and STARS simulators.
 * </p>
 *
 * <h2>Supported CMG Keywords</h2>
 * <ul>
 * <li><b>PVTG</b> - Gas PVT table</li>
 * <li><b>PVTO</b> - Oil PVT table</li>
 * <li><b>PVTW</b> - Water PVT properties</li>
 * <li><b>DENSITY</b> - Stock tank densities</li>
 * <li><b>BWI/CW/VW</b> - Water properties</li>
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
 * CMGEOSExporter.toFile(fluid, Path.of("PVT.DAT"), CMGEOSExporter.Simulator.IMEX);
 * }
 * </pre>
 *
 * @author esol
 * @version 1.0
 * @see BlackOilConverter
 * @see BlackOilPVTTable
 */
public final class CMGEOSExporter {

  /**
   * CMG Simulator target.
   */
  public enum Simulator {
    /** IMEX - Black oil simulator */
    IMEX,
    /** GEM - Compositional simulator */
    GEM,
    /** STARS - Thermal/advanced processes simulator */
    STARS
  }

  /**
   * Unit system for CMG export.
   */
  public enum Units {
    /** SI units: kPa, kg/m³, m³/m³, mPa·s */
    SI,
    /** Field units: psia, lb/ft³, scf/stb, cp */
    FIELD
  }

  /**
   * Configuration for CMG export.
   */
  public static final class ExportConfig {
    private Simulator simulator = Simulator.IMEX;
    private Units units = Units.SI;
    private double referenceTemperature = 288.15; // 15°C
    private double standardPressure = 101.325; // kPa
    private double standardTemperature = 288.15; // K
    private double[] pressureGrid = null;
    private boolean includeHeader = true;
    private String modelName = "NEQSIM_FLUID";
    private String comment = "";

    /**
     * Set the target CMG simulator.
     *
     * @param simulator target simulator
     * @return this config for chaining
     */
    public ExportConfig setSimulator(Simulator simulator) {
      this.simulator = simulator;
      return this;
    }

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
     * @param pressure pressure in kPa
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
     * Set model name for the export.
     *
     * @param name model name
     * @return this config for chaining
     */
    public ExportConfig setModelName(String name) {
      this.modelName = name != null ? name : "NEQSIM_FLUID";
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

  private CMGEOSExporter() {
    // Utility class
  }

  /**
   * Export a compositional fluid to a CMG file using default settings.
   *
   * @param fluid NeqSim compositional fluid
   * @param outputPath output file path
   * @throws IOException if writing fails
   */
  public static void toFile(SystemInterface fluid, Path outputPath) throws IOException {
    toFile(fluid, outputPath, new ExportConfig());
  }

  /**
   * Export a compositional fluid to a CMG file for a specific simulator.
   *
   * @param fluid NeqSim compositional fluid
   * @param outputPath output file path
   * @param simulator target CMG simulator
   * @throws IOException if writing fails
   */
  public static void toFile(SystemInterface fluid, Path outputPath, Simulator simulator)
      throws IOException {
    toFile(fluid, outputPath, new ExportConfig().setSimulator(simulator));
  }

  /**
   * Export a compositional fluid to a CMG file.
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
   * Export a compositional fluid to a CMG format string.
   *
   * @param fluid NeqSim compositional fluid
   * @return CMG format content
   */
  public static String toString(SystemInterface fluid) {
    return toString(fluid, new ExportConfig());
  }

  /**
   * Export a compositional fluid to a CMG format string.
   *
   * @param fluid NeqSim compositional fluid
   * @param config export configuration
   * @return CMG format content
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
   * Export a Black-Oil PVT table to a CMG file.
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
   * Export a Black-Oil PVT table to a CMG file.
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
   * Export a Black-Oil PVT table to a CMG format string.
   *
   * @param pvt Black-Oil PVT table
   * @param rhoOilSc oil density at standard conditions (kg/m³)
   * @param rhoGasSc gas density at standard conditions (kg/m³)
   * @param rhoWaterSc water density at standard conditions (kg/m³)
   * @return CMG format content
   */
  public static String toString(BlackOilPVTTable pvt, double rhoOilSc, double rhoGasSc,
      double rhoWaterSc) {
    return toString(pvt, rhoOilSc, rhoGasSc, rhoWaterSc, new ExportConfig());
  }

  /**
   * Export a Black-Oil PVT table to a CMG format string.
   *
   * @param pvt Black-Oil PVT table
   * @param rhoOilSc oil density at standard conditions (kg/m³)
   * @param rhoGasSc gas density at standard conditions (kg/m³)
   * @param rhoWaterSc water density at standard conditions (kg/m³)
   * @param config export configuration
   * @return CMG format content
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

    // Convert bar to kPa for standard conditions
    double stdPressureBar = config.standardPressure / 100.0; // kPa to bar

    // Convert compositional fluid to Black-Oil
    BlackOilConverter.Result result = BlackOilConverter.convert(fluid, config.referenceTemperature,
        pressures, stdPressureBar, config.standardTemperature);

    writePVTTable(result.pvt, result.rho_o_sc, result.rho_g_sc, result.rho_w_sc, writer, config);
  }

  private static void writePVTTable(BlackOilPVTTable pvt, double rhoOilSc, double rhoGasSc,
      double rhoWaterSc, Writer writer, ExportConfig config) throws IOException {
    BufferedWriter bw =
        (writer instanceof BufferedWriter) ? (BufferedWriter) writer : new BufferedWriter(writer);

    // Unit conversion factors (internal units are bar, kg/m³, Sm³/Sm³, Pa·s)
    double pFactor = 100.0; // bar to kPa
    double rhoFactor = 1.0; // kg/m³
    double rsFactor = 1.0; // Sm³/Sm³
    double viscFactor = 1.0; // Pa·s to Pa·s (CMG uses Pa·s for SI)
    String pUnit = "kPa";
    String rhoUnit = "kg/m3";
    String rsUnit = "m3/m3";
    String viscUnit = "Pa.s";

    if (config.units == Units.FIELD) {
      pFactor = 14.5038; // bar to psia
      rhoFactor = 0.0624279606; // kg/m³ to lb/ft³
      rsFactor = 5.61458; // Sm³/Sm³ to scf/stb
      viscFactor = 1000.0; // Pa·s to cp
      pUnit = "psi";
      rhoUnit = "lb/ft3";
      rsUnit = "scf/stb";
      viscUnit = "cp";
    }

    double bubblePoint = pvt.getBubblePointP();

    // Write header
    if (config.includeHeader) {
      bw.write("** ============================================================\n");
      bw.write("** CMG Black-Oil PVT Data Generated by NeqSim\n");
      bw.write("** Target Simulator: " + config.simulator.name() + "\n");
      bw.write("** Generated: "
          + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
      bw.write("** Units: " + config.units.name() + "\n");
      bw.write("** Bubble Point: " + String.format(Locale.US, "%.2f", bubblePoint * pFactor) + " "
          + pUnit + "\n");
      bw.write("** Model Name: " + config.modelName + "\n");
      if (!config.comment.isEmpty()) {
        bw.write("** " + config.comment.replace("\n", "\n** ") + "\n");
      }
      bw.write("** ============================================================\n\n");
    }

    // Generate pressure points
    List<Double> pressurePoints = generateTablePressures(bubblePoint, config);

    // Write based on simulator type
    switch (config.simulator) {
      case IMEX:
        writeIMEXFormat(bw, pvt, rhoOilSc, rhoGasSc, rhoWaterSc, pressurePoints, bubblePoint,
            pFactor, rhoFactor, rsFactor, viscFactor, pUnit, rhoUnit, rsUnit, viscUnit);
        break;
      case GEM:
        writeGEMFormat(bw, pvt, rhoOilSc, rhoGasSc, rhoWaterSc, pressurePoints, bubblePoint,
            pFactor, rhoFactor, rsFactor, viscFactor, pUnit, rhoUnit, rsUnit, viscUnit);
        break;
      case STARS:
        writeSTARSFormat(bw, pvt, rhoOilSc, rhoGasSc, rhoWaterSc, pressurePoints, bubblePoint,
            pFactor, rhoFactor, rsFactor, viscFactor, pUnit, rhoUnit, rsUnit, viscUnit);
        break;
    }

    bw.flush();
  }

  private static void writeIMEXFormat(BufferedWriter bw, BlackOilPVTTable pvt, double rhoOilSc,
      double rhoGasSc, double rhoWaterSc, List<Double> pressurePoints, double bubblePoint,
      double pFactor, double rhoFactor, double rsFactor, double viscFactor, String pUnit,
      String rhoUnit, String rsUnit, String viscUnit) throws IOException {

    // Model type
    bw.write("*MODEL *BLACKOIL\n\n");

    // Standard conditions
    bw.write("** Standard Conditions\n");
    bw.write("*TRES 15  ** Reference temperature (C)\n");
    bw.write("*PTEFACT 1.0\n\n");

    // Stock tank densities
    bw.write("** Stock Tank Densities (" + rhoUnit + ")\n");
    bw.write(String.format(Locale.US, "*DENSITY *OIL %.4f\n", rhoOilSc * rhoFactor));
    bw.write(String.format(Locale.US, "*DENSITY *GAS %.6f\n", rhoGasSc * rhoFactor));
    bw.write(String.format(Locale.US, "*DENSITY *WATER %.4f\n\n", rhoWaterSc * rhoFactor));

    // Reference pressure (bubble point)
    bw.write(String.format(Locale.US, "*REFPW %.4f  ** Reference pressure for water (%s)\n\n",
        bubblePoint * pFactor, pUnit));

    // Water properties
    double bw_val = pvt.Bw(bubblePoint);
    double muW = pvt.mu_w(bubblePoint) * viscFactor;
    if (!Double.isNaN(bw_val) && bw_val > 0) {
      bw.write("** Water Properties\n");
      bw.write(String.format(Locale.US, "*BWI %.6f\n", bw_val));
      bw.write(String.format(Locale.US, "*VWI %.6e\n", muW));
      bw.write("*CW 4.5e-7  ** Water compressibility (1/kPa)\n");
      bw.write("*CVW 0.0    ** Water viscosibility\n\n");
    }

    // Oil PVT table (similar to PVTO)
    bw.write("** Live Oil PVT Table\n");
    bw.write(
        "** Rs (" + rsUnit + "), Pressure (" + pUnit + "), Bo, Viscosity (" + viscUnit + ")\n");
    bw.write("*PVT *BG 1  ** Use table number 1 for gas Bg\n");
    bw.write("*BOTOIL\n");

    for (Double p : pressurePoints) {
      if (p <= bubblePoint) {
        double rs = pvt.Rs(p) * rsFactor;
        double bo = pvt.Bo(p);
        double muO = pvt.mu_o(p) * viscFactor;
        bw.write(String.format(Locale.US, "  %.4f  %.4f  %.6f  %.6e\n", rs, p * pFactor, bo, muO));
      }
    }
    bw.write("\n");

    // Gas PVT table
    bw.write("** Gas PVT Table\n");
    bw.write("** Pressure (" + pUnit + "), Bg, Viscosity (" + viscUnit + ")\n");
    bw.write("*BOTGAS\n");

    for (Double p : pressurePoints) {
      double bg = pvt.Bg(p);
      double muG = pvt.mu_g(p) * viscFactor;
      if (!Double.isNaN(bg) && bg > 0) {
        bw.write(String.format(Locale.US, "  %.4f  %.8f  %.8e\n", p * pFactor, bg, muG));
      }
    }
    bw.write("\n");
  }

  private static void writeGEMFormat(BufferedWriter bw, BlackOilPVTTable pvt, double rhoOilSc,
      double rhoGasSc, double rhoWaterSc, List<Double> pressurePoints, double bubblePoint,
      double pFactor, double rhoFactor, double rsFactor, double viscFactor, String pUnit,
      String rhoUnit, String rsUnit, String viscUnit) throws IOException {

    // GEM is compositional but can use PVT tables for comparison
    bw.write("** GEM Black-Oil Table (for comparison/validation)\n\n");

    // Standard conditions
    bw.write("*STDCOND 101.325 15  ** Standard P (kPa) and T (C)\n\n");

    // Stock tank densities
    bw.write("** Stock Tank Densities (" + rhoUnit + ")\n");
    bw.write(
        String.format(Locale.US, "*DENSTO %.4f  ** Oil density at SC\n", rhoOilSc * rhoFactor));
    bw.write(
        String.format(Locale.US, "*DENSTG %.6f  ** Gas density at SC\n", rhoGasSc * rhoFactor));
    bw.write(String.format(Locale.US, "*DENSTW %.4f  ** Water density at SC\n\n",
        rhoWaterSc * rhoFactor));

    // Bubble point
    bw.write(
        String.format(Locale.US, "** Bubble Point: %.4f %s\n\n", bubblePoint * pFactor, pUnit));

    // Write PVT as a lookup table for validation
    bw.write("** PVT Data Table for Model Validation\n");
    bw.write("** P(" + pUnit + ")  Rs(" + rsUnit + ")  Bo  Bg  mu_o(" + viscUnit + ")  mu_g("
        + viscUnit + ")\n");
    bw.write("*PVTTABLE\n");

    for (Double p : pressurePoints) {
      double rs = pvt.Rs(p) * rsFactor;
      double bo = pvt.Bo(p);
      double bg = pvt.Bg(p);
      double muO = pvt.mu_o(p) * viscFactor;
      double muG = pvt.mu_g(p) * viscFactor;

      if (!Double.isNaN(bg)) {
        bw.write(String.format(Locale.US, "  %.4f  %.4f  %.6f  %.8f  %.6e  %.6e\n", p * pFactor, rs,
            bo, bg, muO, muG));
      }
    }
    bw.write("\n");
  }

  private static void writeSTARSFormat(BufferedWriter bw, BlackOilPVTTable pvt, double rhoOilSc,
      double rhoGasSc, double rhoWaterSc, List<Double> pressurePoints, double bubblePoint,
      double pFactor, double rhoFactor, double rsFactor, double viscFactor, String pUnit,
      String rhoUnit, String rsUnit, String viscUnit) throws IOException {

    // STARS format for thermal simulation
    bw.write("** STARS Component Properties (from Black-Oil model)\n\n");

    // Reference densities
    bw.write("** Component Densities at Standard Conditions\n");
    bw.write(
        String.format(Locale.US, "*DENSITY 'OIL' %.4f  ** %s\n", rhoOilSc * rhoFactor, rhoUnit));
    bw.write(
        String.format(Locale.US, "*DENSITY 'GAS' %.6f  ** %s\n", rhoGasSc * rhoFactor, rhoUnit));
    bw.write(String.format(Locale.US, "*DENSITY 'WATER' %.4f  ** %s\n\n", rhoWaterSc * rhoFactor,
        rhoUnit));

    // K-value table (approximate from Rs)
    bw.write("** Approximate K-values from Black-Oil Model\n");
    bw.write("*KVTABLIM 1\n");
    bw.write(String.format(Locale.US, "  %.4f %.4f  ** Pmin, Pmax (%s)\n",
        pressurePoints.get(0) * pFactor, pressurePoints.get(pressurePoints.size() - 1) * pFactor,
        pUnit));
    bw.write("\n");

    // Viscosity tables
    bw.write("** Oil Viscosity Table\n");
    bw.write("*VISCTABLE 'OIL'\n");
    bw.write("** P(" + pUnit + ")  mu(" + viscUnit + ")\n");

    for (Double p : pressurePoints) {
      double muO = pvt.mu_o(p) * viscFactor;
      if (!Double.isNaN(muO)) {
        bw.write(String.format(Locale.US, "  %.4f  %.6e\n", p * pFactor, muO));
      }
    }
    bw.write("\n");

    bw.write("** Gas Viscosity Table\n");
    bw.write("*VISCTABLE 'GAS'\n");
    bw.write("** P(" + pUnit + ")  mu(" + viscUnit + ")\n");

    for (Double p : pressurePoints) {
      double muG = pvt.mu_g(p) * viscFactor;
      if (!Double.isNaN(muG)) {
        bw.write(String.format(Locale.US, "  %.4f  %.6e\n", p * pFactor, muG));
      }
    }
    bw.write("\n");
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
