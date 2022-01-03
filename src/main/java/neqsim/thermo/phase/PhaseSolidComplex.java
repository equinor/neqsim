package neqsim.thermo.phase;

/**
 *
 * @author esol
 * @version
 */
public class PhaseSolidComplex extends PhaseSolid {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new PhaseSolidComplex
     */
    public PhaseSolidComplex() {
        super();
    }

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

    @Override
    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
            double beta) {
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
        phaseTypeName = "solidComplex";
    }
}
