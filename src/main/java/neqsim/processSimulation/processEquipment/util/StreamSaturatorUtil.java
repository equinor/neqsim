package neqsim.processSimulation.processEquipment.util;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>StreamSaturatorUtil class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class StreamSaturatorUtil extends ProcessEquipmentBaseClass {

    private static final long serialVersionUID = 1000;

    Stream inletStream;
    Stream outStream;
    SystemInterface thermoSystem;
    private boolean multiPhase = true;

    /**
     * <p>Constructor for StreamSaturatorUtil.</p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public StreamSaturatorUtil(Stream inletStream) {
        setInletStream(inletStream);
    }

    /**
     * <p>Setter for the field <code>inletStream</code>.</p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public void setInletStream(Stream inletStream) {
        this.inletStream = inletStream;

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        outStream = new Stream(thermoSystem);
    }

    /**
     * <p>Getter for the field <code>outStream</code>.</p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public Stream getOutStream() {
        return outStream;
    }

	/** {@inheritDoc} */
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

	/**
	 * <p>isMultiPhase.</p>
	 *
	 * @return a boolean
	 */
	public boolean isMultiPhase() {
		return multiPhase;
	}

	/**
	 * <p>Setter for the field <code>multiPhase</code>.</p>
	 *
	 * @param multiPhase a boolean
	 */
	public void setMultiPhase(boolean multiPhase) {
		this.multiPhase = multiPhase;
	}
}
