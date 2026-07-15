package neqsim.process.engineering;

/** Lifecycle state for generated engineering information. */
public enum EngineeringApprovalStatus {
  /** Automatically proposed information that has not yet been reviewed. */
  PROPOSED,
  /** Information that requires discipline review before design use. */
  REVIEW_REQUIRED,
  /** Information accepted by an accountable engineer. */
  APPROVED,
  /** Information rejected during engineering review. */
  REJECTED
}
