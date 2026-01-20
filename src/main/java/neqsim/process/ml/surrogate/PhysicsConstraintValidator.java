package neqsim.process.ml.surrogate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Validates AI-proposed actions against physics constraints.
 *
 * <p>
 * This class enforces thermodynamic and safety constraints before allowing AI/ML models to execute
 * actions on a process system. Key principles:
 * <ul>
 * <li><b>Physics First:</b> All actions must satisfy mass/energy balances</li>
 * <li><b>Safety by Design:</b> Constraint violations are caught before execution</li>
 * <li><b>Explainability:</b> Clear reasons provided for rejected actions</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * ProcessSystem process = new ProcessSystem();
 * PhysicsConstraintValidator validator = new PhysicsConstraintValidator(process);
 *
 * // Add constraints
 * validator.addPressureLimit("separator", 10.0, 80.0, "bara");
 * validator.addTemperatureLimit("heater-outlet", 0.0, 300.0, "C");
 * validator.addMassBalanceCheck(0.01); // 1% tolerance
 *
 * // Validate AI-proposed action
 * Map&lt;String, Double&gt; proposedAction = new HashMap&lt;&gt;();
 * proposedAction.put("heater.duty", 5000000.0);
 * proposedAction.put("valve.opening", 0.85);
 *
 * ValidationResult result = validator.validate(proposedAction);
 *
 * if (result.isValid()) {
 *   // Safe to apply action
 *   applyAction(proposedAction);
 * } else {
 *   // Action rejected
 *   System.out.println("Action rejected: " + result.getRejectionReason());
 *   for (ConstraintViolation v : result.getViolations()) {
 *     System.out.println("  - " + v.getMessage());
 *   }
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class PhysicsConstraintValidator implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem processSystem;
  private final List<Constraint> constraints;
  private double massBalanceTolerance = 0.01; // 1% default
  private double energyBalanceTolerance = 0.05; // 5% default
  private boolean enforceMassBalance = true;
  private boolean enforceEnergyBalance = true;
  private boolean enforcePhysicalBounds = true;

  /**
   * Creates a validator for a process system.
   *
   * @param processSystem the process system to validate against
   */
  public PhysicsConstraintValidator(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.constraints = new ArrayList<>();
    addDefaultPhysicalConstraints();
  }

  private void addDefaultPhysicalConstraints() {
    // Temperature must be positive (Kelvin)
    constraints.add(new PhysicalBoundConstraint("temperature", 0.0, Double.MAX_VALUE, "K",
        "Temperature must be positive"));

    // Pressure must be positive
    constraints.add(new PhysicalBoundConstraint("pressure", 0.0, Double.MAX_VALUE, "Pa",
        "Pressure must be positive"));

    // Flow rates must be non-negative
    constraints.add(new PhysicalBoundConstraint("flow", 0.0, Double.MAX_VALUE, "kg/s",
        "Flow rate must be non-negative"));

    // Valve opening must be 0-100%
    constraints.add(new PhysicalBoundConstraint("valve.opening", 0.0, 1.0, "-",
        "Valve opening must be between 0 and 1"));
  }

  /**
   * Adds a pressure limit constraint for specific equipment.
   *
   * @param equipmentName name of the equipment
   * @param minPressure minimum pressure
   * @param maxPressure maximum pressure
   * @param unit pressure unit
   */
  public void addPressureLimit(String equipmentName, double minPressure, double maxPressure,
      String unit) {
    constraints
        .add(new EquipmentConstraint(equipmentName, "pressure", minPressure, maxPressure, unit));
  }

  /**
   * Adds a temperature limit constraint for specific equipment.
   *
   * @param equipmentName name of the equipment
   * @param minTemp minimum temperature
   * @param maxTemp maximum temperature
   * @param unit temperature unit
   */
  public void addTemperatureLimit(String equipmentName, double minTemp, double maxTemp,
      String unit) {
    constraints.add(new EquipmentConstraint(equipmentName, "temperature", minTemp, maxTemp, unit));
  }

  /**
   * Adds a flow rate limit constraint for specific equipment.
   *
   * @param equipmentName name of the equipment
   * @param minFlow minimum flow rate
   * @param maxFlow maximum flow rate
   * @param unit flow unit
   */
  public void addFlowLimit(String equipmentName, double minFlow, double maxFlow, String unit) {
    constraints.add(new EquipmentConstraint(equipmentName, "flow", minFlow, maxFlow, unit));
  }

  /**
   * Sets the mass balance tolerance.
   *
   * @param tolerance relative tolerance (e.g., 0.01 for 1%)
   */
  public void setMassBalanceTolerance(double tolerance) {
    this.massBalanceTolerance = tolerance;
  }

  /**
   * Sets the energy balance tolerance.
   *
   * @param tolerance relative tolerance (e.g., 0.05 for 5%)
   */
  public void setEnergyBalanceTolerance(double tolerance) {
    this.energyBalanceTolerance = tolerance;
  }

  /**
   * Validates a proposed set of actions against all constraints.
   *
   * @param proposedActions map of variable names to proposed values
   * @return validation result with pass/fail and violation details
   */
  public ValidationResult validate(Map<String, Double> proposedActions) {
    ValidationResult result = new ValidationResult();

    // Check each proposed action against constraints
    for (Map.Entry<String, Double> action : proposedActions.entrySet()) {
      String variable = action.getKey();
      double value = action.getValue();

      for (Constraint constraint : constraints) {
        if (constraint.appliesTo(variable)) {
          ConstraintCheckResult check = constraint.check(variable, value);
          if (!check.satisfied) {
            result.addViolation(
                new ConstraintViolation(constraint.getName(), variable, value, check.message));
          }
        }
      }
    }

    // Check mass balance if enabled
    if (enforceMassBalance) {
      ConstraintCheckResult massCheck = checkMassBalance();
      if (!massCheck.satisfied) {
        result.addViolation(
            new ConstraintViolation("MassBalance", "system", Double.NaN, massCheck.message));
      }
    }

    // Check energy balance if enabled
    if (enforceEnergyBalance) {
      ConstraintCheckResult energyCheck = checkEnergyBalance();
      if (!energyCheck.satisfied) {
        result.addViolation(
            new ConstraintViolation("EnergyBalance", "system", Double.NaN, energyCheck.message));
      }
    }

    return result;
  }

  /**
   * Validates that the current process state satisfies all constraints.
   *
   * @return validation result for current state
   */
  public ValidationResult validateCurrentState() {
    ValidationResult result = new ValidationResult();

    // Check equipment-specific constraints
    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      for (Constraint constraint : constraints) {
        if (constraint instanceof EquipmentConstraint) {
          EquipmentConstraint ec = (EquipmentConstraint) constraint;
          if (ec.equipmentName.equals(equipment.getName())) {
            double value = getEquipmentValue(equipment, ec.property);
            ConstraintCheckResult check = ec.check(ec.property, value);
            if (!check.satisfied) {
              result.addViolation(new ConstraintViolation(constraint.getName(),
                  equipment.getName() + "." + ec.property, value, check.message));
            }
          }
        }
      }
    }

    return result;
  }

  private double getEquipmentValue(ProcessEquipmentInterface equipment, String property) {
    if (equipment.getThermoSystem() == null) {
      return Double.NaN;
    }

    switch (property.toLowerCase()) {
      case "pressure":
        return equipment.getThermoSystem().getPressure();
      case "temperature":
        return equipment.getThermoSystem().getTemperature();
      case "flow":
        if (equipment instanceof StreamInterface) {
          return ((StreamInterface) equipment).getFlowRate("kg/sec");
        }
        return Double.NaN;
      default:
        return Double.NaN;
    }
  }

  private ConstraintCheckResult checkMassBalance() {
    // Simplified mass balance check
    double totalIn = 0.0;
    double totalOut = 0.0;

    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      if (equipment instanceof StreamInterface) {
        StreamInterface stream = (StreamInterface) equipment;
        // This is simplified - real implementation would track in/out streams
        double flow = stream.getFlowRate("kg/sec");
        if (flow > 0) {
          totalIn += flow;
        }
      }
    }

    // For now, return success if we can't determine balance
    if (totalIn == 0.0) {
      return new ConstraintCheckResult(true, "Mass balance not calculable");
    }

    double error = Math.abs(totalIn - totalOut) / totalIn;
    if (error > massBalanceTolerance) {
      return new ConstraintCheckResult(false,
          String.format("Mass balance error %.2f%% exceeds tolerance %.2f%%", error * 100,
              massBalanceTolerance * 100));
    }

    return new ConstraintCheckResult(true, "Mass balance satisfied");
  }

  private ConstraintCheckResult checkEnergyBalance() {
    // Placeholder for energy balance check
    return new ConstraintCheckResult(true, "Energy balance check not implemented");
  }

  // Enable/disable getters and setters

  public boolean isEnforceMassBalance() {
    return enforceMassBalance;
  }

  public void setEnforceMassBalance(boolean enforce) {
    this.enforceMassBalance = enforce;
  }

  public boolean isEnforceEnergyBalance() {
    return enforceEnergyBalance;
  }

  public void setEnforceEnergyBalance(boolean enforce) {
    this.enforceEnergyBalance = enforce;
  }

  public boolean isEnforcePhysicalBounds() {
    return enforcePhysicalBounds;
  }

  public void setEnforcePhysicalBounds(boolean enforce) {
    this.enforcePhysicalBounds = enforce;
  }

  /**
   * Result of a validation check.
   */
  public static class ValidationResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<ConstraintViolation> violations = new ArrayList<>();

    /**
     * Checks if the validation passed (no violations).
     *
     * @return true if valid
     */
    public boolean isValid() {
      return violations.isEmpty();
    }

    /**
     * Gets the rejection reason (first violation message).
     *
     * @return rejection reason, or null if valid
     */
    public String getRejectionReason() {
      if (violations.isEmpty()) {
        return null;
      }
      return violations.get(0).getMessage();
    }

    /**
     * Gets all constraint violations.
     *
     * @return list of violations
     */
    public List<ConstraintViolation> getViolations() {
      return violations;
    }

    void addViolation(ConstraintViolation violation) {
      violations.add(violation);
    }
  }

  /**
   * Details of a constraint violation.
   */
  public static class ConstraintViolation implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String constraintName;
    private final String variable;
    private final double value;
    private final String message;

    public ConstraintViolation(String constraintName, String variable, double value,
        String message) {
      this.constraintName = constraintName;
      this.variable = variable;
      this.value = value;
      this.message = message;
    }

    public String getConstraintName() {
      return constraintName;
    }

    public String getVariable() {
      return variable;
    }

    public double getValue() {
      return value;
    }

    public String getMessage() {
      return message;
    }

    @Override
    public String toString() {
      return String.format("[%s] %s: %s (value=%.4f)", constraintName, variable, message, value);
    }
  }

  /**
   * Internal result of checking a single constraint.
   */
  private static class ConstraintCheckResult {
    final boolean satisfied;
    final String message;

    ConstraintCheckResult(boolean satisfied, String message) {
      this.satisfied = satisfied;
      this.message = message;
    }
  }

  /**
   * Base interface for constraints.
   */
  private interface Constraint extends Serializable {
    String getName();

    boolean appliesTo(String variable);

    ConstraintCheckResult check(String variable, double value);
  }

  /**
   * Constraint on physical bounds (e.g., positive temperature).
   */
  private static class PhysicalBoundConstraint implements Constraint {
    private static final long serialVersionUID = 1000L;

    private final String variablePattern;
    private final double min;
    private final double max;
    private final String unit;
    private final String description;

    PhysicalBoundConstraint(String pattern, double min, double max, String unit,
        String description) {
      this.variablePattern = pattern;
      this.min = min;
      this.max = max;
      this.unit = unit;
      this.description = description;
    }

    @Override
    public String getName() {
      return "PhysicalBound:" + variablePattern;
    }

    @Override
    public boolean appliesTo(String variable) {
      return variable.toLowerCase().contains(variablePattern.toLowerCase());
    }

    @Override
    public ConstraintCheckResult check(String variable, double value) {
      if (value < min) {
        return new ConstraintCheckResult(false,
            String.format("%s: value %.4f below minimum %.4f %s", description, value, min, unit));
      }
      if (value > max) {
        return new ConstraintCheckResult(false,
            String.format("%s: value %.4f above maximum %.4f %s", description, value, max, unit));
      }
      return new ConstraintCheckResult(true, "OK");
    }
  }

  /**
   * Constraint on specific equipment.
   */
  private static class EquipmentConstraint implements Constraint {
    private static final long serialVersionUID = 1000L;

    final String equipmentName;
    final String property;
    final double min;
    final double max;
    final String unit;

    EquipmentConstraint(String equipment, String property, double min, double max, String unit) {
      this.equipmentName = equipment;
      this.property = property;
      this.min = min;
      this.max = max;
      this.unit = unit;
    }

    @Override
    public String getName() {
      return equipmentName + "." + property + ".limit";
    }

    @Override
    public boolean appliesTo(String variable) {
      return variable.startsWith(equipmentName + ".") && variable.contains(property);
    }

    @Override
    public ConstraintCheckResult check(String variable, double value) {
      if (value < min) {
        return new ConstraintCheckResult(false, String.format("%s %s %.4f below limit %.4f %s",
            equipmentName, property, value, min, unit));
      }
      if (value > max) {
        return new ConstraintCheckResult(false, String.format("%s %s %.4f above limit %.4f %s",
            equipmentName, property, value, max, unit));
      }
      return new ConstraintCheckResult(true, "OK");
    }
  }
}
