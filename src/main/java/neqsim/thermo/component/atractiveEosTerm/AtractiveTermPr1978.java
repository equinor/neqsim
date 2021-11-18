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
public class AtractiveTermPr1978 extends AtractiveTermPr {
    private static final long serialVersionUID = 1000;

    /**
     * Creates new AtractiveTermSrk
     */
    public AtractiveTermPr1978(ComponentEosInterface component) {
        super(component);
        if (component.getAcentricFactor() > 0.49) {
            m = (0.379642 + 1.48503 * component.getAcentricFactor()
                    - 0.164423 * component.getAcentricFactor() * component.getAcentricFactor()
                    + 0.01666 * Math.pow(component.getAcentricFactor(), 3.0));
        } else {
            m = (0.37464 + 1.54226 * component.getAcentricFactor()
                    - 0.26992 * component.getAcentricFactor() * component.getAcentricFactor());
        }
    }

    @Override
    public Object clone() {
        AtractiveTermPr1978 atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermPr1978) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    @Override
    public void init() {
        if (getComponent().getAcentricFactor() > 0.49) {
            m = (0.379642 + 1.48503 * getComponent().getAcentricFactor()
                    - 0.164423 * getComponent().getAcentricFactor()
                            * getComponent().getAcentricFactor()
                    + 0.01666 * Math.pow(getComponent().getAcentricFactor(), 3.0));
        } else {
            m = (0.37464 + 1.54226 * getComponent().getAcentricFactor() - 0.26992
                    * getComponent().getAcentricFactor() * getComponent().getAcentricFactor());
        }
    }
}
