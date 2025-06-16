/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.mechanicalDesign;

import java.util.ArrayList;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processSystem.ProcessSystem;

/**
 *
 * @author esol
 */
public class SystemMechanicalDesign {

    private static final long serialVersionUID = 1000;

    ProcessSystem processSystem = null;
    double totalPlotSpace = 0.0,totalVolume=0.0,totalWeight=0.0;
int numberOfModules = 0;
    public SystemMechanicalDesign(ProcessSystem processSystem) {
        this.processSystem = processSystem;
    }

    public void setCompanySpecificDesignStandards(String name) {
        for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
            ((ProcessEquipmentInterface) processSystem.getUnitOperations().get(i)).getMechanicalDesign().setCompanySpecificDesignStandards(name);
        }

    }

    public void runDesignCalculation() {
        totalPlotSpace = 0.0;
        totalVolume = 0.0;
        totalWeight = 0.0;
        numberOfModules = 0;

        ArrayList names = processSystem.getAllUnitNames();
        for (int i = 0; i < names.size(); i++) {
            try {
                if (!((ProcessEquipmentInterface) processSystem.getUnit((String) names.get(i)) == null)) {
                    ((ProcessEquipmentInterface) processSystem.getUnit((String) names.get(i))).getMechanicalDesign().calcDesign();
                    totalPlotSpace += ((ProcessEquipmentInterface) processSystem.getUnit((String) names.get(i))).getMechanicalDesign().getModuleHeight() * ((ProcessEquipmentInterface) processSystem.getUnit((String) names.get(i))).getMechanicalDesign().getModuleLength();
                    totalVolume += ((ProcessEquipmentInterface) processSystem.getUnit((String) names.get(i))).getMechanicalDesign().getVolumeTotal();
                    totalWeight += ((ProcessEquipmentInterface) processSystem.getUnit((String) names.get(i))).getMechanicalDesign().getWeightTotal();
                    numberOfModules++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public void setDesign() {
        for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
            ((ProcessEquipmentInterface) processSystem.getUnitOperations().get(i)).getMechanicalDesign().setDesign();
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
