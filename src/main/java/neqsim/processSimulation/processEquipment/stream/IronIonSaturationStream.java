/*
 * IronIonSaturationStream.java
 *
 * Created on 12. mars 2001, 13:11
 */

package neqsim.processSimulation.processEquipment.stream;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author Even Solbraa
 * @version
 */
public class IronIonSaturationStream extends Stream {
    private static final long serialVersionUID = 1000;

    protected SystemInterface reactiveThermoSystem;

    /** Creates new IronIonSaturationStream */
    public IronIonSaturationStream() {
        super();
    }

    public IronIonSaturationStream(SystemInterface thermoSystem) {
        super(thermoSystem);
    }

    public IronIonSaturationStream(StreamInterface stream) {
        super(stream);
    }

    public IronIonSaturationStream(String name, SystemInterface thermoSystem) {
        super(name, thermoSystem);
    }

    @Override
    public IronIonSaturationStream clone() {
        IronIonSaturationStream clonedSystem = null;
        try {
            clonedSystem = (IronIonSaturationStream) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedSystem;
    }

    @Override
    public void run() {
        System.out.println("start flashing stream... " + streamNumber);
        if (stream != null) {
            thermoSystem = (SystemInterface) this.stream.getThermoSystem().clone();
        }
        if (stream != null) {
            reactiveThermoSystem =
                    this.stream.getThermoSystem().setModel("Electrolyte-CPA-EOS-statoil");
        }
        reactiveThermoSystem.addComponent("Fe++", 1e-6);
        // reactiveThermoSystem.chemicalReactionInit();
        // reactiveThermoSystem.createDatabase(true);
        // reactiveThermoSystem.addComponent("water",
        // reactiveThermoSystem.getPhase(0).getComponent("MEG").getNumberOfmoles());
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
        thermoOps.TPflash();
        reactiveThermoSystem.display();
        try {
            System.out.println("aqueous phase number "
                    + reactiveThermoSystem.getPhaseNumberOfPhase("aqueous"));
            thermoOps.addIonToScaleSaturation(reactiveThermoSystem.getPhaseNumberOfPhase("aqueous"),
                    "FeCO3", "Fe++");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        reactiveThermoSystem.display();
        System.out.println("number of phases: " + reactiveThermoSystem.getNumberOfPhases());
        System.out.println("beta: " + reactiveThermoSystem.getBeta());
    }

    @Override
    public void displayResult() {
        reactiveThermoSystem.display(name);
    }
}
