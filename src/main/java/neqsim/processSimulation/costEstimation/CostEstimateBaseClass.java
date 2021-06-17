package neqsim.processSimulation.costEstimation;

import java.util.ArrayList;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processSystem.ProcessSystem;

/**
 *
 * @author ESOL
 */
public class CostEstimateBaseClass extends java.lang.Object implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    ProcessSystem procesSystem = null;
    private double CAPEXperWeight = 1000.0; // KNOK/tones

    public CostEstimateBaseClass() {

    }

    public CostEstimateBaseClass(ProcessSystem procesSystem) {
        this.procesSystem = procesSystem;
    }

    public double getWeightBasedCAPEXEstimate() {
        return procesSystem.getSystemMechanicalDesign().getTotalWeight() * CAPEXperWeight;
    }

    public double getCAPEXestimate() {
        double cost = 0;
        ArrayList names = procesSystem.getAllUnitNames();
        for (int i = 0; i < names.size(); i++) {
            try {
                if (!((ProcessEquipmentInterface) procesSystem.getUnit((String) names.get(i)) == null)) {
                    cost += ((ProcessEquipmentInterface) procesSystem.getUnit((String) names.get(i)))
                            .getMechanicalDesign().getCostEstimate().getTotaltCost();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return cost;
    }

    /**
     * @return the CAPEXperWeight
     */
    public double getCAPEXperWeight() {
        return CAPEXperWeight;
    }

}
