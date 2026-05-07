package neqsim.process.research;

import java.util.List;

/**
 * High-level process researcher for generating, simulating, optimizing, and ranking processes.
 *
 * <p>
 * This class is the public entry point for early-stage process synthesis. It generates candidate
 * NeqSim process definitions from a {@link ProcessResearchSpec}, optionally evaluates them with the
 * rigorous process simulator, applies bounded decision-variable screening, and returns ranked
 * candidates with warnings, assumptions, and objective values.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessResearcher {
  private final ProcessCandidateGenerator generator;
  private final ProcessCandidateEvaluator evaluator;
  private final ProcessSynthesisFeasibilityPruner pruner;

  /**
   * Creates a process researcher with default generator and evaluator.
   */
  public ProcessResearcher() {
    this(new ProcessCandidateGenerator(), new ProcessCandidateEvaluator(),
        new ProcessSynthesisFeasibilityPruner());
  }

  /**
   * Creates a process researcher with custom components.
   *
   * @param generator candidate generator
   * @param evaluator candidate evaluator
   */
  public ProcessResearcher(ProcessCandidateGenerator generator,
      ProcessCandidateEvaluator evaluator) {
    this(generator, evaluator, new ProcessSynthesisFeasibilityPruner());
  }

  /**
   * Creates a process researcher with custom components.
   *
   * @param generator candidate generator
   * @param evaluator candidate evaluator
   * @param pruner feasibility pruner
   */
  public ProcessResearcher(ProcessCandidateGenerator generator, ProcessCandidateEvaluator evaluator,
      ProcessSynthesisFeasibilityPruner pruner) {
    this.generator = generator;
    this.evaluator = evaluator;
    this.pruner = pruner;
  }

  /**
   * Runs a process research study.
   *
   * @param spec process research specification
   * @return ranked process research result
   */
  public ProcessResearchResult research(ProcessResearchSpec spec) {
    ProcessResearchResult result = new ProcessResearchResult();
    if (spec.isFeasibilityPruningEnabled()) {
      for (String issue : pruner.validateSpec(spec)) {
        result.addMessage("Specification issue: " + issue);
      }
    }
    List<ProcessCandidate> candidates = generator.generate(spec);
    if (candidates.isEmpty()) {
      result.addMessage("No candidates were generated. Add allowed units, operation options, or "
          + "reaction options that can produce the requested product targets.");
      return result;
    }
    for (ProcessCandidate candidate : candidates) {
      if (spec.isEvaluateCandidates() && candidate.getErrors().isEmpty()) {
        evaluator.evaluate(candidate, spec);
      }
      result.addCandidate(candidate);
    }
    result.sortCandidates();
    markDominatedCandidates(result);
    return result;
  }

  /**
   * Marks feasible candidates dominated by another candidate with at least as good score and lower
   * complexity or power demand.
   *
   * @param result process research result to update
   */
  private void markDominatedCandidates(ProcessResearchResult result) {
    List<ProcessCandidate> candidates = result.getCandidates();
    for (ProcessCandidate candidate : candidates) {
      if (!candidate.isFeasible()) {
        continue;
      }
      for (ProcessCandidate other : candidates) {
        if (candidate == other || !other.isFeasible()) {
          continue;
        }
        if (dominates(other, candidate)) {
          candidate.setDominated(true);
          candidate.setDominanceReason("Dominated by " + other.getName()
              + " on score with no higher complexity or power demand");
          break;
        }
      }
    }
  }

  /**
   * Checks whether one candidate dominates another.
   *
   * @param left candidate that may dominate
   * @param right candidate that may be dominated
   * @return true if left dominates right
   */
  private boolean dominates(ProcessCandidate left, ProcessCandidate right) {
    if (left.getScore() < right.getScore()) {
      return false;
    }
    double leftCount = left.getMetrics().get("equipmentCount", Double.MAX_VALUE);
    double rightCount = right.getMetrics().get("equipmentCount", Double.MAX_VALUE);
    double leftPower = left.getMetrics().get("totalPower_kW", Double.MAX_VALUE);
    double rightPower = right.getMetrics().get("totalPower_kW", Double.MAX_VALUE);
    return leftCount <= rightCount && leftPower <= rightPower
        && (left.getScore() > right.getScore() || leftCount < rightCount || leftPower < rightPower);
  }
}
