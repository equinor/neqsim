/*
 * SystemModifiedFurstElectrolyteEos.java
 *
 * Created on 26. februar 2001, 17:38
 */

package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;

/**
 * <p>SystemFurstElectrolyteEos class.</p>
 *
 * @author Even Solbraa
 */
public class SystemFurstElectrolyteEos extends SystemSrkEos {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new SystemModifiedFurstElectrolyteEos
     */
    public SystemFurstElectrolyteEos() {
        super();
        modelName = "Electrolyte-ScRK-EOS";
        attractiveTermNumber = 2;
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseModifiedFurstElectrolyteEos();
        }
    }

    /**
     * <p>Constructor for SystemFurstElectrolyteEos.</p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemFurstElectrolyteEos(double T, double P) {
        super(T, P);
        attractiveTermNumber = 2;
        modelName = "Electrolyte-ScRK-EOS";
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseModifiedFurstElectrolyteEos();
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }

    /** {@inheritDoc} */
    @Override
    public SystemFurstElectrolyteEos clone() {
        SystemFurstElectrolyteEos clonedSystem = null;
        try {
            clonedSystem = (SystemFurstElectrolyteEos) super.clone();
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
