package neqsim.processSimulation.processEquipment.util;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author esol
 */
public class StreamSaturatorUtil extends ProcessEquipmentBaseClass {

    private static final long serialVersionUID = 1000;

    Stream inletStream;
    Stream outStream;
    SystemInterface thermoSystem;
    private boolean multiPhase = true;

    public StreamSaturatorUtil(Stream inletStream) {
        setInletStream(inletStream);
    }

    public void setInletStream(Stream inletStream) {
        this.inletStream = inletStream;

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        outStream = new Stream(thermoSystem);
    }

    public Stream getOutStream() {
        return outStream;
    }

    @Override
	public void run() {
    	boolean changeBack = false;
        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        if(multiPhase && !thermoSystem.doMultiPhaseCheck()) {
        	thermoSystem.setMultiPhaseCheck(true);  
        	changeBack = true;
        }
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoOps.saturateWithWater();
        thermoSystem.init(3);
        if(changeBack) {
        	thermoSystem.setMultiPhaseCheck(false);        
        }
        outStream.setThermoSystem(thermoSystem);
    }

	public boolean isMultiPhase() {
		return multiPhase;
	}

	public void setMultiPhase(boolean multiPhase) {
		this.multiPhase = multiPhase;
	}
}
