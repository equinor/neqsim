/*
 * SystemModifiedFurstElectrolyteEos.java
 *
 * Created on 26. februar 2001, 17:38
 */

package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseElectrolyteCPA;
import neqsim.thermo.util.constants.FurstElectrolyteConstants;

/**
 *
 * @author  Even Solbraa
 * @version
 */
/** This class defines a thermodynamic system using the Electrolyte CPA EoS of Equinor */
public class SystemElectrolyteCPA extends SystemFurstElectrolyteEos {

    private static final long serialVersionUID = 1000;
    
    /** Creates new SystemModifiedFurstElectrolyteEos */
    public SystemElectrolyteCPA() {
        super();
        modelName = "Electrolyte-CPA-EOS";
        attractiveTermNumber = 0;
        for (int i=0;i<numberOfPhases;i++){
            phaseArray[i] = new PhaseElectrolyteCPA();
        }
        FurstElectrolyteConstants.setFurstParams("electrolyteCPA");
        this.useVolumeCorrection(false);
    }
    
    public SystemElectrolyteCPA(double T, double P) {
        super(T,P);
        attractiveTermNumber = 0;
        modelName = "Electrolyte-CPA-EOS";
        for (int i=0;i<numberOfPhases;i++){
            phaseArray[i] = new PhaseElectrolyteCPA();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        FurstElectrolyteConstants.setFurstParams("electrolyteCPA");
        this.useVolumeCorrection(false);
    }
    
    
    public Object clone(){
        SystemElectrolyteCPA clonedSystem = null;
        try{
            clonedSystem = (SystemElectrolyteCPA) super.clone();
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
        
        //        for(int i = 0; i < numberOfPhases; i++) {
        //            clonedSystem.phaseArray[i] =(PhaseElectrolyteCPA) phaseArray[i].clone();
        //        }
        
        return clonedSystem;
    }
    
    
    
}
