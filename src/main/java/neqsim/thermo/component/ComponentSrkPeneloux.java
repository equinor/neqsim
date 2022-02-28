package neqsim.thermo.component;

import neqsim.thermo.component.attractiveEosTerm.AttractiveTermSrk;

/**
 * <p>
 * ComponentSrkPeneloux class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentSrkPeneloux extends ComponentSrk {
    private static final long serialVersionUID = 1000;

    private double factTemp = Math.pow(2.0, 1.0 / 3.0);

    /**
     * <p>
     * Constructor for ComponentSrkPeneloux.
     * </p>
     *
     * @param component_name a {@link java.lang.String} object
     * @param moles a double
     * @param molesInPhase a double
     * @param compnumber a int
     */
    public ComponentSrkPeneloux(String component_name, double moles, double molesInPhase,
            int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);

        a = 1.0 / (9.0 * (Math.pow(2.0, 1.0 / 3.0) - 1.0)) * R * R * criticalTemperature
                * criticalTemperature / criticalPressure;
        b = (Math.pow(2.0, 1.0 / 3.0) - 1.0) / 3.0 * R * criticalTemperature / criticalPressure;
        // double volCorr = getVolumeCorrection() / 1.0e5 * 0.0;
        // b -= volCorr;
        delta1 = 1.0;
        delta2 = 0.0;
        // System.out.println("a " + a);
        // attractiveParameter = new AttractiveTermSchwartzentruber(this);
        setAttractiveParameter(new AttractiveTermSrk(this));

        double[] surfTensInfluenceParamtemp =
                {-0.7708158524, 0.4990571549, 0.8645478315, -0.3509810630, -0.1611763157};
        this.surfTensInfluenceParam = surfTensInfluenceParamtemp;
    }

    /**
     * <p>
     * Constructor for ComponentSrkPeneloux.
     * </p>
     *
     * @param number a int
     * @param TC a double
     * @param PC a double
     * @param M a double
     * @param a a double
     * @param moles a double
     */
    public ComponentSrkPeneloux(int number, double TC, double PC, double M, double a,
            double moles) {
        super(number, TC, PC, M, a, moles);
    }

    /** {@inheritDoc} */
    @Override
    public ComponentSrkPeneloux clone() {
        ComponentSrkPeneloux clonedComponent = null;
        try {
            clonedComponent = (ComponentSrkPeneloux) super.clone();
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

    /** {@inheritDoc} */
    @Override
    public double calcb() {
        double volCorr = getVolumeCorrection();
        return (factTemp - 1.0) / 3.0 * R * criticalTemperature / criticalPressure - volCorr;
    }
}
