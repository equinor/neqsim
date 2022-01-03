/*
 * AtractiveTermBaseClass.java
 *
 * Created on 13. mai 2001, 21:58
 */

package neqsim.thermo.component.atractiveEosTerm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>AtractiveTermBaseClass class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AtractiveTermBaseClass implements AtractiveTermInterface {

    private static final long serialVersionUID = 1000;

    private ComponentEosInterface component = null;
    protected double m;
    protected double parameters[] = new double[3];
    protected double parametersSolid[] = new double[3];

    static Logger logger = LogManager.getLogger(AtractiveTermBaseClass.class);

    /**
     * <p>Constructor for AtractiveTermBaseClass.</p>
     */
    public AtractiveTermBaseClass() {

    }

    /**
     * Creates new AtractiveTermBaseClass
     *
     * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
     */
    public AtractiveTermBaseClass(ComponentEosInterface component) {
        this.setComponent(component);
    }

    /** {@inheritDoc} */
    @Override
    public void setm(double val) {
        this.m = val;
        logger.info("does not solve for accentric when new m is set... in AccentricBase class");
    }

    /** {@inheritDoc} */
    @Override
    public AtractiveTermBaseClass clone() {
        AtractiveTermBaseClass atractiveTerm = null;
        try {
            atractiveTerm = (AtractiveTermBaseClass) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        // atSystem.out.println("m " + m);ractiveTerm.parameters = (double[])
        // parameters.clone();
        // System.arraycopy(parameters,0, atractiveTerm.parameters, 0,
        // parameters.length);
        return atractiveTerm;
    }

    /** {@inheritDoc} */
    @Override
    public void init() {}

    /** {@inheritDoc} */
    @Override
    public double diffdiffalphaT(double temperature) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public double diffdiffaT(double temperature) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public double aT(double temperature) {
        return getComponent().geta();
    }

    /** {@inheritDoc} */
    @Override
    public double alpha(double temperature) {
        return 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public double diffaT(double temperature) {
        return 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public double diffalphaT(double temperature) {
        return 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public void setParameters(int i, double val) {
        parameters[i] = val;
    }

    /** {@inheritDoc} */
    @Override
    public double getParameters(int i) {
        return parameters[i];
    }

    ComponentEosInterface getComponent() {
        return component;
    }

    void setComponent(ComponentEosInterface component) {
        this.component = component;
    }

}
