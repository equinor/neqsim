package neqsim.process.util.topology;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.failure.EquipmentFailureMode;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionImpactAnalyzer;
import neqsim.process.util.optimizer.ProductionImpactResult;

/**
 * Analyzes dependencies between equipment and their impact on each other.
 *
 * <p>
 * Answers questions like:
 * </p>
 * <ul>
 * <li>"If pump A fails, what else becomes critical?"</li>
 * <li>"Which equipment should we monitor when we see a weakness in compressor B?"</li>
 * <li>"What's the cascade effect of a separator trip?"</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DependencyAnalyzer implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(DependencyAnalyzer.class);

  private ProcessSystem processSystem;
  private ProcessTopologyAnalyzer topologyAnalyzer;
  private ProductionImpactAnalyzer impactAnalyzer;

  // Cross-installation dependencies (for multi-platform analysis)
  private Map<String, List<CrossInstallationDependency>> crossDependencies;

  /**
   * Represents a dependency between equipment on different installations.
   */
  public static class CrossInstallationDependency implements Serializable {
    private static final long serialVersionUID = 1000L;

    private String sourceInstallation;
    private String sourceEquipment;
    private FunctionalLocation sourceLocation;

    private String targetInstallation;
    private String targetEquipment;
    private FunctionalLocation targetLocation;

    private String dependencyType; // gas_export, oil_export, utility, etc.
    private double impactFactor; // 0-1, how much does source affect target

    /**
     * Creates a cross-installation dependency.
     *
     * @param sourceEquipment source equipment name
     * @param targetEquipment target equipment name
     * @param dependencyType type of dependency
     */
    public CrossInstallationDependency(String sourceEquipment, String targetEquipment,
        String dependencyType) {
      this.sourceEquipment = sourceEquipment;
      this.targetEquipment = targetEquipment;
      this.dependencyType = dependencyType;
      this.impactFactor = 1.0;
    }

    // Getters and setters
    public String getSourceInstallation() {
      return sourceInstallation;
    }

    public void setSourceInstallation(String inst) {
      this.sourceInstallation = inst;
    }

    public String getSourceEquipment() {
      return sourceEquipment;
    }

    public FunctionalLocation getSourceLocation() {
      return sourceLocation;
    }

    public void setSourceLocation(FunctionalLocation loc) {
      this.sourceLocation = loc;
      if (loc != null) {
        this.sourceInstallation = loc.getInstallationName();
      }
    }

    public String getTargetInstallation() {
      return targetInstallation;
    }

    public void setTargetInstallation(String inst) {
      this.targetInstallation = inst;
    }

    public String getTargetEquipment() {
      return targetEquipment;
    }

    public FunctionalLocation getTargetLocation() {
      return targetLocation;
    }

    public void setTargetLocation(FunctionalLocation loc) {
      this.targetLocation = loc;
      if (loc != null) {
        this.targetInstallation = loc.getInstallationName();
      }
    }

    public String getDependencyType() {
      return dependencyType;
    }

    public double getImpactFactor() {
      return impactFactor;
    }

    public void setImpactFactor(double factor) {
      this.impactFactor = factor;
    }
  }

  /**
   * Result of a dependency analysis.
   */
  public static class DependencyResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private String failedEquipment;
    private FunctionalLocation failedLocation;

    private List<String> directlyAffected;
    private List<String> indirectlyAffected;
    private Map<String, Double> increasedCriticality;
    private List<String> equipmentToWatch;

    private Map<String, Double> productionImpactByEquipment;
    private double totalProductionLoss;

    private List<CrossInstallationDependency> crossInstallationEffects;

    /**
     * Creates a dependency result.
     *
     * @param failedEquipment the failed equipment name
     */
    public DependencyResult(String failedEquipment) {
      this.failedEquipment = failedEquipment;
      this.directlyAffected = new ArrayList<>();
      this.indirectlyAffected = new ArrayList<>();
      this.increasedCriticality = new LinkedHashMap<>();
      this.equipmentToWatch = new ArrayList<>();
      this.productionImpactByEquipment = new LinkedHashMap<>();
      this.crossInstallationEffects = new ArrayList<>();
    }

    // Getters
    public String getFailedEquipment() {
      return failedEquipment;
    }

    public FunctionalLocation getFailedLocation() {
      return failedLocation;
    }

    public List<String> getDirectlyAffected() {
      return Collections.unmodifiableList(directlyAffected);
    }

    public List<String> getIndirectlyAffected() {
      return Collections.unmodifiableList(indirectlyAffected);
    }

    public Map<String, Double> getIncreasedCriticality() {
      return Collections.unmodifiableMap(increasedCriticality);
    }

    public List<String> getEquipmentToWatch() {
      return Collections.unmodifiableList(equipmentToWatch);
    }

    public Map<String, Double> getProductionImpactByEquipment() {
      return Collections.unmodifiableMap(productionImpactByEquipment);
    }

    public double getTotalProductionLoss() {
      return totalProductionLoss;
    }

    public List<CrossInstallationDependency> getCrossInstallationEffects() {
      return Collections.unmodifiableList(crossInstallationEffects);
    }

    // Setters (package-private)
    void setFailedLocation(FunctionalLocation loc) {
      this.failedLocation = loc;
    }

    void addDirectlyAffected(String equipment) {
      directlyAffected.add(equipment);
    }

    void addIndirectlyAffected(String equipment) {
      indirectlyAffected.add(equipment);
    }

    void setIncreasedCriticality(String equipment, double criticality) {
      increasedCriticality.put(equipment, criticality);
    }

    void addEquipmentToWatch(String equipment) {
      if (!equipmentToWatch.contains(equipment)) {
        equipmentToWatch.add(equipment);
      }
    }

    void setProductionImpact(String equipment, double impact) {
      productionImpactByEquipment.put(equipment, impact);
    }

    void setTotalProductionLoss(double loss) {
      this.totalProductionLoss = loss;
    }

    void addCrossInstallationEffect(CrossInstallationDependency dep) {
      crossInstallationEffects.add(dep);
    }

    /**
     * Exports result to JSON.
     *
     * @return JSON string
     */
    public String toJson() {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("failedEquipment", failedEquipment);
      if (failedLocation != null) {
        result.put("stidTag", failedLocation.getFullTag());
        result.put("installation", failedLocation.getInstallationName());
      }
      result.put("directlyAffected", directlyAffected);
      result.put("indirectlyAffected", indirectlyAffected);
      result.put("increasedCriticality", increasedCriticality);
      result.put("equipmentToWatch", equipmentToWatch);
      result.put("totalProductionLossPercent", totalProductionLoss);

      if (!crossInstallationEffects.isEmpty()) {
        List<Map<String, Object>> crossEffects = new ArrayList<>();
        for (CrossInstallationDependency dep : crossInstallationEffects) {
          Map<String, Object> depMap = new LinkedHashMap<>();
          depMap.put("targetInstallation", dep.getTargetInstallation());
          depMap.put("targetEquipment", dep.getTargetEquipment());
          depMap.put("dependencyType", dep.getDependencyType());
          depMap.put("impactFactor", dep.getImpactFactor());
          crossEffects.add(depMap);
        }
        result.put("crossInstallationEffects", crossEffects);
      }

      return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }
  }

  /**
   * Creates a new dependency analyzer.
   *
   * @param processSystem the process system
   */
  public DependencyAnalyzer(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.topologyAnalyzer = new ProcessTopologyAnalyzer(processSystem);
    this.impactAnalyzer = new ProductionImpactAnalyzer(processSystem);
    this.crossDependencies = new HashMap<>();
  }

  /**
   * Creates a dependency analyzer with an existing topology analyzer.
   *
   * @param processSystem the process system
   * @param topologyAnalyzer existing topology analyzer
   */
  public DependencyAnalyzer(ProcessSystem processSystem, ProcessTopologyAnalyzer topologyAnalyzer) {
    this.processSystem = processSystem;
    this.topologyAnalyzer = topologyAnalyzer;
    this.impactAnalyzer = new ProductionImpactAnalyzer(processSystem);
    this.crossDependencies = new HashMap<>();
  }

  /**
   * Initializes the analyzer (builds topology if not already built).
   */
  public void initialize() {
    if (topologyAnalyzer.getNodes().isEmpty()) {
      topologyAnalyzer.buildTopology();
    }
  }

  /**
   * Analyzes what happens when a specific equipment fails.
   *
   * @param equipmentName the equipment that fails
   * @return dependency analysis result
   */
  public DependencyResult analyzeFailure(String equipmentName) {
    initialize();
    logger.info("Analyzing failure of: {}", equipmentName);

    DependencyResult result = new DependencyResult(equipmentName);

    ProcessTopologyAnalyzer.EquipmentNode node = topologyAnalyzer.getNode(equipmentName);
    if (node != null && node.getFunctionalLocation() != null) {
      result.setFailedLocation(node.getFunctionalLocation());
    }

    // Get directly affected equipment (immediate downstream)
    List<String> downstream = topologyAnalyzer.getDownstreamEquipment(equipmentName);
    for (String eq : downstream) {
      result.addDirectlyAffected(eq);
    }

    // Get indirectly affected (further downstream)
    List<String> allAffected = topologyAnalyzer.getAffectedByFailure(equipmentName);
    for (String eq : allAffected) {
      if (!downstream.contains(eq)) {
        result.addIndirectlyAffected(eq);
      }
    }

    // Get equipment with increased criticality
    Map<String, Double> increased = topologyAnalyzer.getIncreasedCriticalityOn(equipmentName);
    for (Map.Entry<String, Double> entry : increased.entrySet()) {
      result.setIncreasedCriticality(entry.getKey(), entry.getValue());
      result.addEquipmentToWatch(entry.getKey());
    }

    // Add parallel equipment to watch list (they're now carrying full load)
    List<String> parallel = topologyAnalyzer.getParallelEquipment(equipmentName);
    for (String eq : parallel) {
      result.addEquipmentToWatch(eq);
    }

    // Simulate production impact
    try {
      ProductionImpactResult impact = impactAnalyzer.analyzeFailureImpact(equipmentName);
      result.setTotalProductionLoss(impact.getPercentLoss());
    } catch (Exception e) {
      logger.warn("Could not analyze production impact: {}", e.getMessage());
    }

    // Check cross-installation dependencies
    if (crossDependencies.containsKey(equipmentName)) {
      for (CrossInstallationDependency dep : crossDependencies.get(equipmentName)) {
        result.addCrossInstallationEffect(dep);
      }
    }

    return result;
  }

  /**
   * Gets equipment that should be monitored when weakness is detected.
   *
   * <p>
   * Answers: "I see a weakness on this equipment, what else must we watch?"
   * </p>
   *
   * @param equipmentName equipment with detected weakness
   * @return list of equipment to monitor with priority
   */
  public Map<String, String> getEquipmentToMonitor(String equipmentName) {
    initialize();

    Map<String, String> toMonitor = new LinkedHashMap<>();

    ProcessTopologyAnalyzer.EquipmentNode node = topologyAnalyzer.getNode(equipmentName);
    if (node == null) {
      return toMonitor;
    }

    // 1. Parallel equipment (HIGHEST priority - they take over if this fails)
    for (String parallel : node.getParallelEquipment()) {
      toMonitor.put(parallel, "KRITISK - Overtar last ved feil");
    }

    // 2. Immediate downstream (affected first)
    for (String downstream : node.getDownstreamEquipment()) {
      toMonitor.put(downstream, "HØY - Direkte påvirket ved feil");
    }

    // 3. Upstream equipment (if this fails, upstream may trip)
    for (String upstream : node.getUpstreamEquipment()) {
      toMonitor.put(upstream, "MEDIUM - Kan få tilbakeslag ved feil");
    }

    // 4. Cross-installation dependencies
    if (crossDependencies.containsKey(equipmentName)) {
      for (CrossInstallationDependency dep : crossDependencies.get(equipmentName)) {
        String key = dep.getTargetEquipment() + " (" + dep.getTargetInstallation() + ")";
        toMonitor.put(key, "EKSTERN - " + dep.getDependencyType());
      }
    }

    return toMonitor;
  }

  /**
   * Adds a cross-installation dependency.
   *
   * <p>
   * Example: Gas export from Gullfaks C to Åsgard processing
   * </p>
   *
   * @param sourceEquipment source equipment on this installation
   * @param targetEquipment target equipment on other installation
   * @param targetInstallation name of target installation
   * @param dependencyType type (gas_export, oil_export, utility, etc.)
   */
  public void addCrossInstallationDependency(String sourceEquipment, String targetEquipment,
      String targetInstallation, String dependencyType) {
    CrossInstallationDependency dep =
        new CrossInstallationDependency(sourceEquipment, targetEquipment, dependencyType);
    dep.setTargetInstallation(targetInstallation);

    if (!crossDependencies.containsKey(sourceEquipment)) {
      crossDependencies.put(sourceEquipment, new ArrayList<>());
    }
    crossDependencies.get(sourceEquipment).add(dep);
  }

  /**
   * Adds a cross-installation dependency with functional locations.
   *
   * @param sourceLocation source STID
   * @param targetLocation target STID
   * @param dependencyType type of dependency
   * @param impactFactor impact factor (0-1)
   */
  public void addCrossInstallationDependency(FunctionalLocation sourceLocation,
      FunctionalLocation targetLocation, String dependencyType, double impactFactor) {
    CrossInstallationDependency dep = new CrossInstallationDependency(sourceLocation.getFullTag(),
        targetLocation.getFullTag(), dependencyType);
    dep.setSourceLocation(sourceLocation);
    dep.setTargetLocation(targetLocation);
    dep.setImpactFactor(impactFactor);

    String sourceKey = sourceLocation.getFullTag();
    if (!crossDependencies.containsKey(sourceKey)) {
      crossDependencies.put(sourceKey, new ArrayList<>());
    }
    crossDependencies.get(sourceKey).add(dep);
  }

  /**
   * Finds all critical paths through the process.
   *
   * @return list of critical paths (each path is a list of equipment names)
   */
  public List<List<String>> findCriticalPaths() {
    initialize();

    List<List<String>> criticalPaths = new ArrayList<>();

    // Find start nodes (no upstream)
    Set<String> startNodes = new HashSet<>();
    for (ProcessTopologyAnalyzer.EquipmentNode node : topologyAnalyzer.getNodes().values()) {
      if (node.getUpstreamEquipment().isEmpty()) {
        startNodes.add(node.getName());
      }
    }

    // Find end nodes (no downstream)
    Set<String> endNodes = new HashSet<>();
    for (ProcessTopologyAnalyzer.EquipmentNode node : topologyAnalyzer.getNodes().values()) {
      if (node.getDownstreamEquipment().isEmpty()) {
        endNodes.add(node.getName());
      }
    }

    // DFS to find all paths
    for (String start : startNodes) {
      List<String> path = new ArrayList<>();
      findPaths(start, endNodes, path, criticalPaths);
    }

    // Sort by criticality (paths with more critical equipment first)
    criticalPaths.sort((p1, p2) -> {
      double crit1 = calculatePathCriticality(p1);
      double crit2 = calculatePathCriticality(p2);
      return Double.compare(crit2, crit1);
    });

    return criticalPaths;
  }

  private void findPaths(String current, Set<String> endNodes, List<String> path,
      List<List<String>> allPaths) {
    path.add(current);

    if (endNodes.contains(current)) {
      allPaths.add(new ArrayList<>(path));
    }

    ProcessTopologyAnalyzer.EquipmentNode node = topologyAnalyzer.getNode(current);
    if (node != null) {
      for (String downstream : node.getDownstreamEquipment()) {
        if (!path.contains(downstream)) { // Avoid cycles
          findPaths(downstream, endNodes, path, allPaths);
        }
      }
    }

    path.remove(path.size() - 1);
  }

  private double calculatePathCriticality(List<String> path) {
    double totalCriticality = 0;
    for (String equipment : path) {
      ProcessTopologyAnalyzer.EquipmentNode node = topologyAnalyzer.getNode(equipment);
      if (node != null) {
        totalCriticality += node.getCriticality();
      }
    }
    return totalCriticality;
  }

  /**
   * Gets the topology analyzer.
   *
   * @return topology analyzer
   */
  public ProcessTopologyAnalyzer getTopologyAnalyzer() {
    return topologyAnalyzer;
  }

  /**
   * Gets the impact analyzer.
   *
   * @return impact analyzer
   */
  public ProductionImpactAnalyzer getImpactAnalyzer() {
    return impactAnalyzer;
  }
}
