/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.processEquipment.distillation;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class Condenser extends neqsim.processSimulation.processEquipment.distillation.SimpleTray implements TrayInterface {

    private static final long serialVersionUID = 1000;

    private double refluxRatio = 0.1;
    boolean refluxIsSet = false;

    public Condenser() {
    }

    /**
     * @return the refluxRatio
     */
    public double getRefluxRatio() {
        return refluxRatio;
    }

    /**
     * @param refluxRatio the refluxRatio to set
     */
    public void setRefluxRatio(double refluxRatio) {
        this.refluxRatio = refluxRatio;
        refluxIsSet = true;
    }

     public double getDuty(){
        return calcMixStreamEnthalpy();
    }

    public void run() {
        if (!refluxIsSet) {
            super.run();
        } else {
            SystemInterface thermoSystem2 = (SystemInterface) ((StreamInterface) streams.get(0)).getThermoSystem().clone();
            //System.out.println("total number of moles " + thermoSystem2.getTotalNumberOfMoles());
            mixedStream.setThermoSystem(thermoSystem2);
            ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
            testOps.PVrefluxFlash(refluxRatio, 0);
        }
    //System.out.println("enthalpy: " + mixedStream.getThermoSystem().getEnthalpy());
    //        System.out.println("enthalpy: " + enthalpy);
    // System.out.println("temperature: " + mixedStream.getThermoSystem().getTemperature());


    //    System.out.println("beta " + mixedStream.getThermoSystem().getBeta())
    }
}
