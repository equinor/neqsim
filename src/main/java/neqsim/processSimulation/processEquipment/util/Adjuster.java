/*
 * staticMixer.java
 *
 * Created on 11. mars 2001, 01:49
 */
package neqsim.processSimulation.processEquipment.util;

import org.apache.logging.log4j.*;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.separator.GasScrubberSimple;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.util.example.PhaseEnvelope;
import neqsim.thermodynamicOperations.ThermodynamicOperations;


/**
 *
 * @author  Even Solbraa
 * @version
 * 
 * 
 */
public class Adjuster extends ProcessEquipmentBaseClass implements ProcessEquipmentInterface {

    private static final long serialVersionUID = 1000;

    ProcessEquipmentInterface adjustedEquipment = null, targetEquipment=null;
    String adjustedVarialble = "", targetVariable="", targetPhase="", targetComponent="";
    double targetValue = 0.0;
    String targetUnit = "";
    private double tolerance = 1e-3;
    double inputValue = 0.0, oldInputValue=0.0;
    private double error = 1e6, oldError=1.0e6;
    int iterations = 0;
    

    static Logger logger = LogManager.getLogger(PhaseEnvelope.class);

    /** Creates new staticMixer */
    public Adjuster() {
    }

    public Adjuster(String name) {
        super(name);
    }
    
    public void setAdjustedVariable(ProcessEquipmentInterface adjustedEquipment, String adjstedVariable) {
    	this.adjustedEquipment = adjustedEquipment;
    	this.adjustedVarialble = adjstedVariable;
    }
    
    public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable, double targetValue, String targetUnit) {
    	this.targetEquipment = targetEquipment;
    	this.targetVariable = targetVariable;
    	this.targetValue = targetValue;
    	this.targetUnit = targetUnit;
    }
    
    public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable, double targetValue, String targetUnit, String targetPhase) {
    	this.targetEquipment = targetEquipment;
    	this.targetVariable = targetVariable;
    	this.targetValue = targetValue;
    	this.targetUnit = targetUnit;
    	this.targetPhase = targetPhase;
    }
    
    public void setTargetVariable(ProcessEquipmentInterface targetEquipment, String targetVariable, double targetValue, String targetUnit, String targetPhase, String targetComponent) {
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

    public void run() {
    	iterations ++;
    	oldError = error;
    	 inputValue = ((Stream) adjustedEquipment).getThermoSystem().getNumberOfMoles();
    	
    	double targetValueCurrent = ((Stream) targetEquipment).getThermoSystem().getVolume(targetUnit);
    	
    	double deviation = targetValue-targetValueCurrent;
    	
    	error = deviation;
    	logger.info("adjuster deviation " + deviation + " inputValue " + inputValue);
    	if(iterations<2) {
    	((Stream) adjustedEquipment).getThermoSystem().setTotalFlowRate(inputValue+deviation, "mol/sec");
    	}
    	else {
    		double derivate = (error-oldError) / (inputValue-oldInputValue);
    		double newVal = error/derivate;
    		((Stream) adjustedEquipment).getThermoSystem().setTotalFlowRate(inputValue-newVal, "mol/sec");
    	}
    	
    	
    	
    	oldInputValue = inputValue;
    }
    
    public boolean solved() {
    	if(Math.abs(error)<tolerance) return true;
    	else return false;
    }

   
    
    public void displayResult() {
        
    }
  
    
    public double getTolerance() {
        return tolerance;
    }

    /**
     * @param tolerance the tolerance to set
     */
    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
    }

    /**
     * @return the error
     */
    public double getError() {
        return error;
    }

    /**
     * @param error the error to set
     */
    public void setError(double error) {
        this.error = error;
    }
    
    public static void main(String[] args) {
    	// test code for adjuster...
    	  neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15+25.0),20.00);
          testSystem.addComponent("methane", 1000.00);
          testSystem.createDatabase(true);
          testSystem.setMixingRule(2);
          
          Stream stream_1 = new Stream("Stream1", testSystem);
          Adjuster adjuster1 = new Adjuster();
          adjuster1.setAdjustedVariable(stream_1, "molarFlow");
          adjuster1.setTargetVariable(stream_1, "gasVolumeFlow", 10.0, "", "MSm3/day");
          
          neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
          operations.add(stream_1);
          operations.add(adjuster1);
          
          operations.run();
    }
    
}
