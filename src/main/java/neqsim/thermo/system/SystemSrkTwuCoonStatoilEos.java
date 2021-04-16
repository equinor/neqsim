/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermo.system;

public class SystemSrkTwuCoonStatoilEos extends SystemSrkEos {

    private static final long serialVersionUID = 1000;

    /** Creates a thermodynamic system using the SRK equation of state. */
    // SystemSrkEos clonedSystem;
    public SystemSrkTwuCoonStatoilEos() {
        super();
        modelName = "TwuCoonStatoil-EOS";
        attractiveTermNumber = 18;
    }

    public SystemSrkTwuCoonStatoilEos(double T, double P) {
        super(T, P);
        modelName = "TwuCoonStatoil-EOS";
        attractiveTermNumber = 18;
    }

    public SystemSrkTwuCoonStatoilEos(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        modelName = "TwuCoonStatoil-EOS";
        attractiveTermNumber = 18;
    }

    public Object clone() {
        SystemSrkTwuCoonStatoilEos clonedSystem = null;
        try {
            clonedSystem = (SystemSrkTwuCoonStatoilEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

//        
//        for(int i = 0; i < numberOfPhases; i++) {
//            clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
//        }

        return clonedSystem;
    }

}