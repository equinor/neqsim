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
 * <p>Adjuster class.</p>
 *
 * @author Even Solbraa
 */
public class Adjuster extends ProcessEquipmentBaseClass {

    private static final long serialVersionUID = 1000;

    ProcessEquipmentInterface adjustedEquipment = null, targetEquipment = null;
    String adjustedVarialble = "", targetVariable = "", targetPhase = "", targetComponent = "";
    double targetValue = 0.0;
    String targetUnit = "";
    private double tolerance = 1e-6;
    double inputValue = 0.0, oldInputValue = 0.0;
    private double error = 1e6, oldError = 1.0e6;
    int iterations = 0;
    private boolean activateWhenLess = false;

    static Logger logger = LogManager.getLogger(Adjuster.class);

    /**
     * Creates new staticMixer
     */
    public Adjuster() {}

    /**
     * <p>Constructor for Adjuster.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public Adjuster(String name) {
        super(name);
    }

    /**
     * <p>setAdjustedVariable.</p>
     *
     * @param adjustedEquipment a {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface} object
     * @param adjstedVariable a {@link java.lang.String} object
     */
    public void setAdjustedVariable(ProcessEquipmentInterface adjustedEquipment,
            String adjstedVariable) {
        this.adjustedEquipment = adjustedEquipment;
        this.adjustedVarialble = adjstedVariable;
    }

    /**
     * <p>Setter for the field <code>targetVariable</code>.</p>
     *
     * @param targetEquipment a {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface} object
     * @param targetVariable a {@link java.lang.String} object
     * @param targetValue a double
     * @param targetUnit a {@link java.lang.String} object
     */
    public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable,
            double targetValue, String targetUnit) {
        this.targetEquipment = targetEquipment;
        this.targetVariable = targetVariable;
        this.targetValue = targetValue;
        this.targetUnit = targetUnit;
    }

    /**
     * <p>Setter for the field <code>targetVariable</code>.</p>
     *
     * @param targetEquipment a {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface} object
     * @param targetVariable a {@link java.lang.String} object
     * @param targetValue a double
     * @param targetUnit a {@link java.lang.String} object
     * @param targetPhase a {@link java.lang.String} object
     */
    public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable,
            double targetValue, String targetUnit, String targetPhase) {
        this.targetEquipment = targetEquipment;
        this.targetVariable = targetVariable;
        this.targetValue = targetValue;
        this.targetUnit = targetUnit;
        this.targetPhase = targetPhase;
    }

    /**
     * <p>Setter for the field <code>targetVariable</code>.</p>
     *
     * @param targetEquipment a {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface} object
     * @param targetVariable a {@link java.lang.String} object
     * @param targetValue a double
     * @param targetUnit a {@link java.lang.String} object
     * @param targetPhase a {@link java.lang.String} object
     * @param targetComponent a {@link java.lang.String} object
     */
    public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable,
            double targetValue, String targetUnit, String targetPhase, String targetComponent) {
        this.targetEquipment = targetEquipment;
        this.targetVariable = targetVariable;
        this.targetValue = targetValue;
        this.targetUnit = targetUnit;
        this.targetPhase = targetPhase;
        this.targetComponent = targetComponent;
    }

    /**
     * <p>runTransient.</p>
     */
    public void runTransient() {
        run();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        oldError = error;

        if (adjustedVarialble.equals("mass flow")) {
            inputValue = ((Stream) adjustedEquipment).getThermoSystem().getFlowRate("kg/hr");
        } else {
            inputValue = ((Stream) adjustedEquipment).getThermoSystem().getNumberOfMoles();
        }

        double targetValueCurrent = 0.0;
        if (targetVariable.equals("mass fraction") && !targetPhase.equals("")
                && !targetComponent.equals("")) {
            targetValueCurrent = ((Stream) targetEquipment).getThermoSystem().getPhase(targetPhase)
                    .getWtFrac(targetComponent);
        } else if (targetVariable.equals("gasVolumeFlow")) {
            targetValueCurrent =
                    ((Stream) targetEquipment).getThermoSystem().getFlowRate(targetUnit);
        } else if (targetVariable.equals("pressure")) {
            targetValueCurrent =
                    ((Stream) targetEquipment).getThermoSystem().getPressure(targetUnit);
        } else {
            targetValueCurrent = ((Stream) targetEquipment).getThermoSystem().getVolume(targetUnit);
        }

        if (activateWhenLess && targetValueCurrent > targetValue) {
            error = 0.0;
            activateWhenLess = true;
            return;
        }

        iterations++;
        double deviation = targetValue - targetValueCurrent;

        error = deviation;
        logger.info("adjuster deviation " + deviation + " inputValue " + inputValue);
        if (iterations < 2) {
            if (adjustedVarialble.equals("mass flow")) {
                ((Stream) adjustedEquipment).getThermoSystem()
                        .setTotalFlowRate(inputValue + deviation, "kg/hr");
            } else {
                ((Stream) adjustedEquipment).getThermoSystem()
                        .setTotalFlowRate(inputValue + deviation, "mol/sec");
            }
        } else {
            double derivate = (error - oldError) / (inputValue - oldInputValue);
            double newVal = error / derivate;
            if (adjustedVarialble.equals("mass flow")) {
                ((Stream) adjustedEquipment).getThermoSystem().setTotalFlowRate(inputValue - newVal,
                        "kg/hr");
            } else {
                ((Stream) adjustedEquipment).getThermoSystem().setTotalFlowRate(inputValue - newVal,
                        "mol/sec");
            }
        }

        oldInputValue = inputValue;
    }

    /** {@inheritDoc} */
    @Override
    public boolean solved() {
        if (Math.abs(error) < tolerance)
            return true;
        else
            return false;
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {

    }

    /**
     * <p>Getter for the field <code>tolerance</code>.</p>
     *
     * @return a double
     */
    public double getTolerance() {
        return tolerance;
    }

    /**
     * <p>Setter for the field <code>tolerance</code>.</p>
     *
     * @param tolerance the tolerance to set
     */
    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
    }

    /**
     * <p>Getter for the field <code>error</code>.</p>
     *
     * @return the error
     */
    public double getError() {
        return error;
    }

    /**
     * <p>Setter for the field <code>error</code>.</p>
     *
     * @param error the error to set
     */
    public void setError(double error) {
        this.error = error;
    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        // test code for adjuster...
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 20.00);
        testSystem.addComponent("methane", 1000.00);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream stream_1 = new Stream("Stream1", testSystem);
        Adjuster adjuster1 = new Adjuster();
        adjuster1.setAdjustedVariable(stream_1, "molarFlow");
        adjuster1.setTargetVariable(stream_1, "gasVolumeFlow", 10.0, "MSm3/day", "");

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(adjuster1);

        operations.run();
    }

    /**
     * <p>isActivateWhenLess.</p>
     *
     * @return a boolean
     */
    public boolean isActivateWhenLess() {
        return activateWhenLess;
    }

    /**
     * <p>Setter for the field <code>activateWhenLess</code>.</p>
     *
     * @param activateWhenLess a boolean
     */
    public void setActivateWhenLess(boolean activateWhenLess) {
        this.activateWhenLess = activateWhenLess;
    }

}
