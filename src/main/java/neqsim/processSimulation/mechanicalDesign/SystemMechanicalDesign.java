package neqsim.processSimulation.mechanicalDesign;

import java.util.ArrayList;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processSystem.ProcessSystem;

/**
 * <p>
 * SystemMechanicalDesign class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SystemMechanicalDesign implements java.io.Serializable {
    private static final long serialVersionUID = 1000;

    ProcessSystem processSystem = null;
    double totalPlotSpace = 0.0, totalVolume = 0.0, totalWeight = 0.0;
    int numberOfModules = 0;

    /**
     * <p>
     * Constructor for SystemMechanicalDesign.
     * </p>
     *
     * @param processSystem a {@link neqsim.processSimulation.processSystem.ProcessSystem} object
     */
    public SystemMechanicalDesign(ProcessSystem processSystem) {
        this.processSystem = processSystem;
    }

    /**
     * <p>
     * setCompanySpecificDesignStandards.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setCompanySpecificDesignStandards(String name) {
        for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
            processSystem.getUnitOperations().get(i).getMechanicalDesign()
                    .setCompanySpecificDesignStandards(name);
        }
    }

    /**
     * <p>
     * runDesignCalculation.
     * </p>
     */
    public void runDesignCalculation() {
        ArrayList<String> names = processSystem.getAllUnitNames();
        for (int i = 0; i < names.size(); i++) {
            try {
                if (!((ProcessEquipmentInterface) processSystem.getUnit(names.get(i)) == null)) {
                    ((ProcessEquipmentInterface) processSystem.getUnit(names.get(i)))
                            .getMechanicalDesign().calcDesign();
                    totalPlotSpace += ((ProcessEquipmentInterface) processSystem
                            .getUnit(names.get(i))).getMechanicalDesign().getModuleHeight()
                            * ((ProcessEquipmentInterface) processSystem.getUnit(names.get(i)))
                                    .getMechanicalDesign().getModuleLength();
                    totalVolume += ((ProcessEquipmentInterface) processSystem.getUnit(names.get(i)))
                            .getMechanicalDesign().getVolumeTotal();
                    totalWeight += ((ProcessEquipmentInterface) processSystem.getUnit(names.get(i)))
                            .getMechanicalDesign().getWeightTotal();
                    numberOfModules++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * <p>
     * setDesign.
     * </p>
     */
    public void setDesign() {
        for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
            processSystem.getUnitOperations().get(i).getMechanicalDesign().setDesign();
        }
    }

    /**
     * <p>
     * Getter for the field <code>totalPlotSpace</code>.
     * </p>
     *
     * @return a double
     */
    public double getTotalPlotSpace() {
        return totalPlotSpace;
    }

    /**
     * <p>
     * Getter for the field <code>totalVolume</code>.
     * </p>
     *
     * @return a double
     */
    public double getTotalVolume() {
        return totalVolume;
    }

    /**
     * <p>
     * Getter for the field <code>totalWeight</code>.
     * </p>
     *
     * @return a double
     */
    public double getTotalWeight() {
        return totalWeight;
    }

    /**
     * <p>
     * getTotalNumberOfModules.
     * </p>
     *
     * @return a int
     */
    public int getTotalNumberOfModules() {
        return numberOfModules;
    }
}
