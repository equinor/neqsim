package neqsim.process.util.reconciliation;

/**
 * A measured process variable participating in data reconciliation.
 *
 * <p>
 * Each variable represents a single plant measurement (flow rate, temperature, pressure, etc.) with
 * its measured value and uncertainty (standard deviation). After reconciliation, the adjusted value
 * satisfies all balance constraints while minimizing weighted deviations from measurements.
 * </p>
 *
 * <p>
 * Typical usage from Python:
 * </p>
 *
 * <pre>
 * var = ReconciliationVariable("feed_flow", 1000.0, 20.0)
 * var.setUnit("kg/hr")
 * engine.addVariable(var)
 * </pre>
 *
 * @author Process Optimization Team
 * @version 1.0
 */
public class ReconciliationVariable implements java.io.Serializable {

  private static final long serialVersionUID = 1L;

  /** Variable name (user-defined identifier). */
  private String name;

  /** Raw measured value from plant instrumentation. */
  private double measuredValue;

  /** Measurement uncertainty as standard deviation (sigma). */
  private double uncertainty;

  /** Reconciled (adjusted) value after WLS solution. Set by the engine. */
  private double reconciledValue;

  /** Model-predicted value from process simulation. Optional. */
  private double modelValue;

  /** Whether a model value has been set. */
  private boolean hasModelValue;

  /** Engineering unit string (e.g., "kg/hr", "bara", "C"). */
  private String unit = "";

  /**
   * Optional link to the equipment name in {@code ProcessSystem} that this variable measures.
   */
  private String equipmentName = "";

  /**
   * Optional property name on the equipment (e.g., "massFlowRate", "temperature", "pressure").
   */
  private String propertyName = "";

  /** Normalized residual after reconciliation (set by engine). */
  private double normalizedResidual;

  /** Whether this variable was flagged as a gross error. */
  private boolean grossError;

  /**
   * Creates a reconciliation variable with name, measured value, and uncertainty.
   *
   * @param name variable identifier, must be unique within the engine
   * @param measuredValue raw plant measurement
   * @param uncertainty measurement standard deviation (sigma), must be positive
   * @throws IllegalArgumentException if uncertainty is not positive
   */
  public ReconciliationVariable(String name, double measuredValue, double uncertainty) {
    if (uncertainty <= 0.0) {
      throw new IllegalArgumentException("Uncertainty must be positive, got: " + uncertainty);
    }
    this.name = name;
    this.measuredValue = measuredValue;
    this.uncertainty = uncertainty;
    this.reconciledValue = measuredValue; // default: unchanged
    this.hasModelValue = false;
  }

  /**
   * Creates a reconciliation variable linked to a specific equipment property.
   *
   * @param name variable identifier
   * @param equipmentName name of equipment in the ProcessSystem
   * @param propertyName property measured (e.g., "massFlowRate")
   * @param measuredValue raw plant measurement
   * @param uncertainty measurement standard deviation (sigma), must be positive
   * @throws IllegalArgumentException if uncertainty is not positive
   */
  public ReconciliationVariable(String name, String equipmentName, String propertyName,
      double measuredValue, double uncertainty) {
    this(name, measuredValue, uncertainty);
    this.equipmentName = equipmentName;
    this.propertyName = propertyName;
  }

  /**
   * Returns the variable name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the variable name.
   *
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns the raw measured value from the plant.
   *
   * @return measured value in engineering units
   */
  public double getMeasuredValue() {
    return measuredValue;
  }

  /**
   * Sets the raw measured value from the plant.
   *
   * @param measuredValue the measurement reading
   */
  public void setMeasuredValue(double measuredValue) {
    this.measuredValue = measuredValue;
  }

  /**
   * Returns the measurement uncertainty (standard deviation).
   *
   * @return sigma in engineering units
   */
  public double getUncertainty() {
    return uncertainty;
  }

  /**
   * Sets the measurement uncertainty (standard deviation).
   *
   * @param uncertainty sigma, must be positive
   * @throws IllegalArgumentException if uncertainty is not positive
   */
  public void setUncertainty(double uncertainty) {
    if (uncertainty <= 0.0) {
      throw new IllegalArgumentException("Uncertainty must be positive, got: " + uncertainty);
    }
    this.uncertainty = uncertainty;
  }

  /**
   * Returns the reconciled value after the WLS solve.
   *
   * @return adjusted value satisfying balance constraints
   */
  public double getReconciledValue() {
    return reconciledValue;
  }

  /**
   * Sets the reconciled value. Called by {@link DataReconciliationEngine}.
   *
   * @param reconciledValue the adjusted value
   */
  public void setReconciledValue(double reconciledValue) {
    this.reconciledValue = reconciledValue;
  }

  /**
   * Returns the adjustment: reconciled minus measured.
   *
   * @return reconciledValue - measuredValue
   */
  public double getAdjustment() {
    return reconciledValue - measuredValue;
  }

  /**
   * Returns the model-predicted value from process simulation.
   *
   * @return model value, or NaN if not set
   */
  public double getModelValue() {
    return hasModelValue ? modelValue : Double.NaN;
  }

  /**
   * Sets the model-predicted value from process simulation.
   *
   * @param modelValue the simulation prediction
   */
  public void setModelValue(double modelValue) {
    this.modelValue = modelValue;
    this.hasModelValue = true;
  }

  /**
   * Returns whether a model value has been set.
   *
   * @return true if setModelValue was called
   */
  public boolean hasModelValue() {
    return hasModelValue;
  }

  /**
   * Returns the engineering unit string.
   *
   * @return unit string (e.g., "kg/hr")
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Sets the engineering unit string.
   *
   * @param unit the unit to set
   * @return this variable for chaining
   */
  public ReconciliationVariable setUnit(String unit) {
    this.unit = unit;
    return this;
  }

  /**
   * Returns the linked equipment name.
   *
   * @return equipment name, or empty string if not linked
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Sets the linked equipment name.
   *
   * @param equipmentName the equipment name in ProcessSystem
   * @return this variable for chaining
   */
  public ReconciliationVariable setEquipmentName(String equipmentName) {
    this.equipmentName = equipmentName;
    return this;
  }

  /**
   * Returns the linked property name.
   *
   * @return property name, or empty string if not linked
   */
  public String getPropertyName() {
    return propertyName;
  }

  /**
   * Sets the linked property name.
   *
   * @param propertyName the property name (e.g., "massFlowRate")
   * @return this variable for chaining
   */
  public ReconciliationVariable setPropertyName(String propertyName) {
    this.propertyName = propertyName;
    return this;
  }

  /**
   * Returns the normalized residual after reconciliation.
   *
   * <p>
   * Computed as {@code (reconciledValue - measuredValue) / adjustedSigma} where
   * {@code adjustedSigma} accounts for the constraint correction covariance. Used for gross error
   * detection.
   * </p>
   *
   * @return normalized residual (dimensionless)
   */
  public double getNormalizedResidual() {
    return normalizedResidual;
  }

  /**
   * Sets the normalized residual. Called by {@link DataReconciliationEngine}.
   *
   * @param normalizedResidual the computed normalized residual
   */
  public void setNormalizedResidual(double normalizedResidual) {
    this.normalizedResidual = normalizedResidual;
  }

  /**
   * Returns whether this variable was flagged as a gross error.
   *
   * @return true if the normalized residual exceeds the gross error threshold
   */
  public boolean isGrossError() {
    return grossError;
  }

  /**
   * Sets the gross error flag. Called by {@link DataReconciliationEngine}.
   *
   * @param grossError true if flagged
   */
  public void setGrossError(boolean grossError) {
    this.grossError = grossError;
  }

  /**
   * Returns a summary string.
   *
   * @return human-readable representation of this variable
   */
  @Override
  public String toString() {
    String unitStr = unit.isEmpty() ? "" : " " + unit;
    String flag = grossError ? " [GROSS ERROR]" : "";
    return String.format("%s: meas=%.4f%s, rec=%.4f%s, adj=%.4f, |r|=%.3f%s", name, measuredValue,
        unitStr, reconciledValue, unitStr, getAdjustment(), Math.abs(normalizedResidual), flag);
  }
}
