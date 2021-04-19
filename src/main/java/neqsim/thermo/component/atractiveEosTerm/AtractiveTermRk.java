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
public class AtractiveTermRk extends AtractiveTermBaseClass {

    private static final long serialVersionUID = 1000;

    /** Creates new AtractiveTermSrk */
    public AtractiveTermRk(ComponentEosInterface component) {
        super(component);
    }

    @Override
	public Object clone() {
        AtractiveTermRk atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermRk) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    @Override
	public double alpha(double temperature) {
        return Math.sqrt(component.getTC() / temperature);
    }

    @Override
	public double aT(double temperature) {
        return component.geta() * alpha(temperature);
    }

    @Override
	public double diffalphaT(double temperature) {
        return -0.5 * component.getTC() / (Math.sqrt(component.getTC() / temperature) * Math.pow(temperature, 2.0));
    }

    @Override
	public double diffdiffalphaT(double temperature) {
        return -0.25 * component.getTC() * component.getTC()
                / (Math.pow(component.getTC() / temperature, 3.0 / 2.0) * Math.pow(temperature, 4.0))
                + component.getTC() / (Math.sqrt(component.getTC() / temperature) * Math.pow(temperature, 3.0));

    }

    @Override
	public double diffaT(double temperature) {
        return component.geta() * diffalphaT(temperature);
    }

    @Override
	public double diffdiffaT(double temperature) {
        return component.geta() * diffdiffalphaT(temperature);
    }

}
