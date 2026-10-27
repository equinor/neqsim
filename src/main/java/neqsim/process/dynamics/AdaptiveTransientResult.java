package neqsim.process.dynamics;

import java.io.Serializable;
import java.util.UUID;

/**
 * Immutable acceptance and provenance evidence from one bounded adaptive physical step.
 *
 * <p>
 * The accepted state is the second half-step state. Its two physical child identifiers remain distinct from the
 * caller's parent identity and from diagnostic evaluation identifiers.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class AdaptiveTransientResult implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final UUID physicalStepIdentifier;
  private final double acceptedStepSeconds;
  private final double suggestedNextStepSeconds;
  private final double normalizedError;
  private final String limitingVariable;
  private final int attempts;
  private final int rejectedAttempts;
  private final UUID firstHalfStepIdentifier;
  private final UUID secondHalfStepIdentifier;

  /**
   * Creates acceptance evidence.
   *
   * @param physicalStepIdentifier caller's physical-step provenance
   * @param acceptedStepSeconds finite positive elapsed step in seconds
   * @param suggestedNextStepSeconds finite positive next-step recommendation in seconds
   * @param normalizedError finite accepted normalized error between zero and one
   * @param limitingVariable identity of the variable with the maximum normalized error
   * @param attempts total number of trial attempts
   * @param rejectedAttempts number of rejected attempts before acceptance
   * @param firstHalfStepIdentifier physical identity of the accepted first half step
   * @param secondHalfStepIdentifier physical identity of the accepted second half step
   * @throws IllegalArgumentException if acceptance evidence is inconsistent or invalid
   */
  public AdaptiveTransientResult(UUID physicalStepIdentifier, double acceptedStepSeconds,
      double suggestedNextStepSeconds, double normalizedError, String limitingVariable, int attempts,
      int rejectedAttempts, UUID firstHalfStepIdentifier, UUID secondHalfStepIdentifier) {
    if (physicalStepIdentifier == null || firstHalfStepIdentifier == null || secondHalfStepIdentifier == null
        || firstHalfStepIdentifier.equals(secondHalfStepIdentifier)
        || physicalStepIdentifier.equals(firstHalfStepIdentifier)
        || physicalStepIdentifier.equals(secondHalfStepIdentifier) || !Double.isFinite(acceptedStepSeconds)
        || acceptedStepSeconds <= 0.0 || !Double.isFinite(suggestedNextStepSeconds) || suggestedNextStepSeconds <= 0.0
        || !Double.isFinite(normalizedError) || normalizedError < 0.0 || normalizedError > 1.0
        || limitingVariable == null || limitingVariable.trim().isEmpty() || attempts < 1
        || rejectedAttempts != attempts - 1) {
      throw new IllegalArgumentException("Invalid adaptive transient acceptance evidence");
    }
    this.physicalStepIdentifier = physicalStepIdentifier;
    this.acceptedStepSeconds = acceptedStepSeconds;
    this.suggestedNextStepSeconds = suggestedNextStepSeconds;
    this.normalizedError = normalizedError;
    this.limitingVariable = limitingVariable;
    this.attempts = attempts;
    this.rejectedAttempts = rejectedAttempts;
    this.firstHalfStepIdentifier = firstHalfStepIdentifier;
    this.secondHalfStepIdentifier = secondHalfStepIdentifier;
  }

  /**
   * Returns the caller's parent physical-step identity.
   *
   * @return accepted parent identity
   */
  public UUID getPhysicalStepIdentifier() {
    return physicalStepIdentifier;
  }

  /**
   * Returns the elapsed accepted step size.
   *
   * @return accepted step in seconds
   */
  public double getAcceptedStepSeconds() {
    return acceptedStepSeconds;
  }

  /**
   * Returns the bounded next-step recommendation.
   *
   * @return suggested next step in seconds
   */
  public double getSuggestedNextStepSeconds() {
    return suggestedNextStepSeconds;
  }

  /**
   * Returns the maximum normalized full-versus-half state difference.
   *
   * @return dimensionless normalized error
   */
  public double getNormalizedError() {
    return normalizedError;
  }

  /**
   * Returns the deterministically selected limiting variable.
   *
   * @return limiting state identity
   */
  public String getLimitingVariable() {
    return limitingVariable;
  }

  /**
   * Returns the number of trial attempts.
   *
   * @return total attempt count
   */
  public int getAttempts() {
    return attempts;
  }

  /**
   * Returns the number of rejected trials.
   *
   * @return rejection count
   */
  public int getRejectedAttempts() {
    return rejectedAttempts;
  }

  /**
   * Returns the accepted first half step's physical identity.
   *
   * @return first half-step identity
   */
  public UUID getFirstHalfStepIdentifier() {
    return firstHalfStepIdentifier;
  }

  /**
   * Returns the accepted second half step's physical identity.
   *
   * @return second half-step identity
   */
  public UUID getSecondHalfStepIdentifier() {
    return secondHalfStepIdentifier;
  }
}
