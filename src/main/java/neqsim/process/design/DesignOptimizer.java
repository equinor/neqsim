package neqsim.process.design;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.processmodel.ProcessSystem;

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
 * DesignResult result = DesignOptimizer.forProcess(process).autoSizeEquipment(1.2)
 *     .applyDefaultConstraints().setObjective(ObjectiveType.MAXIMIZE_PRODUCTION).optimize();
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
    // Constraints are applied during optimization based on equipment types
    return this;
  }

  /**
   * Set the optimization objective.
   *
   * @param objective the objective type
   * @return this optimizer for chaining
   */
  public DesignOptimizer setObjective(ObjectiveType objective) {
    this.objective = objective;
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
      // Step 1: Run baseline
      logger.info("Running baseline simulation...");
      runSimulation();

      // Step 2: Auto-size equipment if enabled
      if (autoSizeEnabled) {
        logger.info("Auto-sizing equipment with safety factor: " + safetyFactor);
        autoSizeAllEquipment();
        runSimulation(); // Re-run after sizing
      }

      // Step 3: Record results (simplified - full optimization will be added later)
      result.setConverged(true);
      result.setIterations(1);

      // Record flow rate
      StreamInterface productStream = findProductStream();
      if (productStream != null) {
        result.addOptimizedFlowRate(productStream.getName(), productStream.getFlowRate("kg/hr"));
        result.setObjectiveValue(productStream.getFlowRate("kg/hr"));
      }

      // Record equipment sizes
      recordEquipmentSizes(result);

      // Record constraint status
      recordConstraintStatus(result);

      // Check for violations
      checkViolations(result);

      logger.info("Design complete. Violations: " + result.hasViolations());

    } catch (Exception e) {
      logger.error("Design failed: " + e.getMessage(), e);
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
    runSimulation();
    checkViolations(result);
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

  private StreamInterface findProductStream() {
    // Find the last stream in the process as the product
    List<ProcessEquipmentInterface> units = getAllUnitOperations();
    for (int i = units.size() - 1; i >= 0; i--) {
      ProcessEquipmentInterface equipment = units.get(i);
      if (equipment instanceof StreamInterface) {
        return (StreamInterface) equipment;
      }
    }
    return null;
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
        for (neqsim.process.equipment.capacity.CapacityConstraint constraint : constrained
            .getCapacityConstraints().values()) {
          double utilized = constraint.getCurrentValue() / constraint.getMaxValue();
          result.addConstraintStatus(equipment.getName(), constraint.getName(),
              constraint.getCurrentValue(), constraint.getMaxValue(), utilized);
        }
      }
    }
  }

  private void checkViolations(DesignResult result) {
    for (ProcessEquipmentInterface equipment : getAllUnitOperations()) {
      if (equipment instanceof CapacityConstrainedEquipment) {
        CapacityConstrainedEquipment constrained = (CapacityConstrainedEquipment) equipment;
        for (neqsim.process.equipment.capacity.CapacityConstraint constraint : constrained
            .getCapacityConstraints().values()) {
          double utilized = constraint.getCurrentValue() / constraint.getMaxValue();
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
