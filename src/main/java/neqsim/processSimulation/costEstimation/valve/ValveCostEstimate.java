package neqsim.processSimulation.costEstimation.valve;

import neqsim.processSimulation.costEstimation.UnitCostEstimateBaseClass;
import neqsim.processSimulation.mechanicalDesign.valve.ValveMechanicalDesign;

/**
 * <p>ValveCostEstimate class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ValveCostEstimate extends UnitCostEstimateBaseClass {
    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for ValveCostEstimate.</p>
     *
     * @param mechanicalEquipment a {@link neqsim.processSimulation.mechanicalDesign.valve.ValveMechanicalDesign} object
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
