/*
 * PhasePureComponentSolid.java
 *
 * Created on 18. august 2001, 12:39
 */
package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentWax;

/**
 * <p>
 * PhaseWax class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PhaseWax extends PhaseSolid {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for PhaseWax.
     * </p>
     */
    public PhaseWax() {
        super();
        phaseTypeName = "wax";
    }

    /** {@inheritDoc} */
    @Override
    public PhaseWax clone() {
        PhaseWax clonedPhase = null;
        try {
            clonedPhase = (PhaseWax) super.clone();
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
        phaseTypeName = "wax";

    }

    /** {@inheritDoc} */
    @Override
    public void addcomponent(String componentName, double molesInPhase, double moles,
            int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] =
                new ComponentWax(componentName, moles, molesInPhase, compNumber);
        // componentArray[compNumber] = new ComponentWaxWilson(componentName, moles,
        // molesInPhase, compNumber);
        //// componentArray[compNumber] = new ComponentWonWax(componentName, moles,
        // molesInPhase, compNumber);
    }
}
