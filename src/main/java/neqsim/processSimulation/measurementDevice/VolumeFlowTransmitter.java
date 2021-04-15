/*
 * TemperatureTransmitter.java
 *
 * Created on 6. juni 2006, 15:24
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 *
 * @author ESOL
 */
public class VolumeFlowTransmitter extends MeasurementDeviceBaseClass {

    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    protected static int numberOfStreams = 0;
    protected StreamInterface stream = null;
    private int measuredPhaseNumber = 0;

    /** Creates a new instance of TemperatureTransmitter */
    public VolumeFlowTransmitter() {
    }

    public VolumeFlowTransmitter(StreamInterface stream) {
        this.stream = stream;
        numberOfStreams++;
        streamNumber = numberOfStreams;
        name = "volume flow rate";
        unit = "m^3/hr";
    }

    public void displayResult() {
        System.out.println("measured volume " + Double.toString(getMeasuredValue()) + " " + unit);
    }

    public double getMeasuredValue() {
        stream.getThermoSystem().initPhysicalProperties();
        if (unit.equals("m^3/hr")) {
            return stream.getThermoSystem().getPhase(measuredPhaseNumber).getNumberOfMolesInPhase()
                    * stream.getThermoSystem().getPhase(measuredPhaseNumber).getMolarMass()
                    / stream.getThermoSystem().getPhase(measuredPhaseNumber).getPhysicalProperties().getDensity()
                    * 3600.0;
        } else if (unit.equals("Nm^3/day")) {
            return stream.getThermoSystem().getPhase(measuredPhaseNumber).getNumberOfMolesInPhase()
                    * neqsim.thermo.ThermodynamicConstantsInterface.R
                    * neqsim.thermo.ThermodynamicConstantsInterface.normalStateTemperature / 101325.0 * 3600.0 * 24;
        } else if (unit.equals("Sm^3/day")) {
            return stream.getThermoSystem().getPhase(measuredPhaseNumber).getNumberOfMolesInPhase()
                    * neqsim.thermo.ThermodynamicConstantsInterface.R
                    * neqsim.thermo.ThermodynamicConstantsInterface.standardStateTemperature / 101325.0 * 3600.0 * 24;
        } else {
            return stream.getThermoSystem().getPhase(measuredPhaseNumber).getNumberOfMolesInPhase()
                    * stream.getThermoSystem().getPhase(measuredPhaseNumber).getMolarMass()
                    / stream.getThermoSystem().getPhase(measuredPhaseNumber).getPhysicalProperties().getDensity()
                    * 3600.0;
        }
    }

    public int getMeasuredPhaseNumber() {
        return measuredPhaseNumber;
    }

    public void setMeasuredPhaseNumber(int measuredPhase) {
        this.measuredPhaseNumber = measuredPhase;
    }

}
