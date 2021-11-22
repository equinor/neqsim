/*
 * staticMixer.java
 *
 * Created on 11. mars 2001, 01:49
 */
package neqsim.processSimulation.processEquipment.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;

/**
 * @author Even Solbraa
 * @version
 */
public class SetPoint extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000;

    ProcessEquipmentInterface sourceEquipment = null, targetEquipment = null;
    String sourceVarialble = "", targetVariable = "", targetPhase = "", targetComponent = "";
    double targetValue = 0.0;
    String targetUnit = "";
    double inputValue = 0.0, oldInputValue = 0.0;

    static Logger logger = LogManager.getLogger(SetPoint.class);

    /** Creates new staticMixer */
    public SetPoint() {}

    public SetPoint(String name) {
        super(name);
    }

    public SetPoint(String name, ProcessEquipmentInterface targetEquipment, String targetVariable,
            ProcessEquipmentInterface sourceEquipment) {
        this.targetEquipment = targetEquipment;
        this.targetVariable = targetVariable;
        this.sourceEquipment = sourceEquipment;
        run();
    }

    public void setSourceVariable(ProcessEquipmentInterface adjustedEquipment,
            String adjstedVariable) {
        this.sourceEquipment = adjustedEquipment;
        this.sourceVarialble = adjstedVariable;
    }

    public void setSourceVariable(ProcessEquipmentInterface adjustedEquipment) {
        this.sourceEquipment = adjustedEquipment;
    }

    public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable,
            double targetValue, String targetUnit) {
        this.targetEquipment = targetEquipment;
        this.targetVariable = targetVariable;
        this.targetValue = targetValue;
        this.targetUnit = targetUnit;
    }

    public void setTargetVariable(ProcessEquipmentInterface targetEquipment,
            String targetVariable) {
        this.targetEquipment = targetEquipment;
        this.targetVariable = targetVariable;
    }

    public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable,
            double targetValue, String targetUnit, String targetPhase) {
        this.targetEquipment = targetEquipment;
        this.targetVariable = targetVariable;
        this.targetValue = targetValue;
        this.targetUnit = targetUnit;
        this.targetPhase = targetPhase;
    }

    public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable,
            double targetValue, String targetUnit, String targetPhase, String targetComponent) {
        this.targetEquipment = targetEquipment;
        this.targetVariable = targetVariable;
        this.targetValue = targetValue;
        this.targetUnit = targetUnit;
        this.targetPhase = targetPhase;
        this.targetComponent = targetComponent;
    }

    public void runTransient() {
        run();
    }

    @Override
    public void run() {
        if (targetVariable.equals("pressure")) {
            targetEquipment.setPressure(sourceEquipment.getPressure());
        } else {
            inputValue = ((Stream) sourceEquipment).getThermoSystem().getNumberOfMoles();

            double targetValueCurrent =
                    ((Stream) targetEquipment).getThermoSystem().getVolume(targetUnit);

            double deviation = targetValue - targetValueCurrent;

            logger.info("adjuster deviation " + deviation + " inputValue " + inputValue);

            oldInputValue = inputValue;
        }
    }

    @Override
    public void displayResult() {}

    public static void main(String[] args) {
        // test code for adjuster...
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 20.00);
        testSystem.addComponent("methane", 1000.00);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream stream_1 = new Stream("Stream1", testSystem);
        SetPoint adjuster1 = new SetPoint();
        adjuster1.setSourceVariable(stream_1, "molarFlow");
        adjuster1.setTargetVariable(stream_1, "gasVolumeFlow", 10.0, "", "MSm3/day");

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(adjuster1);

        operations.run();
    }
}
