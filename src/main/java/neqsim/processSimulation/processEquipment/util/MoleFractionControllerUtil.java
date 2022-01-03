package neqsim.processSimulation.processEquipment.util;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>MoleFractionControllerUtil class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class MoleFractionControllerUtil extends ProcessEquipmentBaseClass {

	private static final long serialVersionUID = 1000;

	StreamInterface inletStream;
	StreamInterface outStream;
	SystemInterface thermoSystem;
	ThermodynamicOperations thermoOps;
	String compName = null;
	double moleFrac = 1.0, molesChange = 0.0, moleFractionReductionRatio = 0.0;
	boolean moleFractionReduction = false;

	/**
	 * <p>Constructor for MoleFractionControllerUtil.</p>
	 *
	 * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
	 */
	public MoleFractionControllerUtil(StreamInterface inletStream) {
		setInletStream(inletStream);
	}

	/**
	 * <p>Setter for the field <code>inletStream</code>.</p>
	 *
	 * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
	 */
	public void setInletStream(StreamInterface inletStream) {
		this.inletStream = inletStream;

		thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
		outStream = new Stream(thermoSystem);
	}

	/**
	 * <p>Getter for the field <code>outStream</code>.</p>
	 *
	 * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
	 */
	public StreamInterface getOutStream() {
		return outStream;
	}

	/**
	 * <p>Getter for the field <code>molesChange</code>.</p>
	 *
	 * @return a double
	 */
	public double getMolesChange() {
		return molesChange;
	}

	/**
	 * <p>setMoleFraction.</p>
	 *
	 * @param compName a {@link java.lang.String} object
	 * @param moleFrac a double
	 */
	public void setMoleFraction(String compName, double moleFrac) {
		moleFractionReduction = false;
		this.moleFrac = moleFrac;
		this.compName = compName;
	}

	/**
	 * <p>setComponentRate.</p>
	 *
	 * @param compName a {@link java.lang.String} object
	 * @param rate a double
	 * @param unit a {@link java.lang.String} object
	 */
	public void setComponentRate(String compName, double rate, String unit) {
		moleFractionReduction = false;

		if (unit.equals("litre/MSm^3")) {
			//System.out.println("density .." + thermoSystem.getPhase(0).getComponent(compName).getNormalLiquidDensity());
			this.moleFrac = rate * thermoSystem.getPhase(0).getComponent(compName).getNormalLiquidDensity()
					/ thermoSystem.getPhase(0).getComponent(compName).getMolarMass() / 42294896.67;
		} else {
			//System.out.println("error ..unit not defined..");
		}
		this.compName = compName;
	}

	/**
	 * <p>setRelativeMoleFractionReduction.</p>
	 *
	 * @param compName a {@link java.lang.String} object
	 * @param moleFracRatio a double
	 */
	public void setRelativeMoleFractionReduction(String compName, double moleFracRatio) {
		moleFractionReduction = true;
		moleFractionReductionRatio = moleFracRatio;
		this.compName = compName;
	}

	/** {@inheritDoc} */
	@Override
	public void run() {
		//System.out.println("MoleFractionContollerUtil running..");
		thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
		if (thermoSystem.getPhase(0).hasComponent(compName)) {
			thermoOps = new ThermodynamicOperations(thermoSystem);
			thermoSystem.init(1);
			double deltaFrac = moleFrac - thermoSystem.getPhase(0).getComponent(compName).getz();
			if (moleFractionReduction) {
				deltaFrac = (moleFractionReductionRatio) * thermoSystem.getPhase(0).getComponent(compName).getz();
			}
			double molesChange = deltaFrac * thermoSystem.getTotalNumberOfMoles();
			thermoSystem.addComponent(compName, molesChange);// deltaFrac*thermoSystem.getTotalNumberOfMoles());
			thermoOps.TPflash();
		}
		outStream.setThermoSystem(thermoSystem);
	}

	/** {@inheritDoc} */
	@Override
	public void displayResult() {
		thermoSystem.display(getName());
	}
}
