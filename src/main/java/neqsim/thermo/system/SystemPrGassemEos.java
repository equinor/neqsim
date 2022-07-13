package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the PR Gassem equation of state
 * 
 * @author Even Solbraa
 */
public class SystemPrGassemEos extends SystemPrEos {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for SystemPrGassemEos.
     * </p>
     */
    public SystemPrGassemEos() {
        super();
        modelName = "PR-Gassem-EOS";
        attractiveTermNumber = 8;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePrEos();
            phaseArray[i].setTemperature(298.15);
            phaseArray[i].setPressure(1.0);
        }
    }

    /**
     * <p>
     * Constructor for SystemPrGassemEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemPrGassemEos(double T, double P) {
        super(T, P);
        modelName = "PR-Gassem-EOS";
        attractiveTermNumber = 8;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePrEos();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }

    /**
     * <p>
     * Constructor for SystemPrGassemEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemPrGassemEos(double T, double P, boolean solidCheck) {
        this(T, P);
        modelName = "PR-Gassem-EOS";
        attractiveTermNumber = 8;
        setNumberOfPhases(5);
        solidPhaseCheck = solidCheck;

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
    public SystemPrGassemEos clone() {
        SystemPrGassemEos clonedSystem = null;
        try {
            clonedSystem = (SystemPrGassemEos) super.clone();
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
