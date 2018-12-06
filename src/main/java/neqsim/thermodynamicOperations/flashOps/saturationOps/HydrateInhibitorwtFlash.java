/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */
package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class HydrateInhibitorwtFlash extends constantDutyTemperatureFlash {

    private static final long serialVersionUID = 1000;

    double wtfrac = 0.5;
    String inhibitor = "MEG";

    /**
     * Creates new bubblePointFlash
     */
    public HydrateInhibitorwtFlash() {
    }

    public HydrateInhibitorwtFlash(SystemInterface system, String inhibitor, double wtfr) {
        super(system);
        wtfrac = wtfr;
        this.inhibitor = inhibitor;
        
        
    }

    public void stop() {
        system = null;
    }

    public void run() {

        ThermodynamicOperations ops = new ThermodynamicOperations(system);
        int iter = 0;
        double oldWt = 1.0, newWt = 2.0;
        double error = 1.0, oldError = 1.0, oldC = system.getPhase(0).getComponent(inhibitor).getNumberOfmoles();
        double derrordC = 1.0;
        do {
            iter++;
            try {
                derrordC = (error - oldError) / (system.getPhase(0).getComponent(inhibitor).getNumberOfmoles() - oldC);
                oldError = error;
                oldC = system.getPhase(0).getComponent(inhibitor).getNumberOfmoles();

                if (iter < 4) {
                    system.addComponent(inhibitor, error * 0.01);
                } else {

                    double newC = -error / derrordC;
                    double correction = newC * 0.5;//(newC - system.getPhase(0).getComponent(inhibitor).getNumberOfmoles()) * 0.5;

                    system.addComponent(inhibitor, correction);
                }
                system.init(0);
                system.init(1);
                ops.TPflash();
                double wtp = 0.0;
                if (system.hasPhaseType("aqueous")) {
                    wtp = system.getPhase("aqueous").getComponent(inhibitor).getx() * system.getPhase("aqueous").getComponent(inhibitor).getMolarMass() / (system.getPhase("aqueous").getComponent(inhibitor).getx() * system.getPhase("aqueous").getComponent(inhibitor).getMolarMass() + system.getPhase("aqueous").getComponent("water").getx() * system.getPhase("aqueous").getComponent("water").getMolarMass());
                } else {
                    system.addComponent(inhibitor, system.getTotalNumberOfMoles());
                    ops.TPflash();
                    wtp = system.getPhase("aqueous").getComponent(inhibitor).getx() * system.getPhase("aqueous").getComponent(inhibitor).getMolarMass() / (system.getPhase("aqueous").getComponent(inhibitor).getx() * system.getPhase("aqueous").getComponent(inhibitor).getMolarMass() + system.getPhase("aqueous").getComponent("water").getx() * system.getPhase("aqueous").getComponent("water").getMolarMass());
              
                }
                error = -(wtp - wtfrac);

                System.out.println("error " + error);

            } catch (Exception e) {
                e.printStackTrace();
            }

        } while ((Math.abs(error) > 1e-5 && iter < 100) || iter < 3);
        //system.display();
    }

    public void printToFile(String name) {
    }

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 0, 100.0);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testSystem.addComponent("nitrogen", 79.0);
        testSystem.addComponent("oxygen", 21.0);
     //   testSystem.addComponent("ethane", 0.10);
       // testSystem.addComponent("propane", 0.050);
       // testSystem.addComponent("i-butane", 0.0050);
        testSystem.addComponent("MEG", 0.000001);
        testSystem.addComponent("water", 0.0010);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);

        testSystem.init(0);
        testSystem.setMultiPhaseCheck(true);
        testSystem.setHydrateCheck(true);

        try {
            testOps.hydrateInhibitorConcentrationSet("MEG", 0.99);
            double cons = 100 * testSystem.getPhase(0).getComponent("MEG").getNumberOfmoles() * testSystem.getPhase(0).getComponent("MEG").getMolarMass() / (testSystem.getPhase(0).getComponent("MEG").getNumberOfmoles() * testSystem.getPhase(0).getComponent("MEG").getMolarMass() + testSystem.getPhase(0).getComponent("water").getNumberOfmoles() * testSystem.getPhase(0).getComponent("water").getMolarMass());
            System.out.println("hydrate inhibitor concentration " + cons + " wt%");
        } catch (Exception e) {
            e.toString();
        }
        testSystem.display();
    }
}
