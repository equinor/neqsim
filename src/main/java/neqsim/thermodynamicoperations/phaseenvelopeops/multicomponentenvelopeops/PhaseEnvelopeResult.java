package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import java.io.Serializable;

/**
 * Structured status report for a phase envelope calculation.
 *
 * <p>
 * Provides the convergence status, diagnostic message, number of traced points, and key reference
 * values (cricondenbar and cricondentherm) from a completed
 * {@link PTPhaseEnvelopeMichelsen} run.
 * </p>
 *
 * <p>
 * Obtain an instance via {@link PTPhaseEnvelopeMichelsen#getEnvelopeResult()} after calling
 * {@code run()}.
 * </p>
 *
 * @author NeqSim Agent
 * @version 1.0
 */
public class PhaseEnvelopeResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Convergence status of the phase envelope calculation.
   *
   * @author NeqSim Agent
   * @version 1.0
   */
  public enum Status {
    /** Envelope traced successfully with more than one point on each branch. */
    CONVERGED,
    /** Envelope traced but produced very few points (likely a degenerate system). */
    DEGENERATE,
    /** No points were traced at all. */
    EMPTY,
    /** The envelope calculation has not been executed yet. */
    NOT_RUN
  }

  private final Status status;
  private final String diagnosticMessage;
  private final int bubblePointCount;
  private final int dewPointCount;
  private final double cricondenbarPressure;
  private final double cricondenbarTemperature;
  private final double cricondenthermPressure;
  private final double cricondenthermTemperature;

  /**
   * Constructs a PhaseEnvelopeResult.
   *
   * @param status convergence status
   * @param diagnosticMessage human-readable diagnostic
   * @param bubblePointCount number of bubble-point curve points
   * @param dewPointCount number of dew-point curve points
   * @param cricondenbarPressure cricondenbar pressure in bara
   * @param cricondenbarTemperature cricondenbar temperature in K
   * @param cricondenthermPressure cricondentherm pressure in bara
   * @param cricondenthermTemperature cricondentherm temperature in K
   */
  public PhaseEnvelopeResult(Status status, String diagnosticMessage, int bubblePointCount,
      int dewPointCount, double cricondenbarPressure, double cricondenbarTemperature,
      double cricondenthermPressure, double cricondenthermTemperature) {
    this.status = status;
    this.diagnosticMessage = diagnosticMessage;
    this.bubblePointCount = bubblePointCount;
    this.dewPointCount = dewPointCount;
    this.cricondenbarPressure = cricondenbarPressure;
    this.cricondenbarTemperature = cricondenbarTemperature;
    this.cricondenthermPressure = cricondenthermPressure;
    this.cricondenthermTemperature = cricondenthermTemperature;
  }

  /**
   * Returns the convergence status.
   *
   * @return the status
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Returns true if the envelope converged (status is CONVERGED).
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return status == Status.CONVERGED;
  }

  /**
   * Returns the human-readable diagnostic message.
   *
   * @return diagnostic string
   */
  public String getDiagnosticMessage() {
    return diagnosticMessage;
  }

  /**
   * Returns the number of bubble-point curve data points.
   *
   * @return bubble point count
   */
  public int getBubblePointCount() {
    return bubblePointCount;
  }

  /**
   * Returns the number of dew-point curve data points.
   *
   * @return dew point count
   */
  public int getDewPointCount() {
    return dewPointCount;
  }

  /**
   * Returns the total number of traced points across both branches.
   *
   * @return total point count
   */
  public int getTotalPointCount() {
    return bubblePointCount + dewPointCount;
  }

  /**
   * Returns the cricondenbar pressure in bara.
   *
   * @return cricondenbar pressure, or 0 if not determined
   */
  public double getCricondenbarPressure() {
    return cricondenbarPressure;
  }

  /**
   * Returns the cricondenbar temperature in K.
   *
   * @return cricondenbar temperature, or 0 if not determined
   */
  public double getCricondenbarTemperature() {
    return cricondenbarTemperature;
  }

  /**
   * Returns the cricondentherm pressure in bara.
   *
   * @return cricondentherm pressure, or 0 if not determined
   */
  public double getCricondenthermPressure() {
    return cricondenthermPressure;
  }

  /**
   * Returns the cricondentherm temperature in K.
   *
   * @return cricondentherm temperature, or 0 if not determined
   */
  public double getCricondenthermTemperature() {
    return cricondenthermTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "PhaseEnvelopeResult{status=" + status + ", bubPts=" + bubblePointCount + ", dewPts="
        + dewPointCount + ", cricondenbarP=" + cricondenbarPressure + " bara, cricondenthermT="
        + cricondenthermTemperature + " K, message='" + diagnosticMessage + "'}";
  }
}
