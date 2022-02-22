package neqsim.processSimulation.processEquipment.subsea;

import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.pipeline.AdiabaticTwoPhasePipe;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * SimpleFlowLine class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SimpleFlowLine extends TwoPortEquipment {
    private static final long serialVersionUID = 1000;

    private double height = 100.0;
    public double length = 520.0;
    double outletTemperature = 313.15;
    AdiabaticTwoPhasePipe pipeline;

    /**
     * <p>
     * Constructor for SimpleFlowLine.
     * </p>
     *
     * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    @Deprecated
    public SimpleFlowLine(StreamInterface instream) {
        this("SimpleFlowLine", instream);
    }

    /**
     * Constructor for SimpleFlowLine.
     * 
     * @param name
     * @param stream
     */
    public SimpleFlowLine(String name, StreamInterface stream) {
        super(name, stream);
        pipeline = new AdiabaticTwoPhasePipe(stream);
    }

    /**
     * <p>
     * Getter for the field <code>pipeline</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.pipeline.AdiabaticTwoPhasePipe}
     *         object
     */
    public AdiabaticTwoPhasePipe getPipeline() {
        return pipeline;
    }

    /** {@inheritDoc} */
    @Override
    public SystemInterface getThermoSystem() {
        return getOutStream().getThermoSystem();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        pipeline.run();
        getOutStream().setFluid(pipeline.getOutStream().getFluid());

        /*
         * System.out.println("stary P " );
         * 
         * SystemInterface fluidIn = (inStream.getFluid()).clone(); fluidIn.initProperties();
         * 
         * double density = fluidIn.getDensity("kg/m3");
         * 
         * double deltaP =
         * density*getHeight()*neqsim.thermo.ThermodynamicConstantsInterface.gravity/1. 0e5;
         * 
         * System.out.println("density " +density + " delta P " + deltaP);
         * 
         * fluidIn.setPressure(fluidIn.getPressure("bara")-deltaP);
         * fluidIn.setTemperature(outletTemperature);
         * 
         * ThermodynamicOperations ops = new ThermodynamicOperations(fluidIn); ops.TPflash();
         * 
         * getOutStream().setFluid(fluidIn);
         */
    }

    /**
     * <p>
     * Getter for the field <code>outStream</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getOutStream() {
        return outStream;
    }

    /**
     * <p>
     * Setter for the field <code>outStream</code>.
     * </p>
     *
     * @param outStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public void setOutStream(StreamInterface outStream) {
        this.outStream = outStream;
    }

    /**
     * <p>
     * Getter for the field <code>height</code>.
     * </p>
     *
     * @return a double
     */
    public double getHeight() {
        return height;
    }

    /**
     * <p>
     * Setter for the field <code>height</code>.
     * </p>
     *
     * @param height a double
     */
    public void setHeight(double height) {
        this.height = height;
    }
}
