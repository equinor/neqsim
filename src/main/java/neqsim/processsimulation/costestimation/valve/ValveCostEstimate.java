package neqsim.processsimulation.costestimation.valve;

import neqsim.processsimulation.costestimation.UnitCostEstimateBaseClass;
import neqsim.processsimulation.mechanicaldesign.valve.ValveMechanicalDesign;

/**
 * <p>
 * ValveCostEstimate class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ValveCostEstimate extends UnitCostEstimateBaseClass {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ValveCostEstimate.
   * </p>
   *
   * @param mechanicalEquipment a
   *        {@link neqsim.processsimulation.mechanicaldesign.valve.ValveMechanicalDesign} object
   */
  public ValveCostEstimate(ValveMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
  }

  /** {@inheritDoc} */
  @Override
  public double getTotaltCost() {
    ValveMechanicalDesign valveMecDesign = (ValveMechanicalDesign) mechanicalEquipment;

    valveMecDesign.getWeightTotal();
    valveMecDesign.getVolumeTotal();

    return this.mechanicalEquipment.getWeightTotal();
  }
}
