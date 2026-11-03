package neqsim.process.operations.replay;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outcome of one time step of a {@link ProcessReplay}.
 *
 * <p>
 * Residuals are {@code predicted - measured} in the unit of the output binding. They are only recorded for converged
 * steps with a valid measurement.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ReplayStepRecord implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Status of a replay step. */
  public enum Status {
    /** The model converged and outputs were recorded. */
    CONVERGED,
    /** The model ran but did not meet the convergence gate after all retries. */
    NOT_CONVERGED,
    /** An input value was missing, outside its valid range, or rejected by the automation facade. */
    INPUT_INVALID,
    /** The step was skipped by the steady-state or user filter. */
    SKIPPED,
    /** The model run threw an exception. */
    FAILED
  }

  private final int index;
  private final double time;
  private Status status = Status.SKIPPED;
  private String message = "";
  private int attempts = 0;
  private String terminationReason = "";
  private final Map<String, Double> inputs = new LinkedHashMap<String, Double>();
  private final Map<String, Double> predicted = new LinkedHashMap<String, Double>();
  private final Map<String, Double> measured = new LinkedHashMap<String, Double>();
  private final Map<String, Double> residuals = new LinkedHashMap<String, Double>();

  /**
   * Creates a record for one time step.
   *
   * @param index row index in the time series
   * @param time time stamp of the row
   */
  public ReplayStepRecord(int index, double time) {
    this.index = index;
    this.time = time;
  }

  /**
   * Sets the status and message.
   *
   * @param status step status
   * @param message human-readable explanation, may be empty
   */
  void setStatus(Status status, String message) {
    this.status = status;
    this.message = message == null ? "" : message;
  }

  /**
   * Sets the number of run attempts.
   *
   * @param attempts attempts including retries
   */
  void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  /**
   * Sets the run termination reason reported by the model.
   *
   * @param reason termination reason, for example {@code CONVERGED} or {@code ITERATION_LIMIT}
   */
  void setTerminationReason(String reason) {
    this.terminationReason = reason == null ? "" : reason;
  }

  /**
   * Records an applied input value.
   *
   * @param tag input tag
   * @param value converted value written to the model
   */
  void putInput(String tag, double value) {
    inputs.put(tag, value);
  }

  /**
   * Records a predicted output, the measurement and the residual.
   *
   * @param tag measured tag
   * @param predictedValue model prediction
   * @param measuredValue converted measurement, NaN when invalid
   */
  void putOutput(String tag, double predictedValue, double measuredValue) {
    predicted.put(tag, predictedValue);
    measured.put(tag, measuredValue);
    double residual = ReplayBinding.isFinite(predictedValue) && ReplayBinding.isFinite(measuredValue)
        ? predictedValue - measuredValue
        : Double.NaN;
    residuals.put(tag, residual);
  }

  /**
   * Returns the row index.
   *
   * @return row index
   */
  public int getIndex() {
    return index;
  }

  /**
   * Returns the time stamp.
   *
   * @return time stamp
   */
  public double getTime() {
    return time;
  }

  /**
   * Returns the step status.
   *
   * @return status
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Checks whether the step converged.
   *
   * @return true for {@link Status#CONVERGED}
   */
  public boolean isConverged() {
    return status == Status.CONVERGED;
  }

  /**
   * Returns the explanation of the status.
   *
   * @return message, empty when none
   */
  public String getMessage() {
    return message;
  }

  /**
   * Returns the number of run attempts including retries.
   *
   * @return attempt count
   */
  public int getAttempts() {
    return attempts;
  }

  /**
   * Returns the termination reason reported by the model run status.
   *
   * @return termination reason, empty when not run
   */
  public String getTerminationReason() {
    return terminationReason;
  }

  /**
   * Returns the inputs written to the model.
   *
   * @return unmodifiable map from input tag to converted value
   */
  public Map<String, Double> getInputs() {
    return Collections.unmodifiableMap(inputs);
  }

  /**
   * Returns the predicted outputs.
   *
   * @return unmodifiable map from measured tag to prediction
   */
  public Map<String, Double> getPredicted() {
    return Collections.unmodifiableMap(predicted);
  }

  /**
   * Returns the converted measurements.
   *
   * @return unmodifiable map from measured tag to measurement
   */
  public Map<String, Double> getMeasured() {
    return Collections.unmodifiableMap(measured);
  }

  /**
   * Returns the residuals {@code predicted - measured}.
   *
   * @return unmodifiable map from measured tag to residual (NaN when not available)
   */
  public Map<String, Double> getResiduals() {
    return Collections.unmodifiableMap(residuals);
  }

  /**
   * Returns the residual of one tag.
   *
   * @param tag measured tag
   * @return residual, or NaN when not available
   */
  public double getResidual(String tag) {
    Double value = residuals.get(tag);
    return value == null ? Double.NaN : value.doubleValue();
  }
}
