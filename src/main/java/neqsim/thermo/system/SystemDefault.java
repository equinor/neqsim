/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:05
 */

package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author  Even Solbraa
 * @version
 */

/** This class defines a thermodynamic system using the SRK equation of state
 */
public class SystemDefault extends SystemThermo {

    private static final long serialVersionUID = 1000;
    /** Creates a thermodynamic system using the SRK equation of state. */
    
    //  SystemSrkEos clonedSystem;
    double T=100,P=100;
    
    public SystemDefault(){
        super();
    }
    
    public SystemDefault(double T, double P) {
        super(T,P);
        this.T = T;
        this.P= P;
    }
    
    public void setPhase(PhaseInterface phaseType){
        for (int i=0;i<getMaxNumberOfPhases();i++){
            try{
                phaseArray[i] = phaseType.getClass().newInstance();
            }
            catch(Exception e){
                System.err.println("err " + e.toString());
            }
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }
}