package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEosMod2004;

/**
 * This class defines a thermodynamic system using the electrolyte the Modified Furst Electrolyte
 * Eos.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemFurstElectrolyteEosMod2004 extends SystemSrkEos {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for SystemFurstElectrolyteEosMod2004.
     * </p>
     */
    public SystemFurstElectrolyteEosMod2004() {
        super();
        modelName = "Electrolyte-ScRK-EOS";
        attractiveTermNumber = 2;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseModifiedFurstElectrolyteEosMod2004();
        }
    }

    /**
     * <p>
     * Constructor for SystemFurstElectrolyteEosMod2004.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemFurstElectrolyteEosMod2004(double T, double P) {
        super(T, P);
        attractiveTermNumber = 2;
        modelName = "Electrolyte-ScRK-EOS";
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseModifiedFurstElectrolyteEosMod2004();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }

    /** {@inheritDoc} */
    @Override
    public SystemFurstElectrolyteEosMod2004 clone() {
        SystemFurstElectrolyteEosMod2004 clonedSystem = null;
        try {
            clonedSystem = (SystemFurstElectrolyteEosMod2004) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // clonedSystem.phaseArray = (PhaseInterface[]) phaseArray.clone();
        // for(int i = 0; i < numberOfPhases; i++) {
        // clonedSystem.phaseArray[i] = (PhaseModifiedFurstElectrolyteEos)
        // phaseArray[i].clone();
        // }

        return clonedSystem;
    }
}
