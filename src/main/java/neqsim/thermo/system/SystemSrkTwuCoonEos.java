package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the SRK Two Coon equation of state
 * 
 * @author Even Solbraa
 */
public class SystemSrkTwuCoonEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000;

    public SystemSrkTwuCoonEos() {
        super();
        modelName = "TwuCoonRK-EOS";
        attractiveTermNumber = 11;
    }

    /**
     * <p>
     * Constructor for SystemSrkTwuCoonEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemSrkTwuCoonEos(double T, double P) {
        super(T, P);
        modelName = "TwuCoonRK-EOS";
        attractiveTermNumber = 11;
    }

    /**
     * <p>
     * Constructor for SystemSrkTwuCoonEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemSrkTwuCoonEos(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        modelName = "TwuCoonRK-EOS";
        attractiveTermNumber = 11;
    }

    /** {@inheritDoc} */
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
