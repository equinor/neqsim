package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePCSAFTa;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 *
 * @author Even Solbraa
 * @version
 */

/**
 * This class defines a thermodynamic system using the PC-SAFT with association equation of state
 */
public class SystemPCSAFTa extends SystemSrkEos {
    private static final long serialVersionUID = 1000;

    // SystemSrkEos clonedSystem;
    /**
     * <p>
     * Constructor for SystemPCSAFTa.
     * </p>
     */
    public SystemPCSAFTa() {
        super();
        modelName = "PCSAFTa-EOS";
        attractiveTermNumber = 0;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePCSAFTa();
            phaseArray[i].setTemperature(298.15);
            phaseArray[i].setPressure(1.0);
        }
        this.useVolumeCorrection(false);
    }

    /**
     * <p>
     * Constructor for SystemPCSAFTa.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemPCSAFTa(double T, double P) {
        super(T, P);
        modelName = "PCSAFTa-EOS";
        attractiveTermNumber = 0;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePCSAFTa();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        this.useVolumeCorrection(false);
    }

    /**
     * <p>
     * Constructor for SystemPCSAFTa.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemPCSAFTa(double T, double P, boolean solidCheck) {
        this(T, P);
        modelName = "PCSAFTa-EOS";
        attractiveTermNumber = 0;
        numberOfPhases = 5;
        maxNumberOfPhases = 5;
        solidPhaseCheck = solidCheck;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePCSAFTa();
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
        this.useVolumeCorrection(false);
    }

    /** {@inheritDoc} */
    @Override
    public SystemPCSAFTa clone() {
        SystemPCSAFTa clonedSystem = null;
        try {
            clonedSystem = (SystemPCSAFTa) super.clone();
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
