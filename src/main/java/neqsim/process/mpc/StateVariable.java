package neqsim.process.mpc;

import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Represents a state variable (SVR) in a nonlinear MPC system.
 *
 * <p>
 * State variables are internal model states that evolve according to dynamic equations. Unlike CVs
 * (controlled variables), SVRs are not directly controlled but are essential for model accuracy.
 * Examples include:
 * </p>
 * <ul>
 * <li>Flow rates (qin, qout)</li>
 * <li>Internal pressures</li>
 * <li>Valve coefficients (cv)</li>
 * <li>Calculated gains</li>
 * </ul>
 *
 * <p>
 * In nonlinear MPC, state variables track the difference between model predictions and
 * measurements, enabling bias correction and model updates.
 * </p>
 *
 * <p>
 * Key attributes:
 * </p>
 * <ul>
 * <li>ModelValue: Current value from model simulation</li>
 * <li>MeasValue: Measured value (if available)</li>
 * <li>Bias: Difference between measurement and model</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class StateVariable extends MPCVariable {
  private static final long serialVersionUID = 1000L;

  /** Model-predicted value. */
  private double modelValue;

  /** Measured value (if available). */
  private double measuredValue;

  /** Whether measurement is available. */
  private boolean hasMeasurement;

  /** Bias (measurement - model). */
  private double bias;

  /** Bias filter time constant (seconds). */
  private double biasTfilt = 0.0;

  /** Bias prediction time constant (seconds). */
  private double biasTpred = 0.0;

  /** Data index for C++ code linking. */
  private String dtaIx;

  /** Whether to update from measurement. */
  private boolean updateFromMeasurement = true;

  /**
   * Construct a state variable linked to process equipment.
   *
   * @param name variable name
   * @param equipment linked process equipment
   * @param propertyName property to read from equipment
   */
  public StateVariable(String name, ProcessEquipmentInterface equipment, String propertyName) {
    super(name, equipment, propertyName);
    this.modelValue = 0.0;
    this.measuredValue = 0.0;
    this.hasMeasurement = false;
    this.bias = 0.0;
    this.dtaIx = name.toLowerCase().replaceAll("[^a-z0-9]", "_");
  }

  /**
   * Get the model-predicted value.
   *
   * @return model value
   */
  public double getModelValue() {
    return modelValue;
  }

  /**
   * Set the model-predicted value.
   *
   * @param modelValue model value
   */
  public void setModelValue(double modelValue) {
    this.modelValue = modelValue;
  }

  /**
   * Get the measured value.
   *
   * @return measured value, or model value if no measurement
   */
  public double getMeasuredValue() {
    return hasMeasurement ? measuredValue : modelValue;
  }

  /**
   * Set the measured value.
   *
   * @param measuredValue measured value
   */
  public void setMeasuredValue(double measuredValue) {
    this.measuredValue = measuredValue;
    this.hasMeasurement = true;
    updateBias();
  }

  /**
   * Check if measurement is available.
   *
   * @return true if measured
   */
  public boolean hasMeasurement() {
    return hasMeasurement;
  }

  /**
   * Clear the measurement.
   */
  public void clearMeasurement() {
    this.hasMeasurement = false;
    this.bias = 0.0;
  }

  /**
   * Get the bias (measurement - model).
   *
   * @return bias value
   */
  public double getBias() {
    return bias;
  }

  /**
   * Update the bias based on current model and measured values.
   */
  private void updateBias() {
    if (hasMeasurement) {
      if (biasTfilt > 0) {
        // Low-pass filtered bias
        double alpha = 1.0 / (1.0 + biasTfilt);
        double newBias = measuredValue - modelValue;
        bias = alpha * newBias + (1.0 - alpha) * bias;
      } else {
        // Unfiltered bias
        bias = measuredValue - modelValue;
      }
    }
  }

  /**
   * Get the bias filter time constant.
   *
   * @return filter time constant (seconds)
   */
  public double getBiasTfilt() {
    return biasTfilt;
  }

  /**
   * Set the bias filter time constant.
   *
   * @param biasTfilt filter time constant (seconds)
   */
  public void setBiasTfilt(double biasTfilt) {
    this.biasTfilt = biasTfilt;
  }

  /**
   * Get the bias prediction time constant.
   *
   * @return prediction time constant (seconds)
   */
  public double getBiasTpred() {
    return biasTpred;
  }

  /**
   * Set the bias prediction time constant.
   *
   * @param biasTpred prediction time constant (seconds)
   */
  public void setBiasTpred(double biasTpred) {
    this.biasTpred = biasTpred;
  }

  /**
   * Get the data index for C++ code linking.
   *
   * @return data index
   */
  public String getDtaIx() {
    return dtaIx;
  }

  /**
   * Set the data index for C++ code linking.
   *
   * @param dtaIx data index
   */
  public void setDtaIx(String dtaIx) {
    this.dtaIx = dtaIx;
  }

  /**
   * Check if update from measurement is enabled.
   *
   * @return true if updates from measurement
   */
  public boolean isUpdateFromMeasurement() {
    return updateFromMeasurement;
  }

  /**
   * Set whether to update from measurement.
   *
   * @param updateFromMeasurement true to enable updates
   */
  public void setUpdateFromMeasurement(boolean updateFromMeasurement) {
    this.updateFromMeasurement = updateFromMeasurement;
  }

  /**
   * Get the corrected value (model + bias).
   *
   * @return corrected value
   */
  public double getCorrectedValue() {
    return modelValue + bias;
  }

  /**
   * Predict bias at a future time.
   *
   * @param predictionTime time into the future (seconds)
   * @param previousBias bias at previous time step
   * @param sampleTime sample time (seconds)
   * @return predicted bias
   */
  public double predictBias(double predictionTime, double previousBias, double sampleTime) {
    if (biasTpred <= 0) {
      // No bias prediction - constant bias
      return bias;
    }

    // Exponential decay of bias derivative
    double biasDer = (bias - previousBias) / sampleTime;
    double decay = Math.exp(-predictionTime / biasTpred);
    return bias + biasDer * biasTpred * (1.0 - decay);
  }

  @Override
  public double getCurrentValue() {
    return getCorrectedValue();
  }

  @Override
  public double readValue() {
    return readFromProcess();
  }

  @Override
  public MPCVariableType getType() {
    return MPCVariableType.STATE;
  }

  /**
   * Read the current value from the process.
   *
   * @return current value from equipment
   */
  public double readFromProcess() {
    double value = super.getCurrentValue();
    setModelValue(value);
    return value;
  }

  /**
   * Update state from measurement.
   *
   * @param measurement measured value
   */
  public void update(double measurement) {
    if (updateFromMeasurement) {
      setMeasuredValue(measurement);
    }
  }

  @Override
  public String toString() {
    return String.format("StateVariable[name=%s, model=%.4f, meas=%.4f, bias=%.4f, dtaIx=%s]",
        getName(), modelValue, getMeasuredValue(), bias, dtaIx);
  }
}
