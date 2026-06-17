package neqsim.statistics.parameterfitting;

import neqsim.thermo.system.SystemInterface;

/**
 * Parameter adapter for fitting one binary interaction parameter on a thermodynamic system.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class BinaryInteractionParameterAdapter implements ParameterUpdateAdapter {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private final transient SystemInterface system;
  private final String component1;
  private final String component2;
  private final FittingParameter parameter;

  /**
   * Creates a binary interaction parameter adapter.
   *
   * @param system thermodynamic system to update
   * @param component1 first component name
   * @param component2 second component name
   * @param parameter fitting parameter definition
   * @throws IllegalArgumentException if any required input is null
   */
  public BinaryInteractionParameterAdapter(SystemInterface system, String component1,
      String component2, FittingParameter parameter) {
    if (system == null) {
      throw new IllegalArgumentException("system cannot be null");
    }
    if (component1 == null || component2 == null) {
      throw new IllegalArgumentException("component names cannot be null");
    }
    if (parameter == null) {
      throw new IllegalArgumentException("parameter cannot be null");
    }
    this.system = system;
    this.component1 = component1;
    this.component2 = component2;
    this.parameter = parameter;
  }

  /** {@inheritDoc} */
  @Override
  public FittingParameter[] getParameters() {
    return new FittingParameter[] {parameter};
  }

  /** {@inheritDoc} */
  @Override
  public void applyParameters(double[] parameterValues) {
    if (parameterValues == null || parameterValues.length != 1) {
      throw new IllegalArgumentException("Binary interaction adapter expects exactly one value");
    }
    system.setBinaryInteractionParameter(component1, component2, parameterValues[0]);
    system.setBinaryInteractionParameter(component2, component1, parameterValues[0]);
  }

  /**
   * Returns the thermodynamic system updated by this adapter.
   *
   * @return thermodynamic system
   */
  public SystemInterface getSystem() {
    return system;
  }

  /**
   * Returns the first component name.
   *
   * @return first component name
   */
  public String getComponent1() {
    return component1;
  }

  /**
   * Returns the second component name.
   *
   * @return second component name
   */
  public String getComponent2() {
    return component2;
  }
}
