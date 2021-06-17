package neqsim.processSimulation.costEstimation.compressor;

import neqsim.processSimulation.costEstimation.UnitCostEstimateBaseClass;
import neqsim.processSimulation.mechanicalDesign.compressor.CompressorMechanicalDesign;

/**
 *
 * @author ESOL
 */
public class CompressorCostEstimate extends UnitCostEstimateBaseClass {

    private static final long serialVersionUID = 1000;

    public CompressorCostEstimate(CompressorMechanicalDesign mechanicalEquipment) {
        super(mechanicalEquipment);
    }

    @Override
	public double getTotaltCost() {
        CompressorMechanicalDesign sepMecDesign = (CompressorMechanicalDesign) mechanicalEquipment;

        sepMecDesign.getWeightTotal();
        sepMecDesign.getVolumeTotal();

        return this.mechanicalEquipment.getWeightTotal();
    }
}
