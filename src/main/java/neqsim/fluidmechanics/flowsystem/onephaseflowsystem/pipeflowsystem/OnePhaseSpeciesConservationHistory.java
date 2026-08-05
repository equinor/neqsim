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

  /** Amortized-linear accumulator used while a transient solve is running. */
  static final class Builder implements Serializable {
    private static final long serialVersionUID = 1000L;
    private static final int INITIAL_CAPACITY = 16;

    private double[] elapsedTimeSeconds = new double[INITIAL_CAPACITY];
    private OnePhaseSpeciesConservationReport[] reports = new OnePhaseSpeciesConservationReport[INITIAL_CAPACITY];
    private int size;

    /**
     * Append one accepted-step report.
     *
     * @param elapsedSeconds finite, non-negative step-end time in seconds, strictly greater than the previous time
     * @param report non-null immutable conservative species report
     * @throws IllegalArgumentException if the time or report violates the accepted-history contract
     */
    void append(double elapsedSeconds, OnePhaseSpeciesConservationReport report) {
      if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0) {
        throw new IllegalArgumentException("Species-history time must be finite and non-negative: " + elapsedSeconds);
      }
      if (report == null) {
        throw new IllegalArgumentException("Species-history report cannot be null.");
      }
      if (size > 0 && elapsedSeconds <= elapsedTimeSeconds[size - 1]) {
        throw new IllegalArgumentException("Species-history times must increase strictly: previous="
            + elapsedTimeSeconds[size - 1] + ", next=" + elapsedSeconds);
      }

      ensureCapacity(size + 1);
      elapsedTimeSeconds[size] = elapsedSeconds;
      reports[size] = report;
      size++;
    }

    /**
     * Create an immutable snapshot without changing the accumulated accepted steps.
     *
     * @return immutable history snapshot
     */
    OnePhaseSpeciesConservationHistory build() {
      return new OnePhaseSpeciesConservationHistory(Arrays.copyOf(elapsedTimeSeconds, size),
          Arrays.copyOf(reports, size));
    }

    private void ensureCapacity(int requiredCapacity) {
      if (requiredCapacity <= elapsedTimeSeconds.length) {
        return;
      }
      int expandedCapacity = elapsedTimeSeconds.length + (elapsedTimeSeconds.length >> 1);
      int newCapacity = Math.max(requiredCapacity, expandedCapacity);
      elapsedTimeSeconds = Arrays.copyOf(elapsedTimeSeconds, newCapacity);
      reports = Arrays.copyOf(reports, newCapacity);
    }
  }

  private final double[] elapsedTimeSeconds;
  private final OnePhaseSpeciesConservationReport[] reports;

  private OnePhaseSpeciesConservationHistory(double[] elapsedTimeSeconds, OnePhaseSpeciesConservationReport[] reports) {
    this.elapsedTimeSeconds = elapsedTimeSeconds;
    this.reports = reports;
  }

  /**
   * Create an empty history for a system that has not completed a conservative transient step.
   *
   * @return empty immutable history
   */
  public static OnePhaseSpeciesConservationHistory empty() {
    return new OnePhaseSpeciesConservationHistory(new double[0], new OnePhaseSpeciesConservationReport[0]);
  }

  static Builder builder() {
    return new Builder();
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
