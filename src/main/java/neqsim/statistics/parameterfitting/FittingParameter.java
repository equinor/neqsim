package neqsim.statistics.parameterfitting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;

/**
 * Definition of one fitted model parameter including bounds, transform and prior metadata.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FittingParameter implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private String name = "parameter";
  private double initialValue = 0.0;
  private double lowerBound = -1.0e20;
  private double upperBound = 1.0e20;
  private String unit = "";
  private ParameterTransform transform = ParameterTransform.LINEAR;
  private String category = "generic";
  private double priorValue = Double.NaN;
  private double priorStandardDeviation = Double.NaN;

  /**
   * Creates a default parameter definition for serialization frameworks.
   */
  public FittingParameter() {}

  /**
   * Creates a bounded linear fitting parameter.
   *
   * @param name parameter name
   * @param initialValue initial physical parameter value
   * @param lowerBound lower physical parameter bound
   * @param upperBound upper physical parameter bound
   */
  public FittingParameter(String name, double initialValue, double lowerBound, double upperBound) {
    this(name, initialValue, lowerBound, upperBound, "", ParameterTransform.LINEAR, "generic",
        Double.NaN, Double.NaN);
  }

  /**
   * Creates a complete fitting parameter definition.
   *
   * @param name parameter name
   * @param initialValue initial physical parameter value
   * @param lowerBound lower physical parameter bound
   * @param upperBound upper physical parameter bound
   * @param unit parameter unit label
   * @param transform parameter-space transform
   * @param category parameter category or physical meaning
   * @param priorValue optional prior value, or NaN when unused
   * @param priorStandardDeviation optional prior standard deviation, or NaN when unused
   */
  public FittingParameter(String name, double initialValue, double lowerBound, double upperBound,
      String unit, ParameterTransform transform, String category, double priorValue,
      double priorStandardDeviation) {
    this.name = defaultString(name, "parameter");
    this.initialValue = initialValue;
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
    this.unit = defaultString(unit, "");
    this.transform = transform == null ? ParameterTransform.LINEAR : transform;
    this.category = defaultString(category, "generic");
    this.priorValue = priorValue;
    this.priorStandardDeviation = priorStandardDeviation;
    validate();
  }

  /**
   * Validates the parameter definition.
   *
   * @throws IllegalArgumentException if values or bounds are invalid
   */
  public void validate() {
    validateFinite("initialValue", initialValue);
    validateFinite("lowerBound", lowerBound);
    validateFinite("upperBound", upperBound);
    if (lowerBound > upperBound) {
      throw new IllegalArgumentException("lowerBound cannot exceed upperBound for " + name);
    }
    if (initialValue < lowerBound || initialValue > upperBound) {
      throw new IllegalArgumentException("initialValue is outside bounds for " + name);
    }
    transform.toInternal(initialValue, lowerBound, upperBound);
    if (!Double.isNaN(priorStandardDeviation) && priorStandardDeviation <= 0.0) {
      throw new IllegalArgumentException("priorStandardDeviation must be positive for " + name);
    }
  }

  /**
   * Returns the parameter name.
   *
   * @return parameter name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the parameter name.
   *
   * @param name parameter name
   */
  public void setName(String name) {
    this.name = defaultString(name, "parameter");
  }

  /**
   * Returns the initial physical value.
   *
   * @return initial physical value
   */
  public double getInitialValue() {
    return initialValue;
  }

  /**
   * Sets the initial physical value.
   *
   * @param initialValue initial physical value
   */
  public void setInitialValue(double initialValue) {
    this.initialValue = initialValue;
  }

  /**
   * Returns the lower physical bound.
   *
   * @return lower physical bound
   */
  public double getLowerBound() {
    return lowerBound;
  }

  /**
   * Sets the lower physical bound.
   *
   * @param lowerBound lower physical bound
   */
  public void setLowerBound(double lowerBound) {
    this.lowerBound = lowerBound;
  }

  /**
   * Returns the upper physical bound.
   *
   * @return upper physical bound
   */
  public double getUpperBound() {
    return upperBound;
  }

  /**
   * Sets the upper physical bound.
   *
   * @param upperBound upper physical bound
   */
  public void setUpperBound(double upperBound) {
    this.upperBound = upperBound;
  }

  /**
   * Returns the unit label.
   *
   * @return unit label
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Sets the unit label.
   *
   * @param unit unit label
   */
  public void setUnit(String unit) {
    this.unit = defaultString(unit, "");
  }

  /**
   * Returns the parameter transform.
   *
   * @return parameter transform
   */
  public ParameterTransform getTransform() {
    return transform;
  }

  /**
   * Sets the parameter transform.
   *
   * @param transform parameter transform
   */
  public void setTransform(ParameterTransform transform) {
    this.transform = transform == null ? ParameterTransform.LINEAR : transform;
  }

  /**
   * Returns the parameter category.
   *
   * @return parameter category
   */
  public String getCategory() {
    return category;
  }

  /**
   * Sets the parameter category.
   *
   * @param category parameter category
   */
  public void setCategory(String category) {
    this.category = defaultString(category, "generic");
  }

  /**
   * Returns the optional prior value.
   *
   * @return prior value, or NaN when unused
   */
  public double getPriorValue() {
    return priorValue;
  }

  /**
   * Sets the optional prior value.
   *
   * @param priorValue prior value, or NaN when unused
   */
  public void setPriorValue(double priorValue) {
    this.priorValue = priorValue;
  }

  /**
   * Returns the optional prior standard deviation.
   *
   * @return prior standard deviation, or NaN when unused
   */
  public double getPriorStandardDeviation() {
    return priorStandardDeviation;
  }

  /**
   * Sets the optional prior standard deviation.
   *
   * @param priorStandardDeviation prior standard deviation, or NaN when unused
   */
  public void setPriorStandardDeviation(double priorStandardDeviation) {
    this.priorStandardDeviation = priorStandardDeviation;
  }

  /**
   * Returns whether this parameter has a usable Gaussian prior.
   *
   * @return true if prior value and standard deviation are configured
   */
  @JsonIgnore
  public boolean hasPrior() {
    return !Double.isNaN(priorValue) && !Double.isNaN(priorStandardDeviation)
        && priorStandardDeviation > 0.0;
  }

  /**
   * Converts the initial value to optimizer space.
   *
   * @return initial value in optimizer space
   */
  @JsonIgnore
  public double getInternalInitialValue() {
    validate();
    return transform.toInternal(initialValue, lowerBound, upperBound);
  }

  /**
   * Converts the physical bounds to optimizer-space bounds.
   *
   * @return optimizer bounds, or null if the transformed parameter is unbounded
   */
  @JsonIgnore
  public double[] getInternalBounds() {
    validate();
    return transform.toInternalBounds(lowerBound, upperBound);
  }

  /**
   * Converts an optimizer-space value to physical space.
   *
   * @param internalValue optimizer-space value
   * @return physical parameter value
   */
  public double toExternalValue(double internalValue) {
    return transform.toExternal(internalValue, lowerBound, upperBound);
  }

  /**
   * Returns a default string when the value is null.
   *
   * @param value user-supplied value
   * @param defaultValue fallback value
   * @return value or defaultValue
   */
  private static String defaultString(String value, String defaultValue) {
    return value == null ? defaultValue : value;
  }

  /**
   * Validates that a number is finite.
   *
   * @param name value name used in exception messages
   * @param value value to validate
   */
  private static void validateFinite(String name, double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }
}
