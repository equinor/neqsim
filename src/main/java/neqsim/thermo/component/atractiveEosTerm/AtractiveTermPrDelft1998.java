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

    public Object clone() {
        AtractiveTermPr atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermPr) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    public double alpha(double temperature) {
        if (isMethane) {
            return 0.969617 + 0.20089 * temperature / component.getTC()
                    - 0.3256987 * Math.pow(temperature / component.getTC(), 2.0)
                    + 0.06653 * Math.pow(temperature / component.getTC(), 3.0);
        } else {
            return Math.pow(1.0 + m * (1.0 - Math.sqrt(temperature / component.getTC())), 2.0);
        }
    }

    public double aT(double temperature) {
        return component.geta() * alpha(temperature);
    }

    public double diffalphaT(double temperature) {
        if (isMethane) {
            return 0.20089 / component.getTC() - 2.0 * 0.3256987 * temperature / Math.pow(component.getTC(), 2.0)
                    + 3.0 * 0.06653 * Math.pow(temperature, 2.0) / Math.pow(component.getTC(), 3.0);
        } else {
            return -(1.0 + m * (1.0 - Math.sqrt(temperature / component.getTC()))) * m
                    / Math.sqrt(temperature / component.getTC()) / component.getTC();
        }
    }

    public double diffdiffalphaT(double temperature) {
        if (isMethane) {
            return -2.0 * 0.3256987 / Math.pow(component.getTC(), 2.0)
                    + 6.0 * 0.06653 * temperature / Math.pow(component.getTC(), 3.0);
        } else {
            return m * m / temperature / component.getTC() / 2.0
                    + (1.0 + m * (1.0 - Math.sqrt(temperature / component.getTC()))) * m
                            / Math.sqrt(temperature * temperature * temperature / (Math.pow(component.getTC(), 3.0)))
                            / (component.getTC() * component.getTC()) / 2.0;
        }

    }

    public double diffaT(double temperature) {
        return component.geta() * diffalphaT(temperature);
    }

    public double diffdiffaT(double temperature) {
        return component.geta() * diffdiffalphaT(temperature);
    }

}
