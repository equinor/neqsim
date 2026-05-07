package neqsim.process.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P-graph-style material-operation graph for process synthesis.
 *
 * <p>
 * Materials are graph nodes and {@link OperationOption} instances are operation hyperedges. This
 * class enumerates bounded operation paths from a feed material to one or more product targets. It
 * is deliberately lightweight: rigorous thermodynamics, convergence, and ranking remain in NeqSim
 * process simulation, while the graph supplies systematic candidate topology generation.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessSynthesisGraph {
  private final Map<String, MaterialNode> materials = new LinkedHashMap<String, MaterialNode>();
  private final List<OperationOption> operations = new ArrayList<OperationOption>();

  /**
   * Creates an empty synthesis graph.
   */
  public ProcessSynthesisGraph() {}

  /**
   * Creates a synthesis graph from a process research specification.
   *
   * @param spec process research specification
   * @return populated synthesis graph
   */
  public static ProcessSynthesisGraph fromSpec(ProcessResearchSpec spec) {
    ProcessSynthesisGraph graph = new ProcessSynthesisGraph();
    graph.addMaterial(new MaterialNode(spec.getFeedMaterialName(), "feed material", null));
    for (MaterialNode node : spec.getMaterialNodes()) {
      graph.addMaterial(node);
    }
    for (OperationOption option : spec.getOperationOptions()) {
      graph.addOperation(option);
    }
    return graph;
  }

  /**
   * Adds a material node.
   *
   * @param material material node to add
   * @return this graph
   */
  public ProcessSynthesisGraph addMaterial(MaterialNode material) {
    materials.put(normalize(material.getName()), material);
    return this;
  }

  /**
   * Adds an operation option.
   *
   * @param operation operation option to add
   * @return this graph
   */
  public ProcessSynthesisGraph addOperation(OperationOption operation) {
    operations.add(operation);
    for (String input : operation.getInputMaterials()) {
      ensureMaterial(input);
    }
    for (String output : operation.getOutputMaterials()) {
      ensureMaterial(output);
    }
    return this;
  }

  /**
   * Gets material nodes.
   *
   * @return unmodifiable material map keyed by normalized material name
   */
  public Map<String, MaterialNode> getMaterials() {
    return Collections.unmodifiableMap(materials);
  }

  /**
   * Gets operation options.
   *
   * @return unmodifiable operation list
   */
  public List<OperationOption> getOperations() {
    return Collections.unmodifiableList(operations);
  }

  /**
   * Enumerates operation paths from feed to any target material.
   *
   * @param feedMaterialName feed material name
   * @param targetMaterials target material names
   * @param maxDepth maximum number of operations per path
   * @param maxPaths maximum number of paths to return
   * @return operation paths in discovery order
   */
  public List<List<OperationOption>> enumeratePaths(String feedMaterialName,
      List<String> targetMaterials, int maxDepth, int maxPaths) {
    List<List<OperationOption>> paths = new ArrayList<List<OperationOption>>();
    Set<String> initialMaterials = new LinkedHashSet<String>();
    initialMaterials.add(normalize(feedMaterialName));
    Set<String> normalizedTargets = normalizeAll(targetMaterials);
    enumerateRecursive(initialMaterials, normalizedTargets, new ArrayList<OperationOption>(),
        new LinkedHashSet<OperationOption>(), Math.max(1, maxDepth), Math.max(1, maxPaths), paths);
    return paths;
  }

  /**
   * Recursively enumerates operation paths.
   *
   * @param availableMaterials currently available material names
   * @param targetMaterials normalized target material names
   * @param currentPath current operation path
   * @param usedOperations operations already used in this path
   * @param maxDepth maximum path depth
   * @param maxPaths maximum path count
   * @param paths accumulated paths
   */
  private void enumerateRecursive(Set<String> availableMaterials, Set<String> targetMaterials,
      List<OperationOption> currentPath, Set<OperationOption> usedOperations, int maxDepth,
      int maxPaths, List<List<OperationOption>> paths) {
    if (paths.size() >= maxPaths) {
      return;
    }
    if (!currentPath.isEmpty() && containsAny(availableMaterials, targetMaterials)) {
      paths.add(new ArrayList<OperationOption>(currentPath));
      return;
    }
    if (currentPath.size() >= maxDepth) {
      return;
    }
    for (OperationOption operation : operations) {
      if (usedOperations.contains(operation) || !inputsAvailable(operation, availableMaterials)) {
        continue;
      }
      Set<String> nextMaterials = new LinkedHashSet<String>(availableMaterials);
      for (String output : operation.getOutputMaterials()) {
        nextMaterials.add(normalize(output));
      }
      currentPath.add(operation);
      usedOperations.add(operation);
      enumerateRecursive(nextMaterials, targetMaterials, currentPath, usedOperations, maxDepth,
          maxPaths, paths);
      usedOperations.remove(operation);
      currentPath.remove(currentPath.size() - 1);
    }
  }

  /**
   * Checks whether operation inputs are available.
   *
   * @param operation operation to inspect
   * @param availableMaterials available material set
   * @return true if all declared inputs are available
   */
  private boolean inputsAvailable(OperationOption operation, Set<String> availableMaterials) {
    if (operation.getInputMaterials().isEmpty()) {
      return false;
    }
    for (String input : operation.getInputMaterials()) {
      if (!availableMaterials.contains(normalize(input))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks for set intersection.
   *
   * @param left first set
   * @param right second set
   * @return true if the sets share at least one item
   */
  private boolean containsAny(Set<String> left, Set<String> right) {
    for (String value : right) {
      if (left.contains(value)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Ensures a material node exists.
   *
   * @param materialName material name
   */
  private void ensureMaterial(String materialName) {
    String key = normalize(materialName);
    if (!materials.containsKey(key)) {
      materials.put(key, new MaterialNode(materialName, "generated from operation option", null));
    }
  }

  /**
   * Normalizes a collection of material names.
   *
   * @param values material names
   * @return normalized material names
   */
  private Set<String> normalizeAll(List<String> values) {
    Set<String> normalized = new LinkedHashSet<String>();
    for (String value : values) {
      if (value != null && !value.trim().isEmpty()) {
        normalized.add(normalize(value));
      }
    }
    return normalized;
  }

  /**
   * Normalizes a material name.
   *
   * @param value material name
   * @return normalized material name
   */
  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }
}
