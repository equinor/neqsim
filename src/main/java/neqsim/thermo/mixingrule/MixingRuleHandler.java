package neqsim.thermo.mixingrule;

import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * Abstract MixingRuleHandler class.
 * </p>
 *
 * @author ASMF
 */
public abstract class MixingRuleHandler implements ThermodynamicConstantsInterface, Cloneable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  protected String mixingRuleName;

  /**
   * <p>
   * getName.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getName() {
    return mixingRuleName;
  }
}
