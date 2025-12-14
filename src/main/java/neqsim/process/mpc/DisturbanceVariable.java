package neqsim.process.mpc;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Represents a disturbance variable (DV) in an MPC formulation.
 *
 * <p>
 * A disturbance variable is a measured process input that affects the controlled variables but
 * cannot be manipulated by the controller. DVs are used for feedforward control - the MPC uses
 * knowledge of disturbances to proactively adjust manipulated variables before the disturbance
 * affects the outputs.
 * </p>
 *
 * <p>
 * Common examples include:
 * </p>
 * <ul>
 * <li>Feed flow rate</li>
 * <li>Feed composition</li>
 * <li>Ambient temperature</li>
 * <li>Upstream pressure</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * // Feed flow as disturbance
 * DisturbanceVariable feedFlowDV =
 *     new DisturbanceVariable("FeedFlow", feedStream, "flowRate", "kg/hr");
 *
 * // Ambient temperature as disturbance
 * DisturbanceVariable ambientDV =
 *     new DisturbanceVariable("Ambient").setUnit("C").setCurrentValue(25.0); // Manual update from
 *                                                                            // external source
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class DisturbanceVariable extends MPCVariable {
  private static final long serialVersionUID = 1000L;

  /** Previous value for calculating rate of change. */
  private double previousValue = Double.NaN;

  /** Predicted future value (if available). */
  private double predictedValue = Double.NaN;

  /** Time horizon for prediction (if available). */
  private double predictionHorizon = 0.0;

  /** Whether this is a measured or estimated disturbance. */
  private boolean measured = true;

  /** Sensitivity of CVs to this disturbance (for feedforward). */
  private double[] cvSensitivity = new double[0];

  /**
   * Construct a disturbance variable with a name.
   *
   * @param name unique identifier for this DV
   */
  public DisturbanceVariable(String name) {
    super(name);
  }

  /**
   * Construct a disturbance variable bound to equipment.
   *
   * @param name unique identifier for this DV
   * @param equipment the process equipment to monitor
   * @param propertyName the property to read
   */
  public DisturbanceVariable(String name, ProcessEquipmentInterface equipment,
      String propertyName) {
    super(name, equipment, propertyName);
  }

  /**
   * Construct a disturbance variable bound to equipment with unit.
   *
   * @param name unique identifier for this DV
   * @param equipment the process equipment to monitor
   * @param propertyName the property to read
   * @param unit the unit for the property value
   */
  public DisturbanceVariable(String name, ProcessEquipmentInterface equipment, String propertyName,
      String unit) {
    super(name, equipment, propertyName, unit);
  }

  @Override
  public MPCVariableType getType() {
    return MPCVariableType.DISTURBANCE;
  }

  /**
   * Get the previous value.
   *
   * @return the previous measurement
   */
  public double getPreviousValue() {
    return previousValue;
  }

  /**
   * Get the rate of change.
   *
   * @return change from previous to current value
   */
  public double getRateOfChange() {
    if (Double.isFinite(currentValue) && Double.isFinite(previousValue)) {
      return currentValue - previousValue;
    }
    return 0.0;
  }

  /**
   * Get the predicted future value.
   *
   * @return the predicted value (if available)
   */
  public double getPredictedValue() {
    return predictedValue;
  }

  /**
   * Set the predicted future value for feedforward control.
   *
   * @param value the predicted value
   * @param horizon the time horizon for the prediction
   * @return this variable for method chaining
   */
  public DisturbanceVariable setPrediction(double value, double horizon) {
    this.predictedValue = value;
    this.predictionHorizon = horizon;
    return this;
  }

  /**
   * Get the prediction time horizon.
   *
   * @return the horizon in time units
   */
  public double getPredictionHorizon() {
    return predictionHorizon;
  }

  /**
   * Check if this is a measured disturbance.
   *
   * @return true if measured, false if estimated
   */
  public boolean isMeasured() {
    return measured;
  }

  /**
   * Set whether this disturbance is measured or estimated.
   *
   * @param measured true for measured, false for estimated
   * @return this variable for method chaining
   */
  public DisturbanceVariable setMeasured(boolean measured) {
    this.measured = measured;
    return this;
  }

  /**
   * Get the sensitivity of CVs to this disturbance.
   *
   * @return array of sensitivities (∂CV/∂DV)
   */
  public double[] getCvSensitivity() {
    return cvSensitivity.clone();
  }

  /**
   * Set the sensitivity of CVs to this disturbance.
   *
   * <p>
   * Used by the MPC for feedforward compensation. Entry i is ∂CV[i]/∂DV.
   * </p>
   *
   * @param sensitivity array of sensitivities
   * @return this variable for method chaining
   */
  public DisturbanceVariable setCvSensitivity(double... sensitivity) {
    if (sensitivity == null) {
      this.cvSensitivity = new double[0];
    } else {
      this.cvSensitivity = sensitivity.clone();
    }
    return this;
  }

  @Override
  public DisturbanceVariable setBounds(double min, double max) {
    super.setBounds(min, max);
    return this;
  }

  @Override
  public DisturbanceVariable setEquipment(ProcessEquipmentInterface equipment) {
    super.setEquipment(equipment);
    return this;
  }

  @Override
  public DisturbanceVariable setPropertyName(String propertyName) {
    super.setPropertyName(propertyName);
    return this;
  }

  @Override
  public DisturbanceVariable setUnit(String unit) {
    super.setUnit(unit);
    return this;
  }

  @Override
  public void setCurrentValue(double value) {
    this.previousValue = this.currentValue;
    super.setCurrentValue(value);
  }

  @Override
  public double readValue() {
    if (equipment == null) {
      return currentValue;
    }

    // Store previous value
    if (Double.isFinite(currentValue)) {
      previousValue = currentValue;
    }

    // Handle common equipment types
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

    return currentValue;
  }

  /**
   * Update the disturbance value from an external source.
   *
   * <p>
   * Used when the disturbance is not directly readable from NeqSim equipment (e.g., ambient
   * conditions from external sensors).
   * </p>
   *
   * @param value the new value
   * @return this variable for method chaining
   */
  public DisturbanceVariable update(double value) {
    setCurrentValue(value);
    return this;
  }

  /**
   * Calculate the expected change in a CV due to this disturbance change.
   *
   * @param cvIndex the index of the CV
   * @return the expected CV change
   */
  public double getExpectedCvChange(int cvIndex) {
    if (cvIndex < 0 || cvIndex >= cvSensitivity.length) {
      return 0.0;
    }
    return cvSensitivity[cvIndex] * getRateOfChange();
  }

  /**
   * Calculate the expected change in a CV due to predicted disturbance.
   *
   * @param cvIndex the index of the CV
   * @return the expected CV change from predicted disturbance
   */
  public double getExpectedCvChangeFromPrediction(int cvIndex) {
    if (cvIndex < 0 || cvIndex >= cvSensitivity.length) {
      return 0.0;
    }
    if (!Double.isFinite(predictedValue) || !Double.isFinite(currentValue)) {
      return 0.0;
    }
    return cvSensitivity[cvIndex] * (predictedValue - currentValue);
  }
}
