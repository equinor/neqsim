package neqsim.thermo.phase;

/**
 * Types of phases.
 */
public enum PhaseType {
  LIQUID("liquid", 0), GAS("gas", 1), OIL("oil", 2), AQUEOUS("aqueous", 3), HYDRATE("hydrate",
      4), WAX("wax", 5), SOLID("soild", 6), SOLIDCOMPLEX("solidComplex", 7);

  private final int value;
  private final String desc;

  // We know we'll never mutate this, so we can keep
  // a local copy for fast lookup in forName
  private static final PhaseType[] copyOfValues = values();

  private PhaseType(String desc, int value) {
    this.desc = desc;
    this.value = value;
  }

  public int getValue() {
    return this.value;
  }

  public String getDesc() {
    return this.desc;
  }

  /**
   * Get PhaseType by name.
   */
  public static PhaseType byName(String name) {
    for (PhaseType pt : copyOfValues) {
      if (pt.name().equals(name)) {
        return pt;
      }
    }
    return null;
  }

  /**
   * Get PhaseType by desc.
   */
  public static PhaseType byDesc(String desc) {
    for (PhaseType pt : copyOfValues) {
      if (pt.getDesc().equals(desc)) {
        return pt;
      }
    }
    return null;
  }

  /**
   * Get PhaseType by value.
   */
  public static PhaseType byValue(int value) {
    for (PhaseType pt : copyOfValues) {
      if (pt.getValue() == (value)) {
        return pt;
      }
    }
    return null;
  }
}
