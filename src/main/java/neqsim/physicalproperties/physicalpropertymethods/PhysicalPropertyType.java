package neqsim.physicalproperties.physicalpropertymethods;

import neqsim.util.exception.InvalidInputException;

/**
 * Types of PhysicalProperties.
 *
 * @author ASMF
 */

public enum PhysicalPropertyType {
  DEFAULT("default", 0), WATER("water", 1), GLYCOL("glycol", 2), AMINE("amine",
      3), CO2WATER("CO2water", 4), BASIC("basic", 6);

  /** Holder for old style integer pt. */
  private final int value;
  /** Holder for old style string physical property description. */
  private final String desc;

  // We know we'll never mutate this, so we can keep
  // a local copy for fast lookup in forName
  private static final PhysicalPropertyType[] copyOfValues = values();

  /**
   * Constructor for PhysicalPropertyType enum.
   *
   * @param desc Single word descriptor of phase type
   * @param value Numeric value index for phase type
   */
  private PhysicalPropertyType(String desc, int value) {
    this.desc = desc;
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
   * Getter for property desc.
   *
   * @return Single word descriptor for phase type.
   */
  public String getDesc() {
    return this.desc;
  }

  /**
   * Get PhysicalPropertyType by name.
   *
   * @param name Name to get PhysicalPropertyType for.
   * @return PhysicalPropertyType object
   */
  public static PhysicalPropertyType byName(String name) {
    for (PhysicalPropertyType pt : copyOfValues) {
      if (pt.name().equals(name)) {
        return pt;
      }
    }
    throw new RuntimeException(
        new InvalidInputException("PhysicalPropertyType", "byName", "name", "is not valid."));
  }

  /**
   * Get PhysicalPropertyType by desc.
   *
   * @param desc Description to get PhysicalPropertyType for.
   * @return PhysicalPropertyType object.
   */
  public static PhysicalPropertyType byDesc(String desc) {
    for (PhysicalPropertyType pt : copyOfValues) {
      if (pt.getDesc().equals(desc)) {
        return pt;
      }
    }
    throw new RuntimeException(
        new InvalidInputException("PhysicalPropertyType", "byDesc", "desc", "is not valid."));
  }

  /**
   * Get PhysicalPropertyType by value.
   *
   * @param value Value to get PhysicalPropertyType for.
   * @return PhysicalPropertyType object
   */
  public static PhysicalPropertyType byValue(int value) {
    for (PhysicalPropertyType pt : copyOfValues) {
      if (pt.getValue() == (value)) {
        return pt;
      }
    }
    throw new RuntimeException(
        new InvalidInputException("PhysicalPropertyType", "byValue", "value", "is not valid."));
  }
}
