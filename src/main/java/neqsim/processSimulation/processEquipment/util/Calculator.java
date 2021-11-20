package neqsim.processSimulation.processEquipment.util;

import java.util.ArrayList;

import neqsim.processSimulation.processEquipment.*;
import neqsim.processSimulation.processEquipment.stream.*;;

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

    @Override
	public void run() {

        double sum = 0.0;

        if (name.equals("MEG makeup calculator")) {
            for (int i = 0; i < inputVariable.size(); i++) {
                sum += inputVariable.get(i).getFluid().getPhase(0).getComponent("MEG").getFlowRate("kg/hr");
            }
        } else {
            for (int i = 0; i < inputVariable.size(); i++) {
                sum += inputVariable.get(i).getFluid().getPhase(0).getComponent("TEG").getFlowRate("kg/hr");
            }
        }

        //System.out.println("make up MEG " + sum);
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
