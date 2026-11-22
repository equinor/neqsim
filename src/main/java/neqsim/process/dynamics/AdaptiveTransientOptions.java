package neqsim.process.dynamics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Explicit numerical policy for one bounded adaptive transient step.
 *
 * <p>
 * All state tolerances belong to the supplied providers; this class creates no implicit error scales. Method order is
 * the declared positive convergence order of the underlying stepping method. The engine accepts the two-half-step state
 * and uses the unextrapolated full-versus-half difference.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class AdaptiveTransientOptions implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final double minimumStepSeconds;
  private final double maximumStepSeconds;
  private final int maximumAttempts;
  private final int methodOrder;
  private final List<TransientErrorStateBinding> bindings;

  /**
   * Creates a complete explicit step policy.
   *
   * @param minimumStepSeconds finite, strictly positive lower bound for accepted step size
   * @param maximumStepSeconds finite upper bound not below the lower bound
   * @param maximumAttempts positive maximum number of trial attempts
   * @param methodOrder declared integration order, from one through eight
   * @param bindings nonempty numerical provider bindings, copied defensively
   * @throws IllegalArgumentException if the step policy or bindings are invalid
   */
  public AdaptiveTransientOptions(double minimumStepSeconds, double maximumStepSeconds, int maximumAttempts,
      int methodOrder, List<TransientErrorStateBinding> bindings) {
    if (!Double.isFinite(minimumStepSeconds) || minimumStepSeconds <= 0.0 || !Double.isFinite(maximumStepSeconds)
        || maximumStepSeconds < minimumStepSeconds || maximumAttempts < 1 || methodOrder < 1 || methodOrder > 8) {
      throw new IllegalArgumentException("Invalid adaptive step bounds, attempt limit or method order");
    }
    if (bindings == null || bindings.isEmpty()) {
      throw new IllegalArgumentException("Numerical state bindings must not be null or empty");
    }
    ArrayList<TransientErrorStateBinding> copy = new ArrayList<TransientErrorStateBinding>(bindings);
    if (copy.contains(null)) {
      throw new IllegalArgumentException("Numerical state bindings must not contain null");
    }
    this.minimumStepSeconds = minimumStepSeconds;
    this.maximumStepSeconds = maximumStepSeconds;
    this.maximumAttempts = maximumAttempts;
    this.methodOrder = methodOrder;
    this.bindings = Collections.unmodifiableList(copy);
  }

  /**
   * Returns the minimum allowed accepted step size.
   *
   * @return lower timestep bound in seconds
   */
  public double getMinimumStepSeconds() {
    return minimumStepSeconds;
  }

  /**
   * Returns the maximum allowed accepted step size.
   *
   * @return upper timestep bound in seconds
   */
  public double getMaximumStepSeconds() {
    return maximumStepSeconds;
  }

  /**
   * Returns the bounded trial count.
   *
   * @return maximum number of attempts
   */
  public int getMaximumAttempts() {
    return maximumAttempts;
  }

  /**
   * Returns the declared order of the underlying integration method.
   *
   * @return integration order
   */
  public int getMethodOrder() {
    return methodOrder;
  }

  /**
   * Returns the immutable provider-binding list.
   *
   * @return numerical error-state bindings
   */
  public List<TransientErrorStateBinding> getBindings() {
    return bindings;
  }
}
