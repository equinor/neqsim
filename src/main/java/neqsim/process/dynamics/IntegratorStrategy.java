package neqsim.process.dynamics;

import java.io.Serializable;

/**
 * Pluggable strategy interface for dynamic integration of a scalar (or vector) ODE-like state in
 * NeqSim dynamic simulations.
 *
 * <p>
 * The contract is intentionally minimal: given a current state, a slope (dx/dt), and a step size,
 * the integrator returns the next-step state. Implementations may be explicit (Euler, RK4) or
 * implicit (BDF). For implicit methods, callers supply a slope function so the integrator can
 * re-evaluate dx/dt at the implicit point.
 * </p>
 *
 * <p>
 * Implementations are expected to be stateless across calls (no hidden history); BDF-N variants
 * that need previous states should accept them via the {@link #step} overload that takes a
 * {@link IntegrationContext}.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public interface IntegratorStrategy extends Serializable {

  /**
   * Slope function dx/dt = f(t, x).
   */
  interface Slope extends Serializable {
    /**
     * Evaluates the slope at (t, x).
     *
     * @param time current time in seconds
     * @param state current state value
     * @return dx/dt at (time, state)
     */
    double dxdt(double time, double state);
  }

  /**
   * Returns a short human-readable name (e.g. "Explicit Euler", "BDF-1").
   *
   * @return integrator name
   */
  String getName();

  /**
   * Advances the state from {@code x} at time {@code t} by step size {@code dt}.
   *
   * @param time current simulation time in seconds
   * @param state current state value
   * @param slope slope function
   * @param dt step size in seconds (must be {@code > 0})
   * @return state at time {@code t + dt}
   */
  double step(double time, double state, Slope slope, double dt);
}
