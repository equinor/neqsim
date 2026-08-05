package neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem;

import java.io.Serializable;
import java.util.Arrays;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseSpeciesConservationReport;

/** Immutable time-aligned history of accepted conservative one-phase species reports. */
public final class OnePhaseSpeciesConservationHistory implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final double[] elapsedTimeSeconds;
  private final OnePhaseSpeciesConservationReport[] reports;

  private OnePhaseSpeciesConservationHistory(double[] elapsedTimeSeconds, OnePhaseSpeciesConservationReport[] reports) {
    this.elapsedTimeSeconds = elapsedTimeSeconds.clone();
    this.reports = reports.clone();
  }

  /**
   * Create an empty history for a system that has not completed a conservative transient step.
   *
   * @return empty immutable history
   */
  public static OnePhaseSpeciesConservationHistory empty() {
    return new OnePhaseSpeciesConservationHistory(new double[0], new OnePhaseSpeciesConservationReport[0]);
  }

  OnePhaseSpeciesConservationHistory append(double elapsedSeconds, OnePhaseSpeciesConservationReport report) {
    if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0) {
      throw new IllegalArgumentException("Species-history time must be finite and non-negative: " + elapsedSeconds);
    }
    if (report == null) {
      throw new IllegalArgumentException("Species-history report cannot be null.");
    }
    if (elapsedTimeSeconds.length > 0 && elapsedSeconds <= elapsedTimeSeconds[elapsedTimeSeconds.length - 1]) {
      throw new IllegalArgumentException("Species-history times must increase strictly: previous="
          + elapsedTimeSeconds[elapsedTimeSeconds.length - 1] + ", next=" + elapsedSeconds);
    }

    double[] updatedTimes = Arrays.copyOf(elapsedTimeSeconds, elapsedTimeSeconds.length + 1);
    updatedTimes[updatedTimes.length - 1] = elapsedSeconds;
    OnePhaseSpeciesConservationReport[] updatedReports = Arrays.copyOf(reports, reports.length + 1);
    updatedReports[updatedReports.length - 1] = report;
    return new OnePhaseSpeciesConservationHistory(updatedTimes, updatedReports);
  }

  /** @return number of accepted conservative transient steps */
  public int size() {
    return reports.length;
  }

  /** @return true when no conservative transient step has completed */
  public boolean isEmpty() {
    return reports.length == 0;
  }

  /** @return defensive copy of elapsed accepted-step end times in seconds */
  public double[] getElapsedTimeSeconds() {
    return elapsedTimeSeconds.clone();
  }

  /** @return defensive copy of immutable per-step reports */
  public OnePhaseSpeciesConservationReport[] getReports() {
    return reports.clone();
  }

  /**
   * Get one accepted-step report.
   *
   * @param index zero-based accepted-step index
   * @return immutable conservative species report
   */
  public OnePhaseSpeciesConservationReport getReport(int index) {
    return reports[index];
  }

  /**
   * Serialize elapsed times and reports as stable, pretty-printed JSON.
   *
   * <p>
   * Non-finite diagnostic values are represented as JSON null rather than invalid JSON tokens.
   * </p>
   *
   * @return JSON representation suitable for Python-side result capture
   */
  public String toJson() {
    JsonSerializer<Double> finiteDoubleSerializer = (value, type,
        context) -> value != null && Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    return new GsonBuilder().registerTypeAdapter(Double.class, finiteDoubleSerializer)
        .registerTypeAdapter(Double.TYPE, finiteDoubleSerializer).serializeNulls().setPrettyPrinting().create()
        .toJson(this);
  }
}
