/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:05
 */

package neqsim.thermo.system;
/**
 *
 * @author  Even Solbraa
 * @version
 */

/** This class defines a thermodynamic system using the SRK equation of state
 */
public class SystemPrMathiasCopeman extends SystemPrEos {

    private static final long serialVersionUID = 1000;
    /** Creates a thermodynamic system using the SRK equation of state. */
    //  SystemSrkEos clonedSystem;
    public SystemPrMathiasCopeman(){
        super();
        modelName = "Mathias-Copeman-PR-EOS";
        attractiveTermNumber = 13;
    }
    
    public SystemPrMathiasCopeman(double T, double P) {
        super(T,P);
        modelName = "Mathias-Copeman-PR-EOS";
        attractiveTermNumber = 13;
    }
    
    public SystemPrMathiasCopeman(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        attractiveTermNumber = 13;
        modelName = "Mathias-Copeman-PR-EOS";
    }
    
    public Object clone(){
        SystemPrMathiasCopeman clonedSystem = null;
        try{
            clonedSystem = (SystemPrMathiasCopeman) super.clone();
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
        
//        
//        for(int i = 0; i < numberOfPhases; i++) {
//            clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
//        }
        
        return clonedSystem;
    }
    
    
}