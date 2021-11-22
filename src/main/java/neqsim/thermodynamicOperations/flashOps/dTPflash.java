/*
 * dTPflash.java
 *
 * Created on 2. oktober 2000, 22:26
 */

package neqsim.thermodynamicOperations.flashOps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.thermo.system.SystemInterface;

/**
 * @author Even Solbraa
 * @version
 */
public class dTPflash extends TPflash {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(dTPflash.class);
    String[] flashComp = null;

    // SystemInterface clonedSystem;

    /** Creates new dTPflash */
    public dTPflash() {}

    public dTPflash(SystemInterface system, String[] comps) {
        this.system = system;
        this.flashComp = comps;
    }

    @Override
    public void run() {
        iterations = 0;
        double diff = 0.0;
        double fracdiff = 0.0;

        // system.setBeta(0.5);
        do {
            diff = 0.0;
            fracdiff = 0.0;
            iterations++;
            system.init(1);
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                boolean hasgot = false;
                for (int j = 0; j < flashComp.length; j++) {
                    if (flashComp[j].equals(system.getPhase(0).getComponent(i).getName())) {
                        diff += Math.abs((system.getPhase(1).getComponent(i).getx()
                                * system.getPhase(1).getComponent(i).getFugasityCoefficient()
                                * system.getPhase(1).getPressure())
                                - (system.getPhase(0).getComponent(i).getx()
                                        * system.getPhase(0).getComponent(i)
                                                .getFugasityCoefficient()
                                        * system.getPhase(0).getPressure()));
                        system.getPhase(1).getComponent(i)
                                .setx(system.getPhase(1).getComponent(i).getx()
                                        * (system.getPhase(0).getComponent(i).getx()
                                                * system.getPhase(0).getComponent(i)
                                                        .getFugasityCoefficient()
                                                * system.getPhase(0).getPressure())
                                        / (system.getPhase(1).getComponent(i).getx()
                                                * system.getPhase(1).getComponent(i)
                                                        .getFugasityCoefficient()
                                                * system.getPhase(1).getPressure()));
                        fracdiff += system.getPhase(1).getComponent(i).getz()
                                - system.getPhase(1).getComponent(i).getx();
                        hasgot = true;
                        // logger.info("x " + system.getPhase(1).getComponent(i).getx());
                    }
                }
                // if(!hasgot) system.getPhase(1).getComponent(i).setx(1e-16);
            }

            // system.setBeta(0.5+fracdiff);

            system.getPhase(1).normalize();
            logger.info("diff " + diff);
        } while (diff > 1e-10 && iterations < 1000);

        if (diff > 1e-10) {
            logger.error("not able to converge dPflash....continuing....");
        }
    }
}
