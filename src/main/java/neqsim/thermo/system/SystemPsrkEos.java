package neqsim.thermo.system;
/**
 *
 * @author Even Solbraa
 * @version
 */

/**
 * This class defines a thermodynamic system using the Predictive SRK-EoS equation of state
 */
public class SystemPsrkEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000;

    /**
     * Creates a thermodynamic system using the SRK equation of state.
     */
    // SystemSrkEos clonedSystem;
    public SystemPsrkEos() {
        super();
        modelName = "Predictive-SRK-EOS";
        attractiveTermNumber = 4;
    }

    /**
     * <p>
     * Constructor for SystemPsrkEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemPsrkEos(double T, double P) {
        super(T, P);
        modelName = "Predictive-SRK-EOS";
        attractiveTermNumber = 4;
    }

    /**
     * <p>
     * Constructor for SystemPsrkEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemPsrkEos(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        attractiveTermNumber = 4;
        modelName = "Predictive-SRK-EOS";
    }

    /** {@inheritDoc} */
    @Override
    public SystemPsrkEos clone() {
        SystemPsrkEos clonedSystem = null;
        try {
            clonedSystem = (SystemPsrkEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
        // }

        return clonedSystem;
    }
}
