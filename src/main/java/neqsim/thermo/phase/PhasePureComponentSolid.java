/*
 * PhasePureComponentSolid.java
 *
 * Created on 18. august 2001, 12:39
 */

package neqsim.thermo.phase;

/**
 * <p>PhasePureComponentSolid class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PhasePureComponentSolid extends PhaseSolid {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new PhasePureComponentSolid
     */
    public PhasePureComponentSolid() {
        super();
    }

    /** {@inheritDoc} */
    @Override
    public PhasePureComponentSolid clone() {
        PhasePureComponentSolid clonedPhase = null;
        try {
            clonedPhase = (PhasePureComponentSolid) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedPhase;
    }

	/** {@inheritDoc} */
    @Override
	public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta) { // type = 0
                                                                                                            // start
                                                                                                            // init type
                                                                                                            // =1 gi nye
                                                                                                            // betingelser
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
        phaseTypeName = "solid";
    }
}
