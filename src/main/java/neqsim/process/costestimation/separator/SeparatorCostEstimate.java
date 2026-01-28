package neqsim.process.costestimation.separator;

import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;

/**
 * Cost estimation class for separators.
 *
 * <p>
 * This class provides separator-specific cost estimation methods using chemical engineering cost
 * correlations for pressure vessels.
 * </p>
 *
 * @author ESOL
 * @version 2.0
 */
public class SeparatorCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /**
   * Constructor for SeparatorCostEstimate.
   *
   * @param mechanicalEquipment a
   *        {@link neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign} object
   */
  public SeparatorCostEstimate(SeparatorMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("vessel");
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    if (mechanicalEquipment == null) {
      return 0.0;
    }

    SeparatorMechanicalDesign sepMecDesign = (SeparatorMechanicalDesign) mechanicalEquipment;

    double shellWeight = sepMecDesign.getWeigthVesselShell();
    if (shellWeight <= 0) {
      // Use total weight as fallback
      shellWeight = sepMecDesign.getWeightTotal();
    }

    if (shellWeight <= 0) {
      return 0.0;
    }

    // Determine vessel orientation (assume horizontal for 2-phase, vertical for 3-phase)
    // For now, use vertical vessel correlation as it's more common
    return getCostCalculator().calcVerticalVesselCost(shellWeight);
  }

  /** {@inheritDoc} */
  @Override
  public double getTotalCost() {
    if (totalModuleCost > 0) {
      return totalModuleCost;
    }

    SeparatorMechanicalDesign sepMecDesign = (SeparatorMechanicalDesign) mechanicalEquipment;

    sepMecDesign.getWeightTotal();
    sepMecDesign.getVolumeTotal();

    // Calculate cost estimate if not already done
    if (purchasedEquipmentCost <= 0) {
      calculateCostEstimate();
    }

    return totalModuleCost;
  }
}
