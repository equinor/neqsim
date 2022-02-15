package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePrCPA;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the PR-CPA equation of state
 * 
 * @author Even Solbraa
 * @version
 */
public class SystemPrCPA extends SystemPrEos {
    private static final long serialVersionUID = 1000;

public SystemPrCPA() {
        super();
        modelName = "CPA-PR-EOS";
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePrCPA();
        }
        this.useVolumeCorrection(true);
    }

    /**
     * <p>
     * Constructor for SystemPrCPA.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemPrCPA(double T, double P) {
        super(T, P);
        modelName = "CPA-PR-EOS";
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePrCPA();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        this.useVolumeCorrection(true);
    }

    /**
     * <p>
     * Constructor for SystemPrCPA.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemPrCPA(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        modelName = "CPA-PR-EOS";
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePrCPA();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        this.useVolumeCorrection(true);

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
    public SystemPrCPA clone() {
        SystemPrCPA clonedSystem = null;
        try {
            clonedSystem = (SystemPrCPA) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
        // }

        return clonedSystem;
    }
}
