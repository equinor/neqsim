package neqsim.process.logic;

import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Interface for defining conditions that must be met for logic to proceed.
 * 
 * <p>
 * Conditions are used for:
 * <ul>
 * <li>Startup permissives (e.g., temperature above minimum, pressure stable)</li>
 * <li>Conditional branching (e.g., if level high then open valve)</li>
 * <li>Safety interlocks (e.g., verify pump stopped before opening drain)</li>
 * <li>Process state verification (e.g., check all equipment ready)</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * LogicCondition tempReady = new TemperatureCondition(heater, 80.0, ">=");
 * LogicCondition pressureOK = new PressureCondition(vessel, 5.0, ">");
 * 
 * StartupLogic startup = new StartupLogic("Startup Sequence");
 * startup.addPermissive(tempReady);
 * startup.addPermissive(pressureOK);
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public interface LogicCondition {
  /**
   * Evaluates if the condition is currently met.
   *
   * @return true if condition is satisfied, false otherwise
   */
  boolean evaluate();

  /**
   * Gets a description of this condition.
   *
   * @return human-readable description
   */
  String getDescription();

  /**
   * Gets the target equipment this condition monitors (if any).
   *
   * @return target equipment, or null if not equipment-specific
   */
  ProcessEquipmentInterface getTargetEquipment();

  /**
   * Gets the current value being evaluated (for diagnostics).
   *
   * @return current value as string, or empty if not applicable
   */
  String getCurrentValue();

  /**
   * Gets the expected/setpoint value (for diagnostics).
   *
   * @return expected value as string, or empty if not applicable
   */
  String getExpectedValue();
}
