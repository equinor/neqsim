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
public class AtractiveTermCPAstatoil extends AtractiveTermSrk {
    private static final long serialVersionUID = 1000;

    double orgpar = 0.0;

    /**
     * Creates new AtractiveTermSrk
     */
    public AtractiveTermCPAstatoil(ComponentEosInterface component) {
        super(component);
        m = (0.48 + 1.574 * component.getAcentricFactor()
                - 0.176 * component.getAcentricFactor() * component.getAcentricFactor());
        if (component.getName().equals("waterG")) {
            parameters[0] = 0.7892765953;
            parameters[1] = -1.0606510837;
            parameters[2] = 2.2071936510;
        } else if (component.getName().equals("MEG2")) {
            parameters[0] = 0.8581331725;
            parameters[1] = -1.0053180150;
            parameters[2] = 1.2736063639;
        } else if (component.getName().equals("TEG2")) {
            parameters[0] = 1.0008858863;
            parameters[1] = 1.8649645470;
            parameters[2] = -4.6720397496;
        } else if (component.getName().equals("TEG")) {
            parameters[0] = 0.903477158616734;
            parameters[1] = 1.514853438;
            parameters[2] = -1.86430399826;
        } else {
            parameters[0] = m;
            parameters[1] = 0;
            parameters[2] = 0;
        }
    }

    @Override
    public Object clone() {
        AtractiveTermCPAstatoil atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermCPAstatoil) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }
        // atractiveTerm.parameters = (double[]) parameters.clone();
        // System.arraycopy(parameters,0, atractiveTerm.parameters, 0,
        // parameters.length);
        return atractiveTerm;
    }

    @Override
    public void init() {
        super.init();
        if (!getComponent().getName().equals("TEG")) {
            parameters[0] = m;
        }
    }

    @Override
    public double alpha(double temperature) {
        double Tr = temperature / getComponent().getTC();
        double temp1 = 1.0 - Math.sqrt(Tr);
        double var = 1.0 + parameters[0] * temp1 + parameters[1] * temp1 * temp1
                + parameters[2] * temp1 * temp1 * temp1;
        return var * var;// Math.pow(1.0+parameters[0]*(1.0-Math.sqrt(Tr))+parameters[1]*temp1*temp1+parameters[2]*temp1*temp1*temp1,2.0);
    }

    @Override
    public double aT(double temperature) {
        if (temperature / getComponent().getTC() > 1.0) {
            return super.aT(temperature);
        } else {
            return getComponent().geta() * alpha(temperature);
        }
    }

    @Override
    public double diffalphaT(double temperature) {
        double Tr = temperature / getComponent().getTC();
        double TC = getComponent().getTC();
        return 2.0
                * (1.0 + parameters[0] * (1.0 - Math.sqrt(Tr))
                        + parameters[1] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
                        + parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 3.0))
                * (-parameters[0] / Math.sqrt(Tr) / TC / 2.0
                        - parameters[1] * (1.0 - Math.sqrt(Tr)) / Math.sqrt(Tr) / TC
                        - 3.0 / 2.0 * parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
                                / Math.sqrt(Tr) / TC);
    }

    @Override
    public double diffdiffalphaT(double temperature) {
        double Tr = temperature / getComponent().getTC();
        double TC = getComponent().getTC();
        return 2.0
                * Math.pow(-parameters[0] / Math.sqrt(Tr) / TC / 2.0
                        - parameters[1] * (1.0 - Math.sqrt(Tr)) / Math.sqrt(Tr) / TC
                        - 3.0 / 2.0 * parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
                                / Math.sqrt(Tr) / TC,
                        2.0)
                + 2.0 * (1.0 + parameters[0] * (1.0 - Math.sqrt(Tr))
                        + parameters[1] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
                        + parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 3.0))
                        * (parameters[0] / Math.sqrt(Tr * Tr * Tr) / (TC * TC) / 4.0
                                + parameters[1] / temperature / TC / 2.0
                                + parameters[1] * (1.0 - Math.sqrt(Tr)) / Math.sqrt(Tr * Tr * Tr)
                                        / (TC * TC) / 2.0
                                + 3.0 / 2.0 * parameters[2] * (1.0 - Math.sqrt(Tr)) / temperature
                                        / TC
                                + 3.0 / 4.0 * parameters[2] * Math.pow(1.0 - Math.sqrt(Tr), 2.0)
                                        / Math.sqrt(Tr * Tr * Tr) / (TC * TC));
    }

    @Override
    public double diffaT(double temperature) {
        if (temperature / getComponent().getTC() > 1.0) {
            return super.diffaT(temperature);
        } else {
            return getComponent().geta() * diffalphaT(temperature);
        }
    }

    @Override
    public double diffdiffaT(double temperature) {
        if (temperature / getComponent().getTC() > 1.0) {
            return super.diffdiffaT(temperature);
        } else {
            return getComponent().geta() * diffdiffalphaT(temperature);
        }
    }
}
