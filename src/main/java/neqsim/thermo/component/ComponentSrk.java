/*
* System_SRK_EOS.java
*
* Created on 8. april 2000, 23:14
*/
package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermSrk;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ComponentSrk extends ComponentEos {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new System_SRK_EOS
     * 
     */
    private double factTemp = Math.pow(2.0, 1.0 / 3.0);

    public ComponentSrk() {
    }

    public ComponentSrk(double moles) {
        numberOfMoles = moles;
    }

    public ComponentSrk(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);

        a = 1.0 / (9.0 * (Math.pow(2.0, 1.0 / 3.0) - 1.0)) * R * R * criticalTemperature * criticalTemperature
                / criticalPressure;
        b = (Math.pow(2.0, 1.0 / 3.0) - 1.0) / 3.0 * R * criticalTemperature / criticalPressure;
        delta1 = 1.0;
        delta2 = 0.0;
        // System.out.println("a " + a);
        // atractiveParameter = new AtractiveTermSchwartzentruber(this);
        setAtractiveParameter(new AtractiveTermSrk(this));

        double[] surfTensInfluenceParamtemp = { -0.7708158524, 0.4990571549, 0.8645478315, -0.3509810630,
                -0.1611763157 };
        this.surfTensInfluenceParam = surfTensInfluenceParamtemp;

    }

    public ComponentSrk(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    @Override
	public Object clone() {

        ComponentSrk clonedComponent = null;
        try {
            clonedComponent = (ComponentSrk) super.clone();
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
	public double getVolumeCorrection() {
        if (ionicCharge != 0) {
            return 0.0;
        }
        if (Math.abs(this.getRacketZ()) < 1e-10) {
            racketZ = 0.29056 - 0.08775 * getAcentricFactor();
        }
        // System.out.println("racket Z " + racketZ + " vol correction " +
        // (0.40768*(0.29441-this.getRacketZ())*R*criticalTemperature/criticalPressure));
        return 0.40768 * (0.29441 - this.getRacketZ()) * R * criticalTemperature / criticalPressure;
    }

    @Override
	public double calca() {
        return 1.0 / (9.0 * (factTemp - 1.0)) * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    }

    @Override
	public double calcb() {
        return (factTemp - 1.0) / 3.0 * R * criticalTemperature / criticalPressure;
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
            if (componentName.equals("CO2")) {
                TR = 1.0 - 0.9;
            } else {
                TR = 0.6;
            }
        }
        double AA = (surfTensInfluenceParam[0] + surfTensInfluenceParam[1] * getAcentricFactor());
        double BB = surfTensInfluenceParam[2] + surfTensInfluenceParam[3] * getAcentricFactor()
                + surfTensInfluenceParam[4] * getAcentricFactor() * getAcentricFactor();

        if (componentName.equals("water")) {
            return 0.000000000000000000019939776242117276;
        }
        if (componentName.equals("MEG")) {
            return 0.00000000000000000009784248343727627;
        }
        if (componentName.equals("TEG")) {
            return 0.000000000000000000901662233371129;
        }
        // System.out.println("scale2 " + aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) *
        // Math.pow(AA * TR, BB)/1.0e17);
        // System.out.println("scale2 " +
        // Math.pow(ThermodynamicConstantsInterface.avagadroNumber, 2.0 / 3.0));
        return (aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * (AA * TR + BB))
                / Math.pow(ThermodynamicConstantsInterface.avagadroNumber, 2.0 / 3.0);

    }
}
