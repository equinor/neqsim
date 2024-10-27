package neqsim.physicalproperties;

import neqsim.util.exception.InvalidInputException;

/**
 * Types of PhysicalProperties, e.g. mass density, dynamic viscosity, thermal conductivity.
 *
 * @author ASMF
 */

public enum PhysicalPropertyType {
  MASS_DENSITY, DYNAMIC_VISCOSITY, THERMAL_CONDUCTIVITY;

  // We know we'll never mutate this, so we can keep
  // a local copy for fast lookup in byName
  private static final PhysicalPropertyType[] copyOfValues = values();

  /**
   * Get PhysicalPropertyType by name.
   *
   * @param name Name to get PhysicalPropertyType for.
   * @return PhysicalPropertyType object
   */
  public static PhysicalPropertyType byName(String name) {
    // todo: consider replacing this function with built-in valueOf
    for (PhysicalPropertyType pt : copyOfValues) {
      if (pt.name().equals(name.toUpperCase())) {
        return pt;
      }
    }
    throw new RuntimeException(
        new InvalidInputException("PhysicalPropertyType", "byName", "name", "is not valid."));
  }
}
