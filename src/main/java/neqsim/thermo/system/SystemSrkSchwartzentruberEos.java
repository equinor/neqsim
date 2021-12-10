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
public class SystemSrkSchwartzentruberEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000;

    /** Creates a thermodynamic system using the SRK equation of state. */
    // SystemSrkEos clonedSystem;
    public SystemSrkSchwartzentruberEos() {
        super();
        modelName = "ScRK-EOS";
        attractiveTermNumber = 2;
    }

    public SystemSrkSchwartzentruberEos(double T, double P) {
        super(T, P);
        modelName = "ScRK-EOS";
        attractiveTermNumber = 2;
    }

    public SystemSrkSchwartzentruberEos(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        modelName = "ScRK-EOS";
        attractiveTermNumber = 2;
    }

    @Override
    public SystemSrkSchwartzentruberEos clone() {
        SystemSrkSchwartzentruberEos clonedSystem = null;
        try {
            clonedSystem = (SystemSrkSchwartzentruberEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        //
        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
        // }

        return clonedSystem;
    }
}