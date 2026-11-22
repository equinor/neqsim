package neqsim.process.dynamics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Immutable, identity-sorted state vector for a deterministic maximum normalized trial error.
 *
 * <p>
 * Comparison requires exactly matching owner/variable identities, unit labels and tolerances. Values are not converted
 * between units. Arithmetic overflow yields positive infinity so a trial is rejected rather than accidentally accepted
 * with a NaN or a zero error.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TransientErrorStateVector implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final List<TransientErrorStateVariable> variables;

  /**
   * Captures and orders a nonempty numerical state vector.
   *
   * @param variables immutable scalar states, copied and sorted by owner and variable identities
   * @throws IllegalArgumentException if the list is empty, contains null or has duplicate identities
   */
  public TransientErrorStateVector(List<TransientErrorStateVariable> variables) {
    if (variables == null || variables.isEmpty()) {
      throw new IllegalArgumentException("Numerical error-state vector must not be null or empty");
    }
    ArrayList<TransientErrorStateVariable> copy = new ArrayList<TransientErrorStateVariable>(variables);
    if (copy.contains(null)) {
      throw new IllegalArgumentException("Numerical error-state vector must not contain null");
    }
    Collections.sort(copy, new Comparator<TransientErrorStateVariable>() {
      /**
       * Orders state variables independently of provider registration order.
       *
       * @param first first variable
       * @param second second variable
       * @return owner-first lexical ordering
       */
      @Override
      public int compare(TransientErrorStateVariable first, TransientErrorStateVariable second) {
        int owners = first.getOwnerIdentity().compareTo(second.getOwnerIdentity());
        return owners != 0 ? owners : first.getVariableIdentity().compareTo(second.getVariableIdentity());
      }
    });
    for (int index = 1; index < copy.size(); index++) {
      if (sameIdentity(copy.get(index - 1), copy.get(index))) {
        throw new IllegalArgumentException("Duplicate numerical error-state identity: " + describe(copy.get(index)));
      }
    }
    this.variables = Collections.unmodifiableList(copy);
  }

  /**
   * Returns the immutable identity-sorted scalar list.
   *
   * @return numerical state variables
   */
  public List<TransientErrorStateVariable> getVariables() {
    return variables;
  }

  /**
   * Computes the unextrapolated maximum normalized full-versus-half difference.
   *
   * <p>
   * For each scalar, error is absolute value difference divided by the absolute tolerance plus relative tolerance times
   * the larger absolute value. Equal maxima select the lexically first identity.
   * </p>
   *
   * @param other state vector from the alternate trial
   * @return maximum normalized difference and its limiting variable
   * @throws IllegalArgumentException if vector identities, units or tolerance policies differ
   */
  public ErrorEstimate compareTo(TransientErrorStateVector other) {
    if (other == null || variables.size() != other.variables.size()) {
      throw new IllegalArgumentException("Numerical trial state coverage changed");
    }
    double maximum = 0.0;
    String limiting = describe(variables.get(0));
    for (int index = 0; index < variables.size(); index++) {
      TransientErrorStateVariable first = variables.get(index);
      TransientErrorStateVariable second = other.variables.get(index);
      if (!sameIdentity(first, second) || !first.getUnit().equals(second.getUnit())
          || Double.compare(first.getAbsoluteTolerance(), second.getAbsoluteTolerance()) != 0
          || Double.compare(first.getRelativeTolerance(), second.getRelativeTolerance()) != 0) {
        throw new IllegalArgumentException(
            "Numerical trial state identity, unit or tolerance changed: " + describe(first));
      }
      double difference = Math.abs(first.getValue() - second.getValue());
      double scale = first.getAbsoluteTolerance()
          + first.getRelativeTolerance() * Math.max(Math.abs(first.getValue()), Math.abs(second.getValue()));
      double normalized = !Double.isFinite(difference) || !Double.isFinite(scale) ? Double.POSITIVE_INFINITY
          : difference / scale;
      if (normalized > maximum) {
        maximum = normalized;
        limiting = describe(first);
      }
    }
    return new ErrorEstimate(maximum, limiting);
  }

  /**
   * Compares the two identity components without delimiter encoding.
   *
   * @param first first variable
   * @param second second variable
   * @return whether owner and variable identities match exactly
   */
  private static boolean sameIdentity(TransientErrorStateVariable first, TransientErrorStateVariable second) {
    return first.getOwnerIdentity().equals(second.getOwnerIdentity())
        && first.getVariableIdentity().equals(second.getVariableIdentity());
  }

  /**
   * Describes a limiting variable for human-readable diagnostics.
   *
   * @param variable numerical state variable
   * @return owner and variable description
   */
  private static String describe(TransientErrorStateVariable variable) {
    return variable.getOwnerIdentity() + "::" + variable.getVariableIdentity();
  }

  /**
   * Immutable outcome of a numerical trial-vector comparison.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class ErrorEstimate implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double normalizedError;
    private final String limitingVariable;

    /**
     * Creates comparison evidence.
     *
     * @param normalizedError dimensionless maximum normalized error, possibly positive infinity
     * @param limitingVariable identity of the maximum-error variable
     */
    private ErrorEstimate(double normalizedError, String limitingVariable) {
      this.normalizedError = normalizedError;
      this.limitingVariable = limitingVariable;
    }

    /**
     * Returns the maximum normalized difference.
     *
     * @return dimensionless error or positive infinity after arithmetic overflow
     */
    public double getNormalizedError() {
      return normalizedError;
    }

    /**
     * Returns the deterministically selected limiting variable.
     *
     * @return limiting state identity
     */
    public String getLimitingVariable() {
      return limitingVariable;
    }
  }
}
