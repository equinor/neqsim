package neqsim.process.engineering.design;

/**
 * Signals incompatible module proposals for one engineering design variable.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EngineeringDesignConflictException extends IllegalStateException {
  private static final long serialVersionUID = 1000L;

  private final String designVariableKey;

  /**
   * Create a design-update conflict.
   *
   * @param designVariableKey conflicting design variable
   * @param message diagnostic containing module owners and proposed values
   */
  public EngineeringDesignConflictException(String designVariableKey, String message) {
    super(message);
    this.designVariableKey = designVariableKey;
  }

  /** @return conflicting design-variable key */
  public String getDesignVariableKey() {
    return designVariableKey;
  }
}
