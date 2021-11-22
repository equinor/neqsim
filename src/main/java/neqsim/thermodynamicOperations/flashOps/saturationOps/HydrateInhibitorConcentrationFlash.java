/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */
package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

public class HydrateInhibitorConcentrationFlash extends constantDutyTemperatureFlash {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(HydrateInhibitorConcentrationFlash.class);

    double hydT = 273.15;
    String inhibitor = "MEG";

    /**
     * Creates new bubblePointFlash
     */
    public HydrateInhibitorConcentrationFlash() {}

    public HydrateInhibitorConcentrationFlash(SystemInterface system, String inhibitor,
            double hydrateTemperature) {
        super(system);
        hydT = hydrateTemperature;
        this.inhibitor = inhibitor;
    }

    public void stop() {
        system = null;
    }

    @Override
    public void run() {
        ThermodynamicOperations ops = new ThermodynamicOperations(system);
        int iter = 0;
        double oldWt = 1.0, newWt = 2.0;
        double error = 1.0, oldError = 1.0,
                oldC = system.getPhase(0).getComponent(inhibitor).getNumberOfmoles();
        double derrordC = 1.0;
        do {
            iter++;
            try {
                derrordC = (error - oldError)
                        / (system.getPhase(0).getComponent(inhibitor).getNumberOfmoles() - oldC);
                oldError = error;
                oldC = system.getPhase(0).getComponent(inhibitor).getNumberOfmoles();

                if (iter < 4) {
                    system.addComponent(inhibitor, error * 0.01);
                } else {
                    double newC = -error / derrordC;
                    double correction = newC * 0.5;// (newC -
                                                   // system.getPhase(0).getComponent(inhibitor).getNumberOfmoles())
                                                   // *
                                                   // 0.5;

                    system.addComponent(inhibitor, correction);
                }
                system.init(0);
                system.init(1);
                ops.hydrateFormationTemperature(system.getTemperature());
                error = system.getTemperature() - hydT;

                logger.info("error " + error);
            } catch (Exception e) {
                logger.error("error", e);
            }
        } while ((Math.abs(error) > 1e-3 && iter < 100) || iter < 3);
    }

    @Override
    public void printToFile(String name) {}

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 0, 100.0);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testSystem.addComponent("methane", 1.0);
        testSystem.addComponent("ethane", 0.10);
        testSystem.addComponent("propane", 0.050);
        testSystem.addComponent("i-butane", 0.0050);
        testSystem.addComponent("MEG", 0.1);
        testSystem.addComponent("water", 1.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(9);

        testSystem.init(0);
        testSystem.setMultiPhaseCheck(true);
        testSystem.setHydrateCheck(true);

        try {
            testOps.hydrateInhibitorConcentration("MEG", 270.9);
            double cons = 100 * testSystem.getPhase(0).getComponent("MEG").getNumberOfmoles()
                    * testSystem.getPhase(0).getComponent("MEG").getMolarMass()
                    / (testSystem.getPhase(0).getComponent("MEG").getNumberOfmoles()
                            * testSystem.getPhase(0).getComponent("MEG").getMolarMass()
                            + testSystem.getPhase(0).getComponent("water").getNumberOfmoles()
                                    * testSystem.getPhase(0).getComponent("water").getMolarMass());
            logger.info("hydrate inhibitor concentration " + cons + " wt%");
        } catch (Exception e) {
            e.toString();
        }
        testSystem.display();
    }
}
