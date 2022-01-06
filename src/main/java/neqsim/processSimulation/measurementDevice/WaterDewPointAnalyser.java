package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.empiric.BukacekWaterInGas;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * WaterDewPointAnalyser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class WaterDewPointAnalyser extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    /** Constant <code>numberOfStreams=0</code> */
    protected static int numberOfStreams = 0;
    protected StreamInterface stream = null;
    private double referencePressure = 70.0;
    private String method = "Bukacek";

    /**
     * <p>
     * Constructor for WaterDewPointAnalyser.
     * </p>
     */
    public WaterDewPointAnalyser() {}

    /**
     * <p>
     * Constructor for WaterDewPointAnalyser.
     * </p>
     *
     * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public WaterDewPointAnalyser(StreamInterface stream) {
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
        if (method.equals("Bukacek")) {
            SystemInterface tempFluid = stream.getThermoSystem().clone();
            tempFluid.setTemperature(BukacekWaterInGas.waterDewPointTemperature(
                    tempFluid.getComponent("water").getx(), referencePressure));
            return tempFluid.getTemperature(unit);
        } else if (method.equals("multiphase")) {
            SystemInterface tempFluid = stream.getThermoSystem().clone();
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
            SystemInterface tempFluid = stream.getThermoSystem().clone();
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

    /**
     * <p>
     * Getter for the field <code>method</code>.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getMethod() {
        return method;
    }

    /**
     * <p>
     * Setter for the field <code>method</code>.
     * </p>
     *
     * @param method a {@link java.lang.String} object
     */
    public void setMethod(String method) {
        this.method = method;
    }
}
