package neqsim.process.engineering.design;

/**
 * Signals an invalid engineering design-module dependency graph.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EngineeringDesignDependencyException extends IllegalArgumentException {
  private static final long serialVersionUID = 1000L;

  /**
   * Create a dependency-configuration exception.
   *
   * @param message diagnostic containing the invalid module IDs
   */
  public EngineeringDesignDependencyException(String message) {
    super(message);
  }
}
