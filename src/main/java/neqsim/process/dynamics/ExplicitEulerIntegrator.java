package neqsim.process.dynamics;

/**
 * Explicit Euler integrator: x_{n+1} = x_n + dt · f(t_n, x_n).
 *
 * <p>
 * First-order accurate, conditionally stable. Provided as a baseline reference and as a lightweight
 * option for non-stiff problems.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ExplicitEulerIntegrator implements IntegratorStrategy {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "Explicit Euler";
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
    return state + dt * slope.dxdt(time, state);
  }
}
