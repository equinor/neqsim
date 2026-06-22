package neqsim.process.safety.compliance;

import java.io.Serializable;

/**
 * Single finding from a {@link NorsokP002ComplianceChecker}.
 *
 * @author ESOL
 * @version 1.0
 */
public class P002Finding implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String equipmentName;
  private final P002Criterion criterion;
  private final boolean compliant;
  private final double value;
  private final double limit;
  private final String unit;
  private final String message;

  /**
   * Creates a P-002 finding.
   *
   * @param equipmentName equipment / segment name
   * @param criterion P-002 criterion checked
   * @param compliant true if criterion met
   * @param value evaluated value
   * @param limit acceptance limit per P-002
   * @param unit physical unit of value/limit
   * @param message human readable description
   */
  public P002Finding(String equipmentName, P002Criterion criterion, boolean compliant, double value, double limit,
      String unit, String message) {
    this.equipmentName = equipmentName;
    this.criterion = criterion;
    this.compliant = compliant;
    this.value = value;
    this.limit = limit;
    this.unit = unit;
    this.message = message;
  }

  /**
   * @return equipment / segment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * @return criterion
   */
  public P002Criterion getCriterion() {
    return criterion;
  }

  /**
   * @return true if criterion met
   */
  public boolean isCompliant() {
    return compliant;
  }

  /**
   * @return evaluated value
   */
  public double getValue() {
    return value;
  }

  /**
   * @return acceptance limit
   */
  public double getLimit() {
    return limit;
  }

  /**
   * @return physical unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * @return human readable description
   */
  public String getMessage() {
    return message;
  }
}
