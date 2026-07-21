package neqsim.process.engineering.design;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, validated dependency graph for engineering design modules.
 *
 * <p>
 * Modules in the same level are independent. Their proposals are resolved together before the next level is evaluated,
 * so a dependent module sees its dependencies' selected state in the same design iteration.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class EngineeringDesignDependencyGraph implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Map<String, EngineeringDesignModule> modulesById;
  private final Map<String, List<String>> dependenciesById;
  private final List<List<EngineeringDesignModule>> orderedLevels;

  private EngineeringDesignDependencyGraph(List<EngineeringDesignModule> configuredModules) {
    List<EngineeringDesignModule> modules = configuredModules == null
        ? new ArrayList<EngineeringDesignModule>() : new ArrayList<EngineeringDesignModule>(configuredModules);
    Collections.sort(modules, new Comparator<EngineeringDesignModule>() {
      @Override
      public int compare(EngineeringDesignModule first, EngineeringDesignModule second) {
        return requireId(first).compareTo(requireId(second));
      }
    });

    Map<String, EngineeringDesignModule> moduleMap = new LinkedHashMap<String, EngineeringDesignModule>();
    for (EngineeringDesignModule module : modules) {
      String id = requireId(module);
      if (moduleMap.put(id, module) != null) {
        throw new EngineeringDesignDependencyException("Duplicate engineering design module ID " + id);
      }
    }

    Map<String, List<String>> dependencyMap = new LinkedHashMap<String, List<String>>();
    for (EngineeringDesignModule module : modules) {
      String id = requireId(module);
      List<String> dependencies = normalizedDependencies(module.getDependencies(), id);
      for (String dependency : dependencies) {
        if (!moduleMap.containsKey(dependency)) {
          throw new EngineeringDesignDependencyException(
              "Engineering design module " + id + " depends on missing module " + dependency);
        }
        if (id.equals(dependency)) {
          throw new EngineeringDesignDependencyException("Engineering design module " + id + " depends on itself");
        }
      }
      dependencyMap.put(id, Collections.unmodifiableList(dependencies));
    }

    modulesById = Collections.unmodifiableMap(moduleMap);
    dependenciesById = Collections.unmodifiableMap(dependencyMap);
    orderedLevels = buildOrderedLevels(moduleMap, dependencyMap);
  }

  /**
   * Build and validate a dependency graph.
   *
   * @param configuredModules design modules
   * @return immutable dependency graph
   */
  public static EngineeringDesignDependencyGraph of(List<EngineeringDesignModule> configuredModules) {
    return new EngineeringDesignDependencyGraph(configuredModules);
  }

  /** @return module IDs in deterministic topological order */
  public List<String> getOrderedModuleIds() {
    List<String> result = new ArrayList<String>();
    for (List<EngineeringDesignModule> level : orderedLevels) {
      for (EngineeringDesignModule module : level) {
        result.add(module.getId());
      }
    }
    return Collections.unmodifiableList(result);
  }

  /** @return defensive dependency map keyed by module ID */
  public Map<String, List<String>> getDependenciesById() {
    return dependenciesById;
  }

  /**
   * Get a configured module by ID.
   *
   * @param moduleId module ID
   * @return module, or {@code null} when absent
   */
  public EngineeringDesignModule getModule(String moduleId) {
    return modulesById.get(moduleId);
  }

  /** @return immutable topological levels */
  List<List<EngineeringDesignModule>> getOrderedLevels() {
    return orderedLevels;
  }

  private static List<List<EngineeringDesignModule>> buildOrderedLevels(
      Map<String, EngineeringDesignModule> modules, Map<String, List<String>> dependencies) {
    Set<String> remaining = new LinkedHashSet<String>(modules.keySet());
    Set<String> completed = new LinkedHashSet<String>();
    List<List<EngineeringDesignModule>> levels = new ArrayList<List<EngineeringDesignModule>>();
    while (!remaining.isEmpty()) {
      List<String> ready = new ArrayList<String>();
      for (String id : remaining) {
        if (completed.containsAll(dependencies.get(id))) {
          ready.add(id);
        }
      }
      Collections.sort(ready);
      if (ready.isEmpty()) {
        throw new EngineeringDesignDependencyException(
            "Cycle detected among engineering design modules " + remaining);
      }
      List<EngineeringDesignModule> level = new ArrayList<EngineeringDesignModule>();
      for (String id : ready) {
        level.add(modules.get(id));
        remaining.remove(id);
        completed.add(id);
      }
      levels.add(Collections.unmodifiableList(level));
    }
    return Collections.unmodifiableList(levels);
  }

  private static List<String> normalizedDependencies(List<String> configured, String moduleId) {
    if (configured == null) {
      throw new EngineeringDesignDependencyException(
          "Engineering design module " + moduleId + " returned null dependencies");
    }
    Set<String> unique = new LinkedHashSet<String>();
    for (String dependency : configured) {
      if (dependency == null || dependency.trim().isEmpty()) {
        throw new EngineeringDesignDependencyException(
            "Engineering design module " + moduleId + " has a blank dependency ID");
      }
      unique.add(dependency.trim());
    }
    List<String> result = new ArrayList<String>(unique);
    Collections.sort(result);
    return result;
  }

  private static String requireId(EngineeringDesignModule module) {
    if (module == null || module.getId() == null || module.getId().trim().isEmpty()) {
      throw new EngineeringDesignDependencyException("Engineering design modules must have non-blank IDs");
    }
    return module.getId().trim();
  }
}
