package neqsim.processSimulation.processEquipment.util;

import java.util.ArrayList;

import neqsim.processSimulation.mechanicalDesign.absorber.AbsorberMechanicalDesign;
import neqsim.processSimulation.processEquipment.*;
import neqsim.processSimulation.mechanicalDesign.absorber.AbsorberMechanicalDesign;
import neqsim.processSimulation.processEquipment.stream.*;
import neqsim.thermo.system.SystemInterface;;

public class Calculator extends ProcessEquipmentBaseClass {

	ArrayList<ProcessEquipmentInterface> inputVariable = new ArrayList<ProcessEquipmentInterface>();
	private ProcessEquipmentInterface outputVariable;
	String type = "sumTEG";

	public Calculator(String name) {
		super(name);
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

	public void addInputVariable(ProcessEquipmentInterface unit) {
		inputVariable.add(unit);
	}

	public ProcessEquipmentInterface getOutputVariable() {
		return outputVariable;
	}

	public void run() {

			double sum = 0.0;

			for (int i = 0; i < inputVariable.size(); i++) {
				sum += inputVariable.get(i).getFluid().getPhase(0).getComponent("TEG").getFlowRate("kg/hr");
			}

			//System.out.println("make up TEG " + sum);
			outputVariable.getFluid().setTotalFlowRate(sum, "kg/hr");
			try {
				((Stream) outputVariable).setFlowRate(sum, "kg/hr");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	public void setOutputVariable(ProcessEquipmentInterface outputVariable) {
		this.outputVariable = outputVariable;
	}

}
