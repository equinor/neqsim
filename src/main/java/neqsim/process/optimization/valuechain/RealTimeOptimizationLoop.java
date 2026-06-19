package neqsim.process.optimization.valuechain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Orchestrates a closed-loop real-time optimization (RTO) cycle.
 *
 * <p>
 * Real-time optimization closes the loop between the plant and its model: read current
 * measurements, reconcile/calibrate the model against them, optimize the setpoints on the
 * calibrated model, then push the new setpoints back to the plant. This class wires those steps
 * together as pluggable functional stages and runs them for a configured number of cycles, keeping
 * an auditable record of each cycle (measurements, chosen setpoints, achieved objective).
 * </p>
 *
 * <p>
 * Each stage is supplied by the caller so the loop is agnostic to the data source and the
 * optimizer: the reader typically wraps {@code tagreader} historian access, the calibrator wraps a
 * data-reconciliation step, the optimizer wraps {@code AgenticProcessOptimizer} or
 * {@link NetworkAllocationOptimizer}, and the writer pushes setpoints through
 * {@code ProcessAutomation} (or a control-system bridge). The reader, calibrator and objective
 * probe are optional; the optimizer and writer are required.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class RealTimeOptimizationLoop implements Serializable {

  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Stage that reads current plant measurements (optional). */
  private PlantReader reader;

  /** Stage that calibrates the model to measurements (optional). */
  private Calibrator calibrator;

  /** Stage that computes new setpoints (required). */
  private SetpointOptimizer optimizer;

  /** Stage that pushes setpoints to the plant (required). */
  private SetpointWriter writer;

  /** Stage that probes the achieved objective after applying setpoints (optional). */
  private ObjectiveProbe probe;

  /** History of executed cycles. */
  private final List<CycleRecord> history = new ArrayList<CycleRecord>();

  /**
   * Stage that reads current plant measurements.
   */
  public interface PlantReader {
    /**
     * Reads the current plant measurements.
     *
     * @return the measurement vector
     */
    double[] read();
  }

  /**
   * Stage that calibrates the model to the latest measurements.
   */
  public interface Calibrator {
    /**
     * Calibrates the model against the supplied measurements.
     *
     * @param measurements the latest measurement vector
     */
    void calibrate(double[] measurements);
  }

  /**
   * Stage that computes new setpoints from the calibrated model.
   */
  public interface SetpointOptimizer {
    /**
     * Computes the new setpoint vector.
     *
     * @return the new setpoint vector
     */
    double[] optimize();
  }

  /**
   * Stage that pushes setpoints to the plant.
   */
  public interface SetpointWriter {
    /**
     * Applies the supplied setpoints to the plant.
     *
     * @param setpoints the setpoint vector to apply
     */
    void apply(double[] setpoints);
  }

  /**
   * Stage that probes the achieved objective after setpoints are applied.
   */
  public interface ObjectiveProbe {
    /**
     * Returns the current objective value.
     *
     * @return the achieved objective value
     */
    double currentObjective();
  }

  /**
   * Immutable record of one executed RTO cycle.
   */
  public static class CycleRecord implements Serializable {

    /** Serialization version identifier. */
    private static final long serialVersionUID = 1000L;

    /** 1-based cycle index. */
    private final int cycle;

    /** Measurements read at the start of the cycle (may be empty). */
    private final double[] measurements;

    /** Setpoints applied during the cycle. */
    private final double[] setpoints;

    /** Achieved objective after applying setpoints (NaN if not probed). */
    private final double objective;

    /**
     * Creates a cycle record.
     *
     * @param cycle the 1-based cycle index
     * @param measurements the measurements read at the start of the cycle
     * @param setpoints the setpoints applied during the cycle
     * @param objective the achieved objective (NaN if not probed)
     */
    public CycleRecord(int cycle, double[] measurements, double[] setpoints, double objective) {
      this.cycle = cycle;
      this.measurements = measurements != null ? measurements.clone() : new double[0];
      this.setpoints = setpoints != null ? setpoints.clone() : new double[0];
      this.objective = objective;
    }

    /**
     * Gets the 1-based cycle index.
     *
     * @return the cycle index
     */
    public int getCycle() {
      return cycle;
    }

    /**
     * Gets the measurements read at the start of the cycle.
     *
     * @return a copy of the measurement vector
     */
    public double[] getMeasurements() {
      return measurements.clone();
    }

    /**
     * Gets the setpoints applied during the cycle.
     *
     * @return a copy of the setpoint vector
     */
    public double[] getSetpoints() {
      return setpoints.clone();
    }

    /**
     * Gets the achieved objective after applying setpoints.
     *
     * @return the objective value, or NaN if not probed
     */
    public double getObjective() {
      return objective;
    }
  }

  /**
   * Sets the optional plant-measurement reader stage.
   *
   * @param reader the reader stage (may be null)
   * @return this loop for method chaining
   */
  public RealTimeOptimizationLoop setReader(PlantReader reader) {
    this.reader = reader;
    return this;
  }

  /**
   * Sets the optional model-calibration stage.
   *
   * @param calibrator the calibrator stage (may be null)
   * @return this loop for method chaining
   */
  public RealTimeOptimizationLoop setCalibrator(Calibrator calibrator) {
    this.calibrator = calibrator;
    return this;
  }

  /**
   * Sets the required setpoint-optimizer stage.
   *
   * @param optimizer the optimizer stage (must not be null)
   * @return this loop for method chaining
   */
  public RealTimeOptimizationLoop setOptimizer(SetpointOptimizer optimizer) {
    this.optimizer = optimizer;
    return this;
  }

  /**
   * Sets the required setpoint-writer stage.
   *
   * @param writer the writer stage (must not be null)
   * @return this loop for method chaining
   */
  public RealTimeOptimizationLoop setWriter(SetpointWriter writer) {
    this.writer = writer;
    return this;
  }

  /**
   * Sets the optional objective-probe stage.
   *
   * @param probe the probe stage (may be null)
   * @return this loop for method chaining
   */
  public RealTimeOptimizationLoop setObjectiveProbe(ObjectiveProbe probe) {
    this.probe = probe;
    return this;
  }

  /**
   * Runs the closed-loop optimization for the requested number of cycles.
   *
   * <p>
   * Each cycle reads measurements (if a reader is set), calibrates the model (if a calibrator is
   * set), computes new setpoints, applies them, probes the achieved objective (if a probe is set),
   * and records the cycle. The full cycle history is retained and also returned.
   * </p>
   *
   * @param cycles the number of cycles to run (must be positive)
   * @return the list of executed cycle records
   */
  public List<CycleRecord> run(int cycles) {
    if (cycles <= 0) {
      throw new IllegalArgumentException("cycles must be positive");
    }
    if (optimizer == null) {
      throw new IllegalStateException("A SetpointOptimizer stage is required");
    }
    if (writer == null) {
      throw new IllegalStateException("A SetpointWriter stage is required");
    }
    for (int c = 1; c <= cycles; c++) {
      double[] measurements = reader != null ? reader.read() : new double[0];
      if (calibrator != null) {
        calibrator.calibrate(measurements);
      }
      double[] setpoints = optimizer.optimize();
      writer.apply(setpoints);
      double objective = probe != null ? probe.currentObjective() : Double.NaN;
      history.add(new CycleRecord(c, measurements, setpoints, objective));
    }
    return getHistory();
  }

  /**
   * Gets the history of executed cycles.
   *
   * @return an unmodifiable list of cycle records
   */
  public List<CycleRecord> getHistory() {
    return Collections.unmodifiableList(history);
  }

  /**
   * Renders the cycle history as a JSON object.
   *
   * @return a JSON string describing the executed cycles
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"cycles\":[");
    for (int i = 0; i < history.size(); i++) {
      CycleRecord r = history.get(i);
      if (i > 0) {
        sb.append(",");
      }
      sb.append("{");
      sb.append("\"cycle\":").append(r.getCycle()).append(",");
      sb.append("\"setpoints\":").append(Arrays.toString(r.getSetpoints())).append(",");
      sb.append("\"objective\":").append(fmt(r.getObjective()));
      sb.append("}");
    }
    sb.append("]}");
    return sb.toString();
  }

  /**
   * Formats a double for JSON output, mapping non-finite values to {@code null}.
   *
   * @param value the value to format
   * @return the formatted string or {@code null}
   */
  private static String fmt(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return "null";
    }
    return String.format(Locale.US, "%.6g", value);
  }
}
