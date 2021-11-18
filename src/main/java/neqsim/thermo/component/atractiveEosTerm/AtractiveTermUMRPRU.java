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
public class AtractiveTermUMRPRU extends AtractiveTermPr {
    private static final long serialVersionUID = 1000;

    /** Creates new AtractiveTermSrk */
    public AtractiveTermUMRPRU(ComponentEosInterface component) {
        super(component);
        m = (0.384401 + 1.52276 * component.getAcentricFactor()
                - 0.213808 * component.getAcentricFactor() * component.getAcentricFactor()
                + 0.034616 * Math.pow(component.getAcentricFactor(), 3.0)
                - 0.001976 * Math.pow(component.getAcentricFactor(), 4.0));
    }

    @Override
    public Object clone() {
        AtractiveTermUMRPRU atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermUMRPRU) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return atractiveTerm;
    }

    @Override
    public void init() {
        m = (0.384401 + 1.52276 * getComponent().getAcentricFactor()
                - 0.213808 * getComponent().getAcentricFactor() * getComponent().getAcentricFactor()
                + 0.034616 * Math.pow(getComponent().getAcentricFactor(), 3.0)
                - 0.001976 * Math.pow(getComponent().getAcentricFactor(), 4.0));
    }
}
