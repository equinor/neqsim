/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */
package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

public class WATcalc extends constantDutyTemperatureFlash {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(WATcalc.class);

    /** Creates new bubblePointFlash */
    public WATcalc() {
    }

    public WATcalc(SystemInterface system) {
        super(system);
    }

    public void run() {
        double sumx = 0.0;
        // system.setHydrateCheck(true);
        ThermodynamicOperations ops = new ThermodynamicOperations(system);
        int iter = 0;
        double funkOld = 0.0, deltaT = 1.0;
        double[] Ksolid = new double[system.getPhase(0).getNumberOfComponents()];
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            system.getPhases()[5].getComponent(i).setx(1.0);
            Ksolid[i] = 1.0;
        }
        do {
            iter++;
            ops.TPflash();

            sumx = 0.0;
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                system.getPhases()[5].getComponent(i).setx(Ksolid[i] * system.getPhase(0).getComponent(i).getx());
                Ksolid[i] = system.getPhase(0).getComponent(i).getFugasityCoefficient()
                        / system.getPhases()[5].getComponent(i).fugcoef(system.getPhases()[5]);
                sumx += Ksolid[i] * system.getPhase(0).getComponent(i).getx();
            }
            double funk = sumx - 1.0;
            double dfunkdt = (funk - funkOld) / deltaT;
            funkOld = funk;
            double dT = -funk / dfunkdt;
            double oldTemp = system.getTemperature();
            if (iter > 1) {
                system.setTemperature(system.getTemperature() + dT * iter * 1.0 / (5.0 + iter));
            } else {
                system.setTemperature(system.getTemperature() - 0.1);
            }
            deltaT = system.getTemperature() - oldTemp;
            // logger.info("sumx " + sumx + " deltaT "+ deltaT + " dT "+dT + " temperature "
            // + system.getTemperature());
        } while (Math.abs(sumx - 1.0) > 1e-8 && iter < 100);
        // logger.info("sumx " + sumx);

        system.setNumberOfPhases(system.getNumberOfPhases() + 1);
        system.setPhaseIndex(system.getNumberOfPhases() - 1, 5);
        system.setBeta(system.getNumberOfPhases() - 1, 1e-10);
        system.init(3);
    }

    public void printToFile(String name) {
    }
}
