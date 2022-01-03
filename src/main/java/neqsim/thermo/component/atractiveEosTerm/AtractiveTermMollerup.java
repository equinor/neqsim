/*
 * AtractiveTermSrk.java
 *
 * Created on 13. mai 2001, 21:59
 */

package neqsim.thermo.component.atractiveEosTerm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AtractiveTermMollerup class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AtractiveTermMollerup extends AtractiveTermBaseClass {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for AtractiveTermMollerup.
     * </p>
     *
     * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
     */
    public AtractiveTermMollerup(ComponentEosInterface component) {
        super(component);
    }

    /**
     * <p>
     * Constructor for AtractiveTermMollerup.
     * </p>
     *
     * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
     * @param params an array of {@link double} objects
     */
    public AtractiveTermMollerup(ComponentEosInterface component, double[] params) {
        this(component);
        System.arraycopy(params, 0, this.parameters, 0, params.length);
    }

    /** {@inheritDoc} */
    @Override
    public AtractiveTermMollerup clone() {
        AtractiveTermMollerup atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermMollerup) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    /** {@inheritDoc} */
    @Override
    public double alpha(double temperature) {
        return 1.0 + parameters[0] * (1 / temperature * getComponent().getTC() - 1.0)
                + parameters[1] * temperature / getComponent().getTC()
                        * Math.log(temperature / getComponent().getTC())
                + parameters[2] * (temperature / getComponent().getTC() - 1.0);
    }

    /** {@inheritDoc} */
    @Override
    public double aT(double temperature) {
        return getComponent().geta() * alpha(temperature);
    }

    /** {@inheritDoc} */
    @Override
    public double diffalphaT(double temperature) {
        return -parameters[0] / (temperature * temperature) * getComponent().getTC()
                + parameters[1] / getComponent().getTC()
                        * Math.log(temperature / getComponent().getTC())
                + parameters[1] / getComponent().getTC() + parameters[2] / getComponent().getTC();
    }

    /** {@inheritDoc} */
    @Override
    public double diffdiffalphaT(double temperature) {
        return 2.0 * parameters[0] / (temperature * temperature * temperature)
                * getComponent().getTC() + parameters[1] / getComponent().getTC() / temperature;
    }

    /** {@inheritDoc} */
    @Override
    public double diffaT(double temperature) {
        return getComponent().geta() * diffalphaT(temperature);
    }

    /** {@inheritDoc} */
    @Override
    public double diffdiffaT(double temperature) {
        return getComponent().geta() * diffdiffalphaT(temperature);
    }
}
