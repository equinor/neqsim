package neqsim.thermo.component;

import neqsim.thermo.component.atractiveEosTerm.AtractiveTermPr;

/**
 * <p>
 * ComponentPR class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentPR extends ComponentEos {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for ComponentPR.
     * </p>
     */
    public ComponentPR() {}

    /**
     * <p>
     * Constructor for ComponentPR.
     * </p>
     *
     * @param moles a double
     */
    public ComponentPR(double moles) {
        numberOfMoles = moles;
    }

    /**
     * <p>
     * Constructor for ComponentPR.
     * </p>
     *
     * @param component_name a {@link java.lang.String} object
     * @param moles a double
     * @param molesInPhase a double
     * @param compnumber a int
     */
    public ComponentPR(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);

        a = .45724333333 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
        b = .077803333 * R * criticalTemperature / criticalPressure;
        // m = 0.37464 + 1.54226 * acentricFactor - 0.26992* acentricFactor *
        // acentricFactor;

        delta1 = 1.0 + Math.sqrt(2.0);
        delta2 = 1.0 - Math.sqrt(2.0);
        setAtractiveParameter(new AtractiveTermPr(this));

        double[] surfTensInfluenceParamtemp = {1.3192, 1.6606, 1.1173, 0.8443};
        this.surfTensInfluenceParam = surfTensInfluenceParamtemp;
    }

    /**
     * <p>
     * Constructor for ComponentPR.
     * </p>
     *
     * @param number a int
     * @param TC a double
     * @param PC a double
     * @param M a double
     * @param a a double
     * @param moles a double
     */
    public ComponentPR(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void init(double temperature, double pressure, double totalNumberOfMoles, double beta,
            int type) {
        super.init(temperature, pressure, totalNumberOfMoles, beta, type);
    }

    /** {@inheritDoc} */
    @Override
    public double calca() {
        return .45724333333 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    }

    /** {@inheritDoc} */
    @Override
    public double calcb() {
        return .077803333 * R * criticalTemperature / criticalPressure;
    }

    /** {@inheritDoc} */
    @Override
    public double getVolumeCorrection() {
        if (ionicCharge != 0) {
            return 0.0;
        }
        if (Math.abs(getVolumeCorrectionConst()) > 1.0e-10) {
            return getVolumeCorrectionConst() * b;
        } else if (Math.abs(this.getRacketZ()) < 1e-10) {
            racketZ = 0.29056 - 0.08775 * getAcentricFactor();
        }
        return 0.50033 * (0.25969 - this.getRacketZ()) * R * criticalTemperature / criticalPressure;
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

    /** {@inheritDoc} */
    @Override
    public double getSurfaceTenisionInfluenceParameter(double temperature) {
        double TR = 1.0 - temperature / getTC();
        if (TR < 0) {
            TR = 0.5;
        }
        double AA = -1.0e-16
                / (surfTensInfluenceParam[0] + surfTensInfluenceParam[1] * getAcentricFactor());
        double BB = 1.0e-16
                / (surfTensInfluenceParam[2] + surfTensInfluenceParam[3] * getAcentricFactor());

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
