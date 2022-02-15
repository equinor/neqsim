package neqsim.processSimulation.costEstimation;

import java.util.ArrayList;
import java.util.Objects;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processSystem.ProcessSystem;

/**
 * <p>
 * CostEstimateBaseClass class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class CostEstimateBaseClass implements java.io.Serializable {
    private static final long serialVersionUID = 1000;

    ProcessSystem procesSystem = null;
    private double CAPEXperWeight = 1000.0; // KNOK/tones

    /**
     * <p>
     * Constructor for CostEstimateBaseClass.
     * </p>
     */
    public CostEstimateBaseClass() {}

    /**
     * <p>
     * Constructor for CostEstimateBaseClass.
     * </p>
     *
     * @param procesSystem a {@link neqsim.processSimulation.processSystem.ProcessSystem} object
     */
    public CostEstimateBaseClass(ProcessSystem procesSystem) {
        this.procesSystem = procesSystem;
    }

    /**
     * <p>
     * getWeightBasedCAPEXEstimate.
     * </p>
     *
     * @return a double
     */
    public double getWeightBasedCAPEXEstimate() {
        return procesSystem.getSystemMechanicalDesign().getTotalWeight() * CAPEXperWeight;
    }

    /**
     * <p>
     * getCAPEXestimate.
     * </p>
     *
     * @return a double
     */
    public double getCAPEXestimate() {
        double cost = 0;
        ArrayList<String> names = procesSystem.getAllUnitNames();
        for (int i = 0; i < names.size(); i++) {
            try {
                if (!((ProcessEquipmentInterface) procesSystem.getUnit(names.get(i)) == null)) {
                    cost += ((ProcessEquipmentInterface) procesSystem.getUnit(names.get(i)))
                            .getMechanicalDesign().getCostEstimate().getTotaltCost();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return cost;
    }

    /**
     * <p>
     * getCAPEXperWeight.
     * </p>
     *
     * @return the CAPEXperWeight
     */
    public double getCAPEXperWeight() {
        return CAPEXperWeight;
    }

    @Override
    public int hashCode() {
        return Objects.hash(CAPEXperWeight, procesSystem);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CostEstimateBaseClass other = (CostEstimateBaseClass) obj;
        return Double.doubleToLongBits(CAPEXperWeight) == Double
                .doubleToLongBits(other.CAPEXperWeight)
                && Objects.equals(procesSystem, other.procesSystem);
    }
}
