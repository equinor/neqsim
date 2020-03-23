/*
 * Separator.java
 *
 * Created on 12. mars 2001, 19:48
 */

package neqsim.processSimulation.processEquipment.separator;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class ThreePhaseSeparator extends Separator implements ProcessEquipmentInterface, SeparatorInterface{

    private static final long serialVersionUID = 1000;
    
    StreamInterface waterOutStream = new Stream(waterSystem);
    
    /** Creates new Separator */
    public ThreePhaseSeparator() {
        super();
    }
    
    public ThreePhaseSeparator(StreamInterface inletStream) {
        super(inletStream);
    }
    
    public ThreePhaseSeparator(String name, StreamInterface inletStream) {
        super(name,inletStream);
    }
    
    public void setInletStream(StreamInterface inletStream){
        inletStreamMixer.addStream(inletStream);
        
        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasOutStream = new Stream(gasSystem);
        
        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidOutStream = new Stream(liquidSystem);
        
        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        waterSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        waterOutStream = new Stream(waterSystem);
    }
    
    public StreamInterface getWaterOutStream(){
        return waterOutStream;
    }
    
    public StreamInterface getOilOutStream(){
        return liquidOutStream;
    }
    
    public void run(){
        inletStreamMixer.run();
        thermoSystem = (SystemInterface) inletStreamMixer.getOutStream().getThermoSystem().clone();
        
        thermoSystem.setMultiPhaseCheck(true);
        thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoOps.TPflash();
//        thermoSystem.init(3);
//        thermoSystem.setMultiPhaseCheck(false);
        
//
//        //gasSystem = (SystemInterface) thermoSystem.phaseToSystem(0);
//        //gasOutStream.setThermoSystem(gasSystem);
        if(thermoSystem.hasPhaseType("gas")){
            gasOutStream.setThermoSystemFromPhase(thermoSystem, "gas");
        } else {
            gasOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
        }
//        //gasOutStream.run();
//
////        liquidSystem = (SystemInterface) thermoSystem.phaseToSystem(1);
////        liquidOutStream.setThermoSystem(liquidSystem);
        if(thermoSystem.hasPhaseType("oil")){
  //          thermoSystem.display();
            liquidOutStream.setThermoSystemFromPhase(thermoSystem, "oil");
  //          thermoSystem.display();
        } else {
            liquidOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
        }
//        //liquidOutStream.run();
//
////        waterSystem = (SystemInterface) thermoSystem.phaseToSystem(2);
////        waterOutStream.setThermoSystem(waterSystem);
        if(thermoSystem.hasPhaseType("aqueous")){
            waterOutStream.setThermoSystemFromPhase(thermoSystem, "aqueous");
        } else {
            waterOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
        }
//        //waterOutStream.run();
    }
    
    public void displayResult(){
        thermoSystem.display("from here " + getName());
//        gasOutStream.getThermoSystem().initPhysicalProperties();
//        waterOutStream.getThermoSystem().initPhysicalProperties();
//        try {
//            System.out.println("Gas Volume Flow Out " + gasOutStream.getThermoSystem().getPhase(0).getNumberOfMolesInPhase()*gasOutStream.getThermoSystem().getPhase(0).getMolarMass()/gasOutStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity()*3600.0 + " m^3/h");
//        } finally {
//        }
//        try {
//            waterOutStream.getThermoSystem().display();
//            waterOutStream.run();
//            System.out.println("Water/MEG Volume Flow Out " + waterOutStream.getThermoSystem().getPhase(0).getNumberOfMolesInPhase()*waterOutStream.getThermoSystem().getPhase(0).getMolarMass()/waterOutStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity()*3600.0 + " m^3/h");
//            System.out.println("Density MEG " + waterOutStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity());
//        } finally {
//        }
    }
    
    public String getName() {
        return name;
    }
    
    public void runTransient() {
    }
    
}
