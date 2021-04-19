package neqsim.processSimulation.processEquipment.subsea;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.pipeline.AdiabaticTwoPhasePipe;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

public class SimpleFlowLine extends ProcessEquipmentBaseClass {

    protected StreamInterface inStream;
    private StreamInterface outStream;
    private double height = 100.0;
    public double length = 520.0;
    double outletTemperature = 313.15;
    AdiabaticTwoPhasePipe pipeline;

    public SimpleFlowLine(StreamInterface instream) {
        this.inStream = instream;
        setOutStream((StreamInterface) instream.clone());
        pipeline = new AdiabaticTwoPhasePipe(instream);
    }

    public AdiabaticTwoPhasePipe getPipeline() {
        return pipeline;
    }

    @Override
	public SystemInterface getThermoSystem() {
        return getOutStream().getThermoSystem();
    }

    @Override
	public void run() {
        pipeline.run();
        getOutStream().setFluid(pipeline.getOutStream().getFluid());

        /*
         * System.out.println("stary P " );
         * 
         * SystemInterface fluidIn = (SystemInterface) (inStream.getFluid()).clone();
         * fluidIn.initProperties();
         * 
         * double density = fluidIn.getDensity("kg/m3");
         * 
         * double deltaP =
         * density*getHeight()*neqsim.thermo.ThermodynamicConstantsInterface.gravity/1.
         * 0e5;
         * 
         * System.out.println("density " +density + " delta P " + deltaP);
         * 
         * fluidIn.setPressure(fluidIn.getPressure("bara")-deltaP);
         * fluidIn.setTemperature(outletTemperature);
         * 
         * ThermodynamicOperations ops = new ThermodynamicOperations(fluidIn);
         * ops.TPflash();
         * 
         * getOutStream().setFluid(fluidIn);
         */
    }

    public StreamInterface getOutStream() {
        return outStream;
    }

    public void setOutStream(StreamInterface outStream) {
        this.outStream = outStream;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }
}
