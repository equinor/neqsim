/*
 * staticMixer.java
 *
 * Created on 11. mars 2001, 01:49
 */

package neqsim.processSimulation.processEquipment.mixer;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class StaticMixer extends Mixer implements ProcessEquipmentInterface, MixerInterface{

    private static final long serialVersionUID = 1000;
    
    /** Creates new staticMixer */
    public StaticMixer() {
    }
    
    public StaticMixer(String name) {
        super(name);
    }
    
    public void mixStream(){
        int index = 0;
        String compName = new String();
        for(int k=1;k<streams.size();k++){
            
            for(int i=0;i<((SystemInterface) ((StreamInterface) streams.get(k)).getThermoSystem()).getPhases()[0].getNumberOfComponents();i++){
                
                boolean gotComponent=false;
                String componentName = ((SystemInterface) ((StreamInterface) streams.get(k)).getThermoSystem()).getPhases()[0].getComponents()[i].getName();
                //System.out.println("adding: " + componentName);
                double moles = ((SystemInterface) ((StreamInterface) streams.get(k)).getThermoSystem()).getPhases()[0].getComponents()[i].getNumberOfmoles();
                //System.out.println("moles: " + moles + "  " + mixedStream.getThermoSystem().getPhases()[0].getNumberOfComponents());
                for(int p=0;p<mixedStream.getThermoSystem().getPhases()[0].getNumberOfComponents();p++){
                    if(mixedStream.getThermoSystem().getPhases()[0].getComponents()[p].getName().equals(componentName)){
                        gotComponent=true;
                        index = ((SystemInterface) ((StreamInterface) streams.get(0)).getThermoSystem()).getPhases()[0].getComponents()[p].getComponentNumber();
                        compName = ((SystemInterface) ((StreamInterface) streams.get(0)).getThermoSystem()).getPhases()[0].getComponents()[p].getComponentName();
                    }
                }
                
                if(gotComponent){
                    //System.out.println("adding moles starting....");
                    mixedStream.getThermoSystem().addComponent(compName, moles, 0);
                    //mixedStream.getThermoSystem().init_x_y();
                    //System.out.println("adding moles finished");
                }
                else {
                    //System.out.println("ikke gaa hit");
                    mixedStream.getThermoSystem().addComponent(compName, moles, 0);
                }
            }
        }
    }
    
    public double guessTemperature(){
        double gtemp=0;
        for(int k=0;k<streams.size();k++){
            gtemp += ((StreamInterface) streams.get(k)).getThermoSystem().getTemperature()*((StreamInterface) streams.get(k)).getThermoSystem().getNumberOfMoles()/mixedStream.getThermoSystem().getNumberOfMoles();
            
        }
        return gtemp;
    }
    
    public double calcMixStreamEnthalpy(){
        double enthalpy=0;
        for(int k=0;k<streams.size();k++){
            ((StreamInterface) streams.get(k)).getThermoSystem().init(3);
            enthalpy += ((StreamInterface) streams.get(k)).getThermoSystem().getEnthalpy();
            System.out.println("total enthalpy k : " +((SystemInterface) ((StreamInterface) streams.get(k)).getThermoSystem()).getEnthalpy());
        }
        System.out.println("total enthalpy of streams: " + enthalpy);
        return enthalpy;
    }
    
    
    public void run(){
        double enthalpy = 0.0;
        for(int k=0;k<streams.size();k++){
            ((StreamInterface) streams.get(k)).getThermoSystem().init(3);
            enthalpy += ((StreamInterface) streams.get(k)).getThermoSystem().getEnthalpy();
        }
        mixedStream.setThermoSystem(((SystemInterface) ((StreamInterface) streams.get(0)).getThermoSystem().clone()));
        mixedStream.getThermoSystem().setNumberOfPhases(2);
        mixedStream.getThermoSystem().reInitPhaseType();
        mixStream();
        testOps = new ThermodynamicOperations(mixedStream.getThermoSystem());
        testOps.PHflash(enthalpy, 0);
        //System.out.println("temp " + mixedStream.getThermoSystem().getTemperature());
        mixedStream.getThermoSystem().init(3);
    }
    
    public String getName(){
        return name;
    }
    
    public void runTransient() {
    }
    
}
