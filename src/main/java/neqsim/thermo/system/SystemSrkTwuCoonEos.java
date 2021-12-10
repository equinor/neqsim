/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:05
 */

package neqsim.thermo.system;
/**
 *
 * @author Even Solbraa
 * @version
 */

/**
 * This class defines a thermodynamic system using the SRK equation of state
 */
public class SystemSrkTwuCoonEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000;

    /** Creates a thermodynamic system using the SRK equation of state. */
    // SystemSrkEos clonedSystem;
    public SystemSrkTwuCoonEos() {
        super();
        modelName = "TwuCoonRK-EOS";
        attractiveTermNumber = 11;
    }

    public SystemSrkTwuCoonEos(double T, double P) {
        super(T, P);
        modelName = "TwuCoonRK-EOS";
        attractiveTermNumber = 11;
    }

    public SystemSrkTwuCoonEos(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        modelName = "TwuCoonRK-EOS";
        attractiveTermNumber = 11;
    }

    @Override
    public SystemSrkTwuCoonEos clone() {
        SystemSrkTwuCoonEos clonedSystem = null;
        try {
            clonedSystem = (SystemSrkTwuCoonEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
        // }

        return clonedSystem;
    }
}