package neqsim.process.fielddevelopment.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.lang3.StringUtils;
import neqsim.process.fielddevelopment.concept.FieldConcept;

/**
 * Batch runner for parallel evaluation of multiple concepts.
 *
 * <p>
 * Enables overnight batch runs for concept screening with:
 * <ul>
 * <li>Parallel execution across concepts</li>
 * <li>Automatic result aggregation</li>
 * <li>Concept ranking and comparison</li>
 * <li>Progress tracking</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * 
 * <pre>
 * BatchConceptRunner runner = new BatchConceptRunner();
 * runner.addConcept(concept1);
 * runner.addConcept(concept2);
 * runner.addConcept(concept3);
 *
 * BatchResults results = runner.runAll();
 * ConceptKPIs best = results.getBestConcept();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class BatchConceptRunner {

  private final List<FieldConcept> concepts;
  private final ConceptEvaluator evaluator;
  private int parallelism;
  private ProgressListener progressListener;

  /**
   * Creates a batch runner with default evaluator.
   */
  public BatchConceptRunner() {
    this(new ConceptEvaluator());
  }

  /**
   * Creates a batch runner with custom evaluator.
   *
   * @param evaluator concept evaluator to use
   */
  public BatchConceptRunner(ConceptEvaluator evaluator) {
    this.concepts = new ArrayList<>();
    this.evaluator = evaluator;
    this.parallelism = Runtime.getRuntime().availableProcessors();
  }

  /**
   * Adds a concept to the batch.
   *
   * @param concept concept to add
   * @return this runner
   */
  public BatchConceptRunner addConcept(FieldConcept concept) {
    concepts.add(concept);
    return this;
  }

  /**
   * Adds multiple concepts to the batch.
   *
   * @param conceptList concepts to add
   * @return this runner
   */
  public BatchConceptRunner addConcepts(List<FieldConcept> conceptList) {
    concepts.addAll(conceptList);
    return this;
  }

  /**
   * Sets the parallelism level.
   *
   * @param threads number of parallel threads
   * @return this runner
   */
  public BatchConceptRunner parallelism(int threads) {
    this.parallelism = threads;
    return this;
  }

  /**
   * Sets a progress listener.
   *
   * @param listener progress listener
   * @return this runner
   */
  public BatchConceptRunner onProgress(ProgressListener listener) {
    this.progressListener = listener;
    return this;
  }

  /**
   * Runs evaluation for all concepts.
   *
   * @return batch results
   */
  public BatchResults runAll() {
    if (concepts.isEmpty()) {
      return new BatchResults(Collections.emptyList());
    }

    List<ConceptKPIs> results = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    ExecutorService executor = Executors.newFixedThreadPool(parallelism);
    try {
      List<Future<ConceptKPIs>> futures = new ArrayList<>();

      // Submit all concepts for evaluation
      for (FieldConcept concept : concepts) {
        futures.add(executor.submit(new EvaluationTask(concept)));
      }

      // Collect results
      int completed = 0;
      for (int i = 0; i < futures.size(); i++) {
        try {
          ConceptKPIs kpis = futures.get(i).get();
          results.add(kpis);
        } catch (Exception e) {
          String conceptName = concepts.get(i).getName();
          errors.add(conceptName + ": " + e.getMessage());
        }

        completed++;
        if (progressListener != null) {
          progressListener.onProgress(completed, concepts.size());
        }
      }
    } finally {
      executor.shutdown();
    }

    BatchResults batchResults = new BatchResults(results);
    for (String error : errors) {
      batchResults.addError(error);
    }
    return batchResults;
  }

  /**
   * Runs quick screening for all concepts.
   *
   * @return batch results
   */
  public BatchResults quickScreenAll() {
    if (concepts.isEmpty()) {
      return new BatchResults(Collections.emptyList());
    }

    List<ConceptKPIs> results = new ArrayList<>();

    // Quick screen is fast, run sequentially
    int completed = 0;
    for (FieldConcept concept : concepts) {
      try {
        ConceptKPIs kpis = evaluator.quickScreen(concept);
        results.add(kpis);
      } catch (Exception e) {
        // Skip failed concepts in quick screen
      }
      completed++;
      if (progressListener != null) {
        progressListener.onProgress(completed, concepts.size());
      }
    }

    return new BatchResults(results);
  }

  /**
   * Clears all concepts from the batch.
   *
   * @return this runner
   */
  public BatchConceptRunner clear() {
    concepts.clear();
    return this;
  }

  /**
   * Gets the number of concepts in the batch.
   *
   * @return concept count
   */
  public int getConceptCount() {
    return concepts.size();
  }

  /**
   * Task for parallel concept evaluation.
   */
  private class EvaluationTask implements Callable<ConceptKPIs> {
    private final FieldConcept concept;

    EvaluationTask(FieldConcept concept) {
      this.concept = concept;
    }

    @Override
    public ConceptKPIs call() {
      return evaluator.evaluate(concept);
    }
  }

  /**
   * Progress listener interface.
   */
  @FunctionalInterface
  public interface ProgressListener {
    /**
     * Called when progress is made.
     *
     * @param completed number of completed evaluations
     * @param total total number of evaluations
     */
    void onProgress(int completed, int total);
  }

  /**
   * Results from batch evaluation.
   */
  public static class BatchResults {
    private final List<ConceptKPIs> results;
    private final List<String> errors;

    BatchResults(List<ConceptKPIs> results) {
      this.results = new ArrayList<>(results);
      this.errors = new ArrayList<>();
    }

    void addError(String error) {
      errors.add(error);
    }

    /**
     * Gets all results.
     *
     * @return list of KPIs
     */
    public List<ConceptKPIs> getResults() {
      return Collections.unmodifiableList(results);
    }

    /**
     * Gets errors that occurred during evaluation.
     *
     * @return list of error messages
     */
    public List<String> getErrors() {
      return Collections.unmodifiableList(errors);
    }

    /**
     * Gets the number of successful evaluations.
     *
     * @return success count
     */
    public int getSuccessCount() {
      return results.size();
    }

    /**
     * Gets the number of failed evaluations.
     *
     * @return failure count
     */
    public int getFailureCount() {
      return errors.size();
    }

    /**
     * Gets the best concept by overall score.
     *
     * @return best concept KPIs, or null if no results
     */
    public ConceptKPIs getBestConcept() {
      return results.stream().max(Comparator.comparingDouble(ConceptKPIs::getOverallScore))
          .orElse(null);
    }

    /**
     * Gets the best concept by economic score.
     *
     * @return best economic concept
     */
    public ConceptKPIs getBestEconomicConcept() {
      return results.stream().max(Comparator.comparingDouble(ConceptKPIs::getEconomicScore))
          .orElse(null);
    }

    /**
     * Gets the best concept by environmental score.
     *
     * @return best environmental concept
     */
    public ConceptKPIs getBestEnvironmentalConcept() {
      return results.stream().max(Comparator.comparingDouble(ConceptKPIs::getEnvironmentalScore))
          .orElse(null);
    }

    /**
     * Gets the lowest CAPEX concept.
     *
     * @return lowest CAPEX concept
     */
    public ConceptKPIs getLowestCapexConcept() {
      return results.stream().min(Comparator.comparingDouble(ConceptKPIs::getTotalCapexMUSD))
          .orElse(null);
    }

    /**
     * Gets the lowest emissions concept.
     *
     * @return lowest emissions concept
     */
    public ConceptKPIs getLowestEmissionsConcept() {
      return results.stream().min(Comparator.comparingDouble(ConceptKPIs::getCo2IntensityKgPerBoe))
          .orElse(null);
    }

    /**
     * Gets results sorted by overall score (best first).
     *
     * @return sorted list
     */
    public List<ConceptKPIs> getRankedResults() {
      List<ConceptKPIs> sorted = new ArrayList<>(results);
      sorted.sort(Comparator.comparingDouble(ConceptKPIs::getOverallScore).reversed());
      return sorted;
    }

    /**
     * Gets concepts without blocking issues.
     *
     * @return viable concepts
     */
    public List<ConceptKPIs> getViableConcepts() {
      List<ConceptKPIs> viable = new ArrayList<>();
      for (ConceptKPIs kpi : results) {
        if (!kpi.hasBlockingIssues()) {
          viable.add(kpi);
        }
      }
      return viable;
    }

    /**
     * Gets a comparison summary of all concepts.
     *
     * @return comparison table string
     */
    public String getComparisonSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("CONCEPT COMPARISON SUMMARY\n");
      sb.append(StringUtils.repeat("=", 80)).append("\n");
      sb.append(String.format("%-25s %10s %10s %12s %8s %8s\n", "Concept", "CAPEX(M$)",
          "CO2(kg/boe)", "FlowAssur", "Safety", "Score"));
      sb.append(StringUtils.repeat("-", 80)).append("\n");

      for (ConceptKPIs kpi : getRankedResults()) {
        sb.append(String.format("%-25s %10.0f %10.1f %12s %8s %7.0f%%\n",
            truncate(kpi.getConceptName(), 25), kpi.getTotalCapexMUSD(),
            kpi.getCo2IntensityKgPerBoe(), kpi.getFlowAssuranceOverall().getDisplayName(),
            kpi.getSafetyLevel().getDisplayName(), kpi.getOverallScore() * 100));
      }

      sb.append(StringUtils.repeat("-", 80)).append("\n");
      ConceptKPIs best = getBestConcept();
      if (best != null) {
        sb.append("RECOMMENDED: ").append(best.getConceptName());
        sb.append(" (Score: ").append(String.format("%.0f%%", best.getOverallScore() * 100))
            .append(")\n");
      }

      return sb.toString();
    }

    private String truncate(String s, int maxLen) {
      if (s.length() <= maxLen) {
        return s;
      }
      return s.substring(0, maxLen - 3) + "...";
    }

    @Override
    public String toString() {
      return String.format("BatchResults[%d concepts, %d errors, best=%s]", results.size(),
          errors.size(), getBestConcept() != null ? getBestConcept().getConceptName() : "none");
    }
  }
}
