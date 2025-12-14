package neqsim.process.mpc;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ValveInterface;

/**
 * Represents a manipulated variable (MV) in an MPC formulation.
 *
 * <p>
 * A manipulated variable is a process input that the MPC controller can adjust to achieve control
 * objectives. Common examples include valve openings, heater duties, compressor speeds, and flow
 * rate setpoints.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Absolute bounds (min/max physical limits)</li>
 * <li>Rate limits (maximum change per time step)</li>
 * <li>Preferred operating point for economic optimization</li>
 * <li>Cost weighting for multi-objective optimization</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * // Valve opening as MV
 * ManipulatedVariable valveMV =
 *     new ManipulatedVariable("InletValve", valve, "opening").setBounds(0.0, 1.0) // Physical
 *                                                                                 // limits
 *         .setRateLimit(-0.1, 0.1) // Max 10% change per step
 *         .setPreferredValue(0.7) // Preferred operating point
 *         .setCost(0.0); // No direct cost
 *
 * // Heater duty as MV with energy cost
 * ManipulatedVariable heaterMV = new ManipulatedVariable("Heater", heater, "duty", "kW")
 *     .setBounds(0.0, 5000.0).setRateLimit(-500.0, 500.0).setCost(0.05); // $/kWh energy cost
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class ManipulatedVariable extends MPCVariable {
  private static final long serialVersionUID = 1000L;

  /** Minimum allowed change per time step (rate constraint). */
  private double minRateOfChange = Double.NEGATIVE_INFINITY;

  /** Maximum allowed change per time step (rate constraint). */
  private double maxRateOfChange = Double.POSITIVE_INFINITY;

  /** Preferred operating value for economic optimization. */
  private double preferredValue = Double.NaN;

  /** Cost coefficient for using this MV ($/unit or energy cost). */
  private double cost = 0.0;

  /** Weight on control moves (penalizes rapid changes). */
  private double moveWeight = 0.0;

  /** Weight on deviation from preferred value. */
  private double preferredWeight = 0.0;

  /** Initial value before optimization. */
  private double initialValue = Double.NaN;

  /**
   * Construct a manipulated variable with a name.
   *
   * @param name unique identifier for this MV
   */
  public ManipulatedVariable(String name) {
    super(name);
  }

  /**
   * Construct a manipulated variable bound to equipment.
   *
   * @param name unique identifier for this MV
   * @param equipment the process equipment to control
   * @param propertyName the property to manipulate
   */
  public ManipulatedVariable(String name, ProcessEquipmentInterface equipment,
      String propertyName) {
    super(name, equipment, propertyName);
  }

  /**
   * Construct a manipulated variable bound to equipment with unit.
   *
   * @param name unique identifier for this MV
   * @param equipment the process equipment to control
   * @param propertyName the property to manipulate
   * @param unit the unit for the property value
   */
  public ManipulatedVariable(String name, ProcessEquipmentInterface equipment, String propertyName,
      String unit) {
    super(name, equipment, propertyName, unit);
  }

  @Override
  public MPCVariableType getType() {
    return MPCVariableType.MANIPULATED;
  }

  /**
   * Get the minimum rate of change per time step.
   *
   * @return minimum delta (typically negative)
   */
  public double getMinRateOfChange() {
    return minRateOfChange;
  }

  /**
   * Get the maximum rate of change per time step.
   *
   * @return maximum delta (typically positive)
   */
  public double getMaxRateOfChange() {
    return maxRateOfChange;
  }

  /**
   * Set rate limits for this MV.
   *
   * @param minDelta minimum change per time step (negative for decrease)
   * @param maxDelta maximum change per time step (positive for increase)
   * @return this variable for method chaining
   */
  public ManipulatedVariable setRateLimit(double minDelta, double maxDelta) {
    if (minDelta > maxDelta) {
      throw new IllegalArgumentException("Minimum rate must not exceed maximum rate");
    }
    this.minRateOfChange = minDelta;
    this.maxRateOfChange = maxDelta;
    return this;
  }

  /**
   * Get the preferred operating value.
   *
   * @return the preferred value for economic optimization
   */
  public double getPreferredValue() {
    return preferredValue;
  }

  /**
   * Set the preferred operating value.
   *
   * <p>
   * The MPC will try to keep the MV near this value when there is slack in the control objectives.
   * This is useful for economic optimization (e.g., minimize energy use by preferring lower heater
   * duty).
   * </p>
   *
   * @param value the preferred operating point
   * @return this variable for method chaining
   */
  public ManipulatedVariable setPreferredValue(double value) {
    this.preferredValue = value;
    return this;
  }

  /**
   * Get the cost coefficient for this MV.
   *
   * @return the cost per unit
   */
  public double getCost() {
    return cost;
  }

  /**
   * Set the cost coefficient for using this MV.
   *
   * <p>
   * This is used for economic MPC where the objective includes minimizing operational costs.
   * Examples: energy cost for heater duty, compression cost for compressor power.
   * </p>
   *
   * @param cost cost per unit (e.g., $/kWh)
   * @return this variable for method chaining
   */
  public ManipulatedVariable setCost(double cost) {
    if (cost < 0) {
      throw new IllegalArgumentException("Cost must be non-negative");
    }
    this.cost = cost;
    return this;
  }

  /**
   * Get the move weight.
   *
   * @return the weight on control moves
   */
  public double getMoveWeight() {
    return moveWeight;
  }

  /**
   * Set the move weight for penalizing rapid changes.
   *
   * @param weight the weight on move penalty (non-negative)
   * @return this variable for method chaining
   */
  public ManipulatedVariable setMoveWeight(double weight) {
    if (weight < 0) {
      throw new IllegalArgumentException("Move weight must be non-negative");
    }
    this.moveWeight = weight;
    return this;
  }

  /**
   * Get the weight on deviation from preferred value.
   *
   * @return the preferred value weight
   */
  public double getPreferredWeight() {
    return preferredWeight;
  }

  /**
   * Set the weight on deviation from preferred value.
   *
   * @param weight the weight (non-negative)
   * @return this variable for method chaining
   */
  public ManipulatedVariable setPreferredWeight(double weight) {
    if (weight < 0) {
      throw new IllegalArgumentException("Preferred weight must be non-negative");
    }
    this.preferredWeight = weight;
    return this;
  }

  /**
   * Get the initial value.
   *
   * @return the initial value before optimization
   */
  public double getInitialValue() {
    return initialValue;
  }

  /**
   * Set the initial value.
   *
   * @param value the starting value
   * @return this variable for method chaining
   */
  public ManipulatedVariable setInitialValue(double value) {
    this.initialValue = value;
    this.currentValue = value;
    return this;
  }

  @Override
  public ManipulatedVariable setBounds(double min, double max) {
    super.setBounds(min, max);
    return this;
  }

  @Override
  public ManipulatedVariable setEquipment(ProcessEquipmentInterface equipment) {
    super.setEquipment(equipment);
    return this;
  }

  @Override
  public ManipulatedVariable setPropertyName(String propertyName) {
    super.setPropertyName(propertyName);
    return this;
  }

  @Override
  public ManipulatedVariable setUnit(String unit) {
    super.setUnit(unit);
    return this;
  }

  @Override
  public double readValue() {
    if (equipment == null) {
      return currentValue;
    }

    // Handle common equipment types
    if (equipment instanceof ValveInterface) {
      ValveInterface valve = (ValveInterface) equipment;
      if ("opening".equalsIgnoreCase(propertyName)
          || "percentValveOpening".equalsIgnoreCase(propertyName)) {
        currentValue = valve.getPercentValveOpening() / 100.0;
        return currentValue;
      }
    }

    if (equipment instanceof StreamInterface) {
      StreamInterface stream = (StreamInterface) equipment;
      if ("flowRate".equalsIgnoreCase(propertyName)) {
        if (unit != null) {
          currentValue = stream.getFlowRate(unit);
        } else {
          currentValue = stream.getFlowRate("kg/hr");
        }
        return currentValue;
      }
      if ("temperature".equalsIgnoreCase(propertyName)) {
        if ("C".equalsIgnoreCase(unit)) {
          currentValue = stream.getTemperature("C");
        } else if ("K".equalsIgnoreCase(unit)) {
          currentValue = stream.getTemperature("K");
        } else {
          currentValue = stream.getTemperature("C");
        }
        return currentValue;
      }
      if ("pressure".equalsIgnoreCase(propertyName)) {
        if (unit != null) {
          currentValue = stream.getPressure(unit);
        } else {
          currentValue = stream.getPressure("bara");
        }
        return currentValue;
      }
    }

    // Return cached value if property not recognized
    return currentValue;
  }

  /**
   * Write a new value to the bound equipment.
   *
   * @param value the new value to write
   */
  public void writeValue(double value) {
    // Clamp to bounds
    double clampedValue = value;
    if (!Double.isInfinite(minValue)) {
      clampedValue = Math.max(minValue, clampedValue);
    }
    if (!Double.isInfinite(maxValue)) {
      clampedValue = Math.min(maxValue, clampedValue);
    }

    // Apply rate limits
    if (Double.isFinite(currentValue)) {
      double delta = clampedValue - currentValue;
      if (!Double.isInfinite(minRateOfChange)) {
        delta = Math.max(minRateOfChange, delta);
      }
      if (!Double.isInfinite(maxRateOfChange)) {
        delta = Math.min(maxRateOfChange, delta);
      }
      clampedValue = currentValue + delta;
    }

    this.currentValue = clampedValue;

    if (equipment == null) {
      return;
    }

    // Handle common equipment types
    if (equipment instanceof ValveInterface) {
      ValveInterface valve = (ValveInterface) equipment;
      if ("opening".equalsIgnoreCase(propertyName)
          || "percentValveOpening".equalsIgnoreCase(propertyName)) {
        valve.setPercentValveOpening(clampedValue * 100.0);
        return;
      }
    }

    if (equipment instanceof StreamInterface) {
      StreamInterface stream = (StreamInterface) equipment;
      if ("flowRate".equalsIgnoreCase(propertyName)) {
        if (unit != null) {
          stream.setFlowRate(clampedValue, unit);
        } else {
          stream.setFlowRate(clampedValue, "kg/hr");
        }
        return;
      }
      if ("temperature".equalsIgnoreCase(propertyName)) {
        if ("C".equalsIgnoreCase(unit)) {
          stream.setTemperature(clampedValue, "C");
        } else if ("K".equalsIgnoreCase(unit)) {
          stream.setTemperature(clampedValue, "K");
        } else {
          stream.setTemperature(clampedValue, "C");
        }
        return;
      }
      if ("pressure".equalsIgnoreCase(propertyName)) {
        if (unit != null) {
          stream.setPressure(clampedValue, unit);
        } else {
          stream.setPressure(clampedValue, "bara");
        }
        return;
      }
    }
  }

  /**
   * Calculate the cost of the current MV value.
   *
   * @return the cost contribution
   */
  public double calculateCost() {
    if (Double.isFinite(currentValue) && cost > 0) {
      return cost * currentValue;
    }
    return 0.0;
  }

  /**
   * Check if a proposed value satisfies all constraints.
   *
   * @param proposedValue the value to check
   * @return true if the value is feasible
   */
  public boolean isFeasible(double proposedValue) {
    if (!Double.isInfinite(minValue) && proposedValue < minValue) {
      return false;
    }
    if (!Double.isInfinite(maxValue) && proposedValue > maxValue) {
      return false;
    }
    if (Double.isFinite(currentValue)) {
      double delta = proposedValue - currentValue;
      if (!Double.isInfinite(minRateOfChange) && delta < minRateOfChange) {
        return false;
      }
      if (!Double.isInfinite(maxRateOfChange) && delta > maxRateOfChange) {
        return false;
      }
    }
    return true;
  }
}
