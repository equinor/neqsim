/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */
package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * dewPointTemperatureFlashDer class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class dewPointTemperatureFlashDer extends constantDutyTemperatureFlash {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for dewPointTemperatureFlashDer.
     * </p>
     */
    public dewPointTemperatureFlashDer() {}

    /**
     * <p>
     * Constructor for dewPointTemperatureFlashDer.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public dewPointTemperatureFlashDer(SystemInterface system) {
        super(system);
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        if (system.getPhase(0).getNumberOfComponents() == 1
                && system.getPressure() > system.getPhase(0).getComponent(0).getPC()) {
            setSuperCritical(true);
        }

        int iterations = 0, maxNumberOfIterations = 1000;
        double xold = 0, xtotal = 1;
        double deriv = 0, funk = 0;
        // System.out.println("starting");
        system.init(0);
        system.setBeta(0, 1.0 - 1e-15);
        system.setBeta(1, 1e-15);
        system.init(1);
        system.setNumberOfPhases(2);

        double oldTemp = 0;
        if (system.isChemicalSystem()) {
            system.getChemicalReactionOperations().solveChemEq(0);
            system.getChemicalReactionOperations().solveChemEq(1);
        }

        for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
            system.getPhases()[0].getComponents()[i]
                    .setx(system.getPhases()[0].getComponents()[i].getz());
            if (system.getPhases()[0].getComponents()[i].getIonicCharge() != 0) {
                system.getPhases()[0].getComponents()[i].setx(1e-40);
            } else {
                if (system.getPhases()[1].getComponents()[i].getName().equals("water")) {
                    system.getPhases()[1].getComponents()[i].setx(1.0);//
                } else if (system.getPhases()[1].hasComponent("water")) {
                    system.getPhases()[1].getComponents()[i].setx(1.0e-10);
                } else {
                    system.getPhases()[1].getComponents()[i]
                            .setx(1.0 / system.getPhases()[0].getComponents()[i].getK()
                                    * system.getPhases()[1].getComponents()[i].getz());
                }
            }
        }
        // system.setPressure(system.getPhases()[0].getAntoineVaporPressure(system.getTemperature()));
        xtotal = 0.0;
        for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
            xtotal += system.getPhases()[1].getComponents()[i].getx();
        }
        for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
            system.getPhases()[1].getComponents()[i]
                    .setx(system.getPhases()[1].getComponents()[i].getx() / xtotal);
        }

        double ktot = 0.0, oldTemperature = 0.0, fold = 0;
        do {
            oldTemp = system.getTemperature();
            iterations++;
            system.init(2);

            xtotal = 0.0;
            double dfdT = 0.0;
            for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
                xtotal += 1.0 / system.getPhases()[0].getComponents()[i].getK()
                        * system.getPhases()[1].getComponents()[i].getz();
                dfdT -= 1.0 / system.getPhases()[0].getComponents()[i].getK()
                        * system.getPhases()[1].getComponents()[i].getz()
                        * (system.getPhases()[1].getComponents()[i].getdfugdt()
                                - system.getPhases()[0].getComponents()[i].getdfugdt());
            }
            double f = xtotal - 1.0;
            fold = f;

            // System.out.println("x" + xtotal);
            oldTemperature = system.getTemperature();

            if (iterations < 5) {
                system.setTemperature(system.getTemperature() + iterations / (iterations + 100.0)
                        * (xtotal * system.getTemperature() - system.getTemperature()));
            } else {
                system.setTemperature(
                        system.getTemperature() - iterations / (10.0 + iterations) * f / dfdT);
            }
            // System.out.println("temperature " + system.getTemperature());

            system.init(1);

            ktot = 0.0;
            for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
                xold = system.getPhases()[1].getComponents()[i].getx();
                if (system.getPhase(0).getComponent(i).getIonicCharge() != 0) {
                    system.getPhases()[0].getComponents()[i].setK(1e-40);
                } else {
                    system.getPhases()[0].getComponents()[i].setK(Math.exp(
                            system.getPhases()[1].getComponents()[i].getLogFugasityCoeffisient()
                                    - system.getPhases()[0].getComponents()[i]
                                            .getLogFugasityCoeffisient()));
                }
                system.getPhases()[1].getComponents()[i]
                        .setK(system.getPhases()[0].getComponents()[i].getK());
                system.getPhases()[1].getComponents()[i]
                        .setx(1.0 / system.getPhases()[0].getComponents()[i].getK()
                                * system.getPhases()[1].getComponents()[i].getz());
                ktot += Math.abs(system.getPhases()[1].getComponents()[i].getK() - 1.0);
            }
            // system.init_x_y();

            xtotal = 0.0;
            for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
                xtotal += system.getPhases()[1].getComponents()[i].getx();
            }
            // System.out.println("xtotal " + xtotal);
            for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
                system.getPhases()[1].getComponents()[i]
                        .setx(system.getPhases()[1].getComponents()[i].getx() / xtotal);
            }
        } while (((Math.abs(xtotal - 1.0) > 1e-6)
                || Math.abs(oldTemp - system.getTemperature()) / oldTemp > 1e-4)
                || iterations < 3 && (iterations < maxNumberOfIterations));
        if (Math.abs(xtotal - 1.0) > 1e-5
                || ktot < 1.0e-3 && system.getPhase(0).getNumberOfComponents() > 1) {
            setSuperCritical(true);
        }
        if (ktot < 1.0e-3) {
            if (system.getTemperature() < 90.0) {
                setSuperCritical(true);
            } else {
                setSuperCritical(false);
                // system.setTemperature(system.getTemperature() - 10.0);
                // run();
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void printToFile(String name) {}
}
