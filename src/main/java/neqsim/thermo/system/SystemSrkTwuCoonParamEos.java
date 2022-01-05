package neqsim.thermo.system;
/**
 *
 * @author Even Solbraa
 * @version
 */

/**
 * This class defines a thermodynamic system using the SRK equation of state
 */
public class SystemSrkTwuCoonParamEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000;

    /**
     * Creates a thermodynamic system using the SRK equation of state.
     */
    // SystemSrkEos clonedSystem;
    public SystemSrkTwuCoonParamEos() {
        super();
        modelName = "TwuCoonRKparam-EOS";
        attractiveTermNumber = 12;
    }

    /**
     * <p>
     * Constructor for SystemSrkTwuCoonParamEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemSrkTwuCoonParamEos(double T, double P) {
        super(T, P);
        modelName = "TwuCoonRKparam-EOS";
        attractiveTermNumber = 12;
    }

    /**
     * <p>
     * Constructor for SystemSrkTwuCoonParamEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemSrkTwuCoonParamEos(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        modelName = "TwuCoonRKparam-EOS";
        attractiveTermNumber = 12;
    }

    /** {@inheritDoc} */
    @Override
    public SystemSrkTwuCoonParamEos clone() {
        SystemSrkTwuCoonParamEos clonedSystem = null;
        try {
            clonedSystem = (SystemSrkTwuCoonParamEos) super.clone();
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
