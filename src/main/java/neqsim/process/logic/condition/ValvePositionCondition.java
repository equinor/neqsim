package neqsim.process.logic.condition;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.ValveInterface;
import neqsim.process.logic.LogicCondition;

/**
 * Condition that checks valve position/opening percentage.
 * 
 * <p>
 * Supports comparison operators: &gt;, &gt;=, &lt;, &lt;=, ==, !=
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * // Check if valve is closed (less than 5% open)
 * ValvePositionCondition closed = new ValvePositionCondition(valve, "&lt;", 5.0);
 * 
 * // Check if valve is fully open
 * ValvePositionCondition open = new ValvePositionCondition(valve, "&gt;", 95.0);
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ValvePositionCondition implements LogicCondition {
  private final ValveInterface valve;
  private final double setpoint;
  private final String operator;
  private final double tolerance;

  /**
   * Creates a valve position condition with default tolerance (1.0%).
   *
   * @param valve valve to check
   * @param operator comparison operator (&gt;, &gt;=, &lt;, &lt;=, ==, !=)
   * @param setpoint position setpoint (0-100%)
   */
  public ValvePositionCondition(ValveInterface valve, String operator, double setpoint) {
    this(valve, operator, setpoint, 1.0);
  }

  /**
   * Creates a valve position condition with specified tolerance.
   *
   * @param valve valve to check
   * @param operator comparison operator (&gt;, &gt;=, &lt;, &lt;=, ==, !=)
   * @param setpoint position setpoint (0-100%)
   * @param tolerance tolerance for equality checks (%)
   */
  public ValvePositionCondition(ValveInterface valve, String operator, double setpoint,
      double tolerance) {
    this.valve = valve;
    this.operator = operator;
    this.setpoint = setpoint;
    this.tolerance = tolerance;
  }

  @Override
  public boolean evaluate() {
    double position = valve.getPercentValveOpening();
    if (Double.isNaN(position)) {
      return false;
    }

    switch (operator) {
      case ">":
        return position > setpoint;
      case ">=":
        return position >= setpoint;
      case "<":
        return position < setpoint;
      case "<=":
        return position <= setpoint;
      case "==":
        return Math.abs(position - setpoint) <= tolerance;
      case "!=":
        return Math.abs(position - setpoint) > tolerance;
      default:
        throw new IllegalArgumentException("Unknown operator: " + operator);
    }
  }

  @Override
  public String getDescription() {
    return String.format("Valve %s position %s %.1f%%", valve.getName(), operator, setpoint);
  }

  @Override
  public ProcessEquipmentInterface getTargetEquipment() {
    return (ProcessEquipmentInterface) valve; // Cast needed due to interface hierarchy
  }

  @Override
  public String getCurrentValue() {
    double position = valve.getPercentValveOpening();
    return Double.isNaN(position) ? "N/A" : String.format("%.1f%%", position);
  }

  @Override
  public String getExpectedValue() {
    return String.format("%s %.1f%%", operator, setpoint);
  }
}
