/*
 * SnohvitCO2RemovalModule.java
 *
 * Created on 1. november 2006, 20:33
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package neqsim.processSimulation.util.example.modules;

import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processSystem.ProcessModuleBaseClass;

/**
 *
 * @author ESOL
 */
public class CO2RemovalModule extends ProcessModuleBaseClass {

    private static final long serialVersionUID = 1000;
    
    protected StreamInterface streamToAbsorber = null, streamFromAbsorber = null, gasFromCO2Stripper=null;
    
    protected Separator inletSeparator = null;
    
    /** Creates a new instance of SnohvitCO2RemovalModule */
    public CO2RemovalModule() {
    }
    
    public void addInputStream(String streamName, StreamInterface stream){
        if(streamName.equals("streamToAbsorber")) {
            this.streamToAbsorber = stream;
        }
    }
    
    public StreamInterface getOutputStream(String streamName){
        if(!isInitializedStreams) {
            initializeStreams();
        }
        if(streamName.equals("streamFromAbsorber")) {
            return this.streamFromAbsorber;
        } else {
            return null;
        }
    }
    
    public void run(){
        if(!isInitializedModule) {
            initializeModule();
        }
        getOperations().run();
        
        streamFromAbsorber = (Stream) inletSeparator.getGasOutStream().clone();
        streamFromAbsorber.getThermoSystem().addComponent("CO2",-streamFromAbsorber.getThermoSystem().getPhase(0).getComponent("CO2").getNumberOfMolesInPhase()*0.99);
        streamFromAbsorber.getThermoSystem().addComponent("MEG",-streamFromAbsorber.getThermoSystem().getPhase(0).getComponent("MEG").getNumberOfMolesInPhase()*0.99);
        streamFromAbsorber.getThermoSystem().init(1);
    }
    
    public void initializeStreams(){
        isInitializedStreams = true;
        try{
            this.streamFromAbsorber = (Stream) this.streamToAbsorber.clone();
            this.streamFromAbsorber.setName("Stream from ABsorber");
            
            this.gasFromCO2Stripper = (Stream) this.streamToAbsorber.clone();
            this.gasFromCO2Stripper.setName("Gas stream from Stripper");
        } catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public void initializeModule(){
        isInitializedModule = true;
        inletSeparator = new Separator(streamToAbsorber);
        
        getOperations().add(inletSeparator);
    }
    
    public void runTransient(double dt){
        getOperations().runTransient();
    }
    public void calcDesign() {
        
    }
    public void setDesign() {
    }
}
