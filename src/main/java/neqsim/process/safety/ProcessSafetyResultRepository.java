package neqsim.process.safety;

import java.util.List;

/**
 * Optional persistence contract for safety analysis results.
 */
public interface ProcessSafetyResultRepository {
  /**
   * Persist a safety analysis summary.
   *
   * @param summary summary to persist
   */
  void save(ProcessSafetyAnalysisSummary summary);

  /**
   * Retrieve stored results.
   *
   * @return list of stored summaries (may be empty)
   */
  default List<ProcessSafetyAnalysisSummary> findAll() {
    return java.util.Collections.emptyList();
  }
}
