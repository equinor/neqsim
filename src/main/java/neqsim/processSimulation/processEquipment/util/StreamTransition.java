package neqsim.processSimulation.processEquipment.util;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

public class StreamTransition extends ProcessEquipmentBaseClass {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private StreamInterface outletStream = null;
	private StreamInterface inletStream = null;

	public StreamTransition() {
		super();
	}
	
	public StreamTransition(StreamInterface inletStream, StreamInterface outletStream) {
		this.inletStream = inletStream;
		this.outletStream = outletStream;
	}

	public StreamInterface getInletStream() {
		return inletStream;
	}

	public void setInletStream(StreamInterface inletStream) {
		this.inletStream = inletStream;
	}

	public StreamInterface getOutletStream() {
		return outletStream;
	}

	public void setOutletStream(StreamInterface outletStream) {
		this.outletStream = outletStream;
	}

	public void run() {
		SystemInterface outThermoSystem =  null;
		if(outletStream!=null) {
		 outThermoSystem = (SystemInterface) outletStream.getFluid().clone();
		}
		else {
			 outThermoSystem = (SystemInterface) inletStream.getFluid().clone();
		}
		outThermoSystem.removeMoles();
		
	//	SystemInterface fluid1 = outletStream.getFluid();
	//	SystemInterface fluid2 = inletStream.getFluid();
		
		for(int i=0;i<inletStream.getFluid().getNumberOfComponents();i++) {
    		if(outThermoSystem.getPhase(0).hasComponent(inletStream.getFluid().getComponent(i).getName())){
    			outThermoSystem.addComponent(inletStream.getFluid().getComponent(i).getName(), inletStream.getFluid().getComponent(i).getNumberOfmoles());
    		}
    	}
    //	fluid1.init(0);
    //	fluid1.setTemperature(fluid2.getTemperature());
   // 	fluid1.setPressure(fluid2.getPressure());
    	outletStream.setThermoSystem(outThermoSystem);
    	outletStream.run();
		
	}
	
	public void displayResult() {
		outletStream.getFluid().display();
	}
	
	
public static void main(String[] args) {
	ProcessSystem offshoreProcessoperations = ProcessSystem.open("c:/temp/offshorePro.neqsim");
	ProcessSystem TEGprocess = ProcessSystem.open("c:/temp//TEGprocessHX.neqsim");
	StreamTransition trans = new StreamTransition((StreamInterface)offshoreProcessoperations.getUnit("rich gas"),(StreamInterface)TEGprocess.getUnit("dry feed gas"));
	
	offshoreProcessoperations.run();
	trans.run();
	((StreamInterface) offshoreProcessoperations.getUnit("rich gas")).displayResult();
//	((StreamInterface) TEGprocess.getUnit("dry feed gas")).displayResult();
	trans.displayResult();
	TEGprocess.run();
	((StreamInterface) TEGprocess.getUnit("dry feed gas")).displayResult();
	
	//((StreamInterface) TEGprocess.getUnit("dry feed gas")).displayResult();
			
}
}
