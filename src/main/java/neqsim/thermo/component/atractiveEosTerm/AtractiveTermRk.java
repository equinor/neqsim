/*
 * AtractiveTermSrk.java
 *
 * Created on 13. mai 2001, 21:59
 */

package neqsim.thermo.component.atractiveEosTerm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AtractiveTermRk class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AtractiveTermRk extends AtractiveTermBaseClass {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for AtractiveTermRk.</p>
     *
     * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
     */
    public AtractiveTermRk(ComponentEosInterface component) {
        super(component);
    }

    /** {@inheritDoc} */
    @Override
    public AtractiveTermRk clone() {
        AtractiveTermRk atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermRk) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    /** {@inheritDoc} */
    @Override
    public double alpha(double temperature) {
        return Math.sqrt(getComponent().getTC() / temperature);
    }

    /** {@inheritDoc} */
    @Override
    public double aT(double temperature) {
        return getComponent().geta() * alpha(temperature);
    }

    /** {@inheritDoc} */
    @Override
    public double diffalphaT(double temperature) {
        return -0.5 * getComponent().getTC()
                / (Math.sqrt(getComponent().getTC() / temperature) * Math.pow(temperature, 2.0));
    }

    /** {@inheritDoc} */
    @Override
    public double diffdiffalphaT(double temperature) {
        return -0.25 * getComponent().getTC() * getComponent().getTC()
                / (Math.pow(getComponent().getTC() / temperature, 3.0 / 2.0)
                        * Math.pow(temperature, 4.0))
                + getComponent().getTC() / (Math.sqrt(getComponent().getTC() / temperature)
                        * Math.pow(temperature, 3.0));

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
