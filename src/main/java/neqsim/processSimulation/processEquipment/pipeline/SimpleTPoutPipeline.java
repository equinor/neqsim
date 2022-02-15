package neqsim.processSimulation.processEquipment.pipeline;

import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * SimpleTPoutPipeline class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SimpleTPoutPipeline extends Pipeline {
    private static final long serialVersionUID = 1000;

    boolean setTemperature = false;
    protected double temperatureOut = 0, pressureOut = 0.0;
    double dH = 0.0;

    /**
     * <p>
     * Constructor for SimpleTPoutPipeline.
     * </p>
     */
    public SimpleTPoutPipeline() {}

    /**
     * <p>
     * Constructor for SimpleTPoutPipeline.
     * </p>
     *
     * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public SimpleTPoutPipeline(StreamInterface inStream) {
        this.inStream = inStream;
        outStream = (Stream) inStream.clone();
    }

    /** {@inheritDoc} */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getOutStream() {
        return outStream;
    }

    /**
     * <p>
     * setOutTemperature.
     * </p>
     *
     * @param temperature a double
     */
    public void setOutTemperature(double temperature) {
        this.temperatureOut = temperature;
    }

    /**
     * <p>
     * setOutPressure.
     * </p>
     *
     * @param pressure a double
     */
    public void setOutPressure(double pressure) {
        this.pressureOut = pressure;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        system = inStream.getThermoSystem().clone();
        // system.setMultiPhaseCheck(true);
        system.setTemperature(this.temperatureOut);
        system.setPressure(this.pressureOut);
        ThermodynamicOperations testOps = new ThermodynamicOperations(system);
        testOps.TPflash();
        // system.setMultiPhaseCheck(false);
        outStream.setThermoSystem(system);
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        outStream.getThermoSystem().display(name);
        outStream.getThermoSystem().initPhysicalProperties();
        System.out.println("Superficial velocity out gas : " + getSuperficialVelocity(0, 1));
        System.out.println("Superficial velocity out condensate : " + getSuperficialVelocity(1, 1));
        System.out.println("Superficial velocity out MEG/water : " + getSuperficialVelocity(2, 1));
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient() {}

    /** {@inheritDoc} */
    @Override
    public FlowSystemInterface getPipe() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void setInitialFlowPattern(String flowPattern) {}
}
