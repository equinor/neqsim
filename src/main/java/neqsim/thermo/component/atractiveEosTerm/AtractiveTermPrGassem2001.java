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
public class AtractiveTermPrGassem2001 extends AtractiveTermPr {

    private static final long serialVersionUID = 1000;

    protected double A = 2.0, B = 0.836, C = 0.134, D = 0.508, E = -0.0467;

    /** Creates new AtractiveTermSrk */
    public AtractiveTermPrGassem2001(ComponentEosInterface component) {
        super(component);
    }

    @Override
	public Object clone() {
        AtractiveTermPrGassem2001 atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermPrGassem2001) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    @Override
	public void init() {
        m = (0.37464 + 1.54226 * component.getAcentricFactor()
                - 0.26992 * component.getAcentricFactor() * component.getAcentricFactor());
    }

    @Override
	public double alpha(double temperature) {
        // System.out.println("alpha gassem");
        return Math.exp((A + B * temperature / component.getTC())
                * (1.0 - Math.pow(temperature / component.getTC(), C + D * component.getAcentricFactor()
                        + E * component.getAcentricFactor() * component.getAcentricFactor())));
    }

    @Override
	public double aT(double temperature) {
        return component.geta() * alpha(temperature);
    }

    @Override
	public double diffalphaT(double temperature) {
        return 1.0 / component.getTC() * alpha(temperature)
                * ((B * (1.0 - Math.pow(temperature / component.getTC(),
                        C + D * component.getAcentricFactor()
                                + E * component.getAcentricFactor() * component.getAcentricFactor())))
                        - (A + B * temperature / component.getTC())
                                * (C + D * component.getAcentricFactor()
                                        + E * component.getAcentricFactor() * component.getAcentricFactor())
                                * Math.pow(temperature / component.getTC(), C + D * component.getAcentricFactor()
                                        + E * component.getAcentricFactor() * component.getAcentricFactor() - 1.0));
    }

    @Override
	public double diffdiffalphaT(double temperature) {
        // not implemented dubble derivative
        //
        return m * m / temperature / component.getTC() / 2.0
                + (1.0 + m * (1.0 - Math.sqrt(temperature / component.getTC()))) * m
                        / Math.sqrt(temperature * temperature * temperature / (Math.pow(component.getTC(), 3.0)))
                        / (component.getTC() * component.getTC()) / 2.0;

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
