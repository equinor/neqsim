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
package neqsim.processSimulation.processSystem.processModules;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.compressor.CompressorInterface;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessModuleBaseClass;

/**
 *
 * @author ESOL
 */
public class PropaneCoolingModule extends ProcessModuleBaseClass {

    /**
     * @param condenserTemperature the condenserTemperature to set
     */
    public void setCondenserTemperature(double condenserTemperature) {
        this.condenserTemperature = condenserTemperature;
    }

    /**
     * @param vaporizerTemperature the vaporizerTemperature to set
     */
    public void setVaporizerTemperature(double vaporizerTemperature) {
        this.vaporizerTemperature = vaporizerTemperature;
    }

    private static final long serialVersionUID = 1000;

    StreamInterface refrigerantStream;
    private double condenserTemperature = 273.15 + 30.0; // Kelvin
    private double vaporizerTemperature = 273.15 - 40.0; // Kelvin

    @Override
	public void addInputStream(String streamName, StreamInterface stream) {
        if (streamName.equals("refrigerant")) {
            this.refrigerantStream = stream;
        }
    }

    @Override
	public StreamInterface getOutputStream(String streamName) {
        if (!isInitializedStreams) {
            initializeStreams();
        }
        if (streamName.equals("refrigerant")) {
            return this.refrigerantStream;
        } else if (streamName.equals("refrigerant...")) {
            return this.refrigerantStream;
        } else {
            return null;
        }
    }

    @Override
	public void initializeModule() {
        isInitializedModule = true;

        refrigerantStream.getThermoSystem().setTemperature(condenserTemperature);
        ((Stream) refrigerantStream).setSpecification("bubT");
        refrigerantStream.run();

        ThrottlingValve JTvalve = new ThrottlingValve(refrigerantStream);

        Cooler cooler = new Cooler("propane evaporator", JTvalve.getOutStream());
        cooler.setPressureDrop(0.35);
        cooler.setSpecification("out stream");

        Stream stream_2 = new Stream(cooler.getOutStream());
        stream_2.setSpecification("dewT");
        stream_2.getThermoSystem().setTemperature(vaporizerTemperature);
        stream_2.run();

        cooler.setOutStream(stream_2);
        JTvalve.setOutletPressure(stream_2.getPressure());

        Compressor compressor1 = new Compressor("propane compressor", stream_2);
        // compressor1.setIsentropicEfficiency(0.75);
        // compressor1.setPower(180000);
        compressor1.setOutletPressure(refrigerantStream.getPressure());

        Heater condenser = new Heater("propane condenser", compressor1.getOutStream());
        condenser.setPressureDrop(0.07);
        condenser.setSpecification("out stream");
        condenser.setOutStream((Stream) refrigerantStream);

        System.out.println("adding operations....");
        getOperations().add(refrigerantStream);
        getOperations().add(JTvalve);
        getOperations().add(cooler);
        getOperations().add(stream_2);
        getOperations().add(compressor1);
        getOperations().add(condenser);
        System.out.println("finished adding operations....");
    }

    @Override
	public void run() {
        if (!isInitializedModule) {
            initializeModule();
        }
        System.out.println("running model....");
        getOperations().run();

        // gasExitStream = secondStageAfterCooler.getOutStream();
    }

    @Override
	public void initializeStreams() {
        isInitializedStreams = true;

    }

    @Override
	public void runTransient(double dt) {
        getOperations().runTransient();
    }

    @Override
	public void calcDesign() {
        // design is done here //
    }

    @Override
	public void setDesign() {
        // set design is done here //
    }

    @Override
	public void setSpecification(String specificationName, double value) {
        if (specificationName.equals("vaporizerTemperature")) {
            setVaporizerTemperature(value);
        } else if (specificationName.equals("condenserTemperature")) {
            setCondenserTemperature(value);
        }
    }

    public static void main(String[] args) {

        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos(273.15 - 20, 1);
        testSystem.addComponent("propane", 0.30);
        testSystem.createDatabase(true);

        Stream porpane = new Stream(testSystem);
        PropaneCoolingModule propaneModule = new PropaneCoolingModule();
        propaneModule.setCondenserTemperature(273.15 + 30);
        propaneModule.setVaporizerTemperature(273.15 - 40);

        propaneModule.addInputStream("refrigerant", porpane);
        propaneModule.run();

        double compressorWork = ((CompressorInterface) propaneModule.getOperations().getUnit("propane compressor"))
                .getEnergy();

        double evaporatorDuty = ((Cooler) propaneModule.getOperations().getUnit("propane evaporator")).getEnergyInput();
        double evaporatorPressure = ((Cooler) propaneModule.getOperations().getUnit("propane evaporator"))
                .getOutStream().getPressure();
        double evaporatorTemperature = ((Cooler) propaneModule.getOperations().getUnit("propane evaporator"))
                .getOutStream().getTemperature();

        double condenserDuty = ((Heater) propaneModule.getOperations().getUnit("propane condenser")).getEnergyInput();
        double condenserPressure = ((Heater) propaneModule.getOperations().getUnit("propane condenser")).getOutStream()
                .getPressure();
        double condenserTemperature = ((Heater) propaneModule.getOperations().getUnit("propane condenser"))
                .getOutStream().getTemperature();

        System.out.println("Compressor work " + compressorWork + " W");

        System.out.println("evaporator duty " + evaporatorDuty + " W");
        System.out.println("evaporator temperature " + (evaporatorTemperature - 273.15) + " C");
        System.out.println("evaporator pressure " + evaporatorPressure + " bara");

        System.out.println("condenser duty " + condenserDuty + " W");
        System.out.println("condenser temperature " + (condenserTemperature - 273.15) + " C");
        System.out.println("condenser pressure " + condenserPressure + " bara");
        // ((Cooler) propaneModule.getOperations().getUnit("propane
        // evaporator")).getInStream().displayResult();
        // ((Cooler) propaneModule.getOperations().getUnit("propane
        // evaporator")).getOutStream().displayResult();

        // TT ((CompressorInterface) propaneModule.getOperations().getUnit("propane
        // compressor")).displayResult();
        // ((CompressorInterface) propaneModule.getOperations().getUnit("propane
        // compressor")).getOutStream().displayResult();
        // ((Heater) propaneModule.getOperations().getUnit("propane
        // condenser")).getInStream().displayResult();
        // ((Heater) propaneModule.getOperations().getUnit("propane
        // condenser")).getOutStream().displayResult();
    }
}
