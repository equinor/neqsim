package neqsim.process.automation;

import java.io.Serializable;

/**
 * Describes a single variable exposed by a unit operation in a process simulation. Each variable
 * has a stable string address that can be used with
 * {@link ProcessAutomation#getVariableValue(String, String)} and
 * {@link ProcessAutomation#setVariableValue(String, double, String)}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SimulationVariable implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Variable access type.
   */
  public enum VariableType {
    /** A calculated output (read-only). */
    OUTPUT,
    /** A user-settable input (read-write). */
    INPUT
  }

  private final String address;
  private final String name;
  private final VariableType type;
  private final String defaultUnit;
  private final String description;

  /**
   * Creates a new simulation variable descriptor.
   *
   * @param address the stable dot-separated address, e.g. "separator-1.gasOutStream.temperature"
   * @param name the short property name, e.g. "temperature"
   * @param type whether the variable is INPUT (read-write) or OUTPUT (read-only)
   * @param defaultUnit the default unit of measure, e.g. "K", "bara", "kg/hr"
   * @param description a human-readable description
   */
  public SimulationVariable(String address, String name, VariableType type, String defaultUnit,
      String description) {
    this.address = address;
    this.name = name;
    this.type = type;
    this.defaultUnit = defaultUnit;
    this.description = description;
  }

  /**
   * Returns the stable dot-notation address for this variable.
   *
   * @return the variable address
   */
  public String getAddress() {
    return address;
  }

  /**
   * Returns the short property name.
   *
   * @return the property name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns whether this variable is an input or output.
   *
   * @return the variable type
   */
  public VariableType getType() {
    return type;
  }

  /**
   * Returns the default unit of measure.
   *
   * @return the default unit string
   */
  public String getDefaultUnit() {
    return defaultUnit;
  }

  /**
   * Returns a human-readable description of the variable.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return address + " [" + type + ", " + defaultUnit + "] " + description;
  }
}
