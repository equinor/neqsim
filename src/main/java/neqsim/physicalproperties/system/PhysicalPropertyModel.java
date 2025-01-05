package neqsim.physicalproperties.system;

import neqsim.util.exception.InvalidInputException;

/**
 * Types of PhysicalPropertyModel, relating to different kind of phaseTypes. This is used when
 * initializing PhysicalPropertyhandler. Available types are DEFAULT, WATER, GLYCOL, AMINE,
 * CO2WATER, BASIC
 *
 * @author ASMF
 */
public enum PhysicalPropertyModel {
  DEFAULT(0), WATER(1), GLYCOL(2), AMINE(3), CO2WATER(4), BASIC(6);

  /** Holder for old style integer pt. */
  private final int value;
  /** Holder for old style string physical property description. */

  // We know we'll never mutate this, so we can keep
  // a local copy for fast lookup in forName
  private static final PhysicalPropertyModel[] copyOfValues = values();

  /**
   * Constructor for PhysicalPropertyModel enum.
   *
   * @param value Numeric value index for phase type
   */
  private PhysicalPropertyModel(int value) {
    this.value = value;
  }

  /**
   * Getter for property value.
   *
   * @return Numeric index of phase type
   */
  @Deprecated
  public int getValue() {
    return this.value;
  }

  /**
   * Get PhysicalPropertyModel by name.
   *
   * @param name Name to get PhysicalPropertyModel for.
   * @return PhysicalPropertyModel object
   */
  public static PhysicalPropertyModel byName(String name) {
    for (PhysicalPropertyModel pt : copyOfValues) {
      if (pt.name().equals(name.toUpperCase())) {
        return pt;
      }
    }
    throw new RuntimeException(
        new InvalidInputException("PhysicalPropertyModel", "byName", "name", "is not valid."));
  }

  /**
   * Get PhysicalPropertyModel by value.
   *
   * @param value Value to get PhysicalPropertyModel for.
   * @return PhysicalPropertyModel object
   */
  public static PhysicalPropertyModel byValue(int value) {
    for (PhysicalPropertyModel pt : copyOfValues) {
      if (pt.getValue() == (value)) {
        return pt;
      }
    }
    throw new RuntimeException(
        new InvalidInputException("PhysicalPropertyModel", "byValue", "value", "is not valid."));
  }
}
