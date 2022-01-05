package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 *
 * @author Even Solbraa
 * @version
 */

/**
 * This class defines a thermodynamic system using the SRK equation of state
 */
public class SystemPrEosDelft1998 extends SystemPrEos {
    private static final long serialVersionUID = 1000;

    /**
     * Creates a thermodynamic system using the SRK equation of state.
     */

    // SystemPrEos clonedSystem;
    public SystemPrEosDelft1998() {
        super();
        modelName = "PR Delft1998 EOS";
        attractiveTermNumber = 7;
    }

    /**
     * <p>
     * Constructor for SystemPrEosDelft1998.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemPrEosDelft1998(double T, double P) {
        super(T, P);
        modelName = "PR Delft1998 EOS";
        attractiveTermNumber = 7;
    }

    /**
     * <p>
     * Constructor for SystemPrEosDelft1998.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemPrEosDelft1998(double T, double P, boolean solidCheck) {
        this(T, P);
        attractiveTermNumber = 7;
        modelName = "PR Delft1998 EOS";

        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePrEos();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }

        if (solidPhaseCheck) {
            // System.out.println("here first");
            phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
            phaseArray[numberOfPhases - 1].setTemperature(T);
            phaseArray[numberOfPhases - 1].setPressure(P);
            phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
        }

        if (hydrateCheck) {
            // System.out.println("here first");
            phaseArray[numberOfPhases - 1] = new PhaseHydrate();
            phaseArray[numberOfPhases - 1].setTemperature(T);
            phaseArray[numberOfPhases - 1].setPressure(P);
            phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
        }
    }

    /** {@inheritDoc} */
    @Override
    public SystemPrEosDelft1998 clone() {
        SystemPrEosDelft1998 clonedSystem = null;
        try {
            clonedSystem = (SystemPrEosDelft1998) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // clonedSystem.phaseArray = (PhaseInterface[]) phaseArray.clone();
        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
        // }

        return clonedSystem;
    }
}
