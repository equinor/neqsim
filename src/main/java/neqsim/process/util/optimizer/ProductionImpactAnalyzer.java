package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.failure.EquipmentFailureMode;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionImpactResult.RecommendedAction;

/**
 * Analyzer for assessing production impact of equipment failures.
 *
 * <p>
 * This class provides comprehensive analysis of how equipment failures affect production rates and
 * helps determine optimal operating strategies during degraded conditions.
 * </p>
 *
 * <h2>Key Capabilities</h2>
 * <ul>
 * <li>Analyze impact of single equipment failure</li>
 * <li>Compare degraded operation vs. full plant shutdown</li>
 * <li>Find optimized operating point with failed equipment</li>
 * <li>Rank equipment by criticality</li>
 * <li>Identify bottleneck shifts</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * {@code
 * // Create analyzer
 * ProductionImpactAnalyzer analyzer = new ProductionImpactAnalyzer(processSystem);
 * analyzer.setFeedStreamName("Well Feed");
 * analyzer.setProductStreamName("Export Gas");
 *
 * // Analyze compressor failure
 * ProductionImpactResult result = analyzer.analyzeFailureImpact("HP Compressor");
 * System.out.println("Production loss: " + result.getPercentLoss() + "%");
 * System.out.println("Recommendation: " + result.getRecommendedAction());
 *
 * // Get criticality ranking
 * List<ProductionImpactResult> ranking = analyzer.rankEquipmentByCriticality();
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProductionImpactAnalyzer implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(ProductionImpactAnalyzer.class);

  /** The process system to analyze. */
  private ProcessSystem processSystem;

  /** Name of the feed stream for flow rate calculations. */
  private String feedStreamName;

  /** Name of the product/outlet stream for production measurement. */
  private String productStreamName;

  /** Product price for economic calculations ($/kg). */
  private double productPricePerKg = 0.0;

  /** Whether to include optimization of degraded operation. */
  private boolean optimizeDegradedOperation = true;

  /** Tolerance for flow rate optimization. */
  private double optimizationTolerance = 0.01;

  /** Maximum iterations for optimization. */
  private int maxOptimizationIterations = 50;

  /** Cache for baseline values. */
  private transient Double cachedBaselineProduction = null;
  private transient Double cachedBaselinePower = null;
  private transient String cachedBaselineBottleneck = null;
  private transient Double cachedBaselineBottleneckUtil = null;

  /**
   * Creates a production impact analyzer for the given process system.
   *
   * @param processSystem the process system to analyze
   */
  public ProductionImpactAnalyzer(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    autoDetectStreams();
  }

  /**
   * Creates a production impact analyzer with specified streams.
   *
   * @param processSystem the process system
   * @param feedStreamName name of the feed stream
   * @param productStreamName name of the product stream
   */
  public ProductionImpactAnalyzer(ProcessSystem processSystem, String feedStreamName,
      String productStreamName) {
    this.processSystem = processSystem;
    this.feedStreamName = feedStreamName;
    this.productStreamName = productStreamName;
  }

  /**
   * Auto-detects feed and product streams from the process system.
   */
  private void autoDetectStreams() {
    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();

    // Find first and last stream-like equipment
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof StreamInterface) {
        if (feedStreamName == null) {
          feedStreamName = unit.getName();
        }
        productStreamName = unit.getName(); // Last stream becomes product
      }
    }

    logger.debug("Auto-detected feed stream: {}, product stream: {}", feedStreamName,
        productStreamName);
  }

  // Configuration methods

  /**
   * Sets the feed stream name.
   *
   * @param name the feed stream name
   * @return this analyzer for chaining
   */
  public ProductionImpactAnalyzer setFeedStreamName(String name) {
    this.feedStreamName = name;
    return this;
  }

  /**
   * Sets the product stream name.
   *
   * @param name the product stream name
   * @return this analyzer for chaining
   */
  public ProductionImpactAnalyzer setProductStreamName(String name) {
    this.productStreamName = name;
    return this;
  }

  /**
   * Sets the product price for economic calculations.
   *
   * @param pricePerKg price per kg of product
   * @return this analyzer for chaining
   */
  public ProductionImpactAnalyzer setProductPricePerKg(double pricePerKg) {
    this.productPricePerKg = pricePerKg;
    return this;
  }

  /**
   * Sets whether to optimize degraded operation.
   *
   * @param optimize true to optimize
   * @return this analyzer for chaining
   */
  public ProductionImpactAnalyzer setOptimizeDegradedOperation(boolean optimize) {
    this.optimizeDegradedOperation = optimize;
    return this;
  }

  /**
   * Clears the baseline cache, forcing recalculation.
   */
  public void clearCache() {
    cachedBaselineProduction = null;
    cachedBaselinePower = null;
    cachedBaselineBottleneck = null;
    cachedBaselineBottleneckUtil = null;
  }

  // Analysis methods

  /**
   * Analyzes the production impact of failing a specific equipment.
   *
   * @param equipmentName name of the equipment to fail
   * @return production impact result
   */
  public ProductionImpactResult analyzeFailureImpact(String equipmentName) {
    return analyzeFailureImpact(equipmentName, EquipmentFailureMode.trip(equipmentName));
  }

  /**
   * Analyzes the production impact of a specific failure mode.
   *
   * @param equipmentName name of the equipment
   * @param failureMode the failure mode to simulate
   * @return production impact result
   */
  public ProductionImpactResult analyzeFailureImpact(String equipmentName,
      EquipmentFailureMode failureMode) {
    long startTime = System.currentTimeMillis();

    ProductionImpactResult result = new ProductionImpactResult(equipmentName, failureMode);
    result.setProductPricePerKg(productPricePerKg);

    try {
      // Get the equipment
      ProcessEquipmentInterface equipment = processSystem.getUnit(equipmentName);
      if (equipment == null) {
        result.setAnalysisNotes("Equipment not found: " + equipmentName);
        result.setConverged(false);
        return result;
      }

      result.setEquipmentType(equipment.getClass().getSimpleName());

      // Calculate baseline (normal operation)
      calculateBaseline(result);

      // Create a copy of the process for failure simulation
      ProcessSystem failedProcess = processSystem.copy();

      // Apply failure to the equipment
      applyFailure(failedProcess, equipmentName, failureMode, result);

      // Run the failed process
      try {
        failedProcess.run();

        // Get production with failure
        double productionWithFailure = getProductionRate(failedProcess);
        result.setProductionWithFailure(productionWithFailure);
        result.setPowerWithFailure(getTotalPower(failedProcess));

        // Find new bottleneck
        BottleneckResult newBottleneck = findBottleneck(failedProcess);
        if (newBottleneck != null && newBottleneck.hasBottleneck()) {
          result.setNewBottleneck(newBottleneck.getEquipmentName());
          result.setNewBottleneckUtilization(newBottleneck.getUtilization());
        }

      } catch (Exception e) {
        logger.warn("Failed process did not converge: {}", e.getMessage());
        result.setProductionWithFailure(0.0);
        result.setPowerWithFailure(0.0);
        result.setAnalysisNotes("Failed process did not converge: " + e.getMessage());
      }

      // Optimize degraded operation if requested
      if (optimizeDegradedOperation && result.getProductionWithFailure() > 0) {
        optimizeDegradedOperation(failedProcess, equipmentName, result);
      }

      // Full shutdown comparison
      result.setFullShutdownProduction(0.0);

      // Calculate derived metrics
      result.calculateDerivedMetrics();

      // Set recovery time estimate
      if (failureMode != null) {
        result.setEstimatedRecoveryTime(failureMode.getMttr());
      }

      result.setConverged(true);

    } catch (Exception e) {
      logger.error("Error analyzing failure impact for {}: {}", equipmentName, e.getMessage());
      result.setAnalysisNotes("Analysis error: " + e.getMessage());
      result.setConverged(false);
    }

    result.setAnalysisComputeTime(System.currentTimeMillis() - startTime);
    return result;
  }

  /**
   * Calculates baseline (normal operation) values.
   */
  private void calculateBaseline(ProductionImpactResult result) {
    if (cachedBaselineProduction == null) {
      // Run baseline
      processSystem.run();
      cachedBaselineProduction = getProductionRate(processSystem);
      cachedBaselinePower = getTotalPower(processSystem);

      BottleneckResult bottleneck = findBottleneck(processSystem);
      if (bottleneck != null && bottleneck.hasBottleneck()) {
        cachedBaselineBottleneck = bottleneck.getEquipmentName();
        cachedBaselineBottleneckUtil = bottleneck.getUtilization();
      }
    }

    result.setBaselineProductionRate(cachedBaselineProduction);
    result.setBaselinePower(cachedBaselinePower);
    result.setOriginalBottleneck(cachedBaselineBottleneck);
    if (cachedBaselineBottleneckUtil != null) {
      result.setOriginalBottleneckUtilization(cachedBaselineBottleneckUtil);
    }
  }

  /**
   * Applies a failure mode to equipment in the process.
   */
  private void applyFailure(ProcessSystem process, String equipmentName,
      EquipmentFailureMode failureMode, ProductionImpactResult result) {

    ProcessEquipmentInterface equipment = process.getUnit(equipmentName);
    if (equipment == null) {
      return;
    }

    // Mark equipment as inactive for complete failures
    if (failureMode.isCompleteFailure()) {
      equipment.setSpecification("FAILED");

      // For equipment that can be deactivated
      if (equipment instanceof neqsim.process.equipment.ProcessEquipmentBaseClass) {
        ((neqsim.process.equipment.ProcessEquipmentBaseClass) equipment).isActive(false);
      }

      // Disable capacity analysis to avoid including in bottleneck detection
      if (equipment instanceof neqsim.process.equipment.ProcessEquipmentBaseClass) {
        ((neqsim.process.equipment.ProcessEquipmentBaseClass) equipment)
            .setCapacityAnalysisEnabled(false);
      }

      // For compressors, set to bypass mode (no pressure increase)
      if (equipment instanceof Compressor) {
        Compressor comp = (Compressor) equipment;
        comp.setOutletPressure(comp.getInletStream().getPressure());
      }

      // For pumps, similar bypass
      if (equipment instanceof Pump) {
        Pump pump = (Pump) equipment;
        pump.setOutletPressure(pump.getInletStream().getPressure());
      }

      // For heaters/coolers, set to no heat transfer
      if (equipment instanceof Heater) {
        ((Heater) equipment)
            .setOutTemperature(((Heater) equipment).getInletStream().getTemperature());
      }
      if (equipment instanceof Cooler) {
        ((Cooler) equipment)
            .setOutTemperature(((Cooler) equipment).getInletStream().getTemperature());
      }

      result.addAffectedEquipment(equipmentName);

    } else if (failureMode.getCapacityFactor() < 1.0) {
      // Degraded operation - reduce capacity
      // This would require equipment-specific handling
      result.addAffectedEquipment(equipmentName);
    }

    // Find downstream equipment that may be affected
    identifyAffectedEquipment(process, equipmentName, result);
  }

  /**
   * Identifies equipment affected by the failure.
   */
  private void identifyAffectedEquipment(ProcessSystem process, String failedEquipment,
      ProductionImpactResult result) {

    // Simple approach: mark all equipment downstream as potentially affected
    boolean foundFailed = false;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit.getName().equals(failedEquipment)) {
        foundFailed = true;
        continue;
      }
      if (foundFailed) {
        result.addAffectedEquipment(unit.getName());
      }
    }
  }

  /**
   * Optimizes operation with failed equipment.
   */
  private void optimizeDegradedOperation(ProcessSystem failedProcess, String failedEquipment,
      ProductionImpactResult result) {

    try {
      // Find the feed stream
      ProcessEquipmentInterface feedUnit = failedProcess.getUnit(feedStreamName);
      if (!(feedUnit instanceof StreamInterface)) {
        return;
      }
      StreamInterface feed = (StreamInterface) feedUnit;

      // Get current flow rate
      double currentFlow = feed.getFlowRate("kg/hr");
      double optimalFlow = currentFlow;
      double maxProduction = result.getProductionWithFailure();

      // Simple optimization: try reducing flow in steps
      double[] flowFactors = {1.0, 0.9, 0.8, 0.7, 0.6, 0.5};

      for (double factor : flowFactors) {
        double testFlow = currentFlow * factor;
        feed.setFlowRate(testFlow, "kg/hr");

        try {
          failedProcess.run();
          double production = getProductionRate(failedProcess);

          // Check if this is better (higher production or more stable)
          if (production > maxProduction * 0.99) {
            // Similar production but potentially more stable
            optimalFlow = testFlow;
            maxProduction = production;
          }
        } catch (Exception e) {
          // This flow rate doesn't work
          continue;
        }
      }

      result.setOptimizedProductionWithFailure(maxProduction);
      result.addOptimizedSetpoint(feedStreamName + " flow rate (kg/hr)", optimalFlow);

      // Reset to original and get power at optimal
      feed.setFlowRate(optimalFlow, "kg/hr");
      failedProcess.run();
      result.setPowerWithFailure(getTotalPower(failedProcess));

    } catch (Exception e) {
      logger.warn("Could not optimize degraded operation: {}", e.getMessage());
    }
  }

  /**
   * Gets the production rate from a process system.
   */
  private double getProductionRate(ProcessSystem process) {
    if (productStreamName == null) {
      return 0.0;
    }

    ProcessEquipmentInterface unit = process.getUnit(productStreamName);
    if (unit instanceof StreamInterface) {
      return ((StreamInterface) unit).getFlowRate("kg/hr");
    }

    // Try to get outlet stream via TwoPortInterface
    try {
      if (unit instanceof neqsim.process.equipment.TwoPortInterface) {
        StreamInterface outlet =
            ((neqsim.process.equipment.TwoPortInterface) unit).getOutletStream();
        if (outlet != null) {
          return outlet.getFlowRate("kg/hr");
        }
      }
    } catch (Exception e) {
      // Equipment doesn't have outlet stream
    }

    return 0.0;
  }

  /**
   * Gets the total power consumption from a process system.
   */
  private double getTotalPower(ProcessSystem process) {
    double totalPower = 0.0;

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Compressor) {
        totalPower += ((Compressor) unit).getPower("kW");
      } else if (unit instanceof Pump) {
        totalPower += ((Pump) unit).getPower();
      }
    }

    return totalPower;
  }

  /**
   * Finds the current bottleneck in the process.
   */
  private BottleneckResult findBottleneck(ProcessSystem process) {
    ProcessEquipmentInterface bottleneckEquip = null;
    double maxUtilization = 0.0;

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof CapacityConstrainedEquipment) {
        CapacityConstrainedEquipment constrained = (CapacityConstrainedEquipment) unit;
        double util = constrained.getMaxUtilization();
        if (util > maxUtilization) {
          maxUtilization = util;
          bottleneckEquip = unit;
        }
      }
    }

    if (bottleneckEquip != null) {
      return new BottleneckResult(bottleneckEquip, null, maxUtilization);
    }
    return BottleneckResult.empty();
  }

  /**
   * Compares running with failed equipment vs. full plant shutdown.
   *
   * @param equipmentName name of the failed equipment
   * @return comparison result with recommendation
   */
  public ProductionImpactResult compareToPlantStop(String equipmentName) {
    ProductionImpactResult result = analyzeFailureImpact(equipmentName);

    // Full shutdown produces nothing
    result.setFullShutdownProduction(0.0);

    // Recalculate metrics with this in mind
    result.calculateDerivedMetrics();

    // Enhanced recommendation based on comparison
    double productionWithFailure = result.getProductionWithFailure();
    double baselineProduction = result.getBaselineProductionRate();

    if (productionWithFailure > 0) {
      double hoursToRecover = result.getEstimatedRecoveryTime();
      double lostProductionDegraded = result.getAbsoluteLoss() * hoursToRecover;
      double lostProductionShutdown = baselineProduction * hoursToRecover;

      if (lostProductionDegraded < lostProductionShutdown) {
        result.setRecommendedAction(RecommendedAction.REDUCE_THROUGHPUT);
        result.setRecommendationReason(
            String.format("Degraded operation saves %.0f kg over %.1f hours vs shutdown",
                lostProductionShutdown - lostProductionDegraded, hoursToRecover));
      } else {
        result.setRecommendedAction(RecommendedAction.FULL_SHUTDOWN);
        result.setRecommendationReason("Shutdown and repair is more economical");
      }
    }

    return result;
  }

  /**
   * Ranks all equipment by criticality (production impact of failure).
   *
   * @return list of results sorted by production impact (highest first)
   */
  public List<ProductionImpactResult> rankEquipmentByCriticality() {
    List<ProductionImpactResult> results = new ArrayList<ProductionImpactResult>();

    // Calculate baseline first
    clearCache();
    processSystem.run();
    cachedBaselineProduction = getProductionRate(processSystem);
    cachedBaselinePower = getTotalPower(processSystem);

    // Analyze each equipment
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      // Skip streams (they represent connections, not equipment)
      if (unit instanceof StreamInterface) {
        continue;
      }

      try {
        ProductionImpactResult result = analyzeFailureImpact(unit.getName());
        results.add(result);
      } catch (Exception e) {
        logger.warn("Could not analyze {}: {}", unit.getName(), e.getMessage());
      }
    }

    // Sort by percent loss (highest first = most critical)
    Collections.sort(results, new Comparator<ProductionImpactResult>() {
      @Override
      public int compare(ProductionImpactResult a, ProductionImpactResult b) {
        return Double.compare(b.getPercentLoss(), a.getPercentLoss());
      }
    });

    return results;
  }

  /**
   * Analyzes multiple simultaneous equipment failures.
   *
   * @param equipmentNames list of equipment names to fail
   * @return combined production impact result
   */
  public ProductionImpactResult analyzeMultipleFailures(List<String> equipmentNames) {
    ProductionImpactResult result = new ProductionImpactResult();
    result.setEquipmentName(String.join(", ", equipmentNames));
    result.setProductPricePerKg(productPricePerKg);

    try {
      // Calculate baseline
      calculateBaseline(result);

      // Create a copy and apply all failures
      ProcessSystem failedProcess = processSystem.copy();

      for (String equipmentName : equipmentNames) {
        applyFailure(failedProcess, equipmentName, EquipmentFailureMode.trip(equipmentName),
            result);
      }

      // Run the multi-failed process
      try {
        failedProcess.run();
        result.setProductionWithFailure(getProductionRate(failedProcess));
        result.setPowerWithFailure(getTotalPower(failedProcess));
      } catch (Exception e) {
        result.setProductionWithFailure(0.0);
        result.setPowerWithFailure(0.0);
        result.setAnalysisNotes("Multi-failure scenario did not converge");
      }

      result.setFullShutdownProduction(0.0);
      result.calculateDerivedMetrics();
      result.setConverged(true);

    } catch (Exception e) {
      logger.error("Error analyzing multiple failures: {}", e.getMessage());
      result.setConverged(false);
    }

    return result;
  }

  /**
   * Gets what-if analysis comparing different failure scenarios.
   *
   * @param equipmentNames list of equipment to compare
   * @return list of results for each scenario
   */
  public List<ProductionImpactResult> compareFailureScenarios(List<String> equipmentNames) {
    List<ProductionImpactResult> results = new ArrayList<ProductionImpactResult>();

    clearCache();

    for (String name : equipmentNames) {
      results.add(analyzeFailureImpact(name));
    }

    return results;
  }

  /**
   * Gets the process system.
   *
   * @return the process system
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Sets a new process system.
   *
   * @param processSystem the new process system
   */
  public void setProcessSystem(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    clearCache();
    autoDetectStreams();
  }
}
