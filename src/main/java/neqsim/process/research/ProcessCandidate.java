package neqsim.process.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Generated process candidate with simulation and ranking metadata.
 *
 * <p>
 * A candidate keeps the generated NeqSim JSON definition, product stream references, generation
 * provenance, simulation warnings/errors, objective values, and the optional built process system
 * for downstream inspection.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessCandidate {
  private final String id;
  private final String name;
  private final String generationMethod;
  private String description = "";
  private String jsonDefinition;
  private ProcessSystem processSystem;
  private boolean feasible;
  private boolean optimized;
  private boolean dominated;
  private String dominanceReason = "";
  private double score = Double.NaN;
  private ProcessResearchMetrics metrics = new ProcessResearchMetrics();
  private final Map<String, String> productStreamReferences = new LinkedHashMap<>();
  private final Map<String, Double> objectiveValues = new LinkedHashMap<>();
  private final List<String> synthesisPath = new ArrayList<>();
  private final List<String> assumptions = new ArrayList<>();
  private final List<String> warnings = new ArrayList<>();
  private final List<String> errors = new ArrayList<>();

  /**
   * Creates a process candidate.
   *
   * @param id stable candidate identifier
   * @param name human-readable candidate name
   * @param generationMethod method that generated the candidate
   */
  public ProcessCandidate(String id, String name, String generationMethod) {
    this.id = id;
    this.name = name;
    this.generationMethod = generationMethod;
  }

  /**
   * Sets the candidate description.
   *
   * @param description description text
   * @return this candidate
   */
  public ProcessCandidate setDescription(String description) {
    this.description = description == null ? "" : description;
    return this;
  }

  /**
   * Sets the JSON process definition.
   *
   * @param jsonDefinition NeqSim JSON process definition
   * @return this candidate
   */
  public ProcessCandidate setJsonDefinition(String jsonDefinition) {
    this.jsonDefinition = jsonDefinition;
    return this;
  }

  /**
   * Sets the built process system.
   *
   * @param processSystem built process system
   */
  public void setProcessSystem(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Sets whether the candidate is feasible.
   *
   * @param feasible true if the candidate simulated and satisfied hard checks
   */
  public void setFeasible(boolean feasible) {
    this.feasible = feasible;
  }

  /**
   * Sets whether the candidate was optimized.
   *
   * @param optimized true if a decision-variable search was run
   */
  public void setOptimized(boolean optimized) {
    this.optimized = optimized;
  }

  /**
   * Sets whether the candidate is dominated by another candidate.
   *
   * @param dominated true if another candidate is at least as good and simpler or cleaner
   */
  public void setDominated(boolean dominated) {
    this.dominated = dominated;
  }

  /**
   * Sets the dominance reason.
   *
   * @param dominanceReason reason for marking the candidate dominated
   */
  public void setDominanceReason(String dominanceReason) {
    this.dominanceReason = dominanceReason == null ? "" : dominanceReason;
  }

  /**
   * Sets the ranking score.
   *
   * @param score ranking score; higher is better
   */
  public void setScore(double score) {
    this.score = score;
  }

  /**
   * Sets structured process research metrics.
   *
   * @param metrics candidate metrics
   */
  public void setMetrics(ProcessResearchMetrics metrics) {
    this.metrics = metrics == null ? new ProcessResearchMetrics() : metrics;
  }

  /**
   * Adds a product stream reference.
   *
   * @param role product role, e.g. gas, liquid, or product
   * @param streamReference dot-notation stream reference
   * @return this candidate
   */
  public ProcessCandidate addProductStreamReference(String role, String streamReference) {
    productStreamReferences.put(role, streamReference);
    return this;
  }

  /**
   * Adds an objective value.
   *
   * @param name objective name
   * @param value objective value
   */
  public void addObjectiveValue(String name, double value) {
    objectiveValues.put(name, value);
  }

  /**
   * Adds a synthesis path step.
   *
   * @param step path step description
   * @return this candidate
   */
  public ProcessCandidate addSynthesisPathStep(String step) {
    synthesisPath.add(step);
    return this;
  }

  /**
   * Adds an assumption.
   *
   * @param assumption assumption text
   * @return this candidate
   */
  public ProcessCandidate addAssumption(String assumption) {
    assumptions.add(assumption);
    return this;
  }

  /**
   * Adds a warning.
   *
   * @param warning warning text
   */
  public void addWarning(String warning) {
    warnings.add(warning);
  }

  /**
   * Adds an error.
   *
   * @param error error text
   */
  public void addError(String error) {
    errors.add(error);
  }

  /**
   * Gets the candidate id.
   *
   * @return candidate id
   */
  public String getId() {
    return id;
  }

  /**
   * Gets the candidate name.
   *
   * @return candidate name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the generation method.
   *
   * @return generation method
   */
  public String getGenerationMethod() {
    return generationMethod;
  }

  /**
   * Gets the candidate description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Gets the JSON process definition.
   *
   * @return JSON process definition
   */
  public String getJsonDefinition() {
    return jsonDefinition;
  }

  /**
   * Gets the built process system.
   *
   * @return process system, or null if not evaluated
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Returns whether the candidate is feasible.
   *
   * @return true if feasible
   */
  public boolean isFeasible() {
    return feasible;
  }

  /**
   * Returns whether the candidate was optimized.
   *
   * @return true if optimized
   */
  public boolean isOptimized() {
    return optimized;
  }

  /**
   * Returns whether this candidate is dominated.
   *
   * @return true if dominated by another candidate
   */
  public boolean isDominated() {
    return dominated;
  }

  /**
   * Gets the dominance reason.
   *
   * @return dominance reason, possibly empty
   */
  public String getDominanceReason() {
    return dominanceReason;
  }

  /**
   * Gets the ranking score.
   *
   * @return score; higher is better
   */
  public double getScore() {
    return score;
  }

  /**
   * Gets structured process research metrics.
   *
   * @return process research metrics
   */
  public ProcessResearchMetrics getMetrics() {
    return metrics;
  }

  /**
   * Gets product stream references.
   *
   * @return unmodifiable role-to-reference map
   */
  public Map<String, String> getProductStreamReferences() {
    return Collections.unmodifiableMap(productStreamReferences);
  }

  /**
   * Gets a product stream reference by role.
   *
   * @param role product role
   * @return stream reference, or null
   */
  public String getProductStreamReference(String role) {
    return productStreamReferences.get(role);
  }

  /**
   * Gets objective values.
   *
   * @return unmodifiable objective map
   */
  public Map<String, Double> getObjectiveValues() {
    return Collections.unmodifiableMap(objectiveValues);
  }

  /**
   * Gets the process synthesis path.
   *
   * @return unmodifiable synthesis path
   */
  public List<String> getSynthesisPath() {
    return Collections.unmodifiableList(synthesisPath);
  }

  /**
   * Gets assumptions.
   *
   * @return unmodifiable assumption list
   */
  public List<String> getAssumptions() {
    return Collections.unmodifiableList(assumptions);
  }

  /**
   * Gets warnings.
   *
   * @return unmodifiable warning list
   */
  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  /**
   * Gets errors.
   *
   * @return unmodifiable error list
   */
  public List<String> getErrors() {
    return Collections.unmodifiableList(errors);
  }
}
