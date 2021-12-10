/*
 * AtractiveTermSrk.java
 *
 * Created on 13. mai 2001, 21:59
 */

package neqsim.thermo.component.atractiveEosTerm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 *
 * @author esol
 * @version
 */
public class AtractiveTermMollerup extends AtractiveTermBaseClass {
    private static final long serialVersionUID = 1000;

    /** Creates new AtractiveTermSrk */
    public AtractiveTermMollerup(ComponentEosInterface component) {
        super(component);
    }

    /** Creates new AtractiveTermSrk */
    public AtractiveTermMollerup(ComponentEosInterface component, double[] params) {
        this(component);
        System.arraycopy(params, 0, this.parameters, 0, params.length);
    }

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

    @Override
    public double alpha(double temperature) {
        return 1.0 + parameters[0] * (1 / temperature * getComponent().getTC() - 1.0)
                + parameters[1] * temperature / getComponent().getTC()
                        * Math.log(temperature / getComponent().getTC())
                + parameters[2] * (temperature / getComponent().getTC() - 1.0);
    }

    @Override
    public double aT(double temperature) {
        return getComponent().geta() * alpha(temperature);
    }

    @Override
    public double diffalphaT(double temperature) {
        return -parameters[0] / (temperature * temperature) * getComponent().getTC()
                + parameters[1] / getComponent().getTC()
                        * Math.log(temperature / getComponent().getTC())
                + parameters[1] / getComponent().getTC() + parameters[2] / getComponent().getTC();
    }

    @Override
    public double diffdiffalphaT(double temperature) {
        return 2.0 * parameters[0] / (temperature * temperature * temperature)
                * getComponent().getTC() + parameters[1] / getComponent().getTC() / temperature;
    }

    @Override
    public double diffaT(double temperature) {
        return getComponent().geta() * diffalphaT(temperature);
    }

    @Override
    public double diffdiffaT(double temperature) {
        return getComponent().geta() * diffdiffalphaT(temperature);
    }
}
