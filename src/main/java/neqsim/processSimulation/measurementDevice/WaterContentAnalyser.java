/*
 * TemperatureTransmitter.java
 *
 * Created on 6. juni 2006, 15:24
 *
 * To change this template, choose Tools | Template Manager and open the template in the editor.
 */

package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 *
 * @author ESOL
 */
public class WaterContentAnalyser extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    protected static int numberOfStreams = 0;
    protected StreamInterface stream = null;

    /** Creates a new instance of TemperatureTransmitter */
    public WaterContentAnalyser() {}

    public WaterContentAnalyser(StreamInterface stream) {
        this.stream = stream;
        numberOfStreams++;
        streamNumber = numberOfStreams;
        name = "water analyser";
        unit = "kg/day";
    }

    @Override
    public void displayResult() {
        try {
            System.out.println("total water production [kg/dag]"
                    + stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles()
                            * stream.getThermoSystem().getPhase(0).getComponent("water")
                                    .getMolarMass()
                            * 3600 * 24);
            System.out.println("water in phase 1 (ppm) "
                    + stream.getThermoSystem().getPhase(0).getComponent("water").getx() * 1e6);
        } finally {
        }
    }

    @Override
    public double getMeasuredValue() {
        return stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles()
                * stream.getThermoSystem().getPhase(0).getComponent("water").getMolarMass() * 3600
                * 24;
    }
}
