package neqsim.process.engineering.impact;

/** Controlled response required for an engineering object affected by a model change. */
public enum ImpactAction {
  REVIEW_CHANGE, RECALCULATE, REGENERATE, REVALIDATE, REAPPROVE, REQUALIFY
}
