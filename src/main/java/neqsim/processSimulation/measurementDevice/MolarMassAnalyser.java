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
public class MolarMassAnalyser extends MeasurementDeviceBaseClass {

    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    protected static int numberOfStreams = 0;
    protected StreamInterface stream = null;

    /** Creates a new instance of TemperatureTransmitter */
    public MolarMassAnalyser() {
        name = "molar mass analsyer";
        unit = "gr/mol";
    }

    public MolarMassAnalyser(StreamInterface stream) {
        this.stream = stream;
        numberOfStreams++;
        streamNumber = numberOfStreams;
        name = "molar mass analsyer";
        unit = "gr/mol";
    }

    @Override
	public void displayResult() {
        System.out.println("measured temperature " + stream.getThermoSystem().getMolarMass() * 1000.0);
    }

    @Override
	public double getMeasuredValue() {
        return stream.getThermoSystem().getMolarMass() * 1000.0;
    }
}
