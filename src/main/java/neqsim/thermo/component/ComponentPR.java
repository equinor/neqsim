/*
* System_SRK_EOS.java
*
* Created on 8. april 2000, 23:14
*/
package neqsim.thermo.component;

import neqsim.thermo.component.atractiveEosTerm.AtractiveTermPr;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ComponentPR extends ComponentEos {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new System_SRK_EOS
     */
    public ComponentPR() {
    }

    public ComponentPR(double moles) {
        numberOfMoles = moles;
    }

    public ComponentPR(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);

        a = .45724333333 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
        b = .077803333 * R * criticalTemperature / criticalPressure;
        // m = 0.37464 + 1.54226 * acentricFactor - 0.26992* acentricFactor *
        // acentricFactor;

        delta1 = 1.0 + Math.sqrt(2.0);
        delta2 = 1.0 - Math.sqrt(2.0);
        setAtractiveParameter(new AtractiveTermPr(this));

        double[] surfTensInfluenceParamtemp = { 1.3192, 1.6606, 1.1173, 0.8443 };
        this.surfTensInfluenceParam = surfTensInfluenceParamtemp;
    }

    public ComponentPR(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    @Override
    public ComponentPR clone() {

        ComponentPR clonedComponent = null;
        try {
            clonedComponent = (ComponentPR) super.clone();
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
        return .45724333333 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    }

    @Override
	public double calcb() {
        return .077803333 * R * criticalTemperature / criticalPressure;
    }

    @Override
	public double getVolumeCorrection() {
        if (Math.abs(this.getRacketZ()) < 1e-10) {
            racketZ = 0.29056 - 0.08775 * getAcentricFactor();
        }
        return 0.50033 * (0.25969 - this.getRacketZ()) * R * criticalTemperature / criticalPressure;
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

    @Override
	public double getSurfaceTenisionInfluenceParameter(double temperature) {
        double TR = 1.0 - temperature / getTC();
        if (TR < 0) {
            TR = 0.5;
        }
        double AA = -1.0e-16 / (surfTensInfluenceParam[0] + surfTensInfluenceParam[1] * getAcentricFactor());
        double BB = 1.0e-16 / (surfTensInfluenceParam[2] + surfTensInfluenceParam[3] * getAcentricFactor());

        // System.out.println("scale2 " + aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) *
        // (AA * TR + BB));
        if (componentName.equals("water")) {
            AA = -6.99632E-17;
            BB = 5.68347E-17;
        }
        // System.out.println("AA " + AA + " BB " + BB);
        if (componentName.equals("MEG")) {
            return 0.00000000000000000007101030813216131;
        }
        return aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * (AA * TR + BB);/// Math.pow(ThermodynamicConstantsInterface.avagadroNumber,
                                                                          /// 2.0 / 3.0);
    }
}
