package neqsim.thermo.component.atractiveEosTerm;

import neqsim.thermo.component.ComponentEosInterface;

public class AttractiveTermTwuCoonStatoil extends AtractiveTermBaseClass {
    private static final long serialVersionUID = 1000;

    private double a = 0.0, b = 0.0, c = 0.0;

    /** Creates new AtractiveTermSrk */
    public AttractiveTermTwuCoonStatoil(ComponentEosInterface component) {
        super(component);
        a = this.parameters[0];
        b = this.parameters[1];
        c = this.parameters[2];
    }

    /** Creates new AtractiveTermSrk */
    public AttractiveTermTwuCoonStatoil(ComponentEosInterface component, double[] params) {
        this(component);
        // this.parameters [0] for aa benytte gitte input parametre
        System.arraycopy(params, 0, this.parameters, 0, params.length);
    }

    @Override
    public Object clone() {
        AttractiveTermTwuCoonStatoil atractiveTerm = null;
        try {
            atractiveTerm = (AttractiveTermTwuCoonStatoil) super.clone();
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
        a = this.parameters[0];
        b = this.parameters[1];
        c = this.parameters[2];

        double t = temperature;
        double TC = getComponent().getTC();
        double Tr = (t / TC);
        // System.out.println("alpha here " + Math.pow( 1.0 +
        // m*(1.0-Math.sqrt(temperature/component.getTC()))-parameters[0]*(1.0-temperature/component.getTC())*(1.0+parameters[1]*temperature/component.getTC()+parameters[2]*Math.pow(temperature/component.getTC(),2.0)),2.0));
        return Math.pow((Tr), (c * (b - 1))) * Math.exp(a * (1 - Math.pow((Tr), (b * c))));
    }

    // private double alphaCrit(double temperature){
    // c = 1+m/2.0-parameters[0]*(1.0+parameters[1]+parameters[2]);
    // d = 1.0-1.0/d;
    // return Math.pow(Math.exp(c*(1.0-Math.pow(temperature/component.getTC(),1.0*d))),2.0);
    // }
    //
    // private double diffalphaCritT(double temperature){
    // c = 1+m/2.0-parameters[0]*(1.0+parameters[1]+parameters[2]);
    // d = 1.0-1.0/d;
    // return
    // -2.0*Math.pow(Math.exp(c*(1.0-Math.pow(temperature/component.getTC(),1.0*d))),2.0)*c*Math.pow(temperature/component.getTC(),1.0*d)*d/temperature;
    // }
    //
    // private double diffdiffalphaCritT(double temperature){
    // c = 1+m/2.0-parameters[0]*(1.0+parameters[1]+parameters[2]);
    // d = 1-1.0/d;
    // double TC = component.getTC();
    // return
    // 4.0*Math.pow(Math.exp(c*(1.0-Math.pow(temperature/TC,1.0*d))),2.0)*c*c*Math.pow(Math.pow(temperature/TC,1.0*d),2.0)*d*d/(temperature*temperature)-2.0*Math.pow(Math.exp(c*(1.0-Math.pow(temperature/TC,1.0*d))),2.0)*c*Math.pow(temperature/TC,1.0*d)*d*d/(temperature*
    // temperature)+2.0*Math.pow(Math.exp(c*(1.0-Math.pow(temperature/TC,1.0*d))),2.0)*c*Math.pow(
    // temperature/TC,1.0*d)*d/(temperature*temperature);
    // }
    @Override
    public double aT(double temperature) {
        return getComponent().geta() * alpha(temperature);
    }

    @Override
    public double diffalphaT(double temperature) {
        a = this.parameters[0];
        b = this.parameters[1];
        c = this.parameters[2];

        double t = temperature;
        double TC = getComponent().getTC();
        double Tr = (t / TC);

        return Math.pow((Tr), (c * (b - 1))) * c * (b - 1) / t
                * Math.exp(a * (1 - Math.pow((Tr), (b * c))))
                - Math.pow((Tr), (c * (b - 1))) * a * Math.pow((Tr), (b * c)) * b * c / t
                        * Math.exp(a * (1 - Math.pow((Tr), (b * c))));
    }

    @Override
    public double diffdiffalphaT(double temperature) {
        a = this.parameters[0];
        b = this.parameters[1];
        c = this.parameters[2];
        double t = temperature;
        double TC = getComponent().getTC();
        double Tr = (t / TC);
        return Math.pow(Tr, (c * (b - 1))) * (c * c) * (b - 1) * (b - 1) / (t * t)
                * Math.exp(a * (1 - Math.pow(Tr, (b * c))))
                - Math.pow(Tr, (c * (b - 1))) * c * (b - 1) / (t * t)
                        * Math.exp(a * (1 - Math.pow(Tr, (b * c))))
                - 2 * Math.pow(Tr, (c * (b - 1))) * (c * c) * (b - 1) / (t * t) * a
                        * Math.pow(Tr, (b * c)) * b * Math.exp(a * (1 - Math.pow(Tr, (b * c))))
                - Math.pow(Tr, (c * (b - 1))) * a * Math.pow(Tr, (b * c)) * (b * b) * (c * c)
                        / (t * t) * Math.exp(a * (1 - Math.pow(Tr, (b * c))))
                + Math.pow(Tr, (c * (b - 1))) * a * Math.pow(Tr, (b * c)) * b * c / (t * t)
                        * Math.exp(a * (1 - Math.pow(Tr, (b * c))))
                + Math.pow(Tr, (c * (b - 1))) * (a * a) * (Math.pow(Tr, (2 * b * c))) * (b * b)
                        * (c * c) / (t * t) * Math.exp(a * (1 - Math.pow(Tr, (b * c))));
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
