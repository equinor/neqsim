/*
 * TemperatureTransmitter.java
 *
 * Created on 6. juni 2006, 15:24
 *
 * To change this template, choose Tools | Template Manager and open the template in the editor.
 */

package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class pHProbe extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    protected static int numberOfStreams = 0;
    protected String name = new String();
    protected StreamInterface stream = null;
    protected SystemInterface reactiveThermoSystem;
    protected ThermodynamicOperations thermoOps;

    /** Creates a new instance of TemperatureTransmitter */
    public pHProbe() {}

    public pHProbe(StreamInterface stream) {
        this.stream = stream;
        numberOfStreams++;
        streamNumber = numberOfStreams;
    }

    public void run() {
        if (stream != null) {
            reactiveThermoSystem =
                    this.stream.getThermoSystem().setModel("Electrolyte-CPA-EOS-statoil");
        }
        thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
        thermoOps.TPflash();
    }

    @Override
    public void displayResult() {
        System.out.println("measured temperature " + stream.getTemperature());
    }

    @Override
    public double getMeasuredValue() {
        return reactiveThermoSystem.getPhase(reactiveThermoSystem.getPhaseNumberOfPhase("aqueous"))
                .getpH();
    }
}
