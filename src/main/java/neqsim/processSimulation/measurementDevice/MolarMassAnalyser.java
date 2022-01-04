package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * MolarMassAnalyser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class MolarMassAnalyser extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000;

    protected int streamNumber = 0;
    /** Constant <code>numberOfStreams=0</code> */
    protected static int numberOfStreams = 0;
    protected StreamInterface stream = null;

    /**
     * <p>
     * Constructor for MolarMassAnalyser.
     * </p>
     */
    public MolarMassAnalyser() {
        name = "molar mass analsyer";
        unit = "gr/mol";
    }

    /**
     * <p>
     * Constructor for MolarMassAnalyser.
     * </p>
     *
     * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public MolarMassAnalyser(StreamInterface stream) {
        this.stream = stream;
        numberOfStreams++;
        streamNumber = numberOfStreams;
        name = "molar mass analsyer";
        unit = "gr/mol";
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        System.out.println(
                "measured temperature " + stream.getThermoSystem().getMolarMass() * 1000.0);
    }

    /** {@inheritDoc} */
    @Override
    public double getMeasuredValue() {
        return stream.getThermoSystem().getMolarMass() * 1000.0;
    }
}
