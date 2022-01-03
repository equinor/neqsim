package neqsim.processSimulation.costEstimation.compressor;

import neqsim.processSimulation.costEstimation.UnitCostEstimateBaseClass;
import neqsim.processSimulation.mechanicalDesign.compressor.CompressorMechanicalDesign;

/**
 * <p>CompressorCostEstimate class.</p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class CompressorCostEstimate extends UnitCostEstimateBaseClass {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for CompressorCostEstimate.</p>
     *
     * @param mechanicalEquipment a {@link neqsim.processSimulation.mechanicalDesign.compressor.CompressorMechanicalDesign} object
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
