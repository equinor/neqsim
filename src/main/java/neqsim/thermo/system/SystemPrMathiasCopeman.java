package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the PR Mathias Copeman equation of state
 * 
 * @author Even Solbraa
 */
public class SystemPrMathiasCopeman extends SystemPrEos {
    private static final long serialVersionUID = 1000;

    public SystemPrMathiasCopeman() {
        super();
        modelName = "Mathias-Copeman-PR-EOS";
        attractiveTermNumber = 13;
    }

    /**
     * <p>
     * Constructor for SystemPrMathiasCopeman.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemPrMathiasCopeman(double T, double P) {
        super(T, P);
        modelName = "Mathias-Copeman-PR-EOS";
        attractiveTermNumber = 13;
    }

    /**
     * <p>
     * Constructor for SystemPrMathiasCopeman.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemPrMathiasCopeman(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        attractiveTermNumber = 13;
        modelName = "Mathias-Copeman-PR-EOS";
    }

    /** {@inheritDoc} */
    @Override
    public SystemPrMathiasCopeman clone() {
        SystemPrMathiasCopeman clonedSystem = null;
        try {
            clonedSystem = (SystemPrMathiasCopeman) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
        // }

        return clonedSystem;
    }
}
