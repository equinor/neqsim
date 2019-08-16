/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:05
 */

package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseDuanSun;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 *
 * @author  Even Solbraa
 * @version
 */

/** This class defines a thermodynamic system using the SRK EoS and Pitze for liquids 
 */
public class SystemDuanSun extends SystemEos {

    private static final long serialVersionUID = 1000;
    /** Creates a thermodynamic system using the SRK equation of state. */
    //  SystemSrkEos clonedSystem;
    
    public SystemDuanSun(){
        super();
        modelName = "Duan-Sun-model";
        attractiveTermNumber = 0;
        phaseArray[0] = new PhaseSrkEos();
        for (int i=1;i<numberOfPhases;i++){
            phaseArray[i] = new PhaseDuanSun();//modifiedWS();
        }
    }
    
    
    public SystemDuanSun(double T, double P){
        super(T,P);
        attractiveTermNumber = 0;
        modelName = "Duan-Sun-model";
        phaseArray[0] = new PhaseSrkEos();
        phaseArray[0].setTemperature(T);
        phaseArray[0].setPressure(P);
        for (int i=1;i<numberOfPhases;i++){
            phaseArray[i] = new PhaseDuanSun();//new PhaseGENRTLmodifiedWS();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }
    
    public SystemDuanSun(double T, double P, boolean solidCheck) {
        this(T,P);
        attractiveTermNumber = 0;
        numberOfPhases = 4;
        maxNumberOfPhases = 4;
        modelName = "Duan-Sun-model";
        solidPhaseCheck = solidCheck;
        
        phaseArray[0] = new PhaseSrkEos();
        phaseArray[0].setTemperature(T);
        phaseArray[0].setPressure(P);
        for (int i=1;i<numberOfPhases;i++){
            phaseArray[i] = new PhaseDuanSun();//new PhaseGENRTLmodifiedWS();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        
        if(solidPhaseCheck){
            //System.out.println("here first");
            phaseArray[numberOfPhases-1] = new PhasePureComponentSolid();
            phaseArray[numberOfPhases-1].setTemperature(T);
            phaseArray[numberOfPhases-1].setPressure(P);
            phaseArray[numberOfPhases-1].setRefPhase(phaseArray[1].getRefPhase());
        }
    }
    
    
    public Object clone(){
        SystemDuanSun clonedSystem = null;
        try{
            clonedSystem = (SystemDuanSun) super.clone();
        }
        catch(Exception e) {
            logger.error("Cloning failed.", e);
        }
        
        
//        for(int i = 0; i < numberOfPhases; i++) {
//            clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
//        }
        
        return clonedSystem;
    }
    
    
}