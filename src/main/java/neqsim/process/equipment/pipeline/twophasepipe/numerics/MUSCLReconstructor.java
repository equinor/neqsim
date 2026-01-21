package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;

/**
 * MUSCL (Monotone Upstream-centered Schemes for Conservation Laws) reconstruction.
 *
 * <p>
 * Provides second-order spatial accuracy by reconstructing cell-interface values from cell-average
 * values using slope limiters to prevent spurious oscillations near discontinuities.
 * </p>
 *
 * <h2>Reconstruction Formula</h2>
 * <p>
 * For a cell i with value U_i:
 * </p>
 * <ul>
 * <li>Left interface value: U_{i+1/2,L} = U_i + 0.5 * φ(r) * (U_i - U_{i-1})</li>
 * <li>Right interface value: U_{i-1/2,R} = U_i - 0.5 * φ(r) * (U_{i+1} - U_i)</li>
 * </ul>
 * <p>
 * where φ(r) is the slope limiter and r is the ratio of consecutive gradients.
 * </p>
 *
 * <h2>Available Limiters</h2>
 * <ul>
 * <li><b>Minmod:</b> Most diffusive, very robust</li>
 * <li><b>Van Leer:</b> Good balance of accuracy and stability</li>
 * <li><b>Van Albada:</b> Smooth limiter, good for smooth solutions</li>
 * <li><b>Superbee:</b> Least diffusive, can be oscillatory</li>
 * <li><b>MC (Monotonized Central):</b> Between minmod and superbee</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ul>
 * <li>van Leer, B. (1979) - Towards the ultimate conservative difference scheme V.</li>
 * <li>Sweby, P.K. (1984) - High resolution schemes using flux limiters</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class MUSCLReconstructor implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * Available slope limiter types.
   */
  public enum SlopeLimiter {
    /** Minmod limiter - most diffusive, very robust. */
    MINMOD,
    /** Van Leer limiter - smooth, good balance. */
    VAN_LEER,
    /** Van Albada limiter - smooth, C1 continuous. */
    VAN_ALBADA,
    /** Superbee limiter - least diffusive. */
    SUPERBEE,
    /** Monotonized Central limiter. */
    MC,
    /** No limiter (first-order upwind). */
    NONE
  }

  /** Current limiter type. */
  private SlopeLimiter limiterType = SlopeLimiter.VAN_LEER;

  /** Small value to prevent division by zero. */
  private static final double EPSILON = 1e-12;

  /**
   * Reconstructed values at a cell interface.
   */
  public static class ReconstructedPair implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Left state at interface (from cell i). */
    public double left;

    /** Right state at interface (from cell i+1). */
    public double right;
  }

  /**
   * Default constructor using Van Leer limiter.
   */
  public MUSCLReconstructor() {}

  /**
   * Constructor with specified limiter.
   *
   * @param limiter Slope limiter type
   */
  public MUSCLReconstructor(SlopeLimiter limiter) {
    this.limiterType = limiter;
  }

  /**
   * Reconstruct interface values between cells i and i+1.
   *
   * @param uLeft Value at cell i-1 (or i for boundary)
   * @param uCenter Value at cell i
   * @param uRight Value at cell i+1
   * @param uFarRight Value at cell i+2 (or i+1 for boundary)
   * @return Reconstructed left and right states at interface i+1/2
   */
  public ReconstructedPair reconstruct(double uLeft, double uCenter, double uRight,
      double uFarRight) {
    ReconstructedPair result = new ReconstructedPair();

    // Calculate slopes
    double deltaLeft = uCenter - uLeft;
    double deltaCenter = uRight - uCenter;
    double deltaRight = uFarRight - uRight;

    // Left state (extrapolated from cell i towards right interface)
    double slopeL = calcLimitedSlope(deltaLeft, deltaCenter);
    result.left = uCenter + 0.5 * slopeL;

    // Right state (extrapolated from cell i+1 towards left interface)
    double slopeR = calcLimitedSlope(deltaCenter, deltaRight);
    result.right = uRight - 0.5 * slopeR;

    return result;
  }

  /**
   * Reconstruct interface values for an array of cell values.
   *
   * <p>
   * Returns arrays of left and right states at each interface.
   * </p>
   *
   * @param u Cell-average values [0..n-1]
   * @return Array of reconstructed pairs at interfaces [0..n-2]
   */
  public ReconstructedPair[] reconstructArray(double[] u) {
    int n = u.length;
    if (n < 2) {
      return new ReconstructedPair[0];
    }

    ReconstructedPair[] result = new ReconstructedPair[n - 1];

    for (int i = 0; i < n - 1; i++) {
      // Handle boundary cells with reduced stencil
      double uLeft = i > 0 ? u[i - 1] : u[i];
      double uCenter = u[i];
      double uRight = u[i + 1];
      double uFarRight = i < n - 2 ? u[i + 2] : u[i + 1];

      result[i] = reconstruct(uLeft, uCenter, uRight, uFarRight);
    }

    return result;
  }

  /**
   * Calculate limited slope using current limiter.
   *
   * @param deltaLeft Left difference (U_i - U_{i-1})
   * @param deltaRight Right difference (U_{i+1} - U_i)
   * @return Limited slope
   */
  public double calcLimitedSlope(double deltaLeft, double deltaRight) {
    if (limiterType == SlopeLimiter.NONE) {
      return 0; // First-order (no reconstruction)
    }

    // Calculate gradient ratio
    double r;
    if (Math.abs(deltaRight) < EPSILON) {
      if (Math.abs(deltaLeft) < EPSILON) {
        return 0; // Both gradients zero
      }
      r = deltaLeft > 0 ? 1e10 : -1e10; // Large ratio
    } else {
      r = deltaLeft / deltaRight;
    }

    // Apply limiter
    double phi = calcLimiter(r);

    // Limited slope
    return phi * deltaRight;
  }

  /**
   * Calculate slope limiter value φ(r).
   *
   * @param r Gradient ratio
   * @return Limiter value φ
   */
  public double calcLimiter(double r) {
    switch (limiterType) {
      case MINMOD:
        return minmod(r);
      case VAN_LEER:
        return vanLeer(r);
      case VAN_ALBADA:
        return vanAlbada(r);
      case SUPERBEE:
        return superbee(r);
      case MC:
        return mc(r);
      case NONE:
      default:
        return 0;
    }
  }

  /**
   * Minmod limiter: φ(r) = max(0, min(1, r)).
   *
   * <p>
   * Most diffusive limiter, very robust for shocks.
   * </p>
   *
   * @param r Gradient ratio
   * @return Limiter value
   */
  public double minmod(double r) {
    if (r <= 0) {
      return 0;
    }
    return Math.min(1.0, r);
  }

  /**
   * Van Leer limiter: φ(r) = (r + |r|) / (1 + |r|).
   *
   * <p>
   * Smooth limiter with good balance of accuracy and stability.
   * </p>
   *
   * @param r Gradient ratio
   * @return Limiter value
   */
  public double vanLeer(double r) {
    if (r <= 0) {
      return 0;
    }
    return (r + Math.abs(r)) / (1.0 + Math.abs(r));
  }

  /**
   * Van Albada limiter: φ(r) = (r² + r) / (r² + 1).
   *
   * <p>
   * C1 continuous, good for smooth solutions.
   * </p>
   *
   * @param r Gradient ratio
   * @return Limiter value
   */
  public double vanAlbada(double r) {
    if (r <= 0) {
      return 0;
    }
    return (r * r + r) / (r * r + 1.0);
  }

  /**
   * Superbee limiter: φ(r) = max(0, min(2r, 1), min(r, 2)).
   *
   * <p>
   * Least diffusive, compresses discontinuities but can overshoot.
   * </p>
   *
   * @param r Gradient ratio
   * @return Limiter value
   */
  public double superbee(double r) {
    if (r <= 0) {
      return 0;
    }
    double a = Math.min(2.0 * r, 1.0);
    double b = Math.min(r, 2.0);
    return Math.max(Math.max(0, a), b);
  }

  /**
   * MC (Monotonized Central) limiter: φ(r) = max(0, min(2, 2r, (1+r)/2)).
   *
   * <p>
   * Between minmod and superbee in diffusivity.
   * </p>
   *
   * @param r Gradient ratio
   * @return Limiter value
   */
  public double mc(double r) {
    if (r <= 0) {
      return 0;
    }
    double c = (1.0 + r) / 2.0;
    return Math.max(0, Math.min(Math.min(2.0, 2.0 * r), c));
  }

  /**
   * Classic minmod function for three arguments.
   *
   * @param a First value
   * @param b Second value
   * @param c Third value
   * @return Minmod result
   */
  public double minmod3(double a, double b, double c) {
    if (a > 0 && b > 0 && c > 0) {
      return Math.min(Math.min(a, b), c);
    } else if (a < 0 && b < 0 && c < 0) {
      return Math.max(Math.max(a, b), c);
    } else {
      return 0;
    }
  }

  /**
   * Get current limiter type.
   *
   * @return Current slope limiter
   */
  public SlopeLimiter getLimiterType() {
    return limiterType;
  }

  /**
   * Set limiter type.
   *
   * @param limiterType New slope limiter
   */
  public void setLimiterType(SlopeLimiter limiterType) {
    this.limiterType = limiterType;
  }

  /**
   * Check if reconstruction is second-order.
   *
   * @return true if using a limiter (second-order)
   */
  public boolean isSecondOrder() {
    return limiterType != SlopeLimiter.NONE;
  }
}
