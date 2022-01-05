package neqsim.thermo.system;
/**
 *
 * @author Even Solbraa
 * @version
 */

/**
 * This class defines a thermodynamic system using the UMR-PRU with MC paramters equation of state
 */
public class SystemUMRPRUMCEosNew extends SystemUMRPRUMCEos {
    /**
     * <p>
     * Constructor for SystemUMRPRUMCEosNew.
     * </p>
     */
    public SystemUMRPRUMCEosNew() {
        super();
        modelName = "UMR-PRU-MC-EoS-New";
        attractiveTermNumber = 19;
    }

    /**
     * <p>
     * Constructor for SystemUMRPRUMCEosNew.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemUMRPRUMCEosNew(double T, double P) {
        super(T, P);
        modelName = "UMR-PRU-MC-EoS_new";
        attractiveTermNumber = 19;
    }

    /**
     * <p>
     * Constructor for SystemUMRPRUMCEosNew.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemUMRPRUMCEosNew(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
    }

    /** {@inheritDoc} */
    @Override
    public SystemUMRPRUMCEos clone() {
        SystemUMRPRUMCEos clonedSystem = null;
        try {
            clonedSystem = (SystemUMRPRUMCEosNew) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedSystem;
    }
}
