package neqsim.process.engineering.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;

/** Validated standards-aware dependency graph and deterministic execution plan for engineering calculations. */
public final class EngineeringCalculationDag implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Readiness state derived without executing calculation code. */
  public enum Readiness {
    COMPLETE, READY, BLOCKED_BY_DEPENDENCY, MISSING_STANDARD_REFERENCE, BLOCKED, FAILED
  }

  private final Map<String, EngineeringCalculation> calculations;
  private final List<String> topologicalOrder;

  private EngineeringCalculationDag(Map<String, EngineeringCalculation> calculations, List<String> topologicalOrder) {
    this.calculations = calculations;
    this.topologicalOrder = topologicalOrder;
  }

  /** Builds a validated DAG, rejecting unknown prerequisites and cycles. */
  public static EngineeringCalculationDag from(List<EngineeringCalculation> values) {
    if (values == null) {
      throw new IllegalArgumentException("calculations must not be null");
    }
    Map<String, EngineeringCalculation> calculations = new LinkedHashMap<String, EngineeringCalculation>();
    for (EngineeringCalculation calculation : values) {
      if (calculation == null) {
        throw new IllegalArgumentException("calculations must not contain null values");
      }
      if (calculations.put(calculation.getId(), calculation) != null) {
        throw new IllegalArgumentException("Duplicate engineering calculation " + calculation.getId());
      }
    }
    Map<String, Integer> dependencyCount = new LinkedHashMap<String, Integer>();
    Map<String, List<String>> dependents = new LinkedHashMap<String, List<String>>();
    for (EngineeringCalculation calculation : calculations.values()) {
      dependencyCount.put(calculation.getId(), Integer.valueOf(calculation.getPrerequisiteCalculationIds().size()));
      dependents.put(calculation.getId(), new ArrayList<String>());
      for (String prerequisite : calculation.getPrerequisiteCalculationIds()) {
        if (!calculations.containsKey(prerequisite)) {
          throw new IllegalArgumentException(
              "Calculation " + calculation.getId() + " depends on unknown calculation " + prerequisite);
        }
      }
    }
    for (EngineeringCalculation calculation : calculations.values()) {
      for (String prerequisite : calculation.getPrerequisiteCalculationIds()) {
        dependents.get(prerequisite).add(calculation.getId());
      }
    }
    List<String> ready = new ArrayList<String>();
    for (Map.Entry<String, Integer> entry : dependencyCount.entrySet()) {
      if (entry.getValue().intValue() == 0) {
        ready.add(entry.getKey());
      }
    }
    List<String> order = new ArrayList<String>();
    int cursor = 0;
    while (cursor < ready.size()) {
      String id = ready.get(cursor++);
      order.add(id);
      for (String dependent : dependents.get(id)) {
        int remaining = dependencyCount.get(dependent).intValue() - 1;
        dependencyCount.put(dependent, Integer.valueOf(remaining));
        if (remaining == 0) {
          ready.add(dependent);
        }
      }
    }
    if (order.size() != calculations.size()) {
      Set<String> cyclic = new LinkedHashSet<String>(calculations.keySet());
      cyclic.removeAll(order);
      throw new IllegalArgumentException("Engineering calculation dependency cycle: " + cyclic);
    }
    return new EngineeringCalculationDag(Collections.unmodifiableMap(calculations),
        Collections.unmodifiableList(order));
  }

  public List<String> getTopologicalOrder() {
    return topologicalOrder;
  }

  public List<EngineeringCalculation> getCalculationsInExecutionOrder() {
    List<EngineeringCalculation> result = new ArrayList<EngineeringCalculation>();
    for (String id : topologicalOrder) {
      result.add(calculations.get(id));
    }
    return Collections.unmodifiableList(result);
  }

  public Readiness getReadiness(String calculationId) {
    EngineeringCalculation calculation = calculations.get(calculationId);
    if (calculation == null) {
      throw new IllegalArgumentException("Unknown engineering calculation " + calculationId);
    }
    if (calculation.getStatus() == EngineeringCalculation.Status.FAILED) {
      return Readiness.FAILED;
    }
    if (calculation.getStatus() == EngineeringCalculation.Status.BLOCKED) {
      return Readiness.BLOCKED;
    }
    if (calculation.isStandardsRequired() && !calculation.hasStandardsBasis()) {
      return Readiness.MISSING_STANDARD_REFERENCE;
    }
    if (calculation.getStatus() == EngineeringCalculation.Status.CALCULATED
        || calculation.getStatus() == EngineeringCalculation.Status.APPROVED) {
      return Readiness.COMPLETE;
    }
    for (String prerequisiteId : calculation.getPrerequisiteCalculationIds()) {
      EngineeringCalculation prerequisite = calculations.get(prerequisiteId);
      if (prerequisite.getStatus() != EngineeringCalculation.Status.CALCULATED
          && prerequisite.getStatus() != EngineeringCalculation.Status.APPROVED) {
        return Readiness.BLOCKED_BY_DEPENDENCY;
      }
    }
    return Readiness.READY;
  }

  /** Creates the versioned compiler artifact for this execution plan. */
  public Map<String, Object> toMap(String projectId, String revision) {
    Map<String, Object> document = new LinkedHashMap<String, Object>();
    document.put("schemaVersion", EngineeringSchemaCatalog.CALCULATION_DAG);
    document.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.CALCULATION_DAG));
    document.put("projectId", requireText(projectId, "projectId"));
    document.put("revision", requireText(revision, "revision"));
    document.put("topologicalOrder", new ArrayList<String>(topologicalOrder));
    List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
    List<Map<String, Object>> edges = new ArrayList<Map<String, Object>>();
    Map<String, Integer> readinessCounts = new LinkedHashMap<String, Integer>();
    for (String id : topologicalOrder) {
      EngineeringCalculation calculation = calculations.get(id);
      Map<String, Object> node = new LinkedHashMap<String, Object>(calculation.toMap());
      Readiness readiness = getReadiness(id);
      node.put("executionReadiness", readiness.name());
      nodes.add(node);
      Integer count = readinessCounts.get(readiness.name());
      readinessCounts.put(readiness.name(), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
      for (String prerequisite : calculation.getPrerequisiteCalculationIds()) {
        Map<String, Object> edge = new LinkedHashMap<String, Object>();
        edge.put("sourceCalculationId", id);
        edge.put("targetCalculationId", prerequisite);
        edge.put("kind", "DEPENDS_ON");
        edges.add(edge);
      }
    }
    document.put("nodes", nodes);
    document.put("edges", edges);
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("calculationCount", Integer.valueOf(nodes.size()));
    summary.put("dependencyCount", Integer.valueOf(edges.size()));
    summary.put("readinessCounts", readinessCounts);
    document.put("summary", summary);
    return document;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
