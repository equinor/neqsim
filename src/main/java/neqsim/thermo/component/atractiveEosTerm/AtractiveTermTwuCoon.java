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
public class AtractiveTermTwuCoon extends AtractiveTermBaseClass {
        private static final long serialVersionUID = 1000;

        private double a = -0.201158, b = 0.141599, c = 2.29528, d = -0.660145, e = 0.500315,
                        f = 2.63165;

    private double a = -0.201158, b = 0.141599, c = 2.29528, d = -0.660145, e = 0.500315, f = 2.63165;

    /** Creates new AtractiveTermSrk */
    public AtractiveTermTwuCoon(ComponentEosInterface component) {
        super(component);
        m = component.getAcentricFactor();
    }

    /** Creates new AtractiveTermSrk */
//    public AtractiveTermTwuCoon(ComponentEosInterface component, double[] params) {
//        this(component);
//        System.arraycopy(params,0,this.parameters,0,params.length);
//       // c = 1+m/2.0-parameters[0]*(1.0+parameters[1]+parameters[2]);
//       // d = 1-1.0/d;
//    }

    @Override
    public AtractiveTermTwuCoon clone() {
        AtractiveTermTwuCoon atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermTwuCoon) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        /** Creates new AtractiveTermSrk */
        // public AtractiveTermTwuCoon(ComponentEosInterface component, double[] params) {
        // this(component);
        // System.arraycopy(params,0,this.parameters,0,params.length);
        // // c = 1+m/2.0-parameters[0]*(1.0+parameters[1]+parameters[2]);
        // // d = 1-1.0/d;
        // }

        @Override
        public Object clone() {
                AtractiveTermTwuCoon atractiveTerm = null;
                try {
                        atractiveTerm = (AtractiveTermTwuCoon) super.clone();
                } catch (Exception e) {
                        logger.error("Cloning failed.", e);
                }

                return atractiveTerm;
        }

        @Override
        public void init() {
                // m = (0.48508 + 1.55191 * component.getAcentricFactor() - 0.15613 *
                // component.getAcentricFactor() * component.getAcentricFactor());
        }

        @Override
        public double alpha(double temperature) {
                double Tr = (temperature / getComponent().getTC());
                // System.out.println("alpha here " + Math.pow( 1.0 +
                // m*(1.0-Math.sqrt(temperature/component.getTC()))-parameters[0]*(1.0-temperature/component.getTC())*(1.0+parameters[1]*temperature/component.getTC()+parameters[2]*Math.pow(temperature/component.getTC(),2.0)),2.0));
                return Math.pow(Tr, a) * Math.exp(b * (1 - Math.pow(Tr, c))) + m * (Math.pow(Tr, d)
                                * Math.exp(e * (1 - Math.pow(Tr, f)))
                                - Math.pow(Tr, a) * Math.exp(b * (1 - Math.pow(Tr, c))));
        }

        private double alphaCrit(double temperature) {
                // c = 1+m/2.0-parameters[0]*(1.0+parameters[1]+parameters[2]);
                // d = 1.0-1.0/d;
                return Math.pow(Math.exp(c
                                * (1.0 - Math.pow(temperature / getComponent().getTC(), 1.0 * d))),
                                2.0);
        }

        private double diffalphaCritT(double temperature) {
                c = 1 + m / 2.0 - parameters[0] * (1.0 + parameters[1] + parameters[2]);
                d = 1.0 - 1.0 / d;
                return -2.0 * Math.pow(Math.exp(c
                                * (1.0 - Math.pow(temperature / getComponent().getTC(), 1.0 * d))),
                                2.0) * c * Math.pow(temperature / getComponent().getTC(), 1.0 * d)
                                * d / temperature;
        }

        private double diffdiffalphaCritT(double temperature) {
                double t = temperature;
                double TC = getComponent().getTC();
                double Tr = (t / TC);
                return 4.0 * Math.pow(Math.exp(c * (1.0 - Math.pow(temperature / TC, 1.0 * d))),
                                2.0) * c * c * Math.pow(Math.pow(temperature / TC, 1.0 * d), 2.0)
                                * d * d / (temperature * temperature)
                                - 2.0 * Math.pow(Math.exp(
                                                c * (1.0 - Math.pow(temperature / TC, 1.0 * d))),
                                                2.0) * c * Math.pow(temperature / TC, 1.0 * d) * d
                                                * d / (temperature * temperature)
                                + 2.0 * Math.pow(Math.exp(
                                                c * (1.0 - Math.pow(temperature / TC, 1.0 * d))),
                                                2.0) * c * Math.pow(temperature / TC, 1.0 * d) * d
                                                / (temperature * temperature);
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
                double t = temperature;
                double TC = getComponent().getTC();
                double Tr = (t / TC);

                return Math.pow((Tr), a) * a / t * Math.exp(b * (1 - Math.pow(Tr, c)))
                                - Math.pow((Tr), a) * b * Math.pow((Tr), c) * c / t
                                                * Math.exp(b * (1 - Math.pow((Tr), c)))
                                + m * (Math.pow((Tr), d) * d / t
                                                * Math.exp(e * (1 - Math.pow(Tr, f)))
                                                - Math.pow(Tr, d) * e * Math.pow(Tr, f) * f / t
                                                                * Math.exp(e * (1
                                                                                - Math.pow(Tr, f)))
                                                - Math.pow(Tr, a) * a / t * Math
                                                                .exp(b * (1 - Math.pow(Tr, c)))
                                                + Math.pow(Tr, a) * b * Math.pow(Tr, c) * c / t
                                                                * Math.exp(b * (1 - Math.pow(Tr,
                                                                                c))));
        }

        @Override
        public double diffdiffalphaT(double temperature) {
                double t = temperature;
                double TC = getComponent().getTC();
                double Tr = (t / TC);
                return Math.pow(Tr, a) * a * a / Math.pow(t, 2)
                                * Math.exp(b * (1 - Math.pow(Tr, c)))
                                - Math.pow(Tr, a) * a / Math.pow(t, 2)
                                                * Math.exp(b * (1 - Math.pow(Tr, c)))
                                - 2 * Math.pow(Tr, a) * a / Math.pow(t, 2) * b * Math.pow(Tr, c) * c
                                                * Math.exp(b * (1 - Math.pow(Tr, c)))
                                - Math.pow(Tr, a) * b * Math.pow(Tr, c) * Math.pow(c, 2)
                                                / Math.pow(t, 2)
                                                * Math.exp(b * (1 - Math.pow(Tr, c)))
                                + Math.pow(Tr, a) * b * Math.pow(Tr, c) * c / Math.pow(t, 2)
                                                * Math.exp(b * (1 - Math.pow(Tr, c)))
                                + Math.pow(Tr, a) * Math.pow(b, 2) * Math.pow(Math.pow(Tr, c), 2)
                                                * Math.pow(c, 2) / Math.pow(t, 2)
                                                * Math.exp(b * (1 - Math.pow(Tr, c)))
                                + m * (Math.pow(Tr, d) * Math.pow(d, 2) / Math.pow(t, 2)
                                                * Math.exp(e * (1 - Math.pow(Tr, f)))
                                                - Math.pow(Tr, d) * d / Math.pow(t, 2) * Math
                                                                .exp(e * (1 - Math.pow(Tr, f)))
                                                - 2 * Math.pow(Tr, d) * d / Math.pow(t, 2) * e
                                                                * Math.pow(Tr, f) * f
                                                                * Math.exp(e * (1
                                                                                - Math.pow(Tr, f)))
                                                - Math.pow(Tr, d) * e * Math.pow(Tr, f)
                                                                * Math.pow(f, 2) / Math.pow(t, 2)
                                                                * Math.exp(e * (1
                                                                                - Math.pow(Tr, f)))
                                                + Math.pow(Tr, d) * e * Math.pow(Tr, f) * f
                                                                / Math.pow(t, 2)
                                                                * Math.exp(e * (1
                                                                                - Math.pow(Tr, f)))
                                                + Math.pow(Tr, d) * Math.pow(e, 2)
                                                                * Math.pow(Math.pow(Tr, f), 2)
                                                                * Math.pow(f, 2) / Math.pow(t, 2)
                                                                * Math.exp(e * (1
                                                                                - Math.pow(Tr, f)))
                                                - Math.pow(Tr, a) * Math.pow(a, 2) / Math.pow(t, 2)
                                                                * Math.exp(b * (1
                                                                                - Math.pow(Tr, c)))
                                                + Math.pow(Tr, a) * a / Math.pow(t, 2) * Math
                                                                .exp(b * (1 - Math.pow(Tr, c)))
                                                + 2 * Math.pow(Tr, a) * a / Math.pow(t, 2) * b
                                                                * Math.pow(Tr, c) * c
                                                                * Math.exp(b * (1
                                                                                - Math.pow(Tr, c)))
                                                + Math.pow(Tr, a) * b * Math.pow(Tr, c)
                                                                * Math.pow(c, 2) / Math.pow(t, 2)
                                                                * Math.exp(b * (1
                                                                                - Math.pow(Tr, c)))
                                                - Math.pow(Tr, a) * b * Math.pow(Tr, c) * c
                                                                / Math.pow(t, 2)
                                                                * Math.exp(b * (1
                                                                                - Math.pow(Tr, c)))
                                                - Math.pow(Tr, a) * Math.pow(b, 2)
                                                                * Math.pow(Math.pow(Tr, c), 2)
                                                                * Math.pow(c, 2) / Math.pow(t, 2)
                                                                * Math.exp(b * (1 - Math.pow(Tr,
                                                                                c))));
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
