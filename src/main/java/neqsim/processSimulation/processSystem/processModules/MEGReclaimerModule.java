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
 * SnøhvitCO2RemovalModule.java
 *
 * Created on 1. november 2006, 20:33
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package neqsim.processSimulation.processSystem.processModules;

import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessModuleBaseClass;

/**
 *
 * @author ESOL
 */
public class MEGReclaimerModule extends ProcessModuleBaseClass {

    private static final long serialVersionUID = 1000;
    
    protected StreamInterface streamToReclaimer = null, streamToWaterRemoval = null, streamFromBoosterCompressor=null, streamWithWaste=null;
    
    ThrottlingValve inletValve = null;
    Mixer inletMixer = null;
    protected Separator flashSeparator = null;
    Pump MEGRecircPump = null;
    Heater MEGrecircHeater = null;
    ThrottlingValve recircValve = null;
    Heater vacumCooler = null;
    
    double reclaimerPressure=0.17;
    
    /** Creates a new instance of SnøhvitCO2RemovalModule */
    public MEGReclaimerModule() {
    }
    
    public void addInputStream(String streamName, StreamInterface stream){
        if(streamName.equals("streamToReclaimer")) {
            this.streamToReclaimer = stream;
        }
    }
    
    public StreamInterface getOutputStream(String streamName){
        if(!isInitializedStreams) {
            initializeStreams();
        }
        if(streamName.equals("streamToWaterRemoval")) {
            return this.streamToWaterRemoval;
        } else {
            return null;
        }
    }
    
    public void initializeStreams(){
        isInitializedStreams = true;
        try{
            this.streamToWaterRemoval = (Stream) this.streamToReclaimer.clone();
            this.streamToWaterRemoval.setName("Desalted MEG stream");
            
            this.streamFromBoosterCompressor = (Stream) this.streamToReclaimer.clone();
            this.streamFromBoosterCompressor.setName("Stream from Booster Compressor");
            
            this.streamWithWaste = (Stream) this.streamToReclaimer.clone();
            this.streamWithWaste.setName("Reclaimer Waste Stream");
        } catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public void initializeModule(){
        isInitializedModule = true;
        
        inletValve = new ThrottlingValve(streamToReclaimer);
        inletValve.setOutletPressure(reclaimerPressure);
        inletValve.setIsoThermal(true);
        
        inletMixer = new Mixer();
        inletMixer.addStream(inletValve.getOutStream());
        
        flashSeparator = new Separator(inletMixer.getOutStream());
        
        MEGRecircPump = new Pump(flashSeparator.getLiquidOutStream());
        MEGRecircPump.setMolarFlow(50.0);
        MEGRecircPump.setOutletPressure(5.0);
        
        MEGrecircHeater = new Heater(MEGRecircPump.getOutStream());
        //MEGrecircHeater.setEnergyInput(5000.0);
        MEGrecircHeater.setOutTemperature(273+68.9);
        
        recircValve = new ThrottlingValve(MEGrecircHeater.getOutStream());
        recircValve.setOutletPressure(reclaimerPressure);
        recircValve.setIsoThermal(true);
        
        inletMixer.addStream(recircValve.getOutStream());
        
        vacumCooler = new Heater(flashSeparator.getGasOutStream());
        
        getOperations().add(streamToReclaimer);
        getOperations().add(inletValve);
        getOperations().add(inletMixer);
        getOperations().add(flashSeparator);
        getOperations().add(MEGRecircPump);
        getOperations().add(MEGrecircHeater);
        getOperations().add(recircValve);
    }
    
    public void run(){
        if(!isInitializedModule) {
            initializeModule();
        }
        for(int i=0;i<2;i++){
            getOperations().run();
            flashSeparator.displayResult();
            System.out.println("flow to vacum separator " + inletMixer.getOutStream().getThermoSystem().getTotalNumberOfMoles());
        }
        
        streamToWaterRemoval = flashSeparator.getGasOutStream();
        
    }
    
    public void runTransient(double dt){
        getOperations().runTransient();
    }
    
    public void setOperationPressure(double pressure){
        reclaimerPressure = pressure;
    }
    
    public static void main(String[] args){
        
        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15+30.0), 10.0);
        
        testSystem.addComponent("methane", 0.001);
        testSystem.addComponent("CO2", 0.001);
        testSystem.addComponent("MEG", 0.3);
        testSystem.addComponent("water", 0.7);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        
        Stream inletStream = new Stream(testSystem);
        inletStream.run();
        inletStream.displayResult();
        MEGReclaimerModule reclaimer = new MEGReclaimerModule();
        reclaimer.addInputStream("streamToReclaimer", inletStream);
        reclaimer.setOperationPressure(0.17);
        
        
      reclaimer.run();
       // reclaimer.displayResult();
        
    }
    
     public void calcDesign() {
        // design is done here //
    }

    public void setDesign() {
        // set design is done here //
    }
    
}
