package neqsim.process.equipment.network;

import java.io.Serializable;

/**
 * Governed measured, assay-backed, or externally calculated quality value.
 */
public class NetworkMeasuredAttribute implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final double value;
  private final String unit;
  private final String method;
  private final String provenance;
  private final String effectiveDate;
  private final String blendingRule;

  /**
   * Create a governed attribute.
   *
   * @param name attribute name
   * @param value value
   * @param unit unit
   * @param method method or standard
   * @param provenance source
   * @param effectiveDate ISO-8601 date
   * @param blendingRule explicit blending rule
   */
  public NetworkMeasuredAttribute(String name, double value, String unit, String method, String provenance,
      String effectiveDate, String blendingRule) {
    this.name = name;
    this.value = value;
    this.unit = unit;
    this.method = method;
    this.provenance = provenance;
    this.effectiveDate = effectiveDate;
    this.blendingRule = blendingRule;
  }

  /** @return attribute name */
  public String getName() {
    return name;
  }

  /** @return value */
  public double getValue() {
    return value;
  }

  /** @return unit */
  public String getUnit() {
    return unit;
  }

  /** @return method */
  public String getMethod() {
    return method;
  }

  /** @return provenance */
  public String getProvenance() {
    return provenance;
  }

  /** @return effective date */
  public String getEffectiveDate() {
    return effectiveDate;
  }

  /** @return explicit blending rule */
  public String getBlendingRule() {
    return blendingRule;
  }
}
