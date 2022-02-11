/*
 * ComponentGEUniquacmodifiedHV.java
 *
 * Created on 18. juli 2000, 20:24
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 */
abstract class ComponentGEUniquacmodifiedHV extends ComponentGEUniquac {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for ComponentGEUniquacmodifiedHV.
     * </p>
     */
    public ComponentGEUniquacmodifiedHV() {}

    /**
     * <p>
     * Constructor for ComponentGEUniquacmodifiedHV.
     * </p>
     *
     * @param component_name a {@link java.lang.String} object
     * @param moles a double
     * @param molesInPhase a double
     * @param compnumber a int
     */
    public ComponentGEUniquacmodifiedHV(String component_name, double moles, double molesInPhase,
            int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    /** {@inheritDoc} */
    @Override
    public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure, int phasetype) {

        // PhaseGEInterface phaseny = (PhaseGEInterface) phase.getPhase();
        // PhaseGEInterface GEPhase = phaseny.getGEphase();

        return 1;// super.getGamma(GEPhase, numberOfComponents, temperature, pressure,
                 // phasetype);
    }
}
