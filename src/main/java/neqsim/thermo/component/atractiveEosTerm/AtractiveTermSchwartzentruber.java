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
public class AtractiveTermSchwartzentruber extends AtractiveTermBaseClass {

    private static final long serialVersionUID = 1000;

    private double c = 0.0, d = 0.0;

    /** Creates new AtractiveTermSrk */
    public AtractiveTermSchwartzentruber(ComponentEosInterface component) {
        super(component);
        m = (0.48508 + 1.55191 * component.getAcentricFactor()
                - 0.15613 * component.getAcentricFactor() * component.getAcentricFactor());
    }

    /** Creates new AtractiveTermSrk */
    public AtractiveTermSchwartzentruber(ComponentEosInterface component, double[] params) {
        this(component);
        System.arraycopy(params, 0, this.parameters, 0, params.length);
        d = 1.0 + m / 2.0 - parameters[0] * (1.0 + parameters[1] + parameters[2]);
        c = 1.0 - 1.0 / d;
    }

    @Override
	public Object clone() {
        AtractiveTermSchwartzentruber atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermSchwartzentruber) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    @Override
	public void init() {
        m = (0.48508 + 1.55191 * getComponent().getAcentricFactor()
                - 0.15613 * getComponent().getAcentricFactor() * getComponent().getAcentricFactor());
    }

    @Override
	public double alpha(double temperature) {
        // System.out.println("alpha here " + Math.pow( 1.0 +
        // m*(1.0-Math.sqrt(temperature/component.getTC()))-parameters[0]*(1.0-temperature/component.getTC())*(1.0+parameters[1]*temperature/component.getTC()+parameters[2]*Math.pow(temperature/component.getTC(),2.0)),2.0));
        return Math.pow(1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC()))
                - parameters[0] * (1.0 - temperature / getComponent().getTC())
                        * (1.0 + parameters[1] * temperature / getComponent().getTC()
                                + parameters[2] * Math.pow(temperature / getComponent().getTC(), 2.0)),
                2.0);
    }

    private double alphaCrit(double temperature) {
        d = 1.0 + m / 2.0 - parameters[0] * (1.0 + parameters[1] + parameters[2]);
        c = 1.0 - 1.0 / d;
        return Math.pow(Math.exp(c * (1.0 - Math.pow(temperature / getComponent().getTC(), 1.0 * d))), 2.0);
    }

    private double diffalphaCritT(double temperature) {
        d = 1.0 + m / 2.0 - parameters[0] * (1.0 + parameters[1] + parameters[2]);
        c = 1.0 - 1.0 / d;
        return -2.0 * Math.pow(Math.exp(c * (1.0 - Math.pow(temperature / getComponent().getTC(), 1.0 * d))), 2.0) * c
                * Math.pow(temperature / getComponent().getTC(), 1.0 * d) * d / temperature;
    }

    private double diffdiffalphaCritT(double temperature) {
        d = 1.0 + m / 2.0 - parameters[0] * (1.0 + parameters[1] + parameters[2]);
        c = 1.0 - 1.0 / d;
        double TC = getComponent().getTC();
        return 4.0 * Math.pow(Math.exp(c * (1.0 - Math.pow(temperature / TC, 1.0 * d))), 2.0) * c * c
                * Math.pow(Math.pow(temperature / TC, 1.0 * d), 2.0) * d * d / (temperature * temperature)
                - 2.0 * Math.pow(Math.exp(c * (1.0 - Math.pow(temperature / TC, 1.0 * d))), 2.0) * c
                        * Math.pow(temperature / TC, 1.0 * d) * d * d / (temperature * temperature)
                + 2.0 * Math.pow(Math.exp(c * (1.0 - Math.pow(temperature / TC, 1.0 * d))), 2.0) * c
                        * Math.pow(temperature / TC, 1.0 * d) * d / (temperature * temperature);
    }

    @Override
	public double aT(double temperature) {
        if (temperature / getComponent().getTC() > 100.0) {
            return getComponent().geta() * alphaCrit(temperature);
        } else {
            return getComponent().geta() * alpha(temperature);
        }
    }

    @Override
	public double diffalphaT(double temperature) {
        return 2.0
                * (1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC())) - parameters[0]
                        * (1.0 - temperature / getComponent().getTC())
                        * (1.0 + parameters[1] * temperature / getComponent().getTC()
                                + parameters[2] * temperature * temperature / (getComponent().getTC() * getComponent().getTC())))
                * (-m / Math.sqrt(temperature / getComponent().getTC()) / getComponent().getTC() / 2.0
                        + parameters[0] / getComponent().getTC() * (1.0 + parameters[1] * temperature / getComponent().getTC()
                                + parameters[2] * temperature * temperature / (getComponent().getTC() * getComponent().getTC()))
                        - parameters[0] * (1.0 - temperature / getComponent().getTC()) * (parameters[1] / getComponent().getTC()
                                + 2.0 * parameters[2] * temperature / (getComponent().getTC() * getComponent().getTC())));
    }

    @Override
	public double diffdiffalphaT(double temperature) {
        return 2.0
                * Math.pow(-m / Math.sqrt(temperature / getComponent().getTC()) / getComponent().getTC() / 2.0
                        + parameters[0] / getComponent().getTC()
                                * (1.0 + parameters[1] * temperature / getComponent().getTC()
                                        + parameters[2] * temperature * temperature
                                                / (getComponent().getTC() * getComponent().getTC()))
                        - parameters[0] * (1.0 - temperature / getComponent().getTC())
                                * (parameters[1] / getComponent().getTC()
                                        + 2.0 * parameters[2] * temperature / (getComponent().getTC() * getComponent().getTC())),
                        2.0)
                + 2.0 * (1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC())) - parameters[0]
                        * (1.0 - temperature / getComponent().getTC())
                        * (1.0 + parameters[1] * temperature / getComponent().getTC()
                                + parameters[2] * temperature * temperature / (getComponent().getTC() * getComponent().getTC())))
                        * (m / Math.sqrt(temperature * temperature * temperature
                                / (getComponent().getTC() * getComponent().getTC() * getComponent().getTC()))
                                / (getComponent().getTC() * getComponent().getTC()) / 4.0
                                + 2.0 * parameters[0] / getComponent().getTC()
                                        * (parameters[1] / getComponent().getTC() + 2.0 * parameters[2] * temperature
                                                / (getComponent().getTC() * getComponent().getTC()))
                                - 2.0 * parameters[0] * (1.0 - temperature / getComponent().getTC()) * parameters[2]
                                        / (getComponent().getTC() * getComponent().getTC()));
    }

    @Override
	public double diffaT(double temperature) {
        if (temperature / getComponent().getTC() > 100.0) {
            return getComponent().geta() * diffalphaCritT(temperature);
        } else {
            return getComponent().geta() * diffalphaT(temperature);
        }
    }

    @Override
	public double diffdiffaT(double temperature) {
        if (temperature / getComponent().getTC() > 100.0) {
            return getComponent().geta() * diffdiffalphaCritT(temperature);
        } else {
            return getComponent().geta() * diffdiffalphaT(temperature);
        }
    }

}
