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
public class AtractiveTermTwu extends AtractiveTermSrk {

    private static final long serialVersionUID = 1000;

    /** Creates new AtractiveTermSrk */
    public AtractiveTermTwu(ComponentEosInterface component) {
        super(component);
    }

    @Override
	public Object clone() {
        AtractiveTermTwu atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermTwu) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    @Override
	public void init() {
        m = (0.48 + 1.574 * getComponent().getAcentricFactor()
                - 0.175 * getComponent().getAcentricFactor() * getComponent().getAcentricFactor());
    }

    @Override
	public double alpha(double temperature) {
        return Math.pow(1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC())), 2.0);
    }

    @Override
	public double aT(double temperature) {
        return getComponent().geta() * alpha(temperature);
    }

    @Override
	public double diffalphaT(double temperature) {
        return -(1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * m
                / Math.sqrt(temperature / getComponent().getTC()) / getComponent().getTC();
    }

    @Override
	public double diffdiffalphaT(double temperature) {

        return m * m / temperature / getComponent().getTC() / 2.0
                + (1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * m
                        / Math.sqrt(temperature * temperature * temperature / (Math.pow(getComponent().getTC(), 3.0)))
                        / (getComponent().getTC() * getComponent().getTC()) / 2.0;

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
