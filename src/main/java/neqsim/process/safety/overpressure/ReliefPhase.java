package neqsim.process.safety.overpressure;

/**
 * Physical phase of the relieving stream for a relief scenario.
 *
 * <p>
 * The relief phase selects the applicable API 520 sizing method (vapour/gas, liquid, or two-phase) and is used by
 * {@link OverpressureProtectionStudy} when sizing the pressure relief device for the governing case.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum ReliefPhase {
  /** Single-phase vapour/gas relief (API 520 critical/sub-critical gas sizing). */
  VAPOUR,
  /** Single-phase liquid relief (API 520 liquid sizing). */
  LIQUID,
  /** Two-phase (flashing) relief (API 520 / DIERS two-phase sizing). */
  TWO_PHASE
}
