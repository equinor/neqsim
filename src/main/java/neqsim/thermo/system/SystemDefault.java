package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseInterface;

/**
 * This class defines a thermodynamic system using the SRK equation of state
 * 
 * @author Even Solbraa
 * @version
 */
public class SystemDefault extends SystemThermo {
    private static final long serialVersionUID = 1000;

    double T = 100, P = 100;

    /**
     * <p>
     * Constructor for SystemDefault.
     * </p>
     */
    public SystemDefault() {
        super();
    }

    /**
     * <p>
     * Constructor for SystemDefault.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemDefault(double T, double P) {
        super(T, P);
        this.T = T;
        this.P = P;
    }

    /**
     * <p>
     * setPhase.
     * </p>
     *
     * @param phaseType a {@link neqsim.thermo.phase.PhaseInterface} object
     */
    public void setPhase(PhaseInterface phaseType) {
        for (int i = 0; i < getMaxNumberOfPhases(); i++) {
            try {
                phaseArray[i] = phaseType.getClass().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                logger.error("err " + e.toString());
            }
            phaseArray[i].setTemperature(T);
            phaseArray[i].setPressure(P);
        }
    }

    /** {@inheritDoc} */
    public SystemProperties getProperties() {
        throw new java.lang.UnsupportedOperationException("Not supported yet.");
    }
}
