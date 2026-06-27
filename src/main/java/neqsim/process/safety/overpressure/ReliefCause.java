package neqsim.process.safety.overpressure;

/**
 * Credible overpressure causes recognised by Equinor TR3001 (Process safety) and API STD 521 6th edition section 4.4.
 *
 * <p>
 * Each constant carries a short human-readable label and the governing requirement reference (TR3001 SR-clause and/or
 * API STD 521 section) so that a generated relief scenario is traceable to the standard that mandates its
 * consideration.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum ReliefCause {
  /** Blocked outlet from equipment (TR3001 4.7.2 / API 521 4.4.2). */
  BLOCKED_OUTLET("Blocked outlet", "TR3001 SR-26460 / API 521 4.4.2"),

  /** Leakage through check valve(s) (TR3001 4.7.3 / API 521 4.4.9.3). */
  CHECK_VALVE_LEAKAGE("Check valve leakage", "TR3001 SR-26466 / API 521 4.4.9.3"),

  /** Gas blow-by on loss of liquid level (TR3001 4.7.4 / API 521 4.4.8.3). */
  GAS_BLOW_BY("Gas blow-by", "TR3001 SR-26474 / API 521 4.4.8.3"),

  /** Liquid blow-by on loss of interface level (TR3001 4.7.4 / API 521 4.4.8.3). */
  LIQUID_BLOW_BY("Liquid blow-by", "TR3001 SR-26474 / API 521 4.4.8.3"),

  /** Entrance of volatile liquids into a warmer system (TR3001 4.7.5 / API 521 4.4.6). */
  VOLATILE_LIQUID_INGRESS("Volatile liquid ingress", "TR3001 SR-26482 / API 521 4.4.6"),

  /** Inadvertent valve opening (TR3001 4.7.6 / API 521 4.4.9.2). */
  INADVERTENT_VALVE_OPENING("Inadvertent valve opening", "TR3001 SR-26491 / API 521 4.4.9.2"),

  /** Control valve failure, fail-open / increased Cv (TR3001 4.7.7). */
  CONTROL_VALVE_FAILURE("Control valve failure", "TR3001 SR-26501 / API 521 4.4.9"),

  /** External fire relief (TR3001 4.7.8 / API 521 4.4.13.2.4). */
  FIRE("Fire", "TR3001 SR-26504 / API 521 4.4.13.2.4"),

  /** Thermal expansion of trapped/blocked-in liquid (TR3001 4.8.6.3 / API 521 4.4.12). */
  THERMAL_EXPANSION("Thermal expansion", "TR3001 SR-26613 / API 521 4.4.12"),

  /** Choke collapse on inlet arrangement (TR3001 4.8.4.5). */
  CHOKE_COLLAPSE("Choke collapse", "TR3001 SR-26562 / API 521 4.3.3"),

  /** Heat-exchanger tube rupture (TR3001 4.8.6.2 / API 521 4.4.14). */
  TUBE_RUPTURE("Tube rupture", "TR3001 SR-26616 / API 521 4.4.14"),

  /** Vacuum / external pressure collapse (TR3001 5 / API 521 4.4.15). */
  VACUUM("Vacuum collapse", "TR3001 / API 521 4.4.15"),

  /** Any other / user-defined cause. */
  OTHER("Other", "User defined");

  private final String label;
  private final String standardReference;

  /**
   * Creates a relief cause constant.
   *
   * @param label short human-readable label for the cause
   * @param standardReference governing TR3001 SR-clause and/or API STD 521 section reference
   */
  ReliefCause(String label, String standardReference) {
    this.label = label;
    this.standardReference = standardReference;
  }

  /**
   * Gets the short human-readable label for this cause.
   *
   * @return the cause label
   */
  public String getLabel() {
    return label;
  }

  /**
   * Gets the governing standard reference (TR3001 SR-clause and/or API STD 521 section) for this cause.
   *
   * @return the standard reference string
   */
  public String getStandardReference() {
    return standardReference;
  }
}
