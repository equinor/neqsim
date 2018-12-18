/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:05
 */
package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGERG2004Eos;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 *
 * @author  Even Solbraa
 * @version
 */
/** This class defines a thermodynamic system using the SRK equation of state
 */
public class SystemGERG2004Eos extends SystemEos {

    private static final long serialVersionUID = 1000;

    //  SystemSrkEos clonedSystem;
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
            //System.out.println("here first");
            phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
            phaseArray[numberOfPhases - 1].setTemperature(T);
            phaseArray[numberOfPhases - 1].setPressure(P);
            phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
        }

        if (hydrateCheck) {
            //System.out.println("here first");
            phaseArray[numberOfPhases - 1] = new PhaseHydrate();
            phaseArray[numberOfPhases - 1].setTemperature(T);
            phaseArray[numberOfPhases - 1].setPressure(P);
            phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
        }
        this.useVolumeCorrection(false);
        commonInitialization();
    }

    public Object clone() {
        SystemGERG2004Eos clonedSystem = null;
        try {
            clonedSystem = (SystemGERG2004Eos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }


        return clonedSystem;
    }

    public void commonInitialization() {
        setImplementedCompositionDeriativesofFugacity(false);
        setImplementedPressureDeriativesofFugacity(false);
        setImplementedTemperatureDeriativesofFugacity(false);
    }
}
