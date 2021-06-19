package neqsim.processSimulation.costEstimation;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 * @author esol
 */
public class UnitCostEstimateBaseClass implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    private double costPerWeightUnit = 1000.0;
    public MechanicalDesign mechanicalEquipment = null;

    public UnitCostEstimateBaseClass() {

    }

    public UnitCostEstimateBaseClass(MechanicalDesign mechanicalEquipment) {
        this.mechanicalEquipment = mechanicalEquipment;
    }

    /**
     * @return the totaltCost
     */
    public double getTotaltCost() {
        return this.mechanicalEquipment.getWeightTotal() * costPerWeightUnit;
    }

}
