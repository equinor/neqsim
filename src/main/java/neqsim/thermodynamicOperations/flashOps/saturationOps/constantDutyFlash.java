package neqsim.thermodynamicOperations.flashOps.saturationOps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Abstract constantDutyFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class constantDutyFlash implements constantDutyFlashInterface {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(constantDutyFlash.class);

    SystemInterface system;
    protected boolean superCritical = false;
    int i, j = 0, nummer = 0, iterations = 0, maxNumberOfIterations = 10000;
    double gibbsEnergy = 0, gibbsEnergyOld = 0;
    double Kold, deviation = 0, g0 = 0, g1 = 0;
    double lnOldOldK[], lnK[];
    double lnOldK[];
    double oldDeltalnK[], deltalnK[];
    double tm[] = {1, 1};
    double beta = 1e-5;
    int lowestGibbsEnergyPhase = 0; // lowestGibbsEnergyPhase

    /**
     * <p>
     * Constructor for constantDutyFlash.
     * </p>
     */
    public constantDutyFlash() {}

    /**
     * <p>
     * Constructor for constantDutyFlash.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public constantDutyFlash(SystemInterface system) {
        this.system = system;
        lnOldOldK = new double[system.getPhases()[0].getNumberOfComponents()];
        lnOldK = new double[system.getPhases()[0].getNumberOfComponents()];
        lnK = new double[system.getPhases()[0].getNumberOfComponents()];
        oldDeltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
        deltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
    }

    /** {@inheritDoc} */
    @Override
    public void setBeta(double beta) {
        this.beta = beta;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        system.init(0);
        system.init(2);

        int iterations = 0, maxNumberOfIterations = 10000;
        double yold = 0, ytotal = 1, deriv = 0, funk = 0, dkidt = 0, dyidt = 0, dxidt = 0, Told = 0;

        do {
            // system.setBeta(beta+0.65);
            system.init(2);

            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                system.getPhases()[0].getComponents()[i]
                        .setK(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient()
                                / system.getPhases()[1].getComponents()[i]
                                        .getFugasityCoeffisient());
                system.getPhases()[1].getComponents()[i]
                        .setK(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient()
                                / system.getPhases()[1].getComponents()[i]
                                        .getFugasityCoeffisient());
            }

            system.calc_x_y();

            funk = 0e0;
            deriv = 0e0;

            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                dkidt = (system.getPhases()[0].getComponents()[i].getdfugdt()
                        - system.getPhases()[1].getComponents()[i].getdfugdt())
                        * system.getPhases()[0].getComponents()[i].getK();
                dxidt = -system.getPhases()[0].getComponents()[i].getx()
                        * system.getPhases()[0].getComponents()[i].getx() * 1.0
                        / system.getPhases()[0].getComponents()[i].getz() * system.getBeta()
                        * dkidt;
                dyidt = dkidt * system.getPhases()[0].getComponents()[i].getx()
                        + system.getPhases()[0].getComponents()[i].getK() * dxidt;
                funk = funk + system.getPhases()[1].getComponents()[i].getx()
                        - system.getPhases()[0].getComponents()[i].getx();
                deriv = deriv + dyidt - dxidt;
            }

            Told = system.getTemperature();
            system.setTemperature((Told - funk / deriv * 0.9));
            logger.info("Temp: " + system.getTemperature());
        } while (Math.abs((system.getTemperature() - Told) / system.getTemperature()) > 1e-7);
    }

    /** {@inheritDoc} */
    @Override
    public double[][] getPoints(int i) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public double[] get(String name) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        system.display();
    }

    /**
     * {@inheritDoc}
     *
     * Getter for property superCritical.
     */
    @Override
    public boolean isSuperCritical() {
        return superCritical;
    }

    /**
     * Setter for property superCritical.
     *
     * @param superCritical New value of property superCritical.
     */
    public void setSuperCritical(boolean superCritical) {
        this.superCritical = superCritical;
    }

    /** {@inheritDoc} */
    @Override
    public String[][] getResultTable() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void addData(String name, double[][] data) {}
}
