/*
 * staticMixer.java
 *
 * Created on 11. mars 2001, 01:49
 */

package neqsim.processSimulation.processEquipment.mixer;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class StaticPhaseMixer extends StaticMixer implements ProcessEquipmentInterface, MixerInterface{

    private static final long serialVersionUID = 1000;
    
    
    /** Creates new staticMixer */
    public StaticPhaseMixer() {
    }
    
    public StaticPhaseMixer(String name) {
        super(name);
    }
    
    
    public void mixStream(){
        int index = 0;
        String compName = new String();
        
        for(int k=1;k<streams.size();k++){
            
            for(int i=0;i<((SystemInterface) ((StreamInterface) streams.get(k)).getThermoSystem()).getPhases()[0].getNumberOfComponents();i++){
                
                boolean gotComponent=false;
                String componentName = ((SystemInterface) ((StreamInterface) streams.get(k)).getThermoSystem()).getPhases()[0].getComponents()[i].getName();
                System.out.println("adding: " + componentName);
                int numberOfPhases= ((StreamInterface) streams.get(k)).getThermoSystem().getNumberOfPhases();
                double[] moles = new double[numberOfPhases];
                int[] phaseType = new int[numberOfPhases];
                
                
                //
                // her maa man egentlig sjekke at phase typen er den samme !!! antar at begge er to fase elle gass - tofase
                for(int p=0;p<numberOfPhases;p++){
                    moles[p] = ((StreamInterface) streams.get(k)).getThermoSystem().getPhase(p).getComponents()[i].getNumberOfMolesInPhase();
                    phaseType[p] = ((StreamInterface) streams.get(k)).getThermoSystem().getPhase(p).getPhaseType();
                }
                if(k==1){
                    phaseType[0]=0;//
                    mixedStream.getThermoSystem().getPhase(1).setTemperature(((StreamInterface) streams.get(k)).getThermoSystem().getTemperature());
                }
                
                for(int p=0;p<mixedStream.getThermoSystem().getPhases()[0].getNumberOfComponents();p++){
                    if(mixedStream.getThermoSystem().getPhases()[0].getComponents()[p].getName().equals(componentName)){
                        gotComponent=true;
                        index = ((SystemInterface) ((StreamInterface) streams.get(0)).getThermoSystem()).getPhases()[0].getComponents()[p].getComponentNumber();
                        compName = ((SystemInterface) ((StreamInterface) streams.get(0)).getThermoSystem()).getPhases()[0].getComponents()[p].getComponentName();
                        
                    }
                }
                
                if(gotComponent){
                    System.out.println("adding moles starting....");
                    for(int p=0;p<numberOfPhases;p++){
                        if(phaseType[p]==0){
                            System.out.println("adding liq");
                            mixedStream.getThermoSystem().addComponent(compName, moles[p], 1);
                        }
                        else if(phaseType[p]==1){
                            System.out.println("adding gas");
                            mixedStream.getThermoSystem().addComponent(compName, moles[p], 0);
                        }
                        else{
                            System.out.println("not here....");
                        }
                    }
                    System.out.println("adding moles finished");
                }
                else {
                    System.out.println("ikke gaa hit");
                    for(int p=0;p<numberOfPhases;p++){
                        mixedStream.getThermoSystem().addComponent(compName, moles[p], p);
                    }
                }
            }
        }
        mixedStream.getThermoSystem().init_x_y();
        mixedStream.getThermoSystem().initBeta();
        mixedStream.getThermoSystem().init(2);
        
    }
    
    
    
    public void run(){
        double enthalpy = 0.0;
        for(int k=0;k<streams.size();k++){
            ((StreamInterface) streams.get(k)).getThermoSystem().init(3);
            enthalpy += ((StreamInterface) streams.get(k)).getThermoSystem().getEnthalpy();
        }
        
        mixedStream.setThermoSystem(((SystemInterface) ((StreamInterface) streams.get(0)).getThermoSystem().clone()));
         mixedStream.getThermoSystem().init(0);
        mixedStream.getThermoSystem().setBeta(1,1e-10);
        mixedStream.getThermoSystem().init(2);
        mixedStream.getThermoSystem().reInitPhaseType();
        
        mixStream();
        
        mixedStream.getThermoSystem().init(3);
        
    }
    
    public String getName() {
        return name;
    }
    
}
