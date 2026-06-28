package neqsim.process.costestimation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Source and quality basis for a NeqSim cost estimate.
 *
 * <p>
 * This object travels with unit and process estimates so reports can state the estimate class, method, currency, cost
 * year, location factor, and data source instead of only reporting a single CAPEX number.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class CostEstimateBasis implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private EstimateClass estimateClass = EstimateClass.CLASS_4;
  private String estimatingMethod = EstimateClass.CLASS_4.getDefaultMethod();
  private String dataSource = "correlation";
  private String currencyCode = "USD";
  private int costYear = 2025;
  private double locationFactor = 1.0;
  private String locationBasis = "US Gulf Coast";
  private String notes = "Equipment-factored NeqSim screening/study estimate.";

  /**
   * Gets the estimate class.
   *
   * @return estimate class
   */
  public EstimateClass getEstimateClass() {
    return estimateClass;
  }

  /**
   * Sets the estimate class and default estimating method.
   *
   * @param estimateClass the estimate class, or {@link EstimateClass#CLASS_4} when {@code null}
   * @return this basis for chaining
   */
  public CostEstimateBasis setEstimateClass(EstimateClass estimateClass) {
    this.estimateClass = estimateClass == null ? EstimateClass.CLASS_4 : estimateClass;
    this.estimatingMethod = this.estimateClass.getDefaultMethod();
    return this;
  }

  /**
   * Gets the estimating method identifier.
   *
   * @return estimating method
   */
  public String getEstimatingMethod() {
    return estimatingMethod;
  }

  /**
   * Sets the estimating method identifier.
   *
   * @param estimatingMethod method identifier; blank values keep the current value
   * @return this basis for chaining
   */
  public CostEstimateBasis setEstimatingMethod(String estimatingMethod) {
    if (estimatingMethod != null && !estimatingMethod.trim().isEmpty()) {
      this.estimatingMethod = estimatingMethod;
    }
    return this;
  }

  /**
   * Gets the data source identifier.
   *
   * @return data source identifier
   */
  public String getDataSource() {
    return dataSource;
  }

  /**
   * Sets the data source identifier.
   *
   * @param dataSource data source identifier; blank values keep the current value
   * @return this basis for chaining
   */
  public CostEstimateBasis setDataSource(String dataSource) {
    if (dataSource != null && !dataSource.trim().isEmpty()) {
      this.dataSource = dataSource;
    }
    return this;
  }

  /**
   * Gets the currency code.
   *
   * @return currency code
   */
  public String getCurrencyCode() {
    return currencyCode;
  }

  /**
   * Sets the currency code.
   *
   * @param currencyCode ISO-style currency code; blank values keep the current value
   * @return this basis for chaining
   */
  public CostEstimateBasis setCurrencyCode(String currencyCode) {
    if (currencyCode != null && !currencyCode.trim().isEmpty()) {
      this.currencyCode = currencyCode;
    }
    return this;
  }

  /**
   * Gets the cost year.
   *
   * @return cost year
   */
  public int getCostYear() {
    return costYear;
  }

  /**
   * Sets the cost year.
   *
   * @param costYear cost year; values before 1900 are ignored
   * @return this basis for chaining
   */
  public CostEstimateBasis setCostYear(int costYear) {
    if (costYear >= 1900) {
      this.costYear = costYear;
    }
    return this;
  }

  /**
   * Gets the location factor.
   *
   * @return location factor
   */
  public double getLocationFactor() {
    return locationFactor;
  }

  /**
   * Sets the location factor.
   *
   * @param locationFactor location factor; values less than or equal to zero are ignored
   * @return this basis for chaining
   */
  public CostEstimateBasis setLocationFactor(double locationFactor) {
    if (locationFactor > 0.0) {
      this.locationFactor = locationFactor;
    }
    return this;
  }

  /**
   * Gets the location basis label.
   *
   * @return location basis label
   */
  public String getLocationBasis() {
    return locationBasis;
  }

  /**
   * Sets the location basis label.
   *
   * @param locationBasis location basis label; blank values keep the current value
   * @return this basis for chaining
   */
  public CostEstimateBasis setLocationBasis(String locationBasis) {
    if (locationBasis != null && !locationBasis.trim().isEmpty()) {
      this.locationBasis = locationBasis;
    }
    return this;
  }

  /**
   * Gets estimate notes.
   *
   * @return estimate notes
   */
  public String getNotes() {
    return notes;
  }

  /**
   * Sets estimate notes.
   *
   * @param notes estimate notes; blank values keep the current value
   * @return this basis for chaining
   */
  public CostEstimateBasis setNotes(String notes) {
    if (notes != null && !notes.trim().isEmpty()) {
      this.notes = notes;
    }
    return this;
  }

  /**
   * Converts the basis to a JSON-friendly map.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("estimateClass", estimateClass.name());
    result.put("estimateClassNumber", estimateClass.getClassNumber());
    result.put("maturity", estimateClass.getMaturity());
    result.put("estimatingMethod", estimatingMethod);
    result.put("dataSource", dataSource);
    result.put("accuracyLowFraction", estimateClass.getLowAccuracyFraction());
    result.put("accuracyHighFraction", estimateClass.getHighAccuracyFraction());
    result.put("currencyCode", currencyCode);
    result.put("costYear", costYear);
    result.put("locationFactor", locationFactor);
    result.put("locationBasis", locationBasis);
    result.put("notes", notes);
    return result;
  }
}
