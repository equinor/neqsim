package neqsim.thermo.phase;

/**
 * <p>
 * PhaseSolidComplex class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PhaseSolidComplex extends PhaseSolid {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for PhaseSolidComplex.
     * </p>
     */
    public PhaseSolidComplex() {
        super();
    }

    /** {@inheritDoc} */
    @Override
    public PhaseSolidComplex clone() {
        PhaseSolidComplex clonedPhase = null;
        try {
            clonedPhase = (PhaseSolidComplex) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedPhase;
    }

    /** {@inheritDoc} */
    @Override
    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
            double beta) { // type = 0
                           // start
                           // init type
                           // =1 gi nye
                           // betingelser
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
        phaseTypeName = "solidComplex";
    }
}
