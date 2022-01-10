package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * HydrateEquilibriumTemperatureAnalyser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class HydrateEquilibriumTemperatureAnalyser extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    /** Constant <code>numberOfStreams=0</code> */
    protected static int numberOfStreams = 0;
    protected StreamInterface stream = null;
    private double referencePressure = 0;

    /**
     * <p>
     * Constructor for HydrateEquilibriumTemperatureAnalyser.
     * </p>
     */
    public HydrateEquilibriumTemperatureAnalyser() {}

    /**
     * <p>
     * Constructor for HydrateEquilibriumTemperatureAnalyser.
     * </p>
     *
     * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public HydrateEquilibriumTemperatureAnalyser(StreamInterface stream) {
        this.stream = stream;
        numberOfStreams++;
        streamNumber = numberOfStreams;
        unit = "K";
        setConditionAnalysisMaxDeviation(1.0);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public double getMeasuredValue() {
        return getMeasuredValue(unit);
    }

    /** {@inheritDoc} */
    @Override
    public double getMeasuredValue(String unit) {
        SystemInterface tempFluid = stream.getThermoSystem().clone();
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

    /**
     * <p>
     * Getter for the field <code>referencePressure</code>.
     * </p>
     *
     * @return a double
     */
    public double getReferencePressure() {
        return referencePressure;
    }

    /**
     * <p>
     * Setter for the field <code>referencePressure</code>.
     * </p>
     *
     * @param referencePressure a double
     */
    public void setReferencePressure(double referencePressure) {
        this.referencePressure = referencePressure;
    }
}
