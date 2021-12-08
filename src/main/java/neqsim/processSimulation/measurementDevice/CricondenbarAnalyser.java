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

    public CricondenbarAnalyser() {}

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
        tempFluid.removeComponent("water");
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
        try {
            thermoOps.setRunAsThread(true);
            thermoOps.calcPTphaseEnvelope(false, 1.);
            thermoOps.waitAndCheckForFinishedCalculation(15000);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return thermoOps.get("cricondenbar")[1];
    }

    public double getMeasuredValue2(String unit, double temp) {
        SystemInterface tempFluid = (SystemInterface) stream.getThermoSystem().clone();
        tempFluid.setTemperature(temp, "C");
        tempFluid.setPressure(10.0, "bara");
        if (tempFluid.getPhase(0).hasComponent("water")) {
            tempFluid.removeComponent("water");
        }
        neqsim.PVTsimulation.simulation.SaturationPressure thermoOps =
                new neqsim.PVTsimulation.simulation.SaturationPressure(tempFluid);
        try {
            thermoOps.run();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return thermoOps.getSaturationPressure();
    }

}
