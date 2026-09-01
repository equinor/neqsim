package neqsim.process.dynamics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable quantitative preflight report for transient step transaction coverage.
 *
 * <p>
 * Coverage is complete only when every mutable process element belongs to the typed {@link TransientStateParticipant}
 * contract and no orchestration blocker is present. A complete report says that rollback mechanics are available; it is
 * not evidence of physical or numerical qualification.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TransientTransactionCoverage implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final int processElementCount;
  private final int participantCount;
  private final List<String> blockingIssues;

  /**
   * Creates a coverage report.
   *
   * @param processElementCount number of unique registered elements and equipment-attached controllers
   * @param participantCount number implementing {@link TransientStateParticipant}
   * @param blockingIssues deterministic diagnostics for incomplete coverage
   */
  public TransientTransactionCoverage(int processElementCount, int participantCount, List<String> blockingIssues) {
    if (processElementCount < 0 || participantCount < 0 || participantCount > processElementCount) {
      throw new IllegalArgumentException("Invalid transient transaction coverage counts: elements="
          + processElementCount + ", participants=" + participantCount);
    }
    this.processElementCount = processElementCount;
    this.participantCount = participantCount;
    this.blockingIssues = Collections.unmodifiableList(new ArrayList<String>(blockingIssues));
  }

  /**
   * Returns the number of registered process elements included in the preflight.
   *
   * @return element count
   */
  public int getProcessElementCount() {
    return processElementCount;
  }

  /**
   * Returns the number of elements with typed in-place snapshot/restore support.
   *
   * @return participant count
   */
  public int getParticipantCount() {
    return participantCount;
  }

  /**
   * Returns deterministic blocking diagnostics.
   *
   * @return immutable issue list
   */
  public List<String> getBlockingIssues() {
    return blockingIssues;
  }

  /**
   * Returns whether the transaction covers every registered element and orchestration dependency.
   *
   * @return {@code true} when a transaction may begin
   */
  public boolean isComplete() {
    return participantCount == processElementCount && blockingIssues.isEmpty();
  }

  /**
   * Fails before trial mutation when coverage is incomplete.
   *
   * @throws IllegalStateException if {@link #isComplete()} is false
   */
  public void assertComplete() {
    if (!isComplete()) {
      throw new IllegalStateException("Transient step transaction coverage is incomplete (" + participantCount + "/"
          + processElementCount + " process elements): " + blockingIssues);
    }
  }
}
