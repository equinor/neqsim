package neqsim.process.engineering.designcase;

/** Signals an incomplete multi-case run while retaining its auditable partial report. */
public final class EngineeringCaseExecutionException extends IllegalStateException {
  private static final long serialVersionUID = 1000L;
  private final EngineeringCaseRunReport partialReport;

  /**
   * Create an incomplete-case-run exception.
   *
   * @param message failure summary
   * @param partialReport report containing every completed case and finding
   */
  public EngineeringCaseExecutionException(String message, EngineeringCaseRunReport partialReport) {
    super(message);
    if (partialReport == null) {
      throw new IllegalArgumentException("partialReport cannot be null");
    }
    this.partialReport = partialReport;
  }

  /** @return auditable partial report */
  public EngineeringCaseRunReport getPartialReport() {
    return partialReport;
  }
}
