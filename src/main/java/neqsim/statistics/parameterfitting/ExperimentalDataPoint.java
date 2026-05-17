package neqsim.statistics.parameterfitting;

import java.io.Serializable;

/**
 * Immutable experimental data point with measured value, uncertainty and independent variables.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ExperimentalDataPoint implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private final double measuredValue;
  private final double standardDeviation;
  private final double[] dependentValues;
  private final String reference;
  private final String description;

  /**
   * Creates an experimental data point.
   *
   * @param measuredValue measured response value
   * @param standardDeviation positive standard deviation for the measured response
   * @param dependentValues independent variable values used by the fitting function
   * @throws IllegalArgumentException if values are not finite, the uncertainty is not positive, or
   *         the dependent value array is empty
   */
  public ExperimentalDataPoint(double measuredValue, double standardDeviation,
      double[] dependentValues) {
    this(measuredValue, standardDeviation, dependentValues, "unknown", "unknown");
  }

  /**
   * Creates an experimental data point with reference metadata.
   *
   * @param measuredValue measured response value
   * @param standardDeviation positive standard deviation for the measured response
   * @param dependentValues independent variable values used by the fitting function
   * @param reference source reference for the data point
   * @param description short description of the data point
   * @throws IllegalArgumentException if values are not finite, the uncertainty is not positive, or
   *         the dependent value array is empty
   */
  public ExperimentalDataPoint(double measuredValue, double standardDeviation,
      double[] dependentValues, String reference, String description) {
    validateFinite("measuredValue", measuredValue);
    validateFinite("standardDeviation", standardDeviation);
    if (standardDeviation <= 0.0) {
      throw new IllegalArgumentException("standardDeviation must be positive");
    }
    if (dependentValues == null || dependentValues.length == 0) {
      throw new IllegalArgumentException("dependentValues must contain at least one value");
    }
    for (int i = 0; i < dependentValues.length; i++) {
      validateFinite("dependentValues[" + i + "]", dependentValues[i]);
    }
    this.measuredValue = measuredValue;
    this.standardDeviation = standardDeviation;
    this.dependentValues = copyArray(dependentValues);
    this.reference = reference == null ? "unknown" : reference;
    this.description = description == null ? "unknown" : description;
  }

  /**
   * Returns the measured response value.
   *
   * @return measured response value
   */
  public double getMeasuredValue() {
    return measuredValue;
  }

  /**
   * Returns the measured response standard deviation.
   *
   * @return positive standard deviation
   */
  public double getStandardDeviation() {
    return standardDeviation;
  }

  /**
   * Returns a copy of the independent variable values.
   *
   * @return independent variable values
   */
  public double[] getDependentValues() {
    return copyArray(dependentValues);
  }

  /**
   * Returns one independent variable value.
   *
   * @param index zero-based independent variable index
   * @return independent variable value
   * @throws IndexOutOfBoundsException if the index is outside the dependent value array
   */
  public double getDependentValue(int index) {
    return dependentValues[index];
  }

  /**
   * Returns the data source reference.
   *
   * @return source reference
   */
  public String getReference() {
    return reference;
  }

  /**
   * Returns the data point description.
   *
   * @return data point description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Converts this point to the legacy SampleValue representation.
   *
   * @param function fitting function to attach to the sample
   * @return sample value compatible with the existing optimizer API
   * @throws IllegalArgumentException if function is null
   */
  public SampleValue toSampleValue(BaseFunction function) {
    if (function == null) {
      throw new IllegalArgumentException("function cannot be null");
    }
    SampleValue sample = new SampleValue(measuredValue, standardDeviation, dependentValues);
    sample.setReference(reference);
    sample.setDescription(description);
    sample.setFunction(function);
    return sample;
  }

  /**
   * Validates that a number is finite.
   *
   * @param name value name used in error messages
   * @param value value to validate
   * @throws IllegalArgumentException if value is NaN or infinite
   */
  private static void validateFinite(String name, double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  /**
   * Copies a double array.
   *
   * @param values values to copy
   * @return copied array
   */
  private static double[] copyArray(double[] values) {
    double[] copy = new double[values.length];
    System.arraycopy(values, 0, copy, 0, values.length);
    return copy;
  }
}
