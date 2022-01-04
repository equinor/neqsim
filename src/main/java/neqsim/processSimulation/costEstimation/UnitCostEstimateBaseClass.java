package neqsim.processSimulation.costEstimation;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 * <p>
 * UnitCostEstimateBaseClass class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class UnitCostEstimateBaseClass implements java.io.Serializable {
    private static final long serialVersionUID = 1000;

    private double costPerWeightUnit = 1000.0;
    public MechanicalDesign mechanicalEquipment = null;

    /**
     * <p>
     * Constructor for UnitCostEstimateBaseClass.
     * </p>
     */
    public UnitCostEstimateBaseClass() {}

    /**
     * <p>
     * Constructor for UnitCostEstimateBaseClass.
     * </p>
     *
     * @param mechanicalEquipment a
     *        {@link neqsim.processSimulation.mechanicalDesign.MechanicalDesign} object
     */
    public UnitCostEstimateBaseClass(MechanicalDesign mechanicalEquipment) {
        this.mechanicalEquipment = mechanicalEquipment;
    }

    /**
     * <p>
     * getTotaltCost.
     * </p>
     *
     * @return the totaltCost
     */
    public double getTotaltCost() {
        return this.mechanicalEquipment.getWeightTotal() * costPerWeightUnit;
    }
}
