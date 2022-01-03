/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:14
 */

package neqsim.thermo.component;

import neqsim.thermo.component.atractiveEosTerm.AtractiveTermTwu;

/**
 * <p>
 * ComponentTST class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentTST extends ComponentEos {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for ComponentTST.
     * </p>
     */
    public ComponentTST() {}

    /**
     * <p>
     * Constructor for ComponentTST.
     * </p>
     *
     * @param moles a double
     */
    public ComponentTST(double moles) {
        numberOfMoles = moles;
    }

    /**
     * <p>
     * Constructor for ComponentTST.
     * </p>
     *
     * @param component_name a {@link java.lang.String} object
     * @param moles a double
     * @param molesInPhase a double
     * @param compnumber a int
     */
    public ComponentTST(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);

        a = 0.427481 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
        b = .086641 * R * criticalTemperature / criticalPressure;
        // m = 0.37464 + 1.54226 * acentricFactor - 0.26992* acentricFactor *
        // acentricFactor;

        delta1 = 1.0 + Math.sqrt(2.0);
        delta2 = 1.0 - Math.sqrt(2.0);
        setAtractiveParameter(new AtractiveTermTwu(this));
    }

    /**
     * <p>
     * Constructor for ComponentTST.
     * </p>
     *
     * @param number a int
     * @param TC a double
     * @param PC a double
     * @param M a double
     * @param a a double
     * @param moles a double
     */
    public ComponentTST(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    /** {@inheritDoc} */
    @Override
    public ComponentTST clone() {
        ComponentTST clonedComponent = null;
        try {
            clonedComponent = (ComponentTST) super.clone();
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
    public double calca() {
        return .427481 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    }

    /** {@inheritDoc} */
    @Override
    public double calcb() {
        return .086641 * R * criticalTemperature / criticalPressure;
    }

    /** {@inheritDoc} */
    @Override
    public double getVolumeCorrection() {
        if (this.getRacketZ() < 1e-10) {
            return 0.0;
        } else {
            return 0.40768 * (0.29441 - this.getRacketZ()) * R * criticalTemperature
                    / criticalPressure;
        }
    }

    /**
     * <p>
     * getQpure.
     * </p>
     *
     * @param temperature a double
     * @return a double
     */
    public double getQpure(double temperature) {
        return this.getaT() / (this.getb() * R * temperature);
    }

    /**
     * <p>
     * getdQpuredT.
     * </p>
     *
     * @param temperature a double
     * @return a double
     */
    public double getdQpuredT(double temperature) {
        return dqPuredT;
    }

    /**
     * <p>
     * getdQpuredTdT.
     * </p>
     *
     * @param temperature a double
     * @return a double
     */
    public double getdQpuredTdT(double temperature) {
        return dqPuredTdT;
    }
}
