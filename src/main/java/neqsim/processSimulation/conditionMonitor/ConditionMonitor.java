package neqsim.processSimulation.conditionMonitor;

import java.util.ArrayList;

import neqsim.processSimulation.processSystem.ProcessSystem;

public class ConditionMonitor extends java.lang.Object implements java.io.Serializable, Runnable {
    private static final long serialVersionUID = 1000;
    ProcessSystem refprocess = null;
    ProcessSystem process = null;

    public ConditionMonitor() {

    }

    public ConditionMonitor(ProcessSystem refprocess) {
        this.refprocess = refprocess;
        process = refprocess.copy();
    }

    public void conditionAnalysis(String unitName) {
        neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass refUn = (neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass) refprocess
                .getUnit(unitName);
        ((neqsim.processSimulation.processEquipment.ProcessEquipmentInterface) process.getUnit(unitName))
                .runConditionAnalysis(refUn);
    }

    public void conditionAnalysis() {
        ArrayList<String> names = process.getAllUnitNames();
        for (int i = 0; i < names.size(); i++) {
            conditionAnalysis(names.get(i));
        }
    }

    public ProcessSystem getProcess() {
        return process;
    }

    @Override
	public void run() {
        process = refprocess.copy();
    }

}
