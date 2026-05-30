package neqsim.process.dynamics;

/**
 * BDF-1 (backward differentiation formula of order 1, i.e. implicit Euler) integrator:
 *
 * <pre>
 *   x_{n+1} = x_n + dt · f(t_{n+1}, x_{n+1})
 * </pre>
 *
 * <p>
 * Solved by Newton iteration. Implicit Euler is L-stable and well suited for stiff thermal,
 * inventory, and pressure-dynamics problems that arise in dynamic process simulation (vessel
 * pressure decay, large-tank temperature dynamics, slow level loops).
 * </p>
 *
 * <p>
 * The Jacobian ∂f/∂x is approximated by a central finite difference with step
 * {@code max(jacobianEps, jacobianEps · |x|)}. Newton stops when {@code |Δx| < tolerance} or after
 * {@code maxIterations}, whichever comes first; if Newton fails to converge the integrator falls
 * back to a single explicit-Euler step so that the simulation remains stable rather than throwing.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class BDFIntegrator implements IntegratorStrategy {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private double tolerance = 1.0e-8;
  private int maxIterations = 25;
  private double jacobianEps = 1.0e-6;
  private boolean lastStepFellBack = false;

  /**
   * Default constructor; tolerance 1e-8, maxIterations 25, Jacobian epsilon 1e-6.
   */
  public BDFIntegrator() {
    // defaults
  }

  /**
   * Constructor with custom Newton settings.
   *
   * @param tolerance Newton tolerance on |Δx| (must be {@code > 0})
   * @param maxIterations maximum Newton iterations (must be {@code >= 1})
   * @param jacobianEps finite-difference perturbation (must be {@code > 0})
   */
  public BDFIntegrator(double tolerance, int maxIterations, double jacobianEps) {
    if (!(tolerance > 0.0)) {
      throw new IllegalArgumentException("tolerance must be > 0");
    }
    if (maxIterations < 1) {
      throw new IllegalArgumentException("maxIterations must be >= 1");
    }
    if (!(jacobianEps > 0.0)) {
      throw new IllegalArgumentException("jacobianEps must be > 0");
    }
    this.tolerance = tolerance;
    this.maxIterations = maxIterations;
    this.jacobianEps = jacobianEps;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "BDF-1 (Implicit Euler)";
  }

  /**
   * Indicates whether the most recent {@link #step} call had to fall back to explicit Euler because
   * Newton iteration did not converge.
   *
   * @return true when last step fell back
   */
  public boolean lastStepFellBack() {
    return lastStepFellBack;
  }

  /**
   * Returns the configured Newton tolerance.
   *
   * @return tolerance
   */
  public double getTolerance() {
    return tolerance;
  }

  /**
   * Returns the configured max Newton iterations.
   *
   * @return iteration cap
   */
  public int getMaxIterations() {
    return maxIterations;
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
    lastStepFellBack = false;
    double tNext = time + dt;
    // Initial guess: explicit Euler predictor
    double x = state + dt * slope.dxdt(time, state);
    // Solve G(x) = x - state - dt * f(tNext, x) = 0 via Newton
    for (int it = 0; it < maxIterations; it++) {
      double fAtX = slope.dxdt(tNext, x);
      double g = x - state - dt * fAtX;
      double h = Math.max(jacobianEps, jacobianEps * Math.abs(x));
      double dfdx = (slope.dxdt(tNext, x + h) - slope.dxdt(tNext, x - h)) / (2.0 * h);
      double dgdx = 1.0 - dt * dfdx;
      if (Math.abs(dgdx) < 1.0e-14) {
        break;
      }
      double dx = -g / dgdx;
      x += dx;
      if (Math.abs(dx) < tolerance) {
        return x;
      }
    }
    // Fall back to explicit Euler for this step
    lastStepFellBack = true;
    return state + dt * slope.dxdt(time, state);
  }
}
