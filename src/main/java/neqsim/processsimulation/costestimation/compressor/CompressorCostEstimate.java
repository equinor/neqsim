package neqsim.processsimulation.costestimation.compressor;

import neqsim.processsimulation.costestimation.UnitCostEstimateBaseClass;
import neqsim.processsimulation.mechanicaldesign.compressor.CompressorMechanicalDesign;

/**
 * <p>
 * CompressorCostEstimate class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class CompressorCostEstimate extends UnitCostEstimateBaseClass {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for CompressorCostEstimate.
   * </p>
   *
   * @param mechanicalEquipment a
   *        {@link neqsim.processsimulation.mechanicaldesign.compressor.CompressorMechanicalDesign}
   *        object
   */
  public CompressorCostEstimate(CompressorMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
  }

  /** {@inheritDoc} */
  @Override
  public double getTotaltCost() {
    CompressorMechanicalDesign sepMecDesign = (CompressorMechanicalDesign) mechanicalEquipment;

    sepMecDesign.getWeightTotal();
    sepMecDesign.getVolumeTotal();

    return this.mechanicalEquipment.getWeightTotal();
  }
}
