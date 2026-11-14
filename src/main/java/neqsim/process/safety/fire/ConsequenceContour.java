package neqsim.process.safety.fire;

import java.util.function.DoubleUnaryOperator;

/**
 * Bounded contour search for the fire and explosion consequence models.
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class ConsequenceContour {
  /**
   * Prevent instantiation of the contour search.
   */
  private ConsequenceContour() {
  }

  /**
   * Find the outer crossing of a target with a non-increasing consequence curve.
   *
   * @param consequenceAtDistance consequence curve as a function of distance in m
   * @param target positive finite consequence threshold
   * @param nearM lower search distance in m
   * @param farM upper search distance in m
   * @return outer contour distance in m, or {@code Double.NaN} for an unbracketed target
   * @throws IllegalArgumentException if the target is non-positive or non-finite
   */
  static double findDistance(DoubleUnaryOperator consequenceAtDistance, double target, double nearM, double farM) {
    if (target <= 0.0 || !Double.isFinite(target)) {
      throw new IllegalArgumentException("Contour target must be positive and finite");
    }
    if (nearM > farM) {
      return Double.NaN;
    }
    double nearValue = consequenceAtDistance.applyAsDouble(nearM);
    double farValue = consequenceAtDistance.applyAsDouble(farM);
    if (!Double.isFinite(nearValue) || !Double.isFinite(farValue) || target > nearValue || target < farValue) {
      return Double.NaN;
    }
    for (int i = 0; i < 60; i++) {
      double mid = 0.5 * (nearM + farM);
      double value = consequenceAtDistance.applyAsDouble(mid);
      // Equality advances to the outer edge of a flat segment, such as the VCE near-field plateau.
      if (value >= target) {
        nearM = mid;
      } else {
        farM = mid;
      }
    }
    return 0.5 * (nearM + farM);
  }
}
