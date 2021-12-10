package neqsim.processSimulation.processEquipment.util;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author esol
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

    public MoleFractionControllerUtil(StreamInterface inletStream) {
        setInletStream(inletStream);
    }

    public void setInletStream(StreamInterface inletStream) {
        this.inletStream = inletStream;

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        outStream = new Stream(thermoSystem);
    }

    public StreamInterface getOutStream() {
        return outStream;
    }

    public double getMolesChange() {
        return molesChange;
    }

    public void setMoleFraction(String compName, double moleFrac) {
        moleFractionReduction = false;
        this.moleFrac = moleFrac;
        this.compName = compName;
    }

    public void setComponentRate(String compName, double rate, String unit) {
        moleFractionReduction = false;

        if (unit.equals("litre/MSm^3")) {
            System.out.println("density .."
                    + thermoSystem.getPhase(0).getComponent(compName).getNormalLiquidDensity());
            this.moleFrac = rate
                    * thermoSystem.getPhase(0).getComponent(compName).getNormalLiquidDensity()
                    / thermoSystem.getPhase(0).getComponent(compName).getMolarMass() / 42294896.67;
        } else {
            System.out.println("error ..unit not defined..");
        }
        this.compName = compName;
    }

		if (unit.equals("litre/MSm^3")) {
			//System.out.println("density .." + thermoSystem.getPhase(0).getComponent(compName).getNormalLiquidDensity());
			this.moleFrac = rate * thermoSystem.getPhase(0).getComponent(compName).getNormalLiquidDensity()
					/ thermoSystem.getPhase(0).getComponent(compName).getMolarMass() / 42294896.67;
		} else {
			//System.out.println("error ..unit not defined..");
		}
		this.compName = compName;
	}

    @Override
    public void run() {
        System.out.println("MoleFractionContollerUtil running..");
        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        if (thermoSystem.getPhase(0).hasComponent(compName)) {
            thermoOps = new ThermodynamicOperations(thermoSystem);
            thermoSystem.init(1);
            double deltaFrac = moleFrac - thermoSystem.getPhase(0).getComponent(compName).getz();
            if (moleFractionReduction) {
                deltaFrac = (moleFractionReductionRatio)
                        * thermoSystem.getPhase(0).getComponent(compName).getz();
            }
            double molesChange = deltaFrac * thermoSystem.getTotalNumberOfMoles();
            thermoSystem.addComponent(compName, molesChange);// deltaFrac*thermoSystem.getTotalNumberOfMoles());
            thermoOps.TPflash();
        }
        outStream.setThermoSystem(thermoSystem);
    }

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

	@Override
	public void displayResult() {
		thermoSystem.display(getName());
	}
}
