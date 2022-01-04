/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */

package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * constantDutyPressureFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class constantDutyPressureFlash extends constantDutyFlash {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for constantDutyPressureFlash.</p>
     */
    public constantDutyPressureFlash() {}

    /**
     * <p>
     * Constructor for constantDutyPressureFlash.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public constantDutyPressureFlash(SystemInterface system) {
        super(system);
    }

    /** {@inheritDoc} */
    @Override
    public void run() {

        // system.calc_x_y();
        // system.init(2);

        if (system.isChemicalSystem()) {
            system.getChemicalReactionOperations().solveChemEq(0);
        }

        int iterations = 0, maxNumberOfIterations = 10000;
        double yold = 0, ytotal = 1, deriv = 0, funk = 0, dkidp = 0, dyidp = 0, dxidp = 0, Pold = 0;

        do {
            // system.setBeta(beta+0.65);
            system.init(2);
            iterations++;
            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                system.getPhases()[0].getComponents()[i]
                        .setK(system.getPhases()[1].getComponents()[i].getFugasityCoeffisient()
                                / system.getPhases()[0].getComponents()[i]
                                        .getFugasityCoeffisient());
                system.getPhases()[1].getComponents()[i]
                        .setK(system.getPhases()[0].getComponents()[i].getK());
            }

            system.calc_x_y_nonorm();

            funk = 0.0;
            deriv = 0.0;

            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                dkidp = (system.getPhases()[1].getComponents()[i].getdfugdp()
                        - system.getPhases()[0].getComponents()[i].getdfugdp())
                        * system.getPhases()[1].getComponents()[i].getK();
                dxidp = -system.getPhases()[1].getComponents()[i].getz() * system.getBeta() * dkidp
                        / Math.pow(
                                1.0 - system.getBeta()
                                        + system.getBeta()
                                                * system.getPhases()[1].getComponents()[i].getK(),
                                2.0);
                dyidp = dkidp * system.getPhases()[1].getComponents()[i].getx()
                        + system.getPhases()[1].getComponents()[i].getK() * dxidp;
                funk += system.getPhases()[0].getComponents()[i].getx()
                        - system.getPhases()[1].getComponents()[i].getx();
                deriv += dyidp - dxidp;
            }

            // System.out.println("Pressure: " + system.getPressure() + " funk " + funk);

            Pold = system.getPressure();
            double pres = Math.abs(Pold - 0.5 * funk / deriv);
            system.setPressure(pres);
        } while ((Math.abs((system.getPressure() - Pold) / system.getPressure()) > 1e-10
                && iterations < 300) || iterations < 3);

    }

    /** {@inheritDoc} */
    @Override
    public void printToFile(String name) {}

    /** {@inheritDoc} */
    @Override
    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public SystemInterface getThermoSystem() {
        return system;
    }

}
