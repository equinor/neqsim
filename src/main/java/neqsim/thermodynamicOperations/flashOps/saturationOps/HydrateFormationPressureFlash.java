/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */

package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

public class HydrateFormationPressureFlash extends constantDutyTemperatureFlash {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(HydrateFormationPressureFlash.class);

    /** Creates new bubblePointFlash */
    public HydrateFormationPressureFlash() {
    }

    public HydrateFormationPressureFlash(SystemInterface system) {
        super(system);
    }

    @Override
	public void run() {
        double olfFug = 0.0;
        // system.setHydrateCheck(true);
        ThermodynamicOperations ops = new ThermodynamicOperations(system);
        system.getPhase(4).getComponent("water").setx(1.0);
        system.init(0);
        system.init(1);
        int iter = 0;
        do {
            iter++;
            olfFug = system.getPhase(4).getFugacity("water");
            setFug();
            ops.TPflash();

            system.init(1);
            system.getPhase(4).getComponent("water").setx(1.0);
            logger.info("diff " + (system.getPhase(4).getFugacity("water") / system.getPhase(0).getFugacity("water")));
            system.setPressure(system.getPressure()
                    * (system.getPhase(4).getFugacity("water") / system.getPhase(0).getFugacity("water")));
            logger.info("presure " + system.getPressure());
            // logger.info("x water " + system.getPhase(3).getComponent("water").getx());
        } while (Math.abs((olfFug - system.getPhase(4).getFugacity("water")) / olfFug) > 1e-8 && iter < 100);
        // logger.info("hydrate structure = " + ((ComponentHydrate)
        // system.getPhase(3).getComponent("water")).getHydrateStructure());
        logger.info("end");
    }

    public void setFug() {
        for (int j = 0; j < system.getPhase(0).getNumberOfComponents(); j++) {
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                ((ComponentHydrate) system.getPhase(4).getComponent(j)).setRefFug(i, system.getPhase(0).getFugacity(i));
            }
        }
        system.getPhase(4).getComponent("water").fugcoef(system.getPhase(4));
        system.getPhase(4).getComponent("water").setx(1.0);
    }

    @Override
	public void printToFile(String name) {
    }

}
