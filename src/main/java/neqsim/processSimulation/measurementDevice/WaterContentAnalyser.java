package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * WaterContentAnalyser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class WaterContentAnalyser extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    /** Constant <code>numberOfStreams=0</code> */
    protected static int numberOfStreams = 0;
    protected StreamInterface stream = null;

    /**
     * <p>
     * Constructor for WaterContentAnalyser.
     * </p>
     */
    public WaterContentAnalyser() {}

    /**
     * <p>
     * Constructor for WaterContentAnalyser.
     * </p>
     *
     * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public WaterContentAnalyser(StreamInterface stream) {
        this.stream = stream;
        numberOfStreams++;
        streamNumber = numberOfStreams;
        name = "water analyser";
        unit = "kg/day";
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public double getMeasuredValue() {
        return stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles()
                * stream.getThermoSystem().getPhase(0).getComponent("water").getMolarMass() * 3600
                * 24;
    }
}
