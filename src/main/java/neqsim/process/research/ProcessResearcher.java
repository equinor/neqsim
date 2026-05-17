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
    boolean noWorse = metricValue(left, "equipmentCount") <= metricValue(right, "equipmentCount")
        && metricValue(left, "totalPower_kW") <= metricValue(right, "totalPower_kW")
        && utilityValue(left, "hotUtility_kW", "heatingDuty_kW") <= utilityValue(right,
            "hotUtility_kW", "heatingDuty_kW")
        && utilityValue(left, "coldUtility_kW", "coolingDuty_kW") <= utilityValue(right,
            "coldUtility_kW", "coolingDuty_kW")
        && metricValue(left, "annualOperatingCostProxy_USD_per_yr") <= metricValue(right,
            "annualOperatingCostProxy_USD_per_yr")
        && metricValue(left, "emissions_kgCO2e_per_hr") <= metricValue(right,
            "emissions_kgCO2e_per_hr");
    boolean strictlyBetter = left.getScore() > right.getScore()
        || metricValue(left, "equipmentCount") < metricValue(right, "equipmentCount")
        || metricValue(left, "totalPower_kW") < metricValue(right, "totalPower_kW")
        || metricValue(left, "annualOperatingCostProxy_USD_per_yr") < metricValue(right,
            "annualOperatingCostProxy_USD_per_yr")
        || metricValue(left, "emissions_kgCO2e_per_hr") < metricValue(right,
            "emissions_kgCO2e_per_hr");
    return noWorse && strictlyBetter;
  }

  /**
   * Gets a minimization metric value, treating absent values as zero burden.
   *
   * @param candidate candidate to inspect
   * @param metricName metric name
   * @return metric value
   */
  private double metricValue(ProcessCandidate candidate, String metricName) {
    return candidate.getMetrics().get(metricName, 0.0);
  }

  /**
   * Gets a utility metric value with a fallback raw duty metric.
   *
   * @param candidate candidate to inspect
   * @param primaryMetric primary utility metric name
   * @param fallbackMetric fallback duty metric name
   * @return utility burden in kW
   */
  private double utilityValue(ProcessCandidate candidate, String primaryMetric,
      String fallbackMetric) {
    return candidate.getMetrics().get(primaryMetric,
        candidate.getMetrics().get(fallbackMetric, 0.0));
  }
}
