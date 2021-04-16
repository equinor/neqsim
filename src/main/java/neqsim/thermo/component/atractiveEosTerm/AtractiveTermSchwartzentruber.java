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

    public Object clone() {
        AtractiveTermSchwartzentruber atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermSchwartzentruber) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    public void init() {
        m = (0.48508 + 1.55191 * component.getAcentricFactor()
                - 0.15613 * component.getAcentricFactor() * component.getAcentricFactor());
    }

    public double alpha(double temperature) {
        // System.out.println("alpha here " + Math.pow( 1.0 +
        // m*(1.0-Math.sqrt(temperature/component.getTC()))-parameters[0]*(1.0-temperature/component.getTC())*(1.0+parameters[1]*temperature/component.getTC()+parameters[2]*Math.pow(temperature/component.getTC(),2.0)),2.0));
        return Math.pow(1.0 + m * (1.0 - Math.sqrt(temperature / component.getTC()))
                - parameters[0] * (1.0 - temperature / component.getTC())
                        * (1.0 + parameters[1] * temperature / component.getTC()
                                + parameters[2] * Math.pow(temperature / component.getTC(), 2.0)),
                2.0);
    }

    private double alphaCrit(double temperature) {
        d = 1.0 + m / 2.0 - parameters[0] * (1.0 + parameters[1] + parameters[2]);
        c = 1.0 - 1.0 / d;
        return Math.pow(Math.exp(c * (1.0 - Math.pow(temperature / component.getTC(), 1.0 * d))), 2.0);
    }

    private double diffalphaCritT(double temperature) {
        d = 1.0 + m / 2.0 - parameters[0] * (1.0 + parameters[1] + parameters[2]);
        c = 1.0 - 1.0 / d;
        return -2.0 * Math.pow(Math.exp(c * (1.0 - Math.pow(temperature / component.getTC(), 1.0 * d))), 2.0) * c
                * Math.pow(temperature / component.getTC(), 1.0 * d) * d / temperature;
    }

    private double diffdiffalphaCritT(double temperature) {
        d = 1.0 + m / 2.0 - parameters[0] * (1.0 + parameters[1] + parameters[2]);
        c = 1.0 - 1.0 / d;
        double TC = component.getTC();
        return 4.0 * Math.pow(Math.exp(c * (1.0 - Math.pow(temperature / TC, 1.0 * d))), 2.0) * c * c
                * Math.pow(Math.pow(temperature / TC, 1.0 * d), 2.0) * d * d / (temperature * temperature)
                - 2.0 * Math.pow(Math.exp(c * (1.0 - Math.pow(temperature / TC, 1.0 * d))), 2.0) * c
                        * Math.pow(temperature / TC, 1.0 * d) * d * d / (temperature * temperature)
                + 2.0 * Math.pow(Math.exp(c * (1.0 - Math.pow(temperature / TC, 1.0 * d))), 2.0) * c
                        * Math.pow(temperature / TC, 1.0 * d) * d / (temperature * temperature);
    }

    public double aT(double temperature) {
        if (temperature / component.getTC() > 100.0) {
            return component.geta() * alphaCrit(temperature);
        } else {
            return component.geta() * alpha(temperature);
        }
    }

    public double diffalphaT(double temperature) {
        return 2.0
                * (1.0 + m * (1.0 - Math.sqrt(temperature / component.getTC())) - parameters[0]
                        * (1.0 - temperature / component.getTC())
                        * (1.0 + parameters[1] * temperature / component.getTC()
                                + parameters[2] * temperature * temperature / (component.getTC() * component.getTC())))
                * (-m / Math.sqrt(temperature / component.getTC()) / component.getTC() / 2.0
                        + parameters[0] / component.getTC() * (1.0 + parameters[1] * temperature / component.getTC()
                                + parameters[2] * temperature * temperature / (component.getTC() * component.getTC()))
                        - parameters[0] * (1.0 - temperature / component.getTC()) * (parameters[1] / component.getTC()
                                + 2.0 * parameters[2] * temperature / (component.getTC() * component.getTC())));
    }

    public double diffdiffalphaT(double temperature) {
        return 2.0
                * Math.pow(-m / Math.sqrt(temperature / component.getTC()) / component.getTC() / 2.0
                        + parameters[0] / component.getTC()
                                * (1.0 + parameters[1] * temperature / component.getTC()
                                        + parameters[2] * temperature * temperature
                                                / (component.getTC() * component.getTC()))
                        - parameters[0] * (1.0 - temperature / component.getTC())
                                * (parameters[1] / component.getTC()
                                        + 2.0 * parameters[2] * temperature / (component.getTC() * component.getTC())),
                        2.0)
                + 2.0 * (1.0 + m * (1.0 - Math.sqrt(temperature / component.getTC())) - parameters[0]
                        * (1.0 - temperature / component.getTC())
                        * (1.0 + parameters[1] * temperature / component.getTC()
                                + parameters[2] * temperature * temperature / (component.getTC() * component.getTC())))
                        * (m / Math.sqrt(temperature * temperature * temperature
                                / (component.getTC() * component.getTC() * component.getTC()))
                                / (component.getTC() * component.getTC()) / 4.0
                                + 2.0 * parameters[0] / component.getTC()
                                        * (parameters[1] / component.getTC() + 2.0 * parameters[2] * temperature
                                                / (component.getTC() * component.getTC()))
                                - 2.0 * parameters[0] * (1.0 - temperature / component.getTC()) * parameters[2]
                                        / (component.getTC() * component.getTC()));
    }

    public double diffaT(double temperature) {
        if (temperature / component.getTC() > 100.0) {
            return component.geta() * diffalphaCritT(temperature);
        } else {
            return component.geta() * diffalphaT(temperature);
        }
    }

    public double diffdiffaT(double temperature) {
        if (temperature / component.getTC() > 100.0) {
            return component.geta() * diffdiffalphaCritT(temperature);
        } else {
            return component.geta() * diffdiffalphaT(temperature);
        }
    }

}
