/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:14
 */

package neqsim.thermo.component;

import neqsim.thermo.component.atractiveEosTerm.AtractiveTermRk;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ComponentRK extends ComponentEos {
    private static final long serialVersionUID = 1000;

    /**
     * Creates new System_SRK_EOS Ev liten fil ja.
     */

    public ComponentRK() {}

    public ComponentRK(double moles) {
        numberOfMoles = moles;
    }

    public ComponentRK(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);

        a = 1.0 / (9.0 * (Math.pow(2.0, 1.0 / 3.0) - 1.0)) * R * R * criticalTemperature
                * criticalTemperature / criticalPressure;
        b = (Math.pow(2.0, 1.0 / 3.0) - 1.0) / 3.0 * R * criticalTemperature / criticalPressure;
        delta1 = 1.0;
        delta2 = 0.0;
        setAtractiveParameter(new AtractiveTermRk(this));
    }

    public ComponentRK(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    @Override
    public Object clone() {
        ComponentRK clonedComponent = null;
        try {
            clonedComponent = (ComponentRK) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedComponent;
    }

    @Override
    public void init(double temperature, double pressure, double totalNumberOfMoles, double beta,
            int type) {
        super.init(temperature, pressure, totalNumberOfMoles, beta, type);
    }

    @Override
    public double calca() {
        return 1.0 / (9.0 * (Math.pow(2.0, 1.0 / 3.0) - 1.0)) * R * R * criticalTemperature
                * criticalTemperature / criticalPressure;
    }

    @Override
    public double calcb() {
        return (Math.pow(2.0, 1.0 / 3.0) - 1.0) / 3.0 * R * criticalTemperature / criticalPressure;
    }

    @Override
    public double getVolumeCorrection() {
        return 0.0;
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
