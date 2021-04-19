/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:14
 */

package neqsim.thermo.component;

import neqsim.thermo.component.atractiveEosTerm.AtractiveTermTwu;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ComponentTST extends ComponentEos {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new System_SRK_EOS
     */

    public ComponentTST() {
    }

    public ComponentTST(double moles) {
        numberOfMoles = moles;
    }

    public ComponentTST(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);

        a = 0.427481 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
        b = .086641 * R * criticalTemperature / criticalPressure;
        // m = 0.37464 + 1.54226 * acentricFactor - 0.26992* acentricFactor *
        // acentricFactor;

        delta1 = 1.0 + Math.sqrt(2.0);
        delta2 = 1.0 - Math.sqrt(2.0);
        atractiveParameter = new AtractiveTermTwu(this);
    }

    public ComponentTST(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    @Override
	public Object clone() {

        ComponentTST clonedComponent = null;
        try {
            clonedComponent = (ComponentTST) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedComponent;
    }

    @Override
	public void init(double temperature, double pressure, double totalNumberOfMoles, double beta, int type) {
        super.init(temperature, pressure, totalNumberOfMoles, beta, type);
    }

    @Override
	public double calca() {
        return .427481 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    }

    @Override
	public double calcb() {
        return .086641 * R * criticalTemperature / criticalPressure;
    }

    @Override
	public double getVolumeCorrection() {
        if (this.getRacketZ() < 1e-10) {
            return 0.0;
        } else {
            return 0.40768 * (0.29441 - this.getRacketZ()) * R * criticalTemperature / criticalPressure;
        }
    }

    public double getQpure(double temperature) {
        return this.getaT() / (this.getb() * R * temperature);
    }

    public double getdQpuredT(double temperature) {
        return dqPuredT;
    }

    public double getdQpuredTdT(double temperature) {
        return dqPuredTdT;
    }
}
