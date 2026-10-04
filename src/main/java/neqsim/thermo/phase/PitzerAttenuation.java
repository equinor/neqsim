package neqsim.thermo.phase;

/** Numerically stable attenuation functions shared by Pitzer activity and volume models. */
public final class PitzerAttenuation {
  private PitzerAttenuation() {
  }

  /**
   * Evaluates {@code g(x) = 2 * (1 - (1 + x) * exp(-x)) / x^2}.
   *
   * @param x finite, non-negative dimensionless argument
   * @return attenuation, including the analytical limit {@code g(0) = 1}
   */
  public static double value(double x) {
    requireArgument(x);
    if (x <= 0.5) {
      double term = 1.0;
      double sum = 1.0;
      for (int order = 1; order <= 18; order++) {
        term *= -x / order;
        sum += 2.0 * term / (order + 2.0);
      }
      return sum;
    }
    // Divide successively to avoid overflowing x*x for a large finite argument.
    return 2.0 * (-Math.expm1(-x) / x - Math.exp(-x)) / x;
  }

  /**
   * Evaluates {@code (x/2) * dg/dx}, the factor used in {@code dB/dI} for {@code x = alpha * sqrt(I)}. This is a scaled
   * derivative, not {@code dg/dx}; its limit at zero is zero.
   *
   * @param x finite, non-negative dimensionless argument
   * @return scaled derivative with cancellation-free small-argument evaluation
   */
  public static double scaledDerivative(double x) {
    requireArgument(x);
    if (x <= 0.5) {
      double term = 1.0;
      double sum = 0.0;
      for (int order = 1; order <= 18; order++) {
        term *= -x / order;
        sum += order * term / (order + 2.0);
      }
      return sum;
    }
    return Math.exp(-x) - value(x);
  }

  private static void requireArgument(double x) {
    if (!Double.isFinite(x) || x < 0.0) {
      throw new IllegalArgumentException("Pitzer attenuation argument must be finite and non-negative");
    }
  }
}
