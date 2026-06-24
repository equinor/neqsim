package neqsim.thermo.mixingrule;

import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * Abstract MixingRuleHandler class.
 *
 * @author ASMF
 */
public abstract class MixingRuleHandler implements ThermodynamicConstantsInterface, Cloneable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  protected String mixingRuleName;

  /**
   * getName.
   *
   * @return a {@link java.lang.String} object
   */
  public String getName() {
    return mixingRuleName;
  }
}
