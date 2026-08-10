package neqsim.process.dynamics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * BDF-1 (backward differentiation formula of order 1, i.e. implicit Euler) integrator:
 *
 * <pre>
 *   x_{n+1} = x_n + dt · f(t_{n+1}, x_{n+1})
 * </pre>
 *
 * <p>
 * Solved by Newton iteration. Implicit Euler is L-stable and well suited for stiff thermal, inventory, and
 * pressure-dynamics problems that arise in dynamic process simulation (vessel pressure decay, large-tank temperature
 * dynamics, slow level loops).
 * </p>
 *
 * <p>
 * The Jacobian ∂f/∂x is approximated by a central finite difference with step
 * {@code max(jacobianEps, jacobianEps · |x|)}. Newton stops when both the update and implicit-equation residual satisfy
 * the configured tolerance. Newton non-convergence is fail-loud by default; an explicit-Euler fallback is available
 * only through the deliberately opt-in {@link #setExplicitEulerFallbackEnabled(boolean)} compatibility switch. A
 * fallback is never reported as a converged BDF step.
 * </p>
 *
 * <p>
 * This class remains a scalar BDF-1 foundation. It is not a vector sparse BDF or DAE solver and must not be used as
 * evidence of plant-wide stiff/DAE qualification.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class BDFIntegrator implements IntegratorStrategy {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(BDFIntegrator.class);

  private double tolerance = 1.0e-8;
  private int maxIterations = 25;
  private double jacobianEps = 1.0e-6;
  private boolean explicitEulerFallbackEnabled = false;
  private boolean lastStepFellBack = false;
  private boolean lastStepConverged = true;
  private int lastNewtonIterations = 0;
  private double lastResidual = Double.NaN;

  /**
   * Default constructor; tolerance 1e-8, maxIterations 25, Jacobian epsilon 1e-6.
   */
  public BDFIntegrator() {
    // defaults
  }

  /**
   * Constructor with custom Newton settings.
   *
   * @param tolerance Newton tolerance on update and scaled residual (must be {@code > 0})
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
   * Indicates whether the most recent {@link #step} call used the explicitly enabled Euler compatibility fallback.
   *
   * <p>
   * A fallback means the requested BDF step did not converge. It is therefore not a successful implicit step and must
   * not be counted as stiff-integration qualification evidence.
   * </p>
   *
   * @return true when the last step used explicit Euler after BDF non-convergence
   */
  public boolean lastStepFellBack() {
    return lastStepFellBack;
  }

  /**
   * Indicates whether the most recent BDF Newton solve converged.
   *
   * @return true only when the implicit BDF equation converged
   */
  public boolean lastStepConverged() {
    return lastStepConverged;
  }

  /**
   * Returns the number of Newton iterations attempted by the most recent step.
   *
   * @return Newton iteration count
   */
  public int getLastNewtonIterations() {
    return lastNewtonIterations;
  }

  /**
   * Returns the absolute implicit-equation residual from the most recent Newton evaluation.
   *
   * @return absolute residual, or NaN before the first Newton evaluation
   */
  public double getLastResidual() {
    return lastResidual;
  }

  /**
   * Enables or disables the legacy explicit-Euler fallback after BDF Newton non-convergence.
   *
   * <p>
   * The default is {@code false}: non-convergence throws {@link IllegalStateException} so a physical-step coordinator
   * can reject and restore the step. Enabling this switch is an explicit compatibility choice; the returned Euler state
   * is marked by {@link #lastStepFellBack()} and {@link #lastStepConverged()} remains false.
   * </p>
   *
   * @param enabled true to return an explicit-Euler compatibility step after BDF failure
   */
  public void setExplicitEulerFallbackEnabled(boolean enabled) {
    explicitEulerFallbackEnabled = enabled;
  }

  /**
   * Returns whether the explicit-Euler compatibility fallback is enabled.
   *
   * @return true when fallback is enabled
   */
  public boolean isExplicitEulerFallbackEnabled() {
    return explicitEulerFallbackEnabled;
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
    if (!Double.isFinite(dt) || dt <= 0.0) {
      throw new IllegalArgumentException("dt must be finite and > 0, got " + dt);
    }
    lastStepFellBack = false;
    lastStepConverged = false;
    lastNewtonIterations = 0;
    lastResidual = Double.NaN;

    double tNext = time + dt;
    double x = state + dt * slope.dxdt(time, state);
    double residualScale = Math.max(1.0, Math.abs(state));

    // Solve G(x) = x - state - dt * f(tNext, x) = 0 via Newton.
    for (int it = 0; it < maxIterations; it++) {
      lastNewtonIterations = it + 1;
      double fAtX = slope.dxdt(tNext, x);
      double g = x - state - dt * fAtX;
      lastResidual = Math.abs(g);
      double h = Math.max(jacobianEps, jacobianEps * Math.abs(x));
      double fPlus = slope.dxdt(tNext, x + h);
      double fMinus = slope.dxdt(tNext, x - h);
      double dfdx = (fPlus - fMinus) / (2.0 * h);
      double dgdx = 1.0 - dt * dfdx;

      if (!Double.isFinite(g) || !Double.isFinite(dgdx) || Math.abs(dgdx) < 1.0e-14) {
        break;
      }

      double dx = -g / dgdx;
      if (!Double.isFinite(dx)) {
        break;
      }
      x += dx;
      if (!Double.isFinite(x)) {
        break;
      }

      double residualAfter = x - state - dt * slope.dxdt(tNext, x);
      lastResidual = Math.abs(residualAfter);
      if (Math.abs(dx) < tolerance && Double.isFinite(lastResidual) && lastResidual <= tolerance * residualScale) {
        lastStepConverged = true;
        return x;
      }
    }

    if (explicitEulerFallbackEnabled) {
      lastStepFellBack = true;
      logger.warn(
          "BDF-1 Newton did not converge at t={} s with dt={} s after {} iterations (|residual|={}); "
              + "using explicitly enabled Euler compatibility fallback",
          Double.valueOf(time), Double.valueOf(dt), Integer.valueOf(lastNewtonIterations),
          Double.valueOf(lastResidual));
      return state + dt * slope.dxdt(time, state);
    }

    throw new IllegalStateException("BDF-1 Newton did not converge at t=" + time + " s with dt=" + dt + " s after "
        + lastNewtonIterations + " iterations; |residual|=" + lastResidual
        + ". Reduce the timestep or enable the explicit-Euler compatibility fallback deliberately.");
  }
}
