package neqsim.process.safety.selfheating;

/**
 * Screening verdict for a self-heating criticality assessment.
 *
 * @author ESOL
 * @version 1.0
 */
public enum SelfHeatingVerdict {
  /**
   * Criticality parameter is well below the critical value; a stable steady-state temperature profile exists with
   * comfortable margin.
   */
  SUBCRITICAL,
  /**
   * Criticality parameter is below but close to the critical value. Small changes in liquid loading, insulation
   * thickness or surface temperature could tip the system into runaway, so the case warrants confirmation by testing.
   */
  MARGINAL,
  /**
   * Criticality parameter exceeds the critical value; no steady state exists and the body is predicted to self-ignite
   * after an induction period.
   */
  SELF_IGNITION
}
