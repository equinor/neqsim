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
public class AtractiveTermPrDanesh extends AtractiveTermPr1978 {

    private static final long serialVersionUID = 1000;

    double mMod = 0;

    /** Creates new AtractiveTermSrk */
    public AtractiveTermPrDanesh(ComponentEosInterface component) {
        super(component);
    }

    @Override
    public AtractiveTermPrDanesh clone() {
        AtractiveTermPrDanesh atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermPrDanesh) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    @Override
	public void init() {
        m = (0.37464 + 1.54226 * getComponent().getAcentricFactor()
                - 0.26992 * getComponent().getAcentricFactor() * getComponent().getAcentricFactor());
    }

    @Override
	public double alpha(double temperature) {
        if (temperature > getComponent().getTC()) {
            mMod = m * 1.21;
        } else {
            mMod = m;
        }
        return Math.pow(1.0 + mMod * (1.0 - Math.sqrt(temperature / getComponent().getTC())), 2.0);
    }

    @Override
	public double aT(double temperature) {
        return getComponent().geta() * alpha(temperature);
    }

    @Override
	public double diffalphaT(double temperature) {
        if (temperature > getComponent().getTC()) {
            mMod = m * 1.21;
        } else {
            mMod = m;
        }
        return -(1.0 + mMod * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * mMod
                / Math.sqrt(temperature / getComponent().getTC()) / getComponent().getTC();
    }

    @Override
	public double diffdiffalphaT(double temperature) {
        if (temperature > getComponent().getTC()) {
            mMod = m * 1.21;
        } else {
            mMod = m;
        }

        return mMod * mMod / temperature / getComponent().getTC() / 2.0
                + (1.0 + mMod * (1.0 - Math.sqrt(temperature / getComponent().getTC()))) * m
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
