package neqsim.thermo.mixingrule;

import neqsim.util.exception.InvalidInputException;

/**
 * Types of CPAMixingRule, relating to different kind of mixing rules relevant for CPA type phases.
 * Available types are:
 * <ul>
 * <li>CPA_RADOCH - 1 -</li>
 * <li>PCSAFTA_RADOCH - 3 -</li>
 * </ul>
 *
 * @author ASMF
 */
public enum CPAMixingRuleType implements MixingRuleTypeInterface {
  CPA_RADOCH(1), PCSAFTA_RADOCH(3);

  /** Holder for old style integer pt. */
  private final int value;
  /** Holder for old style string physical property description. */

  // We know we'll never mutate this, so we can keep
  // a local copy for fast lookup in forName
  private static final CPAMixingRuleType[] copyOfValues = values();

  /**
   * Constructor for CPAMixingRuleType enum.
   *
   * @param value Numeric value index for mixing rule
   */
  private CPAMixingRuleType(int value) {
    this.value = value;
  }

  /**
   * Getter for property value.
   *
   * @return Numeric index of phase type
   */
  @Override
  @Deprecated
  public int getValue() {
    return this.value;
  }

  /**
   * Get CPAMixingRuleType by name.
   *
   * @param name Name to get CPAMixingRuleType for.
   * @return CPAMixingRuleType object
   */
  public static CPAMixingRuleType byName(String name) {
    for (CPAMixingRuleType mr : copyOfValues) {
      if (mr.name().equals(name.toUpperCase())) {
        return mr;
      }
    }
    throw new RuntimeException(
        new InvalidInputException("CPAMixingRuleType", "byName", "name", "is not valid."));
  }

  /**
   * Get CPAMixingRuleType by value.
   *
   * @param value Value to get CPAMixingRuleType for.
   * @return CPAMixingRuleType object
   */
  public static CPAMixingRuleType byValue(int value) {
    for (CPAMixingRuleType mr : copyOfValues) {
      if (mr.getValue() == (value)) {
        return mr;
      }
    }
    throw new RuntimeException(
        new InvalidInputException("CPAMixingRuleType", "byValue", "value", "is not valid."));
  }
}
