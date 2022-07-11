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

    private ProcessSystem processSystem;
    private double CAPEXperWeight = 1000.0; // KNOK/tones

    /**
     * <p>
     * Constructor for CostEstimateBaseClass.
     * </p>
     *
     * @param process a {@link neqsim.processSimulation.processSystem.ProcessSystem}
     *                object
     */
    public CostEstimateBaseClass(ProcessSystem process) {
        this.processSystem = process;
    }

    /**
     * 
     * @param process input process
     * @param costFactor cost factor
     */
    public CostEstimateBaseClass(ProcessSystem process, double costFactor) {
        this(process);
        this.CAPEXperWeight = costFactor;
    }

    /**
     * <p>
     * getWeightBasedCAPEXEstimate.
     * </p>
     *
     * @return a double
     */
    public double getWeightBasedCAPEXEstimate() {
        return this.processSystem.getSystemMechanicalDesign().getTotalWeight() * CAPEXperWeight;
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
        ArrayList<String> names = this.processSystem.getAllUnitNames();
        for (int i = 0; i < names.size(); i++) {
            try {
                if (!((ProcessEquipmentInterface) this.processSystem.getUnit(names.get(i)) == null)) {
                    cost += ((ProcessEquipmentInterface) this.processSystem.getUnit(names.get(i)))
                            .getMechanicalDesign().getCostEstimate().getTotaltCost();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return cost;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(CAPEXperWeight);
    }

    /** {@inheritDoc} */
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
                .doubleToLongBits(other.CAPEXperWeight);
    }
}
