package neqsim.processSimulation.costEstimation.valve;

import neqsim.processSimulation.costEstimation.UnitCostEstimateBaseClass;
import neqsim.processSimulation.mechanicalDesign.valve.ValveMechanicalDesign;

public class ValveCostEstimate extends UnitCostEstimateBaseClass {
    private static final long serialVersionUID = 1000;

    public ValveCostEstimate(ValveMechanicalDesign mechanicalEquipment) {
        super(mechanicalEquipment);
    }

    @Override
	public double getTotaltCost() {
        ValveMechanicalDesign valveMecDesign = (ValveMechanicalDesign) mechanicalEquipment;

        valveMecDesign.getWeightTotal();
        valveMecDesign.getVolumeTotal();

        return this.mechanicalEquipment.getWeightTotal();
    }
}
