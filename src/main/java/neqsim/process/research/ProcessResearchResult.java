package neqsim.process.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Result from a process research run.
 *
 * <p>
 * The result contains all generated candidates sorted by score, plus study-level messages. Failed
 * candidates are retained so users can understand why process alternatives were rejected.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessResearchResult {
  private final List<ProcessCandidate> candidates = new ArrayList<>();
  private final List<String> messages = new ArrayList<>();

  /**
   * Adds a candidate.
   *
   * @param candidate candidate to add
   */
  public void addCandidate(ProcessCandidate candidate) {
    candidates.add(candidate);
  }

  /**
   * Adds a study-level message.
   *
   * @param message message text
   */
  public void addMessage(String message) {
    messages.add(message);
  }

  /**
   * Sorts candidates by descending score with feasible candidates first.
   */
  public void sortCandidates() {
    Collections.sort(candidates, new Comparator<ProcessCandidate>() {
      @Override
      public int compare(ProcessCandidate left, ProcessCandidate right) {
        if (left.isFeasible() != right.isFeasible()) {
          return left.isFeasible() ? -1 : 1;
        }
        return Double.compare(right.getScore(), left.getScore());
      }
    });
  }

  /**
   * Gets all candidates.
   *
   * @return unmodifiable candidate list
   */
  public List<ProcessCandidate> getCandidates() {
    return Collections.unmodifiableList(candidates);
  }

  /**
   * Gets feasible candidates that are not dominated by another candidate.
   *
   * @return unmodifiable non-dominated candidate list
   */
  public List<ProcessCandidate> getNonDominatedCandidates() {
    List<ProcessCandidate> nonDominated = new ArrayList<ProcessCandidate>();
    for (ProcessCandidate candidate : candidates) {
      if (candidate.isFeasible() && !candidate.isDominated()) {
        nonDominated.add(candidate);
      }
    }
    return Collections.unmodifiableList(nonDominated);
  }

  /**
   * Gets study messages.
   *
   * @return unmodifiable message list
   */
  public List<String> getMessages() {
    return Collections.unmodifiableList(messages);
  }

  /**
   * Gets the best feasible candidate.
   *
   * @return best feasible candidate, or null if none are feasible
   */
  public ProcessCandidate getBestCandidate() {
    for (ProcessCandidate candidate : candidates) {
      if (candidate.isFeasible()) {
        return candidate;
      }
    }
    return null;
  }
}
