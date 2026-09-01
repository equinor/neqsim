package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

/** Immutable time-aligned history of accepted TwoFluidPipe component-conservation reports. */
public final class TwoFluidComponentConservationHistory implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double[] simulationTimeSeconds;
  private final List<TwoFluidComponentConservationReport> reports;

  TwoFluidComponentConservationHistory(double[] simulationTimeSeconds,
      List<TwoFluidComponentConservationReport> reports) {
    if (simulationTimeSeconds == null || reports == null) {
      throw new IllegalArgumentException("Component history times and reports cannot be null");
    }
    if (simulationTimeSeconds.length != reports.size()) {
      throw new IllegalArgumentException("Component history times and reports must have identical lengths");
    }
    double previousTime = Double.NEGATIVE_INFINITY;
    for (int index = 0; index < simulationTimeSeconds.length; index++) {
      double time = simulationTimeSeconds[index];
      if (!Double.isFinite(time) || time < 0.0 || time <= previousTime) {
        throw new IllegalArgumentException("Component history times must be finite, non-negative, and increasing");
      }
      if (reports.get(index) == null) {
        throw new IllegalArgumentException("Component history reports cannot contain null");
      }
      previousTime = time;
    }
    this.simulationTimeSeconds = Arrays.copyOf(simulationTimeSeconds, simulationTimeSeconds.length);
    this.reports = Collections.unmodifiableList(new ArrayList<TwoFluidComponentConservationReport>(reports));
  }

  /** @return immutable empty history */
  public static TwoFluidComponentConservationHistory empty() {
    return new TwoFluidComponentConservationHistory(new double[0],
        Collections.<TwoFluidComponentConservationReport>emptyList());
  }

  /** @return number of accepted outer transient calls retained */
  public int size() {
    return reports.size();
  }

  /** @return defensive accepted simulation-time array in seconds */
  public double[] getSimulationTimeSeconds() {
    return Arrays.copyOf(simulationTimeSeconds, simulationTimeSeconds.length);
  }

  /** @return defensive report array */
  public TwoFluidComponentConservationReport[] getReports() {
    return reports.toArray(new TwoFluidComponentConservationReport[reports.size()]);
  }

  /**
   * Get a report by accepted outer-step index.
   *
   * @param index zero-based history index
   * @return immutable report
   */
  public TwoFluidComponentConservationReport getReport(int index) {
    return reports.get(index);
  }

  /**
   * Serialize the history for Python/JPype capture.
   *
   * @return stable, pretty-printed JSON
   */
  public String toJson() {
    JsonSerializer<Double> finiteDoubleSerializer = (value, type,
        context) -> value != null && Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    return new GsonBuilder().registerTypeAdapter(Double.class, finiteDoubleSerializer)
        .registerTypeAdapter(Double.TYPE, finiteDoubleSerializer).serializeNulls().setPrettyPrinting().create()
        .toJson(this);
  }
}
