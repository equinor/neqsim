package neqsim.processSimulation.processEquipment.util;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * StreamTransition class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class StreamTransition extends ProcessEquipmentBaseClass {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private StreamInterface outletStream = null;
    private StreamInterface inletStream = null;

    /**
     * <p>
     * Constructor for StreamTransition.
     * </p>
     */
    public StreamTransition() {
        super("StreamTransition");
    }

    /**
     * <p>
     * Constructor for StreamTransition.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     * @param outletStream a
     *        {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamTransition(StreamInterface inletStream, StreamInterface outletStream) {
        this("StreamTransition", inletStream, outletStream);
    }

    /**
     * <p>
     * Constructor for StreamTransition.
     * </p>
     *
     * @param name
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     * @param outletStream a
     *        {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamTransition(String name, StreamInterface inletStream,
            StreamInterface outletStream) {
        super(name);
        this.inletStream = inletStream;
        this.outletStream = outletStream;
    }

    /**
     * <p>
     * Getter for the field <code>inletStream</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getInletStream() {
        return inletStream;
    }

    /**
     * <p>
     * Setter for the field <code>inletStream</code>.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public void setInletStream(StreamInterface inletStream) {
        this.inletStream = inletStream;
    }

    /**
     * <p>
     * Getter for the field <code>outletStream</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getOutletStream() {
        return outletStream;
    }

    /**
     * <p>
     * Setter for the field <code>outletStream</code>.
     * </p>
     *
     * @param outletStream a
     *        {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public void setOutletStream(StreamInterface outletStream) {
        this.outletStream = outletStream;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        SystemInterface outThermoSystem = null;
        if (outletStream != null) {
            outThermoSystem = outletStream.getFluid().clone();
        } else {
            outThermoSystem = inletStream.getFluid().clone();
        }
        outThermoSystem.setEmptyFluid();

        // SystemInterface fluid1 = outletStream.getFluid();
        // SystemInterface fluid2 = inletStream.getFluid();

        for (int i = 0; i < inletStream.getFluid().getNumberOfComponents(); i++) {
            if (outThermoSystem.getPhase(0)
                    .hasComponent(inletStream.getFluid().getComponent(i).getName())) {
                outThermoSystem.addComponent(inletStream.getFluid().getComponent(i).getName(),
                        inletStream.getFluid().getComponent(i).getNumberOfmoles());
            }
        }
        // fluid1.init(0);
        // fluid1.setTemperature(fluid2.getTemperature());
        // fluid1.setPressure(fluid2.getPressure());
        outletStream.setThermoSystem(outThermoSystem);
        outletStream.run();
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        outletStream.getFluid().display();
    }

    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        ProcessSystem offshoreProcessoperations = ProcessSystem.open("c:/temp/offshorePro.neqsim");
        ProcessSystem TEGprocess = ProcessSystem.open("c:/temp//TEGprocessHX.neqsim");
        StreamTransition trans = new StreamTransition(
                (StreamInterface) offshoreProcessoperations.getUnit("rich gas"),
                (StreamInterface) TEGprocess.getUnit("dry feed gas"));

        offshoreProcessoperations.run();
        trans.run();
        ((StreamInterface) offshoreProcessoperations.getUnit("rich gas")).displayResult();
        // ((StreamInterface) TEGprocess.getUnit("dry feed gas")).displayResult();
        trans.displayResult();
        TEGprocess.run();
        ((StreamInterface) TEGprocess.getUnit("dry feed gas")).displayResult();

        // ((StreamInterface) TEGprocess.getUnit("dry feed gas")).displayResult();
    }
}
