package neqsim.fluidmechanics.flowsolver;

/**
 * Flux limiter functions for TVD (Total Variation Diminishing) advection schemes.
 *
 * <p>
 * Flux limiters prevent oscillations in higher-order schemes by reducing the scheme to first-order
 * near discontinuities while maintaining second-order accuracy in smooth regions.
 * </p>
 *
 * <p>
 * The limiter function φ(r) where r is the ratio of consecutive gradients: r = (φ_i - φ_{i-1}) /
 * (φ_{i+1} - φ_i)
 * </p>
 *
 * <p>
 * For TVD schemes, the limiter must satisfy: 0 ≤ φ(r) ≤ min(2r, 2) for r > 0
 * </p>
 *
 * @author ESOL
 */
public final class FluxLimiter {

  /** Private constructor to prevent instantiation. */
  private FluxLimiter() {}

  /**
   * Calculate the gradient ratio r for TVD schemes.
   *
   * <p>
   * r = (φ_i - φ_{i-1}) / (φ_{i+1} - φ_i) for positive flow
   * </p>
   *
   * @param phiUpstream value at upstream node (i-1)
   * @param phiCenter value at center node (i)
   * @param phiDownstream value at downstream node (i+1)
   * @return gradient ratio r
   */
  public static double gradientRatio(double phiUpstream, double phiCenter, double phiDownstream) {
    double denominator = phiDownstream - phiCenter;
    double numerator = phiCenter - phiUpstream;

    // Avoid division by zero
    if (Math.abs(denominator) < 1e-15) {
      if (Math.abs(numerator) < 1e-15) {
        return 1.0; // Uniform field
      }
      return numerator > 0 ? 1e10 : -1e10;
    }

    return numerator / denominator;
  }

  /**
   * Minmod flux limiter.
   *
   * <p>
   * φ(r) = max(0, min(r, 1))
   * </p>
   *
   * <p>
   * Most diffusive symmetric TVD limiter. Very stable but may be too diffusive.
   * </p>
   *
   * @param r gradient ratio
   * @return limiter value in [0, 1]
   */
  public static double minmod(double r) {
    if (r <= 0) {
      return 0.0;
    }
    return Math.min(r, 1.0);
  }

  /**
   * Van Leer flux limiter.
   *
   * <p>
   * φ(r) = (r + |r|) / (1 + |r|)
   * </p>
   *
   * <p>
   * Smooth limiter, good balance between accuracy and robustness.
   * </p>
   *
   * @param r gradient ratio
   * @return limiter value
   */
  public static double vanLeer(double r) {
    if (r <= 0) {
      return 0.0;
    }
    return (r + Math.abs(r)) / (1.0 + Math.abs(r));
  }

  /**
   * Superbee flux limiter.
   *
   * <p>
   * φ(r) = max(0, min(2r, 1), min(r, 2))
   * </p>
   *
   * <p>
   * Least diffusive symmetric TVD limiter. Best for sharp fronts but may be too compressive for
   * smooth solutions.
   * </p>
   *
   * @param r gradient ratio
   * @return limiter value
   */
  public static double superbee(double r) {
    if (r <= 0) {
      return 0.0;
    }
    return Math.max(Math.min(2.0 * r, 1.0), Math.min(r, 2.0));
  }

  /**
   * Van Albada flux limiter.
   *
   * <p>
   * φ(r) = (r² + r) / (r² + 1)
   * </p>
   *
   * <p>
   * Smooth, differentiable limiter. Good for optimization and adjoint methods.
   * </p>
   *
   * @param r gradient ratio
   * @return limiter value
   */
  public static double vanAlbada(double r) {
    if (r <= 0) {
      return 0.0;
    }
    return (r * r + r) / (r * r + 1.0);
  }

  /**
   * MC (Monotonized Central) flux limiter.
   *
   * <p>
   * φ(r) = max(0, min(2r, (1+r)/2, 2))
   * </p>
   *
   * <p>
   * A commonly used limiter that is less diffusive than Minmod but more than Superbee.
   * </p>
   *
   * @param r gradient ratio
   * @return limiter value
   */
  public static double mc(double r) {
    if (r <= 0) {
      return 0.0;
    }
    return Math.max(0, Math.min(Math.min(2.0 * r, (1.0 + r) / 2.0), 2.0));
  }

  /**
   * Get the flux limiter value for a given advection scheme.
   *
   * @param scheme the advection scheme
   * @param r gradient ratio
   * @return limiter value
   */
  public static double getLimiterValue(AdvectionScheme scheme, double r) {
    switch (scheme) {
      case TVD_MINMOD:
        return minmod(r);
      case TVD_VAN_LEER:
      case MUSCL_VAN_LEER:
        return vanLeer(r);
      case TVD_SUPERBEE:
        return superbee(r);
      case TVD_VAN_ALBADA:
        return vanAlbada(r);
      default:
        return 0.0; // No limiting for non-TVD schemes
    }
  }

  /**
   * Minmod function for two arguments.
   *
   * <p>
   * Returns the value with smallest magnitude if both have the same sign, zero otherwise.
   * </p>
   *
   * @param a first value
   * @param b second value
   * @return minmod(a, b)
   */
  public static double minmod2(double a, double b) {
    if (a * b <= 0) {
      return 0.0;
    }
    if (Math.abs(a) < Math.abs(b)) {
      return a;
    }
    return b;
  }

  /**
   * Minmod function for three arguments.
   *
   * @param a first value
   * @param b second value
   * @param c third value
   * @return minmod(a, b, c)
   */
  public static double minmod3(double a, double b, double c) {
    return minmod2(a, minmod2(b, c));
  }

  /**
   * Sign function.
   *
   * @param x value
   * @return -1, 0, or 1
   */
  public static double sign(double x) {
    if (x > 0) {
      return 1.0;
    }
    if (x < 0) {
      return -1.0;
    }
    return 0.0;
  }
}
