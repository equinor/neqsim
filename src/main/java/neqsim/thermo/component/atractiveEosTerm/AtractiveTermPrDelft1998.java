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
public class AtractiveTermPrDelft1998 extends AtractiveTermPr1978 {

    private static final long serialVersionUID = 1000;

    boolean isMethane = false;

    /** Creates new AtractiveTermSrk */
    public AtractiveTermPrDelft1998(ComponentEosInterface component) {
        super(component);
        if (component.getName().equals("methane")) {
            isMethane = true;
        }
    }

    @Override
    public AtractiveTermPrDelft1998 clone() {
        AtractiveTermPrDelft1998 atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermPrDelft1998) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    @Override
	public double alpha(double temperature) {
        if (isMethane) {
            return 0.969617 + 0.20089 * temperature / getComponent().getTC()
                    - 0.3256987 * Math.pow(temperature / getComponent().getTC(), 2.0)
                    + 0.06653 * Math.pow(temperature / getComponent().getTC(), 3.0);
        } else {
            return Math.pow(1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC())), 2.0);
        }
    }

    @Override
	public double aT(double temperature) {
        return getComponent().geta() * alpha(temperature);
    }

    @Override
	public double diffalphaT(double temperature) {
        if (isMethane) {
            return 0.20089 / getComponent().getTC() - 2.0 * 0.3256987 * temperature / Math.pow(getComponent().getTC(), 2.0)
                    + 3.0 * 0.06653 * Math.pow(temperature, 2.0) / Math.pow(getComponent().getTC(), 3.0);
        } else {
            return -(1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * m
                    / Math.sqrt(temperature / getComponent().getTC()) / getComponent().getTC();
        }
    }

    @Override
	public double diffdiffalphaT(double temperature) {
        if (isMethane) {
            return -2.0 * 0.3256987 / Math.pow(getComponent().getTC(), 2.0)
                    + 6.0 * 0.06653 * temperature / Math.pow(getComponent().getTC(), 3.0);
        } else {
            return m * m / temperature / getComponent().getTC() / 2.0
                    + (1.0 + m * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * m
                            / Math.sqrt(temperature * temperature * temperature / (Math.pow(getComponent().getTC(), 3.0)))
                            / (getComponent().getTC() * getComponent().getTC()) / 2.0;
        }

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
