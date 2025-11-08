package neqsim.process.logic.condition;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.logic.LogicCondition;

/**
 * Condition that checks if pressure meets a specified criteria.
 * 
 * <p>
 * Supports comparison operators: &gt;, &gt;=, &lt;, &lt;=, ==, !=
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * // Check if pressure is above 5 bara
 * PressureCondition minPressure = new PressureCondition(stream, 5.0, ">");
 * 
 * // Check if pressure is stable at 10 bara (within tolerance)
 * PressureCondition stable = new PressureCondition(stream, 10.0, "==", 0.5);
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class PressureCondition implements LogicCondition {
  private final ProcessEquipmentInterface equipment;
  private final double setpoint;
  private final String operator;
  private final double tolerance;

  /**
   * Creates a pressure condition with default tolerance (0.1 bara).
   *
   * @param equipment equipment with stream to check
   * @param setpoint pressure setpoint in bara
   * @param operator comparison operator (&gt;, &gt;=, &lt;, &lt;=, ==, !=)
   */
  public PressureCondition(ProcessEquipmentInterface equipment, double setpoint, String operator) {
    this(equipment, setpoint, operator, 0.1);
  }

  /**
   * Creates a pressure condition with specified tolerance.
   *
   * @param equipment equipment with stream to check
   * @param setpoint pressure setpoint in bara
   * @param operator comparison operator (&gt;, &gt;=, &lt;, &lt;=, ==, !=)
   * @param tolerance tolerance for equality checks (bara)
   */
  public PressureCondition(ProcessEquipmentInterface equipment, double setpoint, String operator,
      double tolerance) {
    this.equipment = equipment;
    this.setpoint = setpoint;
    this.operator = operator;
    this.tolerance = tolerance;
  }

  @Override
  public boolean evaluate() {
    double pressure = getPressure();
    if (Double.isNaN(pressure)) {
      return false;
    }

    switch (operator) {
      case ">":
        return pressure > setpoint;
      case ">=":
        return pressure >= setpoint;
      case "<":
        return pressure < setpoint;
      case "<=":
        return pressure <= setpoint;
      case "==":
        return Math.abs(pressure - setpoint) <= tolerance;
      case "!=":
        return Math.abs(pressure - setpoint) > tolerance;
      default:
        throw new IllegalArgumentException("Unknown operator: " + operator);
    }
  }

  private double getPressure() {
    if (equipment instanceof StreamInterface) {
      return ((StreamInterface) equipment).getPressure();
    }
    // Equipment doesn't directly expose streams in interface
    return Double.NaN;
  }

  @Override
  public String getDescription() {
    return String.format("Pressure %s %.1f bara", operator, setpoint);
  }

  @Override
  public ProcessEquipmentInterface getTargetEquipment() {
    return equipment;
  }

  @Override
  public String getCurrentValue() {
    double pressure = getPressure();
    return Double.isNaN(pressure) ? "N/A" : String.format("%.1f bara", pressure);
  }

  @Override
  public String getExpectedValue() {
    return String.format("%s %.1f bara", operator, setpoint);
  }
}
