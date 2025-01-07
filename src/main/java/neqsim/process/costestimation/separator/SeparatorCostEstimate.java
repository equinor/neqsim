package neqsim.process.costestimation.separator;

import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;

/**
 * <p>
 * SeparatorCostEstimate class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class SeparatorCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SeparatorCostEstimate.
   * </p>
   *
   * @param mechanicalEquipment a
   *        {@link neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign} object
   */
  public SeparatorCostEstimate(SeparatorMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
  }

  /** {@inheritDoc} */
  @Override
  public double getTotaltCost() {
    SeparatorMechanicalDesign sepMecDesign = (SeparatorMechanicalDesign) mechanicalEquipment;

    sepMecDesign.getWeightTotal();
    sepMecDesign.getVolumeTotal();

    return this.mechanicalEquipment.getWeightTotal();
  }
}
