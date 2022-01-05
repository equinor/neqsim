package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentSrkPeneloux;

/**
 * <p>
 * PhaseSrkPenelouxEos class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseSrkPenelouxEos extends PhaseSrkEos {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for PhaseSrkPenelouxEos.
     * </p>
     */
    public PhaseSrkPenelouxEos() {
        super();
    }

    /** {@inheritDoc} */
    @Override
    public PhaseSrkPenelouxEos clone() {
        PhaseSrkPenelouxEos clonedPhase = null;
        try {
            clonedPhase = (PhaseSrkPenelouxEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedPhase;
    }

    /** {@inheritDoc} */
    @Override
    public void addcomponent(String componentName, double moles, double molesInPhase,
            int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] =
                new ComponentSrkPeneloux(componentName, moles, molesInPhase, compNumber);
    }
}
