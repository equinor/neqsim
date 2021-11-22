package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class HydrateEquilibriumTemperatureAnalyser extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    protected static int numberOfStreams = 0;
    protected StreamInterface stream = null;
    private double referencePressure = 0;

    public HydrateEquilibriumTemperatureAnalyser() {}

    public HydrateEquilibriumTemperatureAnalyser(StreamInterface stream) {
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
        if (!tempFluid.doHydrateCheck()) {
            tempFluid.setHydrateCheck(true);
        }
        tempFluid.setTemperature(10.0, "C");
        if (referencePressure > 1e-10) {
            tempFluid.setPressure(referencePressure);
        }
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
        try {
            thermoOps.hydrateFormationTemperature();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tempFluid.getTemperature(unit);
    }

    public double getReferencePressure() {
        return referencePressure;
    }

    public void setReferencePressure(double referencePressure) {
        this.referencePressure = referencePressure;
    }
}
