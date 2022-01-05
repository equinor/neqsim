package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the SRK equation of state
 * 
 * @author Even Solbraa
 * @version
 */
public class SystemSrkSchwartzentruberEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000;

    public SystemSrkSchwartzentruberEos() {
        super();
        modelName = "ScRK-EOS";
        attractiveTermNumber = 2;
    }

    /**
     * <p>
     * Constructor for SystemSrkSchwartzentruberEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemSrkSchwartzentruberEos(double T, double P) {
        super(T, P);
        modelName = "ScRK-EOS";
        attractiveTermNumber = 2;
    }

    /**
     * <p>
     * Constructor for SystemSrkSchwartzentruberEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemSrkSchwartzentruberEos(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        modelName = "ScRK-EOS";
        attractiveTermNumber = 2;
    }

    /** {@inheritDoc} */
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
