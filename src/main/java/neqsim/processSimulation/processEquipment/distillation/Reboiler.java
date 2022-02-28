package neqsim.processSimulation.processEquipment.distillation;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Reboiler class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Reboiler extends neqsim.processSimulation.processEquipment.distillation.SimpleTray {
    private static final long serialVersionUID = 1000;

    private double refluxRatio = 0.1;
    boolean refluxIsSet = false;
    double duty = 0.0;

    /**
     * <p>
     * Constructor for Reboiler.
     * </p>
     * 
     * @param name
     */
    public Reboiler(String name) {
        super(name);
    }

    /**
     * <p>
     * Getter for the field <code>refluxRatio</code>.
     * </p>
     *
     * @return the refluxRatio
     */
    public double getRefluxRatio() {
        return refluxRatio;
    }

    /**
     * <p>
     * Setter for the field <code>refluxRatio</code>.
     * </p>
     *
     * @param refluxRatio the refluxRatio to set
     */
    public void setRefluxRatio(double refluxRatio) {
        this.refluxRatio = refluxRatio;
        refluxIsSet = true;
    }

    /**
     * <p>
     * Getter for the field <code>duty</code>.
     * </p>
     *
     * @return a double
     */
    public double getDuty() {
        return duty;
        // return calcMixStreamEnthalpy();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        if (!refluxIsSet) {
            super.run();
        } else {
            SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
            // System.out.println("total number of moles " +
            // thermoSystem2.getTotalNumberOfMoles());
            mixedStream.setThermoSystem(thermoSystem2);
            ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
            testOps.PVrefluxFlash(refluxRatio, 1);
        }
        // System.out.println("enthalpy: " +
        // mixedStream.getThermoSystem().getEnthalpy());
        // System.out.println("enthalpy: " + enthalpy);
        // System.out.println("temperature: " +
        // mixedStream.getThermoSystem().getTemperature());

        // System.out.println("beta " + mixedStream.getThermoSystem().getBeta())
        duty = mixedStream.getFluid().getEnthalpy() - calcMixStreamEnthalpy0();
    }
}
