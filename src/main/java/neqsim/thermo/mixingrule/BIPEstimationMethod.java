package neqsim.thermo.mixingrule;

/**
 * Enumeration of binary interaction parameter (BIP) estimation methods.
 *
 * <p>
 * These methods are used to estimate BIPs when experimental values are not available. The methods
 * are based on correlations from the literature, particularly those referenced in the Whitson wiki
 * (https://wiki.whitson.com/eos/bips/).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see BIPEstimator
 */
public enum BIPEstimationMethod {
  /**
   * Chueh-Prausnitz correlation (1967).
   *
   * <p>
   * Uses critical volumes to estimate BIPs. Recommended for hydrocarbon-hydrocarbon pairs.
   * Reference: Chueh, P.L. and Prausnitz, J.M., AIChE Journal, 13:1099-1107, 1967.
   * </p>
   */
  CHUEH_PRAUSNITZ,

  /**
   * Katz-Firoozabadi correlation (1978).
   *
   * <p>
   * Specifically designed for methane interactions with C7+ fractions. Reference: Katz, D.L. and
   * Firoozabadi, A., JPT, paper SPE-6721-PA, 1978.
   * </p>
   */
  KATZ_FIROOZABADI,

  /**
   * Default method - uses database values or zero if not available.
   */
  DEFAULT
}
