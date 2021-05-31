/*
 * Heater.java
 *
 * Created on 15. mars 2001, 14:17
 */

package neqsim.processSimulation.processEquipment.pipeline;

import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class SimpleTPoutPipeline extends Pipeline implements ProcessEquipmentInterface, PipeLineInterface {

    private static final long serialVersionUID = 1000;

    boolean setTemperature = false;
    protected double temperatureOut = 0, pressureOut = 0.0;
    double dH = 0.0;

    /** Creates new Heater */
    public SimpleTPoutPipeline() {
    }

    public SimpleTPoutPipeline(StreamInterface inStream) {
        this.inStream = inStream;
        outStream = (Stream) inStream.clone();
    }

    @Override
	public void setName(String name) {
        this.name = name;
    }

    @Override
	public StreamInterface getOutStream() {
        return outStream;
    }

    public void setOutTemperature(double temperature) {
        this.temperatureOut = temperature;
    }

    public void setOutPressure(double pressure) {
        this.pressureOut = pressure;
    }

    @Override
	public void run() {
        system = (SystemInterface) inStream.getThermoSystem().clone();
        // system.setMultiPhaseCheck(true);
        system.setTemperature(this.temperatureOut);
        system.setPressure(this.pressureOut);
        ThermodynamicOperations testOps = new ThermodynamicOperations(system);
        testOps.TPflash();
        // system.setMultiPhaseCheck(false);
        outStream.setThermoSystem(system);
    }

    @Override
	public void displayResult() {
        outStream.getThermoSystem().display(name);
        outStream.getThermoSystem().initPhysicalProperties();
        System.out.println("Superficial velocity out gas : " + getSuperficialVelocity(0, 1));
        System.out.println("Superficial velocity out condensate : " + getSuperficialVelocity(1, 1));
        System.out.println("Superficial velocity out MEG/water : " + getSuperficialVelocity(2, 1));

    }

    @Override
	public String getName() {
        return name;
    }

    @Override
	public void runTransient() {
    }

    @Override
	public FlowSystemInterface getPipe() {
        return null;
    }

    @Override
	public void setInitialFlowPattern(String flowPattern) {

    }

}
