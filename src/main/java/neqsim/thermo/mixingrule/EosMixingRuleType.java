package neqsim.thermo.mixingrule;

import neqsim.util.exception.InvalidInputException;

/**
 * Types of EosMixingRule, relating to different kind of mixing rules relevant for EOS type phases. Available types are:
 * <ul>
 * <li>NO - 1 - classic mixing rule with all kij set to zero (no-interaction)</li>
 * <li>CLASSIC - 2 - classic mixing rule with kij from NeqSim database</li>
 * <li>CLASSIC_HV - 3 - Huron Vidal mixing rule with parameters from NeqSim database</li>
 * <li>HV - 4 - Huron Vidal mixing rule with parameters from NeqSim database including HVDijT</li>
 * <li>WS - 5 - Wong-Sandler</li>
 * <li>CPA_MIX - 7 - classic mixing rule with kij of CPA from NeqSim Database</li>
 * <li>CLASSIC_T - 8 - classic mixing rule with temperature dependent kij</li>
 * <li>CLASSIC_T_CPA - 9 - classic mixing rule with temperature dependent kij of CPA from NeqSim database</li>
 * <li>CLASSIC_TX_CPA - 10 - classic mixing rule with temperature and composition dependent kij of CPA from NeqSim
 * database</li>
 * <li>SOREIDE_WHITSON - 11 - Soreide Whitson mixing rule</li>
 * </ul>
 *
 * @author ASMF
 */
public enum EosMixingRuleType implements MixingRuleTypeInterface {
  NO(1), CLASSIC(2), CLASSIC_HV(3), HV(4), WS(5), CPA_MIX(7), CLASSIC_T(8), CLASSIC_T_CPA(9), CLASSIC_TX_CPA(10),
  SOREIDE_WHITSON(11), CLASSIC_T2(12);

  /** Holder for old style integer pt. */
  private final int value;
  /** Holder for old style string physical property description. */

  // We know we'll never mutate this, so we can keep
  // a local copy for fast lookup in forName
  private static final EosMixingRuleType[] copyOfValues = values();

  /**
   * Constructor for EosMixingRuleType enum.
   *
   * @param value Numeric value index for mixing rule
   */
  private EosMixingRuleType(int value) {
    this.value = value;
  }

  /** {@inheritDoc} */
  @Override
  @Deprecated
  public int getValue() {
    return this.value;
  }

  /**
   * Get EosMixingRuleType by name.
   *
   * @param name Name to get EosMixingRuleType for.
   * @return EosMixingRuleType object
   */
  public static EosMixingRuleType byName(String name) {
    for (EosMixingRuleType mr : copyOfValues) {
      if (mr.name().equals(name.toUpperCase())) {
        return mr;
      }
    }
    throw new RuntimeException(new InvalidInputException("EosMixingRuleType", "byName", "name", "is not valid."));
  }

  /**
   * Returns the mixing rule a model was parameterised with, for callers that build a fluid from a model name and no
   * explicit rule (JSON builders, MCP runners).
   *
   * <p>
   * CPA-family models (name containing {@code CPA}, e.g. {@code CPA}, {@code CPA-SRK}, {@code ELECTROLYTE-CPA}) need
   * {@link #CLASSIC_TX_CPA}; with {@link #CLASSIC} they read the SRK binary-interaction database and give wrong
   * aqueous-phase results (a wet-gas hydrate temperature near 0 C instead of about 16 C at 100 bara). All other cubic
   * models default to {@link #CLASSIC}.
   * </p>
   *
   * @param modelName model name as used in NeqSim JSON inputs, case-insensitive, may be null
   * @return the default mixing rule for that model
   */
  public static EosMixingRuleType defaultForModel(String modelName) {
    if (modelName != null && modelName.trim().toUpperCase().contains("CPA")) {
      return CLASSIC_TX_CPA;
    }
    return CLASSIC;
  }

  /**
   * Get EosMixingRuleTypes by value.
   *
   * @param value Value to get EosMixingRuleTypes for.
   * @return EosMixingRuleTypes object
   */
  public static EosMixingRuleType byValue(int value) {
    for (EosMixingRuleType mr : copyOfValues) {
      if (mr.getValue() == (value)) {
        return mr;
      }
    }
    throw new RuntimeException(new InvalidInputException("EosMixingRuleType", "byValue", "value", "is not valid."));
  }
}
