package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.empiric.BukacekWaterInGas;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class WaterDewPointAnalyser extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    protected static int numberOfStreams = 0;
    protected StreamInterface stream = null;
    private double referencePressure = 70.0;
    private String method = "Bukacek";

    public WaterDewPointAnalyser() {}

    public WaterDewPointAnalyser(StreamInterface stream) {
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
        if (method.equals("Bukacek")) {
            SystemInterface tempFluid = (SystemInterface) stream.getThermoSystem().clone();
            tempFluid.setTemperature(BukacekWaterInGas.waterDewPointTemperature(
                    tempFluid.getComponent("water").getx(), referencePressure));
            return tempFluid.getTemperature(unit);
        } else if (method.equals("multiphase")) {
            SystemInterface tempFluid = (SystemInterface) stream.getThermoSystem().clone();
            tempFluid.setPressure(referencePressure);
            tempFluid.setTemperature(0.1, "C");
            ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
            try {
                thermoOps.waterDewPointTemperatureMultiphaseFlash();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return tempFluid.getTemperature(unit);
        } else {
            SystemInterface tempFluid = (SystemInterface) stream.getThermoSystem().clone();
            SystemInterface tempFluid2 = tempFluid.setModel("GERG-water-EOS");
            tempFluid2.setPressure(referencePressure);
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

    public double getReferencePressure() {
        return referencePressure;
    }

    public void setReferencePressure(double referencePressure) {
        this.referencePressure = referencePressure;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
}
