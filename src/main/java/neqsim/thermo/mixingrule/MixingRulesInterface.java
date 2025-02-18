package neqsim.thermo.mixingrule;

import java.io.Serializable;

/**
 * <p>MixingRulesInterface interface.</p>
 *
 * @author ASMF
 */
public interface MixingRulesInterface extends Serializable, Cloneable {
  /**
   * <p>
   * Get name of mixing rule.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public java.lang.String getName();
}
