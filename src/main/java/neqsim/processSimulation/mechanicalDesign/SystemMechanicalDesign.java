package neqsim.processSimulation.mechanicalDesign;

import java.util.ArrayList;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processSystem.ProcessSystem;

/**
 *
 * @author esol
 */
public class SystemMechanicalDesign implements java.io.Serializable {
    private static final long serialVersionUID = 1000;

    ProcessSystem processSystem = null;
    double totalPlotSpace = 0.0, totalVolume = 0.0, totalWeight = 0.0;
    int numberOfModules = 0;

    public SystemMechanicalDesign(ProcessSystem processSystem) {
        this.processSystem = processSystem;
    }

    public void setCompanySpecificDesignStandards(String name) {
        for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
            processSystem.getUnitOperations().get(i).getMechanicalDesign()
                    .setCompanySpecificDesignStandards(name);
        }
    }

    public void runDesignCalculation() {
        ArrayList<String> names = processSystem.getAllUnitNames();
        for (int i = 0; i < names.size(); i++) {
            try {
                if (!((ProcessEquipmentInterface) processSystem
                        .getUnit((String) names.get(i)) == null)) {
                    ((ProcessEquipmentInterface) processSystem.getUnit((String) names.get(i)))
                            .getMechanicalDesign().calcDesign();
                    totalPlotSpace += ((ProcessEquipmentInterface) processSystem
                            .getUnit((String) names.get(i))).getMechanicalDesign().getModuleHeight()
                            * ((ProcessEquipmentInterface) processSystem
                                    .getUnit((String) names.get(i))).getMechanicalDesign()
                                            .getModuleLength();
                    totalVolume += ((ProcessEquipmentInterface) processSystem
                            .getUnit((String) names.get(i))).getMechanicalDesign().getVolumeTotal();
                    totalWeight += ((ProcessEquipmentInterface) processSystem
                            .getUnit((String) names.get(i))).getMechanicalDesign().getWeightTotal();
                    numberOfModules++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setDesign() {
        for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
            processSystem.getUnitOperations().get(i).getMechanicalDesign().setDesign();
        }
    }

    public double getTotalPlotSpace() {
        return totalPlotSpace;
    }

    public double getTotalVolume() {
        return totalVolume;
    }

    public double getTotalWeight() {
        return totalWeight;
    }

    public int getTotalNumberOfModules() {
        return numberOfModules;
    }
}
