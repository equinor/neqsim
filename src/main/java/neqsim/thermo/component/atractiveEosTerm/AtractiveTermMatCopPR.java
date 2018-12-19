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
public class AtractiveTermMatCopPR extends AtractiveTermPr {

    private static final long serialVersionUID = 1000;



    double orgpar = 0.0;
    boolean useStandardAlphaForSupercritical = true;

    /**
     * Creates new AtractiveTermSrk
     */
    public AtractiveTermMatCopPR(ComponentEosInterface component) {
        super(component);
        m = (0.37464 + 1.54226 * component.getAcentricFactor() - 0.26992 * component.getAcentricFactor() * component.getAcentricFactor());
 
        if (component.getName().equals("water")) {
            parameters[0] =
                    0.91256735118818810000000000;
            parameters[1] =
                    -0.2872243639795234400000000;
            parameters[2] =
                    0.239526763058374250000000000;
        }
        if (component.getName().equals("TEG")) {
            parameters[0] = 1.051E0;
            parameters[1] = 2.945E0;
            parameters[2] = -5.982E0;
        }

    }

    /**
     * Creates new AtractiveTermSrk
     */
    public AtractiveTermMatCopPR(ComponentEosInterface component, double[] params) {
        this(component);
        System.arraycopy(params, 0, this.parameters, 0, params.length);
        orgpar = parameters[0];
        if (Math.abs(parameters[0]) < 1e-12) {
            parameters[0] = m;
        }

        if (component.getName().equals("water")) {
            parameters[0] =
                    0.91256735118818810000000000;
            parameters[1] =
                    -0.2872243639795234400000000;
            parameters[2] =
                    0.239526763058374250000000000;
        }
        if (component.getName().equals("TEG")) {
            parameters[0] = 1.05E+00;
            parameters[1] = 2.95E+00;
            parameters[2] = -5.98E+00;
        }
    }

    public Object clone() {
        AtractiveTermMatCopPR atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermMatCopPR) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    public double alpha(double temperature) {
        if ((useStandardAlphaForSupercritical && temperature / component.getTC() > 1.0) || parameters[0] < 1e-20) {
            return super.alpha(temperature);
        } else {
            double Tr = temperature / component.getTC();
            double trRoot = 1.0 - Math.sqrt(Tr);
            double temp = 1.0 + parameters[0] * trRoot + parameters[1] * trRoot*trRoot + parameters[2] * trRoot*trRoot*trRoot;
            return temp*temp;
        }
    }

    public double aT(double temperature) {
        if ((useStandardAlphaForSupercritical && temperature / component.getTC() > 1.0) || parameters[0] < 1e-20) {
            return super.aT(temperature);
        } else {
            return component.geta() * alpha(temperature);
        }
    }

    public double diffalphaT(double temperature) {
        if ((useStandardAlphaForSupercritical && temperature / component.getTC() > 1.0) || parameters[0] < 1e-20) {
            return super.diffalphaT(temperature);
        }

        double Tr = temperature / component.getTC();
        double TC = component.getTC();
        double rootTR = 1.0 - Math.sqrt(Tr);
        return 2.0 * (1.0 + parameters[0] * rootTR+ parameters[1] * rootTR*rootTR + parameters[2] * rootTR*rootTR*rootTR) * (-parameters[0] / Math.sqrt(Tr) / TC / 2.0
                - parameters[1] * rootTR / Math.sqrt(Tr) / TC - 3.0 / 2.0 * parameters[2] * rootTR*rootTR / Math.sqrt(Tr) / TC);



    }

    public double diffdiffalphaT(double temperature) {
        if ((useStandardAlphaForSupercritical && temperature / component.getTC() > 1.0) || parameters[0] < 1e-20) {
            return super.diffdiffalphaT(temperature);
        }

        double Tr = temperature / component.getTC();
        double TC = component.getTC();
        double rootTR = 1.0 - Math.sqrt(Tr);
        return 2.0 * Math.pow(-parameters[0] / Math.sqrt(Tr) / TC / 2.0 - parameters[1] * rootTR / Math.sqrt(Tr) / TC - 3.0 / 2.0 * parameters[2] * rootTR*rootTR / Math.sqrt(Tr) / TC, 2.0) + 2.0 * (1.0 + parameters[0] * rootTR + parameters[1] * Math.pow(1.0 - Math.sqrt(Tr), 2.0) + parameters[2] * rootTR*rootTR*rootTR) * (parameters[0] / Math.sqrt(Tr * Tr * Tr) / (TC * TC) / 4.0 + parameters[1] / temperature / TC / 2.0 + parameters[1] * rootTR / Math.sqrt(Tr * Tr * Tr) / (TC * TC) / 2.0
                + 3.0 / 2.0 * parameters[2] * rootTR / temperature / TC + 3.0 / 4.0 * parameters[2] * rootTR*rootTR / Math.sqrt(Tr * Tr * Tr) / (TC * TC));
    }

    public double diffaT(double temperature) {
        if ((useStandardAlphaForSupercritical && temperature / component.getTC() > 1.0) || parameters[0] < 1e-20) {
            return super.diffaT(temperature);
        } else {
            return component.geta() * diffalphaT(temperature);
        }
    }

    public double diffdiffaT(double temperature) {
        if ((useStandardAlphaForSupercritical && temperature / component.getTC() > 1.0) || parameters[0] < 1e-20) {
            return super.diffdiffaT(temperature);
        } else {
            return component.geta() * diffdiffalphaT(temperature);
        }
    }
}
