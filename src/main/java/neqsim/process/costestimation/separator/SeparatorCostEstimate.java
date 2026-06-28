package neqsim.process.costestimation.separator;

import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;

/**
 * Cost estimation class for separators.
 *
 * <p>
 * This class provides separator-specific cost estimation methods using chemical engineering cost correlations for
 * pressure vessels.
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
   * @param mechanicalEquipment a {@link neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign} object
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

    // Use the vessel internal volume (m3) as the Turton capacity basis. Feeding shell
    // weight (kg) into the vertical-vessel coefficients over-estimates large vessels by
    // an order of magnitude, so derive the volume from the sized diameter and length.
    double diameter = sepMecDesign.getInnerDiameter();
    double length = sepMecDesign.getTantanLength();
    double volume = 0.0;
    if (diameter > 0 && length > 0) {
      volume = Math.PI * Math.pow(diameter / 2.0, 2.0) * length;
    }
    if (volume <= 0) {
      volume = sepMecDesign.getVolumeTotal();
    }
    if (volume <= 0) {
      return 0.0;
    }

    // Vertical-vessel correlation is the common default for process separators.
    return getCostCalculator().calcVerticalVesselCostByVolume(volume);
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
