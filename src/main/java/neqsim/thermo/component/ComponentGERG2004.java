package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentGERG2004 class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentGERG2004 extends ComponentEos {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for ComponentGERG2004.
     * </p>
     */
    public ComponentGERG2004() {}

    /**
     * <p>
     * Constructor for ComponentGERG2004.
     * </p>
     *
     * @param moles a double
     */
    public ComponentGERG2004(double moles) {
        numberOfMoles = moles;
    }

    /**
     * <p>
     * Constructor for ComponentGERG2004.
     * </p>
     *
     * @param component_name a {@link java.lang.String} object
     * @param moles a double
     * @param molesInPhase a double
     * @param compnumber a int
     */
    public ComponentGERG2004(String component_name, double moles, double molesInPhase,
            int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    /**
     * <p>
     * Constructor for ComponentGERG2004.
     * </p>
     *
     * @param number a int
     * @param TC a double
     * @param PC a double
     * @param M a double
     * @param a a double
     * @param moles a double
     */
    public ComponentGERG2004(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    /** {@inheritDoc} */
    @Override
    public ComponentGERG2004 clone() {
        ComponentGERG2004 clonedComponent = null;
        try {
            clonedComponent = (ComponentGERG2004) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedComponent;
    }

    /** {@inheritDoc} */
    @Override
    public void init(double temperature, double pressure, double totalNumberOfMoles, double beta,
            int type) {
        super.init(temperature, pressure, totalNumberOfMoles, beta, type);
    }

    /** {@inheritDoc} */
    @Override
    public double getVolumeCorrection() {
        return 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calca() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcb() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public double fugcoef(PhaseInterface phase) {
        return fugacityCoefficient;
    }

    /** {@inheritDoc} */
    @Override
    public double alpha(double temperature) {
        return 1;
    }

    /** {@inheritDoc} */
    @Override
    public double diffaT(double temperature) {
        return 1;
    }

    /** {@inheritDoc} */
    @Override
    public double diffdiffaT(double temperature) {
        return 1;
    }
}
