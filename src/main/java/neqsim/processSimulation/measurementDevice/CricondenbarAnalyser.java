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
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class CricondenbarAnalyser extends MeasurementDeviceBaseClass {

    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    protected static int numberOfStreams = 0;
    protected StreamInterface stream = null;

    /** Creates a new instance of TemperatureTransmitter */
    public CricondenbarAnalyser() {
    }

    public CricondenbarAnalyser(StreamInterface stream) {
        this.stream = stream;
        numberOfStreams++;
        streamNumber = numberOfStreams;
        unit = "K";
        setConditionAnalysisMaxDeviation(1.0);
    }

    @Override
	public void displayResult() {
        try {
            // System.out.println("total water production [kg/dag]" +
            // stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles()*stream.getThermoSystem().getPhase(0).getComponent("water").getMolarMass()*3600*24);
            // System.out.println("water in phase 1 (ppm) " +
            // stream.getThermoSystem().getPhase(0).getComponent("water").getx()*1e6);
        } finally {
        }
    }

    @Override
	public double getMeasuredValue() {
        return getMeasuredValue(unit);
    }

    @Override
	public double getMeasuredValue(String unit) {
        SystemInterface tempFluid = (SystemInterface) stream.getThermoSystem().clone();
        SystemInterface tempFluid2 = tempFluid.setModel("GERG-water-EOS");
        tempFluid2.setPressure(70.0);
        tempFluid2.setTemperature(-17.0, "C");
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid2);
        try {
            thermoOps.waterDewPointTemperatureFlash();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tempFluid2.getTemperature(unit);
    }

}
