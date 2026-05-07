package neqsim.process.research;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fast pre-simulation feasibility checks for process synthesis candidates.
 *
 * <p>
 * These rules catch structurally impossible or weak candidates before rigorous NeqSim simulation.
 * They are intentionally conservative: a rule should reject only candidates that violate basic
 * synthesis logic such as missing feed components, impossible target purity bounds, missing graph
 * inputs, or reaction routes with absent reactants.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessSynthesisFeasibilityPruner {

  /**
   * Creates a feasibility pruner.
   */
  public ProcessSynthesisFeasibilityPruner() {}

  /**
   * Validates a process research specification.
   *
   * @param spec process research specification
   * @return validation messages; empty when no issue is found
   */
  public List<String> validateSpec(ProcessResearchSpec spec) {
    List<String> messages = new ArrayList<String>();
    double total = 0.0;
    for (Map.Entry<String, Double> entry : spec.getFeedComponents().entrySet()) {
      if (entry.getValue() == null || entry.getValue().doubleValue() < 0.0) {
        messages.add("Feed component has negative amount: " + entry.getKey());
      } else {
        total += entry.getValue().doubleValue();
      }
    }
    if (total <= 0.0) {
      messages.add("Total feed component amount must be positive");
    }
    if (spec.getFeedTemperatureK() <= 0.0) {
      messages.add("Feed temperature must be above 0 K");
    }
    if (spec.getFeedPressureBara() <= 0.0) {
      messages.add("Feed pressure must be positive");
    }
    for (ProcessResearchSpec.ProductTarget target : spec.getProductTargets()) {
      if (target.getMinPurity() < 0.0 || target.getMinPurity() > 1.0) {
        messages.add("Product target purity must be between 0 and 1: " + target.getName());
      }
      if (target.getMinFlowRate() < 0.0) {
        messages.add("Product target flow must be non-negative: " + target.getName());
      }
      if (target.getComponentName() != null && !target.getComponentName().trim().isEmpty()
          && !spec.getFeedComponents().containsKey(target.getComponentName())
          && !reactionProducesComponent(target.getComponentName(), spec)) {
        messages.add("Product target component is absent from feed and reaction products: "
            + target.getComponentName());
      }
    }
    return messages;
  }

  /**
   * Checks whether a reaction option passes basic synthesis rules.
   *
   * @param reaction reaction option to inspect
   * @param spec process research specification
   * @return feasibility result
   */
  public FeasibilityResult checkReaction(ReactionOption reaction, ProcessResearchSpec spec) {
    FeasibilityResult result = new FeasibilityResult();
    if (!spec.allowsUnitType(reaction.getReactorType())) {
      result.addIssue("Reactor type is not allowed: " + reaction.getReactorType());
    }
    for (Map.Entry<String, Double> entry : reaction.getStoichiometry().entrySet()) {
      if (entry.getValue().doubleValue() < 0.0
          && !spec.getFeedComponents().containsKey(entry.getKey())) {
        result.addIssue("Reaction reactant is not present in feed: " + entry.getKey());
      }
    }
    if (reaction.getExpectedProductComponent() != null
        && !reaction.getExpectedProductComponent().trim().isEmpty()
        && !spec.getFeedComponents().containsKey(reaction.getExpectedProductComponent())
        && !reaction.getStoichiometry().containsKey(reaction.getExpectedProductComponent())) {
      result.addIssue("Expected reaction product is not in feed or stoichiometry: "
          + reaction.getExpectedProductComponent());
    }
    if (!Double.isNaN(reaction.getReactorTemperatureK())
        && reaction.getReactorTemperatureK() <= 0.0) {
      result.addIssue("Reaction temperature must be above 0 K");
    }
    if (!Double.isNaN(reaction.getReactorPressureBara())
        && reaction.getReactorPressureBara() <= 0.0) {
      result.addIssue("Reaction pressure must be positive");
    }
    return result;
  }

  /**
   * Checks whether an operation path is structurally valid.
   *
   * @param path operation path to inspect
   * @param spec process research specification
   * @return feasibility result
   */
  public FeasibilityResult checkOperationPath(List<OperationOption> path,
      ProcessResearchSpec spec) {
    FeasibilityResult result = new FeasibilityResult();
    if (path == null || path.isEmpty()) {
      result.addIssue("Operation path is empty");
      return result;
    }
    Set<String> availableMaterials = new LinkedHashSet<String>();
    availableMaterials.add(normalize(spec.getFeedMaterialName()));
    for (OperationOption option : path) {
      if (!spec.allowsUnitType(option.getEquipmentType())) {
        result.addIssue("Operation uses a disallowed equipment type: " + option.getEquipmentType());
      }
      if (option.getInputMaterials().isEmpty()) {
        result.addIssue("Operation has no declared input material: " + option.getName());
      }
      for (String input : option.getInputMaterials()) {
        if (!availableMaterials.contains(normalize(input))) {
          result.addIssue("Operation input material is not available before " + option.getName()
              + ": " + input);
        }
      }
      if (option.getOutputMaterials().isEmpty()) {
        result.addIssue("Operation has no declared output material: " + option.getName());
      }
      for (String output : option.getOutputMaterials()) {
        availableMaterials.add(normalize(output));
      }
    }
    for (ProcessResearchSpec.ProductTarget target : spec.getProductTargets()) {
      if (target.getMaterialName() != null && !target.getMaterialName().trim().isEmpty()
          && !availableMaterials.contains(normalize(target.getMaterialName()))) {
        result.addIssue(
            "Operation path does not produce target material: " + target.getMaterialName());
      }
    }
    return result;
  }

  /**
   * Checks if any reaction option produces a component.
   *
   * @param componentName component name to find
   * @param spec process research specification
   * @return true if a reaction produces the component
   */
  private boolean reactionProducesComponent(String componentName, ProcessResearchSpec spec) {
    for (ReactionOption reaction : spec.getReactionOptions()) {
      Double coefficient = reaction.getStoichiometry().get(componentName);
      if (coefficient != null && coefficient.doubleValue() > 0.0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Normalizes material names for graph-continuity checks.
   *
   * @param value material name
   * @return normalized material name
   */
  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  /**
   * Result from a feasibility check.
   */
  public static class FeasibilityResult {
    private final List<String> issues = new ArrayList<String>();

    /**
     * Creates an empty feasibility result.
     */
    public FeasibilityResult() {}

    /**
     * Adds an issue.
     *
     * @param issue issue description
     */
    public void addIssue(String issue) {
      issues.add(issue);
    }

    /**
     * Returns whether the checked item is feasible.
     *
     * @return true when no issues were found
     */
    public boolean isFeasible() {
      return issues.isEmpty();
    }

    /**
     * Gets feasibility issues.
     *
     * @return issue list
     */
    public List<String> getIssues() {
      return java.util.Collections.unmodifiableList(issues);
    }
  }
}
