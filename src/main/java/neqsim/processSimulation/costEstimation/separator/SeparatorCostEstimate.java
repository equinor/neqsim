package neqsim.processSimulation.costEstimation.separator;

import neqsim.processSimulation.costEstimation.UnitCostEstimateBaseClass;
import neqsim.processSimulation.mechanicalDesign.separator.SeparatorMechanicalDesign;

/**
 *
 * @author ESOL
 */
public class SeparatorCostEstimate extends UnitCostEstimateBaseClass {
    private static final long serialVersionUID = 1000;

    public SeparatorCostEstimate(SeparatorMechanicalDesign mechanicalEquipment) {
        super(mechanicalEquipment);
    }

    @Override
    public double getTotaltCost() {
        SeparatorMechanicalDesign sepMecDesign = (SeparatorMechanicalDesign) mechanicalEquipment;

        sepMecDesign.getWeightTotal();
        sepMecDesign.getVolumeTotal();

        return this.mechanicalEquipment.getWeightTotal();
    }
}
