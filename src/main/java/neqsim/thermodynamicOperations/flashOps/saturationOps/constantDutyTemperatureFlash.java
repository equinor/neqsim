/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */
package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;

public class constantDutyTemperatureFlash extends constantDutyFlash {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new bubblePointFlash
     */
    public constantDutyTemperatureFlash() {
    }

    public constantDutyTemperatureFlash(SystemInterface system) {
        super(system);
    }

    public void run() {

        system.init(0);
        system.init(2);

        int iterations = 0, maxNumberOfIterations = 10000;
        double yold = 0, ytotal = 1, deriv = 0, funk = 0, dkidt = 0, dyidt = 0, dxidt = 0, Told = 0;

        do {
            iterations++;
            // system.setBeta(beta+0.65);
            system.init(2);

            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                system.getPhases()[0].getComponents()[i]
                        .setK(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient()
                                / system.getPhases()[1].getComponents()[i].getFugasityCoeffisient());
                system.getPhases()[1].getComponents()[i]
                        .setK(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient()
                                / system.getPhases()[1].getComponents()[i].getFugasityCoeffisient());
            }

            system.calc_x_y_nonorm();

            funk = 0e0;
            deriv = 0e0;

            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                dkidt = (system.getPhases()[0].getComponents()[i].getdfugdt()
                        - system.getPhases()[1].getComponents()[i].getdfugdt())
                        * system.getPhases()[0].getComponents()[i].getK();
                // dxidt=-system.getPhases()[0].getComponents()[i].getx() *
                // system.getPhases()[0].getComponents()[i].getx()*1.0/system.getPhases()[0].getComponents()[i].getz()*system.getBeta()*dkidt;
                dxidt = -system.getPhases()[0].getComponents()[i].getz() * system.getBeta() * dkidt / Math.pow(
                        1.0 - system.getBeta() + system.getBeta() * system.getPhases()[0].getComponents()[i].getK(), 2);
                dyidt = dkidt * system.getPhases()[0].getComponents()[i].getx()
                        + system.getPhases()[0].getComponents()[i].getK() * dxidt;
                funk = funk + system.getPhases()[1].getComponents()[i].getx()
                        - system.getPhases()[0].getComponents()[i].getx();
                deriv = deriv + dyidt - dxidt;
            }

            Told = system.getTemperature();
            system.setTemperature((Told - funk / deriv * 0.7));
            // System.out.println("Temp: " + system.getTemperature() + " funk " + funk);

        } while ((Math.abs((system.getTemperature() - Told) / system.getTemperature()) > 1e-7 && iterations < 300)
                || iterations < 3);
    }

    public void printToFile(String name) {
    }

    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }

    public SystemInterface getThermoSystem() {
        return system;
    }
}
