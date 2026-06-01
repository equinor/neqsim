package neqsim.process.safety.barrier;

/**
 * Classification of safety-system barrier functions used in performance assessments.
 *
 * <p>
 * The categories are intentionally aligned with common safety critical systems extracted from STID,
 * P&amp;ID, cause-and-effect, safety requirement, firewater, and passive fire protection
 * documentation. They provide a stable bridge between document-derived barrier registers and NeqSim
 * consequence, SIS, LOPA, and escalation analyses.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum SafetySystemCategory {
  /** Firewater, deluge, spray, monitor, and sprinkler systems. */
  FIREWATER_DELUGE("Firewater and deluge"),

  /** Fire and gas detectors, F&amp;G voting, and detector coverage functions. */
  FIRE_GAS_DETECTION("Fire and gas detection"),

  /** Passive fire protection on equipment, structures, supports, and walls. */
  PASSIVE_FIRE_PROTECTION("Passive fire protection"),

  /** Process shutdown functions and PSD valves. */
  PSD("Process shutdown"),

  /** Emergency shutdown and blowdown functions. */
  ESD_BLOWDOWN("Emergency shutdown and blowdown"),

  /** Relief, flare, vent, and pressure-relief functions. */
  RELIEF_FLARE("Relief and flare"),

  /** Structural firewalls, blast walls, and fire-resistant structural barriers. */
  STRUCTURAL_FIREWALL("Structural firewall or blast wall"),

  /** High-integrity pressure protection functions. */
  HIPPS("High-integrity pressure protection"),

  /** Category could not be inferred from the available data. */
  UNKNOWN("Unknown safety-system category");

  private final String description;

  /**
   * Creates a safety-system category.
   *
   * @param description human-readable category description
   */
  SafetySystemCategory(String description) {
    this.description = description;
  }

  /**
   * Gets the human-readable category description.
   *
   * @return category description
   */
  public String getDescription() {
    return description;
  }
}
