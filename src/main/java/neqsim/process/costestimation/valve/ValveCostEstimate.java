package neqsim.process.costestimation.valve;

import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;

/**
 * <p>
 * ValveCostEstimate class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ValveCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ValveCostEstimate.
   * </p>
   *
   * @param mechanicalEquipment a
   *        {@link neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign} object
   */
  public ValveCostEstimate(ValveMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
  }

  /** {@inheritDoc} */
  @Override
  public double getTotalCost() {
    ValveMechanicalDesign valveMecDesign = (ValveMechanicalDesign) mechanicalEquipment;

    valveMecDesign.getWeightTotal();
    valveMecDesign.getVolumeTotal();

    return this.mechanicalEquipment.getWeightTotal();
  }
}
