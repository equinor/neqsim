package neqsim.process.equipment.network;

import java.io.Serializable;

/**
 * Structured result for one quality metric at one named point.
 */
public class NetworkQualityResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Compliance status. */
  public enum Status {
    /** Calculated value is within all configured limits. */
    PASS,
    /** Calculated value violates at least one configured limit. */
    FAIL,
    /** The required value could not be calculated or was not supplied. */
    NOT_CALCULABLE
  }

  private final String metricKey;
  private final String attributeName;
  private final Double value;
  private final String unit;
  private final QualityReference reference;
  private final Double lowerLimit;
  private final Double upperLimit;
  private final Double margin;
  private final Status status;
  private final String method;
  private final String provenance;
  private final String message;

  /**
   * Create a quality result.
   *
   * @param metricKey stable metric key
   * @param attributeName optional component/attribute name
   * @param value calculated value, or null
   * @param unit unit
   * @param reference basis/reference condition
   * @param lowerLimit lower limit
   * @param upperLimit upper limit
   * @param margin signed nearest-limit margin
   * @param status compliance status
   * @param method calculation/test method
   * @param provenance source
   * @param message diagnostic
   */
  public NetworkQualityResult(String metricKey, String attributeName, Double value, String unit,
      QualityReference reference, Double lowerLimit, Double upperLimit, Double margin, Status status, String method,
      String provenance, String message) {
    this.metricKey = metricKey;
    this.attributeName = attributeName;
    this.value = value;
    this.unit = unit;
    this.reference = reference;
    this.lowerLimit = lowerLimit;
    this.upperLimit = upperLimit;
    this.margin = margin;
    this.status = status;
    this.method = method;
    this.provenance = provenance;
    this.message = message;
  }

  /** @return metric key */
  public String getMetricKey() {
    return metricKey;
  }

  /** @return optional component/attribute name */
  public String getAttributeName() {
    return attributeName;
  }

  /** @return calculated value, or null */
  public Double getValue() {
    return value;
  }

  /** @return unit */
  public String getUnit() {
    return unit;
  }

  /** @return basis/reference condition */
  public QualityReference getReference() {
    return reference;
  }

  /** @return lower limit */
  public Double getLowerLimit() {
    return lowerLimit;
  }

  /** @return upper limit */
  public Double getUpperLimit() {
    return upperLimit;
  }

  /**
   * Get signed nearest-limit margin.
   *
   * @return positive on-spec margin, negative violation, or null
   */
  public Double getMargin() {
    return margin;
  }

  /** @return compliance status */
  public Status getStatus() {
    return status;
  }

  /** @return method or standard */
  public String getMethod() {
    return method;
  }

  /** @return provenance */
  public String getProvenance() {
    return provenance;
  }

  /** @return diagnostic message */
  public String getMessage() {
    return message;
  }
}
