package neqsim.process.dynamics;

/**
 * Classical fourth-order Runge–Kutta integrator (RK4):
 *
 * <pre>
 *   k1 = f(t,        x)
 *   k2 = f(t + dt/2, x + dt·k1/2)
 *   k3 = f(t + dt/2, x + dt·k2/2)
 *   k4 = f(t + dt,   x + dt·k3)
 *   x_{n+1} = x_n + dt · (k1 + 2·k2 + 2·k3 + k4) / 6
 * </pre>
 *
 * <p>
 * Fourth-order accurate (local error {@code O(dt^5)}, global {@code O(dt^4)}); explicit and
 * therefore conditionally stable, but much more accurate per step than {@link ExplicitEulerIntegrator}
 * for non-stiff problems. Use this as the default for smooth, well-conditioned ODE-like dynamics
 * (pressure relaxation, level dynamics, smooth controller responses). For stiff problems prefer
 * {@link BDFIntegrator}; for problems where step size needs to adapt to local error, prefer
 * {@link AdaptiveRK45Integrator}.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class RK4Integrator implements IntegratorStrategy {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "RK4 (classical Runge-Kutta 4)";
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
    double k1 = slope.dxdt(time, state);
    double k2 = slope.dxdt(time + 0.5 * dt, state + 0.5 * dt * k1);
    double k3 = slope.dxdt(time + 0.5 * dt, state + 0.5 * dt * k2);
    double k4 = slope.dxdt(time + dt, state + dt * k3);
    return state + dt * (k1 + 2.0 * k2 + 2.0 * k3 + k4) / 6.0;
  }
}
