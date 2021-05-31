/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */
package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;
import org.apache.logging.log4j.*;

public class HCdewPointPressureFlash extends constantDutyTemperatureFlash {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(HCdewPointPressureFlash.class);

    /** Creates new bubblePointFlash */
    public HCdewPointPressureFlash() {
    }

    public HCdewPointPressureFlash(SystemInterface system) {
        super(system);
    }

    @Override
	public void run() {
        if (system.getPhase(0).getNumberOfComponents() == 1
                && system.getPressure() > system.getPhase(0).getComponent(0).getPC()) {
            setSuperCritical(true);
        }

        int iterations = 0, maxNumberOfIterations = 500;
        double xold = 0, xtotal = 1, xoldold = 0;
        double deriv = 0, funk = 0;
        // logger.info("starting");
        system.init(0);
        system.setBeta(0, 1.0 - 1e-10);
        system.setBeta(1, 1e-10);

        double oldPres = 0;
        if (system.isChemicalSystem()) {
            system.getChemicalReactionOperations().solveChemEq(0);
            system.getChemicalReactionOperations().solveChemEq(1);
        }

        for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
            system.getPhases()[0].getComponents()[i].setx(system.getPhases()[0].getComponents()[i].getz());
            if (system.getPhases()[0].getComponents()[i].getIonicCharge() != 0) {
                system.getPhases()[0].getComponents()[i].setx(1e-40);
            } else {
                system.getPhases()[1].getComponents()[i].setx(1.0 / system.getPhases()[0].getComponents()[i].getK()
                        * system.getPhases()[1].getComponents()[i].getz());
            }
        }
        // system.setPressure(system.getPhases()[0].getAntoineVaporPressure(system.getTemperature()));
        xtotal = 0.0;
        for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
            xtotal += system.getPhases()[1].getComponents()[i].getx();
        }
        double ktot = 0.0, xTotOld = 0.0, presoldold = 0;
        do {
            iterations++;
            for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
                system.getPhases()[1].getComponents()[i].setx(system.getPhases()[1].getComponents()[i].getx() / xtotal);
            }
            system.init(1);
            ktot = 0.0;
            presoldold = oldPres;
            oldPres = system.getPressure();
            for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
                do {
                    xold = system.getPhases()[1].getComponents()[i].getx();
                    if (system.getPhase(0).getComponent(i).getIonicCharge() != 0) {
                        system.getPhases()[0].getComponents()[i].setK(1e-40);
                    } else {
                        system.getPhases()[0].getComponents()[i].setK(
                                Math.exp(Math.log(system.getPhases()[1].getComponents()[i].getFugasityCoeffisient())
                                        - Math.log(system.getPhases()[0].getComponents()[i].getFugasityCoeffisient())));
                    }
                    system.getPhases()[1].getComponents()[i].setK(system.getPhases()[0].getComponents()[i].getK());
                    system.getPhases()[1].getComponents()[i].setx(1.0 / system.getPhases()[0].getComponents()[i].getK()
                            * system.getPhases()[1].getComponents()[i].getz());
                } while (Math.abs(system.getPhases()[1].getComponents()[i].getx() - xold) > 1e-4);
                ktot += Math.abs(system.getPhases()[1].getComponents()[i].getK() - 1.0);
            }
            xoldold = xTotOld;
            xTotOld = xtotal;
            xtotal = 0.0;
            for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
                xtotal += system.getPhases()[1].getComponents()[i].getx();
            }

            double newPres = 0;
            if (iterations < 4) {
                newPres = system.getPressure() + 0.1;
            } else {
                double dxTOTdP = (xTotOld - xoldold) / (oldPres - presoldold);
                newPres = system.getPressure() - iterations * 1.0 / (iterations + 20000.0) * (xtotal - 1) / dxTOTdP;
            }
            system.setPressure(newPres);

            // logger.info("iter " + iterations + " pressure " + system.getPressure() + "
            // xtotal " + xtotal);
        } while ((((Math.abs(xtotal) - 1.0) > 1e-10) || Math.abs(oldPres - system.getPressure()) / oldPres > 1e-9)
                && (iterations < maxNumberOfIterations));
        // logger.info("iter " + iterations + " XTOT " +xtotal + " k "
        // +system.getPhases()[1].getComponents()[0].getK());
        if (Math.abs(xtotal - 1.0) >= 1e-5 || ktot < 1e-3 && system.getPhase(0).getNumberOfComponents() > 1) {
            setSuperCritical(true);
        }
    }

    @Override
	public void printToFile(String name) {
    }
}
