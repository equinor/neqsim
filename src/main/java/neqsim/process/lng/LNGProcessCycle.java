package neqsim.process.lng;

/**
 * Supported natural-gas liquefaction cycle templates.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public enum LNGProcessCycle {
  /** Single mixed-refrigerant cycle. */
  SMR,
  /** Propane-precooled mixed-refrigerant cycle. */
  C3MR,
  /** Dual mixed-refrigerant cycle. */
  DMR,
  /** Closed reverse-Brayton nitrogen-expander cycle. */
  NITROGEN_EXPANDER
}
