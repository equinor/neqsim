package neqsim.process.design;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer;

/**
 * Integrated design-to-optimization workflow manager.
 *
 * <p>
 * DesignOptimizer provides a unified workflow for:
 * </p>
 * <ul>
 * <li>Building processes from templates</li>
 * <li>Auto-sizing equipment</li>
 * <li>Setting up constraints</li>
 * <li>Running optimization</li>
 * <li>Validating results</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * DesignResult result = DesignOptimizer.forProcess(process).autoSizeEquipment(1.2).applyDefaultConstraints()
 *     .configureFeedRateOptimization("feed", 50000.0, 200000.0, "kg/hr")
 *     .setObjective(ObjectiveType.MAXIMIZE_PRODUCTION).optimize();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DesignOptimizer {

  private static final Logger logger = LogManager.getLogger(DesignOptimizer.class);

  private ProcessSystem process;
  private ProcessModule module;
  private ProcessBasis basis;
  private double safetyFactor = 1.2;
  private boolean autoSizeEnabled = false;
  private ObjectiveType objective = ObjectiveType.MAXIMIZE_PRODUCTION;
  private List<String> excludedEquipment = new ArrayList<>();
  private boolean defaultConstraintsEnabled = false;
  private String optimizationFeedStreamName;
  private String productStreamName;
  private double optimizationLowerBound;
  private double optimizationUpperBound;
  private String optimizationRateUnit = "kg/hr";
  private double optimizationTolerance = 1.0e-3;
  private int optimizationMaxIterations = 30;
  private ProductionOptimizer.SearchMode searchMode = ProductionOptimizer.SearchMode.BINARY_FEASIBILITY;
  private boolean searchModeExplicitlySet = false;
  private ProductionOptimizer.OptimizationObjective customObjective;
  private List<ProductionOptimizer.OptimizationConstraint> optimizationConstraints = new ArrayList<>();

  /**
   * Objective types for optimization.
   */
  public enum ObjectiveType {
    /** Maximize total production rate */
    MAXIMIZE_PRODUCTION,
    /** Maximize oil production */
    MAXIMIZE_OIL,
    /** Maximize gas production */
    MAXIMIZE_GAS,
    /** Minimize total energy consumption */
    MINIMIZE_ENERGY,
    /** Custom objective function */
    CUSTOM
  }

  /**
   * Private constructor - use factory methods.
   *
   * @param process the process to optimize
   */
  private DesignOptimizer(ProcessSystem process) {
    this.process = process;
    this.module = null;
  }

  /**
   * Private constructor for ProcessModule - use factory methods.
   *
   * @param module the process module to optimize
   */
  private DesignOptimizer(ProcessModule module) {
    this.module = module;
    this.process = null;
  }

  // ==================== Factory Methods ====================

  /**
   * Create a DesignOptimizer for an existing process.
   *
   * @param process the process system
   * @return new DesignOptimizer
   */
  public static DesignOptimizer forProcess(ProcessSystem process) {
    return new DesignOptimizer(process);
  }

  /**
   * Create a DesignOptimizer for an existing process module.
   *
   * @param module the process module
   * @return new DesignOptimizer
   */
  public static DesignOptimizer forProcess(ProcessModule module) {
    return new DesignOptimizer(module);
  }

  /**
   * Create a DesignOptimizer from a template.
   *
   * @param template the process template
   * @param basis the process basis
   * @return new DesignOptimizer with created process
   */
  public static DesignOptimizer fromTemplate(ProcessTemplate template, ProcessBasis basis) {
    ProcessSystem process = template.create(basis);
    DesignOptimizer optimizer = new DesignOptimizer(process);
    optimizer.basis = basis;
    return optimizer;
  }

  // ==================== Configuration Methods ====================

  /**
   * Enable auto-sizing of equipment with specified safety factor.
   *
   * @param safetyFactor the safety factor (typically 1.1-1.3)
   * @return this optimizer for chaining
   */
  public DesignOptimizer autoSizeEquipment(double safetyFactor) {
    if (Double.isNaN(safetyFactor) || Double.isInfinite(safetyFactor) || safetyFactor < 1.0) {
      throw new IllegalArgumentException("Safety factor must be finite and at least 1.0");
    }
    this.safetyFactor = safetyFactor;
    this.autoSizeEnabled = true;
    return this;
  }

  /**
   * Enable auto-sizing of equipment with default safety factor (1.2).
   *
   * @return this optimizer for chaining
   */
  public DesignOptimizer autoSizeEquipment() {
    return autoSizeEquipment(1.2);
  }

  /**
   * Apply default constraints based on equipment types.
   *
   * @return this optimizer for chaining
   */
  public DesignOptimizer applyDefaultConstraints() {
    defaultConstraintsEnabled = true;
    return this;
  }

  /**
   * Set the optimization objective.
   *
   * @param objective the objective type
   * @return this optimizer for chaining
   */
  public DesignOptimizer setObjective(ObjectiveType objective) {
    this.objective = Objects.requireNonNull(objective, "Objective type is required");
    return this;
  }

  /**
   * Configure a bounded feed-rate search.
   *
   * <p>
   * Calling {@link #optimize()} without this configuration performs validation and optional auto-sizing only; it does
   * not claim that an optimization search ran.
   * </p>
   *
   * @param feedStreamName manipulated feed stream name
   * @param lowerBound lower feed-rate bound
   * @param upperBound upper feed-rate bound
   * @param rateUnit feed-rate unit
   * @return this optimizer for chaining
   */
  public DesignOptimizer configureFeedRateOptimization(String feedStreamName, double lowerBound, double upperBound,
      String rateUnit) {
    if (feedStreamName == null || feedStreamName.trim().isEmpty()) {
      throw new IllegalArgumentException("Feed stream name is required");
    }
    if (Double.isNaN(lowerBound) || Double.isInfinite(lowerBound) || lowerBound < 0.0
        || Double.isNaN(upperBound) || Double.isInfinite(upperBound) || upperBound <= lowerBound) {
      throw new IllegalArgumentException("Optimization bounds must be finite with 0 <= lower < upper");
    }
    if (rateUnit == null || rateUnit.trim().isEmpty()) {
      throw new IllegalArgumentException("Rate unit is required");
    }
    this.optimizationFeedStreamName = feedStreamName;
    this.optimizationLowerBound = lowerBound;
    this.optimizationUpperBound = upperBound;
    this.optimizationRateUnit = rateUnit;
    return this;
  }

  /**
   * Select the product stream used by oil- or gas-production objectives.
   *
   * @param productStreamName product stream name
   * @return this optimizer for chaining
   */
  public DesignOptimizer setProductStream(String productStreamName) {
    if (productStreamName == null || productStreamName.trim().isEmpty()) {
      throw new IllegalArgumentException("Product stream name is required");
    }
    this.productStreamName = productStreamName;
    return this;
  }

  /**
   * Set search convergence controls.
   *
   * @param tolerance decision-variable tolerance in the configured rate unit
   * @param maxIterations maximum search iterations
   * @return this optimizer for chaining
   */
  public DesignOptimizer setSearchConvergence(double tolerance, int maxIterations) {
    if (Double.isNaN(tolerance) || Double.isInfinite(tolerance) || tolerance <= 0.0) {
      throw new IllegalArgumentException("Search tolerance must be finite and positive");
    }
    if (maxIterations <= 0) {
      throw new IllegalArgumentException("Maximum iterations must be positive");
    }
    this.optimizationTolerance = tolerance;
    this.optimizationMaxIterations = maxIterations;
    return this;
  }

  /**
   * Select the search algorithm used by the production optimizer.
   *
   * @param searchMode search algorithm
   * @return this optimizer for chaining
   */
  public DesignOptimizer setSearchMode(ProductionOptimizer.SearchMode searchMode) {
    this.searchMode = Objects.requireNonNull(searchMode, "Search mode is required");
    this.searchModeExplicitlySet = true;
    return this;
  }

  /**
   * Configure the evaluator used when {@link ObjectiveType#CUSTOM} is selected.
   *
   * @param name objective name
   * @param evaluator process objective evaluator
   * @param maximize {@code true} to maximize, {@code false} to minimize
   * @return this optimizer for chaining
   */
  public DesignOptimizer setCustomObjective(String name, ToDoubleFunction<ProcessSystem> evaluator,
      boolean maximize) {
    ProductionOptimizer.ObjectiveType direction = maximize ? ProductionOptimizer.ObjectiveType.MAXIMIZE
        : ProductionOptimizer.ObjectiveType.MINIMIZE;
    customObjective = new ProductionOptimizer.OptimizationObjective(name, evaluator, 1.0, direction);
    objective = ObjectiveType.CUSTOM;
    return this;
  }

  /**
   * Add an explicit process constraint to the bounded search.
   *
   * @param constraint optimization constraint
   * @return this optimizer for chaining
   */
  public DesignOptimizer addOptimizationConstraint(ProductionOptimizer.OptimizationConstraint constraint) {
    optimizationConstraints.add(Objects.requireNonNull(constraint, "Optimization constraint is required"));
    return this;
  }

  /**
   * Exclude equipment from optimization.
   *
   * @param equipmentNames names of equipment to exclude
   * @return this optimizer for chaining
   */
  public DesignOptimizer excludeEquipment(String... equipmentNames) {
    for (String name : equipmentNames) {
      excludedEquipment.add(name);
    }
    return this;
  }

  // ==================== Main Methods ====================

  /**
   * Run the complete design and optimization workflow.
   *
   * @return the design result
   */
  public DesignResult optimize() {
    DesignResult result = new DesignResult(process != null ? process : getFirstProcessSystem());

    try {
      logger.info("Running baseline simulation...");
      runSimulation();

      if (autoSizeEnabled) {
        logger.info("Auto-sizing equipment with safety factor: " + safetyFactor);
        autoSizeAllEquipment();
        runSimulation();
      }

      if (defaultConstraintsEnabled) {
        initializeDefaultConstraints(result);
      }

      if (optimizationFeedStreamName == null) {
        result.setExecutionStatus(
            autoSizeEnabled ? DesignResult.ExecutionStatus.AUTO_SIZED : DesignResult.ExecutionStatus.VALIDATED);
        result.setConverged(false);
        result.setIterations(0);
        result.addWarning("No optimization search was performed. Configure explicit feed-rate bounds with "
            + "configureFeedRateOptimization(...) before calling optimize().");
      } else {
        runConfiguredOptimization(result);
      }

      recordEquipmentSizes(result);
      recordConstraintStatus(result);
      checkViolations(result);

      logger.info("Design workflow complete. Status: " + result.getExecutionStatus() + ", violations: "
          + result.hasViolations());

    } catch (Exception e) {
      logger.error("Design failed: " + e.getMessage(), e);
      result.setExecutionStatus(DesignResult.ExecutionStatus.FAILED);
      result.setConverged(false);
      result.addViolation("Design failed: " + e.getMessage());
    }

    return result;
  }

  /**
   * Run only equipment auto-sizing (no optimization).
   *
   * @return this optimizer for further operations
   */
  public DesignOptimizer runAutoSizing() {
    runSimulation();
    autoSizeAllEquipment();
    return this;
  }

  /**
   * Validate the current design without optimization.
   *
   * @return design result with validation status
   */
  public DesignResult validate() {
    DesignResult result = new DesignResult(process != null ? process : getFirstProcessSystem());
    try {
      runSimulation();
      if (defaultConstraintsEnabled) {
        initializeDefaultConstraints(result);
      }
      recordEquipmentSizes(result);
      recordConstraintStatus(result);
      checkViolations(result);
      result.setExecutionStatus(DesignResult.ExecutionStatus.VALIDATED);
      result.setConverged(false);
    } catch (Exception e) {
      logger.error("Design validation failed: " + e.getMessage(), e);
      result.setExecutionStatus(DesignResult.ExecutionStatus.FAILED);
      result.addViolation("Design validation failed: " + e.getMessage());
    }
    return result;
  }

  // ==================== Helper Methods ====================

  /**
   * Run the simulation (either ProcessSystem or ProcessModule).
   */
  private void runSimulation() {
    if (process != null) {
      process.run();
    } else if (module != null) {
      module.run();
    }
  }

  /**
   * Delegate an explicitly configured bounded search to the production optimizer.
   *
   * @param result design result to populate
   */
  private void runConfiguredOptimization(DesignResult result) {
    if (process == null) {
      throw new IllegalStateException("Configured optimization currently requires a ProcessSystem, not a module");
    }
    StreamInterface feedStream = requireStream(optimizationFeedStreamName, "optimization feed");
    ProductionOptimizer.SearchMode effectiveSearchMode = effectiveSearchMode();
    ProductionOptimizer.OptimizationConfig config = new ProductionOptimizer.OptimizationConfig(
        optimizationLowerBound, optimizationUpperBound).rateUnit(optimizationRateUnit)
            .tolerance(optimizationTolerance).maxIterations(optimizationMaxIterations)
            .searchMode(effectiveSearchMode);
    ProductionOptimizer.OptimizationObjective configuredObjective = createObjective(feedStream);
    ProductionOptimizer engine = new ProductionOptimizer();
    ProductionOptimizer.OptimizationResult optimizationResult = engine.optimize(process, feedStream, config,
        Collections.singletonList(configuredObjective), new ArrayList<>(optimizationConstraints));

    // The search engine evaluates many mutable process states. Restore and run the selected point so the process and
    // the returned result describe the same operating condition.
    feedStream.setFlowRate(optimizationResult.getOptimalRate(), optimizationRateUnit);
    runSimulation();

    result.setIterations(optimizationResult.getIterationHistory().size());
    result.setConverged(isToleranceConverged(effectiveSearchMode));
    if (!result.isConverged()) {
      result.addWarning("The selected search mode did not demonstrate decision-variable tolerance convergence "
          + "within the configured iteration limit.");
    }
    for (Map.Entry<String, Double> entry : optimizationResult.getDecisionVariables().entrySet()) {
      result.addDecisionVariable(entry.getKey(), entry.getValue());
    }
    result.addOptimizedFlowRate(feedStream.getName(), optimizationResult.getOptimalRate());
    Double objectiveValue = optimizationResult.getObjectiveValues().get(configuredObjective.getName());
    result.setObjectiveValue(objectiveValue == null ? optimizationResult.getScore() : objectiveValue.doubleValue());

    for (ProductionOptimizer.ConstraintStatus status : optimizationResult.getConstraintStatuses()) {
      if (status.violated()) {
        result.addViolation("Optimization constraint " + status.getName() + " violated with margin "
            + status.getMargin());
      }
    }
    if (optimizationResult.isFeasible()) {
      result.setExecutionStatus(DesignResult.ExecutionStatus.OPTIMIZED);
    } else {
      result.setExecutionStatus(DesignResult.ExecutionStatus.INFEASIBLE);
      result.setConverged(false);
      result.addViolation(optimizationResult.getInfeasibilityDiagnosis());
    }
  }

  /**
   * Create the selected objective with explicit phase and stream semantics.
   *
   * @param feedStream manipulated feed stream
   * @return production optimizer objective
   */
  private ProductionOptimizer.OptimizationObjective createObjective(final StreamInterface feedStream) {
    if (objective == ObjectiveType.CUSTOM) {
      if (customObjective == null) {
        throw new IllegalStateException("CUSTOM objective requires setCustomObjective(...)");
      }
      return customObjective;
    }
    if (objective == ObjectiveType.MAXIMIZE_PRODUCTION) {
      return new ProductionOptimizer.OptimizationObjective("total production",
          proc -> feedStream.getFlowRate(optimizationRateUnit), 1.0, ProductionOptimizer.ObjectiveType.MAXIMIZE);
    }
    if (objective == ObjectiveType.MAXIMIZE_OIL) {
      final StreamInterface product = requireConfiguredProductStream();
      return new ProductionOptimizer.OptimizationObjective("oil production",
          proc -> phaseFlow(product, "oil", optimizationRateUnit), 1.0,
          ProductionOptimizer.ObjectiveType.MAXIMIZE);
    }
    if (objective == ObjectiveType.MAXIMIZE_GAS) {
      final StreamInterface product = requireConfiguredProductStream();
      return new ProductionOptimizer.OptimizationObjective("gas production",
          proc -> phaseFlow(product, "gas", optimizationRateUnit), 1.0,
          ProductionOptimizer.ObjectiveType.MAXIMIZE);
    }
    return new ProductionOptimizer.OptimizationObjective("total energy", proc -> totalEnergyRequirement(), 1.0,
        ProductionOptimizer.ObjectiveType.MINIMIZE);
  }

  /**
   * Use feasibility search for throughput and a score search for all other objectives unless explicitly selected.
   *
   * @return effective search mode
   */
  private ProductionOptimizer.SearchMode effectiveSearchMode() {
    if (!searchModeExplicitlySet && objective != ObjectiveType.MAXIMIZE_PRODUCTION) {
      return ProductionOptimizer.SearchMode.GOLDEN_SECTION_SCORE;
    }
    return searchMode;
  }

  /**
   * Check whether interval-reduction algorithms can reach the configured tolerance before their iteration cap.
   *
   * @param effectiveSearchMode selected search mode
   * @return true when tolerance convergence is guaranteed by the configured interval and iteration count
   */
  private boolean isToleranceConverged(ProductionOptimizer.SearchMode effectiveSearchMode) {
    double span = optimizationUpperBound - optimizationLowerBound;
    if (span <= optimizationTolerance) {
      return true;
    }
    if (effectiveSearchMode == ProductionOptimizer.SearchMode.BINARY_FEASIBILITY) {
      double required = Math.ceil(Math.log(span / optimizationTolerance) / Math.log(2.0));
      return optimizationMaxIterations >= required;
    }
    if (effectiveSearchMode == ProductionOptimizer.SearchMode.GOLDEN_SECTION_SCORE) {
      double phi = 0.5 * (Math.sqrt(5.0) - 1.0);
      double required = Math.ceil(Math.log(optimizationTolerance / span) / Math.log(phi));
      return optimizationMaxIterations >= required;
    }
    return false;
  }

  /** @return explicitly configured product stream */
  private StreamInterface requireConfiguredProductStream() {
    if (productStreamName == null) {
      throw new IllegalStateException(objective + " requires setProductStream(...)");
    }
    return requireStream(productStreamName, "product");
  }

  /**
   * Resolve and type-check a named stream.
   *
   * @param name equipment name
   * @param role diagnostic role
   * @return stream
   */
  private StreamInterface requireStream(String name, String role) {
    ProcessEquipmentInterface equipment = process.getUnit(name);
    if (!(equipment instanceof StreamInterface)) {
      throw new IllegalArgumentException("Configured " + role + " '" + name + "' is not a process stream");
    }
    return (StreamInterface) equipment;
  }

  /** @return phase flow, or zero when the requested phase is absent */
  private double phaseFlow(StreamInterface stream, String phaseType, String unit) {
    if (!stream.getFluid().hasPhaseType(phaseType)) {
      return 0.0;
    }
    return stream.getFluid().getPhase(phaseType).getFlowRate(unit);
  }

  /** @return total compressor, pump, and absolute heater duty in kW */
  private double totalEnergyRequirement() {
    double total = 0.0;
    for (ProcessEquipmentInterface equipment : getAllUnitOperations()) {
      if (equipment instanceof Compressor) {
        total += Math.abs(((Compressor) equipment).getPower("kW"));
      } else if (equipment instanceof Pump) {
        total += Math.abs(((Pump) equipment).getPower("kW"));
      } else if (equipment instanceof Heater) {
        total += Math.abs(((Heater) equipment).getDuty()) / 1000.0;
      }
    }
    return total;
  }

  /**
   * Get all unit operations from either ProcessSystem or ProcessModule.
   *
   * @return list of all unit operations
   */
  private List<ProcessEquipmentInterface> getAllUnitOperations() {
    List<ProcessEquipmentInterface> allUnits = new ArrayList<>();

    if (process != null) {
      allUnits.addAll(process.getUnitOperations());
    } else if (module != null) {
      // Collect from all process systems in the module
      for (ProcessSystem sys : module.getAllProcessSystems()) {
        allUnits.addAll(sys.getUnitOperations());
      }
    }

    return allUnits;
  }

  /**
   * Get the first ProcessSystem (for result creation when using module).
   *
   * @return the first ProcessSystem or null
   */
  private ProcessSystem getFirstProcessSystem() {
    if (process != null) {
      return process;
    }
    if (module != null) {
      List<ProcessSystem> systems = module.getAllProcessSystems();
      if (!systems.isEmpty()) {
        return systems.get(0);
      }
    }
    return null;
  }

  private void autoSizeAllEquipment() {
    for (ProcessEquipmentInterface equipment : getAllUnitOperations()) {
      if (equipment instanceof AutoSizeable && !excludedEquipment.contains(equipment.getName())) {
        AutoSizeable sizeable = (AutoSizeable) equipment;
        sizeable.autoSize(safetyFactor);
        logger.debug("Auto-sized: " + equipment.getName());
      }
    }
  }

  /**
   * Materialize lazy equipment constraints and report equipment that has no executable defaults.
   *
   * @param result design result receiving applicability warnings
   */
  private void initializeDefaultConstraints(DesignResult result) {
    for (ProcessEquipmentInterface equipment : getAllUnitOperations()) {
      if (equipment instanceof CapacityConstrainedEquipment && !excludedEquipment.contains(equipment.getName())) {
        CapacityConstrainedEquipment constrained = (CapacityConstrainedEquipment) equipment;
        if (constrained.isCapacityAnalysisEnabled() && constrained.getCapacityConstraints().isEmpty()) {
          result.addWarning(equipment.getName() + " has no executable default capacity constraints");
        }
      }
    }
  }

  private void recordEquipmentSizes(DesignResult result) {
    for (ProcessEquipmentInterface equipment : getAllUnitOperations()) {
      if (equipment instanceof Separator) {
        Separator sep = (Separator) equipment;
        result.addEquipmentSize(sep.getName(), "diameter", sep.getInternalDiameter());
        result.addEquipmentSize(sep.getName(), "length", sep.getSeparatorLength());
      }
      // Add more equipment types as needed
    }
  }

  private void recordConstraintStatus(DesignResult result) {
    // Record constraint utilizations from optimization result
    for (ProcessEquipmentInterface equipment : getAllUnitOperations()) {
      if (equipment instanceof CapacityConstrainedEquipment) {
        CapacityConstrainedEquipment constrained = (CapacityConstrainedEquipment) equipment;
        for (neqsim.process.equipment.capacity.CapacityConstraint constraint : constrained.getCapacityConstraints()
            .values()) {
          if (!constraint.isEnabled()) {
            continue;
          }
          double utilized = constraint.getUtilization();
          result.addConstraintStatus(equipment.getName(), constraint.getName(), constraint.getCurrentValue(),
              constraint.getDisplayDesignValue(), utilized);
        }
      }
    }
  }

  private void checkViolations(DesignResult result) {
    for (ProcessEquipmentInterface equipment : getAllUnitOperations()) {
      if (equipment instanceof CapacityConstrainedEquipment) {
        CapacityConstrainedEquipment constrained = (CapacityConstrainedEquipment) equipment;
        for (neqsim.process.equipment.capacity.CapacityConstraint constraint : constrained.getCapacityConstraints()
            .values()) {
          if (!constraint.isEnabled()) {
            continue;
          }
          double utilized = constraint.getUtilization();
          if (utilized > 1.0) {
            result.addViolation(equipment.getName() + ": " + constraint.getName() + " exceeded ("
                + String.format("%.1f%% utilization", utilized * 100) + ")");
          } else if (utilized > 0.9) {
            result.addWarning(equipment.getName() + ": " + constraint.getName() + " at "
                + String.format("%.1f%% utilization", utilized * 100));
          }
        }
      }
    }
  }

  /**
   * Get the process system.
   *
   * @return the process, or the first process system from the module if using a module
   */
  public ProcessSystem getProcess() {
    if (process != null) {
      return process;
    }
    return getFirstProcessSystem();
  }

  /**
   * Get the process module.
   *
   * @return the module or null if not using a module
   */
  public ProcessModule getModule() {
    return module;
  }

  /**
   * Check if this optimizer is working with a ProcessModule.
   *
   * @return true if using a ProcessModule, false if using a ProcessSystem
   */
  public boolean isModuleMode() {
    return module != null;
  }

  /**
   * Get the process basis.
   *
   * @return the basis or null if not set
   */
  public ProcessBasis getBasis() {
    return basis;
  }
}
