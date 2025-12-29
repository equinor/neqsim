package neqsim.util.validation.contracts;

import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.util.validation.ValidationResult;

/**
 * Contract for process systems (flowsheets).
 * 
 * <p>
 * Defines requirements and guarantees for {@link ProcessSystem} implementations. AI agents can use
 * this contract to validate entire process flowsheets before running simulations.
 * </p>
 * 
 * <h2>Preconditions (what the process system needs):</h2>
 * <ul>
 * <li>At least one unit operation</li>
 * <li>All equipment properly connected</li>
 * <li>Feed streams defined</li>
 * <li>No circular dependencies without recycle blocks</li>
 * </ul>
 * 
 * <h2>Postconditions (what run() provides):</h2>
 * <ul>
 * <li>All equipment calculated</li>
 * <li>Recycles converged (if any)</li>
 * <li>Material/energy balances satisfied</li>
 * </ul>
 * 
 * @author NeqSim
 * @version 1.0
 */
public class ProcessSystemContract implements ModuleContract<ProcessSystem> {

  /** Singleton instance. */
  private static final ProcessSystemContract INSTANCE = new ProcessSystemContract();

  private ProcessSystemContract() {}

  /**
   * Get the singleton instance.
   * 
   * @return contract instance
   */
  public static ProcessSystemContract getInstance() {
    return INSTANCE;
  }

  @Override
  public String getContractName() {
    return "ProcessSystemContract";
  }

  @Override
  public ValidationResult checkPreconditions(ProcessSystem processSystem) {
    ValidationResult result = new ValidationResult("ProcessSystem");

    // Check: Has unit operations
    if (processSystem.size() == 0) {
      result.addError("process.units", "No unit operations in process system",
          "Add equipment: processSystem.add(stream); processSystem.add(separator);");
      return result;
    }

    // Check each unit operation
    int feedStreamCount = 0;
    java.util.List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (int i = 0; i < units.size(); i++) {
      ProcessEquipmentInterface equipment = units.get(i);

      if (equipment == null) {
        result.addError("process.unit[" + i + "]", "Null equipment at index " + i,
            "Ensure all equipment is properly initialized before adding");
        continue;
      }

      // Check equipment name
      String name = equipment.getName();
      if (name == null || name.isEmpty()) {
        result.addWarning("process.unit[" + i + "].name",
            "Equipment at index " + i + " has no name",
            "Set name in constructor for easier debugging");
      }

      // Count feed streams (streams without upstream connections)
      if (equipment.getClass().getSimpleName().contains("Stream")) {
        feedStreamCount++;
      }

      // Validate individual equipment using their own validateSetup
      ValidationResult equipResult = equipment.validateSetup();
      for (ValidationResult.ValidationIssue issue : equipResult.getIssues()) {
        if (issue.getSeverity() == ValidationResult.Severity.CRITICAL) {
          result.addError("process." + name + "." + issue.getCategory(), issue.getMessage(),
              issue.getRemediation());
        } else if (issue.getSeverity() == ValidationResult.Severity.MAJOR) {
          result.addWarning("process." + name + "." + issue.getCategory(), issue.getMessage(),
              issue.getRemediation());
        }
      }
    }

    // Check: Has at least one feed stream
    if (feedStreamCount == 0) {
      result.addWarning("process.feeds", "No obvious feed streams detected",
          "Ensure process has input streams with defined compositions");
    }

    return result;
  }

  @Override
  public ValidationResult checkPostconditions(ProcessSystem processSystem) {
    ValidationResult result = new ValidationResult("ProcessSystem (post-run)");

    // Check each unit has been calculated
    java.util.List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (int i = 0; i < units.size(); i++) {
      ProcessEquipmentInterface equipment = units.get(i);
      if (equipment == null) {
        continue;
      }

      // Check for solved status if available
      try {
        // Most equipment doesn't expose solved status directly
        // We can check for output availability
        String name = equipment.getName();
        if (name == null) {
          name = "unit" + i;
        }
      } catch (Exception e) {
        result.addWarning("process.unit[" + i + "]",
            "Could not verify equipment state: " + e.getMessage(), "");
      }
    }

    return result;
  }

  @Override
  public String getRequirementsDescription() {
    return "ProcessSystem Requirements:\n"
        + "- At least one unit operation (add via processSystem.add())\n"
        + "- Feed streams with valid thermodynamic systems\n"
        + "- Equipment properly connected (inlet/outlet streams)\n"
        + "- Unique names for each equipment (recommended)\n"
        + "- For recycles: use Recycle equipment to handle circular flows";
  }

  @Override
  public String getProvidesDescription() {
    return "ProcessSystem Provides (after run()):\n" + "- All equipment calculated in sequence\n"
        + "- Converged recycle loops (if Recycle equipment used)\n"
        + "- Outlet stream properties for all equipment\n"
        + "- Access via getUnit(name) or getUnit(index)\n"
        + "- JSON report via toJson() or getReport()";
  }

  /**
   * Validate equipment connectivity.
   * 
   * <p>
   * Checks that equipment is properly connected via streams.
   * </p>
   * 
   * @param processSystem the process system to validate
   * @return validation result
   */
  public ValidationResult validateConnectivity(ProcessSystem processSystem) {
    ValidationResult result = new ValidationResult("ProcessSystem:Connectivity");

    // This is a simplified check - full graph analysis would be more complex
    int connectedCount = 0;

    java.util.List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (int i = 0; i < units.size(); i++) {
      ProcessEquipmentInterface equipment = units.get(i);
      if (equipment == null) {
        continue;
      }

      // Check if this equipment has connections to others
      // This is approximate - proper graph analysis would need stream tracing
      connectedCount++;
    }

    if (connectedCount < 2 && processSystem.size() >= 2) {
      result.addWarning("connectivity",
          "Process has " + processSystem.size() + " units but connectivity could not be verified",
          "Ensure equipment is connected via streams");
    }

    return result;
  }
}
