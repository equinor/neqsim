package neqsim.process.equipment.network;

import java.io.Serializable;

/**
 * Typed lower/upper quality limit with unit and reference condition.
 */
public class NetworkQualityLimit implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String metricKey;
  private final String domain;
  private final String unit;
  private final Double lowerLimit;
  private final Double upperLimit;
  private final QualityReference reference;
  private final String attributeName;
  private final String method;
  private final String provenance;

  /**
   * Create a quality limit.
   *
   * @param metric metric
   * @param lowerLimit optional lower limit
   * @param upperLimit optional upper limit
   * @param unit reporting unit
   * @param reference reference condition
   * @param attributeName component or measured-attribute name
   * @param method method or standard
   * @param provenance limit provenance
   */
  public NetworkQualityLimit(NetworkQualityMetric metric, Double lowerLimit, Double upperLimit, String unit,
      QualityReference reference, String attributeName, String method, String provenance) {
    if (metric == null) {
      throw new IllegalArgumentException("Metric cannot be null");
    }
    if (lowerLimit == null && upperLimit == null) {
      throw new IllegalArgumentException("At least one quality limit is required");
    }
    this.metricKey = metric.getKey();
    this.domain = metric.getDomain();
    this.unit = unit == null ? metric.getDefaultUnit() : unit;
    this.lowerLimit = lowerLimit;
    this.upperLimit = upperLimit;
    this.reference = reference;
    this.attributeName = attributeName;
    this.method = method;
    this.provenance = provenance;
  }

  /** @return stable metric key */
  public String getMetricKey() {
    return metricKey;
  }

  /** @return gas or oil */
  public String getDomain() {
    return domain;
  }

  /** @return result/limit unit */
  public String getUnit() {
    return unit;
  }

  /** @return lower limit, or null */
  public Double getLowerLimit() {
    return lowerLimit;
  }

  /** @return upper limit, or null */
  public Double getUpperLimit() {
    return upperLimit;
  }

  /** @return reference condition, or null */
  public QualityReference getReference() {
    return reference;
  }

  /** @return component or measured-attribute name, or null */
  public String getAttributeName() {
    return attributeName;
  }

  /** @return method or standard */
  public String getMethod() {
    return method;
  }

  /** @return provenance */
  public String getProvenance() {
    return provenance;
  }
}
