package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGERG2004Eos;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the SRK equation of state
 * 
 * @author Even Solbraa
 * @version
 */
public class SystemGERG2004Eos extends SystemEos {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for SystemGERG2004Eos.
     * </p>
     */
    public SystemGERG2004Eos() {
        super();
        modelName = "GERG2004-EOS";
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseGERG2004Eos();
            phaseArray[i].setTemperature(298.15);
            phaseArray[i].setPressure(1.0);
        }
        this.useVolumeCorrection(false);
        commonInitialization();
    }

    /**
     * <p>
     * Constructor for SystemGERG2004Eos.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemGERG2004Eos(double T, double P) {
        super(T, P);
        modelName = "GERG2004-EOS";
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseGERG2004Eos();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
        this.useVolumeCorrection(false);
        commonInitialization();
    }

    /**
     * <p>
     * Constructor for SystemGERG2004Eos.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemGERG2004Eos(double T, double P, boolean solidCheck) {
        this(T, P);
        modelName = "GERG2004-EOS";

        numberOfPhases = 5;
        maxNumberOfPhases = 5;
        solidPhaseCheck = solidCheck;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseGERG2004Eos();
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
        commonInitialization();
    }

    /** {@inheritDoc} */
    @Override
    public SystemGERG2004Eos clone() {
        SystemGERG2004Eos clonedSystem = null;
        try {
            clonedSystem = (SystemGERG2004Eos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedSystem;
    }

    /**
     * <p>
     * commonInitialization.
     * </p>
     */
    public void commonInitialization() {
        setImplementedCompositionDeriativesofFugacity(false);
        setImplementedPressureDeriativesofFugacity(false);
        setImplementedTemperatureDeriativesofFugacity(false);
    }
}
