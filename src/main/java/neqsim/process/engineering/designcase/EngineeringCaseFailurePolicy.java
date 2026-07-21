package neqsim.process.engineering.designcase;

/** Controls propagation of incomplete multi-case engineering results. */
public enum EngineeringCaseFailurePolicy {
  /** Return the partial report for explicit inspection. */
  RETURN_PARTIAL,
  /** Throw an exception that retains the partial report. */
  THROW_WITH_PARTIAL_RESULT
}
