package neqsim.process.dynamics;

/**
 * Adaptive Runge–Kutta–Fehlberg (Cash–Karp) integrator producing a 5th-order solution with an
 * embedded 4th-order error estimate. Step size is internally subdivided until the estimated local
 * error meets the configured tolerance, then the high-order solution is returned.
 *
 * <p>
 * The integrator preserves the {@link IntegratorStrategy} contract: callers ask for a single step
 * of size {@code dt} and get back the next state. Internally the requested {@code dt} may be split
 * into one or more sub-steps to keep the per-step error below
 * {@code absTol + relTol · max(|state|, |next|)}. The number of sub-steps actually taken on the
 * most recent call is exposed via {@link #getLastSubSteps()}, which is useful for diagnostics and
 * for choosing better outer step sizes.
 * </p>
 *
 * <p>
 * This integrator is well suited for stiff-but-not-fully-stiff dynamic studies where local
 * behaviour varies sharply (compressor surge transient, fast valve closure, depressurization with
 * choked-to-subsonic transition). For deeply stiff thermal/inventory dynamics prefer
 * {@link BDFIntegrator}.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AdaptiveRK45Integrator implements IntegratorStrategy {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // Cash-Karp coefficients
  private static final double A2 = 1.0 / 5.0;
  private static final double A3 = 3.0 / 10.0;
  private static final double A4 = 3.0 / 5.0;
  private static final double A5 = 1.0;
  private static final double A6 = 7.0 / 8.0;

  private static final double B21 = 1.0 / 5.0;
  private static final double B31 = 3.0 / 40.0;
  private static final double B32 = 9.0 / 40.0;
  private static final double B41 = 3.0 / 10.0;
  private static final double B42 = -9.0 / 10.0;
  private static final double B43 = 6.0 / 5.0;
  private static final double B51 = -11.0 / 54.0;
  private static final double B52 = 5.0 / 2.0;
  private static final double B53 = -70.0 / 27.0;
  private static final double B54 = 35.0 / 27.0;
  private static final double B61 = 1631.0 / 55296.0;
  private static final double B62 = 175.0 / 512.0;
  private static final double B63 = 575.0 / 13824.0;
  private static final double B64 = 44275.0 / 110592.0;
  private static final double B65 = 253.0 / 4096.0;

  // 5th-order solution weights
  private static final double C1 = 37.0 / 378.0;
  private static final double C3 = 250.0 / 621.0;
  private static final double C4 = 125.0 / 594.0;
  private static final double C6 = 512.0 / 1771.0;

  // Embedded 4th-order solution weights
  private static final double DC1 = C1 - 2825.0 / 27648.0;
  private static final double DC3 = C3 - 18575.0 / 48384.0;
  private static final double DC4 = C4 - 13525.0 / 55296.0;
  private static final double DC5 = -277.0 / 14336.0;
  private static final double DC6 = C6 - 1.0 / 4.0;

  private double absTol = 1.0e-6;
  private double relTol = 1.0e-4;
  private int maxSubSteps = 1000;
  private double safety = 0.9;
  private double minScale = 0.1;
  private double maxScale = 5.0;
  private int lastSubSteps = 0;

  /** Default constructor; absTol 1e-6, relTol 1e-4, maxSubSteps 1000. */
  public AdaptiveRK45Integrator() {
    // defaults
  }

  /**
   * Constructor with custom tolerances.
   *
   * @param absTol absolute tolerance on local error (must be {@code > 0})
   * @param relTol relative tolerance on local error (must be {@code > 0})
   * @param maxSubSteps maximum number of internal sub-steps per outer step (must be {@code >= 1})
   */
  public AdaptiveRK45Integrator(double absTol, double relTol, int maxSubSteps) {
    if (!(absTol > 0.0)) {
      throw new IllegalArgumentException("absTol must be > 0");
    }
    if (!(relTol > 0.0)) {
      throw new IllegalArgumentException("relTol must be > 0");
    }
    if (maxSubSteps < 1) {
      throw new IllegalArgumentException("maxSubSteps must be >= 1");
    }
    this.absTol = absTol;
    this.relTol = relTol;
    this.maxSubSteps = maxSubSteps;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "Adaptive RK45 (Cash-Karp)";
  }

  /**
   * Returns the absolute tolerance.
   *
   * @return absTol
   */
  public double getAbsTol() {
    return absTol;
  }

  /**
   * Returns the relative tolerance.
   *
   * @return relTol
   */
  public double getRelTol() {
    return relTol;
  }

  /**
   * Returns the number of internal sub-steps consumed by the most recent {@link #step} call.
   *
   * @return sub-step count
   */
  public int getLastSubSteps() {
    return lastSubSteps;
  }

  /** {@inheritDoc} */
  @Override
  public double step(double time, double state, Slope slope, double dt) {
    if (slope == null) {
      throw new IllegalArgumentException("slope must not be null");
    }
    if (!(dt > 0.0)) {
      throw new IllegalArgumentException("dt must be > 0, got " + dt);
    }
    lastSubSteps = 0;
    double t = time;
    double x = state;
    double tEnd = time + dt;
    double h = dt;
    for (int iter = 0; iter < maxSubSteps; iter++) {
      if (t >= tEnd) {
        return x;
      }
      if (t + h > tEnd) {
        h = tEnd - t;
      }
      double k1 = slope.dxdt(t, x);
      double k2 = slope.dxdt(t + A2 * h, x + h * (B21 * k1));
      double k3 = slope.dxdt(t + A3 * h, x + h * (B31 * k1 + B32 * k2));
      double k4 = slope.dxdt(t + A4 * h, x + h * (B41 * k1 + B42 * k2 + B43 * k3));
      double k5 = slope.dxdt(t + A5 * h, x + h * (B51 * k1 + B52 * k2 + B53 * k3 + B54 * k4));
      double k6 =
          slope.dxdt(t + A6 * h, x + h * (B61 * k1 + B62 * k2 + B63 * k3 + B64 * k4 + B65 * k5));

      double xNew = x + h * (C1 * k1 + C3 * k3 + C4 * k4 + C6 * k6);
      double errEst =
          h * (DC1 * k1 + DC3 * k3 + DC4 * k4 + DC5 * k5 + DC6 * k6);
      double scale = absTol + relTol * Math.max(Math.abs(x), Math.abs(xNew));
      double errNorm = Math.abs(errEst) / scale;

      if (errNorm <= 1.0 || h <= 1.0e-12 * dt) {
        // accept
        t += h;
        x = xNew;
        lastSubSteps++;
        double factor = (errNorm <= 1.0e-12) ? maxScale
            : Math.min(maxScale, safety * Math.pow(errNorm, -0.2));
        h *= factor;
      } else {
        // reject, shrink
        double factor = Math.max(minScale, safety * Math.pow(errNorm, -0.25));
        h *= factor;
      }
    }
    throw new RuntimeException("AdaptiveRK45Integrator exceeded maxSubSteps=" + maxSubSteps
        + " for outer dt=" + dt + " starting at t=" + time);
  }
}
