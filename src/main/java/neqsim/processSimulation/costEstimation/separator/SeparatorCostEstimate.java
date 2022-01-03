package neqsim.processSimulation.costEstimation.separator;

import neqsim.processSimulation.costEstimation.UnitCostEstimateBaseClass;
import neqsim.processSimulation.mechanicalDesign.separator.SeparatorMechanicalDesign;

/**
 * <p>SeparatorCostEstimate class.</p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class SeparatorCostEstimate extends UnitCostEstimateBaseClass {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for SeparatorCostEstimate.</p>
     *
     * @param mechanicalEquipment a {@link neqsim.processSimulation.mechanicalDesign.separator.SeparatorMechanicalDesign} object
     */
    public SeparatorCostEstimate(SeparatorMechanicalDesign mechanicalEquipment) {
        super(mechanicalEquipment);
    }

	/** {@inheritDoc} */
    @Override
	public double getTotaltCost() {
        SeparatorMechanicalDesign sepMecDesign = (SeparatorMechanicalDesign) mechanicalEquipment;

        sepMecDesign.getWeightTotal();
        sepMecDesign.getVolumeTotal();

        return this.mechanicalEquipment.getWeightTotal();
    }
}
