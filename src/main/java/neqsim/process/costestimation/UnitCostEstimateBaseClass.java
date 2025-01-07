package neqsim.process.costestimation;

import java.util.Objects;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * <p>
 * UnitCostEstimateBaseClass class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class UnitCostEstimateBaseClass implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double costPerWeightUnit = 1000.0;
  public MechanicalDesign mechanicalEquipment = null;

  /**
   * <p>
   * Constructor for UnitCostEstimateBaseClass.
   * </p>
   */
  public UnitCostEstimateBaseClass() {}

  /**
   * <p>
   * Constructor for UnitCostEstimateBaseClass.
   * </p>
   *
   * @param mechanicalEquipment a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public UnitCostEstimateBaseClass(MechanicalDesign mechanicalEquipment) {
    this.mechanicalEquipment = mechanicalEquipment;
  }

  /**
   * <p>
   * getTotaltCost.
   * </p>
   *
   * @return the totaltCost
   */
  public double getTotaltCost() {
    return this.mechanicalEquipment.getWeightTotal() * costPerWeightUnit;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(costPerWeightUnit, mechanicalEquipment);
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    UnitCostEstimateBaseClass other = (UnitCostEstimateBaseClass) obj;
    return Double.doubleToLongBits(costPerWeightUnit) == Double
        .doubleToLongBits(other.costPerWeightUnit)
        && Objects.equals(mechanicalEquipment, other.mechanicalEquipment);
  }
}
