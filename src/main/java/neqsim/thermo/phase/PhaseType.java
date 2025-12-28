package neqsim.thermo.phase;

import neqsim.util.exception.InvalidInputException;

/**
 * Types of phases.
 *
 * @author ASMF
 */
public enum PhaseType {
  LIQUID("liquid", 0), GAS("gas", 1), OIL("oil", 2), AQUEOUS("aqueous", 3), HYDRATE("hydrate",
      4), WAX("wax",
          5), SOLID("solid", 6), SOLIDCOMPLEX("solidComplex", 7), ASPHALTENE("asphaltene", 8),
  /**
   * Asphaltene-rich liquid phase for Pedersen's liquid-liquid approach.
   *
   * <p>
   * This phase type is used when modeling asphaltene precipitation as a liquid-liquid equilibrium
   * (Pedersen's classical cubic EOS approach) rather than as a solid precipitate. The
   * LIQUID_ASPHALTENE phase behaves like a heavy oil phase with EOS-calculated properties.
   * </p>
   */
  LIQUID_ASPHALTENE("asphaltene liquid", 9);

  /** Holder for old style integer pt. */
  private final int value;
  /** Holder for old style string phasetypename. */
  private final String desc;

  // We know we'll never mutate this, so we can keep
  // a local copy for fast lookup in forName
  private static final PhaseType[] copyOfValues = values();

  /**
   * Constructor for PhaseType enum.
   *
   * @param desc Single word descriptor of phase type
   * @param value Numeric value index for phase type
   */
  private PhaseType(String desc, int value) {
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
   * Get PhaseType by name.
   *
   * @param name Name to get PhaseType for.
   * @return PhaseType object
   */
  public static PhaseType byName(String name) {
    for (PhaseType pt : copyOfValues) {
      if (pt.name().equals(name)) {
        return pt;
      }
    }
    throw new RuntimeException(
        new InvalidInputException("PhaseType", "byName", "name", "is not valid."));
  }

  /**
   * Get PhaseType by desc.
   *
   * @param desc Description to get PhaseType for.
   * @return PhaseType object.
   */
  public static PhaseType byDesc(String desc) {
    for (PhaseType pt : copyOfValues) {
      if (pt.getDesc().equals(desc)) {
        return pt;
      }
    }
    throw new RuntimeException(
        new InvalidInputException("PhaseType", "byDesc", "desc", "is not valid."));
  }

  /**
   * Get PhaseType by value.
   *
   * @param value Value to get PhaseType for.
   * @return PhaseType object
   */
  public static PhaseType byValue(int value) {
    for (PhaseType pt : copyOfValues) {
      if (pt.getValue() == (value)) {
        return pt;
      }
    }
    throw new RuntimeException(
        new InvalidInputException("PhaseType", "byValue", "value", "is not valid."));
  }
}
