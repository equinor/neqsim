/*
 * PhasePureComponentSolid.java
 *
 * Created on 18. august 2001, 12:39
 */
package neqsim.thermo.phase;

/**
 *
 * @author esol
 * @version
 */
public class PhaseSolidComplex extends PhaseSolid {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new PhasePureComponentSolid
     */
    public PhaseSolidComplex() {
        super();
    }

    @Override
	public Object clone() {
        PhaseSolidComplex clonedPhase = null;
        try {
            clonedPhase = (PhaseSolidComplex) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedPhase;
    }

    @Override
	public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta) { // type = 0
                                                                                                            // start
                                                                                                            // init type
                                                                                                            // =1 gi nye
                                                                                                            // betingelser
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
        phaseTypeName = "solidComplex";
    }
}
