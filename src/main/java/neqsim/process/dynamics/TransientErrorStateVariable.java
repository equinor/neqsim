package neqsim.process.dynamics;

import java.io.Serializable;

/**
 * Immutable scalar used to compare numerical transient trials in an explicit physical unit.
 *
 * <p>
 * The absolute tolerance has the same unit as the value; the relative tolerance is dimensionless. Owner and variable
 * identities describe numerical state and must remain stable between trials.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TransientErrorStateVariable implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String ownerIdentity;
  private final String variableIdentity;
  private final String unit;
  private final double value;
  private final double absoluteTolerance;
  private final double relativeTolerance;

  /**
   * Creates one explicitly scaled state value.
   *
   * @param ownerIdentity stable identity of the state owner
   * @param variableIdentity stable variable identity within that owner
   * @param unit physical unit of the value and absolute tolerance
   * @param value finite state value
   * @param absoluteTolerance finite, strictly positive absolute tolerance
   * @param relativeTolerance finite, nonnegative dimensionless relative tolerance
   * @throws IllegalArgumentException if an identity, value or tolerance is invalid
   */
  public TransientErrorStateVariable(String ownerIdentity, String variableIdentity, String unit, double value,
      double absoluteTolerance, double relativeTolerance) {
    this.ownerIdentity = requireText(ownerIdentity, "ownerIdentity");
    this.variableIdentity = requireText(variableIdentity, "variableIdentity");
    this.unit = requireText(unit, "unit");
    if (!Double.isFinite(value) || !Double.isFinite(absoluteTolerance) || absoluteTolerance <= 0.0
        || !Double.isFinite(relativeTolerance) || relativeTolerance < 0.0) {
      throw new IllegalArgumentException(
          "State values must be finite, absoluteTolerance > 0 and relativeTolerance >= 0");
    }
    this.value = value;
    this.absoluteTolerance = absoluteTolerance;
    this.relativeTolerance = relativeTolerance;
  }

  /**
   * Requires an explicit nonblank identity or unit without rewriting its contents.
   *
   * @param value supplied text
   * @param name field name for diagnostics
   * @return original nonblank text
   * @throws IllegalArgumentException if the text is absent or blank
   */
  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be null or blank");
    }
    return value;
  }

  /**
   * Returns the stable state-owner identity.
   *
   * @return owner identity
   */
  public String getOwnerIdentity() {
    return ownerIdentity;
  }

  /**
   * Returns the variable identity within its owner.
   *
   * @return variable identity
   */
  public String getVariableIdentity() {
    return variableIdentity;
  }

  /**
   * Returns the physical unit of the value and absolute tolerance.
   *
   * @return explicit physical unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Returns the finite numerical state value.
   *
   * @return state value in the declared unit
   */
  public double getValue() {
    return value;
  }

  /**
   * Returns the positive absolute error scale.
   *
   * @return absolute tolerance in the declared unit
   */
  public double getAbsoluteTolerance() {
    return absoluteTolerance;
  }

  /**
   * Returns the nonnegative relative error scale.
   *
   * @return dimensionless relative tolerance
   */
  public double getRelativeTolerance() {
    return relativeTolerance;
  }
}
