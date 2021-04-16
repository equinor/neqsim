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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.mechanicalDesign.pipeline;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.mechanicalDesign.designStandards.MaterialPipeDesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.PipelineDesignStandard;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.pipeline.AdiabaticPipe;
import neqsim.processSimulation.processEquipment.pipeline.Pipeline;
import neqsim.processSimulation.processEquipment.stream.Stream;

/**
 *
 * @author ESOL
 */
public class PipelineMechanicalDeisgn extends MechanicalDesign {

    private static final long serialVersionUID = 1000;

    double innerDiameter = 1.0;
    String designStandardCode = "ANSI/ASME Standard B31.8";

    public PipelineMechanicalDeisgn(ProcessEquipmentInterface equipment) {
        super(equipment);
    }

    public void readDesignSpecifications() {

        super.readDesignSpecifications();

        if (getDesignStandard().containsKey("material pipe design codes")) {
            ((MaterialPipeDesignStandard) getDesignStandard().get("material pipe design codes")).getDesignFactor();
        }
        if (getDesignStandard().containsKey("pipeline design codes")) {
            System.out.println("pressure vessel code standard: "
                    + getDesignStandard().get("pipeline design codes").getStandardName());
            wallThickness = ((PipelineDesignStandard) getDesignStandard().get("pipeline design codes"))
                    .calcPipelineWallThickness();
        } else {
            System.out.println("no pressure vessel code standard specified......");
        }

    }

    public void calcDesign() {
        super.calcDesign();

        Pipeline pipeline = (Pipeline) getProcessEquipment();

        double flow = ((AdiabaticPipe) getProcessEquipment()).getOutStream().getThermoSystem().getVolume() / 1e5;

        double innerArea = Math.PI * innerDiameter * innerDiameter / 4.0;

        double gasVelocity = flow / innerArea;
        double wallThickness = 0.0;

        // ASME/ANSI Code B31.8
        if (designStandardCode.equals("ANSI/ASME Standard B31.8")) {
            wallThickness = ((AdiabaticPipe) getProcessEquipment()).getMechanicalDesign().getMaxOperationPressure()
                    * innerDiameter
                    / (2.0 * ((AdiabaticPipe) getProcessEquipment()).getMechanicalDesign()
                            .getMaterialPipeDesignStandard().getDesignFactor()
                            * ((AdiabaticPipe) getProcessEquipment()).getMechanicalDesign()
                                    .getMaterialPipeDesignStandard().getEfactor()
                            * ((AdiabaticPipe) getProcessEquipment()).getMechanicalDesign()
                                    .getMaterialPipeDesignStandard().getTemperatureDeratingFactor()
                            * ((AdiabaticPipe) getProcessEquipment()).getMechanicalDesign()
                                    .getMaterialPipeDesignStandard().getMinimumYeildStrength());
        } else if (designStandardCode.equals("ANSI/ASME Standard B31.3")) {
            wallThickness = 0.0001; // to be implemented
            // ((AdiabaticPipe)
            // getProcessEquipment()).getMechanicalDesign().getMaxOperationPressure() *
            // innerDiameter / (2.0 * ((AdiabaticPipe)
            // getProcessEquipment()).getMechanicalDesign().getMaterialPipeDesignStandard().getDesignFactor()
            // * ((AdiabaticPipe)
            // getProcessEquipment()).getMechanicalDesign().getMaterialPipeDesignStandard().getEfactor()
            // * ((AdiabaticPipe)
            // getProcessEquipment()).getMechanicalDesign().getMaterialPipeDesignStandard().getTemperatureDeratingFactor()
            // * ((AdiabaticPipe)
            // getProcessEquipment()).getMechanicalDesign().getMaterialPipeDesignStandard().getMinimumYeildStrength());
        }
        // iterate to find correct diamter -> between 60-80 ft/sec9

        // double length = pipeline.getLength();
    }

    public static void main(String args[]) {

        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 20.0), 90.00);
        testSystem.addComponent("methane", 600e3, "kg/hr");
        testSystem.addComponent("ethane", 7.00e3, "kg/hr");
        testSystem.addComponent("propane", 12.0e3, "kg/hr");

        testSystem.createDatabase(true);
        testSystem.setMultiPhaseCheck(true);
        testSystem.setMixingRule(2);

        Stream stream_1 = new Stream("Stream1", testSystem);

        AdiabaticPipe pipe = new AdiabaticPipe(stream_1);
        pipe.setDiameter(1.0);
        pipe.setLength(1000.0);
        pipe.getMechanicalDesign().setMaxOperationPressure(100.0);
        pipe.getMechanicalDesign().setMaxOperationTemperature(273.155 + 60.0);
        pipe.getMechanicalDesign().setMinOperationPressure(50.0);
        pipe.getMechanicalDesign().setMaxDesignGassVolumeFlow(100.0);

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(pipe);

        // operations.run();
        operations.getSystemMechanicalDesign().setCompanySpecificDesignStandards("Statoil");
        operations.getSystemMechanicalDesign().runDesignCalculation();
        operations.getSystemMechanicalDesign().setDesign();
        operations.run();

    }
}
