package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the Peng Robinson equation of state
 * 
 * @author Even Solbraa
 */
public class SystemPrEos extends SystemEos {
    private static final long serialVersionUID = 1000;

    /**
     * Constructor of a fluid object using the SRK equation of state.
     */
    public SystemPrEos() {
        super();
        modelName = "PR-EOS";
        getCharacterization().setTBPModel("PedersenPR"); // (RiaziDaubert PedersenPR PedersenSRK
        attractiveTermNumber = 1;

        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePrEos();
            phaseArray[i].setTemperature(298.15);
            phaseArray[i].setPressure(1.0);
        }
    }

    /**
     * Constructor of a fluid object using the PR-EoS (Peng Robinson)
     *
     * @param T The temperature in unit Kelvin
     * @param P The pressure in unit bara (absolute pressure)
     */
    public SystemPrEos(double T, double P) {
        super(T, P);
        modelName = "PR-EOS";
        getCharacterization().setTBPModel("PedersenPR");
        attractiveTermNumber = 1;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhasePrEos();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }

    /**
     * Constructor of a fluid object using the PR-EoS (Peng Robinson)
     *
     * @param T The temperature in unit Kelvin
     * @param P The pressure in unit bara (absolute pressure)
     * @param solidCheck a boolean variable specifying if solid phase check and calculation should
     *        be done
     */
    public SystemPrEos(double T, double P, boolean solidCheck) {
        this(T, P);
        attractiveTermNumber = 1;
        setNumberOfPhases(5);
        modelName = "PR-EOS";
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
    public SystemPrEos clone() {
        SystemPrEos clonedSystem = null;
        try {
            clonedSystem = (SystemPrEos) super.clone();
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
