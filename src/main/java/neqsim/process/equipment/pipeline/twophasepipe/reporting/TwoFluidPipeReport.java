package neqsim.process.equipment.pipeline.twophasepipe.reporting;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.validation.TwoFluidBenchmarkHarness;

/**
 * Reporting helpers for {@link TwoFluidPipe} profiles, summaries, and benchmark comparisons.
 */
public final class TwoFluidPipeReport {
  private TwoFluidPipeReport() {}

  /**
   * Immutable snapshot of the profile arrays at one simulation time.
   */
  public static final class ProfileSnapshot {
    private final double timeSeconds;
    private final double[] positionMeters;
    private final double[] pressurePa;
    private final double[] temperatureK;
    private final double[] liquidHoldup;
    private final double[] waterCut;
    private final double[] oilHoldup;
    private final double[] waterHoldup;
    private final double[] gasVelocity;
    private final double[] liquidVelocity;
    private final double[] oilVelocity;
    private final double[] waterVelocity;
    private final FlowRegime[] flowRegime;

    private ProfileSnapshot(TwoFluidPipe pipe) {
      this.timeSeconds = pipe.getSimulationTime();
      this.positionMeters = pipe.getPositionProfile();
      this.pressurePa = pipe.getPressureProfile();
      this.temperatureK = pipe.getTemperatureProfile();
      this.liquidHoldup = pipe.getLiquidHoldupProfile();
      this.waterCut = pipe.getWaterCutProfile();
      this.oilHoldup = pipe.getOilHoldupProfile();
      this.waterHoldup = pipe.getWaterHoldupProfile();
      this.gasVelocity = pipe.getGasVelocityProfile();
      this.liquidVelocity = pipe.getLiquidVelocityProfile();
      this.oilVelocity = pipe.getOilVelocityProfile();
      this.waterVelocity = pipe.getWaterVelocityProfile();
      this.flowRegime = pipe.getFlowRegimeProfile();
    }

    public double getTimeSeconds() {
      return timeSeconds;
    }
  }

  /**
   * Capture all reportable pipe profiles at the current simulation time.
   *
   * @param pipe solved pipe
   * @return profile snapshot
   */
  public static ProfileSnapshot capture(TwoFluidPipe pipe) {
    return new ProfileSnapshot(pipe);
  }

  /**
   * Export the current pipe profile as CSV.
   *
   * @param pipe solved pipe
   * @return CSV text
   */
  public static String toSteadyStateProfileCsv(TwoFluidPipe pipe) {
    return toProfileCsvRows(capture(pipe), false);
  }

  /**
   * Export transient snapshots as time-position CSV.
   *
   * @param snapshots profile snapshots collected during transient simulation
   * @return CSV text
   */
  public static String toTransientProfileCsv(List<ProfileSnapshot> snapshots) {
    StringBuilder csv = new StringBuilder();
    boolean headerWritten = false;
    for (ProfileSnapshot snapshot : snapshots) {
      String rows = toProfileCsvRows(snapshot, true);
      if (!headerWritten) {
        csv.append(rows);
        headerWritten = true;
      } else {
        int firstNewline = rows.indexOf('\n');
        if (firstNewline >= 0 && firstNewline + 1 < rows.length()) {
          csv.append(rows.substring(firstNewline + 1));
        }
      }
    }
    return csv.toString();
  }

  /**
   * Build a concise text summary of the solved pipe.
   *
   * @param pipe solved pipe
   * @return text summary
   */
  public static String toSummaryText(TwoFluidPipe pipe) {
    StringBuilder sb = new StringBuilder();
    sb.append("TwoFluidPipe summary: ").append(pipe.getName()).append(System.lineSeparator());
    sb.append(String.format(Locale.ROOT, "Simulation time: %.3f s%n", pipe.getSimulationTime()));
    sb.append(String.format(Locale.ROOT, "Inlet pressure: %.3f bara%n", pipe.getInletPressure()));
    sb.append(String.format(Locale.ROOT, "Outlet pressure: %.3f bara%n", pipe.getOutletPressure()));
    sb.append(String.format(Locale.ROOT, "Average liquid holdup: %.5f%n",
        pipe.getAverageLiquidHoldup()));
    sb.append("Dominant flow regime: ").append(pipe.getDominantFlowRegime())
        .append(System.lineSeparator());
    sb.append(String.format(Locale.ROOT, "Liquid inventory: %.5f m3%n",
        pipe.getLiquidInventory("m3")));
    sb.append(String.format(Locale.ROOT, "Maximum mixture velocity: %.5f m/s%n",
        pipe.getMaxMixtureVelocity()));
    sb.append(String.format(Locale.ROOT, "Erosional velocity margin: %.5f%n",
        pipe.getErosionalVelocityMargin(122.0)));
    sb.append(String.format(Locale.ROOT, "Hydrate risk sections: %d%n",
        pipe.getHydrateRiskSectionCount()));
    sb.append("Wax risk: ").append(pipe.hasWaxRisk()).append(System.lineSeparator());
    sb.append(String.format(Locale.ROOT, "Outlet slug count: %d%n", pipe.getOutletSlugCount()));
    sb.append(String.format(Locale.ROOT, "Total outlet slug volume: %.5f m3%n",
        pipe.getTotalSlugVolumeAtOutlet()));
    return sb.toString();
  }

  /**
   * Build a JSON summary of key pipe results.
   *
   * @param pipe solved pipe
   * @return JSON text
   */
  public static String toSummaryJson(TwoFluidPipe pipe) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("name", pipe.getName());
    summary.put("simulationTimeSeconds", pipe.getSimulationTime());
    summary.put("inletPressureBara", pipe.getInletPressure());
    summary.put("outletPressureBara", pipe.getOutletPressure());
    summary.put("averageLiquidHoldup", pipe.getAverageLiquidHoldup());
    summary.put("dominantFlowRegime", pipe.getDominantFlowRegime());
    summary.put("liquidInventoryM3", pipe.getLiquidInventory("m3"));
    summary.put("averageMixtureDensityKgM3", pipe.getAverageMixtureDensity());
    summary.put("maximumMixtureVelocityMS", pipe.getMaxMixtureVelocity());
    summary.put("erosionalVelocityMargin", pipe.getErosionalVelocityMargin(122.0));
    summary.put("hydrateRiskSectionCount", pipe.getHydrateRiskSectionCount());
    summary.put("hasWaxRisk", pipe.hasWaxRisk());
    summary.put("outletSlugCount", pipe.getOutletSlugCount());
    summary.put("totalSlugVolumeAtOutletM3", pipe.getTotalSlugVolumeAtOutlet());
    summary.put("maxSlugLengthAtOutletM", pipe.getMaxSlugLengthAtOutlet());
    summary.put("maxSlugVolumeAtOutletM3", pipe.getMaxSlugVolumeAtOutlet());
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(summary);
  }

  /**
   * Export slug and flow-assurance events as CSV.
   *
   * @param pipe solved pipe
   * @return CSV text
   */
  public static String toSlugAndFlowAssuranceCsv(TwoFluidPipe pipe) {
    StringBuilder csv = new StringBuilder();
    csv.append("event_type,position_m,value,unit,description\n");
    csv.append(csvRow("slug_count", "", pipe.getOutletSlugCount(), "count", "Outlet slug count"));
    csv.append(csvRow("slug_volume_total", "", pipe.getTotalSlugVolumeAtOutlet(), "m3",
        "Total outlet slug volume"));
    csv.append(csvRow("slug_length_max", "", pipe.getMaxSlugLengthAtOutlet(), "m",
        "Maximum outlet slug length"));
    csv.append(csvRow("slug_volume_max", "", pipe.getMaxSlugVolumeAtOutlet(), "m3",
        "Maximum outlet slug volume"));

    double hydrateDistance = pipe.getDistanceToHydrateRisk();
    if (hydrateDistance >= 0.0) {
      csv.append(csvRow("hydrate_risk_first", hydrateDistance, 1.0, "flag",
          "First section below hydrate risk temperature"));
    }
    csv.append(csvRow("hydrate_risk_count", "", pipe.getHydrateRiskSectionCount(), "count",
        "Number of hydrate risk sections"));
    csv.append(csvRow("wax_risk", "", pipe.hasWaxRisk() ? 1.0 : 0.0, "flag",
        "At least one section below wax appearance temperature"));
    csv.append(csvRow("erosion_margin", "", pipe.getErosionalVelocityMargin(122.0), "ratio",
        "Maximum mixture velocity divided by API 14E erosional velocity"));
    return csv.toString();
  }

  /**
   * Export benchmark comparison results as CSV.
   *
   * @param comparison benchmark comparison
   * @return CSV text
   */
  public static String toComparisonCsv(TwoFluidBenchmarkHarness.Comparison comparison) {
    StringBuilder csv = new StringBuilder();
    csv.append("case,time_s,position_m,variable,reference,model,abs_error,rel_error,passed,source\n");
    for (TwoFluidBenchmarkHarness.ComparisonRow row : comparison.getRows()) {
      TwoFluidBenchmarkHarness.BenchmarkPoint point = row.getReference();
      csv.append(escape(point.getCaseName())).append(',')
          .append(format(point.getTimeSeconds())).append(',')
          .append(format(point.getPositionMeters())).append(',')
          .append(escape(point.getVariable())).append(',')
          .append(format(point.getValue())).append(',')
          .append(format(row.getModelValue())).append(',')
          .append(format(row.getAbsoluteError())).append(',')
          .append(format(row.getRelativeError())).append(',')
          .append(row.isPassed()).append(',')
          .append(escape(point.getSource())).append('\n');
    }
    return csv.toString();
  }

  public static void writeSteadyStateProfileCsv(TwoFluidPipe pipe, Path path) throws IOException {
    write(path, toSteadyStateProfileCsv(pipe));
  }

  public static void writeTransientProfileCsv(List<ProfileSnapshot> snapshots, Path path)
      throws IOException {
    write(path, toTransientProfileCsv(snapshots));
  }

  public static void writeSummaryText(TwoFluidPipe pipe, Path path) throws IOException {
    write(path, toSummaryText(pipe));
  }

  public static void writeSummaryJson(TwoFluidPipe pipe, Path path) throws IOException {
    write(path, toSummaryJson(pipe));
  }

  public static void writeSlugAndFlowAssuranceCsv(TwoFluidPipe pipe, Path path) throws IOException {
    write(path, toSlugAndFlowAssuranceCsv(pipe));
  }

  public static void writeComparisonCsv(TwoFluidBenchmarkHarness.Comparison comparison, Path path)
      throws IOException {
    write(path, toComparisonCsv(comparison));
  }

  private static String toProfileCsvRows(ProfileSnapshot snapshot, boolean includeTime) {
    validateProfileLengths(snapshot);
    StringBuilder csv = new StringBuilder();
    if (includeTime) {
      csv.append("time_s,");
    }
    csv.append("position_m,pressure_bara,temperature_C,liquid_holdup,water_cut,oil_holdup,")
        .append("water_holdup,gas_velocity_m_s,liquid_velocity_m_s,oil_velocity_m_s,")
        .append("water_velocity_m_s,flow_regime\n");

    for (int i = 0; i < snapshot.positionMeters.length; i++) {
      if (includeTime) {
        csv.append(format(snapshot.timeSeconds)).append(',');
      }
      csv.append(format(snapshot.positionMeters[i])).append(',')
          .append(format(snapshot.pressurePa[i] * 1.0e-5)).append(',')
          .append(format(snapshot.temperatureK[i] - 273.15)).append(',')
          .append(format(snapshot.liquidHoldup[i])).append(',')
          .append(format(valueAt(snapshot.waterCut, i))).append(',')
          .append(format(valueAt(snapshot.oilHoldup, i))).append(',')
          .append(format(valueAt(snapshot.waterHoldup, i))).append(',')
          .append(format(valueAt(snapshot.gasVelocity, i))).append(',')
          .append(format(valueAt(snapshot.liquidVelocity, i))).append(',')
          .append(format(valueAt(snapshot.oilVelocity, i))).append(',')
          .append(format(valueAt(snapshot.waterVelocity, i))).append(',')
          .append(snapshot.flowRegime.length > i && snapshot.flowRegime[i] != null
              ? snapshot.flowRegime[i].name()
              : "")
          .append('\n');
    }
    return csv.toString();
  }

  private static void validateProfileLengths(ProfileSnapshot snapshot) {
    int n = snapshot.positionMeters.length;
    if (snapshot.pressurePa.length != n || snapshot.temperatureK.length != n
        || snapshot.liquidHoldup.length != n) {
      throw new IllegalStateException("Core TwoFluidPipe profile lengths differ");
    }
  }

  private static double valueAt(double[] values, int index) {
    return values.length > index ? values[index] : Double.NaN;
  }

  private static String csvRow(String type, Object position, double value, String unit,
      String description) {
    return escape(type) + "," + escape(String.valueOf(position)) + "," + format(value) + ","
        + escape(unit) + "," + escape(description) + "\n";
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.10g", value);
  }

  private static void write(Path path, String content) throws IOException {
    Path parent = path.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Convenience container for collecting transient snapshots.
   *
   * @return mutable snapshot list
   */
  public static List<ProfileSnapshot> newSnapshotList() {
    return new ArrayList<>();
  }
}
