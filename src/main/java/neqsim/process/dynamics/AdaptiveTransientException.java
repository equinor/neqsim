package neqsim.process.dynamics;

/**
 * Structured failure after a bounded adaptive step cannot accept a numerical trial.
 *
 * <p>
 * The caller can distinguish exhausted numerical budgets from an interval that cannot be represented at the current
 * clock. A NaN error explicitly means that no valid comparison was available; positive infinity records arithmetic
 * overflow or an unbounded normalized difference.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class AdaptiveTransientException extends IllegalStateException {
  private static final long serialVersionUID = 1000L;

  /** Numerical termination reason independent of human-readable diagnostics. */
  public enum Reason {
    /** Meeting the error criterion requires a step below the declared minimum. */
    MINIMUM_STEP,
    /** The declared maximum number of trial attempts has been exhausted. */
    ATTEMPT_LIMIT,
    /** The requested interval or its halves cannot advance the model clock representably. */
    UNREPRESENTABLE_INTERVAL
  }

  private final Reason reason;
  private final int attempts;
  private final double attemptedStepSeconds;
  private final double normalizedError;
  private final String limitingVariable;

  /**
   * Creates numerical failure evidence after trial state has been restored.
   *
   * @param reason explicit numerical termination category
   * @param message stable, nonblank diagnostic message
   * @param attempts nonnegative number of attempted trials
   * @param attemptedStepSeconds finite positive size of the final attempted interval in seconds
   * @param normalizedError nonnegative comparison error, positive infinity, or NaN when unavailable
   * @param limitingVariable limiting state identity, or null when no comparison was available
   * @throws IllegalArgumentException if required failure evidence is invalid
   */
  public AdaptiveTransientException(Reason reason, String message, int attempts, double attemptedStepSeconds,
      double normalizedError, String limitingVariable) {
    super(requireMessage(message));
    if (reason == null || attempts < 0 || !Double.isFinite(attemptedStepSeconds) || attemptedStepSeconds <= 0.0
        || normalizedError < 0.0) {
      throw new IllegalArgumentException("Invalid adaptive transient termination evidence");
    }
    this.reason = reason;
    this.attempts = attempts;
    this.attemptedStepSeconds = attemptedStepSeconds;
    this.normalizedError = normalizedError;
    this.limitingVariable = limitingVariable;
  }

  /**
   * Requires a stable explicit diagnostic message.
   *
   * @param message supplied diagnostic text
   * @return original diagnostic message
   * @throws IllegalArgumentException if the message is null or blank
   */
  private static String requireMessage(String message) {
    if (message == null || message.trim().isEmpty()) {
      throw new IllegalArgumentException("Adaptive transient failure message must not be null or blank");
    }
    return message;
  }

  /**
   * Returns the numerical termination category.
   *
   * @return explicit termination reason
   */
  public Reason getReason() {
    return reason;
  }

  /**
   * Returns the number of attempted trials.
   *
   * @return nonnegative trial count
   */
  public int getAttempts() {
    return attempts;
  }

  /**
   * Returns the final attempted interval size.
   *
   * @return attempted timestep in seconds
   */
  public double getAttemptedStepSeconds() {
    return attemptedStepSeconds;
  }

  /**
   * Returns the final numerical error or an explicit unavailable marker.
   *
   * @return nonnegative normalized error, positive infinity, or NaN when unavailable
   */
  public double getNormalizedError() {
    return normalizedError;
  }

  /**
   * Returns the limiting variable when a comparison was available.
   *
   * @return limiting state identity or null when unavailable
   */
  public String getLimitingVariable() {
    return limitingVariable;
  }
}
