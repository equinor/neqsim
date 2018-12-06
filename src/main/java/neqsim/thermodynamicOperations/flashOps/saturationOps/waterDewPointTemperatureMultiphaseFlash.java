/*
 * bubblePointFlash.java
 *
 * Created on 14. oktober 2000, 16:30
 */
package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class waterDewPointTemperatureMultiphaseFlash extends constantDutyTemperatureFlash {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new bubblePointFlash
     */
    public waterDewPointTemperatureMultiphaseFlash() {
    }

    public waterDewPointTemperatureMultiphaseFlash(SystemInterface system) {
        super(system);
    }

    public void run() {

        ThermodynamicOperations TPflashOps = new ThermodynamicOperations(system);
        system.setMultiPhaseCheck(true);
        boolean hasAqueousPhase = false;
        double dT = 0.1;
        system.setTemperature(600.0);
        do {
            i++;
            TPflashOps.TPflash();
            if (system.hasPhaseType("aqueous")) {
                dT = system.getPhaseOfType("aqueous").getComponent("water").getNumberOfMolesInPhase() / system.getPhase(0).getComponent("water").getNumberOfmoles();
                if(dT>1.0) {
                    dT=1.0;
                }
                system.setTemperature(system.getTemperature()+dT);
            } else {
                dT = -10.0;//system.getPhaseOfType("aqueous").getComponent("water").getNumberOfMolesInPhase() / system.getNumberOfMoles();
                system.setTemperature(system.getTemperature()+dT);
                //system.display();
            }
           // System.out.println("dew temperature " + system.getTemperature());
        } while ((i < 350 && Math.abs(dT) > 1e-5));
        System.out.println("i " + i);
        //system.display();

    }

    public void printToFile(String name) {
    }
}
