package neqsim.thermo.mixingrule;

import java.io.Serializable;

/**
 * MixingRulesInterface interface.
 *
 * @author ASMF
 */
public interface MixingRulesInterface extends Serializable, Cloneable {
  /**
   * Get name of mixing rule.
   *
   * @return a {@link java.lang.String} object
   */
  public java.lang.String getName();
}
