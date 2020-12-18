package neqsim.processSimulation.processEquipment.reservoir;

import java.io.Serializable;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class Well implements Serializable{
	
	private StreamInterface stream = null;
	String name;
	double x, y, z;
	
	public Well() {
		
	}

	public Well(String name) {
		this.name = name;
	}

	public StreamInterface getStream() {
		return stream;
	}

	public void setStream(StreamInterface stream) {
		this.stream = stream;
	}
	
	public double getGOR() {
		SystemInterface locStream = (SystemInterface) (stream.getFluid()).clone();
		locStream.setTemperature(288.15);
		locStream.setPressure(1.01325);
		ThermodynamicOperations ops = new ThermodynamicOperations(locStream);
		ops.TPflash();
		double GOR = Double.NaN;
		if(locStream.hasPhaseType("gas") && locStream.hasPhaseType("oil")) {  
			GOR = locStream.getPhase("gas").getVolume("m3")/locStream.getPhase("oil").getVolume("m3");
		}
		return GOR;
	}
	
	public double getStdGasProduction() {
		SystemInterface locStream = (SystemInterface) (stream.getFluid()).clone();
		locStream.setTemperature(288.15);
		locStream.setPressure(1.01325);
		ThermodynamicOperations ops = new ThermodynamicOperations(locStream);
		ops.TPflash();
		double volume = Double.NaN;
		if(locStream.hasPhaseType("gas")) {  
			volume = locStream.getPhase("gas").getVolume("m3");
		}
		return volume;
	}
	
	public double getStdOilProduction() {
		SystemInterface locStream = (SystemInterface) (stream.getFluid()).clone();
		locStream.setTemperature(288.15);
		locStream.setPressure(1.01325);
		ThermodynamicOperations ops = new ThermodynamicOperations(locStream);
		ops.TPflash();
		double volume = Double.NaN;
		if(locStream.hasPhaseType("oil")) {  
			volume = locStream.getPhase("oil").getVolume("m3");
		}
		return volume;
	}
	
	
}
