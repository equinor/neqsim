package neqsim.process.logic.condition;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.logic.LogicCondition;

/**
 * Condition that checks if temperature meets a specified criteria.
 * 
 * <p>
 * Supports comparison operators: &gt;, &gt;=, &lt;, &lt;=, ==, !=
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * // Check if temperature is above 80°C
 * TemperatureCondition minTemp = new TemperatureCondition(heater, 80.0, "&gt;=");
 * 
 * // Check if temperature is below 200°C (max limit)
 * TemperatureCondition maxTemp = new TemperatureCondition(reactor, 200.0, "&lt;");
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class TemperatureCondition implements LogicCondition {
  private final ProcessEquipmentInterface equipment;
  private final double setpoint; // °C
  private final String operator;
  private final double tolerance; // °C

  /**
   * Creates a temperature condition with default tolerance (1.0°C).
   *
   * @param equipment equipment with stream to check
   * @param setpoint temperature setpoint in °C
   * @param operator comparison operator (&gt;, &gt;=, &lt;, &lt;=, ==, !=)
   */
  public TemperatureCondition(ProcessEquipmentInterface equipment, double setpoint,
      String operator) {
    this(equipment, setpoint, operator, 1.0);
  }

  /**
   * Creates a temperature condition with specified tolerance.
   *
   * @param equipment equipment with stream to check
   * @param setpoint temperature setpoint in °C
   * @param operator comparison operator (&gt;, &gt;=, &lt;, &lt;=, ==, !=)
   * @param tolerance tolerance for equality checks (°C)
   */
  public TemperatureCondition(ProcessEquipmentInterface equipment, double setpoint, String operator,
      double tolerance) {
    this.equipment = equipment;
    this.setpoint = setpoint;
    this.operator = operator;
    this.tolerance = tolerance;
  }

  @Override
  public boolean evaluate() {
    double temperature = getTemperature();
    if (Double.isNaN(temperature)) {
      return false;
    }

    switch (operator) {
      case ">":
        return temperature > setpoint;
      case ">=":
        return temperature >= setpoint;
      case "<":
        return temperature < setpoint;
      case "<=":
        return temperature <= setpoint;
      case "==":
        return Math.abs(temperature - setpoint) <= tolerance;
      case "!=":
        return Math.abs(temperature - setpoint) > tolerance;
      default:
        throw new IllegalArgumentException("Unknown operator: " + operator);
    }
  }

  private double getTemperature() {
    if (equipment instanceof StreamInterface) {
      return ((StreamInterface) equipment).getTemperature("C");
    }
    // Equipment doesn't directly expose streams in interface
    return Double.NaN;
  }

  @Override
  public String getDescription() {
    return String.format("Temperature %s %.1f°C", operator, setpoint);
  }

  @Override
  public ProcessEquipmentInterface getTargetEquipment() {
    return equipment;
  }

  @Override
  public String getCurrentValue() {
    double temperature = getTemperature();
    return Double.isNaN(temperature) ? "N/A" : String.format("%.1f°C", temperature);
  }

  @Override
  public String getExpectedValue() {
    return String.format("%s %.1f°C", operator, setpoint);
  }
}
